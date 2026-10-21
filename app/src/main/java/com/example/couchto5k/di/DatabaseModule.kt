package com.example.couchto5k.di

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "couchto5k_db"
        ).build()
    }

    @Provides
    fun provideCompletedWorkoutDao(database: AppDatabase): CompletedWorkoutDao {
        return database.completedWorkoutDao()
    }
}
