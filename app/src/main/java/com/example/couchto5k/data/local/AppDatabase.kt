package com.example.couchto5k.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.CustomWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity

@Database(
    entities = [
        CompletedWorkoutEntity::class,
        CompletedIntervalEntity::class,
        ScheduledWorkoutEntity::class,
        CustomWorkoutEntity::class,
        CustomWorkoutIntervalEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun completedWorkoutDao(): CompletedWorkoutDao
    abstract fun scheduledWorkoutDao(): ScheduledWorkoutDao
    abstract fun customWorkoutDao(): CustomWorkoutDao
}
