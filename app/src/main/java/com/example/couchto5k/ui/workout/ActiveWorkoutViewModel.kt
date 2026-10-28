package com.example.couchto5k.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.service.WorkoutTrackingService
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
    private val application: Application,
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

                val startIntent = Intent(application, WorkoutTrackingService::class.java).apply {
                    action = WorkoutTrackingService.ACTION_START
                }
                ContextCompat.startForegroundService(application, startIntent)
            }
        }
    }

    private fun sendServiceCommand(action: String) {
        val intent = Intent(application, WorkoutTrackingService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(application, intent)
    }

    fun pause() = sendServiceCommand(WorkoutTrackingService.ACTION_PAUSE)
    fun resume() = sendServiceCommand(WorkoutTrackingService.ACTION_RESUME)
    fun skipNext() = workoutEngine.skipToNextInterval()
    fun skipPrevious() = workoutEngine.previousInterval()
    fun stop() = sendServiceCommand(WorkoutTrackingService.ACTION_STOP)

    override fun onCleared() {
        super.onCleared()
        // If the service is running, we let it continue. We DO NOT stop it on VM clear!
    }
}
