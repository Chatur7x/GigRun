package com.gigrun.core.utils

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
            val iLat = (lat * 1e5).toInt()
            val iLng = (lng * 1e5).toInt()

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
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
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
