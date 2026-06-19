package com.gigrun.presentation.platforms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.dao.PlatformStatRow
import com.gigrun.data.database.dao.TripDao
import com.gigrun.ui.components.PlatformBadge
import com.gigrun.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PlatformCompareState(
    val platforms: List<PlatformStatRow> = emptyList(),
    val period: String = "This Week"
)

@HiltViewModel
class PlatformCompareViewModel @Inject constructor(
    private val tripDao: TripDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlatformCompareState())
    val uiState: StateFlow<PlatformCompareState> = _uiState.asStateFlow()

    init { loadWeekStats() }

    fun loadWeekStats() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            _uiState.value = PlatformCompareState(platforms = tripDao.getPlatformStats(cal.timeInMillis))
        }
    }
}

@Composable
fun PlatformCompareScreen(viewModel: PlatformCompareViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWeekStats() }

    Column(
        modifier = Modifier.fillMaxSize().background(SystemBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)
    ) {
        Text(
            "Compare",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = LabelPrimary,
            letterSpacing = 0.37.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text("Platform stats for this week", fontSize = 15.sp, color = LabelSecondary, letterSpacing = (-0.24).sp)
        Spacer(Modifier.height(20.dp))

        if (state.platforms.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = SecondaryBackground, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No trips this week yet.\nStart riding to see stats.", color = LabelSecondary, textAlign = TextAlign.Center, fontSize = 15.sp)
                }
            }
        } else {
            state.platforms.forEach { platform ->
                PlatformCard(stat = platform)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun PlatformCard(stat: PlatformStatRow) {
    Surface(shape = RoundedCornerShape(14.dp), color = SecondaryBackground, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PlatformBadge(platform = stat.platform)
                Spacer(Modifier.weight(1f))
                Text("${stat.tripCount} trips", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LabelSecondary, letterSpacing = (-0.24).sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricColumn("Earned", "₹${(stat.totalEarnings ?: 0.0).toInt()}", SystemGreen)
                MetricColumn("Distance", "${String.format("%.1f", stat.totalDistance ?: 0.0)} km", SystemBlue)
                MetricColumn("Avg Wait", "${((stat.avgWaitTime ?: 0.0) / 60).toInt()} min", SystemOrange)
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.38.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = LabelTertiary, letterSpacing = 0.06.sp)
    }
}
