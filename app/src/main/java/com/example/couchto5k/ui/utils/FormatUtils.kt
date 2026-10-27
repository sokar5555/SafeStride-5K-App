package com.example.couchto5k.ui.utils

import java.util.Locale

fun formatDistanceMiles(distanceMeters: Double): String {
    val miles = distanceMeters / 1609.344
    return String.format(Locale.getDefault(), "%.2f mi", miles)
}

fun formatPace(distanceMeters: Double, durationSeconds: Int): String {
    val miles = distanceMeters / 1609.344
    if ((miles <= 0.001) || (durationSeconds <= 0)) return "--:--"
    val paceSecondsPerMile = (durationSeconds / miles).toInt()
    val minutes = paceSecondsPerMile / 60
    val seconds = paceSecondsPerMile % 60
    return String.format(Locale.getDefault(), "%d:%02d /mi", minutes, seconds)
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
