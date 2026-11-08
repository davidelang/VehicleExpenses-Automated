package com.davidlang.vehicleexpensesautomated.data.torque

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One local producer id for this phone. Not an account. */
@Entity(tableName = "torque_fill_producer")
data class TorqueFillProducerEntity(
    @PrimaryKey val id: Int = 1,
    val producerId: String,
)

/**
 * Highest revision allocated for a fuel [syncId].
 * An acknowledgement does not change this row.
 */
@Entity(tableName = "torque_fill_revision")
data class TorqueFillRevisionEntity(
    @PrimaryKey val syncId: String,
    val highWater: Long,
)

/**
 * One pending canonical payload for a fuel [syncId].
 * [held] waits for a later source change (conflict or invalid event).
 */
@Entity(tableName = "torque_fill_outbox")
data class TorqueFillOutboxEntity(
    @PrimaryKey val syncId: String,
    val revision: Long,
    val payloadHash: String,
    val canonicalPayload: String,
    val vehicleId: Int,
    val held: Boolean,
)
