package com.davidlang.vehicleexpensesautomated.data.location

/**
 * POI / reverse-geocode strategy for live UI and deferred [LocationLookupWorker].
 */
enum class LocationLookupKind {
    /** Overpass amenity=fuel → Nominatim address fallback. */
    FUEL_STATION,

    /** Overpass shop=car_repair|car_parts (+ close tags) → Nominatim fallback. */
    AUTO_SERVICE,

    /** Nominatim reverse only (Trip; expense non-repair categories). */
    ADDRESS_ONLY,
    ;

    fun blobKindTag(): String = when (this) {
        FUEL_STATION -> "fuel_station"
        AUTO_SERVICE -> "auto_service"
        ADDRESS_ONLY -> "address_only"
    }
}
