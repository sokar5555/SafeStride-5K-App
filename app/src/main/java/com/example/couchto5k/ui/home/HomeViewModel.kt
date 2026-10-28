package com.example.couchto5k.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trainingPlanRepository: TrainingPlanRepository,
) : ViewModel() {

    val customWorkouts: StateFlow<List<TrainingWorkout>> =
        trainingPlanRepository.getCustomWorkouts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    fun deleteCustomWorkout(workoutId: Long) {
        viewModelScope.launch {
            trainingPlanRepository.deleteCustomWorkout(workoutId)
        }
    }

    fun clearAllCustomWorkouts() {
        viewModelScope.launch {
            trainingPlanRepository.clearAllCustomWorkouts()
        }
    }
}
