package com.example.couchto5k.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.example.couchto5k.domain.model.ScheduledWorkout
import com.example.couchto5k.domain.model.WorkoutStatus
import com.example.couchto5k.ui.utils.squishyOverscroll
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onWorkoutClick: (Long) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val schedule by viewModel.schedule.collectAsState()
    val startDate by viewModel.programStartDate.collectAsState()
    val preferredDays by viewModel.preferredWorkoutDays.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(value = false) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initializeStartDateIfNotSet()
    }

    LaunchedEffect(startDate) {
        startDate?.let {
            displayedMonth = YearMonth.from(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Schedule Settings")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        val filteredList = if (selectedDate != null) {
            schedule.filter { it.scheduledDate == selectedDate }
        } else {
            schedule
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .squishyOverscroll(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Settings Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Start: ${startDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Not set"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Days: ${preferredDays.joinToString { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Interactive Month Grid View
            item {
                MonthCalendarCard(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    scheduledWorkouts = schedule,
                    onMonthChange = { displayedMonth = it },
                    onDateSelected = { date ->
                        selectedDate = if (selectedDate == date) null else date
                    }
                )
            }

            // 3. Filter Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedDate != null) {
                        Text(
                            text = "Date: ${selectedDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { selectedDate = null }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Show All",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show All")
                        }
                    } else {
                        Text(
                            text = "Scheduled Workouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Workout Detail Cards / Rest Day View
            if (schedule.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if ((selectedDate != null) && filteredList.isEmpty()) {
                item {
                    RestDayCard(date = selectedDate!!)
                }
            } else {
                items(filteredList, key = { it.id }) { workout ->
                    ScheduledWorkoutItem(
                        workout = workout,
                        onClick = { onWorkoutClick(workout.trainingWorkoutId) }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        ScheduleSettingsDialog(
            currentStartDate = startDate ?: LocalDate.now(),
            currentDays = preferredDays,
            onDismiss = { showSettingsDialog = false },
            onSave = { newStartDate, newDays ->
                viewModel.updateScheduleSettings(newStartDate, newDays)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun MonthCalendarCard(
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    scheduledWorkouts: List<ScheduledWorkout>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val workoutMap = remember(scheduledWorkouts) {
        scheduledWorkouts.associateBy { it.scheduledDate }
    }

    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val today = LocalDate.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Month Header Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(displayedMonth.minusMonths(1)) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Month"
                    )
                }
                Text(
                    text = displayedMonth.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onMonthChange(displayedMonth.plusMonths(1)) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day of Week Header Row (Sun .. Sat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                daysOfWeek.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Month Grid Calculations
            val firstDayOfMonth = displayedMonth.atDay(1)
            val daysInMonth = displayedMonth.lengthOfMonth()
            val startDayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7 // Sun=0, Mon=1... Sat=6

            val totalGridCells = startDayOfWeekOffset + daysInMonth
            val totalRows = (totalGridCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (rowIndex in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (colIndex in 0 until 7) {
                            val cellIndex = (rowIndex * 7) + colIndex
                            val dayNumber = (cellIndex - startDayOfWeekOffset) + 1

                            if (dayNumber in 1..daysInMonth) {
                                val date = displayedMonth.atDay(dayNumber)
                                val workout = workoutMap[date]
                                val isSelected = date == selectedDate
                                val isToday = date == today

                                MonthDayCell(
                                    date = date,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    workout = workout,
                                    onClick = { onDateSelected(date) }
                                )
                            } else {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    workout: ScheduledWorkout?,
    onClick: () -> Unit,
) {
    val statusColor = when (workout?.status) {
        WorkoutStatus.COMPLETED -> Color(0xFF4CAF50)
        WorkoutStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
        WorkoutStatus.SKIPPED -> Color(0xFFF44336)
        WorkoutStatus.UPCOMING -> Color.Gray
        WorkoutStatus.IN_PROGRESS -> Color(0xFFFF9800)
        else -> null
    }

    val cellBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .width(32.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(cellBackground)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )

        // Status Indicator Dot below day number
        if (statusColor != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else statusColor)
            )
        } else {
            Spacer(modifier = Modifier.height(7.dp))
        }
    }
}

@Composable
fun RestDayCard(date: LocalDate) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rest & Recovery Day",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No workout scheduled for ${date.format(formatter)}. Rest is essential for muscle recovery!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsDialog(
    currentStartDate: LocalDate,
    currentDays: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Set<DayOfWeek>) -> Unit
) {
    var selectedStartDate by remember { mutableStateOf(currentStartDate) }
    var selectedDays by remember { mutableStateOf(currentDays) }
    var showDatePicker by remember { mutableStateOf(value = false) }

    val allDays = DayOfWeek.entries
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Settings") },
        text = {
            Column {
                Text(
                    text = "Program Start Date",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Start Date",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedStartDate.format(dateFormatter))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Preferred Workout Days",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                            .padding(vertical = 4.dp),
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
                        Text(day.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
                if (selectedDays.size != 3) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                onClick = { onSave(selectedStartDate, selectedDays) },
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedStartDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedStartDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun ScheduledWorkoutItem(
    workout: ScheduledWorkout,
    onClick: () -> Unit = {},
) {
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
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = workout.title,
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
