package com.example.couchto5k.data.local

import androidx.room.TypeConverter
import com.example.couchto5k.domain.model.IntervalType
import com.example.couchto5k.engine.WorkoutEngineStatus
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun fromIntervalType(value: IntervalType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toIntervalType(value: String?): IntervalType? {
        return value?.let { IntervalType.valueOf(it) }
    }

    @TypeConverter
    fun fromWorkoutEngineStatus(value: WorkoutEngineStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toWorkoutEngineStatus(value: String?): WorkoutEngineStatus? {
        return value?.let { WorkoutEngineStatus.valueOf(it) }
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? {
        return value?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }
}
