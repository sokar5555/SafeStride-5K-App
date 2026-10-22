package com.example.couchto5k.data.repository

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngineStatus
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CompletedWorkoutDao
    private lateinit var repository: DefaultWorkoutHistoryRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.completedWorkoutDao()
        repository = DefaultWorkoutHistoryRepository(dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun saveWorkout_calculatesIntervalsAndDurationCorrectly() = runTest {
        val workout = TrainingWorkout(
            id = 1L,
            week = 1,
            workoutNumber = 1,
            title = "Test Workout",
            intervals = listOf(
                WorkoutInterval(0, IntervalType.WARMUP, 300), // 5 min
                WorkoutInterval(1, IntervalType.RUN, 60),    // 1 min
                WorkoutInterval(2, IntervalType.WALK, 90),   // 1.5 min
                WorkoutInterval(3, IntervalType.RUN, 60)     // 1 min
            )
        )

        // Simulate completing first 3 intervals and 30 seconds of the 4th
        val state = ActiveWorkoutState(
            workoutId = 1L,
            totalElapsedMillis = (300 + 60 + 90 + 30) * 1000L,
            startedAt = Instant.now().minusSeconds(480),
            status = WorkoutEngineStatus.COMPLETED
        )

        repository.saveCompletedWorkout(workout, state)

        val history = repository.getHistory().first()
        assertEquals(1, history.size)

        val details = history.first()
        assertEquals(1L, details.workout.trainingWorkoutId)
        assertEquals(480, details.workout.durationSeconds) // 300+60+90+30
        assertEquals(90, details.workout.runningSeconds)   // 60 + 30
        assertEquals(390, details.workout.walkingSeconds)  // 300 (Warmup) + 90 (Walk)
        assertEquals(60, details.workout.longestCompletedRunSeconds)

        assertEquals(4, details.intervals.size)

        // Interval 1 (Warmup)
        assertEquals(300, details.intervals[0].plannedDurationSeconds)
        assertEquals(300, details.intervals[0].actualDurationSeconds)
        assertTrue(details.intervals[0].completed)

        // Interval 2 (Run)
        assertEquals(60, details.intervals[1].plannedDurationSeconds)
        assertEquals(60, details.intervals[1].actualDurationSeconds)
        assertTrue(details.intervals[1].completed)

        // Interval 3 (Walk)
        assertEquals(90, details.intervals[2].plannedDurationSeconds)
        assertEquals(90, details.intervals[2].actualDurationSeconds)
        assertTrue(details.intervals[2].completed)

        // Interval 4 (Run)
        assertEquals(60, details.intervals[3].plannedDurationSeconds)
        assertEquals(30, details.intervals[3].actualDurationSeconds)
        assertEquals(false, details.intervals[3].completed)
    }

    @Test
    fun saveWorkout_skippedCooldown_marksCooldownAndWorkoutCompleted() = runTest {
        val workout = TrainingWorkout(
            id = 2L,
            week = 1,
            workoutNumber = 1,
            title = "Cooldown Test Workout",
            intervals = listOf(
                WorkoutInterval(0, IntervalType.WARMUP, 300), // 5 min
                WorkoutInterval(1, IntervalType.RUN, 60),    // 1 min
                WorkoutInterval(2, IntervalType.WALK, 90),   // 1.5 min
                WorkoutInterval(3, IntervalType.COOLDOWN, 300) // 5 min
            )
        )

        // Simulate completing warmup, run, walk, and skipping cooldown immediately (0s in cooldown)
        val state = ActiveWorkoutState(
            workoutId = 2L,
            totalElapsedMillis = (300 + 60 + 90) * 1000L,
            startedAt = Instant.now().minusSeconds(450),
            status = WorkoutEngineStatus.COMPLETED
        )

        repository.saveCompletedWorkout(workout, state)

        val history = repository.getHistory().first()
        assertEquals(1, history.size)

        val details = history.first()
        assertEquals(WorkoutEngineStatus.COMPLETED, details.workout.status)
        assertEquals(750, details.workout.durationSeconds) // 300+60+90+300 = 750 (includes full cooldown)
        assertEquals(60, details.workout.runningSeconds)
        assertEquals(690, details.workout.walkingSeconds)  // 300 (Warmup) + 90 (Walk) + 300 (Cooldown)
        assertEquals(4, details.intervals.size)

        // Warmup, Run, Walk completed
        assertTrue(details.intervals[0].completed)
        assertTrue(details.intervals[1].completed)
        assertTrue(details.intervals[2].completed)

        // Cooldown should also be marked as completed with full actual duration
        assertTrue(details.intervals[3].completed)
        assertEquals(300, details.intervals[3].actualDurationSeconds)
    }

    @Test
    fun getHistory_returnsOrderedNewestFirst() = runTest {
        val workout = TrainingWorkout(1L, 1, 1, "Test", listOf())

        // Old workout
        repository.saveCompletedWorkout(workout, ActiveWorkoutState(
            totalElapsedMillis = 1000L,
            startedAt = Instant.now().minusSeconds(1000),
            status = WorkoutEngineStatus.COMPLETED
        ))

        // New workout
        repository.saveCompletedWorkout(workout, ActiveWorkoutState(
            totalElapsedMillis = 1000L,
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED
        ))

        val history = repository.getHistory().first()
        assertEquals(2, history.size)
        assertTrue(history[0].workout.startedAt.isAfter(history[1].workout.startedAt))
    }

    @Test
    fun deleteWorkout_removesWorkoutAndIntervals() = runTest {
        val workout = TrainingWorkout(1L, 1, 1, "Test", listOf(WorkoutInterval(0, IntervalType.RUN, 60)))

        repository.saveCompletedWorkout(workout, ActiveWorkoutState(
            totalElapsedMillis = 60000L,
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED
        ))

        val historyBefore = repository.getHistory().first()
        assertEquals(1, historyBefore.size)
        val savedWorkoutId = historyBefore.first().workout.id

        repository.deleteWorkout(savedWorkoutId)

        val historyAfter = repository.getHistory().first()
        assertEquals(0, historyAfter.size)
    }
}
