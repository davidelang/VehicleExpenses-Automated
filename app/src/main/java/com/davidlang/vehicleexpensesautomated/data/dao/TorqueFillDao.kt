package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillOutboxEntity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillProducerEntity
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillRevisionEntity

@Dao
interface TorqueFillDao {
    @Query("SELECT * FROM torque_fill_producer WHERE id = 1")
    suspend fun producer(): TorqueFillProducerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducer(row: TorqueFillProducerEntity)

    @Query("SELECT * FROM torque_fill_revision WHERE syncId = :syncId")
    suspend fun revision(syncId: String): TorqueFillRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRevision(row: TorqueFillRevisionEntity)

    @Query("SELECT * FROM torque_fill_outbox WHERE syncId = :syncId")
    suspend fun outbox(syncId: String): TorqueFillOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(row: TorqueFillOutboxEntity)

    @Query("DELETE FROM torque_fill_outbox WHERE syncId = :syncId")
    suspend fun deleteOutbox(syncId: String)

    @Query("DELETE FROM torque_fill_outbox WHERE vehicleId = :vehicleId")
    suspend fun deleteOutboxForVehicle(vehicleId: Int)

    @Query("SELECT * FROM torque_fill_outbox WHERE held = 0 ORDER BY syncId LIMIT :limit")
    suspend fun pending(limit: Int): List<TorqueFillOutboxEntity>

    @Query("SELECT * FROM torque_fill_outbox WHERE held = 1 ORDER BY syncId LIMIT :limit")
    suspend fun held(limit: Int): List<TorqueFillOutboxEntity>

    @Query(
        """
        DELETE FROM torque_fill_outbox
        WHERE syncId = :syncId AND revision = :revision AND payloadHash = :payloadHash
        """,
    )
    suspend fun deleteExact(syncId: String, revision: Long, payloadHash: String): Int

    @Query(
        """
        UPDATE torque_fill_outbox SET held = 1
        WHERE syncId = :syncId AND revision = :revision AND payloadHash = :payloadHash AND held = 0
        """,
    )
    suspend fun holdExact(syncId: String, revision: Long, payloadHash: String): Int
}
