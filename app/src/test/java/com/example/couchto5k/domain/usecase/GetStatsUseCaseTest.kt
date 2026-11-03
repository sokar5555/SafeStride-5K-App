package com.example.couchto5k.domain.usecase

import com.example.couchto5k.domain.model.CompletedInterval
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class GetStatsUseCaseTest {
    private lateinit var workoutHistoryRepository: WorkoutHistoryRepository
    private lateinit var trainingPlanRepository: TrainingPlanRepository
    private lateinit var getStatsUseCase: GetStatsUseCase

    @Before
    fun setUp() {
        workoutHistoryRepository = mock(WorkoutHistoryRepository::class.java)
        trainingPlanRepository = mock(TrainingPlanRepository::class.java)
        getStatsUseCase = GetStatsUseCase(workoutHistoryRepository, trainingPlanRepository)
    }

    @Test
    fun `invoke with empty history returns null`() = runTest {
        `when`(workoutHistoryRepository.getHistory()).thenReturn(flowOf(emptyList()))
        `when`(trainingPlanRepository.getAllWorkouts()).thenReturn(flowOf(emptyList()))

        val result = getStatsUseCase.invoke().first()
        assertNull(result)
    }

    @Test
    fun `invoke with single workout calculates correct stats`() = runTest {
        val completedAt = Instant.parse("2023-10-01T10:00:00Z")
        val workoutDetails = createMockWorkoutDetails(
            distance = 5000.0,
            duration = 1800,
            runTime = 1200,
            walkTime = 600,
            longestRun = 600,
            completedAt = completedAt
        )
        val trainingWorkout = TrainingWorkout(1, 1, 1, "Workout 1", emptyList())

        `when`(workoutHistoryRepository.getHistory()).thenReturn(flowOf(listOf(workoutDetails)))
        `when`(trainingPlanRepository.getAllWorkouts()).thenReturn(flowOf(listOf(trainingWorkout)))

        val result = getStatsUseCase.invoke().first()
        assertNotNull(result)

        result!!
        assertEquals(1, result.userStats.completedWorkoutsCount)
        assertEquals(5000.0, result.userStats.totalDistanceMeters, 0.01)
        assertEquals(1800, result.userStats.totalDurationSeconds)
        assertEquals(1200, result.userStats.totalRunningSeconds)
        assertEquals(600, result.userStats.totalWalkingSeconds)
        assertEquals(360.0, result.userStats.aggregateAveragePaceSecondsPerKm, 0.01) // 1800 / 5km
        assertEquals(1800, result.userStats.longestWorkoutDurationSeconds)

        assertEquals(600, result.runningProgression.startingLongestRunSeconds)
        assertEquals(600, result.runningProgression.currentLongestRunSeconds)
        assertEquals(0, result.runningProgression.improvementSeconds)
    }

    @Test
    fun `invoke with multiple workouts calculates correct aggregates and cumulative record progression`() = runTest {
        val w1 = createMockWorkoutDetails(distance = 1000.0, duration = 600, runTime = 300, walkTime = 300, longestRun = 60, completedAt = Instant.parse("2023-10-01T10:00:00Z")) // +1 min
        val w2 = createMockWorkoutDetails(distance = 2000.0, duration = 1200, runTime = 600, walkTime = 600, longestRun = 60, completedAt = Instant.parse("2023-10-02T10:00:00Z")) // no improvement
        val w3 = createMockWorkoutDetails(distance = 3000.0, duration = 1800, runTime = 1200, walkTime = 600, longestRun = 180, completedAt = Instant.parse("2023-10-03T10:00:00Z")) // +3 min
        val w4 = createMockWorkoutDetails(distance = 2500.0, duration = 1500, runTime = 1000, walkTime = 500, longestRun = 120, completedAt = Instant.parse("2023-10-04T10:00:00Z")) // no improvement, run less
        val w5 = createMockWorkoutDetails(distance = 5000.0, duration = 2500, runTime = 2000, walkTime = 500, longestRun = 300, completedAt = Instant.parse("2023-10-05T10:00:00Z")) // +5 min

        val history = listOf(w1, w2, w3, w4, w5)

        val trainingWorkouts = List(27) { i -> TrainingWorkout(i.toLong(), i/3 + 1, (i%3)+1, "Workout $i", emptyList()) }

        `when`(workoutHistoryRepository.getHistory()).thenReturn(flowOf(history))
        `when`(trainingPlanRepository.getAllWorkouts()).thenReturn(flowOf(trainingWorkouts))

        val result = getStatsUseCase.invoke().first()
        assertNotNull(result)
        result!!

        // Aggregates
        assertEquals(5, result.userStats.completedWorkoutsCount)
        assertEquals(13500.0, result.userStats.totalDistanceMeters, 0.01) // 1k+2k+3k+2.5k+5k
        assertEquals(7600, result.userStats.totalDurationSeconds) // 600+1200+1800+1500+2500

        val expectedPace = 7600.0 / (13500.0 / 1000.0) // 7600 / 13.5
        assertEquals(expectedPace, result.userStats.aggregateAveragePaceSecondsPerKm, 0.01)

        // Progression
        assertEquals(60, result.runningProgression.startingLongestRunSeconds)
        assertEquals(300, result.runningProgression.currentLongestRunSeconds)
        assertEquals(240, result.runningProgression.improvementSeconds) // 300 - 60

        // Progression series: 60, 60, 180, 180, 300
        assertEquals(5, result.runningProgression.recordHistory.size)
        assertEquals(60, result.runningProgression.recordHistory[0].longestRunSeconds)
        assertEquals(60, result.runningProgression.recordHistory[1].longestRunSeconds)
        assertEquals(180, result.runningProgression.recordHistory[2].longestRunSeconds)
        assertEquals(180, result.runningProgression.recordHistory[3].longestRunSeconds)
        assertEquals(300, result.runningProgression.recordHistory[4].longestRunSeconds)

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
        completedAt: Instant
    ): CompletedWorkoutDetails {
        val completedWorkout = CompletedWorkout(
            id = 1L,
            trainingWorkoutId = 1L,
            scheduledWorkoutId = null,
            title = "Title",
            startedAt = completedAt.minusSeconds(duration.toLong()),
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
}
