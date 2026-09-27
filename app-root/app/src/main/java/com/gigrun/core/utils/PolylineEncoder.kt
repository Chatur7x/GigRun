package com.gigrun.core.utils

import kotlin.math.roundToInt

/**
 * Encodes and decodes lists of GPS coordinates into compact polyline strings
 * using the Google Encoded Polyline Algorithm Format.
 *
 * This allows efficient storage of GPS paths in the database.
 */
object PolylineEncoder {

    /**
     * Encodes a list of lat/lon pairs into a polyline string.
     */
    fun encode(points: List<Pair<Double, Double>>): String {
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for ((lat, lng) in points) {
            // Guard: non-finite or out-of-range GPS silently encoded to garbage.
            if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
            val iLat = (lat * 1e5).roundToInt()
            val iLng = (lng * 1e5).roundToInt()

            encodeValue(iLat - prevLat, result)
            encodeValue(iLng - prevLng, result)

            prevLat = iLat
            prevLng = iLng
        }

        return result.toString()
    }

    /**
     * Decodes a polyline string back into a list of lat/lon pairs.
     */
    fun decode(encoded: String): List<Pair<Double, Double>> {
        if (encoded.isEmpty()) return emptyList()
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                if (index >= encoded.length) return points // malformed guard
                if (shift > 32) return points // hostile run without terminator — bail, don't spin
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                if (index >= encoded.length) return points // malformed guard
                if (shift > 32) return points // hostile run without terminator — bail, don't spin
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(Pair(lat / 1e5, lng / 1e5))
        }

        return points
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            result.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        result.append((v + 63).toChar())
    }
}
