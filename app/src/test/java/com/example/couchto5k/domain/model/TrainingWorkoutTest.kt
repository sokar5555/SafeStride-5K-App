package com.example.couchto5k.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingWorkoutTest {

    @Test
    fun `totalDurationSeconds calculates correctly`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.RUN, 60),
                WorkoutInterval(3, IntervalType.WALK, 90)
            )
        )
        assertEquals(450, workout.totalDurationSeconds)
    }

    @Test
    fun `totalRunningSeconds calculates correctly`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.RUN, 60),
                WorkoutInterval(3, IntervalType.WALK, 90),
                WorkoutInterval(4, IntervalType.RUN, 120)
            )
        )
        assertEquals(180, workout.totalRunningSeconds)
    }

    @Test
    fun `totalWalkingSeconds calculates correctly`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.WALK, 60),
                WorkoutInterval(3, IntervalType.WALK, 90)
            )
        )
        assertEquals(150, workout.totalWalkingSeconds)
    }

    @Test
    fun `longestRunIntervalSeconds calculates correctly`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.RUN, 60),
                WorkoutInterval(3, IntervalType.WALK, 90),
                WorkoutInterval(4, IntervalType.RUN, 180),
                WorkoutInterval(5, IntervalType.RUN, 120)
            )
        )
        assertEquals(180, workout.longestRunIntervalSeconds)
    }

    @Test
    fun `longestRunIntervalSeconds returns 0 when no run intervals`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.WALK, 60)
            )
        )
        assertEquals(0, workout.longestRunIntervalSeconds)
    }

    @Test
    fun `interval sequence ordering is correct`() {
        val workout = TrainingWorkout(
            id = 1, week = 1, workoutNumber = 1, title = "Test",
            intervals = listOf(
                WorkoutInterval(1, IntervalType.WARMUP, 300),
                WorkoutInterval(2, IntervalType.RUN, 60),
                WorkoutInterval(3, IntervalType.WALK, 90)
            )
        )
        val sequences = workout.intervals.map { it.sequence }
        assertEquals(listOf(1, 2, 3), sequences)
    }
}
