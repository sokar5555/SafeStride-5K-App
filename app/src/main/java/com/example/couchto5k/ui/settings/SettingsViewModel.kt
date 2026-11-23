package com.example.couchto5k.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.domain.repository.BackupRepository
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.domain.usecase.GenerateScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class PendingRestoreConfirmation(val backup: AppBackupData) : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository?,
    private val generateScheduleUseCase: GenerateScheduleUseCase?,
    private val backupRepository: BackupRepository?,
    @Suppress("UNUSED_PARAMETER") unusedMarker: Boolean = true,
) : ViewModel() {

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        workoutHistoryRepository: WorkoutHistoryRepository,
        generateScheduleUseCase: GenerateScheduleUseCase,
        backupRepository: BackupRepository,
    ) : this(
        settingsRepository = settingsRepository,
        workoutHistoryRepository = workoutHistoryRepository,
        generateScheduleUseCase = generateScheduleUseCase,
        backupRepository = backupRepository,
        unusedMarker = true,
    )

    constructor(
        settingsRepository: SettingsRepository,
        workoutHistoryRepository: WorkoutHistoryRepository? = null,
        backupRepository: BackupRepository? = null,
    ) : this(
        settingsRepository = settingsRepository,
        workoutHistoryRepository = workoutHistoryRepository,
        generateScheduleUseCase = null,
        backupRepository = backupRepository,
        unusedMarker = true,
    )

    val distanceUnit: StateFlow<DistanceUnit> = settingsRepository.distanceUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DistanceUnit.MILES,
        )

    val voiceCuesEnabled: StateFlow<Boolean> = settingsRepository.voiceCuesEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    val soundCuesEnabled: StateFlow<Boolean> = settingsRepository.soundCuesEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    val audioDuckingEnabled: StateFlow<Boolean> = settingsRepository.audioDuckingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    val countdownVoiceCuesEnabled: StateFlow<Boolean> = settingsRepository.countdownVoiceCuesEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.LIGHT,
        )

    val preferredWorkoutDays: StateFlow<Set<DayOfWeek>> = settingsRepository.preferredWorkoutDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        )

    val programStartDate: StateFlow<LocalDate?> = settingsRepository.programStartDate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val progressiveWeeksExtension: StateFlow<Int> = settingsRepository.progressiveWeeksExtension
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0,
        )

    val longRunDay: StateFlow<DayOfWeek> = settingsRepository.longRunDay
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DayOfWeek.SATURDAY,
        )

    private val _backupUiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            settingsRepository.setDistanceUnit(unit)
        }
    }

    fun toggleDistanceUnit() {
        val nextUnit = if (distanceUnit.value == DistanceUnit.MILES) {
            DistanceUnit.KILOMETERS
        } else {
            DistanceUnit.MILES
        }
        setDistanceUnit(nextUnit)
    }

    fun setVoiceCuesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVoiceCuesEnabled(enabled)
        }
    }

    fun setSoundCuesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSoundCuesEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrationEnabled(enabled)
        }
    }

    fun setAudioDuckingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAudioDuckingEnabled(enabled)
        }
    }

    fun setCountdownVoiceCuesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCountdownVoiceCuesEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {
        viewModelScope.launch {
            settingsRepository.setPreferredWorkoutDays(days)
            val startDate = settingsRepository.programStartDate.first() ?: LocalDate.now()
            val progressiveWeeks = settingsRepository.progressiveWeeksExtension.first()
            val designatedLongRunDay = settingsRepository.longRunDay.first()
            if (days.size == 3) {
                generateScheduleUseCase?.invoke(startDate, days, progressiveWeeks, designatedLongRunDay)
            }
        }
    }

    fun setProgramStartDate(date: LocalDate) {
        viewModelScope.launch {
            settingsRepository.setProgramStartDate(date)
            val days = settingsRepository.preferredWorkoutDays.first()
            val progressiveWeeks = settingsRepository.progressiveWeeksExtension.first()
            val designatedLongRunDay = settingsRepository.longRunDay.first()
            if (days.isNotEmpty()) {
                generateScheduleUseCase?.invoke(date, days, progressiveWeeks, designatedLongRunDay)
            }
        }
    }

    fun setProgressiveWeeksExtension(weeks: Int) {
        viewModelScope.launch {
            settingsRepository.setProgressiveWeeksExtension(weeks)
            val startDate = settingsRepository.programStartDate.first() ?: LocalDate.now()
            val days = settingsRepository.preferredWorkoutDays.first()
            val designatedLongRunDay = settingsRepository.longRunDay.first()
            if (days.isNotEmpty()) {
                generateScheduleUseCase?.invoke(startDate, days, weeks, designatedLongRunDay)
            }
        }
    }

    fun setLongRunDay(day: DayOfWeek) {
        viewModelScope.launch {
            settingsRepository.setLongRunDay(day)
            val startDate = settingsRepository.programStartDate.first() ?: LocalDate.now()
            val days = settingsRepository.preferredWorkoutDays.first()
            val progressiveWeeks = settingsRepository.progressiveWeeksExtension.first()
            if (days.isNotEmpty()) {
                generateScheduleUseCase?.invoke(startDate, days, progressiveWeeks, day)
            }
        }
    }

    fun exportBackup(uriString: String) {
        val repo = backupRepository
        if (repo == null) {
            _backupUiState.value = BackupUiState.Error("Backup service is unavailable.")
            return
        }
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working("Exporting JSON backup...")
            repo.exportBackupToUri(uriString)
                .onSuccess { summary ->
                    _backupUiState.value = BackupUiState.Success(
                        "Exported ${summary.completedWorkoutsCount} workouts and ${summary.customWorkoutsCount} custom workouts.",
                    )
                }
                .onFailure { error ->
                    _backupUiState.value = BackupUiState.Error(
                        error.message ?: "Failed to export backup.",
                    )
                }
        }
    }

    fun prepareImportBackup(uriString: String) {
        val repo = backupRepository
        if (repo == null) {
            _backupUiState.value = BackupUiState.Error("Backup service is unavailable.")
            return
        }
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working("Validating backup file...")
            repo.parseAndValidateBackupFromUri(uriString)
                .onSuccess { backup ->
                    _backupUiState.value = BackupUiState.PendingRestoreConfirmation(backup)
                }
                .onFailure { error ->
                    _backupUiState.value = BackupUiState.Error(
                        error.message ?: "Invalid or unreadable JSON backup.",
                    )
                }
        }
    }

    fun confirmImportBackup() {
        val pending = (_backupUiState.value as? BackupUiState.PendingRestoreConfirmation)?.backup ?: return
        val repo = backupRepository
        if (repo == null) {
            _backupUiState.value = BackupUiState.Error("Backup service is unavailable.")
            return
        }
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working("Restoring backup data...")
            repo.restoreBackup(pending)
                .onSuccess { summary ->
                    _backupUiState.value = BackupUiState.Success(
                        "Restored ${summary.completedWorkoutsCount} workouts, ${summary.customWorkoutsCount} custom workouts, and settings.",
                    )
                }
                .onFailure { error ->
                    _backupUiState.value = BackupUiState.Error(
                        error.message ?: "Failed to restore backup.",
                    )
                }
        }
    }

    fun dismissRestoreConfirmation() {
        if (_backupUiState.value is BackupUiState.PendingRestoreConfirmation) {
            _backupUiState.value = BackupUiState.Idle
        }
    }

    fun clearBackupStatus() {
        _backupUiState.value = BackupUiState.Idle
    }

    fun resetProgramData() {
        viewModelScope.launch {
            workoutHistoryRepository?.clearAllHistory()
            val today = LocalDate.now()
            val defaultDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
            settingsRepository.setProgramStartDate(today)
            settingsRepository.setPreferredWorkoutDays(defaultDays)
            settingsRepository.setProgressiveWeeksExtension(0)
            settingsRepository.setLongRunDay(DayOfWeek.SATURDAY)
            generateScheduleUseCase?.invoke(today, defaultDays, 0, DayOfWeek.SATURDAY)
        }
    }
}
