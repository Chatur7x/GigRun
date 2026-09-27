package com.gigrun.presentation.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.core.utils.GoalsCalculator
import com.gigrun.data.database.dao.EarningsGoalDao
import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.database.entities.EarningsGoal
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.ProgressRing
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalDao: EarningsGoalDao,
    private val tripDao: TripDao
) : ViewModel() {
    val goal = goalDao.getGoal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val suggestion = MutableStateFlow<GoalsCalculator.GoalSuggestion?>(null)

    fun loadSuggestion() {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            val now = cal.timeInMillis
            val twoWeeksAgo = now - 14 * 86_400_000L
            val total = tripDao.getTotalEarningsForDay(twoWeeksAgo, now) ?: 0.0
            val avgDaily = total / 14.0
            suggestion.value = GoalsCalculator.suggest(avgDaily)
        }
    }
    fun save(daily: Double, weekly: Double, monthly: Double, auto: Boolean) {
        // Clamp absurd goals (typo'd crores) — progress math and UI lie otherwise.
        fun clampGoal(v: Double, fallback: Double) =
            v.takeIf { it.isFinite() }?.coerceIn(1.0, 10_000_000.0) ?: fallback
        viewModelScope.launch {
            goalDao.upsert(EarningsGoal(dailyTarget = clampGoal(daily, 800.0), weeklyTarget = clampGoal(weekly, 5000.0), monthlyTarget = clampGoal(monthly, 20000.0), autoCalculate = auto))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val c = LocalGigRunColors.current
    val goal by viewModel.goal.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadSuggestion() }

    // Key on stable id only: re-emission after save must not wipe in-progress typing.
    var daily by remember(goal?.id) { mutableStateOf(goal?.dailyTarget?.toInt()?.toString() ?: "800") }
    var weekly by remember(goal?.id) { mutableStateOf(goal?.weeklyTarget?.toInt()?.toString() ?: "5000") }
    var monthly by remember(goal?.id) { mutableStateOf(goal?.monthlyTarget?.toInt()?.toString() ?: "20000") }
    var auto by remember(goal?.id) { mutableStateOf(goal?.autoCalculate ?: false) }
    // Beast screen-enter: suggestion card is the moment; form stays static.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        containerColor = c.background
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            suggestion?.let { s ->
                val sugAlpha = com.gigrun.ui.design.beastEntranceAlpha(0, entered)
                val sugY = com.gigrun.ui.design.beastEntranceOffsetY(0, entered)
                GigCard(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = sugAlpha
                            translationY = sugY
                        }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("AI suggestion", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textTertiary)
                        Spacer(Modifier.height(6.dp))
                        Text("Based on ₹${s.basedOnAvgDaily.toInt()}/day avg → ₹${s.daily.toInt()}/day", fontSize = 13.sp, color = c.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { daily = s.daily.toInt().toString(); weekly = s.weekly.toInt().toString(); monthly = s.monthly.toInt().toString() }) { Text("Apply suggestion") }
                        }
                    }
                }
            }
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-calculate", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.textPrimary)
                        Switch(checked = auto, onCheckedChange = { auto = it })
                    }
                    Text("When on, daily goal adapts to your 14-day average +10%.", fontSize = 11.sp, color = c.textTertiary)
                    HorizontalDivider(color = c.divider)
                    OutlinedTextField(value = daily, onValueChange = { daily = it.filter { ch -> ch.isDigit() } }, label = { Text("Daily target ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !auto)
                    OutlinedTextField(value = weekly, onValueChange = { weekly = it.filter { ch -> ch.isDigit() } }, label = { Text("Weekly target ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !auto)
                    OutlinedTextField(value = monthly, onValueChange = { monthly = it.filter { ch -> ch.isDigit() } }, label = { Text("Monthly target ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !auto)
                    Button(onClick = { viewModel.save(daily.toDoubleOrNull() ?: 800.0, weekly.toDoubleOrNull() ?: 5000.0, monthly.toDoubleOrNull() ?: 20000.0, auto) }, modifier = Modifier.fillMaxWidth()) { Text("Save goals") }
                }
            }
        }
    }
}
