package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Stored fuel volume is always US gallons.
 * Display and a save convert once, to or from [systemDefaultUnit]. Half-up.
 */
object VolumeUnits {
    const val GALLONS = "G"
    const val LITERS = "L"
    const val GALLONS_PER_LITER = 3.785411784
    const val KILOMETERS_PER_MILE = 1.609344

    /** Editable liter figure. Gallons stay the stored number so an unedited save matches. */
    const val EDIT_VOLUME_DECIMALS = 3

    /** Locale volume. US, LR, and MM are gallons; every other country is liters. */
    fun systemDefaultUnit(): String {
        return if (Locale.getDefault().country in setOf("US", "LR", "MM")) GALLONS else LITERS
    }

    fun isLiters(unit: String): Boolean =
        unit.equals(LITERS, ignoreCase = true) ||
            unit.equals("liters", ignoreCase = true) ||
            unit.equals("litres", ignoreCase = true)

    fun shortLabel(unit: String): String = if (isLiters(unit)) "L" else "G"

    /**
     * User-facing volume string. [value] is stored US gallons.
     * Spacing: `"12.34 L"` (space before unit). Number uses [Locale.getDefault].
     */
    fun formatVolume(value: Double, unit: String, decimals: Int = 2): String {
        val shown = storedGallonsToDisplay(value, unit)
        val label = shortLabel(unit)
        val fmt = "%.${decimals}f %s"
        return String.format(Locale.getDefault(), fmt, shown, label)
    }

    /** Format stored gallons in the locale unit. [context] is the call-site handle; the unit is not a pref. */
    fun formatVolume(@Suppress("UNUSED_PARAMETER") context: Context, value: Double, decimals: Int = 2): String =
        formatVolume(value, systemDefaultUnit(), decimals)

    /** Stored gallons → number in [unit]. Gallons are the stored number. Liters multiply once. */
    fun storedGallonsToDisplay(storedGallons: Double, unit: String): Double {
        if (!isLiters(unit)) return storedGallons
        return storedGallons * GALLONS_PER_LITER
    }

    /** Entered number in [fromUnit] → stored US gallons. Gallons are stored as typed. Liters divide once. */
    fun toStoredGallons(entered: Double, fromUnit: String): Double {
        if (entered == 0.0) return 0.0
        if (!isLiters(fromUnit)) return entered
        return entered / GALLONS_PER_LITER
    }

    /** Scale for a stored-gallons quantity shown in the locale unit (charts). */
    fun displayVolumeScale(): Double =
        if (systemDefaultUnit() == LITERS) GALLONS_PER_LITER else 1.0

    /** Scale for a per-stored-gallon price shown in the locale unit. */
    fun displayUnitPriceScale(): Double =
        if (systemDefaultUnit() == LITERS) 1.0 / GALLONS_PER_LITER else 1.0

    /**
     * Text for an editable volume field.
     * Gallons: [Double.toString] of the stored number.
     * Liters: half-up product at [EDIT_VOLUME_DECIMALS], so a save of that text displays the same figure.
     */
    fun editableVolumeText(storedGallons: Double, unit: String = systemDefaultUnit()): String {
        if (storedGallons == 0.0) return ""
        if (!isLiters(unit)) return storedGallons.toString()
        val shown = halfUp(storedGallons * GALLONS_PER_LITER, EDIT_VOLUME_DECIMALS)
        return String.format(Locale.US, "%.${EDIT_VOLUME_DECIMALS}f", shown)
    }

    fun halfUp(value: Double, scale: Int): Double {
        if (!value.isFinite()) return value
        return BigDecimal(value.toString()).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    fun convert(value: Double, from: String, to: String): Double {
        if (isLiters(from) == isLiters(to)) return value
        return if (!isLiters(from) && isLiters(to)) {
            value * GALLONS_PER_LITER
        } else {
            value / GALLONS_PER_LITER
        }
    }

    /** Miles ↔ kilometers. Stored odometer integers are not passed through this. */
    fun convertDistance(value: Double, fromUnit: String, toUnit: String): Double {
        val from = UnitFormat.normalizeOdometerUnit(fromUnit)
        val to = UnitFormat.normalizeOdometerUnit(toUnit)
        if (from == to) return value
        return if (from == UnitFormat.MI) value * KILOMETERS_PER_MILE else value / KILOMETERS_PER_MILE
    }
}
