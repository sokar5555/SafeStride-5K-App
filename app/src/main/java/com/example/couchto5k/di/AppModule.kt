package com.example.couchto5k.di

import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import dagger.Binds
import dagger.Module
import com.example.couchto5k.engine.AudioHapticManager
import com.example.couchto5k.engine.DefaultAudioHapticManager
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindTrainingPlanRepository(
        impl: DefaultTrainingPlanRepository,
    ): TrainingPlanRepository

    @Binds
    abstract fun bindAudioHapticManager(
        impl: DefaultAudioHapticManager,
    ): AudioHapticManager
}
