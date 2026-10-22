package com.example.couchto5k.engine

import android.os.SystemClock
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.WorkoutInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class WorkoutEngine @Inject constructor(
    private val audioHapticManager: AudioHapticManager,
) {
    private val _workoutState = MutableStateFlow(ActiveWorkoutState())
    val workoutState: StateFlow<ActiveWorkoutState> = _workoutState.asStateFlow()

    private var activeWorkout: TrainingWorkout? = null
    private var tickerJob: Job? = null

    // Core timing anchors
    private var intervalStartRealtimeMillis: Long = 0L
    private var intervalDurationMillis: Long = 0L
    private var totalElapsedBeforeCurrentIntervalMillis: Long = 0L

    // For pause/resume logic
    private var pauseStartRealtimeMillis: Long = 0L
    private var accumulatedPauseMillisInCurrentInterval: Long = 0L

    private val engineScope = CoroutineScope(Dispatchers.Main)

    fun start(workout: TrainingWorkout) {
        val currentStatus = workoutState.value.status
        if (activeWorkout != null && currentStatus != WorkoutEngineStatus.COMPLETED && currentStatus != WorkoutEngineStatus.CANCELLED && currentStatus != WorkoutEngineStatus.NOT_STARTED) {
            stopWorkout()
        }

        activeWorkout = workout
        if (workout.intervals.isEmpty()) {
            _workoutState.value = ActiveWorkoutState(status = WorkoutEngineStatus.COMPLETED)
            return
        }

        val firstInterval = workout.intervals.first()

        intervalDurationMillis = firstInterval.durationSeconds * 1000L
        intervalStartRealtimeMillis = SystemClock.elapsedRealtime()
        totalElapsedBeforeCurrentIntervalMillis = 0L
        accumulatedPauseMillisInCurrentInterval = 0L

        _workoutState.value = ActiveWorkoutState(
            workoutId = workout.id,
            currentIntervalIndex = 0,
            currentInterval = firstInterval,
            intervalRemainingMillis = intervalDurationMillis,
            totalElapsedMillis = 0L,
            isPaused = false,
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.RUNNING,
        )

        announceInterval(firstInterval)
        startTicker()
    }

    fun pause() {
        if (_workoutState.value.status != WorkoutEngineStatus.RUNNING || _workoutState.value.isPaused) return

        stopTicker()
        pauseStartRealtimeMillis = SystemClock.elapsedRealtime()

        _workoutState.update {
            it.copy(
                isPaused = true,
                status = WorkoutEngineStatus.PAUSED,
            )
        }
    }

    fun resume() {
        if (_workoutState.value.status != WorkoutEngineStatus.PAUSED || !_workoutState.value.isPaused) return

        val now = SystemClock.elapsedRealtime()
        accumulatedPauseMillisInCurrentInterval += (now - pauseStartRealtimeMillis)

        _workoutState.update {
            it.copy(
                isPaused = false,
                status = WorkoutEngineStatus.RUNNING,
            )
        }

        startTicker()
    }

    fun skipToNextInterval() {
        val currentWorkout = activeWorkout ?: return
        val currentIntervalIndex = _workoutState.value.currentIntervalIndex

        if (currentIntervalIndex < currentWorkout.intervals.size - 1) {
            transitionToInterval(currentIntervalIndex + 1, currentWorkout)
        } else {
            completeWorkout()
        }
    }

    fun previousInterval() {
        val currentWorkout = activeWorkout ?: return
        val currentIntervalIndex = _workoutState.value.currentIntervalIndex

        if (currentIntervalIndex > 0) {
            transitionToInterval(currentIntervalIndex - 1, currentWorkout)
        } else {
            // Restart current interval
            transitionToInterval(0, currentWorkout)
        }
    }

    fun stopWorkout() {
        val currentState = _workoutState.value
        if (currentState.currentInterval?.type == IntervalType.COOLDOWN && currentState.status != WorkoutEngineStatus.COMPLETED) {
            completeWorkout()
            return
        }

        stopTicker()
        activeWorkout = null
        _workoutState.update {
            it.copy(
                isPaused = true,
                status = WorkoutEngineStatus.CANCELLED,
            )
        }
    }

    private fun completeWorkout() {
        stopTicker()
        audioHapticManager.playTransitionSound()
        audioHapticManager.vibrateTransition()
        audioHapticManager.speak("Workout complete!")

        val totalPlannedMillis = (activeWorkout?.totalPlannedDurationSeconds ?: 0) * 1000L

        _workoutState.update {
            it.copy(
                intervalRemainingMillis = 0L,
                totalElapsedMillis = if (totalPlannedMillis > 0) maxOf(it.totalElapsedMillis, totalPlannedMillis) else it.totalElapsedMillis,
                isPaused = true,
                status = WorkoutEngineStatus.COMPLETED,
            )
        }
    }

    private fun transitionToInterval(newIndex: Int, workout: TrainingWorkout) {
        val currentState = _workoutState.value

        // Calculate total elapsed up to this point exactly based on intervals before this one
        var exactTotalElapsedSoFarSeconds = 0
        for (i in 0 until newIndex) {
            exactTotalElapsedSoFarSeconds += workout.intervals[i].durationSeconds
        }

        val newInterval = workout.intervals[newIndex]

        // Reset timing anchors for new interval
        intervalDurationMillis = newInterval.durationSeconds * 1000L
        intervalStartRealtimeMillis = SystemClock.elapsedRealtime()
        totalElapsedBeforeCurrentIntervalMillis = exactTotalElapsedSoFarSeconds * 1000L
        accumulatedPauseMillisInCurrentInterval = 0L

        val wasPaused = currentState.isPaused

        _workoutState.update {
            it.copy(
                currentIntervalIndex = newIndex,
                currentInterval = newInterval,
                intervalRemainingMillis = intervalDurationMillis,
                totalElapsedMillis = totalElapsedBeforeCurrentIntervalMillis,
                isPaused = wasPaused,
                status = if (wasPaused) WorkoutEngineStatus.PAUSED else WorkoutEngineStatus.RUNNING,
            )
        }

        announceInterval(newInterval)

        if (wasPaused) {
            pauseStartRealtimeMillis = intervalStartRealtimeMillis // Keep pause anchor aligned with start
        }
    }

    private fun announceInterval(interval: WorkoutInterval) {
        audioHapticManager.playTransitionSound()
        audioHapticManager.vibrateTransition()

        val msg = when (interval.type) {
            IntervalType.WARMUP -> "Warmup, ${formatDuration(interval.durationSeconds)}"
            IntervalType.RUN -> "Run for ${formatDuration(interval.durationSeconds)}"
            IntervalType.WALK -> "Walk for ${formatDuration(interval.durationSeconds)}"
            IntervalType.COOLDOWN -> "Cooldown, ${formatDuration(interval.durationSeconds)}"
        }
        audioHapticManager.speak(msg)
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0 && sec > 0) {
            "$min minutes $sec seconds"
        } else if (min > 0) {
            if (min == 1) "1 minute" else "$min minutes"
        } else {
            "$sec seconds"
        }
    }

    private fun startTicker() {
        stopTicker() // Ensure no leak
        tickerJob = engineScope.launch {
            while (isActive) {
                updateTime()
                delay(100.milliseconds) // 10Hz UI update
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateTime() {
        val currentState = _workoutState.value
        if (currentState.status != WorkoutEngineStatus.RUNNING || currentState.isPaused) return

        val workout = activeWorkout ?: return

        val now = SystemClock.elapsedRealtime()

        val activeMillisInInterval = now - intervalStartRealtimeMillis - accumulatedPauseMillisInCurrentInterval

        val remainingMillis = intervalDurationMillis - activeMillisInInterval

        if (remainingMillis <= 0) {
            // Interval complete
            if (currentState.currentIntervalIndex < workout.intervals.size - 1) {
                transitionToInterval(currentState.currentIntervalIndex + 1, workout)
            } else {
                completeWorkout()
            }
        } else {
            _workoutState.update {
                it.copy(
                    intervalRemainingMillis = remainingMillis,
                    totalElapsedMillis = totalElapsedBeforeCurrentIntervalMillis + activeMillisInInterval,
                )
            }
        }
    }
}