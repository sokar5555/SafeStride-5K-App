package com.example.couchto5k.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.TrainingWorkout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSelectionScreen(
    onWorkoutClick: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: WorkoutSelectionViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workouts.collectAsState()

    val workoutsByWeek = workouts.groupBy { (_, week) -> week }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Couch to 5K Program", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            workoutsByWeek.forEach { (week, weekWorkouts) ->
                item(key = "week_header_$week") {
                    WeekHeader(week = week)
                }
                items(weekWorkouts, key = { (workoutId) -> workoutId }) { workout ->
                    WorkoutItem(workout = workout) { onWorkoutClick(workout.id) }
                }
            }
        }
    }
}

@Composable
fun WeekHeader(week: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text = "Week $week",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun WorkoutItem(workout: TrainingWorkout, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = workout.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Total time: ${workout.totalPlannedDurationSeconds / 60} min",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
