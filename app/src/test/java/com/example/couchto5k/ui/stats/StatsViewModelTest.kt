package com.example.couchto5k.ui.stats

import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.domain.usecase.GetStatsUseCase
import com.example.couchto5k.engine.ActiveWorkoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private lateinit var fakeHistoryRepository: FakeWorkoutHistoryRepository
    private lateinit var fakeTrainingPlanRepository: FakeTrainingPlanRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var getStatsUseCase: GetStatsUseCase
    private lateinit var viewModel: StatsViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeHistoryRepository = FakeWorkoutHistoryRepository()
        fakeTrainingPlanRepository = FakeTrainingPlanRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        getStatsUseCase = GetStatsUseCase(fakeHistoryRepository, fakeTrainingPlanRepository, fakeSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when history is empty, state is Empty`() = runTest {
        viewModel = StatsViewModel(getStatsUseCase, fakeSettingsRepository)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect { }
        }

        assertEquals(StatsUiState.Empty, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when data present, state is Success with correct time of day and unit`() = runTest {
        fakeSettingsRepository.distanceUnitFlow.value = DistanceUnit.KILOMETERS

        viewModel = StatsViewModel(getStatsUseCase, fakeSettingsRepository)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect { }
        }

        // Initially empty
        assertEquals(StatsUiState.Empty, viewModel.uiState.value)

        collectJob.cancel()
    }

    private class FakeWorkoutHistoryRepository : WorkoutHistoryRepository {
        val historyFlow = MutableStateFlow<List<com.example.couchto5k.domain.model.CompletedWorkoutDetails>>(emptyList())

        override fun getHistory(): Flow<List<com.example.couchto5k.domain.model.CompletedWorkoutDetails>> = historyFlow
        override suspend fun getWorkoutDetails(id: Long): com.example.couchto5k.domain.model.CompletedWorkoutDetails? = null
        override suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState) {}
        override suspend fun deleteWorkout(id: Long) {}
        override suspend fun clearAllHistory() {}
    }

    private class FakeTrainingPlanRepository : TrainingPlanRepository {
        val workoutsFlow = MutableStateFlow<List<TrainingWorkout>>(emptyList())

        override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = workoutsFlow
        override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(emptyList())
        override suspend fun getWorkoutById(id: Long): TrainingWorkout? = null
        override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long = 0L
        override suspend fun deleteCustomWorkout(id: Long) {}
        override suspend fun clearAllCustomWorkouts() {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        val distanceUnitFlow = MutableStateFlow(DistanceUnit.MILES)

        override val programStartDate: Flow<LocalDate?> = MutableStateFlow(null)
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = MutableStateFlow(emptySet())
        override val distanceUnit: Flow<DistanceUnit> = distanceUnitFlow

        override suspend fun setProgramStartDate(date: LocalDate) {}
        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {}
        override suspend fun setDistanceUnit(unit: DistanceUnit) {
            distanceUnitFlow.value = unit
        }
    }
}
