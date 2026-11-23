package com.example.couchto5k.data.backup

import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.BackupCompletedInterval
import com.example.couchto5k.domain.model.BackupCompletedWorkout
import com.example.couchto5k.domain.model.BackupCustomInterval
import com.example.couchto5k.domain.model.BackupCustomWorkout
import com.example.couchto5k.domain.model.BackupLocationPoint
import com.example.couchto5k.domain.model.BackupScheduledWorkout
import com.example.couchto5k.domain.model.BackupSettings
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.engine.WorkoutEngineStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

object BackupJsonCodec {
    const val CURRENT_SCHEMA_VERSION = 1
    const val APP_NAME = "SafeStride5K"

    fun encode(data: AppBackupData): String {
        val root = JSONObject().apply {
            put("schemaVersion", data.schemaVersion)
            put("appName", data.appName)
            put("exportedAt", data.exportedAt.toString())
            put("settings", encodeSettings(data.settings))
            put(
                "completedWorkouts",
                JSONArray().apply {
                    data.completedWorkouts.forEach { put(encodeCompletedWorkout(it)) }
                },
            )
            put(
                "customWorkouts",
                JSONArray().apply {
                    data.customWorkouts.forEach { put(encodeCustomWorkout(it)) }
                },
            )
            put(
                "scheduledWorkouts",
                JSONArray().apply {
                    data.scheduledWorkouts.forEach { put(encodeScheduledWorkout(it)) }
                },
            )
        }
        return root.toString(2)
    }

    fun decode(rawJson: String): AppBackupData {
        if (rawJson.isBlank()) {
            throw IllegalArgumentException("Backup file is empty.")
        }

        return try {
            val root = JSONObject(rawJson)
            val appName = root.optString("appName", "")
            if (appName != APP_NAME) {
                throw IllegalArgumentException("Unrecognized backup file (expected $APP_NAME).")
            }

            if (!root.has("schemaVersion")) {
                throw IllegalArgumentException("Backup file is missing schemaVersion.")
            }
            val schemaVersion = root.getInt("schemaVersion")
            if (schemaVersion !in (1..CURRENT_SCHEMA_VERSION)) {
                throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion.")
            }

            val exportedAt = Instant.parse(root.getString("exportedAt"))
            val settingsObj = root.getJSONObject("settings")
            val settings = decodeSettings(settingsObj)

            val completedWorkoutsArray = root.getJSONArray("completedWorkouts")
            val completedWorkouts = buildList {
                for (i in 0 until completedWorkoutsArray.length()) {
                    add(decodeCompletedWorkout(completedWorkoutsArray.getJSONObject(i)))
                }
            }

            val customWorkoutsArray = root.getJSONArray("customWorkouts")
            val customWorkouts = buildList {
                for (i in 0 until customWorkoutsArray.length()) {
                    add(decodeCustomWorkout(customWorkoutsArray.getJSONObject(i)))
                }
            }

            val scheduledWorkoutsArray = root.getJSONArray("scheduledWorkouts")
            val scheduledWorkouts = buildList {
                for (i in 0 until scheduledWorkoutsArray.length()) {
                    add(decodeScheduledWorkout(scheduledWorkoutsArray.getJSONObject(i)))
                }
            }

            AppBackupData(
                schemaVersion = schemaVersion,
                appName = appName,
                exportedAt = exportedAt,
                settings = settings,
                completedWorkouts = completedWorkouts,
                customWorkouts = customWorkouts,
                scheduledWorkouts = scheduledWorkouts,
            )
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid SafeStride 5K JSON backup: ${e.message}", e)
        }
    }

    private fun encodeSettings(settings: BackupSettings): JSONObject = JSONObject().apply {
        put("distanceUnit", settings.distanceUnit.name)
        put("programStartDate", settings.programStartDate?.toString() ?: JSONObject.NULL)
        put(
            "preferredWorkoutDays",
            JSONArray().apply {
                settings.preferredWorkoutDays.sortedBy { it.value }.forEach { put(it.name) }
            },
        )
        put("progressiveWeeksExtension", settings.progressiveWeeksExtension)
        put("longRunDay", settings.longRunDay.name)
        put("voiceCuesEnabled", settings.voiceCuesEnabled)
        put("soundCuesEnabled", settings.soundCuesEnabled)
        put("vibrationEnabled", settings.vibrationEnabled)
        put("audioDuckingEnabled", settings.audioDuckingEnabled)
        put("countdownVoiceCuesEnabled", settings.countdownVoiceCuesEnabled)
        put("themeMode", settings.themeMode.name)
        put("onboardingComplete", settings.onboardingComplete)
    }

