package com.example.couchto5k.location

import android.location.Location

class LocationFilter {

    private var lastAcceptedLocation: LocationPoint? = null

    /**
     * Filters raw locations, returns calculated distance from the previous accepted point (in meters),
     * and updates internal state if the location is valid.
     * Returns 0.0 if the point is filtered out as noise or is the first point.
     */
    fun processLocation(point: LocationPoint): Double {
        // 1. Accuracy Check: Ignore poor accuracy > 25m
        if (point.accuracyMeters > 25f) return 0.0

        val last = lastAcceptedLocation
        if (last == null) {
            lastAcceptedLocation = point
            return 0.0
        }

        // 2. Jitter / Duplicate Check
        val timeDiffSeconds = (point.timestamp - last.timestamp) / 1000.0
        if (timeDiffSeconds <= 0) return 0.0 // Duplicate timestamp or backwards in time

        val distance = calculateDistance(last, point)

        // 3. Stationary Jitter: Distance < 1m
        if (distance < 1.0) return 0.0

        // 4. Impossible Speed Jumps: Speed > 15 m/s (~33.5 mph, way too fast for a human running)
        val speedMetersPerSecond = distance / timeDiffSeconds
        if (speedMetersPerSecond > 15.0) return 0.0

        // Point is accepted
        lastAcceptedLocation = point
        return distance
    }

    private fun calculateDistance(p1: LocationPoint, p2: LocationPoint): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0].toDouble()
    }

    fun reset() {
        lastAcceptedLocation = null
    }
}
