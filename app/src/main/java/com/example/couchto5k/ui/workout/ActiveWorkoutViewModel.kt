package com.example.couchto5k.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutEngine: WorkoutEngine,
    private val repository: TrainingPlanRepository,
    private val historyRepository: WorkoutHistoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle.get<Long>("workoutId")
        ?: savedStateHandle.get<String>("workoutId")?.toLongOrNull()
        ?: -1L

    val workoutState = workoutEngine.workoutState

    private val _currentWorkout = MutableStateFlow<TrainingWorkout?>(null)
    val currentWorkout: StateFlow<TrainingWorkout?> = _currentWorkout.asStateFlow()

    init {
        if (workoutId != -1L) {
            loadAndStartWorkout()
        }

        viewModelScope.launch {
            workoutState.collectLatest { state ->
                if (state.status == WorkoutEngineStatus.COMPLETED || state.status == WorkoutEngineStatus.CANCELLED) {
                    val workout = _currentWorkout.value
                    if (workout != null && state.totalElapsedMillis > 0) {
                        saveWorkoutHistory(workout, state)
                    }
                }
            }
        }
    }

    private var hasSaved = false
    private fun saveWorkoutHistory(workout: TrainingWorkout, state: com.example.couchto5k.engine.ActiveWorkoutState) {
        if (hasSaved) return
        hasSaved = true
        viewModelScope.launch {
            historyRepository.saveCompletedWorkout(workout, state)
        }
    }

    private fun loadAndStartWorkout() {
        viewModelScope.launch {
            val workout = repository.getWorkoutById(workoutId)
            if (workout != null) {
                _currentWorkout.value = workout
                workoutEngine.start(workout)
            }
        }
    }

    fun pause() = workoutEngine.pause()
    fun resume() = workoutEngine.resume()
    fun skipNext() = workoutEngine.skipToNextInterval()
    fun skipPrevious() = workoutEngine.previousInterval()
    fun stop() = workoutEngine.stopWorkout()

    override fun onCleared() {
        super.onCleared()
        // Stop workout engine if it's still running when VM is cleared
        workoutEngine.stopWorkout()
    }
}
