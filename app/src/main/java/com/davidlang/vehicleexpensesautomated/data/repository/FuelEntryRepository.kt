package com.davidlang.vehicleexpensesautomated.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.TorqueFillDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillDelivery
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillOutboxEntity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillPayload
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillProducerEntity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillRevisionEntity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FuelEntryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val fuelEntryDao: FuelEntryDao,
    private val vehicleDao: VehicleDao,
    private val torqueFillDao: TorqueFillDao,
    private val photoStorage: PhotoStorageManager,
    private val torqueFillDelivery: TorqueFillDelivery,
) {

    fun getAllFuelEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllFuelEntries()

    fun getEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>> = fuelEntryDao.getFuelEntriesForVehicle(vehicleId)

    suspend fun getById(id: Long): FuelEntry? = fuelEntryDao.getById(id)

    /** Latest non-zero **tracking** odometer on the fill table for this vehicle. */
    suspend fun getLastOdometerForVehicle(vehicleId: Int): Int? =
        fuelEntryDao.getLastOdometerForVehicle(vehicleId)

    suspend fun insertFuelEntry(entry: FuelEntry) {
        val stamped = stampForWrite(entry)
        val allocated = database.withTransaction {
            writeStamped(stamped, existing = null)
        }
        if (allocated) torqueFillDelivery.kick()
    }

    suspend fun updateFuelEntry(entry: FuelEntry) {
        val stamped = stampForWrite(entry)
        val allocated = database.withTransaction {
            val existing = findExisting(stamped)
            if (existing == null) {
                fuelEntryDao.updateFuelEntry(stamped)
                false
            } else {
                writeStamped(stamped.copy(id = existing.id), existing)
            }
        }
        if (allocated) torqueFillDelivery.kick()
    }

    /**
     * Manifest / photoUrl bookkeeping must not bump [FuelEntry.updatedAt] or LWW can spuriously win.
     * It also does not allocate a fill revision.
     */
    suspend fun updateFuelEntryPreservingTimestamp(entry: FuelEntry) {
        val allocated = database.withTransaction {
            val row = ensureSyncId(entry)
            val existing = findExisting(row)
            if (existing == null) {
                fuelEntryDao.updateFuelEntry(row)
                false
            } else {
                writeStamped(row.copy(id = existing.id), existing)
            }
        }
        if (allocated) torqueFillDelivery.kick()
    }

    suspend fun deleteFuelEntry(entry: FuelEntry) = markFuelDeleted(entry)

    suspend fun markFuelDeleted(entry: FuelEntry) {
        val now = System.currentTimeMillis()
        val stamped = stampForWrite(entry).copy(deleted = true, deletedAt = now)
        val allocated = database.withTransaction {
            val existing = findExisting(stamped)
            if (existing == null) {
                fuelEntryDao.updateFuelEntry(stamped)
                false
            } else {
                writeStamped(stamped.copy(id = existing.id), existing)
            }
        }
        if (allocated) torqueFillDelivery.kick()
    }

    /**
     * Hard-delete a local fuel row (Room `@Delete`). Used by batch merge when
     * absorbing partials during initial testing (sync off). Normal UI continues
     * to soft-delete via [deleteFuelEntry] / [markFuelDeleted].
     * A donor that already has a revision is voided in this same transaction.
     */
    suspend fun hardDeleteFuelEntry(entry: FuelEntry) {
        val allocated = database.withTransaction { hardDeleteInside(entry) }
        if (allocated) torqueFillDelivery.kick()
    }

    /**
     * Survivor update and donor delete, one transaction. The donor is voided only
     * when that sync id already has a revision and its vehicle is still opted in.
     */
    suspend fun applyMerge(survivor: FuelEntry, donor: FuelEntry, hardDeleteDonor: Boolean) {
        val allocated = database.withTransaction {
            val survivorStamped = stampForWrite(survivor)
            val existing = findExisting(survivorStamped)
            val wrote = if (existing == null) {
                writeStamped(survivorStamped, null)
            } else {
                writeStamped(survivorStamped.copy(id = existing.id), existing)
            }
            val removed = if (hardDeleteDonor) {
                hardDeleteInside(donor)
            } else {
                val now = System.currentTimeMillis()
                val donorStamped = stampForWrite(donor).copy(deleted = true, deletedAt = now)
                val donorExisting = findExisting(donorStamped)
                if (donorExisting == null) {
                    fuelEntryDao.updateFuelEntry(donorStamped)
                    false
                } else {
                    writeStamped(donorStamped.copy(id = donorExisting.id), donorExisting)
                }
            }
            wrote || removed
        }
        if (allocated) torqueFillDelivery.kick()
    }

    /** Pending copies for this vehicle go away. The revision high-water stays. */
    suspend fun dropPendingForVehicle(vehicleId: Int) {
        database.withTransaction {
            torqueFillDao.deleteOutboxForVehicle(vehicleId)
        }
    }

    suspend fun getAllIncludingDeleted(): List<FuelEntry> = fuelEntryDao.getAllIncludingDeleted()

    suspend fun pageLiveNewest(limit: Int, offset: Int): List<FuelEntry> =
        fuelEntryDao.pageLiveNewest(limit, offset)

    suspend fun pageAllNewest(limit: Int, offset: Int): List<FuelEntry> =
        fuelEntryDao.pageAllNewest(limit, offset)

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
        val allocated = database.withTransaction {
            val withSyncId = ensureSyncId(entry)
            val existing = fuelEntryDao.findBySyncId(withSyncId.syncId)
            val toWrite = if (existing != null) {
                val photoUrl = photoStorage.pickPreferredLocalPath(withSyncId.photoUrl, existing.photoUrl)
                TorqueFillRules.keepSources(existing, withSyncId.copy(id = existing.id, photoUrl = photoUrl))
            } else {
                withSyncId.copy(id = 0)
            }
            writeStamped(toWrite, existing)
        }
        if (allocated) torqueFillDelivery.kick()
    }

    // Legacy methods for existing legacy code
    suspend fun saveEntry(entry: FuelEntry) = insertFuelEntry(entry)

    fun getAllEntries() = getAllFuelEntries()

    private suspend fun writeStamped(row: FuelEntry, existing: FuelEntry?): Boolean {
        val changed = existing == null || !TorqueFillRules.sameFill(existing, row)
        if (existing == null) fuelEntryDao.insertFuelEntry(row) else fuelEntryDao.updateFuelEntry(row)
        return allocate(before = existing, after = row, changed = changed, hardRemoved = false)
    }

    private suspend fun hardDeleteInside(entry: FuelEntry): Boolean {
        val existing = findExisting(entry) ?: return false
        fuelEntryDao.deleteFuelEntry(existing)
        return allocate(before = existing, after = existing, changed = true, hardRemoved = true)
    }

    private suspend fun allocate(
        before: FuelEntry?,
        after: FuelEntry,
        changed: Boolean,
        hardRemoved: Boolean,
    ): Boolean {
        val syncId = after.syncId
        if (syncId.isBlank()) return false
        val vehicle = vehicleDao.getByIdOnce(after.vehicleId)
        val eligible = TorqueFillRules.eligible(vehicle)
        val high = torqueFillDao.revision(syncId)?.highWater ?: 0L
        val decision = TorqueFillRules.decide(
            changed = changed || before == null && !hardRemoved,
            eligible = eligible,
            highWater = high,
            deleted = after.deleted,
            trip = after.tripType.isNotBlank(),
            hardRemoved = hardRemoved,
        )
        if (decision.kind == TorqueFillRules.Kind.NONE) {
            if (!eligible) torqueFillDao.deleteOutbox(syncId)
            return false
        }
        val next = high + 1L
        if (next <= 0L) {
            Log.i(TAG, "fill revision exhausted")
            return false
        }
        val described = vehicle ?: return false
        ensureProducer()
        val frozen = if (decision.kind == TorqueFillRules.Kind.VOID) {
            TorqueFillPayload.void(after, described, decision.voidReason ?: "deleted", BuildConfig.VERSION_NAME)
        } else {
            TorqueFillPayload.upsert(after, described, BuildConfig.VERSION_NAME)
        }
        torqueFillDao.upsertRevision(TorqueFillRevisionEntity(syncId = syncId, highWater = next))
        torqueFillDao.upsertOutbox(
            TorqueFillOutboxEntity(
                syncId = syncId,
                revision = next,
                payloadHash = frozen.hash,
                canonicalPayload = frozen.canonical,
                vehicleId = after.vehicleId,
                held = false,
            ),
        )
        return true
    }

    private suspend fun ensureProducer() {
        if (torqueFillDao.producer() != null) return
        torqueFillDao.upsertProducer(TorqueFillProducerEntity(id = 1, producerId = UUID.randomUUID().toString()))
    }

    private suspend fun findExisting(entry: FuelEntry): FuelEntry? {
        if (entry.id != 0L) {
            val byId = fuelEntryDao.getById(entry.id)
            if (byId != null) return byId
        }
        if (entry.syncId.isNotBlank()) return fuelEntryDao.findBySyncId(entry.syncId)
        return null
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

    private companion object {
        const val TAG = "TorqueFill"
    }
}
