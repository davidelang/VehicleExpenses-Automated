package com.davidlang.vehicleexpensesautomated.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    init {
        loadVehicles()
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            _vehicles.value = vehicleRepository.getAllVehicles()
        }
    }

    fun updateReferenceDashPhoto(vehicleId: Int, photoUrl: String) {
        viewModelScope.launch {
            val vehicle = _vehicles.value.find { it.id == vehicleId } ?: return@launch
            val updated = vehicle.copy(referenceDashPhotoUrl = photoUrl)
            vehicleRepository.updateVehicle(updated)
            loadVehicles() // refresh list
        }
    }
}
