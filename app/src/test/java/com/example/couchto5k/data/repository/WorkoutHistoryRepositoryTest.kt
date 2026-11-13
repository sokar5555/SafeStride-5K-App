package com.example.couchto5k.data.repository

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.OpenExerciseSegment
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.location.LocationPoint
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
                WorkoutInterval(3, IntervalType.RUN, 60),     // 1 min
            ),
        )

        // Simulate completing first 3 intervals and 30 seconds of the 4th
        val state = ActiveWorkoutState(
            workoutId = 1L,
            totalElapsedMillis = (300 + 60 + 90 + 30) * 1000L,
            startedAt = Instant.now().minusSeconds(480),
            status = WorkoutEngineStatus.COMPLETED,
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
                WorkoutInterval(3, IntervalType.COOLDOWN, 300), // 5 min
            ),
        )

        // Simulate completing warmup, run, walk, and skipping cooldown immediately (0s in cooldown)
        val state = ActiveWorkoutState(
            workoutId = 2L,
            totalElapsedMillis = (300 + 60 + 90) * 1000L,
            startedAt = Instant.now().minusSeconds(450),
            status = WorkoutEngineStatus.COMPLETED,
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
        repository.saveCompletedWorkout(
            workout,
            ActiveWorkoutState(
                totalElapsedMillis = 1000L,
                startedAt = Instant.now().minusSeconds(1000),
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )

        // New workout
        repository.saveCompletedWorkout(
            workout,
            ActiveWorkoutState(
                totalElapsedMillis = 1000L,
                startedAt = Instant.now(),
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )

        val history = repository.getHistory().first()
        assertEquals(2, history.size)
        assertTrue(history[0].workout.startedAt.isAfter(history[1].workout.startedAt))
    }

    @Test
    fun deleteWorkout_removesWorkoutAndIntervals() = runTest {
        val workout = TrainingWorkout(1L, 1, 1, "Test", listOf(WorkoutInterval(0, IntervalType.RUN, 60)))

        repository.saveCompletedWorkout(
            workout,
            ActiveWorkoutState(
                totalElapsedMillis = 60000L,
                startedAt = Instant.now(),
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )

        val historyBefore = repository.getHistory().first()
        assertEquals(1, historyBefore.size)
        val savedWorkoutId = historyBefore.first().workout.id

        repository.deleteWorkout(savedWorkoutId)

        val historyAfter = repository.getHistory().first()
        assertEquals(0, historyAfter.size)
    }

    @Test
    fun saveWorkout_withRoutePoints_persistsAndRetrievesPoints() = runTest {
        val workout = TrainingWorkout(1L, 1, 1, "Test Route", listOf())

        val routePoints = listOf(
            LocationPoint(37.4219983, -122.084, null, 5f, 1000L),
            LocationPoint(37.422, -122.085, 10.0, 10f, 2000L),
        )

        val state = ActiveWorkoutState(
            workoutId = 1L,
            totalElapsedMillis = 10000L,
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED,
            routePoints = routePoints,
        )

        repository.saveCompletedWorkout(workout, state)

        val history = repository.getHistory().first()
        assertEquals(1, history.size)
        val savedWorkoutId = history.first().workout.id

        val details = repository.getWorkoutDetails(savedWorkoutId)
        requireNotNull(details)

        assertEquals(2, details.routePoints.size)

        assertEquals(37.4219983, details.routePoints[0].latitude, 0.0001)
        assertEquals(-122.084, details.routePoints[0].longitude, 0.0001)
        assertEquals(null, details.routePoints[0].altitude)
        assertEquals(5f, details.routePoints[0].accuracyMeters)
        assertEquals(1000L, details.routePoints[0].timestamp)

        assertEquals(37.422, details.routePoints[1].latitude, 0.0001)
        assertEquals(-122.085, details.routePoints[1].longitude, 0.0001)
        assertEquals(10.0, details.routePoints[1].altitude)
        assertEquals(10f, details.routePoints[1].accuracyMeters)
        assertEquals(2000L, details.routePoints[1].timestamp)
    }

    @Test
    fun saveWorkout_persistsAndRetrievesPerIntervalDistancesAndFreeRuns() = runTest {
        val workout = TrainingWorkout(
            id = 3L,
            week = 1,
            workoutNumber = 1,
            title = "Interval Distance Workout",
            intervals = listOf(
                WorkoutInterval(0, IntervalType.WARMUP, 300),
                WorkoutInterval(1, IntervalType.RUN, 180),
                WorkoutInterval(2, IntervalType.WALK, 120),
                WorkoutInterval(3, IntervalType.COOLDOWN, 300),
            ),
        )

        val state = ActiveWorkoutState(
            workoutId = 3L,
            totalElapsedMillis = 900 * 1000L,
            distanceMeters = 1850.0,
            intervalDistancesMeters = mapOf(
                0 to 450.0,
                1 to 515.0,
                2 to 235.0,
                3 to 650.0,
            ),
            startedAt = Instant.now().minusSeconds(900),
            status = WorkoutEngineStatus.COMPLETED,
        )

        repository.saveCompletedWorkout(workout, state)

        // Also save a Free Run
        val freeRunState = ActiveWorkoutState(
            workoutId = WorkoutEngine.FREE_RUN_WORKOUT_ID,
            isFreeRun = true,
            totalElapsedMillis = 1500 * 1000L,
            distanceMeters = 3218.68,
            intervalDistancesMeters = mapOf(0 to 3218.68),
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED,
        )
        repository.saveCompletedWorkout(WorkoutEngine.FREE_RUN_WORKOUT, freeRunState)

        val history = repository.getHistory().first()
        assertEquals(2, history.size)

        val savedFreeRun = history.first { it.workout.title == "Free Run" }
        assertEquals("Free Run", savedFreeRun.workout.title)
        assertEquals(1500, savedFreeRun.workout.durationSeconds)
        assertEquals(1500, savedFreeRun.workout.runningSeconds)
        assertEquals(1500, savedFreeRun.workout.longestCompletedRunSeconds)
        assertEquals(3218.68, savedFreeRun.workout.distanceMeters, 0.01)

        val savedIntervalWorkout = history.first { it.workout.trainingWorkoutId == 3L }
        assertEquals(4, savedIntervalWorkout.intervals.size)
        assertEquals(450.0, savedIntervalWorkout.intervals[0].distanceMeters, 0.001)
        assertEquals(515.0, savedIntervalWorkout.intervals[1].distanceMeters, 0.001)
        assertEquals(235.0, savedIntervalWorkout.intervals[2].distanceMeters, 0.001)
        assertEquals(650.0, savedIntervalWorkout.intervals[3].distanceMeters, 0.001)
    }

    @Test
    fun saveWorkout_openExerciseMixedRunAndWalk_persistsSegmentsAndAttributesStatsProperly() = runTest {
        val openWorkout = WorkoutEngine.createOpenExerciseWorkout(IntervalType.RUN)
        val state = ActiveWorkoutState(
            workoutId = WorkoutEngine.FREE_RUN_WORKOUT_ID,
            isFreeRun = true,
            totalElapsedMillis = 360_000L,
            distanceMeters = 830.0,
            openExerciseSegments = listOf(
                OpenExerciseSegment(0, IntervalType.RUN, 120_000L, 300.0),
                OpenExerciseSegment(1, IntervalType.WALK, 60_000L, 80.0),
                OpenExerciseSegment(2, IntervalType.RUN, 180_000L, 450.0),
            ),
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED,
        )

        repository.saveCompletedWorkout(openWorkout, state)

        val history = repository.getHistory().first()
        assertEquals(1, history.size)

        val saved = history.first()
        assertEquals("Free Run & Walk", saved.workout.title)
        assertEquals(0L, saved.workout.trainingWorkoutId)
        assertEquals(360, saved.workout.durationSeconds)
        assertEquals(300, saved.workout.runningSeconds)
        assertEquals(60, saved.workout.walkingSeconds)
        assertEquals(180, saved.workout.longestCompletedRunSeconds)
        assertEquals(830.0, saved.workout.distanceMeters, 0.01)

        assertEquals(3, saved.intervals.size)
        assertEquals(IntervalType.RUN, saved.intervals[0].intervalType)
        assertEquals(120, saved.intervals[0].actualDurationSeconds)
        assertEquals(300.0, saved.intervals[0].distanceMeters, 0.01)

        assertEquals(IntervalType.WALK, saved.intervals[1].intervalType)
        assertEquals(60, saved.intervals[1].actualDurationSeconds)
        assertEquals(80.0, saved.intervals[1].distanceMeters, 0.01)

        assertEquals(IntervalType.RUN, saved.intervals[2].intervalType)
        assertEquals(180, saved.intervals[2].actualDurationSeconds)
        assertEquals(450.0, saved.intervals[2].distanceMeters, 0.01)
    }

    @Test
    fun saveWorkout_openExerciseWalkOnly_savesAsFreeWalkWithZeroLongestRun() = runTest {
        val walkWorkout = WorkoutEngine.createOpenExerciseWorkout(IntervalType.WALK)
        val state = ActiveWorkoutState(
            workoutId = WorkoutEngine.FREE_RUN_WORKOUT_ID,
            isFreeRun = true,
            totalElapsedMillis = 600_000L,
            distanceMeters = 800.0,
            openExerciseSegments = listOf(
                OpenExerciseSegment(0, IntervalType.WALK, 600_000L, 800.0),
            ),
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.COMPLETED,
        )

        repository.saveCompletedWorkout(walkWorkout, state)

        val saved = repository.getHistory().first().first()
        assertEquals("Free Walk", saved.workout.title)
        assertEquals(0, saved.workout.runningSeconds)
        assertEquals(600, saved.workout.walkingSeconds)
        assertEquals(0, saved.workout.longestCompletedRunSeconds)
    }
}
