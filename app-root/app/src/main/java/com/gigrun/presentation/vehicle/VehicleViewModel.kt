package com.gigrun.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.entities.Vehicle
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VehicleUiState {
    data object Loading : VehicleUiState
    data class Success(
        val vehicles: List<Vehicle>,
        val selected: Vehicle?,
        val actualKmpl: Double?,
        val claimedKmpl: Double?,
        val costPerKm: Double?,
        val isEfficient: Boolean
    ) : VehicleUiState
    data class Error(val message: String) : VehicleUiState
}

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<VehicleUiState> = combine(
        repository.getVehicles(),
        repository.getSelected(),
        repository.actualKmpl(),
        prefs.fuelPrice
    ) { vehicles, selected, actualKmpl, fuelPrice ->
        val (claimed, isEfficient, costPerKm) = selected?.let { s ->
            val c = s.claimedKmpl
            val efficient = if (c != null && c > 0 && actualKmpl != null) actualKmpl >= 0.8 * c else true
            val cpkm = if (s.claimedKmpl != null && s.claimedKmpl > 0 && fuelPrice != null && fuelPrice > 0)
                fuelPrice / s.claimedKmpl else null
            Triple(s.claimedKmpl, efficient, cpkm)
        } ?: Triple(null, true, null)
        VehicleUiState.Success(vehicles, selected, actualKmpl, claimed, costPerKm, isEfficient)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState.Loading)

    private val _error = MutableStateFlow<String?>(null)

    fun selectVehicle(id: Long) {
        viewModelScope.launch {
            try { repository.selectVehicle(id) } catch (e: Exception) { _error.value = e.message }
        }
    }
}