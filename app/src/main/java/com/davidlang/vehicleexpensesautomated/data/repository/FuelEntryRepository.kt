package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.FuelEntryDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FuelEntryRepository @Inject constructor(
    private val fuelEntryDao: FuelEntryDao
) {

    fun getAllFuelEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllFuelEntries()

    suspend fun insertFuelEntry(entry: FuelEntry) = fuelEntryDao.insertFuelEntry(entry)

    suspend fun updateFuelEntry(entry: FuelEntry) = fuelEntryDao.updateFuelEntry(entry)

    suspend fun deleteFuelEntry(entry: FuelEntry) = fuelEntryDao.deleteFuelEntry(entry)
}
