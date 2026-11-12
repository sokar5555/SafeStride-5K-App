package com.example.couchto5k.engine

import android.os.SystemClock
import com.example.couchto5k.domain.model.DistanceUnit
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
import java.time.Duration

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

    @Test
    fun freeRun_countsUpIndefinitelyAndCompletesCleanly() {
        workoutEngine.startFreeRun()

        val initialState = workoutEngine.workoutState.value
        assertTrue(initialState.isFreeRun)
        assertEquals(WorkoutEngine.FREE_RUN_WORKOUT_ID, initialState.workoutId)
        assertEquals(WorkoutEngineStatus.RUNNING, initialState.status)
        assertEquals(0L, initialState.intervalRemainingMillis)

        SystemClock.setCurrentTimeMillis(SystemClock.elapsedRealtime() + Duration.ofMinutes(45).toMillis())
        workoutEngine.updateTime()

        val runningState = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.RUNNING, runningState.status)
        assertEquals(0L, runningState.intervalRemainingMillis)
        assertTrue(runningState.totalElapsedMillis >= Duration.ofMinutes(45).toMillis())

        workoutEngine.completeFreeRun()
        val completedState = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.COMPLETED, completedState.status)
        assertTrue(completedState.isPaused)
    }

    @Test
    fun currentSplit_tracksPaceResetsOnMileBoundaryAndTriggersSplitAudioCue() {
        workoutEngine.setDistanceUnit(DistanceUnit.MILES)
        workoutEngine.startFreeRun()
        fakeAudioHapticManager.spokenMessages.clear()

        // Advance clock by 8 minutes 45 seconds (525 seconds)
        SystemClock.setCurrentTimeMillis(SystemClock.elapsedRealtime() + 525_000L)
        workoutEngine.updateTime()

        // Add half a mile first (804.67m) -> no split completed yet, current split pace calculated
        workoutEngine.addDistance(804.67)
        val midSplitState = workoutEngine.workoutState.value
        assertEquals(804.67, midSplitState.currentSplitDistanceMeters, 0.01)
        assertTrue(midSplitState.currentSplitPaceSecondsPerKm > 0.0)
        assertEquals(0, fakeAudioHapticManager.spokenMessages.size)

        // Complete Mile 1 (another 810.0m -> total 1614.67m, crossing 1609.34m boundary with 5.33m remainder)
        workoutEngine.addDistance(810.0)
        val afterMile1State = workoutEngine.workoutState.value

        // Split distance resets to remainder (~5.33m)
        assertEquals(1614.67 - 1609.34, afterMile1State.currentSplitDistanceMeters, 0.01)
        assertEquals(1, fakeAudioHapticManager.spokenMessages.size)
        assertEquals("Mile 1 complete, 8:45 pace", fakeAudioHapticManager.spokenMessages.first())
        assertTrue(fakeAudioHapticManager.transitionSoundPlayed)
        assertTrue(fakeAudioHapticManager.vibrationTriggered)
    }

    @Test
    fun intervalTransitions_snapshotPerIntervalDistancesCorrectly() {
        val workout = createTestWorkout()
        workoutEngine.start(workout)

        // Interval 0 (Warmup): 400m
        workoutEngine.addDistance(400.0)
        workoutEngine.skipToNextInterval()

        // Interval 1 (Run): 250m
        workoutEngine.addDistance(250.0)
        workoutEngine.skipToNextInterval()

        val state = workoutEngine.workoutState.value
        assertEquals(650.0, state.distanceMeters, 0.001)
        assertEquals(400.0, state.intervalDistancesMeters[0] ?: 0.0, 0.001)
        assertEquals(250.0, state.intervalDistancesMeters[1] ?: 0.0, 0.001)
    }
}

class FakeAudioHapticManager : AudioHapticManager {
    override val isTtsReady: StateFlow<Boolean> = MutableStateFlow(true)
    val spokenMessages = mutableListOf<String>()
    var transitionSoundPlayed = false
    var vibrationTriggered = false

    override fun playTransitionSound() {
        transitionSoundPlayed = true
    }

    override fun vibrateTransition() {
        vibrationTriggered = true
    }

    override fun speak(message: String) {
        spokenMessages.add(message)
    }

    override fun release() {}
}
