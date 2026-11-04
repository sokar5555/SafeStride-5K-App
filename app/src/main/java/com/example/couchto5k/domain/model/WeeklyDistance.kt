package com.example.couchto5k.domain.model

data class WeeklyDistancePoint(
    val weekLabel: String, // e.g. "Week 1", "Oct 14"
    val distanceMeters: Double,
)
