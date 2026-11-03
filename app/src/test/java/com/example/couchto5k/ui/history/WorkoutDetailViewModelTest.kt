package com.example.couchto5k.ui.history

import androidx.lifecycle.SavedStateHandle
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeHistoryRepository: FakeWorkoutHistoryRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeHistoryRepository = FakeWorkoutHistoryRepository()
        fakeSettingsRepository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsWorkoutDetails_whenWorkoutIdProvidedInSavedStateHandle() = runTest {
        val completedWorkout = CompletedWorkout(
            id = 42L,
            trainingWorkoutId = 1L,
            title = "Test Workout",
            startedAt = Instant.now(),
            completedAt = Instant.now(),
            durationSeconds = 600,
            runningSeconds = 300,
            walkingSeconds = 300,
            distanceMeters = 1609.344,
            averagePaceSecondsPerKm = 372.8,
            longestCompletedRunSeconds = 180,
            status = WorkoutEngineStatus.COMPLETED,
        )
        val details = CompletedWorkoutDetails(workout = completedWorkout, intervals = emptyList())
        fakeHistoryRepository.storedDetails[42L] = details

        val savedStateHandle = SavedStateHandle(mapOf("workoutId" to 42L))
        val viewModel = WorkoutDetailViewModel(fakeHistoryRepository, fakeSettingsRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals(details, viewModel.workoutDetails.value)
    }

    @Test
    fun handlesInvalidWorkoutId_gracefully() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = WorkoutDetailViewModel(fakeHistoryRepository, fakeSettingsRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals(null, viewModel.workoutDetails.value)
    }

    private class FakeWorkoutHistoryRepository : WorkoutHistoryRepository {
        val storedDetails = mutableMapOf<Long, CompletedWorkoutDetails>()

        override fun getHistory(): Flow<List<CompletedWorkoutDetails>> = flowOf(storedDetails.values.toList())

        override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? = storedDetails[id]

        override suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState) {}

        override suspend fun deleteWorkout(id: Long) {
            storedDetails.remove(id)
        }

        override suspend fun clearAllHistory() {
            storedDetails.clear()
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val programStartDate: Flow<LocalDate?> = flowOf(null)
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = flowOf(emptySet())
        override val distanceUnit: Flow<DistanceUnit> = MutableStateFlow(DistanceUnit.MILES)

        override suspend fun setProgramStartDate(date: LocalDate) {}
        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {}
        override suspend fun setDistanceUnit(unit: DistanceUnit) {}
    }
}
