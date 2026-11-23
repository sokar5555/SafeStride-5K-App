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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupJsonCodecTest {

    @Test
    fun encodeAndDecode_fullRoundTrip_preservesAllData() {
        val original = AppBackupData(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            appName = BackupJsonCodec.APP_NAME,
            exportedAt = Instant.parse("2026-11-13T18:00:00Z"),
            settings = BackupSettings(
                distanceUnit = DistanceUnit.KILOMETERS,
                programStartDate = LocalDate.of(2026, 10, 1),
                preferredWorkoutDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
                progressiveWeeksExtension = 4,
                longRunDay = DayOfWeek.SUNDAY,
                voiceCuesEnabled = false,
                soundCuesEnabled = true,
                vibrationEnabled = false,
                audioDuckingEnabled = false,
                countdownVoiceCuesEnabled = false,
                themeMode = ThemeMode.DARK,
                onboardingComplete = true,
            ),
            completedWorkouts = listOf(
                BackupCompletedWorkout(
                    id = 42L,
                    trainingWorkoutId = 3L,
                    scheduledWorkoutId = 7L,
                    title = "Week 1 • Workout 3",
                    startedAt = Instant.parse("2026-10-05T12:00:00Z"),
                    completedAt = Instant.parse("2026-10-05T12:30:00Z"),
                    durationSeconds = 1800,
                    runningSeconds = 480,
                    walkingSeconds = 1320,
                    distanceMeters = 3250.5,
                    averagePaceSecondsPerKm = 553.76,
                    longestCompletedRunSeconds = 60,
                    status = WorkoutEngineStatus.COMPLETED,
                    intervals = listOf(
                        BackupCompletedInterval(
                            sequence = 0,
                            intervalType = IntervalType.WARMUP,
                            plannedDurationSeconds = 300,
                            actualDurationSeconds = 300,
                            distanceMeters = 410.2,
                            completed = true,
                        ),
                        BackupCompletedInterval(
                            sequence = 1,
                            intervalType = IntervalType.RUN,
                            plannedDurationSeconds = 60,
                            actualDurationSeconds = 60,
                            distanceMeters = 185.0,
                            completed = true,
                        ),
                    ),
                    routePoints = listOf(
                        BackupLocationPoint(
                            sequence = 0,
                            timestamp = 1791201600000L,
                            latitude = 40.7128,
                            longitude = -74.0060,
                            altitude = 12.5,
                            accuracyMeters = 4.2f,
                        ),
                        BackupLocationPoint(
                            sequence = 1,
                            timestamp = 1791201605000L,
                            latitude = 40.7131,
                            longitude = -74.0057,
                            altitude = null,
                            accuracyMeters = 5.0f,
                        ),
                    ),
                ),
            ),
            customWorkouts = listOf(
                BackupCustomWorkout(
                    id = 1001L,
                    title = "Tempo Ladder",
                    createdAt = 1791200000000L,
                    intervals = listOf(
                        BackupCustomInterval(0, IntervalType.WARMUP, 300),
                        BackupCustomInterval(1, IntervalType.RUN, 600),
                        BackupCustomInterval(2, IntervalType.COOLDOWN, 300),
                    ),
                ),
            ),
            scheduledWorkouts = listOf(
                BackupScheduledWorkout(
                    id = 7L,
                    trainingWorkoutId = 3L,
                    scheduledDate = LocalDate.of(2026, 10, 5),
                    status = "COMPLETED",
                ),
            ),
        )

        val encoded = BackupJsonCodec.encode(original)
        val decoded = BackupJsonCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun encodeAndDecode_emptyHistoryAndNullProgramStartDate_succeeds() {
        val emptyBackup = AppBackupData(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            appName = BackupJsonCodec.APP_NAME,
            exportedAt = Instant.parse("2026-11-13T18:00:00Z"),
            settings = BackupSettings(
                distanceUnit = DistanceUnit.MILES,
                programStartDate = null,
                preferredWorkoutDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
                progressiveWeeksExtension = 0,
                longRunDay = DayOfWeek.SATURDAY,
                voiceCuesEnabled = true,
                soundCuesEnabled = true,
                vibrationEnabled = true,
                audioDuckingEnabled = true,
                countdownVoiceCuesEnabled = true,
                themeMode = ThemeMode.SYSTEM,
                onboardingComplete = false,
            ),
            completedWorkouts = emptyList(),
            customWorkouts = emptyList(),
            scheduledWorkouts = emptyList(),
        )

        val encoded = BackupJsonCodec.encode(emptyBackup)
        val decoded = BackupJsonCodec.decode(encoded)

        assertEquals(emptyBackup, decoded)
    }

    @Test
    fun decode_rejectsBlankCorruptWrongAppOrUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode("{ not valid json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode(
                """
                {
                  "appName": "SomeOtherApp",
                  "schemaVersion": 1
                }
                """.trimIndent(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode(
                """
                {
                  "appName": "SafeStride5K",
                  "schemaVersion": 99
                }
                """.trimIndent(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode(
                """
                {
                  "appName": "SafeStride5K"
                }
                """.trimIndent(),
            )
        }
    }
}
