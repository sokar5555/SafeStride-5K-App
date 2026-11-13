package com.example.couchto5k.domain.usecase

import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.LongestRunRecordPoint
import com.example.couchto5k.domain.model.ProgramProgress
import com.example.couchto5k.domain.model.RunningProgression
import com.example.couchto5k.domain.model.UserStats
import com.example.couchto5k.domain.model.WeeklyDistancePoint
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    private val settingsRepository: SettingsRepository,
) {
    private var todayProvider: () -> LocalDate = { LocalDate.now() }

    internal constructor(
        workoutHistoryRepository: WorkoutHistoryRepository,
        trainingPlanRepository: TrainingPlanRepository,
        settingsRepository: SettingsRepository,
        todayProvider: () -> LocalDate,
    ) : this(workoutHistoryRepository, trainingPlanRepository, settingsRepository) {
        this.todayProvider = todayProvider
    }

    fun invoke(): Flow<StatsData?> {
        return combine(
            workoutHistoryRepository.getHistory(),
            trainingPlanRepository.getAllWorkouts(),
            settingsRepository.programStartDate,
            settingsRepository.progressiveWeeksExtension,
        ) { history, allWorkouts, programStartDate, progressiveWeeksExtension ->
            if (history.isEmpty()) return@combine null

            // 1. Overall Stats
            val completedWorkoutsCount = history.size
            val totalDistanceMeters = history.sumOf { (workout) -> workout.distanceMeters }
            val totalDurationSeconds = history.sumOf { (workout) -> workout.durationSeconds }
            val totalRunningSeconds = history.sumOf { (workout) -> workout.runningSeconds }
            val totalWalkingSeconds = history.sumOf { (workout) -> workout.walkingSeconds }
            val aggregateAveragePaceSecondsPerKm = if (totalDistanceMeters > 0) {
                totalDurationSeconds / (totalDistanceMeters / 1000.0)
            } else {
                0.0
            }
            val longestWorkoutDurationSeconds = history.maxOfOrNull { (workout) -> workout.durationSeconds } ?: 0

            val userStats = UserStats(
                completedWorkoutsCount = completedWorkoutsCount,
                totalDistanceMeters = totalDistanceMeters,
                totalDurationSeconds = totalDurationSeconds,
                totalRunningSeconds = totalRunningSeconds,
                totalWalkingSeconds = totalWalkingSeconds,
                aggregateAveragePaceSecondsPerKm = aggregateAveragePaceSecondsPerKm,
                longestWorkoutDurationSeconds = longestWorkoutDurationSeconds,
            )

            // 2. Running Progression
            val sortedHistory = history.sortedBy { (workout) -> workout.completedAt ?: workout.startedAt }
            val runningWorkouts = sortedHistory.filter { (workout) -> workout.longestCompletedRunSeconds > 0 }
            val startingLongestRunSeconds = runningWorkouts.firstOrNull()?.workout?.longestCompletedRunSeconds ?: 0

            var currentMax = 0
            val recordHistory = mutableListOf<LongestRunRecordPoint>()

            for (item in runningWorkouts) {
                val runSec = item.workout.longestCompletedRunSeconds
                if (runSec > currentMax) {
                    currentMax = runSec
                }

                recordHistory.add(
                    LongestRunRecordPoint(
                        workoutId = item.workout.id,
                        workoutTitle = item.workout.title,
                        completedAt = item.workout.completedAt ?: item.workout.startedAt,
                        longestRunSeconds = currentMax,
                    ),
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
                recordHistory = recordHistory,
            )

            // 3. Weekly Distance
            val weeklyDistanceMap = mutableMapOf<String, Double>()
            val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

            sortedHistory.forEach { item ->
                val instant = item.workout.completedAt ?: item.workout.startedAt
                val zdt = instant.atZone(ZoneId.systemDefault())
                val startOfWeek = zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
                TimeOfDay.NIGHT to 0,
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
                    percentage = count.toFloat() / completedWorkoutsCount,
                )
            }

            // 5. Program Progress (count distinct completed official workouts against configured total)
            val extensionWeeks = progressiveWeeksExtension.coerceAtLeast(0)
            val progressiveWorkouts = DefaultTrainingPlanRepository.generateProgressiveWeeks(extensionWeeks)
            val officialWorkouts = allWorkouts + progressiveWorkouts
            val officialWorkoutIds = officialWorkouts.asSequence().map { it.id }.toSet()

            val totalProgramWorkoutsCount = officialWorkouts.size
            val completedOfficialWorkoutIds = history
                .asSequence()
                .filter { it.workout.status == WorkoutEngineStatus.COMPLETED }
                .map { it.workout.trainingWorkoutId }
                .filter { it in officialWorkoutIds }
                .toSet()

            val officialCompletedCount = completedOfficialWorkoutIds.size
            val completionPercentage = if (totalProgramWorkoutsCount > 0) {
                (officialCompletedCount.toFloat() / totalProgramWorkoutsCount).coerceIn(0f, 1f)
            } else {
                0f
            }

            val baseWeeksCount = allWorkouts.maxOfOrNull { it.week } ?: 9
            val totalProgramWeeks = (baseWeeksCount + extensionWeeks).coerceAtLeast(1)
            val currentWeekNumber = calculateCurrentProgramWeek(
                programStartDate = programStartDate,
                today = todayProvider(),
                totalProgramWeeks = totalProgramWeeks,
            )

            val programProgress = ProgramProgress(
                completedWorkoutsCount = officialCompletedCount,
                totalProgramWorkoutsCount = totalProgramWorkoutsCount,
                currentWeekNumber = currentWeekNumber,
                completionPercentage = completionPercentage,
            )

            StatsData(
                userStats = userStats,
                runningProgression = runningProgression,
                weeklyDistance = weeklyDistance,
                timeOfDayDistribution = timeOfDayDistribution,
                programProgress = programProgress,
            )
        }
    }

    private fun calculateCurrentProgramWeek(
        programStartDate: LocalDate?,
        today: LocalDate,
        totalProgramWeeks: Int,
    ): Int {
        if ((programStartDate == null) || today.isBefore(programStartDate)) return 1
        val startWeekSunday = programStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val currentWeekSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weeksElapsed = ChronoUnit.WEEKS.between(startWeekSunday, currentWeekSunday).toInt().coerceAtLeast(0)
        return (weeksElapsed + 1).coerceIn(1, totalProgramWeeks)
    }
}

data class StatsData(
    val userStats: UserStats,
    val runningProgression: RunningProgression,
    val weeklyDistance: List<WeeklyDistancePoint>,
    val timeOfDayDistribution: List<TimeOfDayCount>,
    val programProgress: ProgramProgress,
)
