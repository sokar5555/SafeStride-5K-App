package com.example.couchto5k.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class WorkoutWithIntervals(
    @Embedded val workout: CompletedWorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "completedWorkoutId",
    )
    val intervals: List<CompletedIntervalEntity>,
)
