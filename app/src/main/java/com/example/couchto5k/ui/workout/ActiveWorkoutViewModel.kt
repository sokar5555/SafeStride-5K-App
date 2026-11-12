package com.example.couchto5k.ui.workout

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.service.WorkoutTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutCompletionSummary(
    val completedWorkoutId: Long?,
    val workoutTitle: String,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val longestRunSeconds: Int,
    val isNewPersonalRecord: Boolean,
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutEngine: WorkoutEngine,
    private val repository: TrainingPlanRepository,
    private val historyRepository: WorkoutHistoryRepository,
    settingsRepository: SettingsRepository,
    private val application: Application,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle.get<Long>("workoutId")
        ?: savedStateHandle.get<String>("workoutId")?.toLongOrNull()
        ?: -1L

    val workoutState = workoutEngine.workoutState

    private val _currentWorkout = MutableStateFlow<TrainingWorkout?>(null)
    val currentWorkout: StateFlow<TrainingWorkout?> = _currentWorkout.asStateFlow()

    private val _completionSummary = MutableStateFlow<WorkoutCompletionSummary?>(null)
    val completionSummary: StateFlow<WorkoutCompletionSummary?> = _completionSummary.asStateFlow()

    val distanceUnit: StateFlow<DistanceUnit> = settingsRepository.distanceUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DistanceUnit.MILES,
        )

    private var hasSaved = false

    init {
        if (workoutId != -1L) {
            loadAndStartWorkout()
        }

        viewModelScope.launch {
            workoutState.collectLatest { state ->
                val workout = _currentWorkout.value
                if (workout != null && state.workoutId == workout.id) {
                    if (state.status == WorkoutEngineStatus.COMPLETED) {
                        saveWorkoutHistory(workout, state, isCompleted = true)
                    } else if (state.status == WorkoutEngineStatus.CANCELLED && state.totalElapsedMillis > 0) {
                        saveWorkoutHistory(workout, state, isCompleted = false)
                    }
                }
            }
        }
    }

    private fun saveWorkoutHistory(
        workout: TrainingWorkout,
        state: ActiveWorkoutState,
        isCompleted: Boolean,
    ) {
        if (hasSaved) return
        hasSaved = true
        viewModelScope.launch {
            val existingHistory = historyRepository.getHistory().first()
            val previousBestLongestRun = existingHistory.maxOfOrNull {
                it.workout.longestCompletedRunSeconds
            } ?: 0

            historyRepository.saveCompletedWorkout(workout, state)

            if (isCompleted) {
                val updatedHistory = historyRepository.getHistory().first()
                val latestSaved = updatedHistory.firstOrNull()?.workout

                val durationSeconds = latestSaved?.durationSeconds
                    ?: maxOf((state.totalElapsedMillis / 1000).toInt(), workout.totalPlannedDurationSeconds)
                val longestRunSeconds = latestSaved?.longestCompletedRunSeconds
                    ?: workout.longestPlannedRunIntervalSeconds
                val distanceMeters = latestSaved?.distanceMeters ?: state.distanceMeters
                val isNewRecord = (longestRunSeconds > 0) && (longestRunSeconds > previousBestLongestRun)

                _completionSummary.value = WorkoutCompletionSummary(
                    completedWorkoutId = latestSaved?.id,
                    workoutTitle = workout.title,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    longestRunSeconds = longestRunSeconds,
                    isNewPersonalRecord = isNewRecord,
                )
            }
        }
    }

    private fun loadAndStartWorkout() {
        viewModelScope.launch {
            val workout = if (workoutId == WorkoutEngine.FREE_RUN_WORKOUT_ID) {
                WorkoutEngine.FREE_RUN_WORKOUT
            } else {
                repository.getWorkoutById(workoutId)
            }
            if (workout != null) {
                _currentWorkout.value = workout
                workoutEngine.start(workout, isFreeRun = workout.id == WorkoutEngine.FREE_RUN_WORKOUT_ID)

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
    fun completeFreeRun() = workoutEngine.completeFreeRun()
    fun stop() = sendServiceCommand(WorkoutTrackingService.ACTION_STOP)

    override fun onCleared() {
        super.onCleared()
        // If the service is running, we let it continue. We DO NOT stop it on VM clear!
    }
}
