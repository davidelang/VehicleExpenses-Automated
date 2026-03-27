package com.davidlang.vehicleexpensesautomated.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    val vehicles = repository.getAllVehicles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    suspend fun getVehicleById(id: Int): Vehicle? = repository.getVehicleById(id)

    fun createNewVehicleWithReference(
        name: String,
        make: String,
        model: String,
        year: Int?,
        licensePlate: String?,
        referenceDashPhotoUrl: String?,
        odometerCropRect: androidx.compose.ui.geometry.Rect?,
        initialOdometer: Int
    ) {
        viewModelScope.launch {
            val newVehicle = Vehicle(
                name = name,
                make = make,
                model = model,
                year = year,
                licensePlate = licensePlate,
                referenceDashPhotoUrl = referenceDashPhotoUrl,
                odometerCropLeft = odometerCropRect?.left,
                odometerCropTop = odometerCropRect?.top,
                odometerCropRight = odometerCropRect?.right,
                odometerCropBottom = odometerCropRect?.bottom
            )
            repository.insert(newVehicle)
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.updateVehicle(vehicle)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}
