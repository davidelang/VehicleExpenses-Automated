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

    @Query("SELECT * FROM fuel_entries WHERE deleted = 0 ORDER BY timestamp DESC")
    fun getAllFuelEntries(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    suspend fun getAllIncludingDeleted(): List<FuelEntry>

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId AND deleted = 0 ORDER BY timestamp DESC")
    fun getFuelEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries WHERE originDeviceId = :originDeviceId AND id = :id LIMIT 1")
    suspend fun findBySyncKey(originDeviceId: String, id: Long): FuelEntry?

    @Query("SELECT * FROM fuel_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): FuelEntry?

    @Insert
    suspend fun insertFuelEntry(entry: FuelEntry)

    @Update
    suspend fun updateFuelEntry(entry: FuelEntry)

    @Delete
    suspend fun deleteFuelEntry(entry: FuelEntry)
}
