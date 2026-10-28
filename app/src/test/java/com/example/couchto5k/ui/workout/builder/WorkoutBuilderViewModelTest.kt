package com.example.couchto5k.ui.workout.builder

import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutBuilderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeTrainingPlanRepository
    private lateinit var viewModel: WorkoutBuilderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTrainingPlanRepository()
        viewModel = WorkoutBuilderViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state starts with 5 min warmup and 5 min cooldown`() {
        val state = viewModel.uiState.value
        assertEquals("Custom Workout", state.title)
        assertEquals(300, state.warmupDurationSeconds)
        assertEquals(300, state.cooldownDurationSeconds)
        assertEquals(0, state.middleIntervals.size)
        // 5m warmup + 5m cooldown = 10m = 600s
        assertEquals(600, state.totalDurationSeconds)
    }

    @Test
    fun `test adding run and walk intervals`() {
        viewModel.addInterval(IntervalType.RUN, 180) // 3m run
        viewModel.addInterval(IntervalType.WALK, 120) // 2m walk

        val state = viewModel.uiState.value
        assertEquals(2, state.middleIntervals.size)
        assertEquals(1, state.runCount)
        assertEquals(1, state.walkCount)
        assertEquals(180, state.totalRunDurationSeconds)
        assertEquals(120, state.totalWalkDurationSeconds)

        // 5m warmup + 3m run + 2m walk + 5m cooldown = 15m = 900s
        assertEquals(900, state.totalDurationSeconds)

        // Verify sequence in allIntervals
        val all = state.allIntervals
        assertEquals(4, all.size)
        assertEquals(IntervalType.WARMUP, all[0].type)
        assertEquals(IntervalType.RUN, all[1].type)
        assertEquals(IntervalType.WALK, all[2].type)
        assertEquals(IntervalType.COOLDOWN, all[3].type)
    }

    @Test
    fun `test updating and removing middle intervals`() {
        viewModel.addInterval(IntervalType.RUN, 180)
        viewModel.addInterval(IntervalType.WALK, 120)

        // Update run to 240s
        viewModel.updateMiddleIntervalDuration(0, 240)
        assertEquals(240, viewModel.uiState.value.middleIntervals[0].durationSeconds)

        // Remove walk interval
        viewModel.removeMiddleInterval(1)
        assertEquals(1, viewModel.uiState.value.middleIntervals.size)
        assertEquals(IntervalType.RUN, viewModel.uiState.value.middleIntervals[0].type)
    }

    @Test
    fun `test reordering middle intervals`() {
        viewModel.addInterval(IntervalType.RUN, 180) // index 0: Run
        viewModel.addInterval(IntervalType.WALK, 120) // index 1: Walk
        viewModel.addInterval(IntervalType.RUN, 300) // index 2: Run

        // Move Walk (index 1) up to index 0
        viewModel.moveMiddleIntervalUp(1)
        var intervals = viewModel.uiState.value.middleIntervals
        assertEquals(IntervalType.WALK, intervals[0].type)
        assertEquals(IntervalType.RUN, intervals[1].type)

        // Move Walk (index 0) down to index 1
        viewModel.moveMiddleIntervalDown(0)
        intervals = viewModel.uiState.value.middleIntervals
        assertEquals(IntervalType.RUN, intervals[0].type)
        assertEquals(IntervalType.WALK, intervals[1].type)

        // Move index 0 (Run) to index 2
        viewModel.moveMiddleInterval(0, 2)
        intervals = viewModel.uiState.value.middleIntervals
        assertEquals(IntervalType.WALK, intervals[0].type)
        assertEquals(IntervalType.RUN, intervals[1].type)
        assertEquals(IntervalType.RUN, intervals[2].type)
    }

    @Test
    fun `test finishBuildingWorkout saves workout`() = runTest {
        viewModel.updateTitle("Tempo Intervals")
        viewModel.addInterval(IntervalType.RUN, 300)
        viewModel.addInterval(IntervalType.WALK, 120)

        var savedId: Long? = null
        viewModel.finishBuildingWorkout { id ->
            savedId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(savedId)
        assertEquals(10001L, savedId)
        assertEquals(1, fakeRepository.savedWorkouts.size)
        assertEquals("Tempo Intervals", fakeRepository.savedWorkouts[0].title)
    }

    private class FakeTrainingPlanRepository : TrainingPlanRepository {
        val savedWorkouts = mutableListOf<TrainingWorkout>()
        private var nextId = 10001L

        override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = flowOf(emptyList())

        override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> = flowOf(savedWorkouts)

        override suspend fun getWorkoutById(id: Long): TrainingWorkout? = savedWorkouts.find { it.id == id }

        override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long {
            val id = nextId++
            val workout = TrainingWorkout(
                id = id,
                week = 0,
                workoutNumber = 0,
                title = title,
                intervals = intervals,
            )
            savedWorkouts.add(workout)
            return id
        }

        override suspend fun deleteCustomWorkout(id: Long) {
            savedWorkouts.removeAll { it.id == id }
        }
    }
}
