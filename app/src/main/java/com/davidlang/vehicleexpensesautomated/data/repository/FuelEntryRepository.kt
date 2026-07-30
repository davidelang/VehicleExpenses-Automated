package com.davidlang.vehicleexpensesautomated.data.repository

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FuelEntryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fuelEntryDao: FuelEntryDao,
    private val photoStorage: PhotoStorageManager,
) {

    fun getAllFuelEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllFuelEntries()

    fun getEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>> = fuelEntryDao.getFuelEntriesForVehicle(vehicleId)

    suspend fun getById(id: Long): FuelEntry? = fuelEntryDao.getById(id)

    suspend fun insertFuelEntry(entry: FuelEntry) = fuelEntryDao.insertFuelEntry(stampForWrite(entry))

    suspend fun updateFuelEntry(entry: FuelEntry) = fuelEntryDao.updateFuelEntry(stampForWrite(entry))

    /**
     * Manifest / photoUrl bookkeeping must not bump [FuelEntry.updatedAt] or LWW can spuriously win.
     */
    suspend fun updateFuelEntryPreservingTimestamp(entry: FuelEntry) =
        fuelEntryDao.updateFuelEntry(ensureSyncId(entry))

    suspend fun deleteFuelEntry(entry: FuelEntry) = markFuelDeleted(entry)

    suspend fun markFuelDeleted(entry: FuelEntry) {
        val now = System.currentTimeMillis()
        fuelEntryDao.updateFuelEntry(
            stampForWrite(entry).copy(deleted = true, deletedAt = now)
        )
    }

    /**
     * Hard-delete a local fuel row (Room `@Delete`). Used by batch merge when
     * absorbing partials during initial testing (sync off). Normal UI continues
     * to soft-delete via [deleteFuelEntry] / [markFuelDeleted].
     */
    suspend fun hardDeleteFuelEntry(entry: FuelEntry) {
        fuelEntryDao.deleteFuelEntry(entry)
    }

    suspend fun getAllIncludingDeleted(): List<FuelEntry> = fuelEntryDao.getAllIncludingDeleted()

    suspend fun findBySyncKey(originDeviceId: String, id: Long): FuelEntry? =
        fuelEntryDao.findBySyncKey(originDeviceId, id)

    suspend fun findBySyncId(syncId: String): FuelEntry? =
        fuelEntryDao.findBySyncId(syncId)

    /**
     * Sync path: preserve remote/local winner timestamps; do not stamp updatedAt to now.
     *
     * Cross-device identity is **[FuelEntry.syncId]** only. On insert (`existing == null`),
     * always use **`id = 0`** so Room auto-generates a **local** PK — never insert another
     * device’s Room id from the sheet (UNIQUE constraint crash).
     */
    suspend fun upsertFromSync(entry: FuelEntry) {
        val withSyncId = ensureSyncId(entry)
        val existing = fuelEntryDao.findBySyncId(withSyncId.syncId)
        val toWrite = if (existing != null) {
            val photoUrl = photoStorage.pickPreferredLocalPath(withSyncId.photoUrl, existing.photoUrl)
            withSyncId.copy(id = existing.id, photoUrl = photoUrl)
        } else {
            // Foreign sheet "ID" must not become Room PK
            withSyncId.copy(id = 0)
        }
        if (existing != null) {
            fuelEntryDao.updateFuelEntry(toWrite)
        } else {
            fuelEntryDao.insertFuelEntry(toWrite)
        }
    }

    // Legacy methods for existing legacy code
    suspend fun saveEntry(entry: FuelEntry) = insertFuelEntry(entry)

    fun getAllEntries() = getAllFuelEntries()

    suspend fun convertAllVolumes(fromUnit: String, toUnit: String) {
        if (fromUnit == toUnit) return
        val entries = fuelEntryDao.getAllFuelEntries().first()
        entries.forEach { entry ->
            val converted = VolumeUnits.convert(entry.gallons, fromUnit, toUnit)
            fuelEntryDao.updateFuelEntry(stampForWrite(entry.copy(gallons = converted)))
        }
    }

    private fun stampForWrite(entry: FuelEntry): FuelEntry {
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        val now = System.currentTimeMillis()
        return ensureSyncId(
            entry.copy(
                originDeviceId = entry.originDeviceId.ifBlank { deviceId },
                updatedAt = now,
            ),
        )
    }

    private fun ensureSyncId(entry: FuelEntry): FuelEntry =
        if (entry.syncId.isNotBlank()) entry
        else entry.copy(syncId = SyncIdGenerator.randomSyncId())
}