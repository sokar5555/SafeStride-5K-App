package com.example.couchto5k.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.couchto5k.data.repository.DefaultTrainingPlanRepository
import com.example.couchto5k.domain.model.TrainingWorkout

@Composable
fun WorkoutPreviewScreen(
    workoutId: Long,
    onStartClick: () -> Unit,
    onBack: () -> Unit
) {
    var workout by remember { mutableStateOf<TrainingWorkout?>(null) }
    val repository = remember { DefaultTrainingPlanRepository() }

    LaunchedEffect(workoutId) {
        workout = repository.getWorkoutById(workoutId)
    }

    val currentWorkout = workout
    if (currentWorkout == null) {
        Text("Loading...", modifier = Modifier.padding(16.dp))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = currentWorkout.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Estimated Duration: ${currentWorkout.totalPlannedDurationSeconds / 60} min")
        Text("Total Running Time: ${currentWorkout.totalPlannedRunningSeconds / 60} min")
        Text("Longest Run Interval: ${currentWorkout.longestPlannedRunIntervalSeconds / 60} min")

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Interval Breakdown", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(currentWorkout.intervals) { interval ->
                val typeStr = interval.type.name
                val min = interval.durationSeconds / 60
                val sec = interval.durationSeconds % 60
                val secStr = if (sec < 10) "0$sec" else "$sec"
                Text("${interval.sequence + 1}. $typeStr: $min:$secStr")
            }
        }

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("START WORKOUT")
        }
    }
}
