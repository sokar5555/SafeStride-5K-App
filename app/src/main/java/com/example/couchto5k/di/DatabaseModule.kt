package com.example.couchto5k.di

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "couchto5k.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideCompletedWorkoutDao(database: AppDatabase): CompletedWorkoutDao {
        return database.completedWorkoutDao()
    }

    @Provides
    fun provideScheduledWorkoutDao(database: AppDatabase): ScheduledWorkoutDao {
        return database.scheduledWorkoutDao()
    }
}
