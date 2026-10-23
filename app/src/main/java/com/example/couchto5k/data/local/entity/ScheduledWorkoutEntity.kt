package com.example.couchto5k.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "scheduled_workouts")
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainingWorkoutId: Long,
    val scheduledDate: LocalDate,
    val status: String
)
