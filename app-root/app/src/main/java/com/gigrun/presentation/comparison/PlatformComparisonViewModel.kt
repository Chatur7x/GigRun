package com.gigrun.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.repository.PlatformComparisonRepository
import com.gigrun.data.repository.PlatformComparisonRepository.PlatformComparison
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class RangeOption(val label: String, val cardTitle: String) {
    THIS_WEEK("This week", "Best this week"),
    LAST_WEEK("Last week", "Best last week"),
    THIS_MONTH("This month", "Best this month")
}

sealed interface ComparisonUiState {
    data object Loading : ComparisonUiState
    data class Success(
        val rows: List<PlatformComparison>,
        val bestPlatform: String?,
        val bestNetPerHour: Double,
        val totalRevenue: Double,
        val totalNet: Double,
        val totalHours: Double,
        val rangeLabel: String
    ) : ComparisonUiState
    data class Error(val message: String) : ComparisonUiState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlatformComparisonViewModel @Inject constructor(
    private val repository: PlatformComparisonRepository
) : ViewModel() {

    private val _range = MutableStateFlow(RangeOption.THIS_WEEK)
    val range: StateFlow<RangeOption> = _range.asStateFlow()

    val uiState: StateFlow<ComparisonUiState> = _range
        .flatMapLatest { option ->
            flow {
                emit(ComparisonUiState.Loading)
                emit(buildSuccess(option))
            }
        }
        .catch { e -> emit(ComparisonUiState.Error(e.message ?: "Failed to load comparison")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ComparisonUiState.Loading
        )

    fun setRange(option: RangeOption) {
        _range.value = option
    }

    private suspend fun buildSuccess(option: RangeOption): ComparisonUiState {
        val (start, end) = rangeToMillis(option)
        val rows = repository.getComparison(start, end)
        // "Best" is only meaningful where hours were actually tracked: with revenue
        // but no completed trips every row is 0.0 and rows[0] would be an arbitrary
        // tie-break presented as a recommendation.
        val best = rows.firstOrNull()?.takeIf { it.hasTrackedHours }
        return ComparisonUiState.Success(
            rows = rows,
            bestPlatform = best?.platform,
            bestNetPerHour = best?.netPerHourInr ?: 0.0,
            totalRevenue = rows.sumOf { it.revenueInr },
            totalNet = rows.sumOf { it.netInr },
            totalHours = rows.sumOf { it.activeHours },
            rangeLabel = option.label
        )
    }

    companion object {
        /**
         * Half-open [start, end) window in epoch millis, Monday-anchored for weeks.
         * THIS_MONTH uses the calendar month, not a rolling 30-day window.
         */
        fun rangeToMillis(option: RangeOption, today: LocalDate = LocalDate.now()): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val mondayThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val (startDate, endDate) = when (option) {
                RangeOption.THIS_WEEK ->
                    mondayThisWeek to mondayThisWeek.plusWeeks(1)
                RangeOption.LAST_WEEK ->
                    mondayThisWeek.minusWeeks(1) to mondayThisWeek
                RangeOption.THIS_MONTH ->
                    today.withDayOfMonth(1) to today.withDayOfMonth(1).plusMonths(1)
            }
            return startDate.atStartOfDay(zone).toInstant().toEpochMilli() to
                endDate.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}