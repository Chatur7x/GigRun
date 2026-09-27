package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Executable PoCs for the pentest findings: every attack below ran green
 * BEFORE the fix (value asserted) and is now asserted blocked.
 */
class PrefsValidationTest {

    @Test
    fun premiumContact_rejected() {
        assertFalse(PrefsValidation.isSafeContact("1900123456"))
        assertFalse(PrefsValidation.isSafeContact("09001234567"))
        assertFalse(PrefsValidation.isSafeContact("1-900-123-4567"))
        assertFalse(PrefsValidation.isSafeContact("12345"))
        assertFalse(PrefsValidation.isSafeContact(""))
        assertFalse(PrefsValidation.isSafeContact("abcdefghij"))
    }

    @Test
    fun normalContacts_acceptedAndCleaned() {
        assertTrue(PrefsValidation.isSafeContact("+919876543210"))
        assertTrue(PrefsValidation.isSafeContact("9876543210"))
        val clean = PrefsValidation.cleanContacts(
            listOf(" 9876543210 ", "9876543210", "1900123456", "+919876543211", "x", "+919876543212", "+919876543213")
        )
        // deduped, premium dropped, invalid dropped, capped at 3
        assertEquals(3, clean.size)
        assertFalse(clean.any { it.contains("1900") })
    }

    @Test
    fun thresholds_clampedNaNProof() {
        assertEquals(2.5, PrefsValidation.clampGForce(0.01), 0.001)
        assertEquals(4.0, PrefsValidation.clampGForce(Double.NaN), 0.001)
        assertEquals(8.0, PrefsValidation.clampGForce(99.0), 0.001)
        assertEquals(80.0, PrefsValidation.clampSpeedLimit(Double.NaN), 0.001)
        assertEquals(20.0, PrefsValidation.clampSpeedLimit(1.0), 0.001)
        assertEquals(120.0, PrefsValidation.clampSpeedLimit(250.0), 0.001)
    }

    @Test
    fun geoAndModes_allowlisted() {
        assertNull(PrefsValidation.clampLat(Double.NaN))
        assertEquals(90.0, PrefsValidation.clampLat(91.0)!!, 0.001)
        assertEquals(-90.0, PrefsValidation.clampLat(-99.0)!!, 0.001)
        assertEquals(180.0, PrefsValidation.clampLon(200.0)!!, 0.001)
        assertEquals("system", PrefsValidation.clampTheme("dark-mode-evil"))
        assertEquals("dark", PrefsValidation.clampTheme("dark"))
        assertEquals("balanced", PrefsValidation.clampGpsMode("ultra"))
    }
}
