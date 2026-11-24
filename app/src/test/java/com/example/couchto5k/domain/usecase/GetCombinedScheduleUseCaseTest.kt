package com.example.couchto5k.domain.usecase

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.WorkoutStatus
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GetCombinedScheduleUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduledWorkoutDao: ScheduledWorkoutDao
    private lateinit var completedWorkoutDao: CompletedWorkoutDao
    private lateinit var trainingPlanRepository: DefaultTrainingPlanRepository
    private var fixedToday: LocalDate = LocalDate.of(2026, 11, 13)
    private lateinit var useCase: GetCombinedScheduleUseCase

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduledWorkoutDao = db.scheduledWorkoutDao()
        completedWorkoutDao = db.completedWorkoutDao()
        trainingPlanRepository = DefaultTrainingPlanRepository()
        useCase = GetCombinedScheduleUseCase(
            scheduledWorkoutDao = scheduledWorkoutDao,
            completedWorkoutDao = completedWorkoutDao,
            trainingPlanRepository = trainingPlanRepository,
            todayProvider = { fixedToday },
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun invoke_marksPastUncompletedWorkoutsAsSkippedAndAssignsWorkoutTitle() = runTest {
        val pastDate1 = fixedToday.minusDays(5)
        val pastDate2 = fixedToday.minusDays(3)
        val futureDate = fixedToday.plusDays(2)

        val entities = listOf(
            ScheduledWorkoutEntity(id = 1, trainingWorkoutId = 1, scheduledDate = pastDate1, status = WorkoutStatus.UPCOMING.name),
            ScheduledWorkoutEntity(id = 2, trainingWorkoutId = 2, scheduledDate = pastDate2, status = WorkoutStatus.UPCOMING.name),
            ScheduledWorkoutEntity(id = 3, trainingWorkoutId = 3, scheduledDate = futureDate, status = WorkoutStatus.UPCOMING.name),
        )
        scheduledWorkoutDao.insertScheduledWorkouts(entities)

        val schedule = useCase().first()

        assertEquals(WorkoutStatus.SKIPPED, schedule[0].status)
        assertEquals("Week 1, Workout 1", schedule[0].title)

        assertEquals(WorkoutStatus.SKIPPED, schedule[1].status)
        assertEquals("Week 1, Workout 2", schedule[1].title)

        assertEquals(WorkoutStatus.AVAILABLE, schedule[2].status)
        assertEquals("Week 1, Workout 3", schedule[2].title)
    }

    @Test
    fun invoke_cancelledWorkout_doesNotMarkScheduledWorkoutCompleted() = runTest {
        scheduledWorkoutDao.insertScheduledWorkouts(
            listOf(
                ScheduledWorkoutEntity(id = 1, trainingWorkoutId = 1, scheduledDate = fixedToday, status = WorkoutStatus.UPCOMING.name),
                ScheduledWorkoutEntity(id = 2, trainingWorkoutId = 2, scheduledDate = fixedToday.plusDays(2), status = WorkoutStatus.UPCOMING.name),
            ),
        )

        completedWorkoutDao.insertCompletedWorkout(
            CompletedWorkoutEntity(
                trainingWorkoutId = 1L,
                scheduledWorkoutId = null,
                title = "Week 1, Workout 1",
                startedAt = Instant.parse("2026-11-13T09:00:00Z"),
                completedAt = Instant.parse("2026-11-13T09:02:00Z"),
                durationSeconds = 120,
                runningSeconds = 0,
                walkingSeconds = 120,
                distanceMeters = 100.0,
                averagePaceSecondsPerKm = 1200.0,
                longestCompletedRunSeconds = 0,
                status = WorkoutEngineStatus.CANCELLED,
            ),
        )

        val schedule = useCase().first()
        assertEquals(WorkoutStatus.AVAILABLE, schedule[0].status)
        assertEquals(WorkoutStatus.UPCOMING, schedule[1].status)
    }

    @Test
    fun invoke_monthBoundaryLateCompletionAndRepeatWorkouts_remainAccurate() = runTest {
        // Schedule spans Jan -> Feb boundary
        fixedToday = LocalDate.of(2026, 2, 2)
        scheduledWorkoutDao.insertScheduledWorkouts(
            listOf(
                ScheduledWorkoutEntity(id = 1, trainingWorkoutId = 1, scheduledDate = LocalDate.of(2026, 1, 29), status = WorkoutStatus.UPCOMING.name),
                ScheduledWorkoutEntity(id = 2, trainingWorkoutId = 2, scheduledDate = LocalDate.of(2026, 1, 31), status = WorkoutStatus.UPCOMING.name),
                ScheduledWorkoutEntity(id = 3, trainingWorkoutId = 3, scheduledDate = LocalDate.of(2026, 2, 2), status = WorkoutStatus.UPCOMING.name),
                ScheduledWorkoutEntity(id = 4, trainingWorkoutId = 4, scheduledDate = LocalDate.of(2026, 2, 5), status = WorkoutStatus.UPCOMING.name),
            ),
        )

        // Complete Workout 1 late (Feb 1) and repeat Workout 1 (Feb 2); leave Workout 2 missed
        listOf("2026-02-01T08:00:00Z", "2026-02-02T08:00:00Z").forEach { iso ->
            completedWorkoutDao.insertCompletedWorkout(
                CompletedWorkoutEntity(
                    trainingWorkoutId = 1L,
                    scheduledWorkoutId = null,
                    title = "Week 1, Workout 1",
                    startedAt = Instant.parse(iso),
                    completedAt = Instant.parse(iso).plusSeconds(1800),
                    durationSeconds = 1800,
                    runningSeconds = 480,
                    walkingSeconds = 1320,
                    distanceMeters = 3200.0,
                    averagePaceSecondsPerKm = 562.5,
                    longestCompletedRunSeconds = 60,
                    status = WorkoutEngineStatus.COMPLETED,
                ),
            )
        }

        val schedule = useCase().first()
        assertEquals(LocalDate.of(2026, 1, 29), schedule[0].scheduledDate)
        assertEquals(WorkoutStatus.COMPLETED, schedule[0].status)
        assertEquals(WorkoutStatus.SKIPPED, schedule[1].status)
        assertEquals(LocalDate.of(2026, 2, 2), schedule[2].scheduledDate)
        assertEquals(WorkoutStatus.AVAILABLE, schedule[2].status)
        assertEquals("Week 2, Workout 1", schedule[3].title)
        assertEquals(WorkoutStatus.UPCOMING, schedule[3].status)
    }

    @Test
    fun invoke_weekTransitionAndProgressiveExtensionTitles_resolveCorrectly() = runTest {
        fixedToday = LocalDate.of(2026, 3, 1)
        scheduledWorkoutDao.insertScheduledWorkouts(
            listOf(
                ScheduledWorkoutEntity(id = 27, trainingWorkoutId = 27, scheduledDate = LocalDate.of(2026, 2, 27), status = WorkoutStatus.UPCOMING.name),
                ScheduledWorkoutEntity(id = 28, trainingWorkoutId = 28, scheduledDate = LocalDate.of(2026, 3, 2), status = WorkoutStatus.UPCOMING.name),
            ),
        )

        completedWorkoutDao.insertCompletedWorkout(
            CompletedWorkoutEntity(
                trainingWorkoutId = 27L,
                scheduledWorkoutId = null,
                title = "Week 9, Workout 3",
                startedAt = Instant.parse("2026-02-27T08:00:00Z"),
                completedAt = Instant.parse("2026-02-27T08:40:00Z"),
                durationSeconds = 2400,
                runningSeconds = 1800,
                walkingSeconds = 600,
                distanceMeters = 5000.0,
                averagePaceSecondsPerKm = 480.0,
                longestCompletedRunSeconds = 1800,
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )

        val schedule = useCase().first()
        assertEquals(WorkoutStatus.COMPLETED, schedule[0].status)
        assertEquals("Week 9, Workout 3", schedule[0].title)
        assertEquals(WorkoutStatus.AVAILABLE, schedule[1].status)
        assertEquals("Week 10, Workout 1 (Midweek Run)", schedule[1].title)
    }
}
