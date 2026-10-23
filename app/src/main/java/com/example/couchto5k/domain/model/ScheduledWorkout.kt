package com.example.couchto5k.domain.model

import java.time.LocalDate

data class ScheduledWorkout(
    val id: Long,
    val trainingWorkoutId: Long,
    val scheduledDate: LocalDate,
    val status: WorkoutStatus,
    val title: String = "Week ${((trainingWorkoutId - 1) / 3) + 1}, Workout ${((trainingWorkoutId - 1) % 3) + 1}",
)
