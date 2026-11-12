package com.example.couchto5k.ui.settings

import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.TrainingWorkout
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeHistoryRepository: FakeWorkoutHistoryRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
        fakeHistoryRepository = FakeWorkoutHistoryRepository()
        viewModel = SettingsViewModel(fakeSettingsRepository, fakeHistoryRepository)
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
        assertTrue(viewModel.soundCuesEnabled.value)
        assertTrue(viewModel.vibrationEnabled.value)

        viewModel.setVoiceCuesEnabled(false)
        assertFalse(viewModel.voiceCuesEnabled.value)
        assertFalse(fakeSettingsRepository.voiceCuesFlow.value)

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

    private class FakeSettingsRepository : SettingsRepository {
        val startDateFlow = MutableStateFlow<LocalDate?>(null)
        val preferredDaysFlow = MutableStateFlow(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        )
        val distanceUnitFlow = MutableStateFlow(DistanceUnit.MILES)
        val voiceCuesFlow = MutableStateFlow(true)
        val soundCuesFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val onboardingCompleteFlow = MutableStateFlow(false)

        override val programStartDate: Flow<LocalDate?> = startDateFlow
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = preferredDaysFlow
        override val distanceUnit: Flow<DistanceUnit> = distanceUnitFlow
        override val voiceCuesEnabled: Flow<Boolean> = voiceCuesFlow
        override val soundCuesEnabled: Flow<Boolean> = soundCuesFlow
        override val vibrationEnabled: Flow<Boolean> = vibrationFlow
        override val onboardingComplete: Flow<Boolean> = onboardingCompleteFlow

        override suspend fun setProgramStartDate(date: LocalDate) {
            startDateFlow.value = date
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
}
