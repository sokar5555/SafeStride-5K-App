package com.example.couchto5k.data.repository

import com.example.couchto5k.data.local.dao.CustomWorkoutDao
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutWithIntervals
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.engine.WorkoutEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultTrainingPlanRepository @Inject constructor(
    private val customWorkoutDao: CustomWorkoutDao,
) : TrainingPlanRepository {

    constructor() : this(
        object : CustomWorkoutDao {
            override suspend fun insertCustomWorkout(workout: CustomWorkoutEntity): Long = -1L
            override suspend fun insertCustomWorkoutIntervals(intervals: List<CustomWorkoutIntervalEntity>) {}
            override fun getAllCustomWorkouts(): Flow<List<CustomWorkoutWithIntervals>> = flowOf(emptyList())
            override suspend fun getCustomWorkoutById(id: Long): CustomWorkoutWithIntervals? = null
            override suspend fun deleteCustomWorkout(id: Long) {}
            override suspend fun deleteAllCustomWorkouts() {}
        },
    )

    override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = flowOf(workouts)

    override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> {
        return customWorkoutDao.getAllCustomWorkouts().map { list ->
            list.map { it.toTrainingWorkout(CUSTOM_WORKOUT_OFFSET) }
        }
    }

    override suspend fun getWorkoutById(id: Long): TrainingWorkout? {
        return when {
            id == WorkoutEngine.FREE_RUN_WORKOUT_ID -> WorkoutEngine.FREE_RUN_WORKOUT
            id >= CUSTOM_WORKOUT_OFFSET -> {
                val dbId = id - CUSTOM_WORKOUT_OFFSET
                customWorkoutDao.getCustomWorkoutById(dbId)?.toTrainingWorkout(CUSTOM_WORKOUT_OFFSET)
            }
            else -> workouts.find { (workoutId) -> workoutId == id } ?: generateProgressiveWorkoutById(id)
        }
    }

    override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long {
        val customWorkoutEntity = CustomWorkoutEntity(title = title)
        val workoutId = customWorkoutDao.insertCustomWorkout(customWorkoutEntity)

        val intervalEntities = intervals.mapIndexed { index, (_, type, durationSeconds) ->
            CustomWorkoutIntervalEntity(
                customWorkoutId = workoutId,
                sequence = index,
                intervalType = type,
                durationSeconds = durationSeconds,
            )
        }
        customWorkoutDao.insertCustomWorkoutIntervals(intervalEntities)
        return CUSTOM_WORKOUT_OFFSET + workoutId
    }

    override suspend fun deleteCustomWorkout(id: Long) {
        if (id >= CUSTOM_WORKOUT_OFFSET) {
            val dbId = id - CUSTOM_WORKOUT_OFFSET
            customWorkoutDao.deleteCustomWorkout(dbId)
        }
    }

    override suspend fun clearAllCustomWorkouts() {
        customWorkoutDao.deleteAllCustomWorkouts()
    }

    companion object {
        const val CUSTOM_WORKOUT_OFFSET = 10000L
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

            list.add(WorkoutInterval(seq, IntervalType.COOLDOWN, COOLDOWN_DURATION))
            return list
        }

        private fun createRepeatedIntervals(repeats: Int, runSecs: Int, walkSecs: Int): List<WorkoutInterval> {
            var seq = 0
            val list = mutableListOf<WorkoutInterval>()

            list.add(WorkoutInterval(seq++, IntervalType.WARMUP, WARMUP_DURATION))

            repeat(repeats) {
                list.add(WorkoutInterval(seq++, IntervalType.RUN, runSecs))
                list.add(WorkoutInterval(seq++, IntervalType.WALK, walkSecs))
            }

            list.add(WorkoutInterval(seq, IntervalType.COOLDOWN, COOLDOWN_DURATION))
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
                        intervals = createRepeatedIntervals(8, 60, 90),
                    ),
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
                        intervals = createRepeatedIntervals(6, 90, 120),
                    ),
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
                            90 to 90, 180 to 180,
                        ),
                    ),
                )
            }

            // Week 4
            // W4D1: 5 min warmup, 3m run, 90s walk, 5m run, 2.5m walk, 3m run, 90s walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 1, title = "Week 4, Workout 1",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0),
                ),
            )
            // W4D2: same
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 2, title = "Week 4, Workout 2",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0),
                ),
            )
            // W4D3: same
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 4, workoutNumber = 3, title = "Week 4, Workout 3",
                    intervals = createIntervals(180 to 90, 300 to 150, 180 to 90, 300 to 0),
                ),
            )

            // Week 5
            // W5D1: 5m warmup, 5m run, 3m walk, 5m run, 3m walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 1, title = "Week 5, Workout 1",
                    intervals = createIntervals(300 to 180, 300 to 180, 300 to 0),
                ),
            )
            // W5D2: 5m warmup, 8m run, 5m walk, 8m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 2, title = "Week 5, Workout 2",
                    intervals = createIntervals(480 to 300, 480 to 0),
                ),
            )
            // W5D3: 5m warmup, 20m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 5, workoutNumber = 3, title = "Week 5, Workout 3",
                    intervals = createIntervals(1200 to 0),
                ),
            )

            // Week 6
            // W6D1: 5m warmup, 5m run, 3m walk, 8m run, 3m walk, 5m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 1, title = "Week 6, Workout 1",
                    intervals = createIntervals(300 to 180, 480 to 180, 300 to 0),
                ),
            )
            // W6D2: 5m warmup, 10m run, 3m walk, 10m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 2, title = "Week 6, Workout 2",
                    intervals = createIntervals(600 to 180, 600 to 0),
                ),
            )
            // W6D3: 5m warmup, 22m run, 5m cooldown.
            list.add(
                TrainingWorkout(
                    id = idCounter++, week = 6, workoutNumber = 3, title = "Week 6, Workout 3",
                    intervals = createIntervals(1320 to 0),
                ),
            )

            // Week 7: 5m warmup, 25m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 7, workoutNumber = i, title = "Week 7, Workout $i",
                        intervals = createIntervals(1500 to 0),
                    ),
                )
            }

            // Week 8: 5m warmup, 28m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 8, workoutNumber = i, title = "Week 8, Workout $i",
                        intervals = createIntervals(1680 to 0),
                    ),
                )
            }

            // Week 9: 5m warmup, 30m run, 5m cooldown. (3 times)
            for (i in 1..3) {
                list.add(
                    TrainingWorkout(
                        id = idCounter++, week = 9, workoutNumber = i, title = "Week 9, Workout $i",
                        intervals = createIntervals(1800 to 0),
                    ),
                )
            }

            list
        }

        /**
         * Generates progressive running workouts for weeks beyond Week 9 (5K) using the +10% weekly
         * volume increase rule.
         *
         * Base Week 9 running volume = 3 * 30 min = 90 minutes (5400 seconds).
         * Each week w > 9 multiplies the previous week's total running volume by 1.10 (+10%).
         * Workouts 1 & 2 are shorter midweek runs (~30% of weekly volume each), and Workout 3 is the
         * designated Long Run (~40% of weekly volume).
         */
        fun generateProgressiveWeeks(beyond5kWeeks: Int): List<TrainingWorkout> {
            if (beyond5kWeeks <= 0) return emptyList()
            val progressiveList = mutableListOf<TrainingWorkout>()
            var weeklyRunVolumeSeconds = 90 * 60.0 // Week 9 base: 90 minutes total running

            for (offset in 1..beyond5kWeeks) {
                val weekNumber = 9 + offset
                weeklyRunVolumeSeconds *= 1.10 // +10% weekly running volume increase

                // Distribute weekly volume across 2 shorter midweek runs (30% each) and 1 Long Run (40%)
                val midweekRunSeconds = ((weeklyRunVolumeSeconds * 0.30) / 60.0).toInt() * 60
                val longRunSeconds = ((weeklyRunVolumeSeconds * 0.40) / 60.0).toInt() * 60

                val baseId = ((weekNumber - 1) * 3).toLong()

                progressiveList.add(
                    TrainingWorkout(
                        id = baseId + 1L,
                        week = weekNumber,
                        workoutNumber = 1,
                        title = "Week $weekNumber, Workout 1 (Midweek Run)",
                        intervals = createIntervals(midweekRunSeconds to 0),
                    ),
                )
                progressiveList.add(
                    TrainingWorkout(
                        id = baseId + 2L,
                        week = weekNumber,
                        workoutNumber = 2,
                        title = "Week $weekNumber, Workout 2 (Midweek Run)",
                        intervals = createIntervals(midweekRunSeconds to 0),
                    ),
                )
                progressiveList.add(
                    TrainingWorkout(
                        id = baseId + 3L,
                        week = weekNumber,
                        workoutNumber = 3,
                        title = "Week $weekNumber, Workout 3 (Long Run)",
                        intervals = createIntervals(longRunSeconds to 0),
                    ),
                )
            }
            return progressiveList
        }

        fun generateProgressiveWorkoutById(id: Long): TrainingWorkout? {
            if (id !in (28L until CUSTOM_WORKOUT_OFFSET)) return null
            val weekNumber = ((id - 1L) / 3L).toInt() + 1
            val beyondWeeks = weekNumber - 9
            if (beyondWeeks !in 1..52) return null
            return generateProgressiveWeeks(beyondWeeks).find { (workoutId) -> workoutId == id }
        }
    }
}
