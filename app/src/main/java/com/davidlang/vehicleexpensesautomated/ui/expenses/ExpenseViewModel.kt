package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: Int = savedStateHandle.get<Int>("vehicleId") ?: 0

    val expenses: StateFlow<List<Expense>> = repository.getExpensesForVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(amount: Double, description: String, dateMillis: Long, category: String = "Other") {
        viewModelScope.launch {
            val expense = Expense(
                vehicleId = vehicleId,
                amount = amount,
                description = description,
                dateMillis = dateMillis,
                category = category
            )
            repository.insert(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.delete(expense)
        }
    }
}
