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
import com.davidlang.vehicleexpensesautomated.data.batch.MergeAckStore.Companion.TAG


    /**
     * Write a durable ack for [kind] covering [memberFuelSyncIds].
     * For CONFLICT_ODO / AMBIGUOUS_MULTI_PUMP, also writes MERGE_EXEMPT when
     * [alsoMergeExempt] is true (default) so re-merge will not absorb the pair.
     */
    suspend fun MergeAckStore.acknowledge(
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


    private suspend fun MergeAckStore.upsertLocal(kind: String, memberSet: Set<String>) {
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

