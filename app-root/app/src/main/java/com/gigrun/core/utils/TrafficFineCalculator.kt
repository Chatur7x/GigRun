package com.gigrun.core.utils

/**
 * Indicative fine amounts for common Indian two-wheeler traffic violations.
 *
 * IMPORTANT NOTICE
 * These figures are indicative defaults compiled from publicly known Indian
 * traffic fine schedules (Motor Vehicles Act, 1988 and the Central Motor
 * Vehicle Rules / MoRTH notification tables). They are NOT legal advice.
 *
 * Fine amounts vary by state and by notified jurisdiction, and the schedules
 * are revised periodically. Any amount in this object MUST be verified against
 * the current state and/or Central notification in force before it is relied
 * upon or presented to a user as an authoritative figure. The app should
 * cross-check with official sources (state transport department / Parivahan /
 * official state gazette notifications) before displaying these values.
 *
 * This object is read-only and stateless.
 */
object TrafficFineCalculator {

    /** A single violation with its indicative fine amount and grouping label. */
    data class FineEntry(
        val violation: String,
        val fineInr: Int,
        val category: String
    )

    /** Indicative two-wheeler fines in INR, grouped by broad category. */
    val fines: List<FineEntry> = listOf(
        FineEntry("No helmet", 100, "Safety"),
        FineEntry("Not carrying driving licence", 500, "Documents"),
        FineEntry("Not carrying registration certificate", 500, "Documents"),
        FineEntry("Expired registration", 500, "Documents"),
        FineEntry("Signal jump", 500, "Traffic Rules"),
        FineEntry("Jumping red light", 500, "Traffic Rules"),
        FineEntry("Using mobile phone while driving", 500, "Safety"),
        FineEntry("Overspeeding", 1000, "Traffic Rules"),
        FineEntry("Dangerous driving", 1000, "Safety"),
        FineEntry("Rash driving", 1000, "Safety"),
        FineEntry("Drunk riding", 2000, "Safety"),
        FineEntry("Wrong side driving", 500, "Traffic Rules"),
        FineEntry("Not paying toll", 100, "Documents"),
        FineEntry("Parking violation", 500, "Traffic Rules"),
        FineEntry("Triple riding", 1000, "Safety"),
        FineEntry("Broken number plate", 5000, "Documents"),
        FineEntry("Illegal number plate", 5000, "Documents")
    )

    /**
     * Returns the indicative fine for an exact, case-insensitive match of [violation],
     * or `null` when the violation is not present in [fines].
     */
    fun fineFor(violation: String): Int? {
        val target = violation.trim().lowercase()
        if (target.isEmpty()) return null
        return fines.firstOrNull { it.violation.lowercase() == target }?.fineInr
    }

    /**
     * Sums the indicative fines for [violations], ignoring any entry that is not
     * recognised. An empty list yields `0`.
     */
    fun totalFor(violations: List<String>): Int {
        if (violations.isEmpty()) return 0
        var total = 0
        for (violation in violations) {
            total += fineFor(violation) ?: 0
        }
        return total
    }
}