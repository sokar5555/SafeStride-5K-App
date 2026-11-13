package com.example.couchto5k.engine

import android.os.SystemClock
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.location.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class WorkoutEngine internal constructor(
    private val audioHapticManager: AudioHapticManager,
    private val settingsRepository: SettingsRepository?,
    @Suppress("UNUSED_PARAMETER") unusedMarker: Boolean = true,
) {
    @Inject
    constructor(
        audioHapticManager: AudioHapticManager,
        settingsRepository: SettingsRepository,
    ) : this(
        audioHapticManager = audioHapticManager,
        settingsRepository = settingsRepository,
        unusedMarker = true,
    )

    constructor(
        audioHapticManager: AudioHapticManager,
    ) : this(
        audioHapticManager = audioHapticManager,
        settingsRepository = null,
        unusedMarker = true,
    )

    private val _workoutState = MutableStateFlow(ActiveWorkoutState())
    val workoutState: StateFlow<ActiveWorkoutState> = _workoutState.asStateFlow()

    private var activeWorkout: TrainingWorkout? = null
    private var tickerJob: Job? = null

    @Volatile
    private var currentDistanceUnit: DistanceUnit = DistanceUnit.MILES

    // Core timing anchors
    private var intervalStartRealtimeMillis: Long = 0L
    private var intervalDurationMillis: Long = 0L
    private var totalElapsedBeforeCurrentIntervalMillis: Long = 0L

    // For pause/resume logic
    private var pauseStartRealtimeMillis: Long = 0L
    private var accumulatedPauseMillisInCurrentInterval: Long = 0L

    // Split tracking anchors
    private var splitStartElapsedMillis: Long = 0L
    private var completedSplitsCount: Int = 0

    // Pace throttling anchor
    private var lastPaceUpdateElapsedMillis: Long = -PACE_UPDATE_INTERVAL_MS

    // Per-interval distance tracking and open-exercise segment history
    private var intervalStartDistanceMeters: Double = 0.0
    private val completedOpenSegments = mutableListOf<OpenExerciseSegment>()

    private val engineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val FREE_RUN_WORKOUT_ID = 0L
        const val METERS_PER_MILE = 1609.34
        const val METERS_PER_KM = 1000.0
        const val PACE_UPDATE_INTERVAL_MS = 3_000L

        fun createOpenExerciseWorkout(initialType: IntervalType = IntervalType.RUN): TrainingWorkout {
            val resolvedType = if (initialType == IntervalType.WALK) IntervalType.WALK else IntervalType.RUN
            val title = if (resolvedType == IntervalType.WALK) "Free Walk" else "Free Run"
            return TrainingWorkout(
                id = FREE_RUN_WORKOUT_ID,
                week = 0,
                workoutNumber = 0,
                title = title,
                intervals = listOf(WorkoutInterval(sequence = 0, type = resolvedType, durationSeconds = 0)),
            )
        }

        val FREE_RUN_WORKOUT = createOpenExerciseWorkout(IntervalType.RUN)
    }

    init {
        if (settingsRepository != null) {
            engineScope.launch {
                settingsRepository.distanceUnit.collectLatest { unit ->
                    currentDistanceUnit = unit
                }
            }
        }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        currentDistanceUnit = unit
    }

    private fun getSplitThresholdMeters(): Double {
        return if (currentDistanceUnit == DistanceUnit.MILES) METERS_PER_MILE else METERS_PER_KM
    }

    fun startFreeRun(initialType: IntervalType = IntervalType.RUN) {
        start(createOpenExerciseWorkout(initialType), isFreeRun = true)
    }

    fun start(workout: TrainingWorkout, isFreeRun: Boolean = workout.id == FREE_RUN_WORKOUT_ID) {
        val currentStatus = workoutState.value.status
        if ((activeWorkout != null) && (currentStatus != WorkoutEngineStatus.COMPLETED) && (currentStatus != WorkoutEngineStatus.CANCELLED) && (currentStatus != WorkoutEngineStatus.NOT_STARTED)) {
            stopWorkout()
        }

        val resolvedWorkout = if (isFreeRun && workout.intervals.isEmpty()) {
            FREE_RUN_WORKOUT.copy(id = workout.id, title = workout.title.ifBlank { "Free Run" })
        } else {
            workout
        }

        activeWorkout = resolvedWorkout
        if (!isFreeRun && resolvedWorkout.intervals.isEmpty()) {
            _workoutState.value = ActiveWorkoutState(status = WorkoutEngineStatus.COMPLETED)
            return
        }

        val firstInterval = resolvedWorkout.intervals.firstOrNull()
            ?: WorkoutInterval(0, IntervalType.RUN, 0)

        intervalDurationMillis = if (isFreeRun) 0L else firstInterval.durationSeconds * 1000L
        intervalStartRealtimeMillis = SystemClock.elapsedRealtime()
        totalElapsedBeforeCurrentIntervalMillis = 0L
        accumulatedPauseMillisInCurrentInterval = 0L

        splitStartElapsedMillis = 0L
        completedSplitsCount = 0
        lastPaceUpdateElapsedMillis = -PACE_UPDATE_INTERVAL_MS
        intervalStartDistanceMeters = 0.0
        completedOpenSegments.clear()

        _workoutState.value = ActiveWorkoutState(
            workoutId = resolvedWorkout.id,
            isFreeRun = isFreeRun,
            currentIntervalIndex = 0,
            currentInterval = firstInterval,
            intervalRemainingMillis = intervalDurationMillis,
            totalElapsedMillis = 0L,
            isPaused = false,
            distanceMeters = 0.0,
            averagePaceSecondsPerKm = 0.0,
            currentSplitDistanceMeters = 0.0,
            currentSplitDurationMillis = 0L,
            currentSplitPaceSecondsPerKm = 0.0,
            intervalDistancesMeters = mapOf(0 to 0.0),
            openExerciseSegments = emptyList(),
            startedAt = Instant.now(),
            status = WorkoutEngineStatus.RUNNING,
        )

        if (isFreeRun) {
            audioHapticManager.playTransitionSound()
            audioHapticManager.vibrateTransition()
            val startMsg = if (firstInterval.type == IntervalType.WALK) "Free walk started" else "Free run started"
            audioHapticManager.speak(startMsg)
        } else {
            announceInterval(firstInterval)
        }
        startTicker()
    }

    fun switchOpenExerciseType(newType: IntervalType) {
        val currentState = _workoutState.value
        if (!currentState.isFreeRun) return
        if ((currentState.status != WorkoutEngineStatus.RUNNING) && (currentState.status != WorkoutEngineStatus.PAUSED)) return
        if ((newType != IntervalType.RUN) && (newType != IntervalType.WALK)) return

        val (_, currentType) = currentState.currentInterval ?: return
        if (currentType == newType) return

        val now = SystemClock.elapsedRealtime()
        val activeMillisInSegment = computeActiveMillisInCurrentInterval(currentState, now)
        val segmentDistance = (currentState.distanceMeters - intervalStartDistanceMeters).coerceAtLeast(0.0)

        completedOpenSegments.add(
            OpenExerciseSegment(
                sequence = currentState.currentIntervalIndex,
                type = currentType,
                durationMillis = activeMillisInSegment,
                distanceMeters = segmentDistance,
            ),
        )

        totalElapsedBeforeCurrentIntervalMillis += activeMillisInSegment
        intervalStartRealtimeMillis = now
        accumulatedPauseMillisInCurrentInterval = 0L
        if (currentState.isPaused) {
            pauseStartRealtimeMillis = now
        }
        intervalStartDistanceMeters = currentState.distanceMeters

        val nextIndex = currentState.currentIntervalIndex + 1
        val nextInterval = WorkoutInterval(sequence = nextIndex, type = newType, durationSeconds = 0)
        val updatedIntervalDistances = currentState.intervalDistancesMeters.toMutableMap().apply {
            put(currentState.currentIntervalIndex, segmentDistance)
            putIfAbsent(nextIndex, 0.0)
        }

        _workoutState.update {
            it.copy(
                currentIntervalIndex = nextIndex,
                currentInterval = nextInterval,
                totalElapsedMillis = totalElapsedBeforeCurrentIntervalMillis,
                intervalDistancesMeters = updatedIntervalDistances,
                openExerciseSegments = completedOpenSegments.toList(),
            )
        }

        audioHapticManager.playTransitionSound()
        audioHapticManager.vibrateTransition()
        audioHapticManager.speak(if (newType == IntervalType.WALK) "Walk" else "Run")
    }

    fun pause() {
        if ((_workoutState.value.status != WorkoutEngineStatus.RUNNING) || _workoutState.value.isPaused) return

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
        if ((_workoutState.value.status != WorkoutEngineStatus.PAUSED) || !_workoutState.value.isPaused) return

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
        if (_workoutState.value.isFreeRun) {
            completeWorkout()
            return
        }
        val currentIntervalIndex = _workoutState.value.currentIntervalIndex

        if (currentIntervalIndex < (currentWorkout.intervals.size - 1)) {
            transitionToInterval(currentIntervalIndex + 1, currentWorkout)
        } else {
            completeWorkout()
        }
    }

    fun previousInterval() {
        val currentWorkout = activeWorkout ?: return
        if (_workoutState.value.isFreeRun) return
        val currentIntervalIndex = _workoutState.value.currentIntervalIndex

        if (currentIntervalIndex > 0) {
            transitionToInterval(currentIntervalIndex - 1, currentWorkout)
        } else {
            // Restart current interval
            transitionToInterval(0, currentWorkout)
        }
    }

    fun addDistance(meters: Double) {
        if (meters <= 0.0) return

        val currentState = _workoutState.value
        val activeElapsedMillis = computeActiveTotalElapsedMillis(currentState)
        val newTotalDistance = currentState.distanceMeters + meters
        val currentIntervalDistance = (newTotalDistance - intervalStartDistanceMeters).coerceAtLeast(0.0)
        val updatedIntervalDistances = currentState.intervalDistancesMeters.toMutableMap().apply {
            put(currentState.currentIntervalIndex, currentIntervalDistance)
        }

        val splitThreshold = getSplitThresholdMeters()
        var newSplitDistance = currentState.currentSplitDistanceMeters + meters
        var currentSplitDuration = (activeElapsedMillis - splitStartElapsedMillis).coerceAtLeast(0L)
        var splitCompleted = false

        while (newSplitDistance >= splitThreshold) {
            splitCompleted = true
            completedSplitsCount++
            newSplitDistance -= splitThreshold

            val splitDurationSeconds = currentSplitDuration / 1000.0
            val splitPaceSecondsPerUnit = if (splitDurationSeconds > 0.0) {
                splitDurationSeconds
            } else {
                0.0
            }

            audioHapticManager.announceSplit(
                splitNumber = completedSplitsCount,
                paceSecondsPerUnit = splitPaceSecondsPerUnit,
                unit = currentDistanceUnit,
            )

            splitStartElapsedMillis = activeElapsedMillis
            currentSplitDuration = 0L
        }

        val (averagePaceSecondsPerKm, splitPaceSecondsPerKm) = refreshPacesIfDue(
            activeElapsedMillis = activeElapsedMillis,
            totalDistanceMeters = newTotalDistance,
            splitDistanceMeters = newSplitDistance,
            splitDurationMillis = currentSplitDuration,
            forceUpdate = splitCompleted,
        )

        _workoutState.update {
            it.copy(
                distanceMeters = newTotalDistance,
                averagePaceSecondsPerKm = averagePaceSecondsPerKm,
                currentSplitDistanceMeters = newSplitDistance,
                currentSplitDurationMillis = currentSplitDuration,
                currentSplitPaceSecondsPerKm = splitPaceSecondsPerKm,
                intervalDistancesMeters = updatedIntervalDistances,
            )
        }
    }

    fun addRoutePoint(point: LocationPoint) {
        _workoutState.update {
            it.copy(routePoints = it.routePoints + point)
        }
    }

    fun completeFreeRun() {
        if (_workoutState.value.status == WorkoutEngineStatus.COMPLETED) return
        completeWorkout()
    }

    fun stopWorkout() {
        val currentState = _workoutState.value
        if (currentState.isFreeRun && (currentState.status != WorkoutEngineStatus.COMPLETED)) {
            completeWorkout()
            return
        }
        if ((currentState.currentInterval?.type == IntervalType.COOLDOWN) && (currentState.status != WorkoutEngineStatus.COMPLETED)) {
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
        val currentState = _workoutState.value
        val now = SystemClock.elapsedRealtime()
        val activeElapsedMillis = computeActiveTotalElapsedMillis(currentState)
        val currentIntervalDistance = (currentState.distanceMeters - intervalStartDistanceMeters).coerceAtLeast(0.0)
        val finalIntervalDistances = currentState.intervalDistancesMeters.toMutableMap().apply {
            put(currentState.currentIntervalIndex, currentIntervalDistance)
        }

        val finalOpenSegments = if (currentState.isFreeRun) {
            val activeSegmentMillis = computeActiveMillisInCurrentInterval(currentState, now)
            val currentType = currentState.currentInterval?.type ?: IntervalType.RUN
            (completedOpenSegments + OpenExerciseSegment(
                sequence = currentState.currentIntervalIndex,
                type = currentType,
                durationMillis = activeSegmentMillis,
                distanceMeters = currentIntervalDistance,
            )).toList()
        } else {
            emptyList()
        }

        stopTicker()
        audioHapticManager.playTransitionSound()
        audioHapticManager.vibrateTransition()
        audioHapticManager.speak("Workout complete!")

        val totalPlannedMillis = if (currentState.isFreeRun) {
            0L
        } else {
            (activeWorkout?.totalPlannedDurationSeconds ?: 0) * 1000L
        }

        val finalElapsedMillis = if (totalPlannedMillis > 0) {
            maxOf(activeElapsedMillis, totalPlannedMillis)
        } else {
            maxOf(currentState.totalElapsedMillis, activeElapsedMillis)
        }
        val finalSplitDurationMillis = (finalElapsedMillis - splitStartElapsedMillis).coerceAtLeast(0L)
        val (finalAvgPace, finalSplitPace) = refreshPacesIfDue(
            activeElapsedMillis = finalElapsedMillis,
            totalDistanceMeters = currentState.distanceMeters,
            splitDistanceMeters = currentState.currentSplitDistanceMeters,
            splitDurationMillis = finalSplitDurationMillis,
            forceUpdate = true,
        )

        _workoutState.update {
            it.copy(
                intervalRemainingMillis = 0L,
                totalElapsedMillis = finalElapsedMillis,
                averagePaceSecondsPerKm = finalAvgPace,
                currentSplitPaceSecondsPerKm = finalSplitPace,
                intervalDistancesMeters = finalIntervalDistances,
                openExerciseSegments = finalOpenSegments,
                isPaused = true,
                status = WorkoutEngineStatus.COMPLETED,
            )
        }
    }

    private fun transitionToInterval(newIndex: Int, workout: TrainingWorkout) {
        val currentState = _workoutState.value

        // Snapshot cumulative distance for the interval we are leaving
        val currentIntervalDistance = (currentState.distanceMeters - intervalStartDistanceMeters).coerceAtLeast(0.0)
        val updatedIntervalDistances = currentState.intervalDistancesMeters.toMutableMap().apply {
            put(currentState.currentIntervalIndex, currentIntervalDistance)
            putIfAbsent(newIndex, 0.0)
        }
        intervalStartDistanceMeters = currentState.distanceMeters

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
                intervalDistancesMeters = updatedIntervalDistances,
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
        return if ((min > 0) && (sec > 0)) {
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

    private fun computeActiveMillisInCurrentInterval(currentState: ActiveWorkoutState, now: Long): Long {
        if (currentState.status == WorkoutEngineStatus.NOT_STARTED) return 0L
        val endAnchor = if (currentState.isPaused || (currentState.status != WorkoutEngineStatus.RUNNING)) {
            pauseStartRealtimeMillis
        } else {
            now
        }
        return (endAnchor - intervalStartRealtimeMillis - accumulatedPauseMillisInCurrentInterval).coerceAtLeast(0L)
    }

    private fun computeActiveTotalElapsedMillis(currentState: ActiveWorkoutState): Long {
        if (currentState.status == WorkoutEngineStatus.NOT_STARTED) return 0L
        if (currentState.isPaused || (currentState.status != WorkoutEngineStatus.RUNNING)) {
            return currentState.totalElapsedMillis
        }
        val now = SystemClock.elapsedRealtime()
        val activeMillisInInterval = (now - intervalStartRealtimeMillis - accumulatedPauseMillisInCurrentInterval).coerceAtLeast(0L)
        return maxOf(currentState.totalElapsedMillis, totalElapsedBeforeCurrentIntervalMillis + activeMillisInInterval)
    }

    private fun calculateSplitPaceSecondsPerKm(
        splitDistanceMeters: Double,
        splitDurationMillis: Long,
    ): Double {
        if ((splitDistanceMeters <= 0.001) || (splitDurationMillis <= 0L)) return 0.0
        val splitDurationSeconds = splitDurationMillis / 1000.0
        val splitKm = splitDistanceMeters / 1000.0
        return splitDurationSeconds / splitKm
    }

    private fun refreshPacesIfDue(
        activeElapsedMillis: Long,
        totalDistanceMeters: Double,
        splitDistanceMeters: Double,
        splitDurationMillis: Long,
        forceUpdate: Boolean,
    ): Pair<Double, Double> {
        val currentState = _workoutState.value
        if (totalDistanceMeters <= 0.001) {
            return 0.0 to 0.0
        }

        val isInitialReading = lastPaceUpdateElapsedMillis < 0L
        val isThrottleIntervalElapsed = (activeElapsedMillis - lastPaceUpdateElapsedMillis) >= PACE_UPDATE_INTERVAL_MS

        if (!forceUpdate && !isInitialReading && !isThrottleIntervalElapsed) {
            return currentState.averagePaceSecondsPerKm to currentState.currentSplitPaceSecondsPerKm
        }

        lastPaceUpdateElapsedMillis = activeElapsedMillis
        val averagePace = calculateSplitPaceSecondsPerKm(
            splitDistanceMeters = totalDistanceMeters,
            splitDurationMillis = activeElapsedMillis,
        )
        val splitPace = calculateSplitPaceSecondsPerKm(
            splitDistanceMeters = splitDistanceMeters,
            splitDurationMillis = splitDurationMillis,
        )
        return averagePace to splitPace
    }

    internal fun updateTime() {
        val currentState = _workoutState.value
        if ((currentState.status != WorkoutEngineStatus.RUNNING) || currentState.isPaused) return

        val workout = activeWorkout ?: return

        val now = SystemClock.elapsedRealtime()
        val activeMillisInInterval = (now - intervalStartRealtimeMillis - accumulatedPauseMillisInCurrentInterval).coerceAtLeast(0L)
        val totalElapsed = totalElapsedBeforeCurrentIntervalMillis + activeMillisInInterval
        val currentSplitDuration = (totalElapsed - splitStartElapsedMillis).coerceAtLeast(0L)
        val (averagePaceSecondsPerKm, splitPaceSecondsPerKm) = refreshPacesIfDue(
            activeElapsedMillis = totalElapsed,
            totalDistanceMeters = currentState.distanceMeters,
            splitDistanceMeters = currentState.currentSplitDistanceMeters,
            splitDurationMillis = currentSplitDuration,
            forceUpdate = false,
        )

        if (currentState.isFreeRun) {
            _workoutState.update {
                it.copy(
                    intervalRemainingMillis = 0L,
                    totalElapsedMillis = totalElapsed,
                    averagePaceSecondsPerKm = averagePaceSecondsPerKm,
                    currentSplitDurationMillis = currentSplitDuration,
                    currentSplitPaceSecondsPerKm = splitPaceSecondsPerKm,
                )
            }
            return
        }

        val remainingMillis = intervalDurationMillis - activeMillisInInterval

        if (remainingMillis <= 0) {
            // Interval complete
            if (currentState.currentIntervalIndex < (workout.intervals.size - 1)) {
                transitionToInterval(currentState.currentIntervalIndex + 1, workout)
            } else {
                completeWorkout()
            }
        } else {
            _workoutState.update {
                it.copy(
                    intervalRemainingMillis = remainingMillis,
                    totalElapsedMillis = totalElapsed,
                    averagePaceSecondsPerKm = averagePaceSecondsPerKm,
                    currentSplitDurationMillis = currentSplitDuration,
                    currentSplitPaceSecondsPerKm = splitPaceSecondsPerKm,
                )
            }
        }
    }
}
