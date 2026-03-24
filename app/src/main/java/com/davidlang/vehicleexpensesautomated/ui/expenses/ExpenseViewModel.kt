package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseEntryRepository
) : ViewModel() {

    private val _expenseEntries = MutableStateFlow<List<ExpenseEntry>>(emptyList())
    val expenseEntries: StateFlow<List<ExpenseEntry>> = _expenseEntries

    fun loadExpenseEntries(vehicleId: Int) {
        viewModelScope.launch {
            repository.getEntriesForVehicle(vehicleId).collect { entries ->
                _expenseEntries.value = entries
            }
        }
    }

    suspend fun saveExpense(entry: ExpenseEntry) {
        repository.saveEntry(entry)
    }
}
