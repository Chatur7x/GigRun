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
import com.gigrun.ui.theme.Apple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PlatformCompareState(val platforms: List<PlatformStatRow> = emptyList())

@HiltViewModel
class PlatformCompareViewModel @Inject constructor(private val tripDao: TripDao) : ViewModel() {
    private val _uiState = MutableStateFlow(PlatformCompareState())
    val uiState: StateFlow<PlatformCompareState> = _uiState.asStateFlow()
    init { loadWeekStats() }
    fun loadWeekStats() {
        viewModelScope.launch {
            val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            _uiState.value = PlatformCompareState(tripDao.getPlatformStats(cal.timeInMillis))
        }
    }
}

@Composable
fun PlatformCompareScreen(viewModel: PlatformCompareViewModel = hiltViewModel()) {
    val c = Apple.colors
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadWeekStats() }

    Column(Modifier.fillMaxSize().background(c.groupedBackground).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)) {
        Text("Compare", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.padding(vertical = 8.dp))
        Text("This week's platform stats", fontSize = 15.sp, color = c.secondaryLabel)
        Spacer(Modifier.height(20.dp))

        if (state.platforms.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No trips this week yet.\nStart riding to see stats.", color = c.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
        } else {
            state.platforms.forEach { platform ->
                Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PlatformBadge(platform.platform)
                            Spacer(Modifier.weight(1f))
                            Text("${platform.tripCount} trips", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.secondaryLabel)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            MetricCol("Earned", "₹${(platform.totalEarnings ?: 0.0).toInt()}", c.green)
                            MetricCol("Distance", "${String.format("%.1f", platform.totalDistance ?: 0.0)} km", c.blue)
                            MetricCol("Avg Wait", "${((platform.avgWaitTime ?: 0.0) / 60).toInt()} min", c.orange)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Apple.colors.tertiaryLabel)
    }
}
