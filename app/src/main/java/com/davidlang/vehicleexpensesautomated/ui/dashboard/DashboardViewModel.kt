package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.repository.ExpenseRepository
import com.davidlang.vehicleexpensesautomated.repository.FuelRepository
import com.davidlang.vehicleexpensesautomated.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val fuelRepo: FuelRepository,
    private val vehicleRepo: VehicleRepository
) : ViewModel() {

    val totalVehicles: StateFlow<Int> = vehicleRepo.allVehicles
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalExpenses: StateFlow<Double> = expenseRepo.getAllExpenses()
        .map { expenses -> expenses.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalFuelCost: StateFlow<Double> = fuelRepo.getAllFuelFills()
        .map { fills -> fills.sumOf { it.totalCost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalGallons: StateFlow<Double> = fuelRepo.getAllFuelFills()
        .map { fills -> fills.sumOf { it.gallons } }
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
}
