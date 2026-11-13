package com.example.couchto5k.domain.usecase

import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GetStatsUseCaseTest {
    private lateinit var fakeHistoryRepository: FakeHistoryRepository
    private lateinit var fakeTrainingPlanRepository: FakeTrainingPlanRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private var fixedToday: LocalDate = LocalDate.of(2026, 11, 13)
    private lateinit var getStatsUseCase: GetStatsUseCase

    @Before
    fun setUp() {
        fakeHistoryRepository = FakeHistoryRepository()
        fakeTrainingPlanRepository = FakeTrainingPlanRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        getStatsUseCase = GetStatsUseCase(
            workoutHistoryRepository = fakeHistoryRepository,
            trainingPlanRepository = fakeTrainingPlanRepository,
            settingsRepository = fakeSettingsRepository,
            todayProvider = { fixedToday },
        )
    }

    @Test
    fun `invoke with empty history returns null`() = runTest {
        val result = getStatsUseCase.invoke().first()
        assertNull(result)
    }

    @Test
    fun `invoke with single workout calculates correct stats`() = runTest {
        val localDateTime = LocalDateTime.of(2023, 10, 1, 9, 0)
        val workoutDetails = createMockWorkoutDetails(
            trainingWorkoutId = 1L,
            distance = 5000.0,
            duration = 1800,
            runTime = 1200,
            walkTime = 600,
            longestRun = 600,
            localDateTime = localDateTime,
        )
        val trainingWorkout = TrainingWorkout(1, 1, 1, "Workout 1", emptyList())

        fakeHistoryRepository.historyFlow.value = listOf(workoutDetails)
        fakeTrainingPlanRepository.workoutsFlow.value = listOf(trainingWorkout)

        val result = getStatsUseCase.invoke().first()
        assertNotNull(result)

        result!!
        assertEquals(1, result.userStats.completedWorkoutsCount)
        assertEquals(5000.0, result.userStats.totalDistanceMeters, 0.01)
        assertEquals(1800, result.userStats.totalDurationSeconds)
        assertEquals(1200, result.userStats.totalRunningSeconds)
        assertEquals(600, result.userStats.totalWalkingSeconds)
        assertEquals(360.0, result.userStats.aggregateAveragePaceSecondsPerKm, 0.01)
        assertEquals(1800, result.userStats.longestWorkoutDurationSeconds)

        assertEquals(600, result.runningProgression.startingLongestRunSeconds)
        assertEquals(600, result.runningProgression.currentLongestRunSeconds)
        assertEquals(0, result.runningProgression.improvementSeconds)

        // Verify Time of Day distribution
        assertEquals(4, result.timeOfDayDistribution.size)
        val morningCount = result.timeOfDayDistribution.find { (timeOfDay) -> timeOfDay == TimeOfDay.MORNING }
        assertNotNull(morningCount)
        assertEquals(1, morningCount!!.count)
        assertEquals(1.0f, morningCount.percentage, 0.01f)
    }

    @Test
    fun `invoke with multiple workouts calculates correct aggregates and cumulative record progression`() = runTest {
        val w1 = createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 1000.0, duration = 600, runTime = 300, walkTime = 300, longestRun = 60, localDateTime = LocalDateTime.of(2023, 10, 1, 8, 0))
        val w2 = createMockWorkoutDetails(trainingWorkoutId = 2L, distance = 2000.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2023, 10, 2, 14, 0))
        val w3 = createMockWorkoutDetails(trainingWorkoutId = 3L, distance = 3000.0, duration = 1800, runTime = 1200, walkTime = 600, longestRun = 180, localDateTime = LocalDateTime.of(2023, 10, 3, 18, 0))
        val w4 = createMockWorkoutDetails(trainingWorkoutId = 4L, distance = 2500.0, duration = 1500, runTime = 1000, walkTime = 500, longestRun = 120, localDateTime = LocalDateTime.of(2023, 10, 4, 23, 0))
        val w5 = createMockWorkoutDetails(trainingWorkoutId = 5L, distance = 5000.0, duration = 2500, runTime = 2000, walkTime = 500, longestRun = 300, localDateTime = LocalDateTime.of(2023, 10, 5, 9, 0))

        val history = listOf(w1, w2, w3, w4, w5)
        val trainingWorkouts = List(27) { i -> TrainingWorkout((i + 1).toLong(), (i / 3) + 1, (i % 3) + 1, "Workout ${i + 1}", emptyList()) }

        fakeHistoryRepository.historyFlow.value = history
        fakeTrainingPlanRepository.workoutsFlow.value = trainingWorkouts

        val result = getStatsUseCase.invoke().first()
        assertNotNull(result)
        result!!

        // Aggregates
        assertEquals(5, result.userStats.completedWorkoutsCount)
        assertEquals(13500.0, result.userStats.totalDistanceMeters, 0.01)
        assertEquals(7600, result.userStats.totalDurationSeconds)

        val expectedPace = 7600.0 / (13500.0 / 1000.0)
        assertEquals(expectedPace, result.userStats.aggregateAveragePaceSecondsPerKm, 0.01)

        // Progression
        assertEquals(60, result.runningProgression.startingLongestRunSeconds)
        assertEquals(300, result.runningProgression.currentLongestRunSeconds)
        assertEquals(240, result.runningProgression.improvementSeconds)

        // Progression series: 60, 60, 180, 180, 300
        assertEquals(5, result.runningProgression.recordHistory.size)
        assertEquals(60, result.runningProgression.recordHistory[0].longestRunSeconds)
        assertEquals(60, result.runningProgression.recordHistory[1].longestRunSeconds)
        assertEquals(180, result.runningProgression.recordHistory[2].longestRunSeconds)
        assertEquals(180, result.runningProgression.recordHistory[3].longestRunSeconds)
        assertEquals(300, result.runningProgression.recordHistory[4].longestRunSeconds)

        // Time of Day distribution
        val morningSlot = result.timeOfDayDistribution.find { (timeOfDay) -> timeOfDay == TimeOfDay.MORNING }
        val afternoonSlot = result.timeOfDayDistribution.find { (timeOfDay) -> timeOfDay == TimeOfDay.AFTERNOON }
        val eveningSlot = result.timeOfDayDistribution.find { (timeOfDay) -> timeOfDay == TimeOfDay.EVENING }
        val nightSlot = result.timeOfDayDistribution.find { (timeOfDay) -> timeOfDay == TimeOfDay.NIGHT }

        assertEquals(2, morningSlot?.count)
        assertEquals(1, afternoonSlot?.count)
        assertEquals(1, eveningSlot?.count)
        assertEquals(1, nightSlot?.count)

        // Program Progress
        assertEquals(5, result.programProgress.completedWorkoutsCount)
        assertEquals(27, result.programProgress.totalProgramWorkoutsCount)
        assertEquals(5.0f / 27.0f, result.programProgress.completionPercentage, 0.01f)
    }

    @Test
    fun `programProgress excludes free runs custom workouts cancelled and repeated workouts`() = runTest {
        val official1 = createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 2000.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 1, 9, 0))
        val official1Repeat = createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 2100.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 2, 9, 0))
        val official2 = createMockWorkoutDetails(trainingWorkoutId = 2L, distance = 2200.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 3, 9, 0))
        val official3Cancelled = createMockWorkoutDetails(trainingWorkoutId = 3L, distance = 500.0, duration = 300, runTime = 120, walkTime = 180, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 4, 9, 0), status = WorkoutEngineStatus.CANCELLED)
        val freeRun = createMockWorkoutDetails(trainingWorkoutId = 0L, distance = 3000.0, duration = 1500, runTime = 1500, walkTime = 0, longestRun = 1500, localDateTime = LocalDateTime.of(2026, 11, 5, 9, 0))
        val customWorkout = createMockWorkoutDetails(trainingWorkoutId = 10001L, distance = 2500.0, duration = 1400, runTime = 900, walkTime = 500, longestRun = 300, localDateTime = LocalDateTime.of(2026, 11, 6, 9, 0))

        fakeHistoryRepository.historyFlow.value = listOf(
            official1,
            official1Repeat,
            official2,
            official3Cancelled,
            freeRun,
            customWorkout,
        )
        fakeTrainingPlanRepository.workoutsFlow.value = List(27) { i ->
            TrainingWorkout((i + 1).toLong(), (i / 3) + 1, (i % 3) + 1, "W${i + 1}", emptyList())
        }

        val result = getStatsUseCase.invoke().first()!!

        // Overall stats include all workouts
        assertEquals(6, result.userStats.completedWorkoutsCount)
        // Program progress counts only the 2 distinct completed official workouts (IDs 1 and 2)
        assertEquals(2, result.programProgress.completedWorkoutsCount)
        assertEquals(27, result.programProgress.totalProgramWorkoutsCount)
        assertEquals(2f / 27f, result.programProgress.completionPercentage, 0.001f)
    }

    @Test
    fun `programProgress 13 week program counts out of 39 and derives current week from calendar start week`() = runTest {
        fakeSettingsRepository.progressiveWeeksExtensionFlow.value = 4 // 9 + 4 = 13 weeks (39 workouts)
        fakeSettingsRepository.programStartDateFlow.value = LocalDate.of(2026, 11, 4) // Wed of Week 1 (Sun Nov 1)
        fixedToday = LocalDate.of(2026, 11, 18) // Wed of Week 3 (Sun Nov 15)

        val w1 = createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 2000.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 4, 9, 0))
        val w2 = createMockWorkoutDetails(trainingWorkoutId = 2L, distance = 2100.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 6, 9, 0))
        val w28Progressive = createMockWorkoutDetails(trainingWorkoutId = 28L, distance = 5000.0, duration = 2000, runTime = 1400, walkTime = 600, longestRun = 1400, localDateTime = LocalDateTime.of(2026, 11, 18, 9, 0))

        fakeHistoryRepository.historyFlow.value = listOf(w1, w2, w28Progressive)
        fakeTrainingPlanRepository.workoutsFlow.value = List(27) { i ->
            TrainingWorkout((i + 1).toLong(), (i / 3) + 1, (i % 3) + 1, "W${i + 1}", emptyList())
        }

        val result = getStatsUseCase.invoke().first()!!
        assertEquals(3, result.programProgress.completedWorkoutsCount)
        assertEquals(39, result.programProgress.totalProgramWorkoutsCount)
        assertEquals(3f / 39f, result.programProgress.completionPercentage, 0.001f)
        assertEquals(3, result.programProgress.currentWeekNumber)
    }

    @Test
    fun `programProgress current week aligns to Sunday calendar week boundaries and clamps cleanly`() = runTest {
        fakeSettingsRepository.progressiveWeeksExtensionFlow.value = 0 // 9 weeks max
        fakeTrainingPlanRepository.workoutsFlow.value = List(27) { i ->
            TrainingWorkout((i + 1).toLong(), (i / 3) + 1, (i % 3) + 1, "W${i + 1}", emptyList())
        }
        fakeHistoryRepository.historyFlow.value = listOf(
            createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 1000.0, duration = 600, runTime = 300, walkTime = 300, longestRun = 60, localDateTime = LocalDateTime.of(2026, 11, 7, 9, 0)),
        )

        // Start Saturday Nov 7 -> Sunday Nov 8 is Week 2
        fakeSettingsRepository.programStartDateFlow.value = LocalDate.of(2026, 11, 7)
        fixedToday = LocalDate.of(2026, 11, 8)
        assertEquals(2, getStatsUseCase.invoke().first()!!.programProgress.currentWeekNumber)

        // Before start date -> Week 1
        fixedToday = LocalDate.of(2026, 11, 6)
        assertEquals(1, getStatsUseCase.invoke().first()!!.programProgress.currentWeekNumber)

        // Far after Week 9 -> clamped to 9
        fixedToday = LocalDate.of(2027, 3, 1)
        assertEquals(9, getStatsUseCase.invoke().first()!!.programProgress.currentWeekNumber)
    }

    @Test
    fun `runningProgression ignores pure free walk workouts`() = runTest {
        val freeWalk = createMockWorkoutDetails(trainingWorkoutId = 0L, distance = 1500.0, duration = 900, runTime = 0, walkTime = 900, longestRun = 0, localDateTime = LocalDateTime.of(2026, 11, 1, 9, 0))
        val firstRun = createMockWorkoutDetails(trainingWorkoutId = 1L, distance = 2000.0, duration = 1200, runTime = 300, walkTime = 900, longestRun = 180, localDateTime = LocalDateTime.of(2026, 11, 2, 9, 0))

        fakeHistoryRepository.historyFlow.value = listOf(freeWalk, firstRun)
        fakeTrainingPlanRepository.workoutsFlow.value = List(27) { i ->
            TrainingWorkout((i + 1).toLong(), (i / 3) + 1, (i % 3) + 1, "W${i + 1}", emptyList())
        }

        val result = getStatsUseCase.invoke().first()!!
        assertEquals(180, result.runningProgression.startingLongestRunSeconds)
        assertEquals(180, result.runningProgression.currentLongestRunSeconds)
        assertEquals(0, result.runningProgression.improvementSeconds)
        assertEquals(1, result.runningProgression.recordHistory.size)
    }

    private fun createMockWorkoutDetails(
        trainingWorkoutId: Long = 1L,
        distance: Double,
        duration: Int,
        runTime: Int,
        walkTime: Int,
        longestRun: Int,
        localDateTime: LocalDateTime,
        status: WorkoutEngineStatus = WorkoutEngineStatus.COMPLETED,
    ): CompletedWorkoutDetails {
        val startedAt = localDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val completedAt = startedAt.plusSeconds(duration.toLong())
        val completedWorkout = CompletedWorkout(
            id = trainingWorkoutId,
            trainingWorkoutId = trainingWorkoutId,
            scheduledWorkoutId = null,
            title = "Title",
            startedAt = startedAt,
            completedAt = completedAt,
            durationSeconds = duration,
            runningSeconds = runTime,
            walkingSeconds = walkTime,
            distanceMeters = distance,
            averagePaceSecondsPerKm = 0.0,
            longestCompletedRunSeconds = longestRun,
            status = status,
        )
        return CompletedWorkoutDetails(completedWorkout, emptyList(), emptyList())
    }

    private class FakeHistoryRepository : WorkoutHistoryRepository {
        val historyFlow = MutableStateFlow<List<CompletedWorkoutDetails>>(emptyList())

        override fun getHistory(): Flow<List<CompletedWorkoutDetails>> = historyFlow
        override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? = null
        override suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState) {}
        override suspend fun deleteWorkout(id: Long) {}
        override suspend fun clearAllHistory() {}
    }

    private class FakeTrainingPlanRepository : TrainingPlanRepository {
        val workoutsFlow = MutableStateFlow<List<TrainingWorkout>>(emptyList())

        override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = workoutsFlow
        override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(emptyList())
        override suspend fun getWorkoutById(id: Long): TrainingWorkout? = null
        override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long = 0L
        override suspend fun deleteCustomWorkout(id: Long) {}
        override suspend fun clearAllCustomWorkouts() {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        val programStartDateFlow = MutableStateFlow<LocalDate?>(null)
        val progressiveWeeksExtensionFlow = MutableStateFlow(0)

        override val programStartDate: Flow<LocalDate?> = programStartDateFlow
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = MutableStateFlow(emptySet())
        override val distanceUnit: Flow<DistanceUnit> = MutableStateFlow(DistanceUnit.MILES)
        override val progressiveWeeksExtension: Flow<Int> = progressiveWeeksExtensionFlow

        override suspend fun setProgramStartDate(date: LocalDate) {
            programStartDateFlow.value = date
        }

        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {}
        override suspend fun setDistanceUnit(unit: DistanceUnit) {}
        override suspend fun setProgressiveWeeksExtension(weeks: Int) {
            progressiveWeeksExtensionFlow.value = weeks
        }
    }
}
