package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import java.util.UUID

/**
 * Stable per-install device instance id for multi-device sync.
 * Logical sync key for sheet rows (later): originDeviceId + local autoincrement id.
 */
object SyncIdentity {

    private const val PREFS_NAME = "vehicle_settings"
    private const val KEY_SYNC_DEVICE_ID = "sync_device_id"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SYNC_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SYNC_DEVICE_ID, created).commit()
        return created
    }
}