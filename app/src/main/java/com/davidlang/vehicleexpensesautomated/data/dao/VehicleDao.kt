package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY make, model")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY make, model")
    suspend fun getAllIncludingDeleted(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE originDeviceId = :originDeviceId AND id = :id LIMIT 1")
    suspend fun findBySyncKey(originDeviceId: String, id: Int): Vehicle?

    @Query("SELECT * FROM vehicles WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun getVehicleById(id: Int): Flow<Vehicle?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: Int)

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: Int): Vehicle?

    /**
     * System Unassigned bucket (id=0). Room autoGenerate treats 0 as "unset" on
     * normal insert — use explicit SQL so local id stays 0.
     */
    @Query(
        """
        INSERT OR REPLACE INTO vehicles (
          id, name, make, model, year, licensePlate, vin, notes,
          referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl,
          odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom,
          otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom,
          landmarkTextBlocksJson, cloudManifest, deleted, deletedAt,
          syncId, originDeviceId, updatedAt
        ) VALUES (
          0, :name, NULL, NULL, NULL, NULL, NULL, :notes,
          NULL, NULL,
          NULL, NULL, NULL, NULL,
          NULL, NULL, NULL, NULL,
          NULL, NULL, 0, NULL,
          :syncId, :originDeviceId, :updatedAt
        )
        """,
    )
    suspend fun upsertUnassignedSystemVehicle(
        name: String,
        notes: String,
        syncId: String,
        originDeviceId: String,
        updatedAt: Long,
    )
}
