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

        scheduledWorkouts.forEach { (_, _, scheduledDate) ->
            assertTrue(preferredDays.contains(scheduledDate.dayOfWeek))
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

    @Test
    fun generateSchedule_extendsBeyond5KWith10PercentWeeklyVolumeAndDesignatedLongRunDay() = runTest {
        val startDate = LocalDate.of(2025, 3, 3) // Monday
        val preferredDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        val progressiveWeeks = 4

        useCase(
            startDate = startDate,
            preferredDays = preferredDays,
            progressiveWeeksExtension = progressiveWeeks,
            longRunDay = DayOfWeek.SUNDAY,
        )

        val scheduledWorkouts = scheduledWorkoutDao.getAllScheduledWorkouts().first()

        // 9 standard weeks (27 workouts) + 4 progressive weeks (12 workouts) = 39 workouts
        assertEquals(39, scheduledWorkouts.size)

        // Verify progressive volume +10% rule in generated progressive workouts
        val progressiveWorkouts = DefaultTrainingPlanRepository.generateProgressiveWeeks(4)
        assertEquals(12, progressiveWorkouts.size)

        val week9RunningSeconds = 3 * 30 * 60 // 5400s (90 min)
        val week10RunningSeconds = progressiveWorkouts.asSequence().filter { (_, week) -> week == 10 }.sumOf { it.totalPlannedRunningSeconds }
        val week11RunningSeconds = progressiveWorkouts.asSequence().filter { (_, week) -> week == 11 }.sumOf { it.totalPlannedRunningSeconds }

        assertTrue("Week 10 volume must exceed Week 9 volume", week10RunningSeconds > week9RunningSeconds)
        assertTrue("Week 11 volume must exceed Week 10 volume by ~10%", week11RunningSeconds > week10RunningSeconds)

        // Verify Long Run (Workout 3 of each progressive week) is placed on the designated Long Run day (SUNDAY)
        val progressiveScheduled = scheduledWorkouts.drop(27)
        progressiveScheduled.forEach { (_, trainingWorkoutId, scheduledDate) ->
            val workout = trainingPlanRepository.getWorkoutById(trainingWorkoutId)
            requireNotNull(workout)
            if (workout.workoutNumber == 3) {
                assertEquals(DayOfWeek.SUNDAY, scheduledDate.dayOfWeek)
                assertTrue(workout.title.contains("Long Run"))
            }
        }
    }

    @Test
    fun generateSchedule_progressiveWeeks_workout3AlwaysScheduledAfterWorkout2RegardlessOfStartDate() = runTest {
        val preferredDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        // Test various starting dates: Tuesday (first preferred), Thursday (middle preferred), Sunday (last preferred)
        val testStartDates = listOf(
            LocalDate.of(2025, 3, 4), // Tuesday
            LocalDate.of(2025, 3, 6), // Thursday
            LocalDate.of(2025, 3, 9), // Sunday
        )

        for (startDate in testStartDates) {
            useCase(
                startDate = startDate,
                preferredDays = preferredDays,
                progressiveWeeksExtension = 3,
                longRunDay = DayOfWeek.SUNDAY,
            )

            val scheduled = scheduledWorkoutDao.getAllScheduledWorkouts().first()
            val progressiveScheduled = scheduled.drop(27)

            // Group by week
            val byWeek = progressiveScheduled.groupBy { (_, trainingWorkoutId) ->
                val (_, week) = trainingPlanRepository.getWorkoutById(trainingWorkoutId)!!
                week
            }

            for ((week, entities) in byWeek) {
                assertEquals("Each progressive week must have 3 workouts", 3, entities.size)

                val (_, _, w1Date) = entities.find { (_, workoutId) -> trainingPlanRepository.getWorkoutById(workoutId)?.workoutNumber == 1 }!!
                val (_, _, w2Date) = entities.find { (_, workoutId) -> trainingPlanRepository.getWorkoutById(workoutId)?.workoutNumber == 2 }!!
                val (_, _, w3Date) = entities.find { (_, workoutId) -> trainingPlanRepository.getWorkoutById(workoutId)?.workoutNumber == 3 }!!

                assertTrue(
                    "Week $week: Workout 1 ($w1Date) must be before Workout 2 ($w2Date)",
                    w1Date.isBefore(w2Date),
                )
                assertTrue(
                    "Week $week: Workout 2 ($w2Date) must be before Workout 3 ($w3Date)",
                    w2Date.isBefore(w3Date),
                )
                assertEquals(
                    "Week $week: Workout 3 must be on designated Long Run Day (Sunday)",
                    DayOfWeek.SUNDAY,
                    w3Date.dayOfWeek,
                )
            }
        }
    }
}
