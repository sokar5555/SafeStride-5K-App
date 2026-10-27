package com.example.couchto5k.domain.usecase

import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.domain.model.ScheduledWorkout
import com.example.couchto5k.domain.model.WorkoutStatus
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

class GetCombinedScheduleUseCase @Inject constructor(
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val completedWorkoutDao: CompletedWorkoutDao,
    private val trainingPlanRepository: TrainingPlanRepository,
) {
    operator fun invoke(): Flow<List<ScheduledWorkout>> {
        return combine(
            scheduledWorkoutDao.getAllScheduledWorkouts(),
            completedWorkoutDao.getAllCompletedWorkouts(),
            trainingPlanRepository.getAllWorkouts(),
        ) { scheduledList, completedList, workoutsList ->
            val today = LocalDate.now()

            val workoutsMap = workoutsList.associateBy { (id) -> id }
            val completedMap = completedList.associateBy { it.workout.trainingWorkoutId }

            // Find the first uncompleted workout scheduled today or in the future
            val nextAvailable = scheduledList.firstOrNull { entity ->
                !completedMap.containsKey(entity.trainingWorkoutId) && !entity.scheduledDate.isBefore(today)
            } ?: scheduledList.firstOrNull { entity ->
                !completedMap.containsKey(entity.trainingWorkoutId)
            }

            scheduledList.map { entity ->
                val completed = completedMap[entity.trainingWorkoutId]
                val status = when {
                    completed != null -> WorkoutStatus.COMPLETED
                    entity.scheduledDate.isBefore(today) -> WorkoutStatus.SKIPPED
                    entity.id == nextAvailable?.id -> WorkoutStatus.AVAILABLE
                    else -> WorkoutStatus.UPCOMING
                }

                val workoutTitle = workoutsMap[entity.trainingWorkoutId]?.title
                    ?: "Week ${((entity.trainingWorkoutId - 1) / 3) + 1}, Workout ${((entity.trainingWorkoutId - 1) % 3) + 1}"

                ScheduledWorkout(
                    id = entity.id,
                    trainingWorkoutId = entity.trainingWorkoutId,
                    scheduledDate = entity.scheduledDate,
                    status = status,
                    title = workoutTitle,
                )
            }
        }
    }
}
