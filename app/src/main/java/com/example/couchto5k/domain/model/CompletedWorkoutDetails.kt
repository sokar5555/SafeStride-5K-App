package com.example.couchto5k.domain.model

data class CompletedWorkoutDetails(
    val workout: CompletedWorkout,
    val intervals: List<CompletedInterval>
)
