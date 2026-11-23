package com.example.couchto5k.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.example.couchto5k.data.local.AppDatabase
import com.example.couchto5k.data.local.dao.CompletedWorkoutDao
import com.example.couchto5k.data.local.dao.CustomWorkoutDao
import com.example.couchto5k.data.local.dao.ScheduledWorkoutDao
import com.example.couchto5k.data.local.entity.CompletedIntervalEntity
import com.example.couchto5k.data.local.entity.CompletedWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutEntity
import com.example.couchto5k.data.local.entity.CustomWorkoutIntervalEntity
import com.example.couchto5k.data.local.entity.LocationPointEntity
import com.example.couchto5k.data.local.entity.ScheduledWorkoutEntity
import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.BackupCompletedInterval
import com.example.couchto5k.domain.model.BackupCompletedWorkout
import com.example.couchto5k.domain.model.BackupCustomInterval
import com.example.couchto5k.domain.model.BackupCustomWorkout
import com.example.couchto5k.domain.model.BackupLocationPoint
import com.example.couchto5k.domain.model.BackupScheduledWorkout
import com.example.couchto5k.domain.model.BackupSettings
import com.example.couchto5k.domain.model.BackupSummary
import com.example.couchto5k.domain.repository.BackupRepository
import com.example.couchto5k.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val completedWorkoutDao: CompletedWorkoutDao,
    private val customWorkoutDao: CustomWorkoutDao,
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val settingsRepository: SettingsRepository,
) : BackupRepository {

    override suspend fun exportBackupToUri(uriString: String): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val backupData = createBackupSnapshot()
            val encodedJson = BackupJsonCodec.encode(backupData)
            val uri = uriString.toUri()
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open destination file for writing.")
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(encodedJson)
            }
            backupData.toSummary()
        }
    }

    override suspend fun parseAndValidateBackupFromUri(uriString: String): Result<AppBackupData> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = uriString.toUri()
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected backup file.")
            val rawJson = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            BackupJsonCodec.decode(rawJson)
        }
    }

    override suspend fun restoreBackup(backup: AppBackupData): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            restoreBackupData(backup)
            backup.toSummary()
        }
    }

    internal suspend fun createBackupSnapshot(): AppBackupData {
        val settings = BackupSettings(
            distanceUnit = settingsRepository.distanceUnit.first(),
            programStartDate = settingsRepository.programStartDate.first(),
            preferredWorkoutDays = settingsRepository.preferredWorkoutDays.first(),
            progressiveWeeksExtension = settingsRepository.progressiveWeeksExtension.first(),
            longRunDay = settingsRepository.longRunDay.first(),
            voiceCuesEnabled = settingsRepository.voiceCuesEnabled.first(),
            soundCuesEnabled = settingsRepository.soundCuesEnabled.first(),
            vibrationEnabled = settingsRepository.vibrationEnabled.first(),
            audioDuckingEnabled = settingsRepository.audioDuckingEnabled.first(),
            countdownVoiceCuesEnabled = settingsRepository.countdownVoiceCuesEnabled.first(),
            themeMode = settingsRepository.themeMode.first(),
            onboardingComplete = settingsRepository.onboardingComplete.first(),
        )

        val completedWithIntervals = completedWorkoutDao.getAllCompletedWorkouts().first()
        val pointsByWorkoutId = completedWorkoutDao.getAllLocationPoints().groupBy { it.completedWorkoutId }
        val completedWorkouts = completedWithIntervals.map { (workout, intervals) ->
            BackupCompletedWorkout(
                id = workout.id,
                trainingWorkoutId = workout.trainingWorkoutId,
                scheduledWorkoutId = workout.scheduledWorkoutId,
                title = workout.title,
                startedAt = workout.startedAt,
                completedAt = workout.completedAt,
                durationSeconds = workout.durationSeconds,
                runningSeconds = workout.runningSeconds,
                walkingSeconds = workout.walkingSeconds,
                distanceMeters = workout.distanceMeters,
                averagePaceSecondsPerKm = workout.averagePaceSecondsPerKm,
                longestCompletedRunSeconds = workout.longestCompletedRunSeconds,
                status = workout.status,
                intervals = intervals.sortedBy { it.sequence }.map { interval ->
                    BackupCompletedInterval(
                        sequence = interval.sequence,
                        intervalType = interval.intervalType,
                        plannedDurationSeconds = interval.plannedDurationSeconds,
                        actualDurationSeconds = interval.actualDurationSeconds,
                        distanceMeters = interval.distanceMeters,
                        completed = interval.completed,
                    )
                },
                routePoints = (pointsByWorkoutId[workout.id].orEmpty())
                    .sortedBy { it.sequence }
                    .map { point ->
                        BackupLocationPoint(
                            sequence = point.sequence,
                            timestamp = point.timestamp,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            altitude = point.altitude,
                            accuracyMeters = point.accuracyMeters,
                        )
                    },
            )
        }

        val customWorkouts = customWorkoutDao.getAllCustomWorkouts().first().map { (customWorkout, intervals) ->
            BackupCustomWorkout(
                id = customWorkout.id,
                title = customWorkout.title,
                createdAt = customWorkout.createdAt,
                intervals = intervals.sortedBy { it.sequence }.map { interval ->
                    BackupCustomInterval(
                        sequence = interval.sequence,
                        intervalType = interval.intervalType,
                        durationSeconds = interval.durationSeconds,
                    )
                },
            )
        }

        val scheduledWorkouts = scheduledWorkoutDao.getAllScheduledWorkouts().first().map { scheduled ->
            BackupScheduledWorkout(
                id = scheduled.id,
                trainingWorkoutId = scheduled.trainingWorkoutId,
                scheduledDate = scheduled.scheduledDate,
                status = scheduled.status,
            )
        }

        return AppBackupData(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            appName = BackupJsonCodec.APP_NAME,
            exportedAt = Instant.now(),
            settings = settings,
            completedWorkouts = completedWorkouts,
            customWorkouts = customWorkouts,
            scheduledWorkouts = scheduledWorkouts,
        )
    }

    internal suspend fun restoreBackupData(backup: AppBackupData) {
        appDatabase.withTransaction {
            completedWorkoutDao.clearAllLocationPoints()
            completedWorkoutDao.clearAllCompletedIntervals()
            completedWorkoutDao.clearAllCompletedWorkouts()
            customWorkoutDao.deleteAllCustomWorkouts()
            scheduledWorkoutDao.deleteAll()

            backup.customWorkouts.forEach { customWorkout ->
                val insertedId = customWorkoutDao.insertCustomWorkout(
                    CustomWorkoutEntity(
                        id = customWorkout.id.coerceAtLeast(0L),
                        title = customWorkout.title,
                        createdAt = customWorkout.createdAt,
                    ),
                )
                val intervalEntities = customWorkout.intervals.map { interval ->
                    CustomWorkoutIntervalEntity(
                        customWorkoutId = insertedId,
                        sequence = interval.sequence,
                        intervalType = interval.intervalType,
                        durationSeconds = interval.durationSeconds,
                    )
                }
                if (intervalEntities.isNotEmpty()) {
                    customWorkoutDao.insertCustomWorkoutIntervals(intervalEntities)
                }
            }

            backup.completedWorkouts.forEach { workout ->
                val insertedWorkoutId = completedWorkoutDao.insertCompletedWorkout(
                    CompletedWorkoutEntity(
                        id = workout.id.coerceAtLeast(0L),
                        trainingWorkoutId = workout.trainingWorkoutId,
                        scheduledWorkoutId = workout.scheduledWorkoutId,
                        title = workout.title,
                        startedAt = workout.startedAt,
                        completedAt = workout.completedAt,
                        durationSeconds = workout.durationSeconds,
                        runningSeconds = workout.runningSeconds,
                        walkingSeconds = workout.walkingSeconds,
                        distanceMeters = workout.distanceMeters,
                        averagePaceSecondsPerKm = workout.averagePaceSecondsPerKm,
                        longestCompletedRunSeconds = workout.longestCompletedRunSeconds,
                        status = workout.status,
                    ),
                )

                val intervalEntities = workout.intervals.map { interval ->
                    CompletedIntervalEntity(
                        completedWorkoutId = insertedWorkoutId,
                        sequence = interval.sequence,
                        intervalType = interval.intervalType,
                        plannedDurationSeconds = interval.plannedDurationSeconds,
                        actualDurationSeconds = interval.actualDurationSeconds,
                        distanceMeters = interval.distanceMeters,
                        completed = interval.completed,
                    )
                }
                if (intervalEntities.isNotEmpty()) {
                    completedWorkoutDao.insertCompletedIntervals(intervalEntities)
                }

                val locationEntities = workout.routePoints.map { point ->
                    LocationPointEntity(
                        completedWorkoutId = insertedWorkoutId,
                        sequence = point.sequence,
                        timestamp = point.timestamp,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitude,
                        accuracyMeters = point.accuracyMeters,
                    )
                }
                if (locationEntities.isNotEmpty()) {
                    completedWorkoutDao.insertLocationPoints(locationEntities)
                }
            }

            val scheduledEntities = backup.scheduledWorkouts.map { scheduled ->
                ScheduledWorkoutEntity(
                    id = scheduled.id.coerceAtLeast(0L),
                    trainingWorkoutId = scheduled.trainingWorkoutId,
                    scheduledDate = scheduled.scheduledDate,
                    status = scheduled.status,
                )
            }
            if (scheduledEntities.isNotEmpty()) {
                scheduledWorkoutDao.insertScheduledWorkouts(scheduledEntities)
            }
        }

        val s = backup.settings
        settingsRepository.setDistanceUnit(s.distanceUnit)
        if (s.programStartDate != null) {
            settingsRepository.setProgramStartDate(s.programStartDate)
        } else {
            settingsRepository.clearProgramStartDate()
        }
        settingsRepository.setPreferredWorkoutDays(s.preferredWorkoutDays)
        settingsRepository.setProgressiveWeeksExtension(s.progressiveWeeksExtension)
        settingsRepository.setLongRunDay(s.longRunDay)
        settingsRepository.setVoiceCuesEnabled(s.voiceCuesEnabled)
        settingsRepository.setSoundCuesEnabled(s.soundCuesEnabled)
        settingsRepository.setVibrationEnabled(s.vibrationEnabled)
        settingsRepository.setAudioDuckingEnabled(s.audioDuckingEnabled)
        settingsRepository.setCountdownVoiceCuesEnabled(s.countdownVoiceCuesEnabled)
        settingsRepository.setThemeMode(s.themeMode)
        settingsRepository.setOnboardingComplete(s.onboardingComplete)
    }
}
