package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Global directory of known fuel stations (not per-vehicle).
 * Sheet tab **Stations**; LWW key is [syncId].
 */
@Entity(tableName = "known_stations")
data class KnownStation(
    @PrimaryKey val syncId: String,
    val name: String = "",
    val address: String = "",
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
    val kind: String = KIND_FUEL_STATION,
    val source: String = SOURCE_SEED,
    val originDeviceId: String = "",
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
) {
    companion object {
        const val KIND_FUEL_STATION = "fuel_station"
        const val SOURCE_SEED = "seed"
        const val SOURCE_USER = "user"
        const val SOURCE_HISTORY = "history"
        const val SOURCE_STATIONS = "stations"
    }
}
