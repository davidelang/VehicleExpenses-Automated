package com.davidlang.vehicleexpensesautomated.repository

import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseDao
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao
) {
    fun getExpensesForVehicle(vehicleId: Int): Flow<List<Expense>> = dao.getExpensesForVehicle(vehicleId)

    fun getAllExpenses(): Flow<List<Expense>> = dao.getAllExpenses()

    suspend fun insert(expense: Expense) {
        dao.insertExpense(expense)
    }
}
