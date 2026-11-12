package com.example.couchto5k.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.ui.components.AppCardRole
import com.example.couchto5k.ui.components.AppHeaderBanner
import com.example.couchto5k.ui.components.AppSectionCard
import com.example.couchto5k.ui.components.LocationStatusBanner
import com.example.couchto5k.ui.utils.formatDistance
import com.example.couchto5k.ui.utils.formatDuration
import com.example.couchto5k.ui.utils.formatPace
import com.example.couchto5k.ui.utils.formatPaceFromSecondsPerKm
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onViewWorkoutDetails: (Long) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.workoutState.collectAsState()
    val currentWorkout by viewModel.currentWorkout.collectAsState()
    val completionSummary by viewModel.completionSummary.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val context = LocalContext.current

    var showCancelDialog by remember { mutableStateOf(value = false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val isMatchingWorkout = (currentWorkout != null) && (state.workoutId == currentWorkout?.id)
    val isCancelled = isMatchingWorkout && (state.status == WorkoutEngineStatus.CANCELLED)
    val isCompleted = isMatchingWorkout && (state.status == WorkoutEngineStatus.COMPLETED)

    BackHandler {
        if ((state.status == WorkoutEngineStatus.RUNNING) || (state.status == WorkoutEngineStatus.PAUSED)) {
            showCancelDialog = true
        } else {
            onExit()
        }
    }

    if (isCancelled) {
        LaunchedEffect(state.status, currentWorkout?.id) {
            onExit()
        }
    }

    // C. Cancel Confirmation Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(if (state.isFreeRun) "Finish Free Run?" else "Cancel Workout?") },
            text = {
                Text(
                    if (state.isFreeRun) {
                        "Would you like to finish and save your Free Run to Workout History?"
                    } else {
                        "Are you sure you want to stop this workout? Progress recorded so far will be saved to your history."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        if (state.isFreeRun) {
                            viewModel.completeFreeRun()
                        } else {
                            viewModel.stop()
                            onExit()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isFreeRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        contentColor = if (state.isFreeRun) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (state.isFreeRun) "Finish & Save" else "Stop Workout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Resume Workout", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // D. Completion Celebration & Personal Record Unlock
    if (isCompleted) {
        val elapsedSeconds = (state.totalElapsedMillis / 1000).toInt()
        val summary = completionSummary ?: WorkoutCompletionSummary(
            completedWorkoutId = null,
            workoutTitle = if (state.isFreeRun) "Free Run" else (currentWorkout?.title ?: "Workout"),
            distanceMeters = state.distanceMeters,
            durationSeconds = if (state.isFreeRun) {
                elapsedSeconds
            } else {
                maxOf(elapsedSeconds, currentWorkout?.totalPlannedDurationSeconds ?: 0)
            },
            longestRunSeconds = if (state.isFreeRun) {
                elapsedSeconds
            } else {
                currentWorkout?.longestPlannedRunIntervalSeconds ?: 0
            },
            isNewPersonalRecord = false,
        )
        WorkoutCompletionCelebrationDialog(
            summary = summary,
            distanceUnit = distanceUnit,
            onViewDetails = {
                val id = summary.completedWorkoutId
                if (id != null) {
                    onViewWorkoutDetails(id)
                } else {
                    onExit()
                }
            },
            onBackToHome = onExit,
        )
    }

    Scaffold(
        topBar = {
            AppHeaderBanner(
                title = if (state.isFreeRun) "Free Run" else (currentWorkout?.title ?: "Workout"),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if ((state.status == WorkoutEngineStatus.RUNNING) || (state.status == WorkoutEngineStatus.PAUSED)) {
                                showCancelDialog = true
                            } else {
                                onExit()
                            }
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Workout")
                    }
                },
                actions = {
                    if ((state.status == WorkoutEngineStatus.RUNNING) || (state.status == WorkoutEngineStatus.PAUSED)) {
                        TextButton(
                            onClick = {
                                if (state.isFreeRun) {
                                    viewModel.completeFreeRun()
                                } else {
                                    showCancelDialog = true
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isFreeRun) Icons.Default.Check else Icons.Default.Stop,
                                contentDescription = if (state.isFreeRun) "Finish Free Run" else "Stop Workout",
                                tint = if (state.isFreeRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isFreeRun) "FINISH" else "STOP",
                                color = if (state.isFreeRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // E. Permission-Denied & GPS-Disabled Banner
            LocationStatusBanner()

            val interval = state.currentInterval

            if (interval != null) {
                val typeColors = getIntervalColors(interval.type)

                // 1. Interval Step Counter Indicator
                if (state.isFreeRun) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = "Open-Ended Count-Up Timer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    currentWorkout?.let { workout ->
                        val currentStep = state.currentIntervalIndex + 1
                        val totalSteps = workout.intervals.size
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            Text(
                                text = "Interval $currentStep of $totalSteps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // 2. Prominent Interval Type Indicator (with smooth Crossfade)
                Crossfade(
                    targetState = if (state.isFreeRun) IntervalType.RUN else interval.type,
                    label = "IntervalTypeCrossfade",
                ) { intervalType ->
                    val bannerColors = getIntervalColors(intervalType)
                    Surface(
                        color = bannerColors.first,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = if (state.isFreeRun) "FREE RUN" else intervalType.name,
                            color = bannerColors.second,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }

                // 3. Large Numeric Timer (Count-Up for Free Run, Countdown for Interval Workout)
                val elapsedSeconds = (state.totalElapsedMillis / 1000).toInt()
                val remainingSeconds = (state.intervalRemainingMillis / 1000).toInt()
                val displayedTimerSeconds = if (state.isFreeRun) elapsedSeconds else remainingSeconds

                Text(
                    text = formatTime(displayedTimerSeconds),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (!state.isFreeRun) {
                    val totalSeconds = interval.durationSeconds
                    val rawProgress = if (totalSeconds > 0) {
                        (1f - (state.intervalRemainingMillis.toFloat() / (totalSeconds * 1000f))).coerceIn(0f, 1f)
                    } else 0f
                    val animatedIntervalProgress by animateFloatAsState(
                        targetValue = rawProgress,
                        label = "IntervalProgressAnimation",
                    )

                    // 4. Interval Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedIntervalProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = typeColors.second,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. "NEXT UP" Preview Chip
                    currentWorkout?.let { workout ->
                        if (state.currentIntervalIndex < (workout.intervals.size - 1)) {
                            val nextInterval = workout.intervals[state.currentIntervalIndex + 1]
                            val nextColors = getIntervalColors(nextInterval.type)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "NEXT UP:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = nextColors.first,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            "${nextInterval.type.name} • ${formatTime(nextInterval.durationSeconds)}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = nextColors.second,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "FINAL INTERVAL",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Live GPS Metrics Card (DISTANCE, AVG PACE, and THIS MILE/KM PACE)
                val splitLabel = if (distanceUnit == DistanceUnit.MILES) "THIS MILE PACE" else "THIS KM PACE"
                val currentSplitPaceFormatted = formatPaceFromSecondsPerKm(
                    paceSecondsPerKm = state.currentSplitPaceSecondsPerKm,
                    unit = distanceUnit,
                )

                AppSectionCard(
                    role = AppCardRole.HIGHLIGHT,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = formatDistance(state.distanceMeters, distanceUnit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "DISTANCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = formatPace(state.distanceMeters, elapsedSeconds, distanceUnit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "AVG PACE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = currentSplitPaceFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = splitLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 7. Total Elapsed Time & Overall Workout Progress
                if (!state.isFreeRun) {
                    currentWorkout?.let { workout ->
                        val totalPlanned = workout.totalPlannedDurationSeconds
                        val elapsed = (state.totalElapsedMillis / 1000).toInt()
                        val rawOverallProgress = if (totalPlanned > 0) {
                            (elapsed.toFloat() / totalPlanned.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val overallProgress by animateFloatAsState(
                            targetValue = rawOverallProgress,
                            label = "OverallWorkoutProgress",
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Total Time: ${formatTime(elapsed)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${(overallProgress * 100).toInt()}% Complete",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 8. Controls (Pause/Resume, Skip/Complete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!state.isFreeRun) {
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Default.FastRewind,
                                contentDescription = "Previous Interval",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            if (state.isPaused) viewModel.resume() else viewModel.pause()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Resume Workout" else "Pause Workout",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    if (!state.isFreeRun) {
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = "Next Interval",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.completeFreeRun() },
                            modifier = Modifier.heightIn(min = 52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Complete Free Run")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COMPLETE", fontWeight = FontWeight.Bold)
                        }
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
private fun WorkoutCompletionCelebrationDialog(
    summary: WorkoutCompletionSummary,
    distanceUnit: DistanceUnit,
    onViewDetails: () -> Unit,
    onBackToHome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBackToHome,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Workout Complete! 🎉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.workoutTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(visible = summary.isNewPersonalRecord) {
                    AppSectionCard(
                        role = AppCardRole.SUCCESS,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Personal record trophy",
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "New Personal Record! 🏆",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    text = "Longest Continuous Run: ${formatDuration(summary.longestRunSeconds)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                AppSectionCard(
                    role = AppCardRole.CONTENT,
                    cornerRadius = 14.dp,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CelebrationMetricItem(
                                label = "Distance",
                                value = formatDistance(summary.distanceMeters, distanceUnit),
                                modifier = Modifier.weight(1f),
                            )
                            CelebrationMetricItem(
                                label = "Duration",
                                value = formatDuration(summary.durationSeconds),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CelebrationMetricItem(
                                label = "Avg Pace",
                                value = formatPace(summary.distanceMeters, summary.durationSeconds, distanceUnit),
                                modifier = Modifier.weight(1f),
                            )
                            CelebrationMetricItem(
                                label = "Longest Run",
                                value = formatDuration(summary.longestRunSeconds),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onViewDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("View Workout Details", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onBackToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Back to Home", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun CelebrationMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
        )
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
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
