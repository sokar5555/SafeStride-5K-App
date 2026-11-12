package com.example.couchto5k.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.couchto5k.ui.calendar.CalendarScreen
import com.example.couchto5k.ui.history.HistoryScreen
import com.example.couchto5k.ui.history.WorkoutDetailScreen
import com.example.couchto5k.ui.home.HomeScreen
import com.example.couchto5k.ui.onboarding.OnboardingScreen
import com.example.couchto5k.ui.onboarding.OnboardingViewModel
import com.example.couchto5k.ui.settings.SettingsScreen
import com.example.couchto5k.ui.stats.StatsScreen
import com.example.couchto5k.ui.workout.ActiveWorkoutScreen
import com.example.couchto5k.ui.workout.WorkoutPreviewScreen
import com.example.couchto5k.ui.workout.WorkoutSelectionScreen
import com.example.couchto5k.ui.workout.builder.WorkoutBuilderScreen

@Composable
fun AppNavGraph(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val onboardingLoadedState by onboardingViewModel.isOnboardingCompleteLoaded.collectAsState()

    val isComplete = onboardingLoadedState
    if (isComplete == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val startDestination = remember {
        if (isComplete) BottomNavItem.Home.route else "onboarding"
    }

    // 120ms fast fade retains the visual fade transition while eliminating touch lockout delays
    val fadeSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = fadeSpec) },
        exitTransition = { fadeOut(animationSpec = fadeSpec) },
        popEnterTransition = { fadeIn(animationSpec = fadeSpec) },
        popExitTransition = { fadeOut(animationSpec = fadeSpec) },
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onOnboardingFinished = {
                    navController.navigate(BottomNavItem.Home.route) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable(BottomNavItem.Home.route) {
            HomeScreen(
                bottomBar = { AppBottomNavigation(navController = navController) },
                onBuildCustomWorkoutClick = {
                    navController.navigate("workout_builder")
                },
                onSelectProgramClick = {
                    navController.navigate("workout_selection")
                },
                onWorkoutPreviewClick = { workoutId ->
                    navController.navigate("workout_preview/$workoutId")
                },
                onStartWorkoutClick = { workoutId ->
                    navController.navigate("active_workout/$workoutId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("workout_builder") {
            WorkoutBuilderScreen(
                onBack = { navController.popBackStack() },
                onFinish = { navController.popBackStack() },
                onSetWorkoutNow = { workoutId ->
                    navController.navigate("workout_preview/$workoutId") {
                        popUpTo(BottomNavItem.Home.route)
                    }
                },
            )
        }
        composable("workout_selection") {
            WorkoutSelectionScreen(
                onWorkoutClick = { id ->
                    navController.navigate("workout_preview/$id")
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(BottomNavItem.Calendar.route) {
            CalendarScreen(
                bottomBar = { AppBottomNavigation(navController = navController) },
                onWorkoutClick = { workoutId ->
                    navController.navigate("workout_preview/$workoutId")
                },
            )
        }
        composable(BottomNavItem.History.route) {
            HistoryScreen(
                bottomBar = { AppBottomNavigation(navController = navController) },
                onWorkoutClick = { id -> navController.navigate("workout_detail/$id") },
            )
        }
        composable(BottomNavItem.Stats.route) {
            StatsScreen(
                bottomBar = { AppBottomNavigation(navController = navController) },
            )
        }
        composable(
            route = "workout_preview/{workoutId}",
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            WorkoutPreviewScreen(
                workoutId = workoutId,
                onStartClick = {
                    navController.navigate("active_workout/$workoutId") {
                        popUpTo("workout_preview/$workoutId") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "active_workout/{workoutId}",
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) {
            ActiveWorkoutScreen(
                onExit = {
                    // Pop all the way back to home when exiting an active workout
                    navController.navigate(BottomNavItem.Home.route) {
                        popUpTo(BottomNavItem.Home.route) { inclusive = true }
                    }
                },
                onViewWorkoutDetails = { completedWorkoutId ->
                    navController.navigate("workout_detail/$completedWorkoutId") {
                        popUpTo(BottomNavItem.Home.route)
                    }
                },
            )
        }
        composable(
            route = "workout_detail/{workoutId}",
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) {
            WorkoutDetailScreen(
                onBack = navController::popBackStack,
            )
        }
    }
}
