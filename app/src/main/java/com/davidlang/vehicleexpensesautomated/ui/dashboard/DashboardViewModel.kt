package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fuelEntryRepository: FuelEntryRepository,
    private val expenseEntryRepository: ExpenseEntryRepository
) : ViewModel() {

    private val _recentFuel = MutableStateFlow<List<FuelEntry>>(emptyList())
    val recentFuel: StateFlow<List<FuelEntry>> = _recentFuel

    private val _recentExpenses = MutableStateFlow<List<ExpenseEntry>>(emptyList())
    val recentExpenses: StateFlow<List<ExpenseEntry>> = _recentExpenses

    fun loadRecentData(vehicleId: Int) {
        viewModelScope.launch {
            fuelEntryRepository.getEntriesForVehicle(vehicleId).collect { entries ->
                _recentFuel.value = entries.take(5)
            }
        }
        viewModelScope.launch {
            expenseEntryRepository.getEntriesForVehicle(vehicleId).collect { entries ->
                _recentExpenses.value = entries.take(5)
            }
        }
    }
}
