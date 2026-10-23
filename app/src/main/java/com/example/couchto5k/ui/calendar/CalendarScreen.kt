package com.example.couchto5k.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.ScheduledWorkout
import com.example.couchto5k.domain.model.WorkoutStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val schedule by viewModel.schedule.collectAsState()
    val startDate by viewModel.programStartDate.collectAsState()
    val preferredDays by viewModel.preferredWorkoutDays.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeStartDateIfNotSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Schedule Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Settings summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettingsDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Program Start: ${startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "Not set"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Workout Days: ${preferredDays.joinToString { it.name.take(3) }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (schedule.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(schedule, key = { it.id }) { workout ->
                        ScheduledWorkoutItem(workout)
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentDays = preferredDays,
            onDismiss = { showSettingsDialog = false },
            onSave = { newDays ->
                viewModel.updatePreferredDays(newDays)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun SettingsDialog(
    currentDays: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onSave: (Set<DayOfWeek>) -> Unit
) {
    var selectedDays by remember { mutableStateOf(currentDays) }
    val allDays = DayOfWeek.values()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred Workout Days") },
        text = {
            Column {
                allDays.forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDays = if (selectedDays.contains(day)) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedDays.contains(day),
                            onCheckedChange = { isChecked ->
                                selectedDays = if (isChecked) {
                                    selectedDays + day
                                } else {
                                    selectedDays - day
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(day.name.lowercase().capitalize())
                    }
                }
                if (selectedDays.size != 3) {
                    Text(
                        "Please select exactly 3 days for Couch to 5K.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedDays) },
                enabled = selectedDays.size == 3
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ScheduledWorkoutItem(workout: ScheduledWorkout) {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val isToday = workout.scheduledDate == LocalDate.now()

    val (statusColor, statusText) = when (workout.status) {
        WorkoutStatus.COMPLETED -> Color(0xFF4CAF50) to "Completed"
        WorkoutStatus.AVAILABLE -> MaterialTheme.colorScheme.primary to "Available"
        WorkoutStatus.UPCOMING -> Color.Gray to "Upcoming"
        WorkoutStatus.SKIPPED -> Color(0xFFF44336) to "Missed"
        WorkoutStatus.IN_PROGRESS -> Color(0xFFFF9800) to "In Progress"
        WorkoutStatus.REST_DAY -> Color.LightGray to "Rest Day"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = workout.scheduledDate.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Workout ${workout.trainingWorkoutId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
