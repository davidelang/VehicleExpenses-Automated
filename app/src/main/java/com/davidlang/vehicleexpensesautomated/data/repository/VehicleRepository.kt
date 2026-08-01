package com.davidlang.vehicleexpensesautomated.data.repository

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.FuelTabRenameHintStore
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import com.davidlang.vehicleexpensesautomated.data.expense.ExpenseCategories
import com.davidlang.vehicleexpensesautomated.data.trip.TripTypes
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

    companion object {
        /** Matches [BatchFuelImportCoordinator.UNASSIGNED_VEHICLE_ID]. */
        const val UNASSIGNED_VEHICLE_ID = 0
        const val UNASSIGNED_VEHICLE_NAME = "Unassigned"
        /**
         * Fixed well-known sync id — same on every device so Vehicles + Fuel tabs
         * LWW as one system bucket (`Fuel - Unassigned`).
         */
        const val UNASSIGNED_VEHICLE_SYNC_ID = "a0000000-0000-4000-8000-000000000001"
        private const val UNASSIGNED_NOTES =
            "System bucket for unassigned pump fills until resolved (do not delete)"
    }

    fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun getVehicleById(id: Int): Vehicle? = vehicleDao.getVehicleById(id).first()

    /**
     * Ensure system vehicle id=0 **Unassigned** with fixed [UNASSIGNED_VEHICLE_SYNC_ID].
     * Safe to call on startup / before spreadsheet fuel sync.
     */
    suspend fun ensureUnassignedVehicle() {
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        val now = System.currentTimeMillis()
        val byId = vehicleDao.getByIdOnce(UNASSIGNED_VEHICLE_ID)
        val bySync = vehicleDao.findBySyncId(UNASSIGNED_VEHICLE_SYNC_ID)
        if (byId != null &&
            !byId.deleted &&
            byId.name == UNASSIGNED_VEHICLE_NAME &&
            byId.syncId == UNASSIGNED_VEHICLE_SYNC_ID
        ) {
            return
        }
        // Explicit SQL keeps id=0 (autoGenerate would reassign)
        vehicleDao.upsertUnassignedSystemVehicle(
            name = UNASSIGNED_VEHICLE_NAME,
            notes = UNASSIGNED_NOTES,
            syncId = UNASSIGNED_VEHICLE_SYNC_ID,
            originDeviceId = byId?.originDeviceId?.ifBlank { deviceId }
                ?: bySync?.originDeviceId?.ifBlank { deviceId }
                ?: deviceId,
            updatedAt = maxOf(byId?.updatedAt ?: 0L, bySync?.updatedAt ?: 0L, now),
        )
    }

    /** Real vehicles only (exclude system Unassigned) for OCR / Quick Fill / assign targets. */
    fun isSystemUnassigned(v: Vehicle): Boolean =
        v.id == UNASSIGNED_VEHICLE_ID || v.syncId == UNASSIGNED_VEHICLE_SYNC_ID

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        vehicleDao.insertVehicle(stampForWrite(withCatalogsOnInsert(vehicle)))
        return 0L // Room auto-generates ID; legacy callers expect Long
    }

    /**
     * Local create: if trip types or expense categories JSON is blank, inherit from the
     * non-deleted non-Unassigned vehicle with latest [Vehicle.updatedAt]; else seed defaults.
     * Sync upserts keep remote JSON as-is (including blank).
     */
    private suspend fun withCatalogsOnInsert(vehicle: Vehicle): Vehicle {
        if (isSystemUnassigned(vehicle)) return vehicle
        var v = vehicle
        if (v.tripTypesJson.isBlank()) {
            val inheritFrom = vehicleDao.getAllIncludingDeleted()
                .asSequence()
                .filter { !it.deleted && !isSystemUnassigned(it) && it.tripTypesJson.isNotBlank() }
                .maxByOrNull { it.updatedAt }
            val json = if (inheritFrom != null) {
                TripTypes.ensureNonEmpty(inheritFrom.tripTypesJson)
            } else {
                TripTypes.seedJson()
            }
            v = v.copy(tripTypesJson = json)
        }
        if (v.expenseCategoriesJson.isBlank()) {
            val inheritFrom = vehicleDao.getAllIncludingDeleted()
                .asSequence()
                .filter {
                    !it.deleted && !isSystemUnassigned(it) && it.expenseCategoriesJson.isNotBlank()
                }
                .maxByOrNull { it.updatedAt }
            val json = if (inheritFrom != null) {
                ExpenseCategories.ensureNonEmpty(inheritFrom.expenseCategoriesJson)
            } else {
                ExpenseCategories.seedJson()
            }
            v = v.copy(expenseCategoriesJson = json)
        }
        return v
    }

    // Legacy method for existing CsvManager.kt + SyncWorker.kt
    suspend fun insert(vehicle: Vehicle): Long = insertVehicle(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) {
        val existing = vehicleDao.getVehicleById(vehicle.id).first()
        if (existing != null && existing.name != vehicle.name && existing.syncId.isNotBlank()) {
            val oldTab = TabularSchema.fuelTabName(existing.name)
            val newTab = TabularSchema.fuelTabName(vehicle.name)
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

    suspend fun deleteVehicle(vehicle: Vehicle) {
        if (isSystemUnassigned(vehicle)) return
        vehicleDao.deleteVehicle(vehicle.id)
    }

    suspend fun markVehicleDeleted(vehicle: Vehicle) {
        if (isSystemUnassigned(vehicle)) return
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

/** True for system Unassigned bucket (id=0 / fixed sync id). */
fun isSystemUnassignedVehicle(v: Vehicle): Boolean =
    v.id == VehicleRepository.UNASSIGNED_VEHICLE_ID ||
        v.syncId == VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID

/**
 * Capture / assign pickers: non-deleted, not system Unassigned.
 * Does **not** apply to Reports (Unknown may appear when data-bearing).
 */
fun List<Vehicle>.forUserPicker(): List<Vehicle> =
    filter { !it.deleted && !isSystemUnassignedVehicle(it) }
        .sortedBy { it.name.lowercase() }

/** Manage Vehicles list: same as [forUserPicker]. */
fun List<Vehicle>.forManageList(): List<Vehicle> = forUserPicker()