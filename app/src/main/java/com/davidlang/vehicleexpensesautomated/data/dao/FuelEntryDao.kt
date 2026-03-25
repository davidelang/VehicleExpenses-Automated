package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelEntryDao {

    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    fun getAllFuelEntries(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getFuelEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>>

    @Insert
    suspend fun insertFuelEntry(entry: FuelEntry)

    @Update
    suspend fun updateFuelEntry(entry: FuelEntry)

    @Delete
    suspend fun deleteFuelEntry(entry: FuelEntry)
}
