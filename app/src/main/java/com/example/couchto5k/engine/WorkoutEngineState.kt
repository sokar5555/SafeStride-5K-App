package com.example.couchto5k.engine

import com.example.couchto5k.domain.model.IntervalType
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

data class OpenExerciseSegment(
    val sequence: Int,
    val type: IntervalType,
    val durationMillis: Long,
    val distanceMeters: Double,
)

data class ActiveWorkoutState(
    val workoutId: Long = -1L,
    val isFreeRun: Boolean = false,
    val currentIntervalIndex: Int = 0,
    val currentInterval: WorkoutInterval? = null,
    val intervalRemainingMillis: Long = 0L,
    val totalElapsedMillis: Long = 0L,
    val isPaused: Boolean = true,
    val distanceMeters: Double = 0.0,
    val averagePaceSecondsPerKm: Double = 0.0,
    val currentSplitDistanceMeters: Double = 0.0,
    val currentSplitDurationMillis: Long = 0L,
    val currentSplitPaceSecondsPerKm: Double = 0.0,
    val intervalDistancesMeters: Map<Int, Double> = emptyMap(),
    val openExerciseSegments: List<OpenExerciseSegment> = emptyList(),
    val startedAt: Instant = Instant.MIN,
    val status: WorkoutEngineStatus = WorkoutEngineStatus.NOT_STARTED,
    val routePoints: List<LocationPoint> = emptyList(),
)