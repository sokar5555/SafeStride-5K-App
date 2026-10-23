package com.example.couchto5k.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocationFilterTest {

    private lateinit var filter: LocationFilter

    @Before
    fun setUp() {
        filter = LocationFilter()
    }

    @Test
    fun `first valid point returns zero distance and is accepted`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        val distance = filter.processLocation(p1)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `point with poor accuracy is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        val p2 = LocationPoint(37.7750, -122.4195, null, 50f, 2000L) // Bad accuracy
        val distance = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `stationary jitter under 1 meter is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // Extremely close point
        val p2 = LocationPoint(37.77490001, -122.41940001, null, 10f, 2000L)
        val distance = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `impossible speed jump is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // 1 km away but only 1 second later (1000 m/s)
        val p2 = LocationPoint(37.7839, -122.4194, null, 10f, 2000L)
        val distance = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `valid movement returns valid distance`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // Sensible run speed, sensible distance
        val p2 = LocationPoint(37.7750, -122.4194, null, 10f, 5000L)
        val distance = filter.processLocation(p2)
        assertTrue("Distance should be greater than 0", distance > 0.0)
    }
}