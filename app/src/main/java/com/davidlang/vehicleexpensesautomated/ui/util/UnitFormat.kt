package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import java.util.Locale

/**
 * Economy and distance display labels.
 * Odometer integers are not converted. A km vehicle's economy is that integer delta per stored gallon.
 */
object UnitFormat {
    const val MI = "mi"
    const val KM = "km"

    /** Blank, miles, or anything other than kilometers stays miles. */
    fun normalizeOdometerUnit(raw: String?): String {
        val t = raw?.trim()?.lowercase(Locale.US) ?: return MI
        return when (t) {
            KM, "kilometers", "kilometres" -> KM
            else -> MI
        }
    }

    /** The 5–80 mpg display band applies only to a miles vehicle. */
    fun usesMpgClamp(unit: String?): Boolean = normalizeOdometerUnit(unit) == MI

    /** Miles: `mpg`. Kilometers: km per stored gallon. */
    fun economyEfficiencyLabel(unit: String? = MI): String =
        if (usesMpgClamp(unit)) "mpg" else "km/gal"

    /**
     * Volume per distance, e.g. `G/mi` / `L/km`.
     * Volume follows the locale. Distance follows [unit].
     */
    fun volumePerDistanceLabel(context: Context? = null, unit: String? = MI): String {
        val vol = context?.let { VolumeUnits.shortLabel(VolumeUnits.systemDefaultUnit()) } ?: "G"
        return vol + "/" + distanceUnitShortLabel(unit)
    }

    /** Unit price (cost ÷ stored gallons, labeled in the locale volume unit). */
    fun unitPriceLabel(context: Context? = null): String {
        val vol = context?.let { VolumeUnits.shortLabel(VolumeUnits.systemDefaultUnit()) } ?: "G"
        return "$/" + vol
    }

    fun costPerDistanceLabel(unit: String? = MI): String = "$/" + distanceUnitShortLabel(unit)

    fun distanceUnitShortLabel(unit: String? = MI): String = normalizeOdometerUnit(unit)

    fun odometerReadingLabel(odo: Int, unit: String? = MI): String =
        "$odo ${distanceUnitShortLabel(unit)}"

    fun distanceDeltaLabel(delta: Int, unit: String? = MI): String =
        "$delta ${distanceUnitShortLabel(unit)}"
}
