package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Open corrupt tabular cells. The remote cell stays until the user chooses.
 * Disposition: leave (default), write_back, accept.
 */
object CorruptIncoming {
    private const val TAG = "CorruptIncoming"
    private const val PREFS = SyncDestinationStore.PREFS_NAME
    private const val KEY = "corrupt_incoming_json"

    const val LEAVE = "leave"
    const val WRITE_BACK = "write_back"
    const val ACCEPT = "accept"
    private const val KEY_UNSEEN = "corrupt_incoming_unseen"

    data class Item(
        val sheet: String,
        val syncId: String,
        val column: String,
        val raw: String,
        val localValue: String,
        val disposition: String = LEAVE,
    ) {
        val key: String get() = "$sheet|$syncId|$column"
    }

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun hasOpen(): Boolean = load().isNotEmpty()

    fun openItems(): List<Item> = load()

    fun record(item: Item) {
        val all = load().toMutableList()
        if (all.any { it.key == item.key }) return
        all.add(item)
        save(all)
        noteUnseen()
        Log.e(TAG, "corrupt incoming ${item.sheet} ${item.syncId} ${item.column}")
    }

    fun takeUnseen(): Int {
        val ctx = appContext ?: return 0
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = prefs.getInt(KEY_UNSEEN, 0)
        if (n != 0) prefs.edit().putInt(KEY_UNSEEN, 0).apply()
        return n
    }

    private fun noteUnseen() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_UNSEEN, prefs.getInt(KEY_UNSEEN, 0) + 1).apply()
    }

    fun setDisposition(key: String, disposition: String) {
        val all = load().map { if (it.key == key) it.copy(disposition = disposition) else it }
        save(all)
    }

    fun blocked(syncId: String): Boolean =
        load().any { it.syncId == syncId && it.syncId.isNotBlank() }

    /** Publish substitutes the stored remote text while the user has not chosen to write local. */
    fun outgoingCell(sheet: String, syncId: String, column: String, localCell: String): String {
        val item = load().firstOrNull {
            it.sheet == sheet && it.syncId == syncId && it.column == column
        } ?: return localCell
        return if (item.disposition == LEAVE) item.raw else localCell
    }

    fun consumeResolved() {
        val kept = load().filter { it.disposition == LEAVE }
        save(kept)
    }

    private fun load(): List<Item> {
        val ctx = appContext ?: return emptyList()
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Item(
                            sheet = o.optString("sheet"),
                            syncId = o.optString("syncId"),
                            column = o.optString("column"),
                            raw = o.optString("raw"),
                            localValue = o.optString("localValue"),
                            disposition = o.optString("disposition", LEAVE).ifBlank { LEAVE },
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "corrupt-incoming list unreadable", e)
            emptyList()
        }
    }

    private fun save(items: List<Item>) {
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("sheet", item.sheet)
                    put("syncId", item.syncId)
                    put("column", item.column)
                    put("raw", item.raw)
                    put("localValue", item.localValue)
                    put("disposition", item.disposition)
                },
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    @Volatile
    private var appContext: Context? = null
}
