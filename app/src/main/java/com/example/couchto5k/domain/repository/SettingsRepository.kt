package com.example.couchto5k.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate

interface SettingsRepository {
    val programStartDate: Flow<LocalDate?>
    val preferredWorkoutDays: Flow<Set<DayOfWeek>>

    suspend fun setProgramStartDate(date: LocalDate)
    suspend fun setPreferredWorkoutDays(days: Set<DayOfWeek>)
}
