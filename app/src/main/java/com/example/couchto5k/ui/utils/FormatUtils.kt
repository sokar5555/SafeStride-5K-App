package com.example.couchto5k.ui.utils

import com.example.couchto5k.domain.model.DistanceUnit
import java.util.Locale

fun formatDistance(distanceMeters: Double, unit: DistanceUnit = DistanceUnit.MILES): String {
    return when (unit) {
        DistanceUnit.MILES -> {
            val miles = distanceMeters / 1609.344
            String.format(Locale.getDefault(), "%.2f mi", miles)
        }
        DistanceUnit.KILOMETERS -> {
            val km = distanceMeters / 1000.0
            String.format(Locale.getDefault(), "%.2f km", km)
        }
    }
}

fun formatPace(distanceMeters: Double, durationSeconds: Int, unit: DistanceUnit = DistanceUnit.MILES): String {
    if (distanceMeters <= 0.001 || durationSeconds <= 0) return "--:--"
    return when (unit) {
        DistanceUnit.MILES -> {
            val miles = distanceMeters / 1609.344
            val paceSecondsPerMile = (durationSeconds / miles).toInt()
            val minutes = paceSecondsPerMile / 60
            val seconds = paceSecondsPerMile % 60
            String.format(Locale.getDefault(), "%d:%02d /mi", minutes, seconds)
        }
        DistanceUnit.KILOMETERS -> {
            val km = distanceMeters / 1000.0
            val paceSecondsPerKm = (durationSeconds / km).toInt()
            val minutes = paceSecondsPerKm / 60
            val seconds = paceSecondsPerKm % 60
            String.format(Locale.getDefault(), "%d:%02d /km", minutes, seconds)
        }
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
