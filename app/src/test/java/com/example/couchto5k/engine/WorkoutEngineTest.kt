package com.example.couchto5k.engine

import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutEngineTest {

    private lateinit var fakeAudioHapticManager: FakeAudioHapticManager
    private lateinit var workoutEngine: WorkoutEngine

    @Before
    fun setup() {
        fakeAudioHapticManager = FakeAudioHapticManager()
        workoutEngine = WorkoutEngine(fakeAudioHapticManager)
    }

    private fun createTestWorkout(): TrainingWorkout {
        val intervals = listOf(
            WorkoutInterval(0, IntervalType.WARMUP, 300), // 5 min
            WorkoutInterval(1, IntervalType.RUN, 60),    // 1 min
            WorkoutInterval(2, IntervalType.WALK, 90),   // 1.5 min
            WorkoutInterval(3, IntervalType.RUN, 60),    // 1 min
            WorkoutInterval(4, IntervalType.COOLDOWN, 300) // 5 min
        )
        return TrainingWorkout(
            id = 1L,
            week = 1,
            workoutNumber = 1,
            title = "Week 1, Workout 1",
            intervals = intervals
        )
    }

    @Test
    fun start_initializesEngineStateAndAnnouncesFirstInterval() {
        val workout = createTestWorkout()

        workoutEngine.start(workout)

        val state = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.RUNNING, state.status)
        assertEquals(1L, state.workoutId)
        assertEquals(0, state.currentIntervalIndex)
        assertEquals(IntervalType.WARMUP, state.currentInterval?.type)
        assertEquals(300000L, state.intervalRemainingMillis)
        assertFalse(state.isPaused)

        assertEquals(1, fakeAudioHapticManager.spokenMessages.size)
        assertTrue(fakeAudioHapticManager.spokenMessages.first().contains("Warmup"))
    }

    @Test
    fun pause_and_resume_updatesStateCorrectly() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)

        workoutEngine.pause()
        val pausedState = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.PAUSED, pausedState.status)
        assertTrue(pausedState.isPaused)

        workoutEngine.resume()
        val resumedState = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.RUNNING, resumedState.status)
        assertFalse(resumedState.isPaused)
    }

    @Test
    fun skipToNextInterval_transitionsToNextInterval() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)

        workoutEngine.skipToNextInterval()

        val state = workoutEngine.workoutState.value
        assertEquals(1, state.currentIntervalIndex)
        assertEquals(IntervalType.RUN, state.currentInterval?.type)
        assertEquals(60000L, state.intervalRemainingMillis)

        assertEquals(2, fakeAudioHapticManager.spokenMessages.size)
        assertTrue(fakeAudioHapticManager.spokenMessages.last().contains("Run"))
    }

    @Test
    fun previousInterval_transitionsToPreviousInterval() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)
        workoutEngine.skipToNextInterval() // Move to Interval 1 (RUN)

        workoutEngine.previousInterval() // Move back to Interval 0 (WARMUP)

        val state = workoutEngine.workoutState.value
        assertEquals(0, state.currentIntervalIndex)
        assertEquals(IntervalType.WARMUP, state.currentInterval?.type)
        assertEquals(300000L, state.intervalRemainingMillis)
    }

    @Test
    fun stopWorkout_resetsEngineState() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)

        workoutEngine.stopWorkout()

        val state = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.CANCELLED, state.status)
    }

    @Test
    fun addDistance_accumulatesDistanceCorrectly() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)

        workoutEngine.addDistance(15.5)
        workoutEngine.addDistance(10.0)

        val state = workoutEngine.workoutState.value
        assertEquals(25.5, state.distanceMeters, 0.001)
    }
}

class FakeAudioHapticManager : AudioHapticManager {
    override val isTtsReady: StateFlow<Boolean> = MutableStateFlow(true)
    val spokenMessages = mutableListOf<String>()

    override fun playTransitionSound() {}
    override fun vibrateTransition() {}
    override fun speak(message: String) {
        spokenMessages.add(message)
    }
    override fun release() {}
}
