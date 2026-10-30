package com.davidlang.vehicleexpensesautomated.data.model

import org.json.JSONArray
import com.davidlang.vehicleexpensesautomated.data.model.StoredParse

/** JSON array helpers for [ExpenseEntry.vehicleSyncIdsJson]. */
object ExpenseVehicleSyncIds {

    fun read(json: String?): StoredParse<List<String>> {
        if (json.isNullOrBlank()) return StoredParse.Blank()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) {
            if (trimmed.startsWith("{")) return StoredParse.Corrupt(trimmed)
            return StoredParse.Valid(listOf(trimmed))
        }
        return try {
            val arr = JSONArray(trimmed)
            val out = buildList {
                for (i in 0 until arr.length()) {
                    if (arr.optJSONObject(i) != null || arr.optJSONArray(i) != null) {
                        return StoredParse.Corrupt(trimmed)
                    }
                    val id = arr.optString(i, "").trim()
                    if (id.isNotBlank()) add(id)
                }
            }
            if (out.isEmpty()) StoredParse.Blank() else StoredParse.Valid(out)
        } catch (_: Exception) {
            StoredParse.Corrupt(trimmed)
        }
    }

    /** Ids for a valid or legacy value. Blank and corrupt are empty; callers must use [read]. */
    fun parse(json: String?): List<String> = when (val parsed = read(json)) {
        is StoredParse.Valid -> parsed.value
        is StoredParse.Blank -> emptyList()
        is StoredParse.Corrupt -> emptyList()
    }

    fun format(syncIds: List<String>): String {
        val normalized = syncIds.map { it.trim() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return ""
        val arr = JSONArray()
        for (id in normalized) arr.put(id)
        return arr.toString()
    }

    fun primarySyncId(json: String?): String? = parse(json).firstOrNull()


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