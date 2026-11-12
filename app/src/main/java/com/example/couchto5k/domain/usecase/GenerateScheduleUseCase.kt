package com.example.couchto5k.domain.usecase

import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.WorkoutStatus
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

class GenerateScheduleUseCase @Inject constructor(
    private val trainingPlanRepository: TrainingPlanRepository,
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
) {
    suspend operator fun invoke(
        startDate: LocalDate,
        preferredDays: Set<DayOfWeek>,
        progressiveWeeksExtension: Int = 0,
        longRunDay: DayOfWeek? = null,
    ) {
        val baseWorkouts = trainingPlanRepository.getAllWorkouts().first()
        val sortedDays = preferredDays.asSequence().sortedBy { it.value }.toList() // Monday = 1, Sunday = 7

        if (sortedDays.isEmpty()) return

        val scheduledEntities = mutableListOf<ScheduledWorkoutEntity>()
        var currentDate = startDate

        // Find the first date matching a preferred day
        while (!preferredDays.contains(currentDate.dayOfWeek)) {
            currentDate = currentDate.plusDays(1)
        }

        var dayIndex = sortedDays.indexOf(currentDate.dayOfWeek)

        // 1. Standard Couch to 5K progression (Weeks 1..9)
        for ((id) in baseWorkouts) {
            scheduledEntities.add(
                ScheduledWorkoutEntity(
                    trainingWorkoutId = id,
                    scheduledDate = currentDate,
                    status = WorkoutStatus.UPCOMING.name,
                ),
            )

            // Advance to the next preferred day
            dayIndex = (dayIndex + 1) % sortedDays.size
            var daysToAdd = 1L
            while (currentDate.plusDays(daysToAdd).dayOfWeek != sortedDays[dayIndex]) {
                daysToAdd++
            }
            currentDate = currentDate.plusDays(daysToAdd)
        }

        // 2. Beyond 5K: +10% weekly progressive volume extension with designated Long Run day (Option A)
        if (progressiveWeeksExtension > 0) {
            val progressiveWorkouts = DefaultTrainingPlanRepository.generateProgressiveWeeks(progressiveWeeksExtension)
            val workoutsByWeek = progressiveWorkouts.groupBy { (_, week) -> week }
            val effectiveLongRunDay = if ((longRunDay != null) && preferredDays.contains(longRunDay)) {
                longRunDay
            } else {
                sortedDays.last()
            }

            // Establish the weekly training cycle so that it culminates on effectiveLongRunDay
            val longRunIndex = sortedDays.indexOf(effectiveLongRunDay)
            val fullCycle = if (longRunIndex != -1) {
                sortedDays.drop(longRunIndex + 1) + sortedDays.take(longRunIndex + 1)
            } else {
                sortedDays
            }
            val progressiveDays = if (fullCycle.size >= 3) {
                fullCycle.takeLast(3)
            } else {
                fullCycle
            }

            for ((_, weekWorkouts) in workoutsByWeek) {
                val sortedWorkouts = weekWorkouts.sortedBy { (_, _, workoutNumber) -> workoutNumber }
                for (i in sortedWorkouts.indices) {
                    val (workoutId) = sortedWorkouts[i]
                    val targetDayOfWeek = progressiveDays[i % progressiveDays.size]

                    // Advance currentDate to targetDayOfWeek
                    while (currentDate.dayOfWeek != targetDayOfWeek) {
                        currentDate = currentDate.plusDays(1)
                    }

                    scheduledEntities.add(
                        ScheduledWorkoutEntity(
                            trainingWorkoutId = workoutId,
                            scheduledDate = currentDate,
                            status = WorkoutStatus.UPCOMING.name,
                        ),
                    )

                    // Step at least 1 day forward before seeking the next training day
                    currentDate = currentDate.plusDays(1)
                }
            }
        }

        scheduledWorkoutDao.deleteAll()
        scheduledWorkoutDao.insertScheduledWorkouts(scheduledEntities)
    }
}
