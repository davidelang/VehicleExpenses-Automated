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
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck

/**
 * Bridge between VE entity tabs and remotetable L3 policy (pilot).
 *
 * Default production path remains coordinator LWW; enable via prefs
 * [PREF_USE_POLICY_SYNC_MERGE_ACKS] / [PREF_USE_POLICY_SYNC_EXPENSES] (default **false**).
 *
 * Uses [MergeSync] **lww_row** for bidirectional key+timestamp merge
 * (PolicySync.push is also available for one-way experiments).
 * Fuel is **out of scope** for this bridge pilot.
 */
object PolicySyncBridge {

    /** vehicle_settings / SyncDestinationStore prefs key. Default false. */
    const val PREF_USE_POLICY_SYNC_MERGE_ACKS = "use_policy_sync_merge_acks"

    /** vehicle_settings prefs key for Expenses tab pilot. Default false. */
    const val PREF_USE_POLICY_SYNC_EXPENSES = "use_policy_sync_expenses"

    private const val TAB_LOCAL = "LocalAcks"
    private const val TAB_REMOTE = "RemoteAcks"
    private const val TAB_LOCAL_EXP = "LocalExpenses"
    private const val TAB_REMOTE_EXP = "RemoteExpenses"

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
}