    private fun decodeSettings(obj: JSONObject): BackupSettings {
        val daysArray = obj.getJSONArray("preferredWorkoutDays")
        val preferredDays = buildSet {
            for (i in 0 until daysArray.length()) {
                add(DayOfWeek.valueOf(daysArray.getString(i)))
            }
        }
        val startDate = if (obj.isNull("programStartDate")) {
            null
        } else {
            LocalDate.parse(obj.getString("programStartDate"))
        }

        return BackupSettings(
            distanceUnit = DistanceUnit.valueOf(obj.getString("distanceUnit")),
            programStartDate = startDate,
            preferredWorkoutDays = preferredDays,
            progressiveWeeksExtension = obj.optInt("progressiveWeeksExtension", 0).coerceAtLeast(0),
            longRunDay = obj.optString("longRunDay", DayOfWeek.SATURDAY.name).let { DayOfWeek.valueOf(it) },
            voiceCuesEnabled = obj.optBoolean("voiceCuesEnabled", true),
            soundCuesEnabled = obj.optBoolean("soundCuesEnabled", true),
            vibrationEnabled = obj.optBoolean("vibrationEnabled", true),
            audioDuckingEnabled = obj.optBoolean("audioDuckingEnabled", true),
            countdownVoiceCuesEnabled = obj.optBoolean("countdownVoiceCuesEnabled", true),
            themeMode = obj.optString("themeMode", ThemeMode.LIGHT.name).let { ThemeMode.valueOf(it) },
            onboardingComplete = obj.optBoolean("onboardingComplete", true),
        )
    }

    private fun encodeCompletedWorkout(workout: BackupCompletedWorkout): JSONObject = JSONObject().apply {
        put("id", workout.id)
        put("trainingWorkoutId", workout.trainingWorkoutId)
        put("scheduledWorkoutId", workout.scheduledWorkoutId ?: JSONObject.NULL)
        put("title", workout.title)
        put("startedAt", workout.startedAt.toString())
        put("completedAt", workout.completedAt?.toString() ?: JSONObject.NULL)
        put("durationSeconds", workout.durationSeconds)
        put("runningSeconds", workout.runningSeconds)
        put("walkingSeconds", workout.walkingSeconds)
        put("distanceMeters", workout.distanceMeters)
        put("averagePaceSecondsPerKm", workout.averagePaceSecondsPerKm)
        put("longestCompletedRunSeconds", workout.longestCompletedRunSeconds)
        put("status", workout.status.name)
        put(
            "intervals",
            JSONArray().apply {
                workout.intervals.forEach { interval ->
                    put(
                        JSONObject().apply {
                            put("sequence", interval.sequence)
                            put("intervalType", interval.intervalType.name)
                            put("plannedDurationSeconds", interval.plannedDurationSeconds)
                            put("actualDurationSeconds", interval.actualDurationSeconds)
                            put("distanceMeters", interval.distanceMeters)
                            put("completed", interval.completed)
                        },
                    )
                }
            },
        )
        put(
            "routePoints",
            JSONArray().apply {
                workout.routePoints.forEach { point ->
                    put(
                        JSONObject().apply {
                            put("sequence", point.sequence)
                            put("timestamp", point.timestamp)
                            put("latitude", point.latitude)
                            put("longitude", point.longitude)
                            put("altitude", point.altitude ?: JSONObject.NULL)
                            put("accuracyMeters", point.accuracyMeters.toDouble())
                        },
                    )
                }
            },
        )
    }

