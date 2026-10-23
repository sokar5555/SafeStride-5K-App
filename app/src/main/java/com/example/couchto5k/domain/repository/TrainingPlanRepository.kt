package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.TrainingWorkout
import kotlinx.coroutines.flow.Flow

interface TrainingPlanRepository {
    fun getAllWorkouts(): Flow<List<TrainingWorkout>>
    suspend fun getWorkoutById(id: Long): TrainingWorkout?
}
