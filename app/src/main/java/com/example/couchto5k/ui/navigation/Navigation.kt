package com.example.couchto5k.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.couchto5k.ui.calendar.CalendarScreen
import com.example.couchto5k.ui.history.HistoryScreen
import com.example.couchto5k.ui.home.HomeScreen
import com.example.couchto5k.ui.stats.StatsScreen
import com.example.couchto5k.ui.workout.WorkoutPreviewScreen
import com.example.couchto5k.ui.workout.WorkoutSelectionScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Filled.DateRange)
    object History : Screen("history", "History", Icons.AutoMirrored.Filled.List)
    object Stats : Screen("stats", "Stats", Icons.Filled.Star)
    object WorkoutSelection : Screen("workout_selection", "Workouts", Icons.AutoMirrored.Filled.List)
    object WorkoutPreview : Screen("workout_preview/{workoutId}", "Workout Preview", null) {
        fun createRoute(workoutId: Long) = "workout_preview/$workoutId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Calendar,
    Screen.History,
    Screen.Stats,
    Screen.WorkoutSelection
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route } || currentDestination?.route == Screen.WorkoutSelection.route

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Calendar.route) { CalendarScreen(navController) }
            composable(Screen.History.route) { HistoryScreen(navController) }
            composable(Screen.Stats.route) { StatsScreen(navController) }
            composable(Screen.WorkoutSelection.route) { WorkoutSelectionScreen(navController) }
            composable(Screen.WorkoutPreview.route) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getString("workoutId")?.toLongOrNull() ?: -1L
                WorkoutPreviewScreen(navController = navController, workoutId = workoutId)
            }
        }
    }
}
