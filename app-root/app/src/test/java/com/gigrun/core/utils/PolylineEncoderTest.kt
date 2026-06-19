package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

class PolylineEncoderTest {

    @Test
    fun encodeAndDecode_returnsSameCoordinates() {
        val originalPoints = listOf(
            Pair(17.38504, 78.48667),
            Pair(17.38921, 78.49012),
            Pair(17.39210, 78.49530)
        )

        val encoded = PolylineEncoder.encode(originalPoints)
        assertFalse(encoded.isEmpty())

        val decodedPoints = PolylineEncoder.decode(encoded)
        assertEquals(originalPoints.size, decodedPoints.size)

        for (i in originalPoints.indices) {
            // Google Polyline uses 5 decimal places, so tolerance of 1e-5
            assertEquals(originalPoints[i].first, decodedPoints[i].first, 1e-5)
            assertEquals(originalPoints[i].second, decodedPoints[i].second, 1e-5)
        }
    }

    @Test
    fun encode_emptyList_returnsEmptyString() {
        val encoded = PolylineEncoder.encode(emptyList())
        assertTrue(encoded.isEmpty())
    }

    @Test
    fun decode_emptyString_returnsEmptyList() {
        val decoded = PolylineEncoder.decode("")
        assertTrue(decoded.isEmpty())
    }
}
