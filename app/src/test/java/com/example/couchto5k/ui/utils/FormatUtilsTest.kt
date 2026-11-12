package com.example.couchto5k.ui.utils

import com.example.couchto5k.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatDistance() {
        assertEquals("1.00 mi", formatDistance(1609.344, DistanceUnit.MILES))
        assertEquals("2.00 mi", formatDistance(3218.688, DistanceUnit.MILES))

        assertEquals("1.00 km", formatDistance(1000.0, DistanceUnit.KILOMETERS))
        assertEquals("1.50 km", formatDistance(1500.0, DistanceUnit.KILOMETERS))
    }

    @Test
    fun testFormatPace() {
        // 1 mile in 8:30 (510 seconds)
        assertEquals("8:30 /mi", formatPace(1609.344, 510, DistanceUnit.MILES))

        // 1 km in 5:17 (317 seconds)
        assertEquals("5:17 /km", formatPace(1000.0, 317, DistanceUnit.KILOMETERS))

        // Too little distance
        assertEquals("--:--", formatPace(0.001, 100, DistanceUnit.MILES))

        // Zero duration
        assertEquals("--:--", formatPace(1609.344, 0, DistanceUnit.MILES))
    }

    @Test
    fun testFormatPace_capsAt300MinutesPerMile() {
        // Exactly 300:00 /mi (18000 seconds for 1 mile) is allowed
        assertEquals("300:00 /mi", formatPace(1609.344, 300 * 60, DistanceUnit.MILES))

        // Exceeding 300 min/mi (e.g., 301 min/mi or stationary drift) returns "--:--"
        assertEquals("--:--", formatPace(1609.344, 301 * 60, DistanceUnit.MILES))
        assertEquals("--:--", formatPace(1.0, 600, DistanceUnit.MILES))
        assertEquals("--:--", formatPace(1.0, 600, DistanceUnit.KILOMETERS))

        // Also test formatPaceFromSecondsPerKm cap
        assertEquals("8:02 /mi", formatPaceFromSecondsPerKm(300.0, DistanceUnit.MILES))
        assertEquals("5:00 /km", formatPaceFromSecondsPerKm(300.0, DistanceUnit.KILOMETERS))
        assertEquals("--:--", formatPaceFromSecondsPerKm(12000.0, DistanceUnit.MILES))
        assertEquals("--:--", formatPaceFromSecondsPerKm(12000.0, DistanceUnit.KILOMETERS))
    }
}
