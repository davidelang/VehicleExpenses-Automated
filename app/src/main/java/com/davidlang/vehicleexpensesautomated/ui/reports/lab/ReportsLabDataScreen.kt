package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

/** Shared data + filter state for all Lab report screens. */
@Composable
fun rememberLabReportData(): LabReportData {
    val context = LocalContext.current
    val fuelVm: FuelViewModel = hiltViewModel()
    val expenseVm: ExpenseViewModel = hiltViewModel()
    val vehicleVm: VehicleViewModel = hiltViewModel()
    val fuels by fuelVm.fuelEntries.collectAsState()
    val expenses by expenseVm.expenses.collectAsState()
    val vehicles by vehicleVm.vehicles.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(ReportsLabPrefs.load(context)) }
    val activeVehicles = remember(vehicles) {
        vehicles.filter {
            !it.deleted && it.id != 0 &&
                it.syncId != com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID
        }
    }
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val defaultStored = remember { CurrencyCodes.settingsDefaultStored(context) }
    val volumeLabel = remember {
        VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))
    }
    val fFuel = remember(fuels, filter) { filterFuel(fuels, filter) }
    val fExp = remember(expenses, filter) { filterExpenses(expenses, filter) }
    return LabReportData(
        filter = filter,
        setFilter = { filter = it },
        vehicles = activeVehicles,
        allVehicles = vehicles,
        fuel = fFuel,
        /** Unfiltered (period/vehicle) fuel for trip segment end points outside the period. */
        allFuel = fuels.filter { !it.deleted },
        expenses = fExp,
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
    val defaultSymbol: String,
    val defaultStored: String,
    val volumeLabel: String,
    val context: android.content.Context,
) {
    fun vehicleName(id: Int): String =
        allVehicles.firstOrNull { it.id == id }?.name
            ?: if (id == 0) "Unknown" else "Vehicle $id"

    fun filterVehicleLabel(): String =
        filter.vehicleId?.let { vehicleName(it) } ?: "All vehicles"
}
