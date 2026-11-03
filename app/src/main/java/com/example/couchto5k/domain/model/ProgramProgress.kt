package com.example.couchto5k.domain.model

data class ProgramProgress(
    val completedWorkoutsCount: Int,
    val totalProgramWorkoutsCount: Int, // 27 workouts for standard 9-week program
    val currentWeekNumber: Int,
    val completionPercentage: Float, // e.g. 0.33f
)
