package com.example.couchto5k.domain.usecase

import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.domain.model.WorkoutStatus
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

class GenerateScheduleUseCase @Inject constructor(
    private val trainingPlanRepository: TrainingPlanRepository,
    private val scheduledWorkoutDao: ScheduledWorkoutDao
) {
    suspend operator fun invoke(startDate: LocalDate, preferredDays: Set<DayOfWeek>) {
        val workouts = trainingPlanRepository.getAllWorkouts().first()
        val sortedDays = preferredDays.toList().sortedBy { it.value } // Note: Monday = 1, Sunday = 7

        if (sortedDays.isEmpty()) return

        val scheduledEntities = mutableListOf<ScheduledWorkoutEntity>()
        var currentDate = startDate

        // Find the first date matching a preferred day
        while (!preferredDays.contains(currentDate.dayOfWeek)) {
            currentDate = currentDate.plusDays(1)
        }

        var dayIndex = sortedDays.indexOf(currentDate.dayOfWeek)

        for (workout in workouts) {
            scheduledEntities.add(
                ScheduledWorkoutEntity(
                    trainingWorkoutId = workout.id,
                    scheduledDate = currentDate,
                    status = WorkoutStatus.UPCOMING.name
                )
            )

            // Advance to the next preferred day
            dayIndex = (dayIndex + 1) % sortedDays.size
            var daysToAdd = 1L
            while (currentDate.plusDays(daysToAdd).dayOfWeek != sortedDays[dayIndex]) {
                daysToAdd++
            }
            currentDate = currentDate.plusDays(daysToAdd)
        }

        scheduledWorkoutDao.deleteAll()
        scheduledWorkoutDao.insertScheduledWorkouts(scheduledEntities)
    }
}
