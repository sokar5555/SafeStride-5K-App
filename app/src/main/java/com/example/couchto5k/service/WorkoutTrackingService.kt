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
                if (state.status == WorkoutEngineStatus.RUNNING) {
                    updateNotification(state)
                    startLocationTracking()
                } else if (state.status == WorkoutEngineStatus.PAUSED) {
                    updateNotification(state)
                    stopLocationTracking()
                } else if (state.status == WorkoutEngineStatus.COMPLETED || state.status == WorkoutEngineStatus.CANCELLED) {
                    stopLocationTracking()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
            isTrackingLocation = true
        } catch (e: SecurityException) {
            Log.e("WorkoutTrackingService", "Missing location permissions", e)
        }
    }

    private fun stopLocationTracking() {
        if (!isTrackingLocation) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTrackingLocation = false
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let {
            when (it.action) {
                ACTION_START -> {
                    startForegroundService()
                }
                ACTION_PAUSE -> {
                    workoutEngine.pause()
                }
                ACTION_RESUME -> {
                    workoutEngine.resume()
                }
                ACTION_STOP -> {
                    workoutEngine.stopWorkout()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        startForeground(NOTIFICATION_ID, buildNotification(workoutEngine.workoutState.value))
    }

    private fun updateNotification(state: ActiveWorkoutState) {
        val notification = buildNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ActiveWorkoutState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val pauseResumeAction = if (state.isPaused) {
            val resumeIntent = Intent(this, WorkoutTrackingService::class.java).apply { action = ACTION_RESUME }
            val pendingResume = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Resume", pendingResume)
        } else {
            val pauseIntent = Intent(this, WorkoutTrackingService::class.java).apply { action = ACTION_PAUSE }
            val pendingPause = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pendingPause)
        }

        val stopIntent = Intent(this, WorkoutTrackingService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_delete, "Stop", pendingStop)

        // Formatting
        val remainingSeconds = (state.intervalRemainingMillis / 1000).toInt()
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        val timeRemaining = String.format(Locale.getDefault(), "%d:%02d", m, s)

        val elapsedSeconds = (state.totalElapsedMillis / 1000).toInt()
        val em = elapsedSeconds / 60
        val es = elapsedSeconds % 60
        val timeElapsed = String.format(Locale.getDefault(), "%d:%02d", em, es)

        val miles = state.distanceMeters * 0.000621371
        val distanceStr = String.format(Locale.getDefault(), "%.2f mi", miles)

        val intervalTypeName = state.currentInterval?.type?.name ?: "STARTING"

        val contentText = "$intervalTypeName • $timeRemaining remaining | $distanceStr • $timeElapsed elapsed"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("SafeStride 5K Workout")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOnlyAlertOnce(true) // prevent sound spam
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows ongoing workout timer and distance"
        }
        notificationManager.createNotificationChannel(channel)
    }
}