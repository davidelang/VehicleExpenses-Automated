package com.davidlang.vehicleexpensesautomated.ui.util

/**
 * Central façade for economy / distance **display labels** only.
 *
 * Odometer is stored as an instrument integer (no mi/km conversion in-app yet).
 * Economy math remains Δodo / volume; UI language is currently MPG / $/mi.
 * A future distance-unit plan should swap strings here without hunting call sites.
 *
 * Language packs / RTL are deferred (see sandbox research under
 * `dev-ai-interaction/research/i18n-rtl-and-beyond-languages-20260730.md`).
 */
object UnitFormat {
    /** Efficiency unit label for full-fill economy display (currently MPG language). */
    fun economyEfficiencyLabel(): String = "mpg"

    /** Cost per distance label (currently US-style). */
    fun costPerDistanceLabel(): String = "$/mi"

    /**
     * Short distance unit for odo/Δodo UI (currently `"mi"`).
     * Instrument reading may be km on some vehicles; no conversion yet.
     */
    fun distanceUnitShortLabel(): String = "mi"

    /**
     * Format an absolute odometer reading for display (e.g. toast / summary).
     */
    fun odometerReadingLabel(odo: Int): String = "$odo ${distanceUnitShortLabel()}"

    /**
     * Format an odometer delta for display. Today always uses `mi` wording even though
     * the instrument may be km — product has not added distance prefs yet.
     */
    fun distanceDeltaLabel(delta: Int): String = "$delta ${distanceUnitShortLabel()}"
}
