package com.example.couchto5k.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.usecase.GenerateScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val generateScheduleUseCase: GenerateScheduleUseCase?,
    @Suppress("UNUSED_PARAMETER") unusedMarker: Boolean = true,
) : ViewModel() {

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        generateScheduleUseCase: GenerateScheduleUseCase,
    ) : this(
        settingsRepository = settingsRepository,
        generateScheduleUseCase = generateScheduleUseCase,
        unusedMarker = true,
    )

    constructor(
        settingsRepository: SettingsRepository,
    ) : this(
        settingsRepository = settingsRepository,
        generateScheduleUseCase = null,
        unusedMarker = true,
    )

    val onboardingComplete: StateFlow<Boolean> = settingsRepository.onboardingComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val isOnboardingCompleteLoaded: StateFlow<Boolean?> = settingsRepository.onboardingComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _selectedDistanceUnit = MutableStateFlow(DistanceUnit.MILES)
    val selectedDistanceUnit: StateFlow<DistanceUnit> = _selectedDistanceUnit.asStateFlow()

    private val _selectedWorkoutDays = MutableStateFlow(
        setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
    )
    val selectedWorkoutDays: StateFlow<Set<DayOfWeek>> = _selectedWorkoutDays.asStateFlow()

    private val _selectedStartDate = MutableStateFlow(LocalDate.now())
    val selectedStartDate: StateFlow<LocalDate> = _selectedStartDate.asStateFlow()

    fun selectDistanceUnit(unit: DistanceUnit) {
        _selectedDistanceUnit.value = unit
    }

    fun setWorkoutDays(days: Set<DayOfWeek>) {
        _selectedWorkoutDays.value = days
    }

    fun toggleWorkoutDay(day: DayOfWeek) {
        val current = _selectedWorkoutDays.value
        _selectedWorkoutDays.value = if (current.contains(day)) {
            if (current.size > 1) current - day else current
        } else {
            if (current.size < 3) {
                current + day
            } else {
                current.drop(1).toSet() + day
            }
        }
    }

    fun selectStartDate(date: LocalDate) {
        _selectedStartDate.value = date
    }

    fun nextStep() {
        if (_currentStep.value < 2) {
            _currentStep.value += 1
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value -= 1
        }
    }

    fun completeOnboarding(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val unit = _selectedDistanceUnit.value
            val days = _selectedWorkoutDays.value.ifEmpty {
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
            }
            val startDate = _selectedStartDate.value

            settingsRepository.setDistanceUnit(unit)
            settingsRepository.setPreferredWorkoutDays(days)
            settingsRepository.setProgramStartDate(startDate)
            generateScheduleUseCase?.invoke(startDate, days)
            settingsRepository.setOnboardingComplete(true)
            onComplete()
        }
    }
}
