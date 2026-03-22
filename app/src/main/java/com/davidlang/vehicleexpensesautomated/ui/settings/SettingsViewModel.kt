package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    suspend fun getAllVehicles(): List<Vehicle> = repository.getAllVehicles()

    suspend fun getExpensesForVehicle(vehicleId: Int): List<Expense> =
        repository.getExpensesForVehicle(vehicleId)

    suspend fun getFuelFillsForVehicle(vehicleId: Int): List<FuelFill> =
        repository.getFuelFillsForVehicle(vehicleId)
}
