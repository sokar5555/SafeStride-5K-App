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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val generateScheduleUseCase: GenerateScheduleUseCase,
    getCombinedScheduleUseCase: GetCombinedScheduleUseCase,
) : ViewModel() {

    val programStartDate: StateFlow<LocalDate?> = settingsRepository.programStartDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferredWorkoutDays: StateFlow<Set<DayOfWeek>> = settingsRepository.preferredWorkoutDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressiveWeeksExtension: StateFlow<Int> = settingsRepository.progressiveWeeksExtension
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longRunDay: StateFlow<DayOfWeek> = settingsRepository.longRunDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayOfWeek.SATURDAY)

    val schedule: StateFlow<List<ScheduledWorkout>> = getCombinedScheduleUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Purely ephemeral month scroll state in ViewModel memory (never persisted to DataStore)
    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

    fun setDisplayedMonth(month: YearMonth) {
        _displayedMonth.value = month
    }

    fun previousMonth() {
        _displayedMonth.value = _displayedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _displayedMonth.value = _displayedMonth.value.plusMonths(1)
    }

    fun initializeStartDateIfNotSet() {
        viewModelScope.launch {
            if (settingsRepository.programStartDate.first() == null) {
                val today = LocalDate.now()
                val days = settingsRepository.preferredWorkoutDays.first()
                val progressiveWeeks = settingsRepository.progressiveWeeksExtension.first()
                val designatedLongRunDay = settingsRepository.longRunDay.first()
                settingsRepository.setProgramStartDate(today)
                generateSchedule(today, days, progressiveWeeks, designatedLongRunDay)
            }
        }
    }

    fun updateScheduleSettings(
        startDate: LocalDate,
        days: Set<DayOfWeek>,
        progressiveWeeks: Int = progressiveWeeksExtension.value,
        designatedLongRunDay: DayOfWeek = longRunDay.value,
    ) {
        viewModelScope.launch {
            settingsRepository.setProgramStartDate(startDate)
            settingsRepository.setPreferredWorkoutDays(days)
            settingsRepository.setProgressiveWeeksExtension(progressiveWeeks)
            settingsRepository.setLongRunDay(designatedLongRunDay)
            generateSchedule(startDate, days, progressiveWeeks, designatedLongRunDay)
            _displayedMonth.value = YearMonth.from(startDate)
        }
    }

    private suspend fun generateSchedule(
        startDate: LocalDate,
        days: Set<DayOfWeek>,
        progressiveWeeks: Int,
        designatedLongRunDay: DayOfWeek,
    ) {
        generateScheduleUseCase(
            startDate = startDate,
            preferredDays = days,
            progressiveWeeksExtension = progressiveWeeks,
            longRunDay = designatedLongRunDay,
        )
    }
}
