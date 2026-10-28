package com.example.couchto5k.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval

data class CustomWorkoutWithIntervals(
    @Embedded val customWorkout: CustomWorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customWorkoutId",
    )
    val intervals: List<CustomWorkoutIntervalEntity>,
) {
    fun toTrainingWorkout(offset: Long = 10000L): TrainingWorkout {
        return TrainingWorkout(
            id = offset + customWorkout.id,
            week = 0,
            workoutNumber = 0,
            title = customWorkout.title,
            intervals = intervals.sortedBy { it.sequence }.map {
                WorkoutInterval(
                    sequence = it.sequence,
                    type = it.intervalType,
                    durationSeconds = it.durationSeconds,
                )
            },
        )
    }
}
