package com.example.couchto5k.data.repository

import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.WorkoutWithIntervals
import com.example.couchto5k.domain.model.CompletedInterval
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.data.local.entity.LocationPointEntity
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.location.LocationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min

class DefaultWorkoutHistoryRepository @Inject constructor(
    private val completedWorkoutDao: CompletedWorkoutDao,
) : WorkoutHistoryRepository {

    override fun getHistory(): Flow<List<CompletedWorkoutDetails>> {
        return completedWorkoutDao.getAllCompletedWorkouts().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? {
        val workoutDetails = completedWorkoutDao.getCompletedWorkoutById(id)?.toDomainModel() ?: return null
        val routePoints = completedWorkoutDao.getLocationPointsForWorkout(id).map { entity ->
            LocationPoint(
                latitude = entity.latitude,
                longitude = entity.longitude,
                altitude = entity.altitude,
                accuracyMeters = entity.accuracyMeters,
                timestamp = entity.timestamp,
            )
        }
        return workoutDetails.copy(routePoints = routePoints)
    }

    override suspend fun saveCompletedWorkout(
        workout: TrainingWorkout,
        state: ActiveWorkoutState,
    ) {
        val totalElapsedSeconds = (state.totalElapsedMillis / 1000).toInt()
        val isFreeRun = state.isFreeRun || workout.id == 0L || workout.title.equals("Free Run", ignoreCase = true)

        val completedIntervals = mutableListOf<CompletedIntervalEntity>()
        var longestRunSeconds = 0
        var currentContinuousRunSeconds = 0
        var runningSeconds = 0
        var walkingSeconds = 0

        if (isFreeRun) {
            runningSeconds = totalElapsedSeconds
            longestRunSeconds = totalElapsedSeconds
            val freeRunIntervalDistance = state.intervalDistancesMeters[0] ?: state.distanceMeters
            completedIntervals.add(
                CompletedIntervalEntity(
                    completedWorkoutId = 0,
                    sequence = 0,
                    intervalType = IntervalType.RUN,
                    plannedDurationSeconds = totalElapsedSeconds,
                    actualDurationSeconds = totalElapsedSeconds,
                    distanceMeters = freeRunIntervalDistance,
                    completed = state.status == WorkoutEngineStatus.COMPLETED,
                ),
            )
        } else {
            // Calculate actual interval durations and completion status
            var accumulatedTime = 0

            for ((index, interval) in workout.intervals.withIndex()) {
                val (_, type, durationSeconds) = interval
                val remainingTimeInWorkout = totalElapsedSeconds - accumulatedTime
                val isCooldownCompleted = (state.status == WorkoutEngineStatus.COMPLETED) && (type == IntervalType.COOLDOWN)

                val actualDuration = if (isCooldownCompleted) {
                    durationSeconds
                } else if (accumulatedTime >= totalElapsedSeconds) {
                    0
                } else {
                    min(durationSeconds, maxOf(0, remainingTimeInWorkout))
                }

                val isCompleted = actualDuration >= durationSeconds
                val actualDistanceMeters = state.intervalDistancesMeters[index] ?: 0.0

                completedIntervals.add(
                    CompletedIntervalEntity(
                        completedWorkoutId = 0, // Assigned later
                        sequence = completedIntervals.size,
                        intervalType = type,
                        plannedDurationSeconds = durationSeconds,
                        actualDurationSeconds = actualDuration,
                        distanceMeters = actualDistanceMeters,
                        completed = isCompleted,
                    ),
                )

                if (type == IntervalType.RUN) {
                    runningSeconds += actualDuration
                    currentContinuousRunSeconds += actualDuration
                    if (currentContinuousRunSeconds > longestRunSeconds) {
                        longestRunSeconds = currentContinuousRunSeconds
                    }
                    if (!isCompleted) {
                        currentContinuousRunSeconds = 0 // Break streak if not fully completed
                    }
                } else {
                    currentContinuousRunSeconds = 0
                    walkingSeconds += actualDuration
                }

                accumulatedTime += durationSeconds
            }
        }

        val totalWorkoutDurationSeconds = maxOf(totalElapsedSeconds, completedIntervals.sumOf { it.actualDurationSeconds })
        val averagePaceSecondsPerKm = if (state.distanceMeters > 0.001 && totalWorkoutDurationSeconds > 0) {
            totalWorkoutDurationSeconds / (state.distanceMeters / 1000.0)
        } else {
            0.0
        }

        val entity = CompletedWorkoutEntity(
            trainingWorkoutId = if (isFreeRun) 0L else workout.id,
            scheduledWorkoutId = null,
            title = if (isFreeRun) "Free Run" else workout.title,
            startedAt = state.startedAt,
            completedAt = Instant.now(),
            durationSeconds = totalWorkoutDurationSeconds,
            runningSeconds = runningSeconds,
            walkingSeconds = walkingSeconds,
            distanceMeters = state.distanceMeters,
            averagePaceSecondsPerKm = averagePaceSecondsPerKm,
            longestCompletedRunSeconds = longestRunSeconds,
            status = state.status,
        )

        val workoutId = completedWorkoutDao.insertCompletedWorkout(entity)

        val intervalsWithId = completedIntervals.map { it.copy(completedWorkoutId = workoutId) }
        completedWorkoutDao.insertCompletedIntervals(intervalsWithId)

        val locationPoints = state.routePoints.mapIndexed { index, point ->
            LocationPointEntity(
                completedWorkoutId = workoutId,
                sequence = index,
                timestamp = point.timestamp,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                accuracyMeters = point.accuracyMeters
            )
        }
        if (locationPoints.isNotEmpty()) {
            completedWorkoutDao.insertLocationPoints(locationPoints)
        }
    }

    override suspend fun deleteWorkout(id: Long) {
        completedWorkoutDao.deleteCompletedWorkout(id)
    }

    override suspend fun clearAllHistory() {
        completedWorkoutDao.clearAllCompletedIntervals()
        completedWorkoutDao.clearAllCompletedWorkouts()
    }
}

fun WorkoutWithIntervals.toDomainModel(): CompletedWorkoutDetails {
    val completedWorkout = CompletedWorkout(
        id = workout.id,
        trainingWorkoutId = workout.trainingWorkoutId,
        scheduledWorkoutId = workout.scheduledWorkoutId,
        title = workout.title,
        startedAt = workout.startedAt,
        completedAt = workout.completedAt,
        durationSeconds = workout.durationSeconds,
        runningSeconds = workout.runningSeconds,
        walkingSeconds = workout.walkingSeconds,
        distanceMeters = workout.distanceMeters,
        averagePaceSecondsPerKm = workout.averagePaceSecondsPerKm,
        longestCompletedRunSeconds = workout.longestCompletedRunSeconds,
        status = workout.status,
    )

    val completedIntervals = intervals.map {
        CompletedInterval(
            id = it.id,
            sequence = it.sequence,
            intervalType = it.intervalType,
            plannedDurationSeconds = it.plannedDurationSeconds,
            actualDurationSeconds = it.actualDurationSeconds,
            distanceMeters = it.distanceMeters,
            completed = it.completed,
        )
    }

    return CompletedWorkoutDetails(completedWorkout, completedIntervals)
}
