package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import java.util.Locale

/**
 * Preferred volume unit for fuel storage and display.
 * DB [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.gallons] always stores
 * volume in this unit (G or L), not necessarily US gallons despite the field name.
 */
object VolumeUnits {
    const val GALLONS = "G"
    const val LITERS = "L"
    const val GALLONS_PER_LITER = 3.785411784

    /** Resolve prefs volume_unit → "G" or "L" (system/blank → locale default). */
    fun resolvedPreferredVolumeUnit(context: Context): String {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        val pref = prefs.getString("volume_unit", null)
        if (pref == GALLONS || pref == LITERS) return pref
        return systemDefaultUnit()
    }

    fun systemDefaultUnit(): String {
        return if (Locale.getDefault().country in setOf("US", "LR", "MM")) GALLONS else LITERS
    }

    fun shortLabel(unit: String): String = if (unit == LITERS) "L" else "G"

    fun longLabel(unit: String): String = if (unit == LITERS) "Liters" else "Gallons"

    fun convert(value: Double, from: String, to: String): Double {
        if (from == to) return value
        return if (from == GALLONS && to == LITERS) {
            value * GALLONS_PER_LITER
        } else {
            value / GALLONS_PER_LITER
        }
    }
}
