package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
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

    // Legacy methods for existing code (CsvManager, SyncWorker, DashboardViewModel, FillupViewModel)
    suspend fun saveEntry(entry: FuelEntry) = insertFuelEntry(entry)

    fun getAllEntries() = getAllFuelEntries()

    fun getEntriesForVehicle(vehicleId: Int) = fuelEntryDao.getFuelEntriesForVehicle(vehicleId)
}
