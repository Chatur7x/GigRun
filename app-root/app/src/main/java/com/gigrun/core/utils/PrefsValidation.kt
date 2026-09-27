package com.gigrun.core.utils

/**
 * Pure, unit-testable validation for every user- or root-writable setting that
 * feeds money math, geofencing, or carrier-billed SMS. Single source of truth:
 * DataStore setters AND send-time guards both call these, so a rooted prefs
 * edit that bypasses the setter still fails closed at the sink.
 */
object PrefsValidation {

    private val PREMIUM_PREFIXES = listOf("1900", "0900", "1-900", "1 900", "190-0")

    // Pure-Kotlin phone shape (no android.util.Patterns — JVM-testable):
    // optional +, then digits/spaces/dots/parens/dashes.
    private val PHONE_SHAPE = Regex("""^\+?[0-9. ()-]{7,20}$""")

    fun clampGForce(v: Double): Double =
        v.takeIf { it.isFinite() }?.coerceIn(2.5, 8.0) ?: 4.0

    fun clampSpeedLimit(v: Double): Double =
        v.takeIf { it.isFinite() }?.coerceIn(20.0, 120.0) ?: 80.0

    fun clampNonNegative(v: Double, fallback: Double = 0.0): Double =
        v.takeIf { it.isFinite() && it >= 0 } ?: fallback

    fun clampLat(v: Double): Double? =
        v.takeIf { it.isFinite() }?.coerceIn(-90.0, 90.0)

    fun clampLon(v: Double): Double? =
        v.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0)

    fun clampTheme(mode: String): String =
        if (mode == "light" || mode == "dark") mode else "system"

    fun clampGpsMode(mode: String): String =
        if (mode == "high" || mode == "balanced" || mode == "low") mode else "balanced"

    /**
     * Returns true only for plausible dialable numbers. Blocks premium-rate
     * prefixes, shortcodes, and over-long strings (SMS toll-fraud / spam).
     */
    fun isSafeContact(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 7..15) return false
        if (!PHONE_SHAPE.matches(s)) return false
        val digits = s.filter { it.isDigit() }
        if (digits.length < 7) return false
        val compact = s.replace(" ", "").replace("-", "")
        if (PREMIUM_PREFIXES.any { compact.startsWith(it) }) return false
        return true
    }

    /** Setter path: normalize, validate, dedupe, cap at 3. */
    fun cleanContacts(contacts: List<String>): List<String> =
        contacts.map { it.trim().take(20) }
            .filter { isSafeContact(it) }
            .distinct()
            .take(3)
}
