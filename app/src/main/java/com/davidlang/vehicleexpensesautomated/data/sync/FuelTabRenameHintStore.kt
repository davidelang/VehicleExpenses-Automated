package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context

/**
 * Persists the previous fuel sheet tab title when a vehicle is renamed offline.
 * [SpreadsheetSyncCoordinator] consumes the hint on next spreadsheet sync.
 */
class FuelTabRenameHintStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordHint(vehicleSyncId: String, oldTabName: String) {
        if (vehicleSyncId.isBlank() || oldTabName.isBlank()) return
        prefs.edit().putString(key(vehicleSyncId), oldTabName).apply()
    }

    fun peekHint(vehicleSyncId: String): String? =
        prefs.getString(key(vehicleSyncId), null)?.trim()?.takeIf { it.isNotBlank() }

    fun clearHint(vehicleSyncId: String) {
        if (vehicleSyncId.isBlank()) return
        prefs.edit().remove(key(vehicleSyncId)).apply()
    }

    private fun key(vehicleSyncId: String) = KEY_PREFIX + vehicleSyncId

    companion object {
        private const val PREFS_NAME = "vehicle_settings"
        private const val KEY_PREFIX = "fuel_tab_rename_hint_"
    }
}