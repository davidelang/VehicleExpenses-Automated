package com.davidlang.vehicleexpensesautomated.data.torque

import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.TorqueFillDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends pending outbox rows. A call already in the binder is not recalled.
 * A late acknowledgement that does not match a still-pending row changes nothing.
 */
@Singleton
class TorqueFillDelivery @Inject constructor(
    private val dao: TorqueFillDao,
    private val vehicleDao: VehicleDao,
    private val fuelEntryDao: FuelEntryDao,
    private val client: TorqueFillBridgeClient,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "torque-fill-delivery").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val again = AtomicBoolean(false)

    fun kick() {
        if (running.compareAndSet(false, true)) {
            executor.execute { drain() }
        } else {
            again.set(true)
        }
    }

    private fun drain() {
        try {
            runBlocking { recoverHeldInvalidEvents() }
            do {
                again.set(false)
                flush()
            } while (again.get())
        } catch (e: Exception) {
            Log.i(TAG, "fill delivery stopped")
        } finally {
            running.set(false)
            if (again.compareAndSet(true, false)) kick()
        }
    }

    /**
     * One-time rewrite of retained INVALID_EVENT rows frozen with the old
     * odometerUnit wire. CONFLICT rows and vehicles that are no longer opted-in
     * are left alone. A new revision is allocated so the plugin does not see
     * the same revision with a different hash.
     */
    private suspend fun recoverHeldInvalidEvents() {
        for (row in dao.held(256)) {
            if (!TorqueFillRules.needsOldWireRecovery(row.canonicalPayload)) continue
            val vehicle = vehicleDao.getByIdOnce(row.vehicleId)
            if (!TorqueFillRules.eligible(vehicle) || vehicle == null) continue
            val entry = fuelEntryDao.findBySyncId(row.syncId) ?: continue
            val high = dao.revision(row.syncId)?.highWater ?: 0L
            val decision = TorqueFillRules.decide(
                changed = true,
                eligible = true,
                highWater = high,
                deleted = entry.deleted,
                trip = entry.tripType.isNotBlank(),
                hardRemoved = false,
            )
            if (decision.kind == TorqueFillRules.Kind.NONE) continue
            val next = maxOf(high, row.revision) + 1L
            if (next <= 0L) continue
            val frozen = if (decision.kind == TorqueFillRules.Kind.VOID) {
                TorqueFillPayload.void(entry, vehicle, decision.voidReason ?: "deleted", BuildConfig.VERSION_NAME)
            } else {
                TorqueFillPayload.upsert(entry, vehicle, BuildConfig.VERSION_NAME)
            }
            dao.upsertRevision(TorqueFillRevisionEntity(syncId = row.syncId, highWater = next))
            dao.upsertOutbox(
                TorqueFillOutboxEntity(
                    syncId = row.syncId,
                    revision = next,
                    payloadHash = frozen.hash,
                    canonicalPayload = frozen.canonical,
                    vehicleId = entry.vehicleId,
                    held = false,
                ),
            )
        }
    }

    private fun flush() {
        var guard = 0
        while (guard++ < 32) {
            val rows = runBlocking { readyRows() }
            if (rows.isEmpty()) return
            val producer = runBlocking { dao.producer() }?.producerId ?: return
            val batchId = UUID.randomUUID().toString()
            val chosen = ArrayList<TorqueFillOutboxEntity>()
            for (row in rows) {
                if (!payloadParses(row)) {
                    runBlocking { dao.holdExact(row.syncId, row.revision, row.payloadHash) }
                    Log.i(TAG, "fill payload unreadable")
                    continue
                }
                val trial = ArrayList(chosen)
                trial.add(row)
                val size = envelope(producer, batchId, trial).toByteArray(Charsets.UTF_8).size
                if (size > MAX_BYTES) {
                    if (chosen.isEmpty()) {
                        runBlocking { dao.holdExact(row.syncId, row.revision, row.payloadHash) }
                        Log.i(TAG, "fill event too large")
                    }
                    break
                }
                chosen.add(row)
                if (chosen.size == MAX_EVENTS) break
            }
            if (chosen.isEmpty()) return
            val response = client.submit(envelope(producer, batchId, chosen)) ?: return
            val parsed = parseResponse(response) ?: return
            if (parsed.status != "OK") return
            val moved = runBlocking {
                var n = 0
                for (item in parsed.results) {
                    when (item.status) {
                        TorqueFillRules.ACCEPTED, TorqueFillRules.DUPLICATE, TorqueFillRules.STALE ->
                            n += dao.deleteExact(item.syncId, item.revision, item.hash)
                        TorqueFillRules.CONFLICT, TorqueFillRules.INVALID_EVENT ->
                            n += dao.holdExact(item.syncId, item.revision, item.hash)
                    }
                }
                n
            }
            if (moved == 0) return
        }
    }

    private suspend fun readyRows(): List<TorqueFillOutboxEntity> {
        val out = ArrayList<TorqueFillOutboxEntity>()
        for (row in dao.pending(64)) {
            val vehicle = vehicleDao.getByIdOnce(row.vehicleId)
            if (!TorqueFillRules.eligible(vehicle)) {
                dao.deleteOutbox(row.syncId)
            } else {
                out.add(row)
            }
        }
        return out
    }

    private fun payloadParses(row: TorqueFillOutboxEntity): Boolean = try {
        TorqueFillJson.parse(row.canonicalPayload)
        true
    } catch (e: Exception) {
        false
    }

    private fun envelope(producer: String, batchId: String, rows: List<TorqueFillOutboxEntity>): String {
        val events = rows.map { row ->
            mapOf(
                "eventSyncId" to row.syncId,
                "revision" to row.revision,
                "payloadHash" to row.payloadHash,
                "payload" to TorqueFillJson.parse(row.canonicalPayload),
            )
        }
        return TorqueFillJson.canonical(
            mapOf(
                "schemaVersion" to 1L,
                "producer" to producer,
                "batchId" to batchId,
                "events" to events,
            ),
        )
    }

    private data class Item(val syncId: String, val revision: Long, val hash: String, val status: String)
    private data class Response(val status: String, val results: List<Item>)

    private fun parseResponse(text: String): Response? {
        val root = try {
            TorqueFillJson.parse(text) as? Map<*, *>
        } catch (e: Exception) {
            null
        } ?: return null
        val status = root["envelopeStatus"] as? String ?: return null
        val results = root["results"] as? List<*> ?: return null
        val items = ArrayList<Item>()
        for (row in results) {
            val map = row as? Map<*, *> ?: return null
            val syncId = map["eventSyncId"] as? String ?: return null
            val revision = map["revision"] as? Long ?: return null
            val hash = map["payloadHash"] as? String ?: return null
            val itemStatus = map["status"] as? String ?: return null
            items.add(Item(syncId, revision, hash, itemStatus))
        }
        return Response(status, items)
    }

    private companion object {
        const val TAG = "TorqueFill"
        const val MAX_BYTES = 32768
        const val MAX_EVENTS = 16
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TorqueFillDeliveryEntryPoint {
    fun torqueFillDelivery(): TorqueFillDelivery
}
