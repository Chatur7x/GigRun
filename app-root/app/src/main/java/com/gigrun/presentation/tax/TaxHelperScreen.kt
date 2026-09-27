package com.gigrun.presentation.tax

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.core.utils.TaxCalculator
import com.gigrun.data.database.dao.ExpenseDao
import com.gigrun.data.database.dao.TripDao
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TaxViewModel @Inject constructor(private val tripDao: TripDao, private val expenseDao: ExpenseDao) : ViewModel() {
    private val _summary = MutableStateFlow<TaxCalculator.TaxSummary?>(null)
    val summary: StateFlow<TaxCalculator.TaxSummary?> = _summary
    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()
    init { load() }
    fun load() {
        viewModelScope.launch {
            _loadError.value = false
            try {
                val now = System.currentTimeMillis()
                val monthAgo = now - 30L * 86_400_000L
                val earnings = tripDao.getTotalEarningsForDay(monthAgo, now) ?: 0.0
                val deductible = expenseDao.getDeductibleTotalForRange(monthAgo, now) ?: 0.0
                _summary.value = if (earnings > 0) TaxCalculator.calculate(earnings, 30, deductible) else null
            } catch (e: Exception) {
                android.util.Log.e("TaxVM", "load failed", e)
                _loadError.value = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxHelperScreen(
    viewModel: TaxViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
    onAddExpenseClick: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val summary by viewModel.summary.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tax helper", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        containerColor = c.background
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val s = summary
            if (s == null) {
                GigCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (loadError) "Couldn't load tax data — check storage and retry."
                            else "Not enough data yet — complete a few shifts to see projections.",
                            fontSize = 13.sp, color = c.textSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (loadError) {
                                OutlinedButton(onClick = { viewModel.load() }, shape = RoundedCornerShape(10.dp)) {
                                    Text("Retry")
                                }
                            }
                            OutlinedButton(onClick = onAddExpenseClick, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Log an expense")
                            }
                        }
                    }
                }
                return@Column
            }
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Annual projection", fontSize = 12.sp, color = c.textTertiary)
                    Text("₹${(s.annualProjected / 1000).toInt()}k", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    LinearProgressIndicator(progress = { s.gstProgress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = if (s.gstLiable) c.error else c.primary, trackColor = c.border)
                    Text(if (s.gstLiable) "GST liable (₹20L threshold crossed)" else "${(s.gstProgress * 100).toInt()}% to GST threshold (₹20L)", fontSize = 11.sp, color = if (s.gstLiable) c.error else c.textTertiary)
                    HorizontalDivider(color = c.divider)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Deductible (proj.)", fontSize = 12.sp, color = c.textTertiary); Text("₹${s.deductibleExpenses.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.success)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable (proj.)", fontSize = 12.sp, color = c.textTertiary); Text("₹${s.taxableIncome.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textPrimary)
                    }
                    Text("Save ₹${s.suggestedMonthlySaving.toInt()}/mo · ${(s.suggestedSavingsRate * 100).toInt()}% of income", fontSize = 11.sp, color = c.textTertiary)
                }
            }
            Text("Advance tax due dates", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
            s.quarterly.forEach { q ->
                GigCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                            if (q.isOverdue) Text("Overdue", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.error)
                        }
                        Text("Due ${sdf.format(Date(q.dueDate))} · Proj. ₹${q.projectedIncome.toInt()} · Net ₹${q.netTaxable.toInt()}", fontSize = 11.sp, color = c.textTertiary)
                    }
                }
            }
        }
    }
}
