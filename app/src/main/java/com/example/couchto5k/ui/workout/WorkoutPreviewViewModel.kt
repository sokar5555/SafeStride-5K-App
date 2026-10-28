package com.example.couchto5k.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutPreviewViewModel @Inject constructor(
    private val repository: TrainingPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle.get<Long>("workoutId")
        ?: savedStateHandle.get<String>("workoutId")?.toLongOrNull()
        ?: -1L

    private val _workout = MutableStateFlow<TrainingWorkout?>(null)
    val workout: StateFlow<TrainingWorkout?> = _workout.asStateFlow()

    init {
        if (workoutId != -1L) {
            viewModelScope.launch {
                _workout.value = repository.getWorkoutById(workoutId)
            }
        }
    }
}
