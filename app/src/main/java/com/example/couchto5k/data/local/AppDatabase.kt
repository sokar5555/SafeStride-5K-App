package com.example.couchto5k.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity

@Database(
    entities = [
        CompletedWorkoutEntity::class,
        CompletedIntervalEntity::class,
        ScheduledWorkoutEntity::class,
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun completedWorkoutDao(): CompletedWorkoutDao
    abstract fun scheduledWorkoutDao(): ScheduledWorkoutDao
}
