package com.example.couchto5k.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.LocationPointEntity
import com.example.couchto5k.data.local.entity.WorkoutWithIntervals
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedWorkoutDao {
    @Insert
    suspend fun insertCompletedWorkout(workout: CompletedWorkoutEntity): Long

    @Insert
    suspend fun insertCompletedIntervals(intervals: List<CompletedIntervalEntity>)

    @Insert
    suspend fun insertLocationPoints(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE completedWorkoutId = :workoutId ORDER BY sequence ASC")
    suspend fun getLocationPointsForWorkout(workoutId: Long): List<LocationPointEntity>

    @Transaction
    @Query("SELECT * FROM completed_workouts ORDER BY startedAt DESC")
    fun getAllCompletedWorkouts(): Flow<List<WorkoutWithIntervals>>

    @Transaction
    @Query("SELECT * FROM completed_workouts WHERE id = :id")
    suspend fun getCompletedWorkoutById(id: Long): WorkoutWithIntervals?

    @Query("DELETE FROM completed_workouts WHERE id = :id")
    suspend fun deleteCompletedWorkout(id: Long)

    @Query("DELETE FROM completed_workouts")
    suspend fun clearAllCompletedWorkouts()

    @Query("DELETE FROM completed_intervals")
    suspend fun clearAllCompletedIntervals()
}
