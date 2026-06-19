package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

class HaversineCalculatorTest {

    @Test
    fun distanceInMeters_samePoint_isZero() {
        val lat = 17.38504
        val lon = 78.48667
        val distance = HaversineCalculator.distanceInMeters(lat, lon, lat, lon)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun distanceInKm_isConsistentWithMeters() {
        val lat1 = 17.3850
        val lon1 = 78.4867
        val lat2 = 17.4000
        val lon2 = 78.5000
        val distMeters = HaversineCalculator.distanceInMeters(lat1, lon1, lat2, lon2)
        val distKm = HaversineCalculator.distanceInKm(lat1, lon1, lat2, lon2)
        assertEquals(distMeters / 1000.0, distKm, 0.001)
    }

    @Test
    fun isWithinRadius_correctlyIdentifiesProximity() {
        val anchorLat = 17.3850
        val anchorLon = 78.4867
        val radius = 150.0 // 150 meters

        // Inside
        val closeLat = 17.3853
        val closeLon = 78.4869
        assertTrue(HaversineCalculator.isWithinRadius(closeLat, closeLon, anchorLat, anchorLon, radius))

        // Far outside
        val farLat = 17.4000
        val farLon = 78.5000
        assertFalse(HaversineCalculator.isWithinRadius(farLat, farLon, anchorLat, anchorLon, radius))
    }
}
