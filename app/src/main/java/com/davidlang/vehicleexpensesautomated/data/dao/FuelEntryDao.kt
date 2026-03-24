package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelEntryDao {
    @Insert
    suspend fun insert(entry: FuelEntry)

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<FuelEntry>>
}
