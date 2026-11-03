package com.example.couchto5k.ui.stats

import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.ProgramProgress
import com.example.couchto5k.domain.model.RunningProgression
import com.example.couchto5k.domain.model.UserStats
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.usecase.GetStatsUseCase
import com.example.couchto5k.domain.usecase.StatsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private lateinit var getStatsUseCase: GetStatsUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: StatsViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getStatsUseCase = mock(GetStatsUseCase::class.java)
        settingsRepository = mock(SettingsRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when use case returns null, state is Empty`() = runTest {
        val statsFlow = MutableStateFlow<StatsData?>(null)
        val unitFlow = MutableStateFlow(DistanceUnit.MILES)
        `when`(getStatsUseCase.invoke()).thenReturn(statsFlow)
        `when`(settingsRepository.distanceUnit).thenReturn(unitFlow)

        viewModel = StatsViewModel(getStatsUseCase, settingsRepository)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect { }
        }

        assertEquals(StatsUiState.Empty, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when use case returns data, state is Success`() = runTest {
        val mockData = StatsData(
            userStats = UserStats(1, 1000.0, 600, 300, 300, 360.0, 600),
            runningProgression = RunningProgression(60, 60, 0, emptyList()),
            weeklyDistance = emptyList(),
            programProgress = ProgramProgress(1, 27, 1, 1f/27f)
        )
        val statsFlow = MutableStateFlow<StatsData?>(mockData)
        val unitFlow = MutableStateFlow(DistanceUnit.KILOMETERS)

        `when`(getStatsUseCase.invoke()).thenReturn(statsFlow)
        `when`(settingsRepository.distanceUnit).thenReturn(unitFlow)

        viewModel = StatsViewModel(getStatsUseCase, settingsRepository)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect { }
        }

        val state = viewModel.uiState.value
        assertTrue("State was $state", state is StatsUiState.Success)

        val successState = state as StatsUiState.Success
        assertEquals(DistanceUnit.KILOMETERS, successState.distanceUnit)
        assertEquals(1, successState.userStats.completedWorkoutsCount)
        collectJob.cancel()
    }
}
