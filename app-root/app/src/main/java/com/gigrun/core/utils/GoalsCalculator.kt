package com.gigrun.core.utils

import kotlin.math.roundToInt

object GoalsCalculator {
    data class GoalSuggestion(
        val daily: Double,
        val weekly: Double,
        val monthly: Double,
        val basedOnAvgDaily: Double,
        val growthFactor: Double
    )

    /**
     * Suggest goals from historical average with small growth factor.
     * @param avgDaily average daily earnings over last N days
     * @param growthFactor 1.1 = +10% stretch (coerced > 0)
     * @param workingDaysPerWeek 6 default; 7-day riders pass 7
     */
    fun suggest(avgDaily: Double, growthFactor: Double = 1.10, workingDaysPerWeek: Int = 6): GoalSuggestion {
        val safeGrowth = if (growthFactor.isFinite() && growthFactor > 0) growthFactor else 1.10
        val safeDays = workingDaysPerWeek.coerceIn(1, 7)
        // Floor so new users (or negative input) get reasonable defaults.
        val safe = avgDaily.takeIf { it.isFinite() && it >= 0 }?.coerceAtLeast(200.0) ?: 200.0
        val daily = (safe * safeGrowth / 50).roundToInt() * 50.0 // round to 50
        return GoalSuggestion(
            daily = daily,
            weekly = daily * safeDays,
            monthly = daily * (safeDays * 52 / 12.0),
            basedOnAvgDaily = avgDaily.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            growthFactor = safeGrowth
        )
    }

    data class GoalProgress(
        val current: Double,
        val target: Double,
        val progress: Float, // 0..1 (can exceed 1)
        val remaining: Double,
        val tripsNeeded: Int // at avg fare
    )

    fun progress(current: Double, target: Double, avgFare: Double): GoalProgress {
        val p = if (target > 0) (current / target).toFloat() else 0f
        val remaining = (target - current).coerceAtLeast(0.0)
        val trips = if (avgFare > 0) (remaining / avgFare).let { kotlin.math.ceil(it).toInt() } else 0
        return GoalProgress(current, target, p, remaining, trips)
    }
}
