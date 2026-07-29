package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.dao.MergeAckDao
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable merge/review acknowledgments backed by Room [MergeAck].
 *
 * - [acknowledge] records kind + member fuel syncIds (and optionally MERGE_EXEMPT).
 * - [filterPending] drops Stage C cards already acked.
 * - [liveMergeExemptSets] feeds [FuelRowMergeEngine.planMerge] so exempt clusters
 *   are not absorbed / do not emit CONFLICT.
 * - [upsertFromSync] is LWW by ackId for spreadsheet tab "Merge acks".
 */
@Singleton
class MergeAckStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: MergeAckDao,
) {
    companion object {
        private const val TAG = "MergeAckStore"

        /** extra keys on [BatchPendingItem] that may hold member fuel syncIds. */
        val MEMBER_SYNC_ID_EXTRA_KEYS = listOf(
            "entrySyncIds",
            "memberSyncIds",
            "syncIds",
        )
    }

    /**
     * Write a durable ack for [kind] covering [memberFuelSyncIds].
     * For CONFLICT_ODO / AMBIGUOUS_MULTI_PUMP, also writes MERGE_EXEMPT when
     * [alsoMergeExempt] is true (default) so re-merge will not absorb the pair.
     */
    suspend fun acknowledge(
        kind: String,
        memberFuelSyncIds: Collection<String>,
        alsoMergeExempt: Boolean = true,
    ) {
        val members = MergeAck.sortedMembersCsv(memberFuelSyncIds)
        if (members.isBlank()) {
            Log.w(TAG, "acknowledge ignored: empty members kind=$kind")
            return
        }
        val memberSet = members.split(',').toSet()
        upsertLocal(kind, memberSet)
        val needsExempt = alsoMergeExempt && (
            kind == MergeAck.KIND_CONFLICT_ODO ||
                kind == MergeAck.KIND_AMBIGUOUS_MULTI_PUMP ||
                kind == MergeAck.KIND_MERGE_EXEMPT
            )
        if (needsExempt && kind != MergeAck.KIND_MERGE_EXEMPT) {
            upsertLocal(MergeAck.KIND_MERGE_EXEMPT, memberSet)
        }
        // MERGE_EXEMPT-only path when caller already asked for MERGE_EXEMPT as kind
        if (kind == MergeAck.KIND_MERGE_EXEMPT) {
            // already upserted above
        }
    }

    private suspend fun upsertLocal(kind: String, memberSet: Set<String>) {
        val csv = MergeAck.sortedMembersCsv(memberSet)
        val live = dao.getAllLive()
        val existing = live.find {
            it.kind == kind && MergeAck.sortedMembersCsv(it.memberSet()) == csv
        }
        val now = System.currentTimeMillis()
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        if (existing != null) {
            dao.update(
                existing.copy(
                    updatedAt = now,
                    deleted = false,
                    deletedAt = null,
                    originDeviceId = existing.originDeviceId.ifBlank { deviceId },
                ),
            )
            Log.i(TAG, "ack refresh kind=$kind members=$csv ackId=${existing.ackId}")
        } else {
            val ack = MergeAck(
                ackId = SyncIdGenerator.randomSyncId(),
                kind = kind,
                memberSyncIds = csv,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                deletedAt = null,
                originDeviceId = deviceId,
            )
            dao.insert(ack)
            Log.i(TAG, "ack create kind=$kind members=$csv ackId=${ack.ackId}")
        }
    }

    suspend fun isAcked(kind: String, memberFuelSyncIds: Collection<String>): Boolean {
        val csv = MergeAck.sortedMembersCsv(memberFuelSyncIds)
        if (csv.isBlank()) return false
        return dao.getAllLive().any {
            it.kind == kind && MergeAck.sortedMembersCsv(it.memberSet()) == csv
        }
    }

    /** True if any live MERGE_EXEMPT set is a subset of [clusterSyncIds]. */
    suspend fun isMergeExemptCluster(clusterSyncIds: Collection<String>): Boolean {
        val cluster = clusterSyncIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (cluster.isEmpty()) return false
        return liveMergeExemptSets().any { exempt -> exempt.isNotEmpty() && exempt.all { it in cluster } }
    }

    suspend fun liveMergeExemptSets(): List<Set<String>> =
        dao.getAllLive()
            .filter { it.kind == MergeAck.KIND_MERGE_EXEMPT }
            .map { it.memberSet() }
            .filter { it.isNotEmpty() }

    /**
     * Drop pending whose kind+members fingerprint matches a live ack.
     * Members resolved from extra entrySyncIds/memberSyncIds/syncIds, else empty
     * (caller should enrich before filter when possible).
     */
    suspend fun filterPending(items: List<BatchPendingItem>): List<BatchPendingItem> {
        val live = dao.getAllLive()
        if (live.isEmpty()) return items
        val fingerprints = live.map { it.fingerprint() }.toSet()
        // Also index by kind → list of member sets for subset / exact match
        val byKind = live.groupBy { it.kind }
        return items.filter { item ->
            val members = pendingMemberSyncIds(item)
            if (members.isEmpty()) {
                true // cannot match without syncIds — keep (engine/UI should enrich)
            } else {
                val kindName = item.kind.name
                val fp = MergeAck.companionFingerprint(kindName, members)
                if (fp in fingerprints) {
                    Log.i(TAG, "filterPending drop exact $fp")
                    false
                } else {
                    // Also drop if any live ack of same kind has same sorted members
                    val sameKind = byKind[kindName].orEmpty()
                    val csv = MergeAck.sortedMembersCsv(members)
                    val hit = sameKind.any { MergeAck.sortedMembersCsv(it.memberSet()) == csv }
                    if (hit) {
                        Log.i(TAG, "filterPending drop kind=$kindName members=$csv")
                    }
                    !hit
                }
            }
        }
    }

    fun pendingMemberSyncIds(item: BatchPendingItem): Set<String> {
        for (key in MEMBER_SYNC_ID_EXTRA_KEYS) {
            val raw = item.extra[key] ?: continue
            val parts = raw.split(',', '|').map { it.trim() }.filter { it.isNotBlank() }
            if (parts.isNotEmpty()) return parts.toSet()
        }
        item.extra["syncId"]?.trim()?.takeIf { it.isNotBlank() }?.let { return setOf(it) }
        return emptySet()
    }

    /**
     * Sync path: preserve remote/local winner timestamps; LWW by ackId.
     */
    suspend fun upsertFromSync(ack: MergeAck) {
        if (ack.ackId.isBlank()) {
            Log.w(TAG, "upsertFromSync skip blank ackId")
            return
        }
        val existing = dao.findByAckId(ack.ackId)
        if (existing != null) {
            dao.update(ack.copy(ackId = existing.ackId))
        } else {
            dao.insert(ack)
        }
    }

    suspend fun getAllIncludingDeleted(): List<MergeAck> = dao.getAllIncludingDeleted()

    suspend fun getAllLive(): List<MergeAck> = dao.getAllLive()
}
