package com.example.couchto5k.data.repository

import com.example.couchto5k.domain.model.IntervalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanRepositoryTest {

    @Test
    fun `test repository contains 9 weeks of workouts`() {
        val workouts = DefaultTrainingPlanRepository.workouts
        assertEquals("There should be 27 workouts in a 9-week program (3 per week)", 27, workouts.size)

        // Check week distribution
        for (week in 1..9) {
            val weekWorkouts = workouts.filter { (_, weekNumber) -> weekNumber == week }
            assertEquals("Week $week should have exactly 3 workouts", 3, weekWorkouts.size)
        }
    }

    @Test
    fun `test week 1 workout totals`() {
        val w1d1 = DefaultTrainingPlanRepository.workouts.first { (_, week, workoutNumber) -> (week == 1) && (workoutNumber == 1) }

        // 5m warmup + 8*(1m run + 1.5m walk) + 5m cooldown = 5 + 8*2.5 + 5 = 30 minutes
        assertEquals("W1D1 total duration should be 30 minutes", 30 * 60, w1d1.totalPlannedDurationSeconds)
        assertEquals("W1D1 running duration should be 8 minutes", 8 * 60, w1d1.totalPlannedRunningSeconds)
        assertEquals("W1D1 walking duration should be 22 minutes (including warmup and cooldown)", 22 * 60, w1d1.totalPlannedWalkingSeconds)
        assertEquals("W1D1 longest run should be 60 seconds", 60, w1d1.longestPlannedRunIntervalSeconds)
    }

    @Test
    fun `test week 9 workout totals`() {
        val w9d1 = DefaultTrainingPlanRepository.workouts.first { (_, week, workoutNumber) -> (week == 9) && (workoutNumber == 1) }

        // 5m warmup + 30m run + 5m cooldown = 40 minutes
        assertEquals("W9D1 total duration should be 40 minutes", 40 * 60, w9d1.totalPlannedDurationSeconds)
        assertEquals("W9D1 longest run should be 30 minutes", 30 * 60, w9d1.longestPlannedRunIntervalSeconds)
    }

    @Test
    fun `test sequence ordering`() {
        val (_, _, _, _, intervals) = DefaultTrainingPlanRepository.workouts.first()

        // First interval should be warmup, last should be cooldown
        assertEquals(IntervalType.WARMUP, intervals.first().type)
        assertEquals(IntervalType.COOLDOWN, intervals.last().type)

        // Sequences should be strictly increasing
        for (i in 1 until intervals.size) {
            assertTrue("Sequence must increase strictly", intervals[i].sequence > intervals[i-1].sequence)
        }
    }
}
