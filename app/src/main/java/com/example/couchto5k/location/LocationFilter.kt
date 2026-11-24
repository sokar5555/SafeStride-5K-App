package com.example.couchto5k.location

import android.location.Location

class LocationFilter(
    private val minAccuracyMeters: Float = 30f,
    private val maxSpeedMetersPerSecond: Double = 12.0, // ~27 mph (filters out driving/teleportation)
    private val minDistanceMeters: Double = 2.0, // Filters out stationary GPS drift
) {
    private var lastAcceptedPoint: LocationPoint? = null

    fun processLocation(point: LocationPoint): Pair<Double, LocationPoint?> {
        // 1. Filter out poor or invalid accuracy fixes
        if ((point.accuracyMeters <= 0f) || (point.accuracyMeters > minAccuracyMeters)) {
            return 0.0 to null
        }

        val lastPoint = lastAcceptedPoint
        if (lastPoint == null) {
            lastAcceptedPoint = point
            return 0.0 to point
        }

        // 2. Reject stale, duplicate, or out-of-order timestamps
        val timeDiffMillis = point.timestamp - lastPoint.timestamp
        if (timeDiffMillis <= 0L) {
            return 0.0 to null
        }

        // 3. Calculate distance between previous and new point
        val results = FloatArray(1)
        Location.distanceBetween(
            lastPoint.latitude,
            lastPoint.longitude,
            point.latitude,
            point.longitude,
            results,
        )

        val distanceMeters = results[0].toDouble()

        // 4. Filter out stationary noise
        if (distanceMeters < minDistanceMeters) {
            return 0.0 to null
        }

        // 5. Calculate speed to filter out impossible speed jumps (e.g. driving/GPS teleport)
        val timeDiffSeconds = timeDiffMillis / 1000.0
        val calculatedSpeed = distanceMeters / timeDiffSeconds
        if (calculatedSpeed > maxSpeedMetersPerSecond) {
            return 0.0 to null
        }

        // Point is valid
        lastAcceptedPoint = point
        return distanceMeters to point
    }

    fun reset() {
        lastAcceptedPoint = null
    }
}
