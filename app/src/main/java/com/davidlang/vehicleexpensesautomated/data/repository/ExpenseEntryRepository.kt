package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseEntryRepository @Inject constructor(private val dao: ExpenseEntryDao) {
    suspend fun saveEntry(entry: ExpenseEntry) = dao.insert(entry)
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<ExpenseEntry>> = dao.getEntriesForVehicle(vehicleId)
    fun getAllEntries(): Flow<List<ExpenseEntry>> = dao.getAllEntries()
}
