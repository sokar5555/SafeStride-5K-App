package com.example.couchto5k.engine

import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.location.LocationPoint
import java.time.Instant

enum class WorkoutEngineStatus {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED
}

data class ActiveWorkoutState(
    val workoutId: Long = -1L,
    val currentIntervalIndex: Int = 0,
    val currentInterval: WorkoutInterval? = null,
    val intervalRemainingMillis: Long = 0L,
    val totalElapsedMillis: Long = 0L,
    val isPaused: Boolean = true,
    val distanceMeters: Double = 0.0,
    val startedAt: Instant = Instant.MIN,
    val status: WorkoutEngineStatus = WorkoutEngineStatus.NOT_STARTED,
    val routePoints: List<LocationPoint> = emptyList(),
)