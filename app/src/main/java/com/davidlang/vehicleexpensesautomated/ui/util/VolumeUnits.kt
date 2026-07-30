package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import java.util.Locale

/**
 * Preferred volume unit for fuel storage and display.
 *
 * DB [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.gallons] always stores
 * volume in the **preferred** unit (G or L from settings), not necessarily US gallons
 * despite the field name.
 *
 * Display must use [formatVolume] / [shortLabel] — do not invent conversion at display time
 * except at Quick Fill save and Settings convert-all.
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

    /**
     * User-facing volume string. Spacing: `"12.34 L"` (space before unit).
     * Uses [Locale.getDefault] for the number (not Locale.US).
     */
    fun formatVolume(value: Double, unit: String, decimals: Int = 2): String {
        val label = shortLabel(unit)
        val fmt = "%.${decimals}f %s"
        return String.format(Locale.getDefault(), fmt, value, label)
    }

    /** Format using the device's preferred volume unit from settings. */
    fun formatVolume(context: Context, value: Double, decimals: Int = 2): String =
        formatVolume(value, resolvedPreferredVolumeUnit(context), decimals)

    fun convert(value: Double, from: String, to: String): Double {
        if (from == to) return value
        return if (from == GALLONS && to == LITERS) {
            value * GALLONS_PER_LITER
        } else {
            value / GALLONS_PER_LITER
        }
    }
}
