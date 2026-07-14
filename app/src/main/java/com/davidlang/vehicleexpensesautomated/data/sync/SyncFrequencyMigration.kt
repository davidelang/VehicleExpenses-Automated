package com.davidlang.vehicleexpensesautomated.data.sync

/**
 * Canonical frequency migration: stored minutes win; legacy JSON hours are fallback only.
 */
object SyncFrequencyMigration {
    const val MIN_MINUTES = 15
    const val MAX_MINUTES = 24 * 60
    /** Legacy JSON default when neither minutes nor hours were persisted. */
    const val LEGACY_JSON_DEFAULT_HOURS = 6

    fun resolveMinutes(
        frequencyMinutes: Int,
        legacyFrequencyHours: Int = 0,
        defaultMinutes: Int = 60,
    ): Int = when {
        frequencyMinutes > 0 -> frequencyMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        legacyFrequencyHours > 0 ->
            (legacyFrequencyHours * 60).coerceIn(MIN_MINUTES, MAX_MINUTES)
        else -> defaultMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    /** Read legacy destination JSON (`frequencyMinutes` / `frequencyHours` keys). */
    fun fromStoredJson(frequencyMinutes: Int, legacyFrequencyHours: Int): Int {
        if (frequencyMinutes > 0) {
            return frequencyMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        }
        val hours = legacyFrequencyHours.takeIf { it > 0 } ?: LEGACY_JSON_DEFAULT_HOURS
        return (hours * 60).coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}