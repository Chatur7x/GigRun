package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

class TaxCalculatorTest {

    @Test
    fun calculate_belowThreshold_notGstLiable() {
        // ₹800/day * 365 ≈ ₹292k — well below ₹20L
        val s = TaxCalculator.calculate(totalEarnings = 24_000.0, daysObserved = 30, deductibleExpenses = 2_000.0)
        assertFalse(s.gstLiable)
        assertTrue(s.gstProgress in 0f..1f)
        assertEquals(4, s.quarterly.size)
        // FY semantics: overdue flag always agrees with the due date; labels carry FY year.
        val now = System.currentTimeMillis()
        s.quarterly.forEach { q ->
            assertEquals(q.dueDate < now, q.isOverdue)
            assertTrue(q.label.matches(Regex(""".*\d{2}""")))
            assertTrue(q.netTaxable >= 0.0)
        }
    }

    @Test
    fun calculate_aboveThreshold_gstLiableAndHigherSavings() {
        // ₹6k/day * 365 ≈ ₹21.9L — above ₹20L
        val s = TaxCalculator.calculate(totalEarnings = 180_000.0, daysObserved = 30, deductibleExpenses = 0.0)
        assertTrue(s.gstLiable)
        assertEquals(1f, s.gstProgress, 0.001f)
        assertEquals(0.20, s.suggestedSavingsRate, 0.001)
    }

    @Test
    fun calculate_zeroDaysObserved_neverDividesByZero() {
        val s = TaxCalculator.calculate(totalEarnings = 0.0, daysObserved = 0, deductibleExpenses = 0.0)
        assertEquals(0.0, s.annualProjected, 0.001)
        assertFalse(s.gstLiable)
    }

    @Test
    fun calculate_deductibleExceedsIncome_taxableFlooredAtZero() {
        val s = TaxCalculator.calculate(totalEarnings = 1_000.0, daysObserved = 30, deductibleExpenses = 50_000.0)
        assertEquals(0.0, s.taxableIncome, 0.001)
    }

    @Test
    fun calculate_exactThresholdBoundary_isLiable() {
        // Exactly ₹20L annualized → liable (>= threshold).
        val dailyFor20L = 2_000_000.0 / 365
        val s = TaxCalculator.calculate(totalEarnings = dailyFor20L * 30, daysObserved = 30, deductibleExpenses = 0.0)
        assertTrue(s.gstLiable)
        assertEquals(1f, s.gstProgress, 0.01f)
        assertEquals(0.20, s.suggestedSavingsRate, 0.001)
    }

    @Test
    fun calculate_negativeInputs_clampedSafe() {
        val s = TaxCalculator.calculate(totalEarnings = -500.0, daysObserved = -5, deductibleExpenses = -100.0)
        assertEquals(0.0, s.taxableIncome, 0.001)
        assertEquals(0.0, s.annualProjected, 0.001)
        assertFalse(s.gstLiable)
    }

    @Test
    fun calculate_quarterlyIncome_sumsToAnnual() {
        val s = TaxCalculator.calculate(totalEarnings = 24_000.0, daysObserved = 30, deductibleExpenses = 2_000.0)
        val qSum = s.quarterly.sumOf { it.projectedIncome }
        assertEquals(s.annualProjected, qSum, s.annualProjected * 0.01)
    }
}
