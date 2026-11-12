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

        // 2. Beyond 5K: +10% weekly progressive volume extension with designated Long Run day
        if (progressiveWeeksExtension > 0) {
            val progressiveWorkouts = DefaultTrainingPlanRepository.generateProgressiveWeeks(progressiveWeeksExtension)
            val workoutsByWeek = progressiveWorkouts.groupBy { it.week }
            val effectiveLongRunDay = if (longRunDay != null && preferredDays.contains(longRunDay)) {
                longRunDay
            } else {
                sortedDays.last()
            }

            for ((_, weekWorkouts) in workoutsByWeek) {
                val midweekWorkouts = weekWorkouts.filter { it.workoutNumber != 3 }.toMutableList()
                val longRunWorkout = weekWorkouts.find { it.workoutNumber == 3 } ?: weekWorkouts.last()

                // Collect the next 3 scheduled training dates for this progressive week
                val weekDates = mutableListOf<LocalDate>()
                repeat(weekWorkouts.size) {
                    weekDates.add(currentDate)
                    dayIndex = (dayIndex + 1) % sortedDays.size
                    var daysToAdd = 1L
                    while (currentDate.plusDays(daysToAdd).dayOfWeek != sortedDays[dayIndex]) {
                        daysToAdd++
                    }
                    currentDate = currentDate.plusDays(daysToAdd)
                }

                // Assign Long Run to the date matching effectiveLongRunDay (or the last date of the week)
                val longRunDate = weekDates.find { it.dayOfWeek == effectiveLongRunDay } ?: weekDates.last()
                var midweekIndex = 0

                for (date in weekDates) {
                    val assignedWorkout = if (date == longRunDate) {
                        longRunWorkout
                    } else {
                        midweekWorkouts.getOrElse(midweekIndex++) { longRunWorkout }
                    }
                    scheduledEntities.add(
                        ScheduledWorkoutEntity(
                            trainingWorkoutId = assignedWorkout.id,
                            scheduledDate = date,
                            status = WorkoutStatus.UPCOMING.name,
                        ),
                    )
                }
            }
        }

        scheduledWorkoutDao.deleteAll()
        scheduledWorkoutDao.insertScheduledWorkouts(scheduledEntities)
    }
}
