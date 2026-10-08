package com.gigrun.presentation.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gigrun.data.repository.PlatformComparisonRepository.PlatformComparison
import com.gigrun.ui.components.AnimatedRupees
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.ProgressRing
import com.gigrun.ui.design.GigRunTheme
import com.gigrun.ui.design.LocalGigRunColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformComparisonScreen(
    onBack: () -> Unit = {},
    viewModel: PlatformComparisonViewModel = hiltViewModel()
) {
    val c = LocalGigRunColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Platform Comparison", fontWeight = FontWeight.Bold, color = c.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        containerColor = c.background
    ) { pad ->
        when (val current = state) {
            is ComparisonUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(pad),
                    contentAlignment = Alignment.Center
                ) {
                    ProgressRing(progress = 0f, size = 48.dp, strokeWidth = 4.dp)
                }
            }
            is ComparisonUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(pad).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(current.message, color = c.error, fontSize = 14.sp)
                }
            }
            is ComparisonUiState.Success -> {
                ComparisonContent(
                    state = current,
                    selectedRange = range,
                    onRangeSelected = viewModel::setRange,
                    modifier = Modifier.padding(pad)
                )
            }
        }
    }
}

/**
 * Stateless success body — kept separate from [PlatformComparisonScreen] so the
 * layout can be rendered in a UI test without a Hilt-provided ViewModel.
 */
@Composable
internal fun ComparisonContent(
    state: ComparisonUiState.Success,
    selectedRange: RangeOption,
    onRangeSelected: (RangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RangeOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedRange == option,
                    onClick = { onRangeSelected(option) },
                    label = { Text(option.label, fontSize = 11.sp) },
                    modifier = Modifier.testTag("range_${option.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        BestPlatformCard(
            title = selectedRange.cardTitle,
            bestPlatform = state.bestPlatform,
            bestNetPerHour = state.bestNetPerHour
        )

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoneyStat("Revenue", state.totalRevenue, Modifier.weight(1f))
            SignedMoneyStat("Net", state.totalNet, Modifier.weight(1f))
            HoursStat("Hours", state.totalHours, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        if (state.rows.isEmpty()) {
            EmptyState(
                icon = Icons.Default.BarChart,
                title = "No trips in this range",
                subtitle = "Try a different time window, or log a shift first.",
                modifier = Modifier.testTag("empty_comparison")
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.rows, key = { it.platform }) { row ->
                    PlatformRow(row, state.rows.first().netPerHourInr)
                }
            }
        }
    }
}

@Composable
private fun BestPlatformCard(
    title: String,
    bestPlatform: String?,
    bestNetPerHour: Double
) {
    val c = LocalGigRunColors.current
    GigCard(Modifier.fillMaxWidth().testTag("best_platform_card")) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = c.textTertiary)
            Spacer(Modifier.height(4.dp))
            if (bestPlatform == null) {
                Text(
                    "No data yet",
                    fontSize = 18.sp,
                    color = c.textTertiary,
                    modifier = Modifier.testTag("best_platform_empty")
                )
            } else {
                Text(bestPlatform, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Spacer(Modifier.height(2.dp))
                NetPerHourText(
                    netPerHour = bestNetPerHour,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("best_platform_rate")
                )
            }
        }
    }
}

/**
 * [AnimatedRupees] clamps negative targets to 0 (it is a rise-from-zero counter),
 * so a platform that lost money would silently read "₹0". Money that can be
 * negative therefore renders as plain signed text; only non-negative values get
 * the animated counter.
 */
@Composable
private fun NetPerHourText(
    netPerHour: Double,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val c = LocalGigRunColors.current
    val tint = if (netPerHour >= 0) c.success else c.error
    val label = " / hr"
    if (netPerHour >= 0) {
        AnimatedRupees(target = netPerHour, modifier = modifier) { text ->
            Text("$text$label", fontSize = fontSize, fontWeight = fontWeight, color = tint)
        }
    } else {
        Text(
            "-₹${-netPerHour.toInt()}$label",
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = tint,
            modifier = modifier
        )
    }
}

