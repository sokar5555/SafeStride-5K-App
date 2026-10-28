package com.example.couchto5k.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repository: WorkoutHistoryRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val completedWorkoutId: Long = savedStateHandle.get<Long>("completedWorkoutId") ?: -1L

    private val _workoutDetails = MutableStateFlow<CompletedWorkoutDetails?>(null)
    val workoutDetails: StateFlow<CompletedWorkoutDetails?> = _workoutDetails.asStateFlow()

    val distanceUnit: StateFlow<DistanceUnit> = settingsRepository.distanceUnit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DistanceUnit.MILES,
    )

    init {
        if (completedWorkoutId != -1L) {
            viewModelScope.launch {
                _workoutDetails.value = repository.getWorkoutDetails(completedWorkoutId)
            }
        }
    }
}
