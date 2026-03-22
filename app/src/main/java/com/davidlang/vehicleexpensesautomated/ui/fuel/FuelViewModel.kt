package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.repository.FuelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val repository: FuelRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: Int = savedStateHandle.get<Int>("vehicleId") ?: 0

    val fuelFills: StateFlow<List<FuelFill>> = repository.getFuelFillsForVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalGallons: Double get() = fuelFills.value.sumOf { it.gallons }
    val totalCost: Double get() = fuelFills.value.sumOf { it.totalCost }
    val avgPricePerGallon: Double get() = if (totalGallons > 0) totalCost / totalGallons else 0.0

    fun addFuelFill(gallons: Double, pricePerGallon: Double, odometer: Int, dateMillis: Long) {
        viewModelScope.launch {
            val fill = FuelFill(
                vehicleId = vehicleId,
                gallons = gallons,
                pricePerGallon = pricePerGallon,
                odometer = odometer,
                dateMillis = dateMillis,
                totalCost = gallons * pricePerGallon
            )
            repository.insert(fill)
        }
    }
}
