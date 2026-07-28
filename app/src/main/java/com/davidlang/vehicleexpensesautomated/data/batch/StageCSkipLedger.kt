package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import org.json.JSONArray

/**
 * Same-phase skip ledger: items the user skipped stay suppressed until phase
 * advance / clear / post-sync reset. Not sticky across sync.
 */
object StageCSkipLedger {
    private const val PREFS = "stage_c_skip_ledger"
    private const val KEY_KEYS = "keys"
    private const val KEY_PHASE = "phase"

    /** Stable key: kind + photo stem + fuel syncId or entry fingerprint. */
    fun keyFor(item: BatchPendingItem): String {
        val stem = photoStem(
            item.durablePhotoPath ?: item.photoPath
                ?: item.extra["photoPaths"]?.split('|')?.firstOrNull().orEmpty(),
        )
        val sync = item.extra["syncId"].orEmpty()
        val fid = item.fuelEntryId?.toString()
            ?: item.extra["suspectId"]
            ?: item.extra["entryIds"].orEmpty()
        return listOf(item.kind.name, stem, sync, fid).joinToString("|")
    }

    fun load(context: Context, currentPhase: Int): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedPhase = p.getInt(KEY_PHASE, -1)
        if (storedPhase != currentPhase) return emptySet()
        val raw = p.getString(KEY_KEYS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) add(s)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun add(context: Context, phase: Int, item: BatchPendingItem) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedPhase = p.getInt(KEY_PHASE, -1)
        val existing = if (storedPhase == phase) load(context, phase).toMutableSet() else mutableSetOf()
        existing.add(keyFor(item))
        val arr = JSONArray()
        existing.forEach { arr.put(it) }
        p.edit()
            .putInt(KEY_PHASE, phase)
            .putString(KEY_KEYS, arr.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun filterOut(items: List<BatchPendingItem>, skipped: Set<String>): List<BatchPendingItem> {
        if (skipped.isEmpty()) return items
        return items.filter { keyFor(it) !in skipped }
    }
}
