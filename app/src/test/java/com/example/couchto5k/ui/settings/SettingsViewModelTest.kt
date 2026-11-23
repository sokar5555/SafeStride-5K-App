package com.example.couchto5k.ui.settings

import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.BackupSettings
import com.example.couchto5k.domain.model.BackupSummary
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.repository.BackupRepository
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.engine.ActiveWorkoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeHistoryRepository: FakeWorkoutHistoryRepository
    private lateinit var fakeBackupRepository: FakeBackupRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
        fakeHistoryRepository = FakeWorkoutHistoryRepository()
        fakeBackupRepository = FakeBackupRepository()
        viewModel = SettingsViewModel(
            settingsRepository = fakeSettingsRepository,
            workoutHistoryRepository = fakeHistoryRepository,
            backupRepository = fakeBackupRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling distance units updates Miles and Kilometers in DataStore`() = runTest {
        assertEquals(DistanceUnit.MILES, viewModel.distanceUnit.value)

        viewModel.setDistanceUnit(DistanceUnit.KILOMETERS)
        assertEquals(DistanceUnit.KILOMETERS, viewModel.distanceUnit.value)
        assertEquals(DistanceUnit.KILOMETERS, fakeSettingsRepository.distanceUnitFlow.value)

        viewModel.toggleDistanceUnit()
        assertEquals(DistanceUnit.MILES, viewModel.distanceUnit.value)
        assertEquals(DistanceUnit.MILES, fakeSettingsRepository.distanceUnitFlow.value)
    }

    @Test
    fun `updating audio and haptic cue preferences persists in DataStore`() = runTest {
        assertTrue(viewModel.voiceCuesEnabled.value)
        assertTrue(viewModel.countdownVoiceCuesEnabled.value)
        assertTrue(viewModel.audioDuckingEnabled.value)
        assertTrue(viewModel.soundCuesEnabled.value)
        assertTrue(viewModel.vibrationEnabled.value)

        viewModel.setVoiceCuesEnabled(false)
        assertFalse(viewModel.voiceCuesEnabled.value)
        assertFalse(fakeSettingsRepository.voiceCuesFlow.value)

        viewModel.setCountdownVoiceCuesEnabled(false)
        assertFalse(viewModel.countdownVoiceCuesEnabled.value)
        assertFalse(fakeSettingsRepository.countdownVoiceCuesFlow.value)

        viewModel.setAudioDuckingEnabled(false)
        assertFalse(viewModel.audioDuckingEnabled.value)
        assertFalse(fakeSettingsRepository.audioDuckingFlow.value)

        viewModel.setSoundCuesEnabled(false)
        assertFalse(viewModel.soundCuesEnabled.value)
        assertFalse(fakeSettingsRepository.soundCuesFlow.value)

        viewModel.setVibrationEnabled(false)
        assertFalse(viewModel.vibrationEnabled.value)
        assertFalse(fakeSettingsRepository.vibrationFlow.value)

        viewModel.setVoiceCuesEnabled(true)
        assertTrue(viewModel.voiceCuesEnabled.value)
        assertTrue(fakeSettingsRepository.voiceCuesFlow.value)
    }

    @Test
    fun `theme mode defaults to LIGHT and persists LIGHT DARK SYSTEM`() = runTest {
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)

        viewModel.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals(ThemeMode.DARK, fakeSettingsRepository.themeModeFlow.value)

        viewModel.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
        assertEquals(ThemeMode.SYSTEM, fakeSettingsRepository.themeModeFlow.value)
    }

    @Test
    fun `updating schedule preferences and resetting program data works`() = runTest {
        val customDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        val customDate = LocalDate.of(2026, 5, 1)

        viewModel.setPreferredWorkoutDays(customDays)
        viewModel.setProgramStartDate(customDate)

        assertEquals(customDays, viewModel.preferredWorkoutDays.value)
        assertEquals(customDate, viewModel.programStartDate.value)

        viewModel.resetProgramData()

        assertTrue(fakeHistoryRepository.historyCleared)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
            viewModel.preferredWorkoutDays.value,
        )
    }

    @Test
    fun `export and import backup transitions BackupUiState cleanly`() = runTest {
        assertEquals(BackupUiState.Idle, viewModel.backupUiState.value)

        viewModel.exportBackup("content://test/backup.json")
        assertTrue(viewModel.backupUiState.value is BackupUiState.Success)
        assertEquals("content://test/backup.json", fakeBackupRepository.lastExportUri)

        viewModel.prepareImportBackup("content://test/restore.json")
        assertTrue(viewModel.backupUiState.value is BackupUiState.PendingRestoreConfirmation)

        viewModel.confirmImportBackup()
        assertTrue(viewModel.backupUiState.value is BackupUiState.Success)
        assertTrue(fakeBackupRepository.restoreCalled)

        viewModel.clearBackupStatus()
        assertEquals(BackupUiState.Idle, viewModel.backupUiState.value)
    }

    @Test
    fun `invalid import backup sets BackupUiState Error`() = runTest {
        fakeBackupRepository.parseError = IllegalArgumentException("Corrupt JSON")

        viewModel.prepareImportBackup("content://test/bad.json")
        val state = viewModel.backupUiState.value
        assertTrue(state is BackupUiState.Error)
        assertEquals("Corrupt JSON", (state as BackupUiState.Error).message)
    }

    private class FakeSettingsRepository : SettingsRepository {
        val startDateFlow = MutableStateFlow<LocalDate?>(null)
        val preferredDaysFlow = MutableStateFlow(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        )
        val distanceUnitFlow = MutableStateFlow(DistanceUnit.MILES)
        val voiceCuesFlow = MutableStateFlow(true)
        val soundCuesFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val audioDuckingFlow = MutableStateFlow(true)
        val countdownVoiceCuesFlow = MutableStateFlow(true)
        val themeModeFlow = MutableStateFlow(ThemeMode.LIGHT)
        val onboardingCompleteFlow = MutableStateFlow(false)

        override val programStartDate: Flow<LocalDate?> = startDateFlow
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = preferredDaysFlow
        override val distanceUnit: Flow<DistanceUnit> = distanceUnitFlow
        override val voiceCuesEnabled: Flow<Boolean> = voiceCuesFlow
        override val soundCuesEnabled: Flow<Boolean> = soundCuesFlow
        override val vibrationEnabled: Flow<Boolean> = vibrationFlow
        override val audioDuckingEnabled: Flow<Boolean> = audioDuckingFlow
        override val countdownVoiceCuesEnabled: Flow<Boolean> = countdownVoiceCuesFlow
        override val themeMode: Flow<ThemeMode> = themeModeFlow
        override val onboardingComplete: Flow<Boolean> = onboardingCompleteFlow

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
            onboardingCompleteFlow.value = complete
        }
    }

    private class FakeWorkoutHistoryRepository : WorkoutHistoryRepository {
        var historyCleared = false

        override fun getHistory(): Flow<List<CompletedWorkoutDetails>> =
            MutableStateFlow(emptyList())

        override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? = null

        override suspend fun saveCompletedWorkout(
            workout: TrainingWorkout,
            state: ActiveWorkoutState,
        ) {}

        override suspend fun deleteWorkout(id: Long) {}

        override suspend fun clearAllHistory() {
            historyCleared = true
        }
    }

    private class FakeBackupRepository : BackupRepository {
        var lastExportUri: String? = null
        var restoreCalled = false
        var parseError: Throwable? = null

        private val sampleBackup = AppBackupData(
            schemaVersion = 1,
            appName = "SafeStride5K",
            exportedAt = Instant.parse("2026-11-13T18:00:00Z"),
            settings = BackupSettings(
                distanceUnit = DistanceUnit.MILES,
                programStartDate = null,
                preferredWorkoutDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
                progressiveWeeksExtension = 0,
                longRunDay = DayOfWeek.SATURDAY,
                voiceCuesEnabled = true,
                soundCuesEnabled = true,
                vibrationEnabled = true,
                audioDuckingEnabled = true,
                countdownVoiceCuesEnabled = true,
                themeMode = ThemeMode.LIGHT,
                onboardingComplete = true,
            ),
            completedWorkouts = emptyList(),
            customWorkouts = emptyList(),
            scheduledWorkouts = emptyList(),
        )

        override suspend fun exportBackupToUri(uriString: String): Result<BackupSummary> {
            lastExportUri = uriString
            return Result.success(
                BackupSummary(
                    completedWorkoutsCount = 3,
                    customWorkoutsCount = 1,
                    scheduledWorkoutsCount = 27,
                    exportedAt = Instant.parse("2026-11-13T18:00:00Z"),
                ),
            )
        }

        override suspend fun parseAndValidateBackupFromUri(uriString: String): Result<AppBackupData> {
            val error = parseError
            return if (error != null) Result.failure(error) else Result.success(sampleBackup)
        }

        override suspend fun restoreBackup(backup: AppBackupData): Result<BackupSummary> {
            restoreCalled = true
            return Result.success(backup.toSummary())
        }
    }
}
