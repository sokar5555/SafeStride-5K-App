package com.example.couchto5k.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.engine.WorkoutEngineStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onWorkoutClick: (Long) -> Unit = {},
) {
    val history by viewModel.history.collectAsState()
    var workoutToDelete by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout History") }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No completed workouts yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.workout.id }) { item ->
                    WorkoutHistoryCard(
                        details = item,
                        onClick = { onWorkoutClick(item.workout.id) },
                        onDeleteClick = { workoutToDelete = item.workout.id },
                    )
                }
            }
        }

        if (workoutToDelete != null) {
            AlertDialog(
                onDismissRequest = { workoutToDelete = null },
                title = { Text("Delete Workout") },
                text = { Text("Are you sure you want to delete this workout? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            workoutToDelete?.let { viewModel.deleteWorkout(it) }
                            workoutToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workoutToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun WorkoutHistoryCard(
    details: CompletedWorkoutDetails,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val workout = details.workout
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    val statusColor = when (workout.status) {
        WorkoutEngineStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workout.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Workout",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = formatter.format(workout.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Total: ${formatDuration(workout.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Run: ${formatDuration(workout.runningSeconds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Longest Run: ${formatDuration(workout.longestCompletedRunSeconds)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = workout.status.name,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
