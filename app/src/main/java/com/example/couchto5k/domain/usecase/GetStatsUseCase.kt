package com.example.couchto5k.domain.usecase

import com.example.couchto5k.domain.model.LongestRunRecordPoint
import com.example.couchto5k.domain.model.ProgramProgress
import com.example.couchto5k.domain.model.RunningProgression
import com.example.couchto5k.domain.model.UserStats
import com.example.couchto5k.domain.model.WeeklyDistancePoint
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class TimeOfDay(val displayName: String, val icon: String) {
    MORNING("Morning", "🌅"),
    AFTERNOON("Afternoon", "☀️"),
    EVENING("Evening", "🌆"),
    NIGHT("Night", "🌙")
}

data class TimeOfDayCount(
    val timeOfDay: TimeOfDay,
    val count: Int,
    val percentage: Float,
)


class GetStatsUseCase @Inject constructor(
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
) {
    fun invoke(): Flow<StatsData?> {
        return combine(
            workoutHistoryRepository.getHistory(),
            trainingPlanRepository.getAllWorkouts()
        ) { history, allWorkouts ->
            if (history.isEmpty()) return@combine null

            // 1. Overall Stats
            val completedWorkoutsCount = history.size
            val totalDistanceMeters = history.sumOf { it.workout.distanceMeters }
            val totalDurationSeconds = history.sumOf { it.workout.durationSeconds }
            val totalRunningSeconds = history.sumOf { it.workout.runningSeconds }
            val totalWalkingSeconds = history.sumOf { it.workout.walkingSeconds }
            val aggregateAveragePaceSecondsPerKm = if (totalDistanceMeters > 0) {
                totalDurationSeconds / (totalDistanceMeters / 1000.0)
            } else {
                0.0
            }
            val longestWorkoutDurationSeconds = history.maxOfOrNull { it.workout.durationSeconds } ?: 0

            val userStats = UserStats(
                completedWorkoutsCount = completedWorkoutsCount,
                totalDistanceMeters = totalDistanceMeters,
                totalDurationSeconds = totalDurationSeconds,
                totalRunningSeconds = totalRunningSeconds,
                totalWalkingSeconds = totalWalkingSeconds,
                aggregateAveragePaceSecondsPerKm = aggregateAveragePaceSecondsPerKm,
                longestWorkoutDurationSeconds = longestWorkoutDurationSeconds
            )

            // 2. Running Progression
            val sortedHistory = history.sortedBy { it.workout.completedAt ?: it.workout.startedAt }
            val startingLongestRunSeconds = sortedHistory.firstOrNull()?.workout?.longestCompletedRunSeconds ?: 60

            var currentMax = 0
            val recordHistory = mutableListOf<LongestRunRecordPoint>()

            for (item in sortedHistory) {
                val runSec = item.workout.longestCompletedRunSeconds
                if (runSec > currentMax) {
                    currentMax = runSec
                }

                recordHistory.add(
                    LongestRunRecordPoint(
                        workoutId = item.workout.id,
                        workoutTitle = item.workout.title,
                        completedAt = item.workout.completedAt ?: item.workout.startedAt,
                        longestRunSeconds = currentMax
                    )
                )
            }

            val currentLongestRunSeconds = currentMax
            val improvementSeconds = if (recordHistory.isNotEmpty()) {
                currentLongestRunSeconds - startingLongestRunSeconds
            } else {
                0
            }

            val runningProgression = RunningProgression(
                startingLongestRunSeconds = startingLongestRunSeconds,
                currentLongestRunSeconds = currentLongestRunSeconds,
                improvementSeconds = improvementSeconds,
                recordHistory = recordHistory
            )

            // 3. Weekly Distance
            val weeklyDistanceMap = mutableMapOf<String, Double>()
            val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

            sortedHistory.forEach { item ->
                val instant = item.workout.completedAt ?: item.workout.startedAt
                val zdt = instant.atZone(ZoneId.systemDefault())
                val startOfWeek = zdt.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val weekLabel = "Week of " + formatter.format(startOfWeek)

                weeklyDistanceMap[weekLabel] = (weeklyDistanceMap[weekLabel] ?: 0.0) + item.workout.distanceMeters
            }

            val weeklyDistance = weeklyDistanceMap.map { (label, dist) ->
                WeeklyDistancePoint(label, dist)
            }

            // 4. Time of Day Distribution
            val timeOfDayCounts = mutableMapOf(
                TimeOfDay.MORNING to 0,
                TimeOfDay.AFTERNOON to 0,
                TimeOfDay.EVENING to 0,
                TimeOfDay.NIGHT to 0
            )

            history.forEach { item ->
                val slot = when (item.workout.startedAt.atZone(ZoneId.systemDefault()).hour) {
                    in 5..11 -> TimeOfDay.MORNING
                    in 12..16 -> TimeOfDay.AFTERNOON
                    in 17..20 -> TimeOfDay.EVENING
                    else -> TimeOfDay.NIGHT
                }
                timeOfDayCounts[slot] = (timeOfDayCounts[slot] ?: 0) + 1
            }

            val timeOfDayDistribution = TimeOfDay.entries.map { slot ->
                val count = timeOfDayCounts[slot] ?: 0
                TimeOfDayCount(
                    timeOfDay = slot,
                    count = count,
                    percentage = count.toFloat() / completedWorkoutsCount
                )
            }

            // 5. Program Progress
            val totalProgramWorkoutsCount = allWorkouts.size
            val completionPercentage = if (totalProgramWorkoutsCount > 0) {
                (completedWorkoutsCount.toFloat() / totalProgramWorkoutsCount).coerceAtMost(1f)
            } else {
                0f
            }

            val lastWorkoutId = sortedHistory.lastOrNull()?.workout?.trainingWorkoutId
            val lastWorkout = allWorkouts.find { it.id == lastWorkoutId }
            val currentWeekNumber = lastWorkout?.week ?: 1

            val programProgress = ProgramProgress(
                completedWorkoutsCount = completedWorkoutsCount,
                totalProgramWorkoutsCount = totalProgramWorkoutsCount,
                currentWeekNumber = currentWeekNumber,
                completionPercentage = completionPercentage
            )

            StatsData(
                userStats = userStats,
                runningProgression = runningProgression,
                weeklyDistance = weeklyDistance,
                timeOfDayDistribution = timeOfDayDistribution,
                programProgress = programProgress
            )
        }
    }
}

data class StatsData(
    val userStats: UserStats,
    val runningProgression: RunningProgression,
    val weeklyDistance: List<WeeklyDistancePoint>,
    val timeOfDayDistribution: List<TimeOfDayCount>,
    val programProgress: ProgramProgress,
)
