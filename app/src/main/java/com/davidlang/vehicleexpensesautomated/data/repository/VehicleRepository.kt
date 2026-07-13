package com.davidlang.vehicleexpensesautomated.data.repository

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.FuelTabRenameHintStore
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicleDao: VehicleDao,
    private val photoStorage: PhotoStorageManager,
) {

    fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun getVehicleById(id: Int): Vehicle? = vehicleDao.getVehicleById(id).first()

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        vehicleDao.insertVehicle(stampForWrite(vehicle))
        return 0L // Room auto-generates ID; legacy callers expect Long
    }

    // Legacy method for existing CsvManager.kt + SyncWorker.kt
    suspend fun insert(vehicle: Vehicle): Long = insertVehicle(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) {
        val existing = vehicleDao.getVehicleById(vehicle.id).first()
        if (existing != null && existing.name != vehicle.name && existing.syncId.isNotBlank()) {
            val oldTab = GoogleSheetsClient.fuelTabName(existing.name)
            val newTab = GoogleSheetsClient.fuelTabName(vehicle.name)
            if (oldTab != newTab) {
                FuelTabRenameHintStore(context).recordHint(existing.syncId, oldTab)
            }
        }
        vehicleDao.updateVehicle(stampForWrite(vehicle))
    }

    /**
     * Asset-only updates (photo paths, cloud manifest bookkeeping) must not bump [Vehicle.updatedAt]
     * or LWW merge can let a thin local row beat a rich remote sheet definition.
     */
    suspend fun updateVehiclePreservingTimestamp(vehicle: Vehicle) =
        vehicleDao.updateVehicle(ensureSyncId(vehicle))

    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.deleteVehicle(vehicle.id)

    suspend fun markVehicleDeleted(vehicle: Vehicle) {
        val now = System.currentTimeMillis()
        vehicleDao.updateVehicle(stampForWrite(vehicle).copy(deleted = true, deletedAt = now))
    }

    suspend fun getAllIncludingDeleted(): List<Vehicle> = vehicleDao.getAllIncludingDeleted()

    suspend fun findBySyncKey(originDeviceId: String, id: Int): Vehicle? =
        vehicleDao.findBySyncKey(originDeviceId, id)

    suspend fun findBySyncId(syncId: String): Vehicle? =
        vehicleDao.findBySyncId(syncId)

    /** Sync path: preserve remote/local winner timestamps; do not stamp updatedAt to now. */
    suspend fun upsertFromSync(vehicle: Vehicle) {
        val withSyncId = ensureSyncId(vehicle)
        val existing = vehicleDao.findBySyncId(withSyncId.syncId)
        val toWrite = if (existing != null) {
            preserveLocalPhotoPaths(withSyncId, existing).copy(id = existing.id)
        } else {
            withSyncId
        }
        if (existing != null) {
            vehicleDao.updateVehicle(toWrite)
        } else {
            vehicleDao.insertVehicle(toWrite)
        }
    }

    private fun preserveLocalPhotoPaths(incoming: Vehicle, existing: Vehicle): Vehicle {
        val ref = photoStorage.pickPreferredLocalPath(
            incoming.referenceDashPhotoUrl,
            existing.referenceDashPhotoUrl,
        )
        val cleaned = photoStorage.pickPreferredLocalPath(
            incoming.cleanedReferenceDashPhotoUrl,
            existing.cleanedReferenceDashPhotoUrl,
        )
        return if (ref != incoming.referenceDashPhotoUrl || cleaned != incoming.cleanedReferenceDashPhotoUrl) {
            incoming.copy(
                referenceDashPhotoUrl = ref,
                cleanedReferenceDashPhotoUrl = cleaned,
            )
        } else {
            incoming
        }
    }

    private fun stampForWrite(vehicle: Vehicle): Vehicle {
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        val now = System.currentTimeMillis()
        return ensureSyncId(
            vehicle.copy(
                originDeviceId = vehicle.originDeviceId.ifBlank { deviceId },
                updatedAt = now,
            ),
        )
    }

    private fun ensureSyncId(vehicle: Vehicle): Vehicle =
        if (vehicle.syncId.isNotBlank()) vehicle
        else vehicle.copy(syncId = SyncIdGenerator.randomSyncId())
}