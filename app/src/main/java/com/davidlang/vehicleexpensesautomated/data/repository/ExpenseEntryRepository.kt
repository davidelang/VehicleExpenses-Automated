package com.davidlang.vehicleexpensesautomated.data.repository

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpensePhotoUrls
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseEntryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ExpenseEntryDao,
    private val vehicleRepository: VehicleRepository,
    private val photoStorage: PhotoStorageManager,
) {
    suspend fun saveEntry(entry: ExpenseEntry) = dao.insert(stampForWrite(entry))

    fun getEntriesForVehicle(vehicleId: Int): Flow<List<ExpenseEntry>> = dao.getEntriesForVehicle(vehicleId)

    fun getAllEntries(): Flow<List<ExpenseEntry>> = dao.getAllEntries()

    suspend fun getById(id: Long): ExpenseEntry? = dao.getById(id)

    suspend fun updateExpenseEntry(entry: ExpenseEntry) = dao.update(stampForWrite(entry))

    /**
     * Manifest / photoUrl bookkeeping must not bump [ExpenseEntry.updatedAt] or LWW can spuriously win.
     */
    suspend fun updateExpenseEntryPreservingTimestamp(entry: ExpenseEntry) =
        dao.update(ensureSyncId(entry))

    suspend fun markExpenseDeleted(entry: ExpenseEntry) {
        val now = System.currentTimeMillis()
        dao.update(stampForWrite(entry).copy(deleted = true, deletedAt = now))
    }

    suspend fun getAllIncludingDeleted(): List<ExpenseEntry> = dao.getAllIncludingDeleted()

    suspend fun findBySyncKey(originDeviceId: String, id: Long): ExpenseEntry? =
        dao.findBySyncKey(originDeviceId, id)

    suspend fun findBySyncId(syncId: String): ExpenseEntry? =
        dao.findBySyncId(syncId)

    /** Sync path: preserve remote/local winner timestamps; do not stamp updatedAt to now. */
    suspend fun upsertFromSync(entry: ExpenseEntry) {
        val withSyncId = ensureSyncId(entry)
        val existing = dao.findBySyncId(withSyncId.syncId)
        val toWrite = if (existing != null) {
            val photoUrl = ExpensePhotoUrls.mergePreferredReadable(
                withSyncId.photoUrl,
                existing.photoUrl,
                photoStorage::isLocalReadable,
            )
            withSyncId.copy(id = existing.id, photoUrl = photoUrl)
        } else {
            withSyncId
        }
        if (existing != null) {
            dao.update(toWrite)
        } else {
            dao.insert(toWrite)
        }
    }

    // Added for ExpenseViewModel compatibility
    suspend fun insertExpenseEntry(entry: ExpenseEntry) = saveEntry(entry)

    private suspend fun stampForWrite(entry: ExpenseEntry): ExpenseEntry {
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        val now = System.currentTimeMillis()
        return ensureSyncId(
            ensureVehicleSyncIdsJson(
                entry.copy(
                    originDeviceId = entry.originDeviceId.ifBlank { deviceId },
                    updatedAt = now,
                    receiptImagePath = null,
                ),
            ),
        )
    }

    /** Single-vehicle save path: one-element JSON from primary vehicle syncId. */
    private suspend fun ensureVehicleSyncIdsJson(entry: ExpenseEntry): ExpenseEntry {
        val existing = ExpenseVehicleSyncIds.parse(entry.vehicleSyncIdsJson)
        if (existing.isNotEmpty()) return entry
        val vehicle = vehicleRepository.getVehicleById(entry.vehicleId) ?: return entry
        val syncId = vehicle.syncId.takeIf { it.isNotBlank() } ?: return entry
        return entry.copy(vehicleSyncIdsJson = ExpenseVehicleSyncIds.format(listOf(syncId)))
    }

    private fun ensureSyncId(entry: ExpenseEntry): ExpenseEntry =
        if (entry.syncId.isNotBlank()) entry
        else entry.copy(syncId = SyncIdGenerator.randomSyncId())
}