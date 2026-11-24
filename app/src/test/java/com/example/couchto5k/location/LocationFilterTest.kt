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
        val (distance, acceptedPoint) = filter.processLocation(p1)
        assertEquals(0.0, distance, 0.001)
        assertEquals(p1, acceptedPoint)
    }

    @Test
    fun `point with poor accuracy is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        val p2 = LocationPoint(37.7750, -122.4195, null, 50f, 2000L) // Bad accuracy
        val (distance, acceptedPoint) = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
        assertEquals(null, acceptedPoint)
    }

    @Test
    fun `stationary jitter under 1 meter is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // Extremely close point
        val p2 = LocationPoint(37.77490001, -122.41940001, null, 10f, 2000L)
        val (distance, acceptedPoint) = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
        assertEquals(null, acceptedPoint)
    }

    @Test
    fun `impossible speed jump is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // 1 km away but only 1 second later (1000 m/s)
        val p2 = LocationPoint(37.7839, -122.4194, null, 10f, 2000L)
        val (distance, acceptedPoint) = filter.processLocation(p2)
        assertEquals(0.0, distance, 0.001)
        assertEquals(null, acceptedPoint)
    }

    @Test
    fun `valid movement returns valid distance`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(p1)

        // Sensible run speed, sensible distance
        val p2 = LocationPoint(37.7750, -122.4194, null, 10f, 5000L)
        val (distance, acceptedPoint) = filter.processLocation(p2)
        assertTrue("Distance should be greater than 0", distance > 0.0)
        assertEquals(p2, acceptedPoint)
    }

    @Test
    fun `zero or negative accuracy is ignored`() {
        val zeroAcc = LocationPoint(37.7749, -122.4194, null, 0f, 1000L)
        val (d1, accepted1) = filter.processLocation(zeroAcc)
        assertEquals(0.0, d1, 0.001)
        assertEquals(null, accepted1)

        val negativeAcc = LocationPoint(37.7749, -122.4194, null, -5f, 2000L)
        val (d2, accepted2) = filter.processLocation(negativeAcc)
        assertEquals(0.0, d2, 0.001)
        assertEquals(null, accepted2)
    }

    @Test
    fun `stale or out-of-order timestamp is ignored`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 10f, 5000L)
        filter.processLocation(p1)

        val duplicateTs = LocationPoint(37.7751, -122.4194, null, 10f, 5000L)
        assertEquals(0.0 to null, filter.processLocation(duplicateTs))

        val olderTs = LocationPoint(37.7752, -122.4194, null, 10f, 3000L)
        assertEquals(0.0 to null, filter.processLocation(olderTs))
    }

    @Test
    fun `stationary user across multiple jitter updates accumulates zero distance`() {
        val base = LocationPoint(37.7749, -122.4194, null, 8f, 1000L)
        assertEquals(0.0 to base, filter.processLocation(base))

        val jitter1 = LocationPoint(37.774905, -122.419405, null, 8f, 4000L)
        val jitter2 = LocationPoint(37.774895, -122.419395, null, 8f, 7000L)
        assertEquals(0.0 to null, filter.processLocation(jitter1))
        assertEquals(0.0 to null, filter.processLocation(jitter2))
    }

    @Test
    fun `speed boundary accepts normal fast run and rejects impossible jump`() {
        val p1 = LocationPoint(37.7749, -122.4194, null, 5f, 1000L)
        filter.processLocation(p1)

        // ~111m over 10s = ~11.1 m/s (<= 12.0 m/s threshold) -> accepted
        val fastSprint = LocationPoint(37.7759, -122.4194, null, 5f, 11000L)
        val (sprintDist, acceptedSprint) = filter.processLocation(fastSprint)
        assertTrue(sprintDist > 100.0)
        assertEquals(fastSprint, acceptedSprint)

        // ~111m over 5s = ~22.2 m/s (> 12.0 m/s threshold) -> rejected
        val drivingJump = LocationPoint(37.7769, -122.4194, null, 5f, 16000L)
        assertEquals(0.0 to null, filter.processLocation(drivingJump))
    }

    @Test
    fun `reset clears previous workout anchor across sessions`() {
        val workoutAPoint = LocationPoint(37.7749, -122.4194, null, 10f, 1000L)
        filter.processLocation(workoutAPoint)

        // Workout B starts 50 km away 2 seconds later — without reset, rejected as impossible jump
        val workoutBPoint = LocationPoint(38.2249, -122.4194, null, 10f, 3000L)
        assertEquals(0.0 to null, filter.processLocation(workoutBPoint))

        filter.reset()
        val (distAfterReset, acceptedAfterReset) = filter.processLocation(workoutBPoint)
        assertEquals(0.0, distAfterReset, 0.001)
        assertEquals(workoutBPoint, acceptedAfterReset)
    }
}