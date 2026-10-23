package com.example.couchto5k.location

import android.location.Location

class LocationFilter(
    private val minAccuracyMeters: Float = 30f,
    private val maxSpeedMetersPerSecond: Double = 12.0, // ~27 mph (filters out driving/teleportation)
    private val minDistanceMeters: Double = 2.0, // Filters out stationary GPS drift
) {
    private var lastAcceptedPoint: LocationPoint? = null

    fun processLocation(point: LocationPoint): Double {
        // 1. Filter out poor accuracy fixes
        if (point.accuracyMeters > minAccuracyMeters) {
            return 0.0
        }

        val lastPoint = lastAcceptedPoint
        if (lastPoint == null) {
            lastAcceptedPoint = point
            return 0.0
        }

        // 2. Calculate distance between previous and new point
        val results = FloatArray(1)
        Location.distanceBetween(
            lastPoint.latitude,
            lastPoint.longitude,
            point.latitude,
            point.longitude,
            results,
        )

        val distanceMeters = results[0].toDouble()

        // 3. Filter out stationary noise
        if (distanceMeters < minDistanceMeters) {
            return 0.0
        }

        // 4. Calculate speed to filter out impossible speed jumps (e.g. driving/GPS teleport)
        val timeDiffSeconds = (point.timestamp - lastPoint.timestamp) / 1000.0
        if (timeDiffSeconds > 0) {
            val calculatedSpeed = distanceMeters / timeDiffSeconds
            if (calculatedSpeed > maxSpeedMetersPerSecond) {
                return 0.0
            }
        }

        // Point is valid
        lastAcceptedPoint = point
        return distanceMeters
    }

    fun reset() {
        lastAcceptedPoint = null
    }
}
