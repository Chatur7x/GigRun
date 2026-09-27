package com.gigrun.presentation.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.core.utils.GoalsCalculator
import com.gigrun.core.utils.PdfExporter
import com.gigrun.core.utils.TaxCalculator
import com.gigrun.data.database.dao.EarningsGoalDao
import com.gigrun.data.database.dao.ExpenseDao
import com.gigrun.data.database.dao.ShiftDao
import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val totalEarned: Double = 0.0,
    val fuelCost: Double = 0.0,
    val netEarned: Double = 0.0,
    val shiftTimeMinutes: Long = 0,
    val waitTimeMinutes: Long = 0,
    val ridingTimeMinutes: Long = 0,
    val grossPerHour: Double = 0.0,
    val netPerHour: Double = 0.0,
    val tripsCompleted: Int = 0,
    val avgEarningPerTrip: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val breakEvenTarget: Double = 0.0,
    val isShiftActive: Boolean = false,
    val currentFsmState: String = "IDLE",
    val ridingScore: Int = 100,
    val ridingTip: String = "No data yet",
    val speedAlertLimit: Double = 80.0,
    // Surge / tips
    val surgeTotal: Double = 0.0,
    val bonusTotal: Double = 0.0,
    val tipsTotal: Double = 0.0,
    // Goals
    val dailyGoal: Double = 800.0,
    val goalProgress: Float = 0f,
    val goalRemaining: Double = 0.0,
    val tripsToGoal: Int = 0,
    val isGoalAuto: Boolean = false,
    // Expenses
    val expensesTotal: Double = 0.0,
    val deductibleTotal: Double = 0.0,
    // Tax
    val taxSummary: TaxCalculator.TaxSummary? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shiftDao: ShiftDao,
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val goalDao: EarningsGoalDao,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init { loadTodayStats() }

    fun loadTodayStats() {
        viewModelScope.launch {
            try {
                loadTodayStatsInner()
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "loadTodayStats failed, keeping last state", e)
            }
        }
    }

    private suspend fun loadTodayStatsInner() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 86_400_000L

            // Single-round-trip aggregates — no full-entity (pathEncoded blob) load.
            val totals = tripDao.getDayTotals(startOfDay, endOfDay)
            val tripCount = totals.count
            val totalDistance = totals.distance ?: 0.0
            val totalWaitSec = totals.waitSec ?: 0
            val totalEarnings = totals.earnings ?: 0.0

            val shifts = shiftDao.getShiftsForDay(startOfDay, endOfDay).first()
            val shiftTimeMs = shifts.sumOf { shift ->
                val end = shift.endTime ?: System.currentTimeMillis()
                end - shift.startTime
            }
            val shiftTimeMin = shiftTimeMs / 60_000L
            val waitTimeMin = totalWaitSec.toLong() / 60
            val ridingTimeMin = (shiftTimeMin - waitTimeMin).coerceAtLeast(0)

            val fuelEfficiency = prefs.fuelEfficiency.first()
            val fuelPrice = prefs.fuelPrice.first()
            val fuelCost = if (fuelEfficiency != null && fuelPrice != null && fuelEfficiency > 0) {
                (totalDistance / fuelEfficiency) * fuelPrice
            } else {
                shifts.sumOf { it.fuelCostInr ?: 0.0 }
            }

            val netEarned = totalEarnings - fuelCost
            val grossPerHour = if (shiftTimeMin > 0) totalEarnings / (shiftTimeMin / 60.0) else 0.0
            val netPerHour = if (shiftTimeMin > 0) netEarned / (shiftTimeMin / 60.0) else 0.0
            val avgPerTrip = if (tripCount > 0) totalEarnings / tripCount else 0.0
            val fixedCosts = prefs.dailyFixedCosts.first()
            val breakEvenTarget = fuelCost + fixedCosts
            val isActive = shiftDao.getActiveShift() != null
            val speedLimit = prefs.speedLimit.first()

            // Surge / tips from trips of today — sums only, no blob load.
            val surgeTotals = tripDao.getSurgeTotals(startOfDay, endOfDay)
            val surgeTotal = surgeTotals.surge ?: 0.0
            val bonusTotal = surgeTotals.bonus ?: 0.0
            val tipsTotal = surgeTotals.tip ?: 0.0

            // Goals
            val goal = goalDao.getGoalSync()
            val dailyGoal = goal?.dailyTarget ?: 800.0
            val isAuto = goal?.autoCalculate ?: false
            // Auto-calc if enabled and no manual target set recently
            val effectiveGoal = if (isAuto) {
                // Use last 14 days avg
                val twoWeeksAgo = startOfDay - 14 * 86_400_000L
                val avgDaily = (tripDao.getTotalEarningsForDay(twoWeeksAgo, startOfDay) ?: 0.0) / 14.0
                GoalsCalculator.suggest(avgDaily.coerceAtLeast(300.0)).daily
            } else dailyGoal
            val gp = GoalsCalculator.progress(totalEarnings, effectiveGoal, avgPerTrip)

            // Expenses today
            val expensesTotal = expenseDao.getTotalForRange(startOfDay, endOfDay) ?: 0.0
            val deductibleTotal = expenseDao.getDeductibleTotalForRange(startOfDay, endOfDay) ?: 0.0

            // Tax projection (using last 30 days)
            val monthAgo = startOfDay - 30 * 86_400_000L
            val monthEarnings = tripDao.getTotalEarningsForDay(monthAgo, endOfDay) ?: totalEarnings
            val monthDeductible = expenseDao.getDeductibleTotalForRange(monthAgo, endOfDay) ?: 0.0
            val taxSummary = if (monthEarnings > 0) TaxCalculator.calculate(monthEarnings, 30, monthDeductible) else null

            _uiState.value = DashboardUiState(
                totalEarned = totalEarnings, fuelCost = fuelCost, netEarned = netEarned,
                shiftTimeMinutes = shiftTimeMin, waitTimeMinutes = waitTimeMin,
                ridingTimeMinutes = ridingTimeMin, grossPerHour = grossPerHour,
                netPerHour = netPerHour, tripsCompleted = tripCount,
                avgEarningPerTrip = avgPerTrip, totalDistanceKm = totalDistance,
                breakEvenTarget = breakEvenTarget, isShiftActive = isActive,
                speedAlertLimit = speedLimit,
                surgeTotal = surgeTotal, bonusTotal = bonusTotal, tipsTotal = tipsTotal,
                dailyGoal = effectiveGoal, goalProgress = gp.progress, goalRemaining = gp.remaining, tripsToGoal = gp.tripsNeeded,
                isGoalAuto = isAuto, expensesTotal = expensesTotal, deductibleTotal = deductibleTotal,
                taxSummary = taxSummary
            )
        }

    fun setFuelCost(cost: Double) {
        // NOTE: no active callers — fuel cost derives from mileage prefs in
        // loadTodayStatsInner. Kept as explicit API; fails visibly, never silently.
        viewModelScope.launch {
            val updated = try {
                val activeShift = shiftDao.getActiveShift()
                if (activeShift != null) {
                    shiftDao.update(activeShift.copy(fuelCostInr = cost))
                    true
                } else {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val startOfDay = cal.timeInMillis
                    val endOfDay = startOfDay + 86_400_000L
                    val shifts = shiftDao.getShiftsForDay(startOfDay, endOfDay).first()
                    if (shifts.isNotEmpty()) {
                        shiftDao.update(shifts.first().copy(fuelCostInr = cost))
                        true
                    } else false
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "setFuelCost failed", e)
                false
            }
            if (updated) loadTodayStats()
            else android.util.Log.w("DashboardVM", "setFuelCost: no shift today, ignored")
        }
    }

    fun generateAndShareReport(context: Context) {
        viewModelScope.launch {
            try {
                generateAndShareReportInner(context)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "report share failed", e)
            }
        }
    }

    private suspend fun generateAndShareReportInner(context: Context) {
            val state = _uiState.value
            val cal = Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            val todayStr = dateFormat.format(cal.time)

            val startOfDay = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val platformStats = tripDao.getPlatformStats(startOfDay)
            // Per-platform ₹/hr scaled from overall net rate by earnings share.
            val totalPlatEarnings = platformStats.sumOf { it.totalEarnings ?: 0.0 }.takeIf { it > 0 } ?: 1.0
            val platformBreakdown = platformStats.map { ps ->
                val platEarn = ps.totalEarnings ?: 0.0
                PdfExporter.PlatformSummary(
                    name = ps.platform.replaceFirstChar { it.uppercase() },
                    trips = ps.tripCount,
                    earnings = platEarn,
                    netPerHour = if (state.shiftTimeMinutes > 0) (state.netPerHour * platEarn / totalPlatEarnings) else 0.0,
                    avgWaitMinutes = ((ps.avgWaitTime ?: 0.0) / 60.0),
                    distanceKm = ps.totalDistance ?: 0.0
                )
            }

            val reportData = PdfExporter.ShiftReportData(
                dateRange = todayStr,
                totalTrips = state.tripsCompleted,
                totalDistanceKm = state.totalDistanceKm,
                totalShiftTimeMinutes = state.shiftTimeMinutes,
                totalRidingTimeMinutes = state.ridingTimeMinutes,
                totalWaitTimeMinutes = state.waitTimeMinutes,
                grossEarnings = state.totalEarned,
                fuelCost = state.fuelCost,
                netEarnings = state.netEarned,
                grossPerHour = state.grossPerHour,
                netPerHour = state.netPerHour,
                platformBreakdown = platformBreakdown
            )

            val file = PdfExporter.generateReport(context, reportData)
            val shareIntent = PdfExporter.shareReport(context, file)
            val chooser = Intent.createChooser(shareIntent, "Share Shift Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.e("DashboardVM", "no share target", e)
            }
    }
}
