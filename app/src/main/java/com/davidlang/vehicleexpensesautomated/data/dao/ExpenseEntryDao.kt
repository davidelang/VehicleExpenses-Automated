package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExpenseEntry)

    @Update
    suspend fun update(entry: ExpenseEntry)

    @Query(
        """
        SELECT e.* FROM expense_entries e
        INNER JOIN vehicles v ON v.id = :vehicleId
        WHERE e.deleted = 0
        AND (
            e.vehicleId = :vehicleId
            OR (v.syncId != '' AND e.vehicleSyncIdsJson LIKE '%' || '"' || v.syncId || '"' || '%')
        )
        ORDER BY e.date DESC
        """,
    )
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<ExpenseEntry>>

    @Query("SELECT * FROM expense_entries WHERE deleted = 0 ORDER BY date DESC")
    fun getAllEntries(): Flow<List<ExpenseEntry>>

    @Query("SELECT * FROM expense_entries ORDER BY date DESC")
    suspend fun getAllIncludingDeleted(): List<ExpenseEntry>

    @Query("SELECT * FROM expense_entries WHERE originDeviceId = :originDeviceId AND id = :id LIMIT 1")
    suspend fun findBySyncKey(originDeviceId: String, id: Long): ExpenseEntry?

    @Query("SELECT * FROM expense_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): ExpenseEntry?

    @Query("SELECT * FROM expense_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntry?
}
