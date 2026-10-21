package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.engine.ActiveWorkoutState
import kotlinx.coroutines.flow.Flow

interface WorkoutHistoryRepository {
    fun getHistory(): Flow<List<CompletedWorkoutDetails>>
    suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails?
    suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState)
    suspend fun deleteWorkout(id: Long)
}
