package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable user acknowledgment that a merge/review situation is correct and
 * should not be re-raised (Stage C pending) and/or should not field-merge
 * members of an exempt set.
 *
 * Identity fingerprint: [kind] + sorted member fuel [syncId]s (not Room ids).
 * Survives rescan, phase advance, restart, and spreadsheet sync (tab "Merge acks").
 */
@Entity(tableName = "merge_acks")
data class MergeAck(
    /** Stable cross-device key (UUID). Sheet LWW key. */
    @PrimaryKey val ackId: String,
    /**
     * One of [KIND_CONFLICT_ODO], [KIND_AMBIGUOUS_MULTI_PUMP], [KIND_MPG_OUTLIER],
     * [KIND_MERGE_EXEMPT].
     */
    val kind: String,
    /** Sorted CSV of fuel entry syncIds that this ack covers. */
    val memberSyncIds: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val originDeviceId: String = "",
) {
    fun memberSet(): Set<String> =
        memberSyncIds.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun fingerprint(): String = companionFingerprint(kind, memberSet())

    companion object {
        const val KIND_CONFLICT_ODO = "CONFLICT_ODO"
        const val KIND_AMBIGUOUS_MULTI_PUMP = "AMBIGUOUS_MULTI_PUMP"
        const val KIND_MPG_OUTLIER = "MPG_OUTLIER"
        /** Field-merge suppress: members must not auto-absorb into each other. */
        const val KIND_MERGE_EXEMPT = "MERGE_EXEMPT"

        fun sortedMembersCsv(syncIds: Collection<String>): String =
            syncIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                .joinToString(",")

        fun companionFingerprint(kind: String, syncIds: Collection<String>): String =
            kind.trim() + "|" + sortedMembersCsv(syncIds)
    }
}
