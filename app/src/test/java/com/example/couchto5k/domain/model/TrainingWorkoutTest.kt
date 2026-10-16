package com.example.couchto5k.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingWorkoutTest {

    @Test
    fun `test workout summary calculations`() {
        val intervals = listOf(
            WorkoutInterval(0, IntervalType.WARMUP, 300),
            WorkoutInterval(1, IntervalType.RUN, 60),
            WorkoutInterval(2, IntervalType.WALK, 90),
            WorkoutInterval(3, IntervalType.RUN, 120),
            WorkoutInterval(4, IntervalType.COOLDOWN, 300)
        )

        val workout = TrainingWorkout(
            id = 1,
            week = 1,
            workoutNumber = 1,
            title = "Test Workout",
            intervals = intervals
        )

        assertEquals("Total duration should be sum of all intervals", 870, workout.totalPlannedDurationSeconds)
        assertEquals("Total running time should be sum of RUN intervals", 180, workout.totalPlannedRunningSeconds)
        assertEquals("Total walking time should be sum of WALK intervals", 90, workout.totalPlannedWalkingSeconds)
        assertEquals("Longest run should be the max RUN interval duration", 120, workout.longestPlannedRunIntervalSeconds)
    }
}
