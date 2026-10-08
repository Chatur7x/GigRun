package com.gigrun.presentation.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.design.GigRunTheme
import com.gigrun.ui.design.LocalGigRunColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    onBack: () -> Unit = {},
    viewModel: VehicleViewModel = hiltViewModel()
) {
    val c = LocalGigRunColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Vehicle", fontWeight = FontWeight.Bold, color = c.textPrimary) },
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
            is VehicleUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = c.textTertiary)
                }
            }
            is VehicleUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(pad).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { Text(current.message, color = c.error, fontSize = 14.sp) }
            }
            is VehicleUiState.Success -> {
                Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))

                    // Efficiency + cost summary for the selected vehicle.
                    if (current.selected != null) {
                        GigCard(Modifier.fillMaxWidth().testTag("vehicle_summary_card")) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    current.selected.name,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Claimed: ${current.claimedKmpl?.let { String.format("%.1f", it) } ?: "—"} km/L" +
                                        " · Actual: ${current.actualKmpl?.let { String.format("%.1f", it) } ?: "—"} km/L",
                                    fontSize = 13.sp, color = c.textSecondary
                                )
                                if (current.costPerKm != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Estimated cost/km: ₹${String.format("%.2f", current.costPerKm)}",
                                        fontSize = 13.sp, color = c.textTertiary
                                    )
                                }
                                if (!current.isEfficient) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Mileage is below 80% of claimed — check tyre pressure, air filter and fuel quality.",
                                        fontSize = 12.sp, color = c.warning
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text("Garage", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))

                    if (current.vehicles.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.DirectionsBike,
                            title = "No vehicles yet",
                            subtitle = "Add your bike or car to track fuel, mileage and running cost.",
                            modifier = Modifier.testTag("vehicle_empty")
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                        ) {
                            items(current.vehicles, key = { it.id }) { v ->
                                val isSelected = current.selected?.id == v.id
                                GigCard(
                                    Modifier.fillMaxWidth().testTag("vehicle_row_${v.id}"),
                                    onClick = { viewModel.selectVehicle(v.id) }
                                ) {
                                    Row(
                                        Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsBike, null,
                                            tint = if (isSelected) c.primary else c.textTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.size(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(v.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                                            Text(
                                                "${v.company} ${v.model}",
                                                fontSize = 12.sp, color = c.textTertiary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Star, null, tint = c.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewVehicleScreen() {
    GigRunTheme { Text("Vehicle") }
}