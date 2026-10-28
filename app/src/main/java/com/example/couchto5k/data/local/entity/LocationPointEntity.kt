package com.example.couchto5k.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = CompletedWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["completedWorkoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["completedWorkoutId"])]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedWorkoutId: Long,
    val sequence: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float
)
