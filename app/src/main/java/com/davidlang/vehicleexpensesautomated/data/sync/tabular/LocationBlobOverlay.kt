package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * VE-domain **location JSON** merge after library full-row LWW.
 *
 * remotetable [MergeSync] picks one entire row (including the Location cell).
 * Product still needs [FuelLocationJson.mergeBlobs] (confirmed place, coords, etc.).
 * Same pattern as [VehicleDefinitionOverlay]: apply after LWW, before Room upsert.
 */
object LocationBlobOverlay {

    /**
     * Merge location blobs from pre-merge local + remote onto the LWW [winner].
     * If only one side exists, returns [winner] unchanged.
     */
    fun applyFuel(
        winner: FuelEntry,
        local: FuelEntry?,
        remote: FuelEntry?,
    ): FuelEntry {
        if (local == null || remote == null) return winner
        val mergedLoc = FuelLocationJson.mergeBlobs(
            local.location,
            remote.location,
            updatedAtA = local.updatedAt,
            updatedAtB = remote.updatedAt,
        )
        return if (mergedLoc == winner.location) winner else winner.copy(location = mergedLoc)
    }

    fun applyExpense(
        winner: ExpenseEntry,
        local: ExpenseEntry?,
        remote: ExpenseEntry?,
    ): ExpenseEntry {
        if (local == null || remote == null) return winner
        val mergedLoc = FuelLocationJson.mergeBlobs(
            local.location,
            remote.location,
            updatedAtA = local.updatedAt,
            updatedAtB = remote.updatedAt,
        )
        return if (mergedLoc == winner.location) winner else winner.copy(location = mergedLoc)
    }

    fun applyToFuelList(
        winners: List<FuelEntry>,
        localBySyncId: Map<String, FuelEntry>,
        remoteBySyncId: Map<String, FuelEntry>,
    ): List<FuelEntry> =
        winners.map { w ->
            if (w.syncId.isBlank()) w
            else applyFuel(w, localBySyncId[w.syncId], remoteBySyncId[w.syncId])
        }

    fun applyToExpenseList(
        winners: List<ExpenseEntry>,
        localBySyncId: Map<String, ExpenseEntry>,
        remoteBySyncId: Map<String, ExpenseEntry>,
    ): List<ExpenseEntry> =
        winners.map { w ->
            if (w.syncId.isBlank()) w
            else applyExpense(w, localBySyncId[w.syncId], remoteBySyncId[w.syncId])
        }
}
