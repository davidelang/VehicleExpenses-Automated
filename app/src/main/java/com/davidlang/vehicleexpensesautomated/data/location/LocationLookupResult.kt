package com.davidlang.vehicleexpensesautomated.data.location

/**
 * Single nearest lookup result (v1). Failures are null from the facade — no throw to UI.
 */
data class LocationLookupResult(
    val name: String = "",
    val address: String = "",
    val source: String,
    val kind: LocationLookupKind,
    val lookedUpAt: Long = System.currentTimeMillis(),
) {
    fun hasPlace(): Boolean = name.isNotBlank() || address.isNotBlank()

    fun displayLine(): String = when {
        name.isNotBlank() && address.isNotBlank() -> "$name — $address"
        name.isNotBlank() -> name
        address.isNotBlank() -> address
        else -> ""
    }
}
