package com.example.couchto5k.data.local.dao

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.domain.model.IntervalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CustomWorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: CustomWorkoutDao

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.customWorkoutDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `test insert and query custom workout`() = runBlocking {
        val workoutId = dao.insertCustomWorkout(CustomWorkoutEntity(title = "Morning Intervals"))

        val intervals = listOf(
            CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 0, intervalType = IntervalType.WARMUP, durationSeconds = 300),
            CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 1, intervalType = IntervalType.RUN, durationSeconds = 180),
            CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 2, intervalType = IntervalType.WALK, durationSeconds = 120),
            CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 3, intervalType = IntervalType.COOLDOWN, durationSeconds = 300),
        )
        dao.insertCustomWorkoutIntervals(intervals)

        val retrieved = dao.getCustomWorkoutById(workoutId)
        assertNotNull(retrieved)
        assertEquals("Morning Intervals", retrieved?.customWorkout?.title)
        assertEquals(4, retrieved?.intervals?.size)

        val domainWorkout = retrieved?.toTrainingWorkout(10000L)
        assertNotNull(domainWorkout)
        assertEquals(10000L + workoutId, domainWorkout?.id)
        assertEquals(900, domainWorkout?.totalPlannedDurationSeconds)

        val allWorkouts = dao.getAllCustomWorkouts().first()
        assertEquals(1, allWorkouts.size)
    }

    @Test
    fun `test delete custom workout removes intervals via cascade`() = runBlocking {
        val workoutId = dao.insertCustomWorkout(CustomWorkoutEntity(title = "Speed Work"))
        dao.insertCustomWorkoutIntervals(
            listOf(
                CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 0, intervalType = IntervalType.WARMUP, durationSeconds = 300),
                CustomWorkoutIntervalEntity(customWorkoutId = workoutId, sequence = 1, intervalType = IntervalType.RUN, durationSeconds = 300),
            ),
        )

        dao.deleteCustomWorkout(workoutId)

        val retrieved = dao.getCustomWorkoutById(workoutId)
        assertNull(retrieved)

        val allWorkouts = dao.getAllCustomWorkouts().first()
        assertEquals(0, allWorkouts.size)
    }
}
