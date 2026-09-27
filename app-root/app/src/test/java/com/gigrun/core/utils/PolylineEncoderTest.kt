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

    @Test
    fun roundTrip_negativeHemisphere_matches() {
        val pts = listOf(
            Pair(-33.865143, 151.209900),
            Pair(-33.870000, 151.190000),
            Pair(0.0, -0.1278)
        )
        val dec = PolylineEncoder.decode(PolylineEncoder.encode(pts))
        assertEquals(pts.size, dec.size)
        pts.forEachIndexed { i, _ ->
            assertEquals(pts[i].first, dec[i].first, 1e-5)
            assertEquals(pts[i].second, dec[i].second, 1e-5)
        }
    }

    @Test
    fun decode_canonicalGoogleVector_matches() {
        val dec = PolylineEncoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, dec.size)
        assertEquals(38.5, dec[0].first, 1e-5)
        assertEquals(-120.2, dec[0].second, 1e-5)
        assertEquals(43.252, dec[2].first, 1e-5)
        assertEquals(-126.453, dec[2].second, 1e-5)
    }

    @Test
    fun decode_malformed_neverThrows() {
        // Truncated / hostile input returns partial-or-empty, never throws.
        PolylineEncoder.decode("???")
        PolylineEncoder.decode("~")
        PolylineEncoder.decode("a".repeat(200))
    }
}
