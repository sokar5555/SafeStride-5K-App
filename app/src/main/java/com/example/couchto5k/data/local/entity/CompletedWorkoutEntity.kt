package com.example.couchto5k.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.couchto5k.engine.WorkoutEngineStatus
import java.time.Instant

@Entity(tableName = "completed_workouts")
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainingWorkoutId: Long,
    val title: String, // Keep title for easier UI display
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Int,
    val runningSeconds: Int,
    val walkingSeconds: Int,
    val distanceMeters: Double,
    val averagePaceSecondsPerKm: Double,
    val longestCompletedRunSeconds: Int,
    val status: WorkoutEngineStatus,
)
