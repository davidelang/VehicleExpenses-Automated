package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect

/**
 * Tunable pump OCR / cost-vol classification parameters (vehicle_settings prefs).
 * Selector logic reads these as experiments converge; defaults match current production intent.
 */
object PumpOcrSettings {
    const val PREFS_NAME = "vehicle_settings"

    const val KEY_MAX_RED_BOXES = "pump_max_red_boxes"
    const val KEY_LABEL_Y_BAND_EXTRA_FRACTION = "pump_label_y_band_extra_fraction"
    const val KEY_RATIO_BAND_LO = "pump_cost_vol_ratio_band_lo"
    const val KEY_RATIO_BAND_HI = "pump_cost_vol_ratio_band_hi"

    const val DEFAULT_MAX_RED_BOXES = 8
    const val MIN_MAX_RED_BOXES = 4
    const val MAX_MAX_RED_BOXES = 12

    /** Extend label↔value Y band by this fraction of the smallest value-cluster rect height (resolution-independent). */
    const val DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION = 0.15f

    /** Band-gated floor $/gal (below any plausible pump price) and high-ratio confusion cap. */
    const val DEFAULT_RATIO_BAND_LO = 2.0f
    const val DEFAULT_RATIO_BAND_HI = 30.0f
    const val MIN_RATIO_BAND = 0.5f
    const val MAX_RATIO_BAND = 30.0f

    fun maxRedBoxes(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_RED_BOXES, DEFAULT_MAX_RED_BOXES)
            .coerceIn(MIN_MAX_RED_BOXES, MAX_MAX_RED_BOXES)

    fun labelYBandExtraFraction(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_LABEL_Y_BAND_EXTRA_FRACTION, DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION)
            .coerceIn(0f, 1f)

    fun ratioBandLo(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_RATIO_BAND_LO, DEFAULT_RATIO_BAND_LO)
            .coerceIn(MIN_RATIO_BAND, MAX_RATIO_BAND)

    fun ratioBandHi(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_RATIO_BAND_HI, DEFAULT_RATIO_BAND_HI)
            .coerceIn(MIN_RATIO_BAND, MAX_RATIO_BAND)

    /** Smallest-area rect among [valueRects] defines the Y band for label center matching. */
    fun smallestRect(rects: List<Rect>): Rect? =
        rects.minByOrNull { (it.width().coerceAtLeast(1)) * (it.height().coerceAtLeast(1)) }

    /**
     * True when [labelCenterY] falls inside the smallest value rect's [top,bottom], optionally
     * expanded by [extraFraction] × rect height (resolution-independent).
     */
    fun labelCenterYInValueBand(
        labelCenterY: Float,
        valueRects: List<Rect>,
        extraFraction: Float,
    ): Boolean {
        val base = smallestRect(valueRects) ?: return false
        val h = base.height().coerceAtLeast(1)
        val pad = (extraFraction * h).toInt()
        val top = base.top - pad
        val bottom = base.bottom + pad
        return labelCenterY in top.toFloat()..bottom.toFloat()
    }

    fun rectCenterY(rect: Rect): Float = (rect.top + rect.bottom) / 2f
}