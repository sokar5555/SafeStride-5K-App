package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate

interface SettingsRepository {
    val programStartDate: Flow<LocalDate?>
    val preferredWorkoutDays: Flow<Set<DayOfWeek>>
    val distanceUnit: Flow<DistanceUnit>

    suspend fun setProgramStartDate(date: LocalDate)
    suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>)
    suspend fun setDistanceUnit(unit: DistanceUnit)
}
