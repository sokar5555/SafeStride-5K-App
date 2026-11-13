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
            WorkoutInterval(4, IntervalType.COOLDOWN, 300), // 5 min
        )
        return TrainingWorkout(
            id = 1L,
            week = 1,
            workoutNumber = 1,
            title = "Week 1, Workout 1",
            intervals = intervals,
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

    @Test
    fun paceUpdates_areThrottledToEveryThreeSeconds() {
        workoutEngine.setDistanceUnit(DistanceUnit.KILOMETERS)
        workoutEngine.startFreeRun(IntervalType.RUN)
        val startRealtime = SystemClock.elapsedRealtime()

        // At t = 1.0s, first distance arrives -> initial pace latches
        SystemClock.setCurrentTimeMillis(startRealtime + 1_000L)
        workoutEngine.updateTime()
        workoutEngine.addDistance(5.0) // 5m in 1s -> 200 s/km
        val paceAt1s = workoutEngine.workoutState.value.averagePaceSecondsPerKm
        val splitPaceAt1s = workoutEngine.workoutState.value.currentSplitPaceSecondsPerKm
        assertEquals(200.0, paceAt1s, 0.1)
        assertEquals(200.0, splitPaceAt1s, 0.1)

        // At t = 2.5s (only 1.5s elapsed since last pace update < 3s) -> paces stay unchanged
        SystemClock.setCurrentTimeMillis(startRealtime + 2_500L)
        workoutEngine.updateTime()
        workoutEngine.addDistance(5.0) // total 10m in 2.5s -> would be 250 s/km if unthrottled
        assertEquals(200.0, workoutEngine.workoutState.value.averagePaceSecondsPerKm, 0.1)
        assertEquals(200.0, workoutEngine.workoutState.value.currentSplitPaceSecondsPerKm, 0.1)

        // At t = 4.0s (3.0s elapsed since last pace update >= 3s) -> paces refresh
        SystemClock.setCurrentTimeMillis(startRealtime + 4_000L)
        workoutEngine.updateTime()
        assertEquals(400.0, workoutEngine.workoutState.value.averagePaceSecondsPerKm, 0.1)
        assertEquals(400.0, workoutEngine.workoutState.value.currentSplitPaceSecondsPerKm, 0.1)
    }

    @Test
    fun openExercise_startsAsWalk_andSwitchesLiveBetweenWalkAndRun() {
        workoutEngine.startFreeRun(IntervalType.WALK)
        val startRealtime = SystemClock.elapsedRealtime()

        val initialState = workoutEngine.workoutState.value
        assertTrue(initialState.isFreeRun)
        assertEquals(IntervalType.WALK, initialState.currentInterval?.type)
        assertEquals("Free walk started", fakeAudioHapticManager.spokenMessages.last())

        // Walk for 60s and 80m
        SystemClock.setCurrentTimeMillis(startRealtime + 60_000L)
        workoutEngine.updateTime()
        workoutEngine.addDistance(80.0)

        // Switch live to RUN
        workoutEngine.switchOpenExerciseType(IntervalType.RUN)
        val switchedToRunState = workoutEngine.workoutState.value
        assertEquals(1, switchedToRunState.currentIntervalIndex)
        assertEquals(IntervalType.RUN, switchedToRunState.currentInterval?.type)
        assertEquals("Run", fakeAudioHapticManager.spokenMessages.last())
        assertEquals(1, switchedToRunState.openExerciseSegments.size)
        assertEquals(IntervalType.WALK, switchedToRunState.openExerciseSegments[0].type)
        assertEquals(60_000L, switchedToRunState.openExerciseSegments[0].durationMillis)
        assertEquals(80.0, switchedToRunState.openExerciseSegments[0].distanceMeters, 0.01)

        // Run for 120s and 300m
        SystemClock.setCurrentTimeMillis(startRealtime + 180_000L)
        workoutEngine.updateTime()
        workoutEngine.addDistance(300.0)

        workoutEngine.completeFreeRun()
        val completedState = workoutEngine.workoutState.value
        assertEquals(WorkoutEngineStatus.COMPLETED, completedState.status)
        assertEquals(2, completedState.openExerciseSegments.size)
        assertEquals(IntervalType.WALK, completedState.openExerciseSegments[0].type)
        assertEquals(60_000L, completedState.openExerciseSegments[0].durationMillis)
        assertEquals(80.0, completedState.openExerciseSegments[0].distanceMeters, 0.01)
        assertEquals(IntervalType.RUN, completedState.openExerciseSegments[1].type)
        assertEquals(120_000L, completedState.openExerciseSegments[1].durationMillis)
        assertEquals(300.0, completedState.openExerciseSegments[1].distanceMeters, 0.01)
    }

    @Test
    fun openExercise_pauseDuringSegment_excludesPausedDurationFromSegment() {
        workoutEngine.startFreeRun(IntervalType.RUN)
        val t0 = SystemClock.elapsedRealtime()

        // Active for 10s
        SystemClock.setCurrentTimeMillis(t0 + 10_000L)
        workoutEngine.pause()

        // Paused for 30s
        SystemClock.setCurrentTimeMillis(t0 + 40_000L)
        workoutEngine.resume()

        // Active for 5s more
        SystemClock.setCurrentTimeMillis(t0 + 45_000L)
        workoutEngine.switchOpenExerciseType(IntervalType.WALK)

        val segments = workoutEngine.workoutState.value.openExerciseSegments
        assertEquals(1, segments.size)
        assertEquals(15_000L, segments[0].durationMillis)
    }
}

class FakeAudioHapticManager : AudioHapticManager {
    override val isTtsReady: StateFlow<Boolean> = MutableStateFlow(value = true)
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
