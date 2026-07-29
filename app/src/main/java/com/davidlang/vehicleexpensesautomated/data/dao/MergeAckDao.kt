package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck

@Dao
interface MergeAckDao {

    @Query("SELECT * FROM merge_acks WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllLive(): List<MergeAck>

    @Query("SELECT * FROM merge_acks ORDER BY updatedAt DESC")
    suspend fun getAllIncludingDeleted(): List<MergeAck>

    @Query("SELECT * FROM merge_acks WHERE ackId = :ackId LIMIT 1")
    suspend fun findByAckId(ackId: String): MergeAck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ack: MergeAck)

    @Update
    suspend fun update(ack: MergeAck)
}
