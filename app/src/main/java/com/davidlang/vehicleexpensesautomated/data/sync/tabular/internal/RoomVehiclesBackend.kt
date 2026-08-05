package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backend
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **Pilot** read-oriented remotetable [Backend] over Room vehicles.
 *
 * - Not registered as a production spreadsheet destination.
 * - Default [allowWrite] = false: writes throw (no silent Room wipe).
 * - Grid uses [TabularSchema.VEHICLE_HEADERS] including **Sync ID**.
 *
 * Validation: [exportJsonBook] or [readRows] → push to json-book/EtherCalc offline.
 */
@Singleton
class RoomVehiclesBackend @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) : Backend {

    /** When false (default), [writeRows] throws. Enable only in controlled tests. */
    @Volatile
    var allowWrite: Boolean = false

    override val backendId: String = BACKEND_ID

    override fun testConnection(): Map<String, Any?> = mapOf(
        "ok" to true,
        "message" to "room-vehicles pilot (read-only=${!allowWrite})",
        "backend" to BACKEND_ID,
    )

    override fun listTabs(): List<String> = listOf(TAB_NAME)

    override fun ensureHeaders(tab: String, headers: List<String>): List<String> {
        requireTab(tab)
        return TabularSchema.VEHICLE_HEADERS
    }

    override fun readRows(tab: String): TabData {
        requireTab(tab)
        val vehicles = runBlocking { vehicleRepository.getAllIncludingDeleted() }
        return vehiclesToTabData(vehicles)
    }

    override fun writeRows(
        tab: String,
        headers: List<String>,
        rows: List<List<String>>,
        mode: String,
    ): Int {
        requireTab(tab)
        if (!allowWrite) {
            throw UnsupportedOperationException(
                "RoomVehiclesBackend is read-only by default " +
                    "(set allowWrite=true only in tests; uses upsertFromSync)",
            )
        }
        val idx = TabularSchema.headerIndex(
            if (headers.isNotEmpty()) headers else TabularSchema.VEHICLE_HEADERS,
        )
        var n = 0
        runBlocking {
            for (row in rows) {
                val v = TabularSchema.rowToVehicle(row, idx)
                if (v.syncId.isBlank()) continue
                vehicleRepository.upsertFromSync(v)
                n++
            }
        }
        return n
    }

    companion object {
        const val BACKEND_ID = "room-vehicles"
        const val TAB_NAME = TabularSchema.TAB_VEHICLES

        fun vehiclesToTabData(vehicles: List<Vehicle>): TabData {
            val rows = vehicles
                .filter { it.syncId.isNotBlank() }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
                .map { TabularSchema.vehicleToRow(it) }
            return TabData(TabularSchema.VEHICLE_HEADERS, rows)
        }

        /**
         * Write a remotetable **json-book** file
         * `{ "tabs": { "Vehicles": { headers, rows } } }`.
         * Pure from [vehicles] (no Room) for tests/export.
         */
        fun exportJsonBook(file: File, vehicles: List<Vehicle>) {
            val data = vehiclesToTabData(vehicles)
            val headersJa = JSONArray()
            data.headers.forEach { headersJa.put(it) }
            val rowsJa = JSONArray()
            for (row in data.rows) {
                val r = JSONArray()
                row.forEach { r.put(it) }
                rowsJa.put(r)
            }
            val tab = JSONObject()
                .put("headers", headersJa)
                .put("rows", rowsJa)
            val root = JSONObject().put("tabs", JSONObject().put(TAB_NAME, tab))
            file.parentFile?.mkdirs()
            file.writeText(root.toString(2) + "\n")
        }
    }

    private fun requireTab(tab: String) {
        require(tab == TAB_NAME || tab.isBlank()) {
            "RoomVehiclesBackend only supports tab \"$TAB_NAME\" (got \"$tab\")"
        }
    }
}
