package com.gigrun.presentation.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.core.utils.PdfExporter
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
    val speedAlertLimit: Double = 80.0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shiftDao: ShiftDao,
    private val tripDao: TripDao,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init { loadTodayStats() }

    fun loadTodayStats() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 86_400_000L

            val tripCount = tripDao.getTripCountForDay(startOfDay, endOfDay)
            val totalDistance = tripDao.getTotalDistanceForDay(startOfDay, endOfDay) ?: 0.0
            val totalWaitSec = tripDao.getTotalWaitTimeForDay(startOfDay, endOfDay) ?: 0
            val totalEarnings = tripDao.getTotalEarningsForDay(startOfDay, endOfDay) ?: 0.0

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

            _uiState.value = DashboardUiState(
                totalEarned = totalEarnings, fuelCost = fuelCost, netEarned = netEarned,
                shiftTimeMinutes = shiftTimeMin, waitTimeMinutes = waitTimeMin,
                ridingTimeMinutes = ridingTimeMin, grossPerHour = grossPerHour,
                netPerHour = netPerHour, tripsCompleted = tripCount,
                avgEarningPerTrip = avgPerTrip, totalDistanceKm = totalDistance,
                breakEvenTarget = breakEvenTarget, isShiftActive = isActive,
                speedAlertLimit = speedLimit
            )
        }
    }

    fun setFuelCost(cost: Double) {
        viewModelScope.launch {
            val activeShift = shiftDao.getActiveShift()
            if (activeShift != null) {
                shiftDao.update(activeShift.copy(fuelCostInr = cost))
                loadTodayStats()
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
                    loadTodayStats()
                }
            }
        }
    }

    fun generateAndShareReport(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value
            val cal = Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            val todayStr = dateFormat.format(cal.time)

            val startOfDay = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val platformStats = tripDao.getPlatformStats(startOfDay)
            val platformBreakdown = platformStats.map { ps ->
                PdfExporter.PlatformSummary(
                    name = ps.platform.replaceFirstChar { it.uppercase() },
                    trips = ps.tripCount,
                    earnings = ps.totalEarnings ?: 0.0,
                    netPerHour = 0.0,
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
            context.startActivity(chooser)
        }
    }
}