    private fun decodeCompletedWorkout(obj: JSONObject): BackupCompletedWorkout {
        val intervalsArray = obj.optJSONArray("intervals") ?: JSONArray()
        val intervals = buildList {
            for (i in 0 until intervalsArray.length()) {
                val intervalObj = intervalsArray.getJSONObject(i)
                add(
                    BackupCompletedInterval(
                        sequence = intervalObj.getInt("sequence"),
                        intervalType = IntervalType.valueOf(intervalObj.getString("intervalType")),
                        plannedDurationSeconds = intervalObj.getInt("plannedDurationSeconds"),
                        actualDurationSeconds = intervalObj.getInt("actualDurationSeconds"),
                        distanceMeters = intervalObj.optDouble("distanceMeters", 0.0),
                        completed = intervalObj.getBoolean("completed"),
                    ),
                )
            }
        }

        val routePointsArray = obj.optJSONArray("routePoints") ?: JSONArray()
        val routePoints = buildList {
            for (i in 0 until routePointsArray.length()) {
                val pointObj = routePointsArray.getJSONObject(i)
                val altitude = if (pointObj.isNull("altitude")) null else pointObj.getDouble("altitude")
                add(
                    BackupLocationPoint(
                        sequence = pointObj.getInt("sequence"),
                        timestamp = pointObj.getLong("timestamp"),
                        latitude = pointObj.getDouble("latitude"),
                        longitude = pointObj.getDouble("longitude"),
                        altitude = altitude,
                        accuracyMeters = pointObj.getDouble("accuracyMeters").toFloat(),
                    ),
                )
            }
        }

        return BackupCompletedWorkout(
            id = obj.getLong("id"),
            trainingWorkoutId = obj.getLong("trainingWorkoutId"),
            scheduledWorkoutId = if (obj.isNull("scheduledWorkoutId")) null else obj.getLong("scheduledWorkoutId"),
            title = obj.getString("title"),
            startedAt = Instant.parse(obj.getString("startedAt")),
            completedAt = if (obj.isNull("completedAt")) null else Instant.parse(obj.getString("completedAt")),
            durationSeconds = obj.getInt("durationSeconds"),
            runningSeconds = obj.getInt("runningSeconds"),
            walkingSeconds = obj.getInt("walkingSeconds"),
            distanceMeters = obj.getDouble("distanceMeters"),
            averagePaceSecondsPerKm = obj.getDouble("averagePaceSecondsPerKm"),
            longestCompletedRunSeconds = obj.getInt("longestCompletedRunSeconds"),
            status = WorkoutEngineStatus.valueOf(obj.getString("status")),
            intervals = intervals,
            routePoints = routePoints,
        )
    }

    private fun encodeCustomWorkout(workout: BackupCustomWorkout): JSONObject = JSONObject().apply {
        put("id", workout.id)
        put("title", workout.title)
        put("createdAt", workout.createdAt)
        put(
            "intervals",
            JSONArray().apply {
                workout.intervals.forEach { interval ->
                    put(
                        JSONObject().apply {
                            put("sequence", interval.sequence)
                            put("intervalType", interval.intervalType.name)
                            put("durationSeconds", interval.durationSeconds)
                        },
                    )
                }
            },
        )
    }

    private fun decodeCustomWorkout(obj: JSONObject): BackupCustomWorkout {
        val intervalsArray = obj.optJSONArray("intervals") ?: JSONArray()
        val intervals = buildList {
            for (i in 0 until intervalsArray.length()) {
                val intervalObj = intervalsArray.getJSONObject(i)
                add(
                    BackupCustomInterval(
                        sequence = intervalObj.getInt("sequence"),
                        intervalType = IntervalType.valueOf(intervalObj.getString("intervalType")),
                        durationSeconds = intervalObj.getInt("durationSeconds"),
                    ),
                )
            }
        }

        return BackupCustomWorkout(
            id = obj.getLong("id"),
            title = obj.getString("title"),
            createdAt = obj.getLong("createdAt"),
            intervals = intervals,
        )
    }

    private fun encodeScheduledWorkout(workout: BackupScheduledWorkout): JSONObject = JSONObject().apply {
        put("id", workout.id)
        put("trainingWorkoutId", workout.trainingWorkoutId)
        put("scheduledDate", workout.scheduledDate.toString())
        put("status", workout.status)
    }

    private fun decodeScheduledWorkout(obj: JSONObject): BackupScheduledWorkout = BackupScheduledWorkout(
        id = obj.getLong("id"),
        trainingWorkoutId = obj.getLong("trainingWorkoutId"),
        scheduledDate = LocalDate.parse(obj.getString("scheduledDate")),
        status = obj.getString("status"),
    )
}
