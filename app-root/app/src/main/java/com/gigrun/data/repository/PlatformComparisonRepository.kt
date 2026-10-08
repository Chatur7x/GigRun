package com.gigrun.data.repository

import com.gigrun.data.database.dao.EarningDao
import com.gigrun.data.database.dao.ExpenseDao
import com.gigrun.data.database.dao.FuelLogDao
import com.gigrun.data.database.dao.TripDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature 28 — read-only platform economics.
 *
 * Neither [com.gigrun.data.database.entities.FuelLog] nor
 * [com.gigrun.data.database.entities.Expense] carries a platform column, so
 * per-platform cost cannot be measured directly at this schema version. Fuel and
 * non-fuel expenses for the window are therefore spread across platforms in
 * proportion to each platform's completed-trip active hours. Per-platform fuel
 * tagging is deferred to Phase B (Vehicle).
 *
 * Every division is guarded: an empty window, a platform with revenue but no
 * trips, and a window with no active hours at all all resolve to zeros rather
 * than NaN/Infinity.
 */
@Singleton
class PlatformComparisonRepository @Inject constructor(
    private val earningDao: EarningDao,
    private val tripDao: TripDao,
    private val fuelLogDao: FuelLogDao,
    private val expenseDao: ExpenseDao
) {

    data class PlatformComparison(
        val platform: String,
        val revenueInr: Double,
        val activeHours: Double,
        val distanceKm: Double,
        val tripCount: Int,
        val allocatedCostInr: Double,
        val netInr: Double,
        val netPerHourInr: Double
    ) {
        /** False when earnings exist but no completed trip was recorded in range. */
        val hasTrackedHours: Boolean get() = activeHours > 0.0
    }

    /**
     * @param start inclusive lower bound, epoch millis.
     * @param end   exclusive upper bound, epoch millis.
     * @return rows sorted by [PlatformComparison.netPerHourInr] descending.
     */
    suspend fun getComparison(start: Long, end: Long): List<PlatformComparison> {
        val revenueRows = earningDao.getRevenueByPlatform(start, end)
        val activityRows = tripDao.getActivityByPlatform(start, end)
        val totalFuel = fuelLogDao.getTotalFuel(start, end) ?: 0.0
        val totalOtherCost = expenseDao.getTotalNonFuelExpenses(start, end) ?: 0.0

        val revenueByPlatform = revenueRows.associate { it.platform to (it.total ?: 0.0) }
        val activityByPlatform = activityRows.associateBy { it.platform }

        // Union of both sources so a platform with only earnings (notification
        // captured, trip never closed) still appears. Blank platforms are dropped —
        // they carry no identity and would render as an empty row.
        val allPlatforms = (revenueByPlatform.keys + activityByPlatform.keys)
            .filter { it.isNotBlank() }

        val totalActiveHours = activityRows.sumOf { it.hours }
        val totalCost = totalFuel + totalOtherCost

        return allPlatforms.map { platform ->
            val revenue = revenueByPlatform[platform] ?: 0.0
            val activity = activityByPlatform[platform]
            val hours = activity?.hours ?: 0.0
            val km = activity?.km ?: 0.0
            val trips = activity?.tripCount ?: 0

            val share = if (totalActiveHours > 0.0) hours / totalActiveHours else 0.0
            val allocated = totalCost * share
            val net = revenue - allocated
            val netPerHour = if (hours > 0.0) net / hours else 0.0

            PlatformComparison(
                platform = platform,
                revenueInr = revenue,
                activeHours = hours,
                distanceKm = km,
                tripCount = trips,
                allocatedCostInr = allocated,
                netInr = net,
                netPerHourInr = netPerHour
            )
        }.sortedByDescending { it.netPerHourInr }
    }
}