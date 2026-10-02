package com.gigrun.presentation.penalties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.entities.Penalty
import com.gigrun.data.repository.PenaltyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PenaltyUiState {
    data object Loading : PenaltyUiState
    data class Success(
        val penalties: List<Penalty>,
        val monthlyTotal: Double,
        val disputedTotal: Double,
        val platformBreakdown: List<com.gigrun.data.database.dao.PenaltyPlatformRow>
    ) : PenaltyUiState
    data class Error(val message: String) : PenaltyUiState
}

sealed interface PenaltyEvent {
    data object PenaltyAdded : PenaltyEvent
    data object PenaltyDeleted : PenaltyEvent
    data class DisputeTemplateGenerated(val text: String) : PenaltyEvent
    data class Error(val message: String) : PenaltyEvent
}

@HiltViewModel
class PenaltyTrackerViewModel @Inject constructor(
    private val repository: PenaltyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PenaltyUiState>(PenaltyUiState.Loading)
    val uiState: StateFlow<PenaltyUiState> = _uiState.asStateFlow()

    private val _events = Channel<PenaltyEvent>(Channel.BUFFERED)
    val events: Flow<PenaltyEvent> = _events.receiveAsFlow()

    init {
        val (start, end) = monthRange()
        viewModelScope.launch {
            combine(
                repository.getAllPenalties(),
                repository.getMonthlyTotal(start, end),
                repository.getDisputedTotal(start, end),
                repository.getPlatformBreakdown(start, end)
            ) { penalties, monthlyTotal, disputedTotal, breakdown ->
                PenaltyUiState.Success(penalties, monthlyTotal, disputedTotal, breakdown)
            }.catch { e ->
                _uiState.value = PenaltyUiState.Error(e.message ?: "Unknown error")
            }.collect { state -> _uiState.value = state }
        }
    }

    private fun monthRange(): Pair<Long, Long> {
        val zone = java.time.ZoneId.systemDefault()
        val firstOfMonth = java.time.LocalDate.now()
            .withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val firstOfNextMonth = java.time.LocalDate.now()
            .withDayOfMonth(1)
            .plusMonths(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return firstOfMonth to firstOfNextMonth
    }

    fun addPenalty(platform: String, amountInr: Double, reason: String) {
        viewModelScope.launch {
            try {
                val safePlatform = platform.take(30).trim()
                val safeAmount = amountInr.coerceIn(0.0, 100_000.0)
                val safeReason = reason.take(200).trim()
                if (safePlatform.isBlank() || safeReason.isBlank() || safeAmount <= 0) {
                    _events.send(PenaltyEvent.Error("Fill all fields with valid values"))
                    return@launch
                }
                repository.addPenalty(
                    Penalty(
                        platform = safePlatform,
                        amountInr = safeAmount,
                        reason = safeReason,
                        timestamp = System.currentTimeMillis(),
                        isDisputed = false
                    )
                )
                _events.send(PenaltyEvent.PenaltyAdded)
            } catch (e: Exception) {
                _events.send(PenaltyEvent.Error(e.message ?: "Failed to add penalty"))
            }
        }
    }

    fun deletePenalty(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                _events.send(PenaltyEvent.PenaltyDeleted)
            } catch (e: Exception) {
                _events.send(PenaltyEvent.Error(e.message ?: "Failed to delete penalty"))
            }
        }
    }

    fun toggleDisputed(penalty: Penalty) {
        viewModelScope.launch {
            try {
                repository.setDisputed(penalty.id, !penalty.isDisputed)
            } catch (e: Exception) {
                _events.send(PenaltyEvent.Error(e.message ?: "Failed to update"))
            }
        }
    }

    fun generateDisputeTemplate(penalty: Penalty): String {
        val instant = java.time.Instant.ofEpochMilli(penalty.timestamp)
        val dateStr = java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
        return """
            Subject: Dispute — Penalty of ₹${penalty.amountInr.toInt()} on ${penalty.platform}

            Dear ${penalty.platform} Support Team,

            I am writing to dispute a penalty charged to my account on $dateStr.

            Details:
            - Platform: ${penalty.platform}
            - Amount: ₹${penalty.amountInr.toInt()}
            - Reason given: ${penalty.reason}
            - Date/Time: $dateStr

            I believe this penalty was applied in error. I request a review and reversal of this charge.

            Please find attached any relevant evidence from my app logs.

            Regards,
            GigRun User
        """.trimIndent()
    }
}
