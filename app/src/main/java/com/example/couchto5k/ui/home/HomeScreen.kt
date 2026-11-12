package com.example.couchto5k.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.ui.components.AppCardRole
import com.example.couchto5k.ui.components.AppHeaderBanner
import com.example.couchto5k.ui.components.AppSectionCard
import com.example.couchto5k.ui.components.AppSectionHeader
import com.example.couchto5k.ui.utils.formatDuration
import com.example.couchto5k.ui.utils.squishyOverscroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBuildCustomWorkoutClick: () -> Unit,
    onSelectProgramClick: () -> Unit,
    onWorkoutPreviewClick: (Long) -> Unit,
    onStartWorkoutClick: (Long) -> Unit,
    onSettingsClick: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val customWorkouts by viewModel.customWorkouts.collectAsState()
    var workoutToDelete by remember { mutableStateOf<TrainingWorkout?>(null) }
    var showClearAllSavedDialog by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            AppHeaderBanner(
                title = "SafeStride 5K",
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .squishyOverscroll(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Free Run Mode Feature Card
            item {
                AppSectionCard(
                    role = AppCardRole.HIGHLIGHT,
                    cornerRadius = 16.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Free Run Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Open-ended run with count-up timer, live GPS route tracking, and automatic mile/km split cues.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onStartWorkoutClick(WorkoutEngine.FREE_RUN_WORKOUT_ID) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START FREE RUN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Build Custom Workout Feature Card
            item {
                AppSectionCard(
                    role = AppCardRole.CONTENT,
                    cornerRadius = 16.dp,
                ) {
                    Text(
                        text = "Build Custom Workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Design your own custom run and walk intervals with automatic warmup and cooldown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onBuildCustomWorkoutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BUILD WORKOUT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // C25K Standard Plan Access Card
            item {
                AppSectionCard(
                    role = AppCardRole.CONTENT,
                    cornerRadius = 16.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "C25K 9-Week Plan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Standard Couch to 5K beginner progression program.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onSelectProgramClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("BROWSE C25K WORKOUTS", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Saved Workouts Section at the bottom of the Home screen
            item {
                AppSectionHeader(
                    title = "Saved Workouts",
                    showDivider = true,
                    modifier = Modifier.padding(top = 8.dp),
                    trailingContent = {
                        if (customWorkouts.isNotEmpty()) {
                            IconButton(onClick = { showClearAllSavedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All Saved Workouts",
                                )
                            }
                        }
                    },
                )
            }

            if (customWorkouts.isEmpty()) {
                item {
                    AppSectionCard(
                        role = AppCardRole.CONTENT,
                        cornerRadius = 12.dp,
                        contentPadding = PaddingValues(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No Saved Workouts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Tap 'BUILD WORKOUT' above to construct your custom run/walk intervals.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(customWorkouts, key = { it.id }) { workout ->
                    SavedWorkoutCard(
                        workout = workout,
                        onStartClick = { onStartWorkoutClick(workout.id) },
                        onPreviewClick = { onWorkoutPreviewClick(workout.id) },
                        onDeleteClick = { workoutToDelete = workout },
                    )
                }
            }
        }
    }

    val toDelete = workoutToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Delete Custom Workout?") },
            text = { Text("Are you sure you want to delete '${toDelete.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomWorkout(toDelete.id)
                        workoutToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearAllSavedDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllSavedDialog = false },
            title = { Text("Clear All Saved Workouts?") },
            text = { Text("Are you sure you want to delete all saved custom workouts? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllCustomWorkouts()
                        showClearAllSavedDialog = false
                    },
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllSavedDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SavedWorkoutCard(
    workout: TrainingWorkout,
    onStartClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    AppSectionCard(
        role = AppCardRole.CONTENT,
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = workout.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Custom Workout",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${formatDuration(workout.totalPlannedDurationSeconds)} total • ${workout.numberOfRunIntervals} runs, ${workout.numberOfWalkIntervals} walks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start workout",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "START NOW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }

            OutlinedButton(
                onClick = onPreviewClick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Preview workout",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PREVIEW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
