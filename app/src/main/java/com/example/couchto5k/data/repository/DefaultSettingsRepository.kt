package com.example.couchto5k.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val startDateKey = longPreferencesKey("program_start_date")
    private val preferredDaysKey = stringSetPreferencesKey("preferred_workout_days")
    private val distanceUnitKey = stringPreferencesKey("distance_unit")
    private val voiceCuesKey = booleanPreferencesKey("voice_cues_enabled")
    private val soundCuesKey = booleanPreferencesKey("sound_cues_enabled")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val progressiveWeeksKey = intPreferencesKey("progressive_weeks_extension")
    private val longRunDayKey = stringPreferencesKey("long_run_day")

    override val programStartDate: Flow<LocalDate?> = dataStore.data.map { preferences ->
        preferences[startDateKey]?.let { LocalDate.ofEpochDay(it) }
    }

    override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = dataStore.data.map { preferences ->
        val daysStrings = preferences[preferredDaysKey]
        if (daysStrings.isNullOrEmpty()) {
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY) // Default
        } else {
            daysStrings.asSequence().map { DayOfWeek.valueOf(it) }.toSet()
        }
    }

    override val distanceUnit: Flow<DistanceUnit> = dataStore.data.map { preferences ->
        preferences[distanceUnitKey]?.let { DistanceUnit.valueOf(it) } ?: DistanceUnit.MILES
    }

    override val voiceCuesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[voiceCuesKey] ?: true
    }

    override val soundCuesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[soundCuesKey] ?: true
    }

    override val vibrationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[vibrationKey] ?: true
    }

    override val onboardingComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[onboardingCompleteKey] ?: false
    }

    override val progressiveWeeksExtension: Flow<Int> = dataStore.data.map { preferences ->
        preferences[progressiveWeeksKey] ?: 0
    }

    override val longRunDay: Flow<DayOfWeek> = dataStore.data.map { preferences ->
        preferences[longRunDayKey]?.let { DayOfWeek.valueOf(it) } ?: DayOfWeek.SATURDAY
    }

    override suspend fun setProgramStartDate(date: LocalDate) {
        dataStore.edit { preferences ->
            preferences[startDateKey] = date.toEpochDay()
        }
    }

    override suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>) {
        dataStore.edit { preferences ->
            preferences[preferredDaysKey] = days.asSequence().map { it.name }.toSet()
        }
    }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { preferences ->
            preferences[distanceUnitKey] = unit.name
        }
    }

    override suspend fun setVoiceCuesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[voiceCuesKey] = enabled
        }
    }

    override suspend fun setSoundCuesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[soundCuesKey] = enabled
        }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[vibrationKey] = enabled
        }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[onboardingCompleteKey] = complete
        }
    }

    override suspend fun setProgressiveWeeksExtension(weeks: Int) {
        dataStore.edit { preferences ->
            preferences[progressiveWeeksKey] = weeks.coerceAtLeast(0)
        }
    }

    override suspend fun setLongRunDay(day: DayOfWeek) {
        dataStore.edit { preferences ->
            preferences[longRunDayKey] = day.name
        }
    }
}

