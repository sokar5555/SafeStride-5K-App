package com.example.couchto5k.domain.model

data class WorkoutInterval(
    val sequence: Int,
    val type: IntervalType,
    val durationSeconds: Int
)
