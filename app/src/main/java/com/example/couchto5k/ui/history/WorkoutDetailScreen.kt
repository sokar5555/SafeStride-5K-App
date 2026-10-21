package com.example.couchto5k.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.CompletedInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val details by viewModel.workoutDetails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details?.workout?.title ?: "Workout Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (details == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val workout = details!!.workout
            val intervals = details!!.intervals

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Summary Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Total Time",
                            value = formatDuration(workout.durationSeconds)
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Longest Run",
                            value = formatDuration(workout.longestCompletedRunSeconds)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Run Time",
                            value = formatDuration(workout.runningSeconds)
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Walk Time",
                            value = formatDuration(workout.walkingSeconds)
                        )
                    }
                }

                item {
                    // Placeholder for Map
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Map Placeholder (Phase 6)", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                item {
                    Text("Interval Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                items(intervals) { interval ->
                    IntervalDetailItem(interval)
                }
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IntervalDetailItem(interval: CompletedInterval) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${interval.sequence + 1}. ${interval.intervalType.name}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Planned: ${formatDuration(interval.plannedDurationSeconds)} | Actual: ${formatDuration(interval.actualDurationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (interval.completed) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color(0xFF4CAF50))
        } else {
            Icon(imageVector = Icons.Default.Cancel, contentDescription = "Incomplete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
