package com.example.couchto5k.domain.model

import com.example.couchto5k.location.LocationPoint

data class CompletedWorkoutDetails(
    val workout: CompletedWorkout,
    val intervals: List<CompletedInterval>,
    val routePoints: List<LocationPoint> = emptyList()
)
