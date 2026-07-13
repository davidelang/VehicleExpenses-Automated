package com.davidlang.vehicleexpensesautomated.data.model

import org.json.JSONArray

/** JSON array helpers for [ExpenseEntry.vehicleSyncIdsJson]. */
object ExpenseVehicleSyncIds {

    fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i, "").trim()
                    if (id.isNotBlank()) add(id)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun format(syncIds: List<String>): String {
        val normalized = syncIds.map { it.trim() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return ""
        val arr = JSONArray()
        for (id in normalized) arr.put(id)
        return arr.toString()
    }

    fun primarySyncId(json: String?): String? = parse(json).firstOrNull()

    fun containsSyncId(json: String?, syncId: String): Boolean {
        if (syncId.isBlank()) return false
        return parse(json).contains(syncId)
    }

    /**
     * Resolve primary [ExpenseEntry.vehicleId] from first syncId and persist full JSON list.
     */
    fun applyResolvedVehicles(
        entry: ExpenseEntry,
        vehicleSyncIds: List<String>,
        vehicleIdBySyncId: Map<String, Int>,
    ): ExpenseEntry {
        val primarySyncId = vehicleSyncIds.firstOrNull().orEmpty()
        val resolvedId = primarySyncId.takeIf { it.isNotBlank() }?.let { vehicleIdBySyncId[it] }
        val vehicleId = resolvedId ?: entry.vehicleId
        val json = if (vehicleSyncIds.isNotEmpty()) format(vehicleSyncIds) else entry.vehicleSyncIdsJson
        return entry.copy(vehicleId = vehicleId, vehicleSyncIdsJson = json)
    }
}