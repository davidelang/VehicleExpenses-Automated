package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.repository.ExpenseRepository
import com.davidlang.vehicleexpensesautomated.repository.FuelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val fuelRepo: FuelRepository
) : ViewModel() {

    val allExpenses: StateFlow<List<Expense>> = expenseRepo.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFuelFills: StateFlow<List<FuelFill>> = fuelRepo.getAllFuelFills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalExpenses: Double get() = allExpenses.value.sumOf { it.amount }

    val totalFuelCost: Double get() = allFuelFills.value.sumOf { it.totalCost }
    val totalGallons: Double get() = allFuelFills.value.sumOf { it.gallons }
    val avgPricePerGallon: Double get() = if (totalGallons > 0) totalFuelCost / totalGallons else 0.0

    // Rough global avg MPG (average across all fills with odometer delta)
    val avgMPG: Double get() {
        val sortedFills = allFuelFills.value.sortedBy { it.dateMillis }
        val mpgs = sortedFills.windowed(2).mapNotNull { (prev, curr) ->
            if (curr.odometer > prev.odometer && curr.gallons > 0) {
                (curr.odometer - prev.odometer).toDouble() / curr.gallons
            } else null
        }
        return if (mpgs.isNotEmpty()) mpgs.average() else 0.0
    }
}
