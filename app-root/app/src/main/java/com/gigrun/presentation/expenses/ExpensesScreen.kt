package com.gigrun.presentation.expenses

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.dao.ExpenseDao
import com.gigrun.data.database.entities.Expense
import com.gigrun.data.database.entities.ExpenseCategory
import com.gigrun.ui.components.*
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(private val dao: ExpenseDao) : ViewModel() {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    private val _totalToday = MutableStateFlow(0.0)
    val totalToday: StateFlow<Double> = _totalToday.asStateFlow()

    init { load() }
    fun load() {
        viewModelScope.launch {
            try {
                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                val start = cal.timeInMillis; val end = start + 86_400_000L
                // Range total first (cheap), then the list collector — no nested query per emission.
                _totalToday.value = dao.getTotalForRange(start, end) ?: 0.0
                dao.getAllExpenses().collect { list ->
                    _expenses.value = list
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpensesVM", "load failed", e)
            }
        }
    }
    fun add(category: ExpenseCategory, amount: Double, note: String?) {
        // VM-level guard: dialog filter blocks "-" in UI, but the API must hold
        // for any caller — NaN/negative/huge poison tax totals.
        val safe = amount.takeIf { it.isFinite() }?.coerceIn(0.0, 10_000_000.0) ?: return
        viewModelScope.launch {
            dao.insert(Expense(category = category.name, amount = safe, note = note?.take(200), timestamp = System.currentTimeMillis(), isDeductible = category.isDeductible))
        }
    }
    fun delete(id: Long) { viewModelScope.launch { dao.deleteById(id) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val expenses by viewModel.expenses.collectAsState()
    val totalToday by viewModel.totalToday.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    // Beast screen-enter: summary card is the single moment; rows stay static.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = c.primary) { Icon(Icons.Default.Add, null, tint = c.textOnPrimary) } },
        containerColor = c.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            val summaryAlpha = com.gigrun.ui.design.beastEntranceAlpha(0, entered)
            val summaryY = com.gigrun.ui.design.beastEntranceOffsetY(0, entered)
            GigCard(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = summaryAlpha
                        translationY = summaryY
                    }
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Today's expenses", fontSize = 12.sp, color = c.textTertiary)
                        Text("₹${totalToday.toInt()}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Text("${expenses.size} entries", fontSize = 12.sp, color = c.textTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (expenses.isEmpty()) {
                EmptyState(
                    Icons.Default.ReceiptLong, "No expenses yet", "Tap + to add fuel, parking, repairs, etc.",
                    actionLabel = "+ Add expense", onAction = { showAdd = true }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(expenses) { e ->
                        GigCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val cat = try { ExpenseCategory.valueOf(e.category) } catch (_: Exception) { ExpenseCategory.OTHER }
                                        Text(cat.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                                        if (e.isDeductible) Text("deductible", fontSize = 10.sp, color = c.success, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    e.note?.let { Text(it, fontSize = 11.sp, color = c.textTertiary) }
                                    Text(sdf.format(Date(e.timestamp)), fontSize = 11.sp, color = c.textTertiary)
                                }
                                Text("₹${e.amount.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                IconButton(onClick = { viewModel.delete(e.id) }) { Icon(Icons.Default.Delete, null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
        if (showAdd) AddExpenseDialog(onDismiss = { showAdd = false }, onSave = { cat, amt, note -> viewModel.add(cat, amt, note); showAdd = false })
    }
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (ExpenseCategory, Double, String?) -> Unit) {
    val c = LocalGigRunColors.current
    var cat by remember { mutableStateOf(ExpenseCategory.FUEL) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense", fontWeight = FontWeight.Bold, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpenseCategory.entries.take(4).forEach { ec ->
                        FilterChip(selected = cat == ec, onClick = { cat = ec }, label = { Text(ec.label, fontSize = 11.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.drop(4).forEach { ec ->
                        FilterChip(selected = cat == ec, onClick = { cat = ec }, label = { Text(ec.label, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Amount ₹") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { amount.toDoubleOrNull()?.let { onSave(cat, it, note.takeIf { n -> n.isNotBlank() }) } }, enabled = amount.toDoubleOrNull() != null) { Text("Save", color = c.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textTertiary) } },
        containerColor = c.surface
    )
}
