package com.example.couchto5k.domain.usecase

import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.domain.model.ScheduledWorkout
import com.example.couchto5k.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

class GetCombinedScheduleUseCase @Inject constructor(
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val completedWorkoutDao: CompletedWorkoutDao
) {
    operator fun invoke(): Flow<List<ScheduledWorkout>> {
        return combine(
            scheduledWorkoutDao.getAllScheduledWorkouts(),
            completedWorkoutDao.getAllCompletedWorkouts()
        ) { scheduledList, completedList ->
            val today = LocalDate.now()

            // Map scheduled by trainingWorkoutId
            val completedMap = completedList.associateBy { it.workout.trainingWorkoutId }

            // Find the first upcoming workout
            val firstUpcoming = scheduledList.firstOrNull {
                !completedMap.containsKey(it.trainingWorkoutId)
            }

            scheduledList.map { entity ->
                val completed = completedMap[entity.trainingWorkoutId]
                val status = when {
                    completed != null -> WorkoutStatus.COMPLETED
                    entity.id == firstUpcoming?.id -> WorkoutStatus.AVAILABLE
                    entity.scheduledDate.isBefore(today) -> WorkoutStatus.SKIPPED // or MISSED
                    else -> WorkoutStatus.UPCOMING
                }

                ScheduledWorkout(
                    id = entity.id,
                    trainingWorkoutId = entity.trainingWorkoutId,
                    scheduledDate = entity.scheduledDate,
                    status = status
                )
            }
        }
    }
}
