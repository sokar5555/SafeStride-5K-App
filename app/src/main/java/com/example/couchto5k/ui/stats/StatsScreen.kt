package com.example.couchto5k.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.LongestRunRecordPoint
import com.example.couchto5k.domain.model.ProgramProgress
import com.example.couchto5k.domain.model.RunningProgression
import com.example.couchto5k.domain.model.UserStats
import com.example.couchto5k.domain.model.WeeklyDistancePoint
import com.example.couchto5k.domain.usecase.TimeOfDayCount
import com.example.couchto5k.ui.components.AppCardRole
import com.example.couchto5k.ui.components.AppHeaderBanner
import com.example.couchto5k.ui.components.AppSectionCard
import com.example.couchto5k.ui.components.AppSectionHeader
import com.example.couchto5k.ui.utils.formatDistance
import com.example.couchto5k.ui.utils.formatDuration
import com.example.couchto5k.ui.utils.formatPace

@Composable
fun StatsScreen(
    bottomBar: @Composable () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppHeaderBanner(title = "Running Statistics")
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is StatsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is StatsUiState.Empty -> {
                    EmptyState()
                }
                is StatsUiState.Success -> {
                    StatsContent(
                        userStats = state.userStats,
                        runningProgression = state.runningProgression,
                        weeklyDistance = state.weeklyDistance,
                        timeOfDayDistribution = state.timeOfDayDistribution,
                        programProgress = state.programProgress,
                        distanceUnit = state.distanceUnit,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppSectionCard(
            role = AppCardRole.CONTENT,
            cornerRadius = 16.dp,
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No stats available yet. Complete your first workout to see your progress!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(
    userStats: UserStats,
    runningProgression: RunningProgression,
    weeklyDistance: List<WeeklyDistancePoint>,
    timeOfDayDistribution: List<TimeOfDayCount>,
    programProgress: ProgramProgress,
    distanceUnit: DistanceUnit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OverallSummarySection(userStats, distanceUnit)
        RunVsWalkRatioSection(userStats)
        RunningProgressionSection(runningProgression)
        WeeklyDistanceSection(weeklyDistance, distanceUnit)
        TimeOfDayHeatmapSection(timeOfDayDistribution)
        ProgramProgressSection(programProgress)
    }
}

@Composable
private fun OverallSummarySection(userStats: UserStats, distanceUnit: DistanceUnit) {
    AppSectionHeader(title = "Overall Stats")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(Modifier.weight(1f), "Workouts", userStats.completedWorkoutsCount.toString())
        MetricCard(Modifier.weight(1f), "Total Distance", formatDistance(userStats.totalDistanceMeters, distanceUnit))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(Modifier.weight(1f), "Total Time", formatDuration(userStats.totalDurationSeconds))
        MetricCard(Modifier.weight(1f), "Avg Pace", formatPace(userStats.totalDistanceMeters, userStats.totalDurationSeconds, distanceUnit))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(Modifier.weight(1f), "Run Time", formatDuration(userStats.totalRunningSeconds))
        MetricCard(Modifier.weight(1f), "Walk Time", formatDuration(userStats.totalWalkingSeconds))
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RunVsWalkRatioSection(userStats: UserStats) {
    AppSectionHeader(title = "Run vs Walk Ratio")
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 16.dp,
    ) {
        val totalSeconds = userStats.totalRunningSeconds + userStats.totalWalkingSeconds
        val runRatio = if (totalSeconds > 0) userStats.totalRunningSeconds.toFloat() / totalSeconds else 0f
        val runPct = (runRatio * 100).toInt()
        val walkPct = 100 - runPct

        val runColor = MaterialTheme.colorScheme.primary
        val walkColor = Color(0xFF00796B) // High contrast deep teal for walking

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏃 Running: $runPct%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = runColor)
            Text("🚶 Walking: $walkPct%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = walkColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        ) {
            val safeRunRatio = runRatio.coerceIn(0f, 1f)
            val safeWalkRatio = 1f - safeRunRatio

            if (safeRunRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(safeRunRatio)
                        .background(runColor),
                )
            }
            if (safeWalkRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(safeWalkRatio)
                        .background(walkColor),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Run Time: ${formatDuration(userStats.totalRunningSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Walk Time: ${formatDuration(userStats.totalWalkingSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeOfDayHeatmapSection(distribution: List<TimeOfDayCount>) {
    AppSectionHeader(title = "Time of Day Distribution")
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val maxCount = distribution.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

            distribution.forEach { slotCount ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${slotCount.timeOfDay.icon} ${slotCount.timeOfDay.displayName}", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (slotCount.count.toFloat() / maxCount).coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }

                    Text(
                        text = slotCount.count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningProgressionSection(runningProgression: RunningProgression) {
    AppSectionHeader(title = "Running Progression")
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 16.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Starting Record", style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(runningProgression.startingLongestRunSeconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Improvement", style = MaterialTheme.typography.bodySmall)
                Text("+${formatDuration(runningProgression.improvementSeconds)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Current Record", style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(runningProgression.currentLongestRunSeconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (runningProgression.recordHistory.isNotEmpty()) {
            RecordProgressionChart(runningProgression.recordHistory)
        }
    }
}

@Composable
private fun RecordProgressionChart(history: List<LongestRunRecordPoint>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val maxRecord = history.maxOf { it.longestRunSeconds }.toFloat()

        val width = size.width
        val height = size.height

        val stepX = if (history.size > 1) width / (history.size - 1) else width

        val path = Path()

        history.forEachIndexed { index, point ->
            val x = index * stepX
            val normalizedY = if (maxRecord > 0) point.longestRunSeconds / maxRecord else 0f
            val y = height - (normalizedY * height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4.dp.toPx()),
        )
    }
}

@Composable
private fun WeeklyDistanceSection(weeklyDistance: List<WeeklyDistancePoint>, distanceUnit: DistanceUnit) {
    val unitLabel = if (distanceUnit == DistanceUnit.MILES) "mi" else "km"
    AppSectionHeader(title = "Weekly Distance ($unitLabel)")
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 16.dp,
    ) {
        if (weeklyDistance.isEmpty()) {
            Text("No data yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val maxDist = weeklyDistance.maxOf { it.distanceMeters }.toFloat()
                val width = size.width
                val height = size.height

                val barWidth = width / (weeklyDistance.size * 2)

                weeklyDistance.forEachIndexed { index, point ->
                    val normalizedY = if (maxDist > 0) (point.distanceMeters / maxDist).toFloat() else 0f
                    val barHeight = normalizedY * height

                    val x = (index * 2 * barWidth) + (barWidth / 2)
                    val y = height - barHeight

                    drawRect(
                        color = Color(0xFF1976D2),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weeklyDistance.forEach {
                    Text(it.weekLabel.substringAfter(" "), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ProgramProgressSection(programProgress: ProgramProgress) {
    AppSectionHeader(title = "Program Progress")
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 16.dp,
    ) {
        Text("${programProgress.completedWorkoutsCount} of ${programProgress.totalProgramWorkoutsCount} Workouts Completed", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { programProgress.completionPercentage },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Current Week: Week ${programProgress.currentWeekNumber}", style = MaterialTheme.typography.bodyMedium)
    }
}
