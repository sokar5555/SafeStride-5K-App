package com.example.couchto5k.ui.history

import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.couchto5k.domain.model.CompletedInterval
import com.example.couchto5k.location.LocationPoint
import com.example.couchto5k.ui.utils.formatDistance
import com.example.couchto5k.ui.utils.formatDuration
import com.example.couchto5k.ui.utils.formatPace
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val details by viewModel.workoutDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details?.workout?.title ?: "Workout Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val currentDetails = details
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentDetails == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = "Workout details not found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val (workout, intervals) = currentDetails

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    // Summary Metric Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Distance",
                            value = formatDistance(workout.distanceMeters, distanceUnit),
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Average Pace",
                            value = formatPace(workout.distanceMeters, workout.durationSeconds, distanceUnit),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Total Time",
                            value = formatDuration(workout.durationSeconds),
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Longest Run",
                            value = formatDuration(workout.longestCompletedRunSeconds),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Run Time",
                            value = formatDuration(workout.runningSeconds),
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Walk Time",
                            value = formatDuration(workout.walkingSeconds),
                        )
                    }
                }

                item {
                    RouteMapCard(currentDetails.routePoints)
                }

                item {
                    Text("Interval Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                items(intervals) { interval ->
                    IntervalDetailItem(interval)
                }
            }
        }
    }
}

@Composable
fun RouteMapCard(routePoints: List<LocationPoint>) {
    if (routePoints.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No GPS route recorded for this workout", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    var mapError by remember { mutableStateOf(value = false) }

    if (mapError) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GPS Route Recorded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${routePoints.size} location points stored", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        val primaryColorInt = MaterialTheme.colorScheme.primary.toArgb()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    try {
                        MapView(context).apply {
                            onCreate(null)
                            onResume()
                            getMapAsync { googleMap ->
                                try {
                                    val latLngPoints = routePoints.map { (lat, lng) -> LatLng(lat, lng) }
                                    if (latLngPoints.isNotEmpty()) {
                                        googleMap.addPolyline(
                                            PolylineOptions()
                                                .addAll(latLngPoints)
                                                .color(primaryColorInt)
                                                .width(10f),
                                        )
                                        googleMap.addMarker(
                                            MarkerOptions()
                                                .position(latLngPoints.first())
                                                .title("Start")
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
                                        )
                                        val isNotSameEndpoint = (latLngPoints.last().latitude != latLngPoints.first().latitude) || (latLngPoints.last().longitude != latLngPoints.first().longitude)
                                        if ((latLngPoints.size > 1) && isNotSameEndpoint) {
                                            googleMap.addMarker(
                                                MarkerOptions()
                                                    .position(latLngPoints.last())
                                                    .title("Finish")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
                                            )
                                        }

                                        val isSinglePoint = (latLngPoints.size == 1) || latLngPoints.all {
                                            (it.latitude == latLngPoints.first().latitude) && (it.longitude == latLngPoints.first().longitude)
                                        }
                                        if (isSinglePoint) {
                                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngPoints.first(), 15f))
                                        } else {
                                            val boundsBuilder = LatLngBounds.builder()
                                            latLngPoints.forEach { boundsBuilder.include(it) }
                                            val bounds = boundsBuilder.build()
                                            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RouteMapCard", "Map setup error: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("RouteMapCard", "MapView creation failed: ${e.message}")
                        mapError = true
                        View(context)
                    }
                },
            )
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IntervalDetailItem(interval: CompletedInterval) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "${interval.sequence + 1}. ${interval.intervalType.name}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Planned: ${formatDuration(interval.plannedDurationSeconds)} | Actual: ${formatDuration(interval.actualDurationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (interval.completed) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color(0xFF4CAF50))
        } else {
            Icon(imageVector = Icons.Default.Cancel, contentDescription = "Incomplete", tint = MaterialTheme.colorScheme.error)
        }
    }
}
