package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

class GoalsCalculatorTest {

    @Test
    fun suggest_appliesGrowthAndRoundsTo50() {
        val s = GoalsCalculator.suggest(avgDaily = 800.0, growthFactor = 1.10)
        // 800 * 1.1 = 880 → rounded to 900
        assertEquals(900.0, s.daily, 0.001)
        assertEquals(900.0 * 6, s.weekly, 0.001)
        assertEquals(900.0 * 26, s.monthly, 0.001)
        assertEquals(800.0, s.basedOnAvgDaily, 0.001)
    }

    @Test
    fun suggest_floorsNewUsersTo200() {
        val s = GoalsCalculator.suggest(avgDaily = 0.0)
        // floor 200 * 1.1 = 220 → rounded to 200
        assertEquals(200.0, s.daily, 0.001)
    }

    @Test
    fun progress_computesRemainingAndTrips() {
        val p = GoalsCalculator.progress(current = 500.0, target = 800.0, avgFare = 100.0)
        assertEquals(0.625f, p.progress, 0.001f)
        assertEquals(300.0, p.remaining, 0.001)
        assertEquals(3, p.tripsNeeded)
    }

    @Test
    fun progress_zeroTarget_neverDividesByZero() {
        val p = GoalsCalculator.progress(current = 100.0, target = 0.0, avgFare = 0.0)
        assertEquals(0f, p.progress, 0.001f)
        assertEquals(0.0, p.remaining, 0.001)
        assertEquals(0, p.tripsNeeded)
    }
}
