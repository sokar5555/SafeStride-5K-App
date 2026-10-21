package com.example.couchto5k.domain.model

import com.example.couchto5k.engine.WorkoutEngineStatus
import java.time.Instant

data class CompletedWorkout(
    val id: Long,
    val trainingWorkoutId: Long,
    val title: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Int,
    val runningSeconds: Int,
    val walkingSeconds: Int,
    val distanceMeters: Double,
    val averagePaceSecondsPerKm: Double,
    val longestCompletedRunSeconds: Int,
    val status: WorkoutEngineStatus
)
