package com.example.couchto5k.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.ui.components.AppCardRole
import com.example.couchto5k.ui.components.AppHeaderBanner
import com.example.couchto5k.ui.components.AppSectionCard
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val voiceCuesEnabled by viewModel.voiceCuesEnabled.collectAsState()
    val countdownVoiceCuesEnabled by viewModel.countdownVoiceCuesEnabled.collectAsState()
    val audioDuckingEnabled by viewModel.audioDuckingEnabled.collectAsState()
    val soundCuesEnabled by viewModel.soundCuesEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val preferredDays by viewModel.preferredWorkoutDays.collectAsState()
    val programStartDate by viewModel.programStartDate.collectAsState()
    val progressiveWeeks by viewModel.progressiveWeeksExtension.collectAsState()
    val longRunDay by viewModel.longRunDay.collectAsState()
    val backupUiState by viewModel.backupUiState.collectAsState()

    var showDatePicker by remember { mutableStateOf(value = false) }
    var showResetConfirmDialog by remember { mutableStateOf(value = false) }
    var showResetSuccessBanner by remember { mutableStateOf(value = false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportBackup(it.toString()) }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.prepareImportBackup(it.toString()) }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy") }
    val extensionOptions = listOf(0, 4, 8, 12)

    Scaffold(
        topBar = {
            AppHeaderBanner(
                title = "Settings",
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(visible = showResetSuccessBanner) {
                AppSectionCard(
                    role = AppCardRole.SUCCESS,
                    cornerRadius = 12.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Program schedule and workout history have been reset.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showResetSuccessBanner = false }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // 1. Appearance Section
            AppSectionCard(
                title = "Appearance",
                icon = Icons.Default.Palette,
                iconContentDescription = "Appearance theme icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Choose Light, Dark, or follow your device system theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val themeOptions = listOf(
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                        ThemeMode.SYSTEM to "System",
                    )
                    themeOptions.forEach { (mode, label) ->
                        val isSelected = themeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "$label theme selected",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        )
                    }
                }
            }

            // 2. Distance Units Section
            AppSectionCard(
                title = "Distance Units",
                icon = Icons.Default.Straighten,
                iconContentDescription = "Distance units icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Choose how distance and average pace are displayed across workouts and stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterChip(
                        selected = distanceUnit == DistanceUnit.MILES,
                        onClick = { viewModel.setDistanceUnit(DistanceUnit.MILES) },
                        label = {
                            Text(
                                text = "Miles (mi)",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        leadingIcon = if (distanceUnit == DistanceUnit.MILES) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Miles selected",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    )

                    FilterChip(
                        selected = distanceUnit == DistanceUnit.KILOMETERS,
                        onClick = { viewModel.setDistanceUnit(DistanceUnit.KILOMETERS) },
                        label = {
                            Text(
                                text = "Kilometers (km)",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        leadingIcon = if (distanceUnit == DistanceUnit.KILOMETERS) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Kilometers selected",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    )
                }
            }

            // 3. Audio & Haptics Section
            AppSectionCard(
                title = "Audio & Haptic Cues",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconContentDescription = "Audio and haptic cues icon",
                role = AppCardRole.CONTENT,
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.RecordVoiceOver,
                    iconDescription = "Voice cues",
                    title = "Voice Cues",
                    subtitle = "Spoken announcements at each interval and mile/km split",
                    checked = voiceCuesEnabled,
                    onCheckedChange = viewModel::setVoiceCuesEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    icon = Icons.Default.Timer,
                    iconDescription = "Countdown voice cues",
                    title = "Countdown Voice Cues",
                    subtitle = if (voiceCuesEnabled) {
                        "Announce 30s, 10s, and 5s remaining during structured intervals"
                    } else {
                        "Turn on Voice Cues above to enable interval countdown announcements"
                    },
                    checked = countdownVoiceCuesEnabled,
                    enabled = voiceCuesEnabled,
                    onCheckedChange = viewModel::setCountdownVoiceCuesEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    icon = Icons.Default.GraphicEq,
                    iconDescription = "Duck background audio",
                    title = "Duck Background Audio",
                    subtitle = "Briefly lower music or podcast volume while cues play",
                    checked = audioDuckingEnabled,
                    onCheckedChange = viewModel::setAudioDuckingEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    iconDescription = "Sound cues",
                    title = "Sound Cues",
                    subtitle = "Audible tone alert when switching intervals or completing splits",
                    checked = soundCuesEnabled,
                    onCheckedChange = viewModel::setSoundCuesEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    icon = Icons.Default.Vibration,
                    iconDescription = "Vibration cues",
                    title = "Vibration",
                    subtitle = "Haptic vibration feedback at interval transitions and splits",
                    checked = vibrationEnabled,
                    onCheckedChange = viewModel::setVibrationEnabled,
                )
            }

            // 4. Schedule Preferences Section
            AppSectionCard(
                title = "Schedule Preferences",
                icon = Icons.Default.CalendarMonth,
                iconContentDescription = "Schedule preferences icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Preferred Workout Days (Select 3 days/week)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val isSelected = preferredDays.contains(day)
                        val shortLabel = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = if (isSelected) {
                                    if (preferredDays.size > 1) preferredDays - day else preferredDays
                                } else {
                                    if (preferredDays.size < 3) {
                                        preferredDays + day
                                    } else {
                                        preferredDays.asSequence().drop(1).toSet() + day
                                    }
                                }
                                viewModel.setPreferredWorkoutDays(updated)
                            },
                            label = {
                                Text(
                                    text = shortLabel,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "$shortLabel selected",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = preferredDays.size != 3) {
                    Text(
                        text = "Select 3 days per week for your Couch to 5K schedule (${preferredDays.size}/3 selected).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Program Start Date",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val displayedDate = programStartDate ?: LocalDate.now()
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Choose program start date",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayedDate.format(dateFormatter),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 5. Progressive Plan Extension (+10% Weekly Volume Beyond 5K)
            AppSectionCard(
                title = "Progressive Plan Extension (Beyond 5K)",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconContentDescription = "Progressive plan extension icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Extend your calendar beyond Week 9 using the +10% weekly running volume rule with a designated Long Run day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    extensionOptions.forEach { weeks ->
                        val isSelected = progressiveWeeks == weeks
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setProgressiveWeeksExtension(weeks) },
                            label = {
                                Text(
                                    text = if (weeks == 0) "Standard 5K (9 Wks)" else "+$weeks Weeks",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = progressiveWeeks > 0) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Designated Long Run Day",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val candidateDays = if (preferredDays.isNotEmpty()) {
                                preferredDays.sortedBy { it.value }
                            } else {
                                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                            }
                            candidateDays.forEach { day ->
                                val isSelected = longRunDay == day
                                val shortLabel = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setLongRunDay(day) },
                                    label = {
                                        Text(
                                            text = shortLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "$shortLabel long run day",
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 6. Data Backup (JSON) Section
            AppSectionCard(
                title = "Data Backup (JSON)",
                icon = Icons.Default.SaveAlt,
                iconContentDescription = "Data backup icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Export all completed workouts, GPS route points, custom workouts, schedule, and preferences to JSON, or restore from a backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (val state = backupUiState) {
                    is BackupUiState.Working -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    is BackupUiState.Success -> {
                        AppSectionCard(
                            role = AppCardRole.SUCCESS,
                            cornerRadius = 12.dp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearBackupStatus) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    is BackupUiState.Error -> {
                        AppSectionCard(
                            role = AppCardRole.ALERT,
                            cornerRadius = 12.dp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearBackupStatus) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    BackupUiState.Idle,
                    is BackupUiState.PendingRestoreConfirmation,
                    -> Unit
                }

                val isBackupBusy = backupUiState is BackupUiState.Working

                Button(
                    onClick = {
                        exportBackupLauncher.launch("safestride-5k-backup-${LocalDate.now()}.json")
                    },
                    enabled = !isBackupBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Export JSON backup",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXPORT BACKUP (JSON)",
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    enabled = !isBackupBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import JSON backup",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "IMPORT / RESTORE (JSON)",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 7. Reset Program / Clear Data Section
            AppSectionCard(
                title = "Reset Program & Data",
                icon = Icons.Default.DeleteForever,
                iconContentDescription = "Reset program data icon",
                role = AppCardRole.CONTENT,
            ) {
                Text(
                    text = "Clear all completed workout history and reset your training schedule back to Week 1, Workout 1 starting today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Reset program and clear data",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RESET PROGRAM / CLEAR DATA",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    val pendingRestore = (backupUiState as? BackupUiState.PendingRestoreConfirmation)?.backup
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirmation,
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "Restoring this JSON backup will replace all current workout history, custom workouts, schedule, and settings.\n\n" +
                        "Backup contents:\n" +
                        "• ${pendingRestore.completedWorkouts.size} completed workouts\n" +
                        "• ${pendingRestore.customWorkouts.size} custom workouts\n" +
                        "• ${pendingRestore.scheduledWorkouts.size} scheduled entries",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImportBackup) {
                    Text("Restore Data")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestoreConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDatePicker) {
        val initialDate = programStartDate ?: LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            viewModel.setProgramStartDate(newDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset Program & Clear Data?") },
            text = {
                Text(
                    "Are you sure you want to clear all workout history and reset your training schedule? This action cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetProgramData()
                        showResetConfirmDialog = false
                        showResetSuccessBanner = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Reset Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconDescription: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
