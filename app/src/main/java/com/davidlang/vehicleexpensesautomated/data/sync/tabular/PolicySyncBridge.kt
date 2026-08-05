package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidelang.remotetable.Backends
import com.davidelang.remotetable.ColumnDef
import com.davidelang.remotetable.MergeMode
import com.davidelang.remotetable.MergeSync
import com.davidelang.remotetable.MergeUnit
import com.davidelang.remotetable.PolicySync
import com.davidelang.remotetable.PushResult
import com.davidelang.remotetable.TabData
import com.davidelang.remotetable.TableSyncUnit
import com.davidelang.remotetable.TombstoneConfig
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

/**
 * VE ↔ remotetable L3 bridge: map entity grids and call [MergeSync] / [PolicySync].
 *
 * Library stays domain-agnostic (keys, timestamps, grids). This object holds VE
 * header/row mapping only. Coordinator always uses these helpers for tab LWW
 * (merge-acks, expenses, vehicles, fuel Pass 1). No prefs / pilot flags.
 *
 * **Vehicles:** [mergeVehiclesViaLwwRow] then [VehicleDefinitionOverlay] in coordinator.
 * **Fuel:** full-row LWW here; app field-merge still runs after LWW.
 */
object PolicySyncBridge {

    private const val TAB_LOCAL = "LocalAcks"
    private const val TAB_REMOTE = "RemoteAcks"
    private const val TAB_LOCAL_EXP = "LocalExpenses"
    private const val TAB_REMOTE_EXP = "RemoteExpenses"
    private const val TAB_LOCAL_VEH = "LocalVehicles"
    private const val TAB_REMOTE_VEH = "RemoteVehicles"
    private const val TAB_LOCAL_FUEL = "LocalFuel"
    private const val TAB_REMOTE_FUEL = "RemoteFuel"

    private val TOMBSTONE = TombstoneConfig(
        column = "Deleted",
        trueValues = listOf("true", "1", "yes", "TRUE", "True"),
    )

    fun mergeAckColumnDefs(): List<ColumnDef> =
        TabularSchema.MERGE_ACK_HEADERS.map { name ->
            val type = when (name) {
                "Created At", "Updated At", "Deleted At" -> "timestamp"
                "Deleted" -> "checkbox"
                else -> "string"
            }
            ColumnDef(name, type)
        }

    fun mergeAcksMergeUnit(
        mode: MergeMode = MergeMode.LWW_ROW,
        writeTarget: String = "none",
    ): MergeUnit = MergeUnit(
        id = "merge-acks-pilot",
        mergeMode = mode,
        writeTarget = writeTarget,
        tableA = TAB_LOCAL,
        tableB = TAB_REMOTE,
        columns = mergeAckColumnDefs(),
        columnMapA = emptyMap(),
        columnMapB = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun mergeAcksPushUnit(): TableSyncUnit = TableSyncUnit(
        id = "merge-acks-push-pilot",
        direction = "push",
        sourceTable = TAB_LOCAL,
        destTable = TAB_REMOTE,
        columns = mergeAckColumnDefs(),
        columnMap = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun tabDataFromAcks(acks: List<MergeAck>): TabData {
        val rows = acks
            .filter { it.ackId.isNotBlank() }
            .sortedWith(compareBy({ it.kind }, { it.ackId }))
            .map { TabularSchema.ackToRow(it) }
        return TabData(TabularSchema.MERGE_ACK_HEADERS, rows)
    }

    fun tabDataFromGrid(
        grid: List<List<String>>,
        canonical: List<String> = TabularSchema.MERGE_ACK_HEADERS,
    ): TabData {
        if (grid.isEmpty()) return TabData(canonical, emptyList())
        val headers = grid.first().map { it.trim() }.let { h ->
            if (TabularSchema.isValidHeaderRow(h)) h else canonical
        }
        val data = if (grid.size <= 1) emptyList() else grid.drop(1)
        return TabData(headers, data)
    }

    fun acksFromTabData(data: TabData): List<MergeAck> {
        val idx = TabularSchema.headerIndex(data.headers)
        return data.rows
            .filter { row -> row.any { it.isNotBlank() } }
            .map { TabularSchema.rowToAck(it, idx) }
            .filter { it.ackId.isNotBlank() }
    }

    /**
     * Bidirectional LWW merge of local Room acks with remote sheet grid via [MergeSync].
     * Returns merged ack list (does not touch Room or network).
     */
    fun mergeAcksViaLwwRow(
        localAcks: List<MergeAck>,
        remoteGrid: List<List<String>>,
    ): List<MergeAck> {
        val localData = tabDataFromAcks(localAcks)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.MERGE_ACK_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE to remoteData))
        val result = MergeSync.merge(a, b, mergeAcksMergeUnit(writeTarget = "none"))
        return acksFromTabData(result.data)
    }

