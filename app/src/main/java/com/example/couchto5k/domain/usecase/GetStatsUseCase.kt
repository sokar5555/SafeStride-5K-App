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
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

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
            // Sort history by completedAt to process chronologically
            val sortedHistory = history.sortedBy { it.workout.completedAt ?: it.workout.startedAt }

            val startingLongestRunSeconds = sortedHistory.firstOrNull()?.workout?.longestCompletedRunSeconds ?: 60

            var currentMax = 0
            val recordHistory = mutableListOf<LongestRunRecordPoint>()

            for (item in sortedHistory) {
                val runSec = item.workout.longestCompletedRunSeconds
                if (runSec > currentMax) {
                    currentMax = runSec
                }

                // We record the current record at this point in time
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
                // Group by the start of the week (Monday)
                val startOfWeek = zdt.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val weekLabel = "Week of " + formatter.format(startOfWeek)

                weeklyDistanceMap[weekLabel] = (weeklyDistanceMap[weekLabel] ?: 0.0) + item.workout.distanceMeters
            }

            val weeklyDistance = weeklyDistanceMap.map { (label, dist) ->
                WeeklyDistancePoint(label, dist)
            }

            // 4. Program Progress
            val totalProgramWorkoutsCount = allWorkouts.size
            val completionPercentage = if (totalProgramWorkoutsCount > 0) {
                (completedWorkoutsCount.toFloat() / totalProgramWorkoutsCount).coerceAtMost(1f)
            } else {
                0f
            }

            // Figure out the current week based on the last completed workout's week, if available, or just a rough calculation
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
                programProgress = programProgress
            )
        }
    }
}

data class StatsData(
    val userStats: UserStats,
    val runningProgression: RunningProgression,
    val weeklyDistance: List<WeeklyDistancePoint>,
    val programProgress: ProgramProgress,
)
