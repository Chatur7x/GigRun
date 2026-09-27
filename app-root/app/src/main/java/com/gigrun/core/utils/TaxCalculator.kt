package com.gigrun.core.utils

import java.util.Calendar

object TaxCalculator {
    const val GST_THRESHOLD_ANNUAL = 2_000_000.0 // 20L
    const val GST_RATE = 0.18

    data class TaxSummary(
        val annualProjected: Double,
        val gstLiable: Boolean,
        val gstProgress: Float, // 0..1 of threshold
        val quarterly: List<QuarterlyEstimate>,
        val deductibleExpenses: Double,
        val taxableIncome: Double,
        val suggestedSavingsRate: Double, // 0..1
        val suggestedMonthlySaving: Double
    )

    data class QuarterlyEstimate(
        val label: String, // e.g. "Q1 FY26 (Apr-Jun)"
        val dueDate: Long,
        val projectedIncome: Double,
        val estimatedDeduction: Double,
        val netTaxable: Double,
        val isOverdue: Boolean = false
    )

    /**
     * Project annual from current earnings run-rate.
     * @param totalEarnings earnings in observed period
     * @param daysObserved number of days of data
     * @param deductibleExpenses total deductible expenses in same period
     */
    fun calculate(
        totalEarnings: Double,
        daysObserved: Int,
        deductibleExpenses: Double
    ): TaxSummary {
        val safeEarnings = totalEarnings.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        val safeDeductible = deductibleExpenses.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        val safeDays = daysObserved.coerceAtLeast(1)
        val dailyAvg = safeEarnings / safeDays
        val annualProjected = dailyAvg * 365
        val projectedDeductible = (safeDeductible / safeDays) * 365
        val taxable = (annualProjected - projectedDeductible).coerceAtLeast(0.0)
        val gstLiable = annualProjected >= GST_THRESHOLD_ANNUAL
        val gstProgress = (annualProjected / GST_THRESHOLD_ANNUAL).toFloat().coerceIn(0f, 1f)

        // Quarterly estimates — advance tax due dates for India (15 Jun, 15 Sep, 15 Dec, 15 Mar)
        val quarterly = buildQuarterly(dailyAvg, projectedDeductible / 4)

        // Suggested savings: 10% if below threshold, 20% if above (GST buffer)
        val rate = if (gstLiable) 0.20 else 0.10
        val monthlySaving = annualProjected * rate / 12

        return TaxSummary(
            annualProjected = annualProjected,
            gstLiable = gstLiable,
            gstProgress = gstProgress,
            quarterly = quarterly,
            deductibleExpenses = projectedDeductible,
            taxableIncome = taxable,
            suggestedSavingsRate = rate,
            suggestedMonthlySaving = monthlySaving
        )
    }

    private fun buildQuarterly(dailyAvg: Double, quarterlyDeduction: Double): List<QuarterlyEstimate> {
        val labels = listOf("Q1 (Apr-Jun)", "Q2 (Jul-Sep)", "Q3 (Oct-Dec)", "Q4 (Jan-Mar)")
        val dueMonths = listOf(Calendar.JUNE to 15, Calendar.SEPTEMBER to 15, Calendar.DECEMBER to 15, Calendar.MARCH to 15)
        val daysPerQuarter = 91.25
        val qIncome = dailyAvg * daysPerQuarter
        val now = System.currentTimeMillis()
        return labels.mapIndexed { i, label ->
            val (month, day) = dueMonths[i]
            // FY year: Apr-Dec dues belong to the current calendar year when now is
            // Apr-Dec; Mar dues belong to next year. Never roll silently — flag overdue.
            val calYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val inJanMar = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.MONTH) <= Calendar.MARCH
            val fyYear = when (month) {
                Calendar.MARCH -> if (inJanMar) calYear else calYear + 1
                else -> if (inJanMar) calYear - 1 else calYear
            }
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, fyYear)
                set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val due = cal.timeInMillis
            QuarterlyEstimate(
                "$label ${fyYear % 100}",
                due, qIncome, quarterlyDeduction,
                (qIncome - quarterlyDeduction).coerceAtLeast(0.0),
                isOverdue = due < now
            )
        }
    }
}
