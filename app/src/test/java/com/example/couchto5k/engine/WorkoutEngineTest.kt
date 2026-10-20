package com.example.couchto5k.engine

import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WorkoutEngineTest {

    private lateinit var workoutEngine: WorkoutEngine
    private lateinit var fakeAudioHapticManager: FakeAudioHapticManager
    private val testDispatcher = StandardTestDispatcher()

    private val testWorkout = TrainingWorkout(
        id = 1L,
        week = 1,
        workoutNumber = 1,
        title = "Test Workout",
        intervals = listOf(
            WorkoutInterval(0, IntervalType.WARMUP, 300), // 5 mins
            WorkoutInterval(1, IntervalType.RUN, 60),     // 1 min
            WorkoutInterval(2, IntervalType.WALK, 90),    // 1.5 mins
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeAudioHapticManager = FakeAudioHapticManager()
        workoutEngine = WorkoutEngine(fakeAudioHapticManager)
        ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1000000L)) // Start at arbitrary non-zero time
    }

    @After
    fun tearDown() {
        if (::workoutEngine.isInitialized) {
            workoutEngine.stopWorkout()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial start`() = runTest(testDispatcher) {
        workoutEngine.start(testWorkout)

        val (_, currentIntervalIndex, currentInterval, intervalRemainingMillis, totalElapsedMillis, isPaused, _, _, status) = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.RUNNING, status)
        assertFalse(isPaused)
        assertEquals(0, currentIntervalIndex)
        assertEquals(IntervalType.WARMUP, currentInterval?.type)
        assertEquals(300000L, intervalRemainingMillis) // 5 minutes in ms
        assertEquals(0L, totalElapsedMillis)
        assertEquals(1, fakeAudioHapticManager.speakCount) // Announced first interval

        workoutEngine.stopWorkout()
    }

    @Test
    fun `test pause and resume without drift`() = runTest(testDispatcher) {
        workoutEngine.start(testWorkout)

        // Let some time pass
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10))
        testDispatcher.scheduler.advanceTimeBy(100) // Let ticker update

        // After 10s running
        val state1 = workoutEngine.workoutState.value
        assertEquals(290000L, state1.intervalRemainingMillis) // 300s - 10s
        assertEquals(10000L, state1.totalElapsedMillis)

        // Pause
        workoutEngine.pause()
        assertEquals(WorkoutEngineStatus.PAUSED, workoutEngine.workoutState.value.status)
        assertTrue(workoutEngine.workoutState.value.isPaused)

        // Advance time by 30 seconds while paused
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(30))
        testDispatcher.scheduler.advanceTimeBy(100) // Ticker shouldn't update, but just in case

        val state2 = workoutEngine.workoutState.value
        assertEquals(290000L, state2.intervalRemainingMillis) // Still 290s
        assertEquals(10000L, state2.totalElapsedMillis)

        // Resume
        workoutEngine.resume()
        assertEquals(WorkoutEngineStatus.RUNNING, workoutEngine.workoutState.value.status)
        assertFalse(workoutEngine.workoutState.value.isPaused)

        // Advance time by another 5 seconds running
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5))
        testDispatcher.scheduler.advanceTimeBy(100) // Let ticker update

        val state3 = workoutEngine.workoutState.value
        assertEquals(285000L, state3.intervalRemainingMillis) // 290s - 5s
        assertEquals(15000L, state3.totalElapsedMillis) // 10s + 5s

        workoutEngine.stopWorkout()
    }

    @Test
    fun `test skip forward`() = runTest(testDispatcher) {
        workoutEngine.start(testWorkout)

        // Advance 10s into warmup
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10))
        testDispatcher.scheduler.advanceTimeBy(100)

        workoutEngine.skipToNextInterval()

        val state = workoutEngine.workoutState.value
        assertEquals(1, state.currentIntervalIndex)
        assertEquals(IntervalType.RUN, state.currentInterval?.type)
        assertEquals(60000L, state.intervalRemainingMillis) // New interval 60s
        assertEquals(300000L, state.totalElapsedMillis) // Elapsed is sum of previous intervals, skipping immediately counts them as elapsed
        assertEquals(2, fakeAudioHapticManager.speakCount)

        workoutEngine.stopWorkout()
    }

    @Test
    fun `test previous interval`() = runTest(testDispatcher) {
        workoutEngine.start(testWorkout)
        workoutEngine.skipToNextInterval() // go to RUN (index 1)
        workoutEngine.skipToNextInterval() // go to WALK (index 2)

        // Advance 5s into walk
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5))
        testDispatcher.scheduler.advanceTimeBy(100)

        workoutEngine.previousInterval() // Back to RUN (index 1)

        val state = workoutEngine.workoutState.value
        assertEquals(1, state.currentIntervalIndex)
        assertEquals(IntervalType.RUN, state.currentInterval?.type)
        assertEquals(60000L, state.intervalRemainingMillis) // Resets to 60s
        assertEquals(300000L, state.totalElapsedMillis) // Total elapsed is just warmup time

        workoutEngine.stopWorkout()
    }

    @Test
    fun `test natural progression and completion`() = runTest(testDispatcher) {
        workoutEngine.start(testWorkout)

        // Advance full 5 mins of warmup
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(300))
        testDispatcher.scheduler.advanceTimeBy(100)

        // Should auto-transition to RUN
        assertEquals(1, workoutEngine.workoutState.value.currentIntervalIndex)
        assertEquals(300000L, workoutEngine.workoutState.value.totalElapsedMillis)

        // Advance full 1 min of RUN
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(60))
        testDispatcher.scheduler.advanceTimeBy(100)

        // Should auto-transition to WALK
        assertEquals(2, workoutEngine.workoutState.value.currentIntervalIndex)
        assertEquals(360000L, workoutEngine.workoutState.value.totalElapsedMillis)

        // Advance full 1.5 mins of WALK
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(90))
        testDispatcher.scheduler.advanceTimeBy(100)

        // Should complete
        val state = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.COMPLETED, state.status)
        assertTrue(state.isPaused)
        assertEquals(0L, state.intervalRemainingMillis)

        workoutEngine.stopWorkout()
    }

    class FakeAudioHapticManager : AudioHapticManager {
        override val isTtsReady = MutableStateFlow(value = true)
        var speakCount = 0
        var transitionSoundCount = 0
        var transitionVibrateCount = 0

        override fun playTransitionSound() { transitionSoundCount++ }
        override fun vibrateTransition() { transitionVibrateCount++ }
        override fun speak(message: String) { speakCount++ }
        override fun release() {}
    }
}