package com.gigrun.core.utils

import kotlin.math.*

/**
 * Computes great-circle distance between two GPS coordinates
 * using the Haversine formula.
 *
 * d = 2r * arcsin( sqrt( sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2) ) )
 * where r = 6371.0 km (mean Earth radius)
 */
object HaversineCalculator {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Returns distance in meters between two lat/lon pairs.
     */
    fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Returns distance in kilometers between two lat/lon pairs.
     */
    fun distanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return distanceInMeters(lat1, lon1, lat2, lon2) / 1000.0
    }

    /**
     * Checks if a coordinate is within a given radius (meters) of an anchor point.
     */
    fun isWithinRadius(
        currentLat: Double, currentLon: Double,
        anchorLat: Double, anchorLon: Double,
        radiusMeters: Double
    ): Boolean {
        return distanceInMeters(currentLat, currentLon, anchorLat, anchorLon) <= radiusMeters
    }
}
