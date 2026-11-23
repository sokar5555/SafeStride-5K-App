package com.example.couchto5k.data.backup

import android.content.Context
import androidx.room.Room
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.data.local.entity.LocationPointEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.BackupCompletedInterval
import com.example.couchto5k.domain.model.BackupCompletedWorkout
import com.example.couchto5k.domain.model.BackupCustomInterval
import com.example.couchto5k.domain.model.BackupCustomWorkout
import com.example.couchto5k.domain.model.BackupLocationPoint
import com.example.couchto5k.domain.model.BackupScheduledWorkout
import com.example.couchto5k.domain.model.BackupSettings
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.engine.WorkoutEngineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DefaultBackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var backupRepository: DefaultBackupRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = InMemorySettingsRepository()
        backupRepository = DefaultBackupRepository(
            context = context,
            appDatabase = db,
            completedWorkoutDao = db.completedWorkoutDao(),
            customWorkoutDao = db.customWorkoutDao(),
            scheduledWorkoutDao = db.scheduledWorkoutDao(),
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createBackupSnapshot_capturesWorkoutsCustomWorkoutsScheduleAndSettings() = runTest {
        settingsRepository.setDistanceUnit(DistanceUnit.KILOMETERS)
        settingsRepository.setThemeMode(ThemeMode.DARK)
        settingsRepository.setAudioDuckingEnabled(false)
        settingsRepository.setCountdownVoiceCuesEnabled(false)
        settingsRepository.setProgramStartDate(LocalDate.of(2026, 9, 1))

        val workoutId = db.completedWorkoutDao().insertCompletedWorkout(
            CompletedWorkoutEntity(
                trainingWorkoutId = 2L,
                scheduledWorkoutId = 10L,
                title = "Week 1, Workout 2",
                startedAt = Instant.parse("2026-09-02T10:00:00Z"),
                completedAt = Instant.parse("2026-09-02T10:30:00Z"),
                durationSeconds = 1800,
                runningSeconds = 480,
                walkingSeconds = 1320,
                distanceMeters = 3100.0,
                averagePaceSecondsPerKm = 580.6,
                longestCompletedRunSeconds = 60,
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )
        db.completedWorkoutDao().insertCompletedIntervals(
            listOf(
                CompletedIntervalEntity(
                    completedWorkoutId = workoutId,
                    sequence = 0,
                    intervalType = IntervalType.WARMUP,
                    plannedDurationSeconds = 300,
                    actualDurationSeconds = 300,
                    distanceMeters = 400.0,
                    completed = true,
                ),
            ),
        )
        db.completedWorkoutDao().insertLocationPoints(
            listOf(
                LocationPointEntity(
                    completedWorkoutId = workoutId,
                    timestamp = 1788343200000L,
                    latitude = 40.7,
                    longitude = -74.0,
                    altitude = null,
                    accuracyMeters = 4.0f,
                    sequence = 0,
                ),
            ),
        )

        val customId = db.customWorkoutDao().insertCustomWorkout(
            CustomWorkoutEntity(title = "Intervals", createdAt = 12345L),
        )
        db.customWorkoutDao().insertCustomWorkoutIntervals(
            listOf(
                CustomWorkoutIntervalEntity(
                    customWorkoutId = customId,
                    sequence = 0,
                    intervalType = IntervalType.RUN,
                    durationSeconds = 600,
                ),
            ),
        )

        db.scheduledWorkoutDao().insertScheduledWorkouts(
            listOf(
                ScheduledWorkoutEntity(
                    id = 10L,
                    trainingWorkoutId = 2L,
                    scheduledDate = LocalDate.of(2026, 9, 2),
                    status = "COMPLETED",
                ),
            ),
        )

        val snapshot = backupRepository.createBackupSnapshot()
        assertEquals(DistanceUnit.KILOMETERS, snapshot.settings.distanceUnit)
        assertEquals(ThemeMode.DARK, snapshot.settings.themeMode)
        assertEquals(false, snapshot.settings.audioDuckingEnabled)
        assertEquals(false, snapshot.settings.countdownVoiceCuesEnabled)
        assertEquals(1, snapshot.completedWorkouts.size)
        assertEquals(400.0, snapshot.completedWorkouts.first().intervals.first().distanceMeters, 0.001)
        assertEquals(1, snapshot.completedWorkouts.first().routePoints.size)
        assertNull(snapshot.completedWorkouts.first().routePoints.first().altitude)
        assertEquals(1, snapshot.customWorkouts.size)
        assertEquals(1, snapshot.scheduledWorkouts.size)
    }

    @Test
    fun restoreBackupData_replacesExistingDatabaseAndSettingsAtomically() = runTest {
        // Seed existing data that should be replaced
        db.completedWorkoutDao().insertCompletedWorkout(
            CompletedWorkoutEntity(
                trainingWorkoutId = 1L,
                title = "Old Workout",
                startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                completedAt = Instant.parse("2026-01-01T08:20:00Z"),
                durationSeconds = 1200,
                runningSeconds = 300,
                walkingSeconds = 900,
                distanceMeters = 1500.0,
                averagePaceSecondsPerKm = 800.0,
                longestCompletedRunSeconds = 60,
                status = WorkoutEngineStatus.COMPLETED,
            ),
        )

        val incoming = AppBackupData(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            appName = BackupJsonCodec.APP_NAME,
            exportedAt = Instant.parse("2026-11-13T18:00:00Z"),
            settings = BackupSettings(
                distanceUnit = DistanceUnit.KILOMETERS,
                programStartDate = null,
                preferredWorkoutDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                progressiveWeeksExtension = 8,
                longRunDay = DayOfWeek.SATURDAY,
                voiceCuesEnabled = false,
                soundCuesEnabled = true,
                vibrationEnabled = false,
                audioDuckingEnabled = false,
                countdownVoiceCuesEnabled = false,
                themeMode = ThemeMode.SYSTEM,
                onboardingComplete = true,
            ),
            completedWorkouts = listOf(
                BackupCompletedWorkout(
                    id = 5L,
                    trainingWorkoutId = 9L,
                    scheduledWorkoutId = 20L,
                    title = "Restored Workout",
                    startedAt = Instant.parse("2026-10-10T09:00:00Z"),
                    completedAt = Instant.parse("2026-10-10T09:35:00Z"),
                    durationSeconds = 2100,
                    runningSeconds = 900,
                    walkingSeconds = 1200,
                    distanceMeters = 4200.0,
                    averagePaceSecondsPerKm = 500.0,
                    longestCompletedRunSeconds = 300,
                    status = WorkoutEngineStatus.COMPLETED,
                    intervals = listOf(
                        BackupCompletedInterval(0, IntervalType.RUN, 300, 300, 1000.0, true),
                    ),
                    routePoints = listOf(
                        BackupLocationPoint(0, 1791622800000L, 40.75, -73.99, 15.0, 3.5f),
                    ),
                ),
            ),
            customWorkouts = listOf(
                BackupCustomWorkout(
                    id = 99L,
                    title = "Restored Custom",
                    createdAt = 55555L,
                    intervals = listOf(BackupCustomInterval(0, IntervalType.RUN, 900)),
                ),
            ),
            scheduledWorkouts = listOf(
                BackupScheduledWorkout(20L, 9L, LocalDate.of(2026, 10, 10), "COMPLETED"),
            ),
        )

        backupRepository.restoreBackupData(incoming)

        val workouts = db.completedWorkoutDao().getAllCompletedWorkouts().first()
        assertEquals(1, workouts.size)
        assertEquals("Restored Workout", workouts.first().workout.title)
        assertEquals(1000.0, workouts.first().intervals.first().distanceMeters, 0.001)
        assertEquals(1, db.completedWorkoutDao().getAllLocationPoints().size)

        val customs = db.customWorkoutDao().getAllCustomWorkouts().first()
        assertEquals(1, customs.size)
        assertEquals("Restored Custom", customs.first().customWorkout.title)

        val scheduled = db.scheduledWorkoutDao().getAllScheduledWorkouts().first()
        assertEquals(1, scheduled.size)
        assertEquals(20L, scheduled.first().id)

        assertEquals(DistanceUnit.KILOMETERS, settingsRepository.distanceUnit.first())
        assertNull(settingsRepository.programStartDate.first())
        assertEquals(ThemeMode.SYSTEM, settingsRepository.themeMode.first())
        assertEquals(false, settingsRepository.audioDuckingEnabled.first())
        assertEquals(false, settingsRepository.countdownVoiceCuesEnabled.first())
        assertEquals(8, settingsRepository.progressiveWeeksExtension.first())
    }

    private class InMemorySettingsRepository : SettingsRepository {
        val startDateFlow = MutableStateFlow<LocalDate?>(null)
        val preferredDaysFlow = MutableStateFlow(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))
        val distanceUnitFlow = MutableStateFlow(DistanceUnit.MILES)
        val voiceCuesFlow = MutableStateFlow(true)
        val soundCuesFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val audioDuckingFlow = MutableStateFlow(true)
        val countdownVoiceCuesFlow = MutableStateFlow(true)
        val themeModeFlow = MutableStateFlow(ThemeMode.LIGHT)
        val onboardingFlow = MutableStateFlow(false)
        val progressiveWeeksFlow = MutableStateFlow(0)
        val longRunDayFlow = MutableStateFlow(DayOfWeek.SATURDAY)

        override val programStartDate: Flow<LocalDate?> = startDateFlow
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = preferredDaysFlow
        override val distanceUnit: Flow<DistanceUnit> = distanceUnitFlow
        override val voiceCuesEnabled: Flow<Boolean> = voiceCuesFlow
        override val soundCuesEnabled: Flow<Boolean> = soundCuesFlow
        override val vibrationEnabled: Flow<Boolean> = vibrationFlow
        override val audioDuckingEnabled: Flow<Boolean> = audioDuckingFlow
        override val countdownVoiceCuesEnabled: Flow<Boolean> = countdownVoiceCuesFlow
        override val themeMode: Flow<ThemeMode> = themeModeFlow
        override val onboardingComplete: Flow<Boolean> = onboardingFlow
        override val progressiveWeeksExtension: Flow<Int> = progressiveWeeksFlow
        override val longRunDay: Flow<DayOfWeek> = longRunDayFlow

        override suspend fun setProgramStartDate(date: LocalDate) {
            startDateFlow.value = date
        }

        override suspend fun clearProgramStartDate() {
            startDateFlow.value = null
        }

        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {
            preferredDaysFlow.value = days
        }

        override suspend fun setDistanceUnit(unit: DistanceUnit) {
            distanceUnitFlow.value = unit
        }

        override suspend fun setVoiceCuesEnabled(enabled: Boolean) {
            voiceCuesFlow.value = enabled
        }

        override suspend fun setSoundCuesEnabled(enabled: Boolean) {
            soundCuesFlow.value = enabled
        }

        override suspend fun setVibrationEnabled(enabled: Boolean) {
            vibrationFlow.value = enabled
        }

        override suspend fun setAudioDuckingEnabled(enabled: Boolean) {
            audioDuckingFlow.value = enabled
        }

        override suspend fun setCountdownVoiceCuesEnabled(enabled: Boolean) {
            countdownVoiceCuesFlow.value = enabled
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            themeModeFlow.value = mode
        }

        override suspend fun setOnboardingComplete(complete: Boolean) {
            onboardingFlow.value = complete
        }

        override suspend fun setProgressiveWeeksExtension(weeks: Int) {
            progressiveWeeksFlow.value = weeks
        }

        override suspend fun setLongRunDay(day: DayOfWeek) {
            longRunDayFlow.value = day
        }
    }
}
