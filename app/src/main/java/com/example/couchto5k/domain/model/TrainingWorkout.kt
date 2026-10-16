package com.example.couchto5k.domain.model

data class TrainingWorkout(
    val id: Long,
    val week: Int,
    val workoutNumber: Int,
    val title: String,
    val intervals: List<WorkoutInterval>
) {
    val totalDurationSeconds: Int
        get() = intervals.sumOf { it.durationSeconds }

    val totalRunningSeconds: Int
        get() = intervals.filter { it.type == IntervalType.RUN }.sumOf { it.durationSeconds }

    val totalWalkingSeconds: Int
        get() = intervals.filter { it.type == IntervalType.WALK }.sumOf { it.durationSeconds }

    val longestRunIntervalSeconds: Int
        get() = intervals.filter { it.type == IntervalType.RUN }.maxOfOrNull { it.durationSeconds } ?: 0
}
