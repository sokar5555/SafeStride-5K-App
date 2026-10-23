package com.example.couchto5k.data.repository

import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.WorkoutWithIntervals
import com.example.couchto5k.domain.model.CompletedInterval
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min

class DefaultWorkoutHistoryRepository @Inject constructor(
    private val completedWorkoutDao: CompletedWorkoutDao
) : WorkoutHistoryRepository {

    override fun getHistory(): Flow<List<CompletedWorkoutDetails>> {
        return completedWorkoutDao.getAllCompletedWorkouts().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? {
        return completedWorkoutDao.getCompletedWorkoutById(id)?.toDomainModel()
    }

    override suspend fun saveCompletedWorkout(
        workout: TrainingWorkout,
        state: ActiveWorkoutState
    ) {
        val totalElapsedSeconds = (state.totalElapsedMillis / 1000).toInt()

        // Calculate actual interval durations and completion status
        var accumulatedTime = 0
        val completedIntervals = mutableListOf<CompletedIntervalEntity>()
        var longestRunSeconds = 0
        var currentContinuousRunSeconds = 0
        var runningSeconds = 0
        var walkingSeconds = 0

        for (interval in workout.intervals) {
            val remainingTimeInWorkout = totalElapsedSeconds - accumulatedTime
            val isCooldownCompleted = state.status == WorkoutEngineStatus.COMPLETED && interval.type == IntervalType.COOLDOWN

            val actualDuration = if (isCooldownCompleted) {
                interval.durationSeconds
            } else if (accumulatedTime >= totalElapsedSeconds) {
                0
            } else {
                min(interval.durationSeconds, maxOf(0, remainingTimeInWorkout))
            }

            val isCompleted = actualDuration >= interval.durationSeconds

            completedIntervals.add(
                CompletedIntervalEntity(
                    completedWorkoutId = 0, // Assigned later
                    sequence = interval.sequence,
                    intervalType = interval.type,
                    plannedDurationSeconds = interval.durationSeconds,
                    actualDurationSeconds = actualDuration,
                    completed = isCompleted
                )
            )

            if (interval.type == IntervalType.RUN) {
                runningSeconds += actualDuration
                currentContinuousRunSeconds += actualDuration
                if (currentContinuousRunSeconds > longestRunSeconds) {
                    longestRunSeconds = currentContinuousRunSeconds
                }
                if (!isCompleted) {
                    currentContinuousRunSeconds = 0 // Break streak if not fully completed?
                }
            } else {
                currentContinuousRunSeconds = 0
                walkingSeconds += actualDuration
            }

            accumulatedTime += interval.durationSeconds
        }

        val totalWorkoutDurationSeconds = maxOf(totalElapsedSeconds, completedIntervals.sumOf { it.actualDurationSeconds })

        val entity = CompletedWorkoutEntity(
            trainingWorkoutId = workout.id,
            title = workout.title,
            startedAt = state.startedAt,
            completedAt = Instant.now(),
            durationSeconds = totalWorkoutDurationSeconds,
            runningSeconds = runningSeconds,
            walkingSeconds = walkingSeconds,
            distanceMeters = state.distanceMeters,
            averagePaceSecondsPerKm = 0.0, // Calculate when distance > 0
            longestCompletedRunSeconds = longestRunSeconds,
            status = state.status
        )

        val workoutId = completedWorkoutDao.insertCompletedWorkout(entity)

        val intervalsWithId = completedIntervals.map { it.copy(completedWorkoutId = workoutId) }
        completedWorkoutDao.insertCompletedIntervals(intervalsWithId)
    }

    override suspend fun deleteWorkout(id: Long) {
        completedWorkoutDao.deleteCompletedWorkout(id)
    }
}

fun WorkoutWithIntervals.toDomainModel(): CompletedWorkoutDetails {
    val completedWorkout = CompletedWorkout(
        id = workout.id,
        trainingWorkoutId = workout.trainingWorkoutId,
        title = workout.title,
        startedAt = workout.startedAt,
        completedAt = workout.completedAt,
        durationSeconds = workout.durationSeconds,
        runningSeconds = workout.runningSeconds,
        walkingSeconds = workout.walkingSeconds,
        distanceMeters = workout.distanceMeters,
        averagePaceSecondsPerKm = workout.averagePaceSecondsPerKm,
        longestCompletedRunSeconds = workout.longestCompletedRunSeconds,
        status = workout.status
    )

    val completedIntervals = intervals.map {
        CompletedInterval(
            id = it.id,
            sequence = it.sequence,
            intervalType = it.intervalType,
            plannedDurationSeconds = it.plannedDurationSeconds,
            actualDurationSeconds = it.actualDurationSeconds,
            completed = it.completed
        )
    }

    return CompletedWorkoutDetails(completedWorkout, completedIntervals)
}
