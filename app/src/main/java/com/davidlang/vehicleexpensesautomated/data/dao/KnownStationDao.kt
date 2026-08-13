package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation

@Dao
interface KnownStationDao {

    @Query("SELECT * FROM known_stations WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllLive(): List<KnownStation>

    @Query("SELECT * FROM known_stations ORDER BY updatedAt DESC")
    suspend fun getAllIncludingDeleted(): List<KnownStation>

    @Query("SELECT * FROM known_stations WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): KnownStation?

    @Query("SELECT COUNT(*) FROM known_stations")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM known_stations WHERE deleted = 0")
    suspend fun countLive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: KnownStation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<KnownStation>)

    @Update
    suspend fun update(station: KnownStation)
}
