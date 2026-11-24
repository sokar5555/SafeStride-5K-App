package com.example.couchto5k.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.graphics.Insets
import android.location.LocationManager
import android.view.View
import android.view.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.couchto5k.domain.model.CompletedWorkout
import com.example.couchto5k.domain.model.CompletedWorkoutDetails
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.domain.model.TrainingWorkout
import com.example.couchto5k.domain.model.WorkoutInterval
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.domain.repository.TrainingPlanRepository
import com.example.couchto5k.domain.repository.WorkoutHistoryRepository
import com.example.couchto5k.domain.usecase.GetStatsUseCase
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.FakeAudioHapticManager
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.ui.history.HistoryScreen
import com.example.couchto5k.ui.history.HistoryViewModel
import com.example.couchto5k.ui.home.HomeScreen
import com.example.couchto5k.ui.home.HomeViewModel
import com.example.couchto5k.ui.onboarding.OnboardingScreen
import com.example.couchto5k.ui.onboarding.OnboardingViewModel
import com.example.couchto5k.ui.stats.StatsScreen
import com.example.couchto5k.ui.stats.StatsViewModel
import com.example.couchto5k.ui.theme.SafeStride5KTheme
import com.example.couchto5k.ui.workout.ActiveWorkoutScreen
import com.example.couchto5k.ui.workout.ActiveWorkoutViewModel
import com.example.couchto5k.ui.workout.WorkoutPreviewScreen
import com.example.couchto5k.ui.workout.WorkoutPreviewViewModel
import com.example.couchto5k.ui.workout.WorkoutSelectionScreen
import com.example.couchto5k.ui.workout.WorkoutSelectionViewModel
import com.example.couchto5k.ui.workout.builder.WorkoutBuilderScreen
import com.example.couchto5k.ui.workout.builder.WorkoutBuilderViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h915dp")
class CriticalFlowsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var workoutEngine: WorkoutEngine
    private lateinit var trainingPlanRepository: FakeTrainingPlanRepository
    private lateinit var historyRepository: FakeHistoryRepository
    private lateinit var settingsRepository: FakeSettingsRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        workoutEngine = WorkoutEngine(FakeAudioHapticManager())
        trainingPlanRepository = FakeTrainingPlanRepository()
        historyRepository = FakeHistoryRepository()
        settingsRepository = FakeSettingsRepository()

        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setLocationEnabled(true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        workoutEngine.stopWorkout()
    }

    @Test
    fun launch_onboardingAndHome_renderExpectedActionsAndRecoveryBanner() {
        val onboardingViewModel = OnboardingViewModel(settingsRepository)
        var finished = false

        composeRule.setContent {
            SafeStride5KTheme(themeMode = ThemeMode.LIGHT) {
                OnboardingScreen(
                    onOnboardingFinished = { finished = true },
                    viewModel = onboardingViewModel,
                )
            }
        }

        composeRule.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").performClick()
        assertTrue(finished)
    }

    @Test
    fun homeScreen_displaysMainCardsAndActiveWorkoutRecoveryCard() {
        workoutEngine.start(trainingPlanRepository.workouts.first())
        workoutEngine.pause()
        val homeViewModel = HomeViewModel(trainingPlanRepository, workoutEngine)
        var resumedWorkoutId = -1L

        composeRule.setContent {
            SafeStride5KTheme {
                HomeScreen(
                    onBuildCustomWorkoutClick = {},
                    onSelectProgramClick = {},
                    onWorkoutPreviewClick = {},
                    onStartWorkoutClick = { resumedWorkoutId = it },
                    viewModel = homeViewModel,
                )
            }
        }

        composeRule.onNodeWithText("Open Exercise").assertIsDisplayed()
        composeRule.onNodeWithText("C25K 9-Week Plan").assertIsDisplayed()
        composeRule.onNodeWithText("Workout in Progress (Paused)").assertIsDisplayed()
        composeRule.onNodeWithText("RESUME WORKOUT").performClick()
        assertEquals(1L, resumedWorkoutId)
    }

    @Test
    fun openWorkoutAndPreview_showIntervalsStartButtonAndApproximateLocationNotice() {
        val selectionVm = WorkoutSelectionViewModel(trainingPlanRepository)
        var selectedId = -1L

        composeRule.setContent {
            SafeStride5KTheme {
                WorkoutSelectionScreen(
                    onWorkoutClick = { selectedId = it },
                    viewModel = selectionVm,
                )
            }
        }

        composeRule.onNodeWithText("Couch to 5K Program").assertIsDisplayed()
        composeRule.onNodeWithText("Week 1, Workout 1").performClick()
        assertEquals(1L, selectedId)
    }

    @Test
    fun workoutPreview_showsApproximateLocationBannerAndStartsWorkout() {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        val previewVm = WorkoutPreviewViewModel(
            repository = trainingPlanRepository,
            savedStateHandle = SavedStateHandle(mapOf("workoutId" to 1L)),
        )
        var started = false

        composeRule.setContent {
            SafeStride5KTheme {
                WorkoutPreviewScreen(
                    workoutId = 1L,
                    onStartClick = { started = true },
                    onBack = {},
                    viewModel = previewVm,
                )
            }
        }

        composeRule.onNodeWithText("Interval Breakdown").assertIsDisplayed()
        composeRule.onNodeWithText("Enable Precise").assertIsDisplayed()
        composeRule.onNodeWithText("START WORKOUT").performClick()
        assertTrue(started)
    }

    @Test
    fun activeWorkout_startPauseResumeAndCompleteFlow() {
        val activeVm = ActiveWorkoutViewModel(
            workoutEngine = workoutEngine,
            repository = trainingPlanRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            application = application,
            savedStateHandle = SavedStateHandle(mapOf("workoutId" to 1L)),
        )
        var exited = false

        composeRule.setContent {
            SafeStride5KTheme {
                ActiveWorkoutScreen(
                    onExit = { exited = true },
                    viewModel = activeVm,
                )
            }
        }

        composeRule.onNodeWithText("Interval 1 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("WARMUP").assertIsDisplayed()
        composeRule.onNodeWithText("DISTANCE").assertIsDisplayed()
        composeRule.onNodeWithText("AVG PACE").assertIsDisplayed()
        composeRule.onNodeWithText("THIS MILE PACE").assertIsDisplayed()

        // Pause -> Resume transitions
        composeRule.onNodeWithContentDescription("Pause Workout").performClick()
        workoutEngine.pause()
        composeRule.onNodeWithContentDescription("Resume Workout").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Resume Workout").performClick()
        workoutEngine.resume()
        composeRule.onNodeWithContentDescription("Pause Workout").assertIsDisplayed()

        // Complete workout -> Celebration dialog appears
        workoutEngine.skipToNextInterval()
        workoutEngine.skipToNextInterval()
        composeRule.onNodeWithText("Workout Complete! 🎉").assertIsDisplayed()
        composeRule.onNodeWithText("New Personal Record! 🏆").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Home").performClick()
        assertTrue(exited)
    }

    @Test
    fun historyScreen_displaysEmptyAndPopulatedHistoryWithDeleteConfirmation() {
        val historyVm = HistoryViewModel(historyRepository, settingsRepository)
        var clickedId = -1L

        composeRule.setContent {
            SafeStride5KTheme {
                HistoryScreen(
                    viewModel = historyVm,
                    onWorkoutClick = { clickedId = it },
                )
            }
        }

        composeRule.onNodeWithText("No Completed Workouts Yet").assertIsDisplayed()

        historyRepository.historyFlow.value = listOf(
            CompletedWorkoutDetails(
                workout = CompletedWorkout(
                    id = 42L,
                    trainingWorkoutId = 1L,
                    scheduledWorkoutId = null,
                    title = "Week 1, Workout 1",
                    startedAt = Instant.parse("2026-11-10T09:00:00Z"),
                    completedAt = Instant.parse("2026-11-10T09:30:00Z"),
                    durationSeconds = 1800,
                    runningSeconds = 480,
                    walkingSeconds = 1320,
                    distanceMeters = 3218.68,
                    averagePaceSecondsPerKm = 559.0,
                    longestCompletedRunSeconds = 60,
                    status = WorkoutEngineStatus.COMPLETED,
                ),
                intervals = emptyList(),
                routePoints = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Week 1, Workout 1").assertIsDisplayed()
        composeRule.onNodeWithText("COMPLETED").assertIsDisplayed()
        composeRule.onNodeWithText("Week 1, Workout 1").performClick()
        assertEquals(42L, clickedId)

        composeRule.onNodeWithContentDescription("Delete Workout").performClick()
        composeRule.onNodeWithText("Delete Workout").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun statsScreen_displaysEmptyAndPopulatedDashboardSections() {
        val getStatsUseCase = GetStatsUseCase(
            workoutHistoryRepository = historyRepository,
            trainingPlanRepository = trainingPlanRepository,
            settingsRepository = settingsRepository,
            todayProvider = { LocalDate.of(2026, 11, 13) },
        )
        val statsVm = StatsViewModel(getStatsUseCase, settingsRepository)

        composeRule.setContent {
            SafeStride5KTheme {
                StatsScreen(viewModel = statsVm)
            }
        }

        composeRule.onNodeWithText("No stats available yet. Complete your first workout to see your progress!").assertIsDisplayed()

        historyRepository.historyFlow.value = listOf(
            CompletedWorkoutDetails(
                workout = CompletedWorkout(
                    id = 1L,
                    trainingWorkoutId = 1L,
                    scheduledWorkoutId = null,
                    title = "Week 1, Workout 1",
                    startedAt = Instant.parse("2026-11-10T09:00:00Z"),
                    completedAt = Instant.parse("2026-11-10T09:30:00Z"),
                    durationSeconds = 1800,
                    runningSeconds = 600,
                    walkingSeconds = 1200,
                    distanceMeters = 4000.0,
                    averagePaceSecondsPerKm = 450.0,
                    longestCompletedRunSeconds = 180,
                    status = WorkoutEngineStatus.COMPLETED,
                ),
                intervals = emptyList(),
                routePoints = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Overall Stats").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Running Progression").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Current Record").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Program Progress").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 of 1 Workouts Completed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun workoutBuilder_bottomButtonsClearNavigationBarInsets() {
        val builderVm = WorkoutBuilderViewModel(trainingPlanRepository)
        var composeView: View? = null
        var navBarPx = 0

        composeRule.setContent {
            composeView = LocalView.current
            navBarPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            SafeStride5KTheme {
                WorkoutBuilderScreen(
                    onBack = {},
                    onFinish = {},
                    onSetWorkoutNow = {},
                    viewModel = builderVm,
                )
            }
        }

        composeRule.runOnIdle {
            val view = composeView!!
            val targetView = (view.parent as? View) ?: view
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                .build()
            targetView.dispatchApplyWindowInsets(insets)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("PREVIEW WORKOUT NOW").assertIsDisplayed()
        composeRule.onNodeWithText("FINISH BUILDING WORKOUT").assertIsDisplayed()

        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        val finishBounds = composeRule.onNodeWithText("FINISH BUILDING WORKOUT").getBoundsInRoot()
        // Button text/container must sit above the 48dp navigation bar inset
        assertTrue(
            "Expected FINISH BUILDING WORKOUT bottom (${finishBounds.bottom}) to clear 48dp nav bar (rootBottom=${rootBounds.bottom})",
            finishBounds.bottom <= (rootBounds.bottom - 48.dp),
        )
    }

    private class FakeTrainingPlanRepository : TrainingPlanRepository {
        val workouts = listOf(
            TrainingWorkout(
                id = 1L,
                week = 1,
                workoutNumber = 1,
                title = "Week 1, Workout 1",
                intervals = listOf(
                    WorkoutInterval(0, IntervalType.WARMUP, 300),
                    WorkoutInterval(1, IntervalType.RUN, 60),
                ),
            ),
        )

        override fun getAllWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(workouts)
        override fun getCustomWorkouts(): Flow<List<TrainingWorkout>> = MutableStateFlow(emptyList())
        override suspend fun getWorkoutById(id: Long): TrainingWorkout? = workouts.firstOrNull { it.id == id }
        override suspend fun saveCustomWorkout(title: String, intervals: List<WorkoutInterval>): Long = 0L
        override suspend fun deleteCustomWorkout(id: Long) {}
        override suspend fun clearAllCustomWorkouts() {}
    }

    private class FakeHistoryRepository : WorkoutHistoryRepository {
        val historyFlow = MutableStateFlow<List<CompletedWorkoutDetails>>(emptyList())

        override fun getHistory(): Flow<List<CompletedWorkoutDetails>> = historyFlow
        override suspend fun getWorkoutDetails(id: Long): CompletedWorkoutDetails? =
            historyFlow.value.firstOrNull { it.workout.id == id }

        override suspend fun saveCompletedWorkout(workout: TrainingWorkout, state: ActiveWorkoutState) {
            val details = CompletedWorkoutDetails(
                workout = CompletedWorkout(
                    id = (historyFlow.value.size + 1).toLong(),
                    trainingWorkoutId = workout.id,
                    scheduledWorkoutId = null,
                    title = workout.title,
                    startedAt = state.startedAt,
                    completedAt = Instant.now(),
                    durationSeconds = (state.totalElapsedMillis / 1000L).toInt(),
                    runningSeconds = workout.totalPlannedRunningSeconds,
                    walkingSeconds = workout.totalPlannedWalkingSeconds,
                    distanceMeters = state.distanceMeters,
                    averagePaceSecondsPerKm = state.averagePaceSecondsPerKm,
                    longestCompletedRunSeconds = workout.longestPlannedRunIntervalSeconds,
                    status = state.status,
                ),
                intervals = emptyList(),
                routePoints = emptyList(),
            )
            historyFlow.value = listOf(details) + historyFlow.value
        }

        override suspend fun deleteWorkout(id: Long) {
            historyFlow.value = historyFlow.value.filterNot { it.workout.id == id }
        }

        override suspend fun clearAllHistory() {
            historyFlow.value = emptyList()
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val programStartDate: Flow<LocalDate?> = MutableStateFlow(LocalDate.of(2026, 11, 9))
        override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = MutableStateFlow(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        )
        override val distanceUnit: Flow<DistanceUnit> = MutableStateFlow(DistanceUnit.MILES)
        override val onboardingComplete: Flow<Boolean> = MutableStateFlow(value = false)

        override suspend fun setProgramStartDate(date: LocalDate) {}
        override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {}
        override suspend fun setDistanceUnit(unit: DistanceUnit) {}
    }
}
