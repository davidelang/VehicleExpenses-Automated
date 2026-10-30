package com.davidlang.vehicleexpensesautomated.data.trip

import com.davidlang.vehicleexpensesautomated.data.model.StoredParse
import org.json.JSONArray

/**
 * Pure helpers for per-vehicle ordered trip type names stored as JSON string arrays
 * on [com.davidlang.vehicleexpensesautomated.data.model.Vehicle.tripTypesJson].
 *
 * First entry is the Start-trip dropdown default.
 */
object TripTypes {
    val DEFAULT_ORDERED: List<String> = listOf(
        "Business",
        "Personal",
        "Charity",
        "Medical",
        "Moving",
    )

    const val PERSONAL = "Personal"

    fun read(json: String?): StoredParse<List<String>> {
        if (json.isNullOrBlank()) return StoredParse.Blank()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) {
            if (trimmed.startsWith("{")) return StoredParse.Corrupt(trimmed)
            return StoredParse.Valid(listOf(trimmed))
        }
        return try {
            val arr = JSONArray(trimmed)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i) != null || arr.optJSONArray(i) != null) {
                    return StoredParse.Corrupt(trimmed)
                }
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) out.add(s)
            }
            if (out.isEmpty()) StoredParse.Blank() else StoredParse.Valid(out)
        } catch (_: Exception) {
            StoredParse.Corrupt(trimmed)
        }
    }

    /** Display list. Blank keeps the seed. Corrupt shows the default list; parse() does not change the stored value. */
    fun parse(json: String?): List<String> = when (val parsed = read(json)) {
        is StoredParse.Valid -> parsed.value
        is StoredParse.Blank -> DEFAULT_ORDERED.toList()
        is StoredParse.Corrupt -> DEFAULT_ORDERED.toList()
    }

    fun format(types: List<String>): String {
        val cleaned = types.map { it.trim() }.filter { it.isNotEmpty() }
        val list = if (cleaned.isEmpty()) DEFAULT_ORDERED else cleaned
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun seedJson(): String = format(DEFAULT_ORDERED)

    /** Seed when blank. A corrupt value is returned unchanged. */
    fun ensureNonEmpty(json: String?): String = when (val parsed = read(json)) {
        is StoredParse.Corrupt -> json ?: seedJson()
        is StoredParse.Blank -> seedJson()
        is StoredParse.Valid -> format(parsed.value)
    }


    fun rename(types: List<String>, index: Int, newName: String): List<String> {
        val name = newName.trim()
        if (name.isEmpty() || index !in types.indices) return types
        return types.toMutableList().also { it[index] = name }
    }

    fun moveUp(types: List<String>, index: Int): List<String> {
        if (index <= 0 || index >= types.size) return types
        return types.toMutableList().also {
            val t = it[index]
            it[index] = it[index - 1]
            it[index - 1] = t
        }
    }

    fun moveDown(types: List<String>, index: Int): List<String> {
        if (index < 0 || index >= types.size - 1) return types
        return types.toMutableList().also {
            val t = it[index]
            it[index] = it[index + 1]
            it[index + 1] = t
        }
    }

    fun add(types: List<String>, name: String): List<String> {
        val n = name.trim()
        if (n.isEmpty()) return types
        if (types.any { it.equals(n, ignoreCase = true) }) return types
        return types + n
    }

    fun removeAt(types: List<String>, index: Int): List<String> {
        if (index !in types.indices) return types
        if (types.size <= 1) return types
        return types.toMutableList().also { it.removeAt(index) }
    }
}
