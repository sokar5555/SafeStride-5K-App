package com.example.couchto5k.domain.model

data class TrainingWorkout(
    val id: Long,
    val week: Int,
    val workoutNumber: Int,
    val title: String,
    val intervals: List<WorkoutInterval>
) {
    val totalPlannedDurationSeconds: Int
        get() = intervals.sumOf { it.durationSeconds }

    val totalPlannedRunningSeconds: Int
        get() = intervals.filter { it.type == IntervalType.RUN }.sumOf { it.durationSeconds }

    val totalPlannedWalkingSeconds: Int
        get() = intervals.filter { it.type != IntervalType.RUN }.sumOf { it.durationSeconds }

    val longestPlannedRunIntervalSeconds: Int
        get() = intervals.filter { it.type == IntervalType.RUN }.maxOfOrNull { it.durationSeconds } ?: 0

    val numberOfRunIntervals: Int
        get() = intervals.count { it.type == IntervalType.RUN }

    val numberOfWalkIntervals: Int
        get() = intervals.count { it.type == IntervalType.WALK }
}
