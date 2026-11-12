package com.example.couchto5k.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.ProgramProgress
import com.example.couchto5k.domain.model.RunningProgression
import com.example.couchto5k.domain.model.UserStats
import com.example.couchto5k.domain.model.WeeklyDistancePoint
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.usecase.GetStatsUseCase
import com.example.couchto5k.domain.usecase.TimeOfDayCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface StatsUiState {
    object Loading : StatsUiState
    object Empty : StatsUiState
    data class Success(
        val userStats: UserStats,
        val runningProgression: RunningProgression,
        val weeklyDistance: List<WeeklyDistancePoint>,
        val timeOfDayDistribution: List<TimeOfDayCount>,
        val programProgress: ProgramProgress,
        val distanceUnit: DistanceUnit,
    ) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    getStatsUseCase: GetStatsUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        getStatsUseCase.invoke(),
        settingsRepository.distanceUnit,
    ) { statsData, distanceUnit ->
        if (statsData == null) {
            StatsUiState.Empty
        } else {
            StatsUiState.Success(
                userStats = statsData.userStats,
                runningProgression = statsData.runningProgression,
                weeklyDistance = statsData.weeklyDistance,
                timeOfDayDistribution = statsData.timeOfDayDistribution,
                programProgress = statsData.programProgress,
                distanceUnit = distanceUnit
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState.Loading
    )
}
