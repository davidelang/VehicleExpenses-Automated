package com.davidlang.vehicleexpensesautomated.repository

import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseDao
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    fun getExpensesForVehicle(vehicleId: Int): Flow<List<Expense>> = expenseDao.getExpensesForVehicle(vehicleId)

    suspend fun insert(expense: Expense) {
        expenseDao.insertExpense(expense)
    }
}
