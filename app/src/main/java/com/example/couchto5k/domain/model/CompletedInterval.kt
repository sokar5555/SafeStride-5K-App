package com.example.couchto5k.domain.model

data class CompletedInterval(
    val id: Long,
    val sequence: Int,
    val intervalType: IntervalType,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val completed: Boolean
)
