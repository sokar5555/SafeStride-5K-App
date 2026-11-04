package com.example.couchto5k.domain.usecase

import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
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
import java.time.LocalDateTime
import java.time.ZoneId

class GetStatsUseCaseTest {
    private lateinit var fakeHistoryRepository: FakeHistoryRepository
    private lateinit var fakeTrainingPlanRepository: FakeTrainingPlanRepository
    private lateinit var getStatsUseCase: GetStatsUseCase

    @Before
    fun setUp() {
        fakeHistoryRepository = FakeHistoryRepository()
        fakeTrainingPlanRepository = FakeTrainingPlanRepository()
        getStatsUseCase = GetStatsUseCase(fakeHistoryRepository, fakeTrainingPlanRepository)
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
            distance = 5000.0,
            duration = 1800,
            runTime = 1200,
            walkTime = 600,
            longestRun = 600,
            localDateTime = localDateTime
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
        val morningCount = result.timeOfDayDistribution.find { it.timeOfDay == TimeOfDay.MORNING }
        assertNotNull(morningCount)
        assertEquals(1, morningCount!!.count)
        assertEquals(1.0f, morningCount.percentage, 0.01f)
    }

    @Test
    fun `invoke with multiple workouts calculates correct aggregates and cumulative record progression`() = runTest {
        val w1 = createMockWorkoutDetails(distance = 1000.0, duration = 600, runTime = 300, walkTime = 300, longestRun = 60, localDateTime = LocalDateTime.of(2023, 10, 1, 8, 0)) // Morning
        val w2 = createMockWorkoutDetails(distance = 2000.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, localDateTime = LocalDateTime.of(2023, 10, 2, 14, 0)) // Afternoon
        val w3 = createMockWorkoutDetails(distance = 3000.0, duration = 1800, runTime = 1200, walkTime = 600, longestRun = 180, localDateTime = LocalDateTime.of(2023, 10, 3, 18, 0)) // Evening
        val w4 = createMockWorkoutDetails(distance = 2500.0, duration = 1500, runTime = 1000, walkTime = 500, longestRun = 120, localDateTime = LocalDateTime.of(2023, 10, 4, 23, 0)) // Night
        val w5 = createMockWorkoutDetails(distance = 5000.0, duration = 2500, runTime = 2000, walkTime = 500, longestRun = 300, localDateTime = LocalDateTime.of(2023, 10, 5, 9, 0)) // Morning

        val history = listOf(w1, w2, w3, w4, w5)
        val trainingWorkouts = List(27) { i -> TrainingWorkout(i.toLong(), i/3 + 1, (i%3)+1, "Workout $i", emptyList()) }

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
        val morningSlot = result.timeOfDayDistribution.find { it.timeOfDay == TimeOfDay.MORNING }
        val afternoonSlot = result.timeOfDayDistribution.find { it.timeOfDay == TimeOfDay.AFTERNOON }
        val eveningSlot = result.timeOfDayDistribution.find { it.timeOfDay == TimeOfDay.EVENING }
        val nightSlot = result.timeOfDayDistribution.find { it.timeOfDay == TimeOfDay.NIGHT }

        assertEquals(2, morningSlot?.count)
        assertEquals(1, afternoonSlot?.count)
        assertEquals(1, eveningSlot?.count)
        assertEquals(1, nightSlot?.count)

        // Program Progress
        assertEquals(27, result.programProgress.totalProgramWorkoutsCount)
        assertEquals(5.0f / 27.0f, result.programProgress.completionPercentage, 0.01f)
    }

    private fun createMockWorkoutDetails(
        distance: Double,
        duration: Int,
        runTime: Int,
        walkTime: Int,
        longestRun: Int,
        localDateTime: LocalDateTime
    ): CompletedWorkoutDetails {
        val startedAt = localDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val completedAt = startedAt.plusSeconds(duration.toLong())
        val completedWorkout = CompletedWorkout(
            id = 1L,
            trainingWorkoutId = 1L,
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
            status = WorkoutEngineStatus.COMPLETED
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
}
