package com.example.couchto5k.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.ScheduledWorkout
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.usecase.GenerateScheduleUseCase
import com.example.couchto5k.domain.usecase.GetCombinedScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val generateScheduleUseCase: GenerateScheduleUseCase,
    getCombinedScheduleUseCase: GetCombinedScheduleUseCase
) : ViewModel() {

    val programStartDate: StateFlow<LocalDate?> = settingsRepository.programStartDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferredWorkoutDays: StateFlow<Set<DayOfWeek>> = settingsRepository.preferredWorkoutDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val schedule: StateFlow<List<ScheduledWorkout>> = getCombinedScheduleUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initializeStartDateIfNotSet() {
        viewModelScope.launch {
            if (settingsRepository.programStartDate.first() == null) {
                val today = LocalDate.now()
                settingsRepository.setProgramStartDate(today)
                generateSchedule(today, settingsRepository.preferredWorkoutDays.first())
            }
        }
    }

    fun updateStartDate(date: LocalDate) {
        viewModelScope.launch {
            settingsRepository.setProgramStartDate(date)
            generateSchedule(date, preferredWorkoutDays.value)
        }
    }

    fun updatePreferredDays(days: Set<DayOfWeek>) {
        viewModelScope.launch {
            settingsRepository.setPreferredWorkoutDays(days)
            val startDate = programStartDate.value ?: LocalDate.now()
            generateSchedule(startDate, days)
        }
    }

    private suspend fun generateSchedule(startDate: LocalDate, days: Set<DayOfWeek>) {
        generateScheduleUseCase(startDate, days)
    }
}
