package com.davidlang.vehicleexpensesautomated.data.location

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
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
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            var anyFail = false
            val fuelPending = fuelRepository.getAllIncludingDeleted()
                .filter { !it.deleted && FuelLocationJson.hasCoordsWithoutPlace(it.location) }
                .sortedWith(compareByDescending<com.davidlang.vehicleexpensesautomated.data.model.FuelEntry> { it.timestamp }.thenByDescending { it.id })
                .take(MAX_PER_RUN)
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

            val expensePending = expenseRepository.getAllIncludingDeleted()
                .filter { !it.deleted && FuelLocationJson.hasCoordsWithoutPlace(it.location) }
                .sortedWith(compareByDescending<com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry> { it.date }.thenByDescending { it.id })
                .take(MAX_PER_RUN)
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
        } catch (e: Exception) {
            Log.e(TAG, "LocationLookupWorker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LocationLookupWorker"
        private const val MAX_PER_RUN = 3
        private const val POLITE_DELAY_MS = 1_200L
    }
}
