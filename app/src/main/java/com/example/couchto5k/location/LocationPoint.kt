package com.example.couchto5k.location

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float,
    val timestamp: Long
)
