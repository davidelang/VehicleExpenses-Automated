package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context

/**
 * Central façade for economy / distance **display labels** only.
 *
 * ## Same-unit odometer model
 * Odometer values are **opaque instrument integers**. Economy math is still
 * Δodo / volume — **no** mi↔km conversion. Every vehicle is assumed to use the
 * same instrument unit; the user is responsible for that consistency.
 *
 * Global pref [PREF_KEY] (`mi` | `km`, default [MI]) only switches **wording**
 * (mi/km, mpg vs km/L, $/mi vs $/km). Changing the pref never multiplies odo.
 *
 * Per-vehicle distance unit + conversion is a future backlog item (TODO).
 */
object UnitFormat {
    const val PREFS_NAME = "vehicle_settings"
    const val PREF_KEY = "distance_unit"
    const val MI = "mi"
    const val KM = "km"

    /**
     * Resolve preferred distance **label** unit from prefs.
     * Default [MI] when unset (matches historical hardcodes).
     */
    fun resolvedDistanceUnit(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pref = prefs.getString(PREF_KEY, MI) ?: MI
        return if (pref == KM) KM else MI
    }

    fun setDistanceUnit(context: Context, unit: String) {
        val normalized = if (unit == KM) KM else MI
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, normalized)
            .apply()
    }

    /**
     * Efficiency unit label: [MI] → `"mpg"`; [KM] → `"km/L"`.
     * **Labels only** — the number is still Δodo ÷ volume in the preferred volume unit
     * (not a true mi↔km or gal↔L re-derivation of economy).
     */
    fun economyEfficiencyLabel(context: Context? = null): String =
        if (distanceUnitShortLabel(context) == KM) "km/L" else "mpg"

    /**
     * Volume per distance (inverse economy), e.g. `G/mi` / `L/km`.
     * Uses preferred volume unit from settings when [context] is provided.
     */
    fun volumePerDistanceLabel(context: Context? = null): String {
        val vol = context?.let { VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(it)) }
            ?: "G"
        return vol + "/" + distanceUnitShortLabel(context)
    }

    /**
     * Unit price (cost ÷ volume), e.g. `$/G` / `$/L`.
     */
    fun unitPriceLabel(context: Context? = null): String {
        val vol = context?.let { VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(it)) }
            ?: "G"
        return "$/" + vol
    }

    /** Cost per distance label: `$/mi` or `$/km`. */
    fun costPerDistanceLabel(context: Context? = null): String =
        if (distanceUnitShortLabel(context) == KM) "$/km" else "$/mi"

    /**
     * Short distance unit for odo/Δodo UI (`"mi"` or `"km"`).
     * No conversion of the numeric reading.
     */
    fun distanceUnitShortLabel(context: Context? = null): String {
        if (context == null) return MI
        return resolvedDistanceUnit(context)
    }

    /**
     * Format an absolute odometer reading for display (e.g. toast / summary).
     * Number is unchanged; only the unit word follows the pref.
     */
    fun odometerReadingLabel(odo: Int, context: Context? = null): String =
        "$odo ${distanceUnitShortLabel(context)}"

    /**
     * Format an odometer delta for display. Number is unchanged; unit word follows pref.
     */
    fun distanceDeltaLabel(delta: Int, context: Context? = null): String =
        "$delta ${distanceUnitShortLabel(context)}"
}
