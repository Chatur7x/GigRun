package com.gigrun.presentation.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.preferences.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

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
    val currentFsmState: String = "IDLE"
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = androidx.room.Room.databaseBuilder(
        application, AppDatabase::class.java, "gigrun_db"
    ).fallbackToDestructiveMigration().build()
    private val prefs = UserPreferences(application)

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

            val tripCount = database.tripDao().getTripCountForDay(startOfDay, endOfDay)
            val totalDistance = database.tripDao().getTotalDistanceForDay(startOfDay, endOfDay) ?: 0.0
            val totalWaitSec = database.tripDao().getTotalWaitTimeForDay(startOfDay, endOfDay) ?: 0
            val totalEarnings = database.tripDao().getTotalEarningsForDay(startOfDay, endOfDay) ?: 0.0

            val shifts = database.shiftDao().getShiftsForDay(startOfDay, endOfDay).first()
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
            val isActive = database.shiftDao().getActiveShift() != null

            _uiState.value = DashboardUiState(
                totalEarned = totalEarnings, fuelCost = fuelCost, netEarned = netEarned,
                shiftTimeMinutes = shiftTimeMin, waitTimeMinutes = waitTimeMin,
                ridingTimeMinutes = ridingTimeMin, grossPerHour = grossPerHour,
                netPerHour = netPerHour, tripsCompleted = tripCount,
                avgEarningPerTrip = avgPerTrip, totalDistanceKm = totalDistance,
                breakEvenTarget = breakEvenTarget, isShiftActive = isActive
            )
        }
    }

    fun setFuelCost(cost: Double) {
        viewModelScope.launch {
            val activeShift = database.shiftDao().getActiveShift()
            if (activeShift != null) {
                database.shiftDao().update(activeShift.copy(fuelCostInr = cost))
                loadTodayStats()
            } else {
                // If there's no active shift, update the most recent shift of today
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                val endOfDay = startOfDay + 86_400_000L
                val shifts = database.shiftDao().getShiftsForDay(startOfDay, endOfDay).first()
                if (shifts.isNotEmpty()) {
                    database.shiftDao().update(shifts.first().copy(fuelCostInr = cost))
                    loadTodayStats()
                }
            }
        }
    }
}
