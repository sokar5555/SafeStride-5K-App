package com.example.couchto5k.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.couchto5k.ui.calendar.CalendarScreen
import com.example.couchto5k.ui.history.HistoryScreen
import com.example.couchto5k.ui.history.WorkoutDetailScreen
import com.example.couchto5k.ui.home.HomeScreen
import com.example.couchto5k.ui.stats.StatsScreen
import com.example.couchto5k.ui.workout.ActiveWorkoutScreen
import com.example.couchto5k.ui.workout.WorkoutPreviewScreen
import com.example.couchto5k.ui.workout.WorkoutSelectionScreen
import com.example.couchto5k.ui.workout.builder.WorkoutBuilderScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                AppBottomNavigation(navController = navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
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
                    onWorkoutClick = { workoutId ->
                        navController.navigate("workout_preview/$workoutId")
                    },
                )
            }
            composable(BottomNavItem.History.route) {
                HistoryScreen(
                    onWorkoutClick = { id -> navController.navigate("workout_detail/$id") },
                )
            }
            composable(BottomNavItem.Stats.route) {
                StatsScreen()
            }
            composable(
                route = "workout_preview/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                WorkoutPreviewScreen(
                    workoutId = workoutId,
                    onStartClick = { navController.navigate("active_workout/$workoutId") },
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
                )
            }
            composable(
                route = "workout_detail/{completedWorkoutId}",
                arguments = listOf(navArgument("completedWorkoutId") { type = NavType.LongType }),
            ) {
                WorkoutDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