    /**
     * One-way PolicySync.push (local → remote snapshot). For experiments; pilot prefers LWW.
     */
    fun pushAcksViaPolicy(
        localAcks: List<MergeAck>,
        remoteGrid: List<List<String>>,
    ): Pair<List<MergeAck>, PushResult> {
        val localData = tabDataFromAcks(localAcks)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.MERGE_ACK_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE to remoteData))
        val pushResult = PolicySync.push(a, b, mergeAcksPushUnit())
        val after = b.readRows(TAB_REMOTE)
        return acksFromTabData(after) to pushResult
    }

    /** Pure key-set check for tests (no Backends). */
    fun syncIds(acks: List<MergeAck>): Set<String> =
        acks.map { it.ackId }.filter { it.isNotBlank() }.toSet()

    // ── Expenses pilot ──────────────────────────────────────────────────────

    fun expenseColumnDefs(): List<ColumnDef> =
        TabularSchema.EXPENSE_HEADERS.map { name ->
            val type = when (name) {
                "Date", "Updated At", "Deleted At" -> "timestamp"
                "Amount", "Odometer" -> "number"
                "Deleted" -> "checkbox"
                else -> "string"
            }
            ColumnDef(name, type)
        }

    fun expensesMergeUnit(
        mode: MergeMode = MergeMode.LWW_ROW,
        writeTarget: String = "none",
    ): MergeUnit = MergeUnit(
        id = "expenses-pilot",
        mergeMode = mode,
        writeTarget = writeTarget,
        tableA = TAB_LOCAL_EXP,
        tableB = TAB_REMOTE_EXP,
        columns = expenseColumnDefs(),
        columnMapA = emptyMap(),
        columnMapB = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun expensesPushUnit(): TableSyncUnit = TableSyncUnit(
        id = "expenses-push-pilot",
        direction = "push",
        sourceTable = TAB_LOCAL_EXP,
        destTable = TAB_REMOTE_EXP,
        columns = expenseColumnDefs(),
        columnMap = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    /**
     * @param vehicleSyncIdById Room vehicleId → vehicle Sync ID for [TabularSchema.expenseToRow].
     */
    fun tabDataFromExpenses(
        expenses: List<ExpenseEntry>,
        vehicleSyncIdById: Map<Int, String> = emptyMap(),
    ): TabData {
        val rows = expenses
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.date }, { it.syncId }))
            .map { TabularSchema.expenseToRow(it, vehicleSyncIdById[it.vehicleId].orEmpty()) }
        return TabData(TabularSchema.EXPENSE_HEADERS, rows)
    }

    /**
     * Decode expenses from merged [TabData]; resolve vehicle links like coordinator.
     *
     * @param vehicleIdBySyncId vehicle Sync ID → Room vehicleId
     */
    fun expensesFromTabData(
        data: TabData,
        vehicleIdBySyncId: Map<String, Int> = emptyMap(),
    ): List<ExpenseEntry> {
        val idx = TabularSchema.headerIndex(data.headers)
        return data.rows
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row ->
                val parsed = TabularSchema.rowToExpense(row, idx)
                val vehicleSyncIds = TabularSchema.rowToExpenseVehicleSyncIds(row, idx)
                ExpenseVehicleSyncIds.applyResolvedVehicles(parsed, vehicleSyncIds, vehicleIdBySyncId)
            }
            .filter { it.syncId.isNotBlank() }
    }

    /**
     * Bidirectional LWW merge of local Room expenses with remote sheet grid via [MergeSync].
     * Full-row pick (library); does not apply coordinator location-blob field merge.
     */
    fun mergeExpensesViaLwwRow(
        localExpenses: List<ExpenseEntry>,
        remoteGrid: List<List<String>>,
        vehicleSyncIdById: Map<Int, String> = emptyMap(),
        vehicleIdBySyncId: Map<String, Int> = emptyMap(),
    ): List<ExpenseEntry> {
        val localData = tabDataFromExpenses(localExpenses, vehicleSyncIdById)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.EXPENSE_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_EXP to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_EXP to remoteData))
        val result = MergeSync.merge(a, b, expensesMergeUnit(writeTarget = "none"))
        return expensesFromTabData(result.data, vehicleIdBySyncId)
    }

    /** One-way PolicySync.push for expenses experiments. */
    fun pushExpensesViaPolicy(
        localExpenses: List<ExpenseEntry>,
        remoteGrid: List<List<String>>,
        vehicleSyncIdById: Map<Int, String> = emptyMap(),
        vehicleIdBySyncId: Map<String, Int> = emptyMap(),
    ): Pair<List<ExpenseEntry>, PushResult> {
        val localData = tabDataFromExpenses(localExpenses, vehicleSyncIdById)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.EXPENSE_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_EXP to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_EXP to remoteData))
        val pushResult = PolicySync.push(a, b, expensesPushUnit())
        val after = b.readRows(TAB_REMOTE_EXP)
        return expensesFromTabData(after, vehicleIdBySyncId) to pushResult
    }

    // ── Vehicles pilot ──────────────────────────────────────────────────────

    fun vehicleColumnDefs(): List<ColumnDef> =
        TabularSchema.VEHICLE_HEADERS.map { name ->
            val type = when (name) {
                "Updated At", "Deleted At" -> "timestamp"
                "Deleted" -> "checkbox"
                "Year",
                "Odo Crop L", "Odo Crop T", "Odo Crop R", "Odo Crop B",
                "Other Crop L", "Other Crop T", "Other Crop R", "Other Crop B",
                -> "number"
                else -> "string"
            }
            ColumnDef(name, type)
        }

    fun vehiclesMergeUnit(
        mode: MergeMode = MergeMode.LWW_ROW,
        writeTarget: String = "none",
    ): MergeUnit = MergeUnit(
        id = "vehicles-pilot",
        mergeMode = mode,
        writeTarget = writeTarget,
        tableA = TAB_LOCAL_VEH,
        tableB = TAB_REMOTE_VEH,
        columns = vehicleColumnDefs(),
        columnMapA = emptyMap(),
        columnMapB = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun vehiclesPushUnit(): TableSyncUnit = TableSyncUnit(
        id = "vehicles-push-pilot",
        direction = "push",
        sourceTable = TAB_LOCAL_VEH,
        destTable = TAB_REMOTE_VEH,
        columns = vehicleColumnDefs(),
        columnMap = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun tabDataFromVehicles(vehicles: List<Vehicle>): TabData {
        val rows = vehicles
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
            .map { TabularSchema.vehicleToRow(it) }
        return TabData(TabularSchema.VEHICLE_HEADERS, rows)
    }

    fun vehiclesFromTabData(data: TabData): List<Vehicle> {
        val idx = TabularSchema.headerIndex(data.headers)
        return data.rows
            .filter { row -> row.any { it.isNotBlank() } }
            .map { TabularSchema.rowToVehicle(it, idx) }
            .filter { it.syncId.isNotBlank() }
    }

    /**
     * Bidirectional LWW merge of local Room vehicles with remote sheet grid via [MergeSync].
     *
     * Full-row pick only at library layer. Callers (coordinator) should apply
     * [VehicleDefinitionOverlay.applyToMergedList] so definition fields match legacy path.
     */
    fun mergeVehiclesViaLwwRow(
        localVehicles: List<Vehicle>,
        remoteGrid: List<List<String>>,
    ): List<Vehicle> {
        val localData = tabDataFromVehicles(localVehicles)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.VEHICLE_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_VEH to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_VEH to remoteData))
        val result = MergeSync.merge(a, b, vehiclesMergeUnit(writeTarget = "none"))
        return vehiclesFromTabData(result.data)
    }

    /** One-way PolicySync.push for vehicles experiments. */
    fun pushVehiclesViaPolicy(
        localVehicles: List<Vehicle>,
        remoteGrid: List<List<String>>,
    ): Pair<List<Vehicle>, PushResult> {
        val localData = tabDataFromVehicles(localVehicles)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.VEHICLE_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_VEH to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_VEH to remoteData))
        val pushResult = PolicySync.push(a, b, vehiclesPushUnit())
        val after = b.readRows(TAB_REMOTE_VEH)
        return vehiclesFromTabData(after) to pushResult
    }

    // ── Fuel pilot (per-tab LWW only; field-merge stays in app) ──────────────

    fun fuelColumnDefs(): List<ColumnDef> =
        TabularSchema.FUEL_HEADERS.map { name ->
            val type = when (name) {
                "Timestamp", "Updated At", "Deleted At" -> "timestamp"
                "Deleted", "Partial Fill", "Economy Ignored" -> "checkbox"
                "Odometer", "Gallons", "Cost" -> "number"
                else -> "string"
            }
            ColumnDef(name, type)
        }

    fun fuelMergeUnit(
        mode: MergeMode = MergeMode.LWW_ROW,
        writeTarget: String = "none",
    ): MergeUnit = MergeUnit(
        id = "fuel-pilot",
        mergeMode = mode,
        writeTarget = writeTarget,
        tableA = TAB_LOCAL_FUEL,
        tableB = TAB_REMOTE_FUEL,
        columns = fuelColumnDefs(),
        columnMapA = emptyMap(),
        columnMapB = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun fuelPushUnit(): TableSyncUnit = TableSyncUnit(
        id = "fuel-push-pilot",
        direction = "push",
        sourceTable = TAB_LOCAL_FUEL,
        destTable = TAB_REMOTE_FUEL,
        columns = fuelColumnDefs(),
        columnMap = emptyMap(),
        keys = listOf("Sync ID"),
        timestamp = "Updated At",
        tombstone = TOMBSTONE,
    )

    fun tabDataFromFuel(
        entries: List<FuelEntry>,
        vehicleSyncId: String = "",
        columnOrder: List<String> = TabularSchema.FUEL_HEADERS,
    ): TabData {
        val rows = entries
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.timestamp }, { it.odometer }, { it.syncId }))
            .map { TabularSchema.fuelToRow(it, vehicleSyncId, columnOrder) }
        return TabData(columnOrder, rows)
    }

    fun fuelFromTabData(
        data: TabData,
        vehicleId: Int,
    ): List<FuelEntry> {
        val idx = TabularSchema.headerIndex(data.headers)
        return data.rows
            .filter { row -> row.any { it.isNotBlank() } }
            .map { TabularSchema.rowToFuel(it, idx).copy(vehicleId = vehicleId) }
            .filter { it.syncId.isNotBlank() }
    }

    /**
     * Bidirectional LWW merge of local Room fuel with remote sheet grid via [MergeSync].
     *
     * Full-row pick only (no coordinator location-blob merge). Caller must still run
     * app field-merge (absorb partials) after upserting results.
     *
     * @param vehicleId forced on all returned rows
     * @param vehicleSyncId used when encoding local rows (Vehicle Sync ID column)
     */
    fun mergeFuelViaLwwRow(
        localEntries: List<FuelEntry>,
        remoteGrid: List<List<String>>,
        vehicleId: Int,
        vehicleSyncId: String = "",
    ): List<FuelEntry> {
        val localData = tabDataFromFuel(localEntries, vehicleSyncId)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.FUEL_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_FUEL to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_FUEL to remoteData))
        val result = MergeSync.merge(a, b, fuelMergeUnit(writeTarget = "none"))
        return fuelFromTabData(result.data, vehicleId)
    }

    /** One-way PolicySync.push for fuel experiments (single tab). */
    fun pushFuelViaPolicy(
        localEntries: List<FuelEntry>,
        remoteGrid: List<List<String>>,
        vehicleId: Int,
        vehicleSyncId: String = "",
    ): Pair<List<FuelEntry>, PushResult> {
        val localData = tabDataFromFuel(localEntries, vehicleSyncId)
        val remoteData = tabDataFromGrid(remoteGrid, TabularSchema.FUEL_HEADERS)
        val a = Backends.mock(mapOf(TAB_LOCAL_FUEL to localData))
        val b = Backends.mock(mapOf(TAB_REMOTE_FUEL to remoteData))
        val pushResult = PolicySync.push(a, b, fuelPushUnit())
        val after = b.readRows(TAB_REMOTE_FUEL)
        return fuelFromTabData(after, vehicleId) to pushResult
    }
}
