package com.example.couchto5k.data.repository

import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class DefaultTrainingPlanRepository @Inject constructor() : TrainingPlanRepository {

    override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = flowOf(workouts)

    override suspend fun getWorkoutById(id: Long): TrainingWorkout? = workouts.find { it.id == id }

    companion object {
        private const val WARMUP_DURATION = 5 * 60
        private const val COOLDOWN_DURATION = 5 * 60

        private fun createIntervals(vararg runWalkPairs: Pair<Int, Int>): List<WorkoutInterval> {
            var seq = 0
            val list = mutableListOf<WorkoutInterval>()

            list.add(WorkoutInterval(seq++, IntervalType.WARMUP, WARMUP_DURATION))

            for ((runSecs, walkSecs) in runWalkPairs) {
                if (runSecs > 0) {
                    list.add(WorkoutInterval(seq++, IntervalType.RUN, runSecs))
                }
                if (walkSecs > 0) {
                    list.add(WorkoutInterval(seq++, IntervalType.WALK, walkSecs))
                }
            }

            list.add(WorkoutInterval(seq++, IntervalType.COOLDOWN, COOLDOWN_DURATION))
            return list
        }

        private fun createRepeatedIntervals(repeats: Int, runSecs: Int, walkSecs: Int): List<WorkoutInterval> {
            var seq = 0
            val list = mutableListOf<WorkoutInterval>()

            list.add(WorkoutInterval(seq++, IntervalType.WARMUP, WARMUP_DURATION))

            for (i in 0 until repeats) {
                list.add(WorkoutInterval(seq++, IntervalType.RUN, runSecs))
                list.add(WorkoutInterval(seq++, IntervalType.WALK, walkSecs))
            }

            list.add(WorkoutInterval(seq++, IntervalType.COOLDOWN, COOLDOWN_DURATION))
            return list
        }

        val workouts: List<TrainingWorkout> by lazy {
            var idCounter = 1L
            val list = mutableListOf<TrainingWorkout>()

            // Week 1: 5 min warmup, alternate 60 sec jog / 90 sec walk for 20 mins (8 repeats), 5 min cooldown. Total = 30m.
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++,
                        week = 1,
                        workoutNumber = i,
                        title = "Week 1, Workout $i",
                        intervals = createRepeatedIntervals(8, 60, 90)
                    )
                )
            }

            // Week 2: 5 min warmup, alternate 90 sec jog / 2 min walk for 21 mins (6 repeats), 5 min cooldown. Total = 31m.
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++,
                        week = 2,
                        workoutNumber = i,
                        title = "Week 2, Workout $i",
                        intervals = createRepeatedIntervals(6, 90, 120)
                    )
                )
            }

            // Week 3: 5 min warmup, 2 reps of (90 sec run, 90 sec walk, 3 min run, 3 min walk), 5 min cooldown. Total = 28m.
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++,
                        week = 3,
                        workoutNumber = i,
                        title = "Week 3, Workout $i",
                        intervals = createIntervals(
                            90 to 90, 180 to 180,
                            90 to 90, 180 to 180
                        )
                    )
                )
            }

            // Week 4
            // W4D1: 5 min warmup, 3m run, 90s walk, 5m run, 2.5m walk, 3m run, 90s walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 1, title = "Week 4, Workout 1",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0)
                )
            )
            // W4D2: same
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 2, title = "Week 4, Workout 2",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0)
                )
            )
            // W4D3: same
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 3, title = "Week 4, Workout 3",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0)
                )
            )

            // Week 5
            // W5D1: 5m warmup, 5m run, 3m walk, 5m run, 3m walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 1, title = "Week 5, Workout 1",
                    intervals = createIntervals(300 to 180, 300 to 180, 300 to 0)
                )
            )
            // W5D2: 5m warmup, 8m run, 5m walk, 8m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 2, title = "Week 5, Workout 2",
                    intervals = createIntervals(480 to 300, 480 to 0)
                )
            )
            // W5D3: 5m warmup, 20m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 3, title = "Week 5, Workout 3",
                    intervals = createIntervals(1200 to 0)
                )
            )

            // Week 6
            // W6D1: 5m warmup, 5m run, 3m walk, 8m run, 3m walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 1, title = "Week 6, Workout 1",
                    intervals = createIntervals(300 to 180, 480 to 180, 300 to 0)
                )
            )
            // W6D2: 5m warmup, 10m run, 3m walk, 10m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 2, title = "Week 6, Workout 2",
                    intervals = createIntervals(600 to 180, 600 to 0)
                )
            )
            // W6D3: 5m warmup, 22m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 3, title = "Week 6, Workout 3",
                    intervals = createIntervals(1320 to 0)
                )
            )

            // Week 7: 5m warmup, 25m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 7, workoutNumber = i, title = "Week 7, Workout $i",
                        intervals = createIntervals(1500 to 0)
                    )
                )
            }

            // Week 8: 5m warmup, 28m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 8, workoutNumber = i, title = "Week 8, Workout $i",
                        intervals = createIntervals(1680 to 0)
                    )
                )
            }

            // Week 9: 5m warmup, 30m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 9, workoutNumber = i, title = "Week 9, Workout $i",
                        intervals = createIntervals(1800 to 0)
                    )
                )
            }

            list
        }
    }
}
