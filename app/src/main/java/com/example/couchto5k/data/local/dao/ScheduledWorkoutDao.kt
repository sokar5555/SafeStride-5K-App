package com.example.couchto5k.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledWorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledWorkouts(workouts: List<ScheduledWorkoutEntity>)

    @Query("SELECT * FROM scheduled_workouts ORDER BY scheduledDate ASC")
    fun getAllScheduledWorkouts(): Flow<List<ScheduledWorkoutEntity>>

    @Query("DELETE FROM scheduled_workouts")
    suspend fun deleteAll()
}
