package com.example.couchto5k.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import com.example.couchto5k.domain.model.IntervalType

@Entity(
    tableName = "completed_intervals",
    foreignKeys = [
        ForeignKey(
            entity = CompletedWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["completedWorkoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("completedWorkoutId")]
)
data class CompletedIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedWorkoutId: Long,
    val sequence: Int,
    val intervalType: IntervalType,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val completed: Boolean
)
