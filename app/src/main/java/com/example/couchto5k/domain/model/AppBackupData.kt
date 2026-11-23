package com.example.couchto5k.domain.model

import com.example.couchto5k.engine.WorkoutEngineStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class AppBackupData(
    val schemaVersion: Int,
    val appName: String,
    val exportedAt: Instant,
    val settings: BackupSettings,
    val completedWorkouts: List<BackupCompletedWorkout>,
    val customWorkouts: List<BackupCustomWorkout>,
    val scheduledWorkouts: List<BackupScheduledWorkout>,
) {
    fun toSummary(): BackupSummary = BackupSummary(
        completedWorkoutsCount = completedWorkouts.size,
        customWorkoutsCount = customWorkouts.size,
        scheduledWorkoutsCount = scheduledWorkouts.size,
        exportedAt = exportedAt,
    )
}

data class BackupSummary(
    val completedWorkoutsCount: Int,
    val customWorkoutsCount: Int,
    val scheduledWorkoutsCount: Int,
    val exportedAt: Instant,
)

data class BackupSettings(
    val distanceUnit: DistanceUnit,
    val programStartDate: LocalDate?,
    val preferredWorkoutDays: Set<DayOfWeek>,
    val progressiveWeeksExtension: Int,
    val longRunDay: DayOfWeek,
    val voiceCuesEnabled: Boolean,
    val soundCuesEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val audioDuckingEnabled: Boolean,
    val countdownVoiceCuesEnabled: Boolean,
    val themeMode: ThemeMode,
    val onboardingComplete: Boolean,
)

data class BackupCompletedWorkout(
    val id: Long,
    val trainingWorkoutId: Long,
    val scheduledWorkoutId: Long?,
    val title: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Int,
    val runningSeconds: Int,
    val walkingSeconds: Int,
    val distanceMeters: Double,
    val averagePaceSecondsPerKm: Double,
    val longestCompletedRunSeconds: Int,
    val status: WorkoutEngineStatus,
    val intervals: List<BackupCompletedInterval>,
    val routePoints: List<BackupLocationPoint>,
)

data class BackupCompletedInterval(
    val sequence: Int,
    val intervalType: IntervalType,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val distanceMeters: Double,
    val completed: Boolean,
)

data class BackupLocationPoint(
    val sequence: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float,
)

data class BackupCustomWorkout(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val intervals: List<BackupCustomInterval>,
)

data class BackupCustomInterval(
    val sequence: Int,
    val intervalType: IntervalType,
    val durationSeconds: Int,
)

data class BackupScheduledWorkout(
    val id: Long,
    val trainingWorkoutId: Long,
    val scheduledDate: LocalDate,
    val status: String,
)
