package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backend
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **Pilot** multi-tab remotetable [Backend] over Room fuel entries.
 *
 * - Not registered as a production spreadsheet destination (coordinator LWW stays).
 * - Default [allowWrite] = false: writes throw (no silent Room wipe).
 * - Tab names match production [TabularSchema.fuelTabName] (`Fuel - {name}`).
 * - Rows: **include soft-deleted** fuel (sync export / LWW parity); only non-blank
 *   [FuelEntry.syncId]; sorted by timestamp, odometer, syncId.
 * - Headers: [TabularSchema.FUEL_HEADERS] (includes **Sync ID**).
 *
 * Validation: [exportJsonBook] or [readRows] → json-book / EtherCalc offline.
 */
@Singleton
class RoomFuelBackend @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fuelEntryRepository: FuelEntryRepository,
) : Backend {

    /** When false (default), [writeRows] throws. Enable only in controlled tests. */
    @Volatile
    var allowWrite: Boolean = false

    override val backendId: String = BACKEND_ID

    override fun testConnection(): Map<String, Any?> = mapOf(
        "ok" to true,
        "message" to "room-fuel pilot (read-only=${!allowWrite})",
        "backend" to BACKEND_ID,
    )

    override fun listTabs(): List<String> {
        val vehicles = liveVehiclesWithSyncId()
        return vehicles.map { TabularSchema.fuelTabName(it.name) }
    }

    override fun ensureHeaders(tab: String, headers: List<String>): List<String> {
        requireFuelTab(tab)
        return TabularSchema.FUEL_HEADERS
    }

    override fun readRows(tab: String): TabData {
        requireFuelTab(tab)
        val vehicles = liveVehiclesWithSyncId()
        val vehicle = vehicles.find { TabularSchema.fuelTabName(it.name) == tab }
            ?: return TabData(TabularSchema.FUEL_HEADERS, emptyList())
        val fuels = runBlocking { fuelEntryRepository.getAllIncludingDeleted() }
            .filter { it.vehicleId == vehicle.id }
        return fuelsToTabData(fuels, vehicle.syncId)
    }

    override fun writeRows(
        tab: String,
        headers: List<String>,
        rows: List<List<String>>,
        mode: String,
    ): Int {
        requireFuelTab(tab)
        if (!allowWrite) {
            throw UnsupportedOperationException(
                "RoomFuelBackend is read-only by default " +
                    "(set allowWrite=true only in tests; uses upsertFromSync)",
            )
        }
        val vehicles = liveVehiclesWithSyncId()
        val vehicle = vehicles.find { TabularSchema.fuelTabName(it.name) == tab }
            ?: throw IllegalArgumentException("No live vehicle for fuel tab \"$tab\"")
        val idx = TabularSchema.headerIndex(
            if (headers.isNotEmpty()) headers else TabularSchema.FUEL_HEADERS,
        )
        var n = 0
        runBlocking {
            for (row in rows) {
                val entry = TabularSchema.rowToFuel(row, idx).copy(vehicleId = vehicle.id)
                if (entry.syncId.isBlank()) continue
                fuelEntryRepository.upsertFromSync(entry)
                n++
            }
        }
        return n
    }

    private fun liveVehiclesWithSyncId(): List<Vehicle> = runBlocking {
        vehicleRepository.getAllIncludingDeleted()
            .filter { !it.deleted && it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
    }

    private fun requireFuelTab(tab: String) {
        if (tab.isBlank()) return
        require(tab.startsWith(TabularSchema.FUEL_TAB_PREFIX)) {
            "RoomFuelBackend only supports tabs \"${TabularSchema.FUEL_TAB_PREFIX}*\" (got \"$tab\")"
        }
    }

    companion object {
        const val BACKEND_ID = "room-fuel"

        /**
         * Fuel grid for one vehicle: non-blank syncId; soft-deleted included;
         * sorted timestamp → odometer → syncId (sync export parity).
         */
        fun fuelsToTabData(fuels: List<FuelEntry>, vehicleSyncId: String): TabData {
            val rows = fuels
                .filter { it.syncId.isNotBlank() }
                .sortedWith(
                    compareBy(
                        { it.timestamp },
                        { it.odometer },
                        { it.syncId },
                    ),
                )
                .map { TabularSchema.fuelToRow(it, vehicleSyncId, TabularSchema.FUEL_HEADERS) }
            return TabData(TabularSchema.FUEL_HEADERS, rows)
        }

        /**
         * Multi-tab remotetable **json-book**:
         * `{ "tabs": { "Fuel - Car A": { headers, rows }, … } }`.
         *
         * Vehicles: non-deleted + non-blank syncId (same set as sheet fuel tabs).
         * Fuel rows: all including soft-deleted for that vehicleId; blank syncId dropped.
         * Pure from lists (no Room) for tests/export.
         */
        fun exportJsonBook(
            file: File,
            vehicles: List<Vehicle>,
            fuels: List<FuelEntry>,
        ) {
            val live = vehicles
                .filter { !it.deleted && it.syncId.isNotBlank() }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
            val byVehicle = fuels.groupBy { it.vehicleId }
            val tabsObj = JSONObject()
            for (v in live) {
                val data = fuelsToTabData(byVehicle[v.id].orEmpty(), v.syncId)
                val headersJa = JSONArray()
                data.headers.forEach { headersJa.put(it) }
                val rowsJa = JSONArray()
                for (row in data.rows) {
                    val r = JSONArray()
                    row.forEach { r.put(it) }
                    rowsJa.put(r)
                }
                tabsObj.put(
                    TabularSchema.fuelTabName(v.name),
                    JSONObject().put("headers", headersJa).put("rows", rowsJa),
                )
            }
            val root = JSONObject().put("tabs", tabsObj)
            file.parentFile?.mkdirs()
            file.writeText(root.toString(2) + "\n")
        }
    }
}
