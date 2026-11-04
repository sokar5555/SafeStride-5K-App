package com.example.couchto5k.domain.model

data class UserStats(
    val completedWorkoutsCount: Int,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Int,
    val totalRunningSeconds: Int,
    val totalWalkingSeconds: Int,
    val aggregateAveragePaceSecondsPerKm: Double,
    val longestWorkoutDurationSeconds: Int,
)
