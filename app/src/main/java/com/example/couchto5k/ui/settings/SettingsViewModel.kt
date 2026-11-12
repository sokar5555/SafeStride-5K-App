package com.example.couchto5k.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.domain.usecase.GenerateScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository?,
    private val generateScheduleUseCase: GenerateScheduleUseCase?,
    @Suppress("UNUSED_PARAMETER") unusedMarker: Boolean = true,
) : ViewModel() {

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        workoutHistoryRepository: WorkoutHistoryRepository,
        generateScheduleUseCase: GenerateScheduleUseCase,
    ) : this(
        settingsRepository = settingsRepository,
        workoutHistoryRepository = workoutHistoryRepository,
        generateScheduleUseCase = generateScheduleUseCase,
        unusedMarker = true,
    )

    constructor(
        settingsRepository: SettingsRepository,
        workoutHistoryRepository: WorkoutHistoryRepository? = null,
    ) : this(
        settingsRepository = settingsRepository,
        workoutHistoryRepository = workoutHistoryRepository,
        generateScheduleUseCase = null,
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

    fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {
        viewModelScope.launch {
            settingsRepository.setPreferredWorkoutDays(days)
            val startDate = settingsRepository.programStartDate.first() ?: LocalDate.now()
            if (days.size == 3) {
                generateScheduleUseCase?.invoke(startDate, days)
            }
        }
    }

    fun setProgramStartDate(date: LocalDate) {
        viewModelScope.launch {
            settingsRepository.setProgramStartDate(date)
            val days = settingsRepository.preferredWorkoutDays.first()
            if (days.isNotEmpty()) {
                generateScheduleUseCase?.invoke(date, days)
            }
        }
    }

    fun resetProgramData() {
        viewModelScope.launch {
            workoutHistoryRepository?.clearAllHistory()
            val today = LocalDate.now()
            val defaultDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
            settingsRepository.setProgramStartDate(today)
            settingsRepository.setPreferredWorkoutDays(defaultDays)
            generateScheduleUseCase?.invoke(today, defaultDays)
        }
    }
}
