package com.davidlang.vehicleexpensesautomated.data.batch

import org.json.JSONObject

/**
 * Station place data stored in [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.location].
 *
 * Canonical form: `{"name":"Shell","address":"123 Main St, City, ST 00000"}`.
 * Legacy plain text is treated as [name] only (no crash).
 * Batch provenance belongs in [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.notes], not location.
 */
object FuelLocationJson {

    data class Place(val name: String = "", val address: String = "") {
        fun isBlank(): Boolean = name.isBlank() && address.isBlank()
        fun displayLine(): String = when {
            name.isNotBlank() && address.isNotBlank() -> "$name — $address"
            name.isNotBlank() -> name
            address.isNotBlank() -> address
            else -> ""
        }
    }

    fun format(name: String?, address: String?): String? {
        val n = name?.trim().orEmpty()
        val a = address?.trim().orEmpty()
        if (n.isEmpty() && a.isEmpty()) return null
        return JSONObject().apply {
            if (n.isNotEmpty()) put("name", n)
            if (a.isNotEmpty()) put("address", a)
        }.toString()
    }

    fun parse(location: String?): Place? {
        if (location.isNullOrBlank()) return null
        val t = location.trim()
        if (t.startsWith("{")) {
            return try {
                val o = JSONObject(t)
                Place(
                    name = o.optString("name", "").trim(),
                    address = o.optString("address", "").trim(),
                ).takeUnless { it.isBlank() }
            } catch (_: Exception) {
                Place(name = t)
            }
        }
        // Legacy plain text or batch tags — display as name if not a batch tag
        if (t.startsWith("batch_")) return null
        return Place(name = t)
    }

    fun displayLine(location: String?): String =
        parse(location)?.displayLine().orEmpty()
}
