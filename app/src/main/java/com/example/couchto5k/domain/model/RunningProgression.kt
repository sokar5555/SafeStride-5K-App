package com.example.couchto5k.domain.model

import java.time.Instant

data class RunningProgression(
    val startingLongestRunSeconds: Int,
    val currentLongestRunSeconds: Int,
    val improvementSeconds: Int, // (current - starting)
    val recordHistory: List<LongestRunRecordPoint>,
)

data class LongestRunRecordPoint(
    val workoutId: Long,
    val workoutTitle: String,
    val completedAt: Instant,
    val longestRunSeconds: Int,
)
