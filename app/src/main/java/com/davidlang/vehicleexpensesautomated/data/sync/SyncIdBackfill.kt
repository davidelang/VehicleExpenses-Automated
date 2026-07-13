package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncIdBackfill @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicleDao: VehicleDao,
    private val fuelEntryDao: FuelEntryDao,
    private val expenseEntryDao: ExpenseEntryDao,
) {

    fun isBackfillDone(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_SYNC_ID_BACKFILL_DONE, false)
    }

    suspend fun runIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_SYNC_ID_BACKFILL_DONE, false)) return@withContext

        vehicleDao.getAllIncludingDeleted().forEach { vehicle ->
            if (vehicle.syncId.isBlank()) {
                vehicleDao.updateVehicle(
                    vehicle.copy(syncId = SyncIdGenerator.deterministicVehicleSyncId(vehicle)),
                )
            }
        }
        fuelEntryDao.getAllIncludingDeleted().forEach { entry ->
            if (entry.syncId.isBlank()) {
                fuelEntryDao.updateFuelEntry(
                    entry.copy(syncId = SyncIdGenerator.deterministicFuelSyncId(entry)),
                )
            }
        }
        expenseEntryDao.getAllIncludingDeleted().forEach { entry ->
            if (entry.syncId.isBlank()) {
                expenseEntryDao.update(
                    entry.copy(syncId = SyncIdGenerator.deterministicExpenseSyncId(entry)),
                )
            }
        }

        prefs.edit().putBoolean(PREF_SYNC_ID_BACKFILL_DONE, true).apply()
    }

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val PREF_SYNC_ID_BACKFILL_DONE = "sync_id_backfill_done"
    }
}