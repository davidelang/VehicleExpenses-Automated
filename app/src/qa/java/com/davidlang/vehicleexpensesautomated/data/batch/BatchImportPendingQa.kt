package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * End-of-batch questions / unassigned items (survive process death via JSON file).
 * Stage A writes pending; Stage C applies answers (minimal UI stub can list them).
 */


    fun BatchImportPendingStore.removeAll(context: Context, ids: Collection<String>) {
        val drop = ids.toSet()
        val items = load(context).filter { it.id !in drop }
        save(context, items)
    }

