package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FuelEntryRepository @Inject constructor(private val dao: FuelEntryDao) {
    suspend fun saveEntry(entry: FuelEntry) = dao.insert(entry)
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>> = dao.getEntriesForVehicle(vehicleId)
    fun getAllEntries(): Flow<List<FuelEntry>> = dao.getAllEntries()
}
