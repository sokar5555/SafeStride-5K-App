package com.example.couchto5k.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    override val programStartDate: Flow<LocalDate?> = dataStore.data.map { preferences ->
        preferences[startDateKey]?.let { LocalDate.ofEpochDay(it) }
    }

    override val preferredWorkoutDays: Flow<Set<DayOfWeek>> = dataStore.data.map { preferences ->
        val daysStrings = preferences[preferredDaysKey]
        if (daysStrings.isNullOrEmpty()) {
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) // Default
        } else {
            daysStrings.asSequence().map { DayOfWeek.valueOf(it) }.toSet()
        }
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
}
