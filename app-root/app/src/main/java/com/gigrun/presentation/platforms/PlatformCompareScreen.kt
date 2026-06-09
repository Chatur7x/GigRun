package com.gigrun.presentation.platforms

import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.dao.PlatformStatRow
import com.gigrun.ui.components.PlatformBadge
import com.gigrun.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class PlatformCompareState(
    val platforms: List<PlatformStatRow> = emptyList(),
    val period: String = "This Week"
)

class PlatformCompareViewModel(application: Application) : AndroidViewModel(application) {
    private val database = androidx.room.Room.databaseBuilder(
        application, AppDatabase::class.java, "gigrun_db"
    ).fallbackToDestructiveMigration().build()

    private val _uiState = MutableStateFlow(PlatformCompareState())
    val uiState: StateFlow<PlatformCompareState> = _uiState.asStateFlow()

    init { loadWeekStats() }

    fun loadWeekStats() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            val startOfWeek = cal.timeInMillis
            val stats = database.tripDao().getPlatformStats(startOfWeek)
            _uiState.value = PlatformCompareState(platforms = stats)
        }
    }
}

@Composable
fun PlatformCompareScreen(viewModel: PlatformCompareViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWeekStats()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepCarbon)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 100.dp)
    ) {
        Text("PLATFORM COMPARISON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(state.period, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(20.dp))

        if (state.platforms.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No trips recorded yet this week.\nStart riding to see platform stats!", color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        } else {
            for (platform in state.platforms) {
                PlatformCard(stat = platform)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PlatformCard(stat: PlatformStatRow) {
    Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                PlatformBadge(platform = stat.platform)
                Text("${stat.tripCount} trips", fontSize = 14.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Earned", "₹${(stat.totalEarnings ?: 0.0).toInt()}", EmeraldGreen)
                StatColumn("Distance", "${String.format("%.1f", stat.totalDistance ?: 0.0)} km", CyberCyan)
                StatColumn("Avg Wait", "${((stat.avgWaitTime ?: 0.0) / 60).toInt()} min", MoltenAmber)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}
