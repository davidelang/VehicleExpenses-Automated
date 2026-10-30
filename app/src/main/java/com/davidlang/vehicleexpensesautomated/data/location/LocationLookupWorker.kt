package com.davidlang.vehicleexpensesautomated.data.location

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Deferred POI fill for rows with lat/lon but blank place.
 * Newest-first, 1–3 per entity type per run, silent `confirmed: false`.
 */
@HiltWorker
class LocationLookupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val fuelRepository: FuelEntryRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val knownStationStore: KnownStationStore,
    private val dbState: DatabaseHealthState,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        databaseGate(dbState, appContext)?.let { return@withContext it }
        try {
            var anyFail = false
            val fuelPending = pageFuelPending(MAX_PER_RUN)
            for (entry in fuelPending) {
                val blob = FuelLocationJson.parseBlob(entry.location) ?: continue
                val lat = blob.lat ?: continue
                val lon = blob.lon ?: continue
                // Trip starts (and address_only blobs) → Nominatim only; normal fills → gas station.
                val kind = when {
                    entry.tripType.isNotBlank() -> LocationLookupKind.ADDRESS_ONLY
                    blob.kind == "address_only" -> LocationLookupKind.ADDRESS_ONLY
                    else -> LocationLookupKind.FUEL_STATION
                }
                Log.i(TAG, "Fuel id=${entry.id} kind=$kind tripType=${entry.tripType}")
                val result = LocationLookup.lookup(
                    lat = lat,
                    lon = lon,
                    kind = kind,
                    accuracyM = blob.accuracyM,
                    uiTimeout = false,
                    stationStore = if (kind == LocationLookupKind.FUEL_STATION) knownStationStore else null,
                )
                if (result != null && result.hasPlace()) {
                    val updated = LocationLookup.mergePlaceIntoBlob(entry.location, result, confirmed = false)
                    fuelRepository.updateFuelEntryPreservingTimestamp(entry.copy(location = updated))
                    Log.i(TAG, "Filled fuel id=${entry.id} place=${result.displayLine()}")
                } else {
                    Log.i(TAG, "No place for fuel id=${entry.id}")
                    anyFail = true
                }
                delay(POLITE_DELAY_MS)
            }

            val expensePending = pageExpensePending(MAX_PER_RUN)
            for (entry in expensePending) {
                val blob = FuelLocationJson.parseBlob(entry.location) ?: continue
                val lat = blob.lat ?: continue
                val lon = blob.lon ?: continue
                val kind = LocationLookup.kindForExpenseCategory(entry.category)
                val result = LocationLookup.lookup(
                    lat = lat,
                    lon = lon,
                    kind = kind,
                    accuracyM = blob.accuracyM,
                    uiTimeout = false,
                )
                if (result != null && result.hasPlace()) {
                    val updated = LocationLookup.mergePlaceIntoBlob(entry.location, result, confirmed = false)
                    // Prefer silent bookkeeping update if repo has preserving API
                    expenseRepository.updateExpenseEntryPreservingTimestamp(entry.copy(location = updated))
                    Log.i(TAG, "Filled expense id=${entry.id} place=${result.displayLine()}")
                } else {
                    Log.i(TAG, "No place for expense id=${entry.id}")
                    anyFail = true
                }
                delay(POLITE_DELAY_MS)
            }
            if (anyFail && (fuelPending.isNotEmpty() || expensePending.isNotEmpty())) {
                Result.retry()
            } else {
                Result.success()
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            Log.e(TAG, "LocationLookupWorker failed", e)
            Result.retry()
        }
    }

    private suspend fun pageFuelPending(max: Int) = pagePending(max) { offset, limit ->
        fuelRepository.pageLiveNewest(limit, offset)
    }

    private suspend fun pageExpensePending(max: Int) = pagePending(max) { offset, limit ->
        expenseRepository.pageLiveNewest(limit, offset)
    }

    private suspend fun <T> pagePending(
        max: Int,
        load: suspend (offset: Int, limit: Int) -> List<T>,
    ): List<T> where T : Any {
        val out = ArrayList<T>(max)
        var offset = 0
        val page = 40
        while (out.size < max) {
            val rows = load(offset, page)
            if (rows.isEmpty()) break
            for (row in rows) {
                val location = when (row) {
                    is com.davidlang.vehicleexpensesautomated.data.model.FuelEntry -> row.location
                    is com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry -> row.location
                    else -> null
                }
                if (location != null && FuelLocationJson.hasCoordsWithoutPlace(location)) {
                    out.add(row)
                    if (out.size == max) break
                }
            }
            offset += rows.size
            if (rows.size < page) break
        }
        return out
    }

    private fun databaseGate(dbState: DatabaseHealthState, appContext: Context): Result? {
        val app = appContext.applicationContext as VehicleExpensesApplication
        var health = dbState.states.value
        if (health is Unchecked) {
            app.verifyDatabase()
            health = dbState.states.value
        }
        return when (health) {
            is Failed -> {
                Log.i(TAG, "database failed; failure")
                Result.failure()
            }
            is Exporting, is Importing -> {
                Log.i(TAG, "transfer in progress; retry")
                Result.retry()
            }
            Unchecked -> Result.retry()
            Ok -> null
        }
    }

    companion object {
        private const val TAG = "LocationLookupWorker"
        private const val MAX_PER_RUN = 3
        private const val POLITE_DELAY_MS = 1_200L
    }
}
