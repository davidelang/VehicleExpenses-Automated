package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.repository.ExpenseRepository
import com.davidlang.vehicleexpensesautomated.repository.FuelRepository
import com.davidlang.vehicleexpensesautomated.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val expenseRepo: ExpenseRepository,
    private val fuelRepo: FuelRepository
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepo.allVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalVehicles: StateFlow<Int> = vehicles.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalExpenses: StateFlow<Double> = expenseRepo.getAllExpenses()
        .map { it.sumOf { e -> e.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalFuelCost: StateFlow<Double> = fuelRepo.getAllFuelFills()
        .map { it.sumOf { f -> f.totalCost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalGallons: StateFlow<Double> = fuelRepo.getAllFuelFills()
        .map { it.sumOf { f -> f.gallons } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val avgPricePerGallon: StateFlow<Double> = combine(totalFuelCost, totalGallons) { cost, gallons ->
        if (gallons > 0) cost / gallons else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val roughAvgMPG: StateFlow<Double> = fuelRepo.getAllFuelFills()
        .map { fills ->
            val sorted = fills.sortedBy { it.dateMillis }
            val mpgs = sorted.windowed(2).mapNotNull { (prev, curr) ->
                if (curr.odometer > prev.odometer && curr.gallons > 0) {
                    (curr.odometer - prev.odometer).toDouble() / curr.gallons
                } else null
            }
            if (mpgs.isNotEmpty()) mpgs.average() else 0.0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Per-vehicle summary (list of vehicles with their totals)
    val perVehicleSummary: StateFlow<List<VehicleSummary>> = combine(
        vehicles,
        expenseRepo.getAllExpenses(),
        fuelRepo.getAllFuelFills()
    ) { vehicles, allExpenses, allFills ->
        vehicles.map { vehicle ->
            val vehicleExpenses = allExpenses.filter { it.vehicleId == vehicle.id }
            val vehicleFills = allFills.filter { it.vehicleId == vehicle.id }

            VehicleSummary(
                vehicle = vehicle,
                totalExpense = vehicleExpenses.sumOf { it.amount },
                totalFuelCost = vehicleFills.sumOf { it.totalCost },
                totalGallons = vehicleFills.sumOf { it.gallons }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

data class VehicleSummary(
    val vehicle: Vehicle,
    val totalExpense: Double,
    val totalFuelCost: Double,
    val totalGallons: Double
)
