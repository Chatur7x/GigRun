package com.gigrun.presentation.penalties

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gigrun.data.database.entities.Penalty
import com.gigrun.ui.components.*
import com.gigrun.ui.design.LocalGigRunColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenaltyTrackerScreen(
    onBack: () -> Unit = {},
    viewModel: PenaltyTrackerViewModel = hiltViewModel()
) {
    val c = LocalGigRunColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf<Penalty?>(null) }
    val sdf = remember {
        java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PenaltyEvent.PenaltyAdded -> snackbarHostState.showSnackbar("Penalty logged")
                is PenaltyEvent.PenaltyDeleted -> snackbarHostState.showSnackbar("Penalty deleted")
                is PenaltyEvent.DisputeTemplateGenerated -> {
                    snackbarHostState.showSnackbar("Dispute template copied", actionLabel = "Share")
                }
                is PenaltyEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Penalties", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = c.primary,
                modifier = Modifier.testTag("add_penalty_fab")
            ) {
                Icon(Icons.Default.Add, null, tint = c.textOnPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = c.background
    ) { pad ->
        when (state) {
            is PenaltyUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    ProgressRing(progress = 0f, modifier = Modifier.size(48.dp))
                }
            }
            is PenaltyUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text((state as PenaltyUiState.Error).message, color = c.error)
                }
            }
            is PenaltyUiState.Success -> {
                val success = state as PenaltyUiState.Success
                Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                    // Monthly total card
                    GigCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("This Month", fontSize = 12.sp, color = c.textTertiary)
                            AnimatedRupees(target = success.monthlyTotal, content = { Text(it, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.textPrimary) })
                            Spacer(Modifier.height(4.dp))
                            Text("In Dispute: ₹${success.disputedTotal.toInt()}", fontSize = 12.sp, color = c.warning)
                        }
                    }

                    // Platform breakdown
                    if (success.platformBreakdown.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("By Platform", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        success.platformBreakdown.forEach { row ->
                            val maxTotal = (success.platformBreakdown.maxOfOrNull { it.total ?: 0.0 } ?: 0.0).coerceAtLeast(1.0)
                            val fraction = ((row.total ?: 0.0) / maxTotal).toFloat().coerceIn(0f, 1f)
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.platform, fontSize = 12.sp, color = c.textSecondary)
                                    Text("₹${row.total?.toInt() ?: 0}", fontSize = 12.sp, color = c.textPrimary)
                                }
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = c.primary,
                                    trackColor = c.surfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (success.penalties.isEmpty()) {
                        EmptyState(
                            Icons.Default.Warning,
                            "No penalties logged yet",
                            "Tap + to log a penalty and track your disputes.",
                            actionLabel = "+ Add penalty",
                            onAction = { showAdd = true }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(success.penalties) { penalty ->
                                GigCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(penalty.platform, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                                            AnimatedRupees(target = penalty.amountInr, content = { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary) })
                                        }
                                        Text(penalty.reason, fontSize = 12.sp, color = c.textSecondary)
                                        Text(sdf.format(java.time.Instant.ofEpochMilli(penalty.timestamp)), fontSize = 11.sp, color = c.textTertiary)
                                        if (penalty.isDisputed) {
                                            Text(
                                                "In Dispute",
                                                fontSize = 10.sp,
                                                color = c.warning,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            IconButton(
                                                onClick = { viewModel.toggleDisputed(penalty) },
                                                modifier = Modifier.testTag("toggle_dispute_${penalty.id}")
                                            ) {
                                                Icon(Icons.Default.Flag, null, tint = if (penalty.isDisputed) c.warning else c.textTertiary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    val template = viewModel.generateDisputeTemplate(penalty)
                                                    scope.launch { snackbarHostState.showSnackbar("Template ready", actionLabel = "Copy") }
                                                },
                                                modifier = Modifier.testTag("dispute_template_${penalty.id}")
                                            ) {
                                                Icon(Icons.Default.Description, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.deletePenalty(penalty.id) },
                                                modifier = Modifier.testTag("delete_${penalty.id}")
                                            ) {
                                                Icon(Icons.Default.Delete, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
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

        if (showAdd) {
            AddPenaltyDialog(
                onDismiss = { showAdd = false },
                onSave = { platform, amount, reason ->
                    viewModel.addPenalty(platform, amount, reason)
                    showAdd = false
                }
            )
        }
    }
}

@Composable
private fun AddPenaltyDialog(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    val c = LocalGigRunColors.current
    var platform by remember { mutableStateOf("Blinkit") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val platforms = listOf("Blinkit", "Zepto", "Rapido", "Uber", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add penalty", fontWeight = FontWeight.Bold, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    platforms.take(3).forEach { p ->
                        FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p, fontSize = 11.sp) }, modifier = Modifier.testTag("platform_chip_$p"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    platforms.drop(3).forEach { p ->
                        FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p, fontSize = 11.sp) }, modifier = Modifier.testTag("platform_chip_$p"))
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount ₹") },
                    singleLine = true,
                    modifier = Modifier.testTag("amount_field")
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 200) reason = it },
                    label = { Text("Reason (max 200 chars)") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.testTag("reason_field")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount.toDoubleOrNull()?.let { onSave(platform, it, reason) } },
                enabled = amount.toDoubleOrNull() != null && reason.isNotBlank(),
                modifier = Modifier.testTag("save_btn")
            ) { Text("Save", color = c.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_btn")) { Text("Cancel", color = c.textTertiary) } },
        containerColor = c.surface
    )
}
