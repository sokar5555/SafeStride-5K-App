package com.example.couchto5k.ui.workout

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.FakeAudioHapticManager
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.service.WorkoutTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActiveWorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var workoutEngine: WorkoutEngine
    private lateinit var trainingPlanRepository: FakeTrainingPlanRepo
    private lateinit var historyRepository: FakeHistoryRepo
    private lateinit var settingsRepository: FakeSettingsRepo

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).clearStartedServices()
        workoutEngine = WorkoutEngine(FakeAudioHapticManager())
        trainingPlanRepository = FakeTrainingPlanRepo()
        historyRepository = FakeHistoryRepo()
        settingsRepository = FakeSettingsRepo()
    }

    @After
    fun tearDown() {
        workoutEngine.stopWorkout()
        Dispatchers.resetMain()
    }

    @Test
    fun recreatingViewModelWhileWorkoutIsRunning_preservesElapsedTimeDistanceAndDoesNotRestart() = runTest {
        try {
            val vm1 = createViewModel(workoutId = 1L)
            runCurrent()

            assertEquals(1L, vm1.currentWorkout.value?.id)
            assertEquals(WorkoutEngineStatus.RUNNING, workoutEngine.workoutState.value.status)
            assertNotNull(shadowOf(application).nextStartedService)
            shadowOf(application).clearStartedServices()

            // Simulate active progress (within the 300s warmup interval so workout stays RUNNING)
            val t0 = SystemClock.elapsedRealtime()
            SystemClock.setCurrentTimeMillis(t0 + 90_000L)
            workoutEngine.updateTime()
            workoutEngine.addDistance(320.0)

            // Simulate Activity/ViewModel recreation for the same workoutId
            val vm2 = createViewModel(workoutId = 1L)
            runCurrent()

            assertEquals(1L, vm2.currentWorkout.value?.id)
            assertEquals(WorkoutEngineStatus.RUNNING, workoutEngine.workoutState.value.status)
            assertEquals(90_000L, workoutEngine.workoutState.value.totalElapsedMillis)
            assertEquals(320.0, workoutEngine.workoutState.value.distanceMeters, 0.01)
            // Must NOT send another ACTION_START on recreation
            assertEquals(null, shadowOf(application).nextStartedService)
        } finally {
            workoutEngine.stopWorkout()
        }
    }

    @Test
    fun recreatingViewModelWhilePaused_preservesPausedStateAndOpenExerciseMode() = runTest {
        try {
            workoutEngine.startFreeRun(IntervalType.WALK)
            val t0 = SystemClock.elapsedRealtime()
            SystemClock.setCurrentTimeMillis(t0 + 45_000L)
            workoutEngine.updateTime()
            workoutEngine.pause()

            val vm = createViewModel(
                workoutId = WorkoutEngine.FREE_RUN_WORKOUT_ID,
                initialType = IntervalType.RUN, // Even if nav default says RUN, preserve active WALK
            )
            runCurrent()

            assertEquals("Free Walk", vm.currentWorkout.value?.title)
            assertEquals(WorkoutEngineStatus.PAUSED, workoutEngine.workoutState.value.status)
            assertTrue(workoutEngine.workoutState.value.isPaused)
            assertEquals(45_000L, workoutEngine.workoutState.value.totalElapsedMillis)
        } finally {
            workoutEngine.stopWorkout()
        }
    }

    @Test
    fun pauseResumeStop_dispatchExpectedForegroundServiceCommands() = runTest {
        try {
            val vm = createViewModel(workoutId = 1L)
            runCurrent()
            shadowOf(application).clearStartedServices()

            vm.pause()
            assertEquals(WorkoutTrackingService.ACTION_PAUSE, shadowOf(application).nextStartedService?.action)

            vm.resume()
            assertEquals(WorkoutTrackingService.ACTION_RESUME, shadowOf(application).nextStartedService?.action)

            vm.stop()
            assertEquals(WorkoutTrackingService.ACTION_STOP, shadowOf(application).nextStartedService?.action)
        } finally {
            workoutEngine.stopWorkout()
        }
    }

    @Test
    fun completingWorkout_savesHistoryOnceAndSetsPersonalRecordSummary() = runTest {
        try {
            val vm = createViewModel(workoutId = 2L)
            runCurrent()

            // Complete the workout (single 60s RUN interval in workout2 fixture)
            workoutEngine.skipToNextInterval()
            runCurrent()

            assertEquals(1, historyRepository.saveCount)
            val summary = vm.completionSummary.value
            assertNotNull(summary)
            assertTrue(summary!!.isNewPersonalRecord)
            assertEquals(60, summary.longestRunSeconds)

            // Start a second workout with same longestRun (60s) -> non-record workout
            val vm2 = createViewModel(workoutId = 2L)
            runCurrent()
            workoutEngine.skipToNextInterval()
            runCurrent()

            assertEquals(2, historyRepository.saveCount)
            val summary2 = vm2.completionSummary.value
            assertNotNull(summary2)
            assertFalse(summary2!!.isNewPersonalRecord)
        } finally {
            workoutEngine.stopWorkout()
        }
    }

    private fun createViewModel(
        workoutId: Long,
        initialType: IntervalType? = null,
    ): ActiveWorkoutViewModel {
        val handle = SavedStateHandle().apply {
            set("workoutId", workoutId)
            initialType?.let { set("initialType", it.name) }
        }
        return ActiveWorkoutViewModel(
            workoutEngine = workoutEngine,
            repository = trainingPlanRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            application = application,
            savedStateHandle = handle,
        )
    }

    private class FakeTrainingPlanRepo : TrainingPlanRepository {
        private val workout1 = TrainingWorkout(
            id = 1L,
            week = 1,
            workoutNumber = 1,
            title = "Week 1, Workout 1",
            intervals = listOf(
                WorkoutInterval(0, IntervalType.WARMUP, 300),
                WorkoutInterval(1, IntervalType.RUN, 60),
            ),
        )
        private val workout2 = TrainingWorkout(
            id = 2L,
            week = 1,
            workoutNumber = 2,
            title = "Week 1, Workout 2",
            intervals = listOf(WorkoutInterval(0, IntervalType.RUN, 60)),
        )

        override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(listOf(workout1, workout2))
        override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(emptyList())
        override suspend fun getWorkoutById(id: Long): TrainingWorkout? = when (id) {
            1L -> workout1
            2L -> workout2
            else -> null
        }
        override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long = 0L
        override suspend fun deleteCustomWorkout(id: Long) {}
        override suspend fun clearAllCustomWorkouts() {}
    }

    private class FakeHistoryRepo : WorkoutHistoryRepository {
        val historyFlow = MutableStateFlow<List<CompletedWorkoutDetails>>(emptyList())
        var saveCount = 0

        override fun getHistory(): Flow<List<CompletedWorkoutDetails>> = historyFlow
        override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? = null
        override suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState) {
            saveCount++
            val savedId = saveCount.toLong()
            val details = CompletedWorkoutDetails(
                workout = CompletedWorkout(
                    id = savedId,
                    trainingWorkoutId = workout.id,
                    scheduledWorkoutId = null,
                    title = workout.title,
                    startedAt = state.startedAt,
                    completedAt = Instant.now(),
                    durationSeconds = (state.totalElapsedMillis / 1000L).toInt(),
                    runningSeconds = workout.totalPlannedRunningSeconds,
                    walkingSeconds = workout.totalPlannedWalkingSeconds,
                    distanceMeters = state.distanceMeters,
                    averagePaceSecondsPerKm = state.averagePaceSecondsPerKm,
                    longestCompletedRunSeconds = workout.longestPlannedRunIntervalSeconds,
                    status = state.status,
                ),
                intervals = emptyList(),
                routePoints = emptyList(),
            )
            historyFlow.value = listOf(details) + historyFlow.value
        }

        override suspend fun deleteWorkout(id: Long) {}
        override suspend fun clearAllHistory() {}
    }

    private class FakeSettingsRepo : SettingsRepository {
        override val programStartDate: Flow<LocalDate?> = MutableStateFlow(null)
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = MutableStateFlow(emptySet())
        override val distanceUnit: Flow<DistanceUnit> = MutableStateFlow(DistanceUnit.MILES)
        override suspend fun setProgramStartDate(date: LocalDate) {}
        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {}
        override suspend fun setDistanceUnit(unit: DistanceUnit) {}
    }
}
