package com.example.couchto5k.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.engine.WorkoutEngineStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.workoutState.collectAsState()
    val currentWorkout by viewModel.currentWorkout.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (state.status == WorkoutEngineStatus.RUNNING || state.status == WorkoutEngineStatus.PAUSED) {
            showExitDialog = true
        } else {
            onExit()
        }
    }

    if (state.status == WorkoutEngineStatus.COMPLETED || state.status == WorkoutEngineStatus.CANCELLED) {
        LaunchedEffect(state.status) {
            onExit()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Stop Workout?") },
            text = { Text("Are you sure you want to stop the workout? Progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.stop()
                    onExit()
                }) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentWorkout?.title ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Workout")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val interval = state.currentInterval

            if (interval != null) {
                val typeColors = getIntervalColors(interval.type)

                // 1. Prominent Interval Type Indicator
                Surface(
                    color = typeColors.first,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = interval.type.name,
                        color = typeColors.second,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                // 2. Large Numeric Interval Countdown Timer
                val remainingSeconds = (state.intervalRemainingMillis / 1000).toInt()
                val totalSeconds = interval.durationSeconds
                val progress = if (totalSeconds > 0) {
                    1f - (state.intervalRemainingMillis.toFloat() / (totalSeconds * 1000f))
                } else 0f

                Text(
                    text = formatTime(remainingSeconds),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 3. Interval Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = typeColors.second,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 4. "NEXT UP" Preview Chip
                currentWorkout?.let { workout ->
                    if (state.currentIntervalIndex < workout.intervals.size - 1) {
                        val nextInterval = workout.intervals[state.currentIntervalIndex + 1]
                        val nextColors = getIntervalColors(nextInterval.type)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "NEXT UP:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = nextColors.first,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${nextInterval.type.name} • ${formatTime(nextInterval.durationSeconds)}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = nextColors.second,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "FINAL INTERVAL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 5. Total Elapsed Time Readout
                Text(
                    text = "Total Time: ${formatTime((state.totalElapsedMillis / 1000).toInt())}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Controls (Pause/Resume, Skip, Previous)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Previous Interval", modifier = Modifier.size(32.dp))
                    }

                    FloatingActionButton(
                        onClick = {
                            if (state.isPaused) viewModel.resume() else viewModel.pause()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.skipNext() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Next Interval", modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun getIntervalColors(type: IntervalType): Pair<Color, Color> {
    return when (type) {
        IntervalType.WARMUP -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        IntervalType.RUN -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        IntervalType.WALK -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        IntervalType.COOLDOWN -> Pair(Color(0xFFE0F2F1), Color(0xFF004D40))
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}