@Composable
private fun StatCardShell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val c = LocalGigRunColors.current
    GigCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = c.textTertiary)
            Spacer(Modifier.height(2.dp))
            value()
        }
    }
}

/** Non-negative money (gross revenue) — safe for the rise-from-zero counter. */
@Composable
private fun MoneyStat(label: String, amount: Double, modifier: Modifier = Modifier) {
    StatCardShell(label, modifier) {
        AnimatedRupees(target = amount) { text ->
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LocalGigRunColors.current.textPrimary)
        }
    }
}

/** Money that may be negative (net after allocated cost) — signed plain text. */
@Composable
private fun SignedMoneyStat(label: String, amount: Double, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    StatCardShell(label, modifier) {
        Text(
            if (amount >= 0) "₹${amount.toInt()}" else "-₹${-amount.toInt()}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (amount >= 0) c.textPrimary else c.error
        )
    }
}

@Composable
private fun HoursStat(label: String, hours: Double, modifier: Modifier = Modifier) {
    StatCardShell(label, modifier) {
        Text(
            String.format("%.1f h", hours),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LocalGigRunColors.current.textPrimary
        )
    }
}

@Composable
private fun PlatformRow(row: PlatformComparison, bestNetPerHour: Double) {
    val c = LocalGigRunColors.current
    GigCard(Modifier.fillMaxWidth().testTag("platform_row_${row.platform}")) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.platform, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                NetPerHourText(
                    netPerHour = row.netPerHourInr,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("platform_rate_${row.platform}")
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("₹${row.revenueInr.toInt()} gross")
                    append(" · ₹${row.allocatedCostInr.toInt()} cost")
                    append(" · ${String.format("%.1f", row.activeHours)} h")
                    append(" · ${String.format("%.1f", row.distanceKm)} km")
                    append(" · ${row.tripCount} trips")
                },
                fontSize = 11.sp,
                color = c.textTertiary
            )
            if (!row.hasTrackedHours) {
                Spacer(Modifier.height(4.dp))
                Text("Hours not tracked", fontSize = 11.sp, color = c.warning)
            }
            // Relative-to-best bar. Guarded on a positive best: without it every
            // fraction is 0/0 (NaN) or a negative quotient of two negatives.
            if (bestNetPerHour > 0 && row.netPerHourInr > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (row.netPerHourInr / bestNetPerHour).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = c.success,
                    trackColor = c.surfaceVariant
                )
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

private fun previewRows(): List<PlatformComparison> = listOf(
    PlatformComparison("Blinkit", 3200.0, 8.0, 62.5, 41, 900.0, 2300.0, 287.5),
    PlatformComparison("Zepto", 2100.0, 7.5, 48.0, 33, 843.75, 1256.25, 167.5),
    PlatformComparison("Rapido", 400.0, 4.0, 30.0, 12, 450.0, -50.0, -12.5)
)

private fun previewState(rows: List<PlatformComparison> = previewRows()) = ComparisonUiState.Success(
    rows = rows,
    bestPlatform = rows.firstOrNull()?.takeIf { it.hasTrackedHours }?.platform,
    bestNetPerHour = rows.firstOrNull()?.takeIf { it.hasTrackedHours }?.netPerHourInr ?: 0.0,
    totalRevenue = rows.sumOf { it.revenueInr },
    totalNet = rows.sumOf { it.netInr },
    totalHours = rows.sumOf { it.activeHours },
    rangeLabel = RangeOption.THIS_WEEK.label
)

@Preview(showBackground = true)
@Composable
private fun PreviewComparisonSuccess() {
    GigRunTheme {
        ComparisonContent(previewState(), RangeOption.THIS_WEEK, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewComparisonEmpty() {
    GigRunTheme {
        ComparisonContent(previewState(emptyList()), RangeOption.THIS_WEEK, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewComparisonLoading() {
    GigRunTheme {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressRing(progress = 0f, size = 48.dp, strokeWidth = 4.dp)
            Spacer(Modifier.size(12.dp))
            Text("Loading comparison…", color = LocalGigRunColors.current.textTertiary, fontSize = 13.sp)
        }
    }
}