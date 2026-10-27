package com.example.couchto5k.di

import com.example.couchto5k.data.repository.DefaultSettingsRepository
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.data.repository.DefaultWorkoutHistoryRepository
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.AudioHapticManager
import com.example.couchto5k.engine.DefaultAudioHapticManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTrainingPlanRepository(
        impl: DefaultTrainingPlanRepository,
    ): TrainingPlanRepository

    @Binds
    @Singleton
    abstract fun bindAudioHapticManager(
        impl: DefaultAudioHapticManager,
    ): AudioHapticManager

    @Binds
    @Singleton
    abstract fun bindWorkoutHistoryRepository(
        impl: DefaultWorkoutHistoryRepository,
    ): WorkoutHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DefaultSettingsRepository,
    ): SettingsRepository
}
