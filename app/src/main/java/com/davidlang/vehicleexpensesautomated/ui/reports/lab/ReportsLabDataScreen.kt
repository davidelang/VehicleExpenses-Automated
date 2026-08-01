package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

/** Which all-time series drives the child report vehicle dropdown (Part E). */
enum class LabVehicleMembership {
    /** Live fuel rows only. */
    FUEL,
    /** Live expenses only. */
    EXPENSE,
    /** Union of fuel + expense vehicleIds (monthly costs). */
    FUEL_OR_EXPENSE,
}

/**
 * Vehicles that have **all-time** data for this report type (not period-filtered).
 * Includes Unknown (id=0) only when all-time rows use vehicleId=0.
 * "All vehicles" remains a separate option in the filter bar.
 */
fun dataBearingVehicles(
    allVehicles: List<Vehicle>,
    allFuel: List<FuelEntry>,
    allExpenses: List<ExpenseEntry>,
    membership: LabVehicleMembership,
): List<Vehicle> {
    val ids = when (membership) {
        LabVehicleMembership.FUEL ->
            allFuel.map { it.vehicleId }.toSet()
        LabVehicleMembership.EXPENSE ->
            allExpenses.map { it.vehicleId }.toSet()
        LabVehicleMembership.FUEL_OR_EXPENSE ->
            (allFuel.map { it.vehicleId } + allExpenses.map { it.vehicleId }).toSet()
    }
    if (ids.isEmpty()) return emptyList()
    val byId = allVehicles.filter { !it.deleted }.associateBy { it.id }
    val out = mutableListOf<Vehicle>()
    for (id in ids.sorted()) {
        val v = byId[id]
        if (v != null) {
            // Show system Unassigned as display name Unknown in reports only
            out += if (id == VehicleRepository.UNASSIGNED_VEHICLE_ID) {
                v.copy(name = "Unknown")
            } else {
                v
            }
        } else if (id == VehicleRepository.UNASSIGNED_VEHICLE_ID) {
            out += Vehicle(
                id = 0,
                name = "Unknown",
                syncId = VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID,
            )
        } else {
            out += Vehicle(id = id, name = "Vehicle $id")
        }
    }
    return out.sortedBy { it.name.lowercase() }
}

/** Shared data + filter state for all Lab report screens. */
@Composable
fun rememberLabReportData(
    membership: LabVehicleMembership = LabVehicleMembership.FUEL,
): LabReportData {
    val context = LocalContext.current
    val fuelVm: FuelViewModel = hiltViewModel()
    val expenseVm: ExpenseViewModel = hiltViewModel()
    val vehicleVm: VehicleViewModel = hiltViewModel()
    val fuels by fuelVm.fuelEntries.collectAsState()
    val expenses by expenseVm.expenses.collectAsState()
    val vehiclesAll by vehicleVm.vehicles.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(ReportsLabPrefs.load(context)) }
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val defaultStored = remember { CurrencyCodes.settingsDefaultStored(context) }
    val volumeLabel = remember {
        VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))
    }
    val allFuel = remember(fuels) { fuels.filter { !it.deleted } }
    val allExpenses = remember(expenses) { expenses.filter { !it.deleted } }
    // Period only filters data — vehicle list is all-time membership (E6)
    val pickerVehicles = remember(vehiclesAll, allFuel, allExpenses, membership) {
        dataBearingVehicles(vehiclesAll, allFuel, allExpenses, membership)
    }
    // Clear SINGLE only when that vehicle vanishes; do not thrash ALL/EACH.
    LaunchedEffect(pickerVehicles, filter.vehicleMode, filter.vehicleId) {
        if (filter.vehicleMode != LabVehicleMode.SINGLE) return@LaunchedEffect
        val vid = filter.vehicleId ?: run {
            val next = filter.copy(vehicleMode = LabVehicleMode.ALL, vehicleId = null)
            ReportsLabPrefs.save(context, next)
            filter = next
            return@LaunchedEffect
        }
        if (pickerVehicles.isEmpty()) return@LaunchedEffect // list still loading — don't thrash
        if (pickerVehicles.none { it.id == vid }) {
            val next = filter.copy(vehicleMode = LabVehicleMode.ALL, vehicleId = null)
            ReportsLabPrefs.save(context, next)
            filter = next
        }
    }
    val fFuel = remember(allFuel, filter) { filterFuel(allFuel, filter) }
    val fExp = remember(allExpenses, filter) { filterExpenses(allExpenses, filter) }
    return LabReportData(
        filter = filter,
        setFilter = { filter = it },
        vehicles = pickerVehicles,
        allVehicles = vehiclesAll,
        fuel = fFuel,
        allFuel = allFuel,
        expenses = fExp,
        allExpenses = allExpenses,
        defaultSymbol = defaultSymbol,
        defaultStored = defaultStored,
        volumeLabel = volumeLabel,
        context = context,
    )
}

data class LabReportData(
    val filter: ReportsLabFilterState,
    val setFilter: (ReportsLabFilterState) -> Unit,
    val vehicles: List<Vehicle>,
    val allVehicles: List<Vehicle>,
    val fuel: List<FuelEntry>,
    val allFuel: List<FuelEntry>,
    val expenses: List<ExpenseEntry>,
    val allExpenses: List<ExpenseEntry> = emptyList(),
    val defaultSymbol: String,
    val defaultStored: String,
    val volumeLabel: String,
    val context: android.content.Context,
) {
    fun vehicleName(id: Int): String {
        // Reports always label system bucket as Unknown (never "Unassigned")
        if (id == 0 || id == VehicleRepository.UNASSIGNED_VEHICLE_ID) return "Unknown"
        return allVehicles.firstOrNull { it.id == id }?.name ?: "Vehicle $id"
    }

    fun filterVehicleLabel(): String =
        when (filter.vehicleMode) {
            LabVehicleMode.ALL -> "All vehicles"
            LabVehicleMode.EACH -> "Each vehicle"
            LabVehicleMode.SINGLE -> filter.vehicleId?.let { vehicleName(it) } ?: "All vehicles"
        }

    /** Group fuel by vehicle when EACH; otherwise single bucket null key = combined. */
    fun fuelByVehicleScope(): Map<Int?, List<FuelEntry>> =
        when (filter.vehicleMode) {
            LabVehicleMode.EACH -> fuel.groupBy { it.vehicleId }
            else -> mapOf(null to fuel)
        }

    fun expensesByVehicleScope(): Map<Int?, List<ExpenseEntry>> =
        when (filter.vehicleMode) {
            LabVehicleMode.EACH -> expenses.groupBy { it.vehicleId }
            else -> mapOf(null to expenses)
        }
}
