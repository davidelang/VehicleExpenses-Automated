package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val vehicleId: Int
) : ViewModel() {

    val expenses = repository.getExpensesForVehicle(vehicleId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addExpense(amount: Double, category: String, description: String?, dateMillis: Long) {
        viewModelScope.launch {
            repository.insert(
                Expense(
                    vehicleId = vehicleId,
                    amount = amount,
                    dateMillis = dateMillis,
                    category = category,
                    description = description
                )
            )
        }
    }
}
