package com.example.couchto5k.ui.onboarding

import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
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
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
        viewModel = OnboardingViewModel(fakeSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completing onboarding updates onboardingComplete preference and saves user selections`() = runTest {
        assertFalse(viewModel.onboardingComplete.value)

        val chosenDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        val chosenDate = LocalDate.of(2026, 6, 15)

        viewModel.selectDistanceUnit(DistanceUnit.KILOMETERS)
        viewModel.setWorkoutDays(chosenDays)
        viewModel.selectStartDate(chosenDate)

        var callbackInvoked = false
        viewModel.completeOnboarding {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        assertTrue(viewModel.onboardingComplete.value)
        assertTrue(fakeSettingsRepository.onboardingCompleteFlow.value)
        assertEquals(DistanceUnit.KILOMETERS, fakeSettingsRepository.distanceUnitFlow.value)
        assertEquals(chosenDays, fakeSettingsRepository.preferredDaysFlow.value)
        assertEquals(chosenDate, fakeSettingsRepository.startDateFlow.value)
    }

    @Test
    fun `wizard step navigation increments and decrements within bounds`() {
        assertEquals(0, viewModel.currentStep.value)

        viewModel.nextStep()
        assertEquals(1, viewModel.currentStep.value)

        viewModel.nextStep()
        assertEquals(2, viewModel.currentStep.value)

        // Does not exceed step 2
        viewModel.nextStep()
        assertEquals(2, viewModel.currentStep.value)

        viewModel.previousStep()
        assertEquals(1, viewModel.currentStep.value)

        viewModel.previousStep()
        assertEquals(0, viewModel.currentStep.value)

        // Does not go below step 0
        viewModel.previousStep()
        assertEquals(0, viewModel.currentStep.value)
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
}
