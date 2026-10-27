package com.example.couchto5k.domain.usecase

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GenerateScheduleUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduledWorkoutDao: ScheduledWorkoutDao
    private lateinit var trainingPlanRepository: DefaultTrainingPlanRepository
    private lateinit var useCase: GenerateScheduleUseCase

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduledWorkoutDao = db.scheduledWorkoutDao()
        trainingPlanRepository = DefaultTrainingPlanRepository()
        useCase = GenerateScheduleUseCase(trainingPlanRepository, scheduledWorkoutDao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun generateSchedule_schedulesAllWorkoutsOnPreferredDaysStartingFromStartDate() = runTest {
        val startDate = LocalDate.of(2025, 3, 3) // Monday
        val preferredDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        useCase(startDate, preferredDays)

        val scheduledWorkouts = scheduledWorkoutDao.getAllScheduledWorkouts().first()

        assertEquals(27, scheduledWorkouts.size)
        assertEquals(startDate, scheduledWorkouts.first().scheduledDate)
        assertTrue(preferredDays.contains(scheduledWorkouts.first().scheduledDate.dayOfWeek))

        scheduledWorkouts.forEach { item ->
            assertTrue(preferredDays.contains(item.scheduledDate.dayOfWeek))
        }
    }

    @Test
    fun generateSchedule_regeneratesScheduleWhenStartDateChanges() = runTest {
        val initialStartDate = LocalDate.of(2025, 3, 3) // Monday
        val preferredDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        useCase(initialStartDate, preferredDays)
        val initialSchedule = scheduledWorkoutDao.getAllScheduledWorkouts().first()
        assertEquals(initialStartDate, initialSchedule.first().scheduledDate)

        val newStartDate = LocalDate.of(2025, 4, 7) // Monday
        useCase(newStartDate, preferredDays)
        val updatedSchedule = scheduledWorkoutDao.getAllScheduledWorkouts().first()

        assertEquals(27, updatedSchedule.size)
        assertEquals(newStartDate, updatedSchedule.first().scheduledDate)
    }
}
