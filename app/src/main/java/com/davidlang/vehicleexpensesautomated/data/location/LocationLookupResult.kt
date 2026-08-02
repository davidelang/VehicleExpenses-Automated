package com.davidlang.vehicleexpensesautomated.data.location

/**
 * POI / reverse-geocode result. Failures are empty list / null from facades — no throw to UI.
 * [distanceM] is from query lat/lon to the POI when known (picker sort/display).
 * Capture coords for the fuel/expense row stay separate — do not overwrite with [poiLat]/[poiLon].
 */
data class LocationLookupResult(
    val name: String = "",
    val address: String = "",
    val source: String,
    val kind: LocationLookupKind,
    val lookedUpAt: Long = System.currentTimeMillis(),
    val distanceM: Double? = null,
    val poiLat: Double? = null,
    val poiLon: Double? = null,
) {
    fun hasPlace(): Boolean = name.isNotBlank() || address.isNotBlank()

    fun displayLine(): String = when {
        name.isNotBlank() && address.isNotBlank() -> "$name — $address"
        name.isNotBlank() -> name
        address.isNotBlank() -> address
        else -> ""
    }
}
