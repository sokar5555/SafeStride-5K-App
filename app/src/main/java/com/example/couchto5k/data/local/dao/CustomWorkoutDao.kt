package com.example.couchto5k.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutWithIntervals
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomWorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomWorkout(workout: CustomWorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomWorkoutIntervals(intervals: List<CustomWorkoutIntervalEntity>)

    @Transaction
    @Query("SELECT * FROM custom_workouts ORDER BY createdAt DESC")
    fun getAllCustomWorkouts(): Flow<List<CustomWorkoutWithIntervals>>

    @Transaction
    @Query("SELECT * FROM custom_workouts WHERE id = :id")
    suspend fun getCustomWorkoutById(id: Long): CustomWorkoutWithIntervals?

    @Query("DELETE FROM custom_workouts WHERE id = :id")
    suspend fun deleteCustomWorkout(id: Long)
}
