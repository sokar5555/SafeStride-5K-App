package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import kotlinx.coroutines.flow.Flow

interface TrainingPlanRepository {
    fun getAllWorkouts(): Flow<List<TrainingWorkout>>
    fun getCustomWorkouts(): Flow<List<TrainingWorkout>>
    suspend fun getWorkoutById(id: Long): TrainingWorkout?
    suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long
    suspend fun deleteCustomWorkout(id: Long)
}
