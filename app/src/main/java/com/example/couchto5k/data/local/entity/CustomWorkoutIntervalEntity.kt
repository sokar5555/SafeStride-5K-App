package com.example.couchto5k.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.couchto5k.domain.model.IntervalType

@Entity(
    tableName = "custom_workout_intervals",
    foreignKeys = [
        ForeignKey(
            entity = CustomWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["customWorkoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("customWorkoutId")],
)
data class CustomWorkoutIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customWorkoutId: Long,
    val sequence: Int,
    val intervalType: IntervalType,
    val durationSeconds: Int,
)
