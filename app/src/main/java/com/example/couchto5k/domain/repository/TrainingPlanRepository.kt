package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.TrainingWorkout

interface TrainingPlanRepository {
    suspend fun getAllWorkouts(): List<TrainingWorkout>
    suspend fun getWorkoutById(id: Long): TrainingWorkout?
}
