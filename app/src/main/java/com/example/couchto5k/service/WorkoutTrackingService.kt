package com.example.couchto5k.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.couchto5k.MainActivity
import com.example.couchto5k.engine.ActiveWorkoutState
import com.example.couchto5k.engine.WorkoutEngine
import com.example.couchto5k.engine.WorkoutEngineStatus
import com.example.couchto5k.location.LocationFilter
import com.example.couchto5k.location.LocationPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutTrackingService : Service() {

    @Inject
    lateinit var workoutEngine: WorkoutEngine

    private lateinit var serviceScope: CoroutineScope

    private lateinit var notificationManager: NotificationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val locationFilter = LocationFilter()

    private var isTrackingLocation = false

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "workout_tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationCallback()
        createNotificationChannel()
        serviceScope = CoroutineScope(Dispatchers.Main)

        serviceScope.launch {
            workoutEngine.workoutState.collectLatest { state ->
                when (state.status) {
                    WorkoutEngineStatus.RUNNING -> {
                        updateNotification(state)
                        startLocationTracking()
                    }
                    WorkoutEngineStatus.PAUSED -> {
                        updateNotification(state)
                        stopLocationTracking()
                    }
                    WorkoutEngineStatus.COMPLETED, WorkoutEngineStatus.CANCELLED -> {
                        stopLocationTracking()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    val point = LocationPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accuracyMeters = location.accuracy,
                        timestamp = location.time,
                    )

                    val incrementalDistance = locationFilter.processLocation(point)
                    if (incrementalDistance > 0) {
                        workoutEngine.addDistance(incrementalDistance)
                    }
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (isTrackingLocation) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
            isTrackingLocation = true
        } catch (e: SecurityException) {
            Log.e("WorkoutTrackingService", "Location permission not granted: ${e.message}")
        }
    }

    private fun stopLocationTracking() {
        if (!isTrackingLocation) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTrackingLocation = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Active Workout Tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows live status and controls for active workout tracking."
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(state: ActiveWorkoutState) {
        val notification = buildNotification(state)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ActiveWorkoutState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val intervalName = state.currentInterval?.type?.name ?: "WORKOUT"
        val remainingSeconds = (state.intervalRemainingMillis / 1000).toInt()
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val formattedTime = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)

        val contentText = "$intervalName • $formattedTime remaining"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SafeStride 5K Workout")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                updateNotification(workoutEngine.workoutState.value)
            }
            ACTION_PAUSE -> workoutEngine.pause()
            ACTION_RESUME -> workoutEngine.resume()
            ACTION_STOP -> workoutEngine.stopWorkout()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent) = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        serviceScope.cancel()
    }
}
