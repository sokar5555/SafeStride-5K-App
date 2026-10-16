package com.example.couchto5k.data.repository

import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import javax.inject.Inject

class TrainingPlanRepositoryImpl @Inject constructor() : TrainingPlanRepository {

    private val workouts: List<TrainingWorkout> by lazy {
        generateTrainingPlan()
    }

    override suspend fun getAllWorkouts(): List<TrainingWorkout> {
        return workouts
    }

    override suspend fun getWorkoutById(id: Long): TrainingWorkout? {
        return workouts.find { it.id == id }
    }

    private fun generateTrainingPlan(): List<TrainingWorkout> {
        val plan = mutableListOf<TrainingWorkout>()
        var idCounter = 1L

        fun createIntervals(vararg pairs: Pair<IntervalType, Int>): List<WorkoutInterval> {
            val list = mutableListOf<WorkoutInterval>()
            var seq = 1
            list.add(WorkoutInterval(seq++, IntervalType.WARMUP, 5 * 60))
            for (p in pairs) {
                list.add(WorkoutInterval(seq++, p.first, p.second))
            }
            list.add(WorkoutInterval(seq, IntervalType.COOLDOWN, 5 * 60))
            return list
        }

        fun repeatIntervals(times: Int, vararg pairs: Pair<IntervalType, Int>): List<WorkoutInterval> {
            val list = mutableListOf<WorkoutInterval>()
            var seq = 1
            list.add(WorkoutInterval(seq++, IntervalType.WARMUP, 5 * 60))
            repeat(times) {
                for (p in pairs) {
                    list.add(WorkoutInterval(seq++, p.first, p.second))
                }
            }
            list.add(WorkoutInterval(seq, IntervalType.COOLDOWN, 5 * 60))
            return list
        }

        // Week 1
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 1, workoutNumber = w, title = "W1 D$w",
                    intervals = repeatIntervals(8, IntervalType.RUN to 60, IntervalType.WALK to 90)
                )
            )
        }

        // Week 2
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 2, workoutNumber = w, title = "W2 D$w",
                    intervals = repeatIntervals(6, IntervalType.RUN to 90, IntervalType.WALK to 120)
                )
            )
        }

        // Week 3
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 3, workoutNumber = w, title = "W3 D$w",
                    intervals = repeatIntervals(2,
                        IntervalType.RUN to 90, IntervalType.WALK to 90,
                        IntervalType.RUN to 180, IntervalType.WALK to 180
                    )
                )
            )
        }

        // Week 4
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = w, title = "W4 D$w",
                    intervals = repeatIntervals(1,
                        IntervalType.RUN to 180, IntervalType.WALK to 90,
                        IntervalType.RUN to 300, IntervalType.WALK to 150,
                        IntervalType.RUN to 180, IntervalType.WALK to 90,
                        IntervalType.RUN to 300
                    )
                )
            )
        }

        // Week 5
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 5, workoutNumber = 1, title = "W5 D1",
                intervals = repeatIntervals(1,
                    IntervalType.RUN to 300, IntervalType.WALK to 180,
                    IntervalType.RUN to 300, IntervalType.WALK to 180,
                    IntervalType.RUN to 300
                )
            )
        )
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 5, workoutNumber = 2, title = "W5 D2",
                intervals = repeatIntervals(1,
                    IntervalType.RUN to 480, IntervalType.WALK to 300,
                    IntervalType.RUN to 480
                )
            )
        )
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 5, workoutNumber = 3, title = "W5 D3",
                intervals = createIntervals(IntervalType.RUN to 1200)
            )
        )

        // Week 6
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 6, workoutNumber = 1, title = "W6 D1",
                intervals = repeatIntervals(1,
                    IntervalType.RUN to 300, IntervalType.WALK to 180,
                    IntervalType.RUN to 480, IntervalType.WALK to 180,
                    IntervalType.RUN to 300
                )
            )
        )
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 6, workoutNumber = 2, title = "W6 D2",
                intervals = repeatIntervals(1,
                    IntervalType.RUN to 600, IntervalType.WALK to 180,
                    IntervalType.RUN to 600
                )
            )
        )
        plan.add(
            TrainingWorkout(
                id = idCounter++, week = 6, workoutNumber = 3, title = "W6 D3",
                intervals = createIntervals(IntervalType.RUN to 1500)
            )
        )

        // Week 7
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 7, workoutNumber = w, title = "W7 D$w",
                    intervals = createIntervals(IntervalType.RUN to 1500)
                )
            )
        }

        // Week 8
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 8, workoutNumber = w, title = "W8 D$w",
                    intervals = createIntervals(IntervalType.RUN to 1680) // 28 minutes
                )
            )
        }

        // Week 9
        for (w in 1..3) {
            plan.add(
                TrainingWorkout(
                    id = idCounter++, week = 9, workoutNumber = w, title = "W9 D$w",
                    intervals = createIntervals(IntervalType.RUN to 1800) // 30 minutes
                )
            )
        }

        return plan
    }
}
