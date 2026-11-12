package com.example.couchto5k.ui.calendar

import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.LocationPointEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.data.local.entity.WorkoutWithIntervals
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.usecase.GenerateScheduleUseCase
import com.example.couchto5k.domain.usecase.GetCombinedScheduleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeScheduledWorkoutDao: FakeScheduledWorkoutDao
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
        fakeScheduledWorkoutDao = FakeScheduledWorkoutDao()
        val trainingPlanRepo = DefaultTrainingPlanRepository()
        val generateScheduleUseCase = GenerateScheduleUseCase(trainingPlanRepo, fakeScheduledWorkoutDao)
        val getCombinedScheduleUseCase = GetCombinedScheduleUseCase(
            scheduledWorkoutDao = fakeScheduledWorkoutDao,
            completedWorkoutDao = FakeCompletedWorkoutDao(),
            trainingPlanRepository = trainingPlanRepo,
        )
        viewModel = CalendarViewModel(
            settingsRepository = fakeSettingsRepository,
            generateScheduleUseCase = generateScheduleUseCase,
            getCombinedScheduleUseCase = getCombinedScheduleUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scrollingMonths_isTransientAndDoesNotModifySavedStartDateInDataStore() = runTest {
        val initialStartDate = LocalDate.of(2026, 5, 4)
        fakeSettingsRepository.setProgramStartDate(initialStartDate)
        val initialStartDateSetCount = fakeSettingsRepository.startDateSetCount

        val targetMonth = YearMonth.of(2026, 8)
        viewModel.setDisplayedMonth(targetMonth)
        assertEquals(targetMonth, viewModel.displayedMonth.value)

        viewModel.nextMonth()
        assertEquals(YearMonth.of(2026, 9), viewModel.displayedMonth.value)

        viewModel.previousMonth()
        assertEquals(YearMonth.of(2026, 8), viewModel.displayedMonth.value)

        // Saved program start date in DataStore must remain untouched
        assertEquals(initialStartDate, fakeSettingsRepository.startDateFlow.value)
        assertEquals(initialStartDateSetCount, fakeSettingsRepository.startDateSetCount)
    }

    private class FakeSettingsRepository : SettingsRepository {
        val startDateFlow = MutableStateFlow<LocalDate?>(LocalDate.of(2026, 5, 4))
        val preferredDaysFlow = MutableStateFlow(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))
        val distanceUnitFlow = MutableStateFlow(DistanceUnit.MILES)
        val progressiveWeeksFlow = MutableStateFlow(0)
        val longRunDayFlow = MutableStateFlow(DayOfWeek.SATURDAY)
        var startDateSetCount = 0

        override val programStartDate: Flow<LocalDate?> = startDateFlow
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = preferredDaysFlow
        override val distanceUnit: Flow<DistanceUnit> = distanceUnitFlow
        override val progressiveWeeksExtension: Flow<Int> = progressiveWeeksFlow
        override val longRunDay: Flow<DayOfWeek> = longRunDayFlow

        override suspend fun setProgramStartDate(date: LocalDate) {
            startDateSetCount++
            startDateFlow.value = date
        }

        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {
            preferredDaysFlow.value = days
        }

        override suspend fun setDistanceUnit(unit: DistanceUnit) {
            distanceUnitFlow.value = unit
        }

        override suspend fun setProgressiveWeeksExtension(weeks: Int) {
            progressiveWeeksFlow.value = weeks
        }

        override suspend fun setLongRunDay(day: DayOfWeek) {
            longRunDayFlow.value = day
        }
    }

    private class FakeScheduledWorkoutDao : ScheduledWorkoutDao {
        private val flow = MutableStateFlow<List<ScheduledWorkoutEntity>>(emptyList())

        override fun getAllScheduledWorkouts(): Flow<List<ScheduledWorkoutEntity>> = flow

        override suspend fun insertScheduledWorkouts(workouts: List<ScheduledWorkoutEntity>) {
            flow.value = workouts
        }

        override suspend fun deleteAll() {
            flow.value = emptyList()
        }
    }

    private class FakeCompletedWorkoutDao : CompletedWorkoutDao {
        override suspend fun insertCompletedWorkout(workout: CompletedWorkoutEntity): Long = 1L
        override suspend fun insertCompletedIntervals(intervals: List<CompletedIntervalEntity>) {}
        override fun getAllCompletedWorkouts(): Flow<List<WorkoutWithIntervals>> = flowOf(emptyList())
        override suspend fun getCompletedWorkoutById(id: Long): WorkoutWithIntervals? = null
        override suspend fun deleteCompletedWorkout(id: Long) {}
        override suspend fun clearAllCompletedIntervals() {}
        override suspend fun clearAllCompletedWorkouts() {}
        override suspend fun insertLocationPoints(points: List<LocationPointEntity>) {}
        override suspend fun getLocationPointsForWorkout(workoutId: Long): List<LocationPointEntity> = emptyList()
    }
}
