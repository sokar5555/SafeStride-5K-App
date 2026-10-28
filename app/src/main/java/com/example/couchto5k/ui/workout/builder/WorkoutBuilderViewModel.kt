package com.example.couchto5k.ui.workout.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutBuilderUiState(
    val title: String = "Custom Workout",
    val warmupDurationSeconds: Int = 300,
    val cooldownDurationSeconds: Int = 300,
    val middleIntervals: List<WorkoutInterval> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalRunDurationSeconds: Int
        get() = middleIntervals.sumOf { if (it.type == IntervalType.RUN) it.durationSeconds else 0 }

    val totalWalkDurationSeconds: Int
        get() = middleIntervals.sumOf { if (it.type == IntervalType.WALK) it.durationSeconds else 0 }

    val totalDurationSeconds: Int
        get() = warmupDurationSeconds + cooldownDurationSeconds + totalRunDurationSeconds + totalWalkDurationSeconds

    val runCount: Int
        get() = middleIntervals.count { it.type == IntervalType.RUN }

    val walkCount: Int
        get() = middleIntervals.count { it.type == IntervalType.WALK }

    val allIntervals: List<WorkoutInterval>
        get() {
            var seq = 0
            val list = mutableListOf<WorkoutInterval>()
            if (warmupDurationSeconds > 0) {
                list.add(WorkoutInterval(sequence = seq++, type = IntervalType.WARMUP, durationSeconds = warmupDurationSeconds))
            }
            middleIntervals.forEach { interval ->
                list.add(interval.copy(sequence = seq++))
            }
            if (cooldownDurationSeconds > 0) {
                list.add(WorkoutInterval(sequence = seq, type = IntervalType.COOLDOWN, durationSeconds = cooldownDurationSeconds))
            }
            return list
        }
}

@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    private val trainingPlanRepository: TrainingPlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutBuilderUiState())
    val uiState: StateFlow<WorkoutBuilderUiState> = _uiState.asStateFlow()

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun updateWarmupDuration(seconds: Int) {
        _uiState.update { it.copy(warmupDurationSeconds = seconds.coerceAtLeast(0)) }
    }

    fun updateCooldownDuration(seconds: Int) {
        _uiState.update { it.copy(cooldownDurationSeconds = seconds.coerceAtLeast(0)) }
    }

    fun addInterval(type: IntervalType, durationSeconds: Int) {
        if (durationSeconds <= 0) return
        _uiState.update { state ->
            val updated = state.middleIntervals.toMutableList()
            updated.add(WorkoutInterval(sequence = updated.size, type = type, durationSeconds = durationSeconds))
            state.copy(middleIntervals = updated, errorMessage = null)
        }
    }

    fun updateMiddleIntervalDuration(index: Int, newDurationSeconds: Int) {
        _uiState.update { state ->
            if (index in state.middleIntervals.indices) {
                val updated = state.middleIntervals.toMutableList()
                if (newDurationSeconds > 0) {
                    updated[index] = updated[index].copy(durationSeconds = newDurationSeconds)
                } else {
                    updated.removeAt(index)
                }
                state.copy(middleIntervals = updated)
            } else {
                state
            }
        }
    }

    fun removeMiddleInterval(index: Int) {
        _uiState.update { state ->
            if (index in state.middleIntervals.indices) {
                val updated = state.middleIntervals.toMutableList()
                updated.removeAt(index)
                state.copy(middleIntervals = updated)
            } else {
                state
            }
        }
    }

    fun moveMiddleInterval(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if ((fromIndex in state.middleIntervals.indices) && (toIndex in state.middleIntervals.indices) && (fromIndex != toIndex)) {
                val list = state.middleIntervals.toMutableList()
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                state.copy(middleIntervals = list)
            } else {
                state
            }
        }
    }

    fun moveMiddleIntervalUp(index: Int) {
        if (index > 0) {
            moveMiddleInterval(index, index - 1)
        }
    }

    fun moveMiddleIntervalDown(index: Int) {
        if (index < _uiState.value.middleIntervals.size - 1) {
            moveMiddleInterval(index, index + 1)
        }
    }

    fun finishBuildingWorkout(onSuccess: (Long) -> Unit) {
        val currentState = _uiState.value
        if (currentState.middleIntervals.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please add at least one Run or Walk interval.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val titleToSave = currentState.title.ifBlank { "Custom Workout" }
            val newId = trainingPlanRepository.saveCustomWorkout(titleToSave, currentState.allIntervals)
            _uiState.update { it.copy(isSaving = false) }
            onSuccess(newId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
