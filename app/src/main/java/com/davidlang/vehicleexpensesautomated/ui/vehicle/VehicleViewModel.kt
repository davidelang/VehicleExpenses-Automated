package com.davidlang.vehicleexpensesautomated.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
            vehicleRepository.getAllVehicles().collectLatest { list ->
                _vehicles.value = list
            }
        }
    }

    fun updateReferenceDashPhoto(vehicleId: Int, photoUrl: String) {
        viewModelScope.launch {
            val vehicle = _vehicles.value.find { it.id == vehicleId } ?: return@launch
            val updated = vehicle.copy(referenceDashPhotoUrl = photoUrl)
            vehicleRepository.updateVehicle(updated)
            // Flow will auto-refresh the list
        }
    }

    fun createNewVehicleWithReference(
        make: String,
        model: String,
        year: Int,
        licensePlate: String,
        referenceDashPhotoUrl: String,
        initialOdometer: Int
    ) {
        viewModelScope.launch {
            val newVehicle = Vehicle(
                make = make,
                model = model,
                year = year,
                licensePlate = licensePlate,
                referenceDashPhotoUrl = referenceDashPhotoUrl
            )
            val newId = vehicleRepository.insertVehicle(newVehicle)
            // initialOdometer can be stored later in a FuelEntry or notes field
        }
    }
}
