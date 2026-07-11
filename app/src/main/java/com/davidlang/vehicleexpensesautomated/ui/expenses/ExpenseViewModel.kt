package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseEntryRepository: ExpenseEntryRepository
) : ViewModel() {

    val expenses: StateFlow<List<ExpenseEntry>> = expenseEntryRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Persist then return — callers must await before navigate. */
    suspend fun saveExpense(entry: ExpenseEntry) {
        withContext(Dispatchers.IO) {
            expenseEntryRepository.saveEntry(entry)
        }
    }

    suspend fun getExpenseById(id: Long): ExpenseEntry? = expenseEntryRepository.getById(id)
}
