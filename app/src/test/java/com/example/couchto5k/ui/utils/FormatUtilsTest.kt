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
}
