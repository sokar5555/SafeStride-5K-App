package com.example.couchto5k.di

import com.example.couchto5k.data.repository.TrainingPlanRepositoryImpl
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTrainingPlanRepository(
        trainingPlanRepositoryImpl: TrainingPlanRepositoryImpl
    ): TrainingPlanRepository
}
