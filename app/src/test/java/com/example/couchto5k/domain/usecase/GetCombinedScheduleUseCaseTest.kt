package com.example.couchto5k.domain.usecase

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GetCombinedScheduleUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduledWorkoutDao: ScheduledWorkoutDao
    private lateinit var completedWorkoutDao: CompletedWorkoutDao
    private lateinit var trainingPlanRepository: DefaultTrainingPlanRepository
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
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun invoke_marksPastUncompletedWorkoutsAsSkippedAndAssignsWorkoutTitle() = runTest {
        val today = LocalDate.now()
        val pastDate1 = today.minusDays(5)
        val pastDate2 = today.minusDays(3)
        val futureDate = today.plusDays(2)

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
}
