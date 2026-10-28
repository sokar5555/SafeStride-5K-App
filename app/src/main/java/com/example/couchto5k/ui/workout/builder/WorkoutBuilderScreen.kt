package com.example.couchto5k.ui.workout.builder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.example.couchto5k.ui.utils.squishyOverscroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.WorkoutInterval
import kotlin.math.roundToInt

sealed interface TimePickerTarget {
    data class AddNew(val type: IntervalType) : TimePickerTarget
    data class EditMiddle(val index: Int, val currentDurationSeconds: Int, val type: IntervalType) : TimePickerTarget
    data class EditWarmup(val currentDurationSeconds: Int) : TimePickerTarget
    data class EditCooldown(val currentDurationSeconds: Int) : TimePickerTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onSetWorkoutNow: (Long) -> Unit,
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Build Custom Workout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Start Workout Now Button
                Button(
                    onClick = {
                        viewModel.finishBuildingWorkout { workoutId ->
                            onSetWorkoutNow(workoutId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START WORKOUT NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Finish Building Workout Button
                OutlinedButton(
                    onClick = {
                        viewModel.finishBuildingWorkout {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FINISH BUILDING WORKOUT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .squishyOverscroll(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Title Field
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Workout Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            item {
                // Workout Overview Summary Card
                WorkoutSummaryCard(uiState = uiState)
            }

            item {
                // Interval Section Header & Add Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Interval Sequence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Long press to drag & reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { timePickerTarget = TimePickerTarget.AddNew(IntervalType.RUN) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD RUN", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { timePickerTarget = TimePickerTarget.AddNew(IntervalType.WALK) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD WALK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Warmup Item (Always First)
            item {
                FixedIntervalCard(
                    title = "Warmup",
                    type = IntervalType.WARMUP,
                    durationSeconds = uiState.warmupDurationSeconds,
                    onEditClick = {
                        timePickerTarget = TimePickerTarget.EditWarmup(uiState.warmupDurationSeconds)
                    },
                )
            }

            // Added Middle Intervals (Runs and Walks) with smooth drag visual motion & multi-spot move support
            itemsIndexed(
                items = uiState.middleIntervals,
                key = { _, interval -> System.identityHashCode(interval) },
            ) { index, interval ->
                MiddleIntervalCard(
                    sequenceNumber = index + 2, // 1 is Warmup
                    interval = interval,
                    index = index,
                    totalCount = uiState.middleIntervals.size,
                    isFirst = index == 0,
                    isLast = index == uiState.middleIntervals.size - 1,
                    onEditClick = {
                        timePickerTarget = TimePickerTarget.EditMiddle(
                            index = index,
                            currentDurationSeconds = interval.durationSeconds,
                            type = interval.type,
                        )
                    },
                    onDeleteClick = {
                        viewModel.removeMiddleInterval(index)
                    },
                    onMove = { fromIndex, toIndex ->
                        viewModel.moveMiddleInterval(fromIndex, toIndex)
                    },
                    onMoveUp = {
                        viewModel.moveMiddleIntervalUp(index)
                    },
                    onMoveDown = {
                        viewModel.moveMiddleIntervalDown(index)
                    },
                )
            }

            // Cooldown Item (Always Last)
            item {
                FixedIntervalCard(
                    title = "Cooldown",
                    type = IntervalType.COOLDOWN,
                    durationSeconds = uiState.cooldownDurationSeconds,
                    onEditClick = {
                        timePickerTarget = TimePickerTarget.EditCooldown(uiState.cooldownDurationSeconds)
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Time Picker Dialog
    val currentTarget = timePickerTarget
    if (currentTarget != null) {
        val initialSeconds = when (currentTarget) {
            is TimePickerTarget.AddNew -> if (currentTarget.type == IntervalType.RUN) 180 else 120
            is TimePickerTarget.EditMiddle -> currentTarget.currentDurationSeconds
            is TimePickerTarget.EditWarmup -> currentTarget.currentDurationSeconds
            is TimePickerTarget.EditCooldown -> currentTarget.currentDurationSeconds
        }

        val dialogTitle = when (currentTarget) {
            is TimePickerTarget.AddNew -> if (currentTarget.type == IntervalType.RUN) "Set Run Duration" else "Set Walk Duration"
            is TimePickerTarget.EditMiddle -> if (currentTarget.type == IntervalType.RUN) "Edit Run Duration" else "Edit Walk Duration"
            is TimePickerTarget.EditWarmup -> "Edit Warmup Duration"
            is TimePickerTarget.EditCooldown -> "Edit Cooldown Duration"
        }

        DurationPickerDialog(
            title = dialogTitle,
            initialDurationSeconds = initialSeconds,
            onDismiss = { timePickerTarget = null },
            onConfirm = { selectedSeconds ->
                when (currentTarget) {
                    is TimePickerTarget.AddNew -> {
                        viewModel.addInterval(currentTarget.type, selectedSeconds)
                    }
                    is TimePickerTarget.EditMiddle -> {
                        viewModel.updateMiddleIntervalDuration(currentTarget.index, selectedSeconds)
                    }
                    is TimePickerTarget.EditWarmup -> {
                        viewModel.updateWarmupDuration(selectedSeconds)
                    }
                    is TimePickerTarget.EditCooldown -> {
                        viewModel.updateCooldownDuration(selectedSeconds)
                    }
                }
                timePickerTarget = null
            },
        )
    }
}

@Composable
private fun WorkoutSummaryCard(uiState: WorkoutBuilderUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "WORKOUT SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Duration",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTime(uiState.totalDurationSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Runs: ${uiState.runCount} (${formatTime(uiState.totalRunDurationSeconds)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Walks: ${uiState.walkCount} (${formatTime(uiState.totalWalkDurationSeconds)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FixedIntervalCard(
    title: String,
    type: IntervalType,
    durationSeconds: Int,
    onEditClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (badgeBg, badgeText) = when (type) {
                    IntervalType.WARMUP -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
                    else -> Pair(Color(0xFFE0F2F1), Color(0xFF004D40))
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = formatTime(durationSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit $title",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MiddleIntervalCard(
    sequenceNumber: Int,
    interval: WorkoutInterval,
    index: Int,
    totalCount: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredCardHeight by remember { mutableFloatStateOf(0f) }

    val currentItemIndex by rememberUpdatedState(index)
    val currentTotalCount by rememberUpdatedState(totalCount)
    val currentOnMove by rememberUpdatedState(onMove)

    val isDragging = draggingIndex >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
            }
            .onGloballyPositioned { coordinates ->
                if (coordinates.size.height > 0) {
                    measuredCardHeight = coordinates.size.height.toFloat()
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingIndex = currentItemIndex
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                        val effectiveHeight = if (measuredCardHeight > 0f) measuredCardHeight else 140f
                        val steps = (dragOffsetY / effectiveHeight).roundToInt()
                        if (steps != 0) {
                            val targetIndex = (draggingIndex + steps).coerceIn(0, currentTotalCount - 1)
                            if (targetIndex != draggingIndex) {
                                val delta = targetIndex - draggingIndex
                                currentOnMove(draggingIndex, targetIndex)
                                dragOffsetY -= delta * effectiveHeight
                                draggingIndex = targetIndex
                            }
                        }
                    },
                    onDragEnd = {
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else when (interval.type) {
                IntervalType.RUN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            },
        ),
        border = if (isDragging) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 4.dp else 0.dp,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Long press to drag",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sequenceNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    color = if (interval.type == IntervalType.RUN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = if (interval.type == IntervalType.RUN) "Run" else "Walk",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (interval.type == IntervalType.RUN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = formatTime(interval.durationSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Interval",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Interval",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPickerDialog(
    title: String,
    initialDurationSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(initialDurationSeconds / 60) }
    var seconds by remember { mutableIntStateOf(initialDurationSeconds % 60) }

    val presets = listOf(30 to "30s", 60 to "1m", 90 to "1.5m", 120 to "2m", 180 to "3m", 300 to "5m", 600 to "10m")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Preset Chips
                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (presetSeconds, label) ->
                        val isSelected = (minutes * 60 + seconds) == presetSeconds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                minutes = presetSeconds / 60
                                seconds = presetSeconds % 60
                            },
                            label = { Text(label) },
                        )
                    }
                }

                // Minutes and Seconds Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Minutes Picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minutes", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { if (minutes > 0) minutes-- },
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

                            Text(
                                text = "$minutes",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )

                            OutlinedButton(
                                onClick = { if (minutes < 60) minutes++ },
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    // Seconds Picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Seconds", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    seconds = if (seconds >= 5) seconds - 5 else 55
                                },
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

                            Text(
                                text = String.format(java.util.Locale.US, "%02d", seconds),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )

                            OutlinedButton(
                                onClick = {
                                    seconds = (seconds + 5) % 60
                                },
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val totalSecs = minutes * 60 + seconds
                    if (totalSecs > 0) {
                        onConfirm(totalSecs)
                    }
                },
            ) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
