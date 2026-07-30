package com.davidlang.vehicleexpensesautomated.ui.trip

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.trip.TripTimeline
import com.davidlang.vehicleexpensesautomated.data.trip.TripTypes
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Open-only trip tracking: insert fuel rows with non-blank [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.tripType].
 * Close trip = Personal start (same path). No separate close event kind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTrackingScreen(
    navController: NavHostController? = null,
) {
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allVehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState(initial = emptyList())

    val vehicles = remember(allVehicles) {
        allVehicles.filter { !it.deleted && !isUnassigned(it) }
    }

    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var selectedTripType by rememberSaveable { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var eventTimestamp by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var showManageTypes by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        } else if (selectedVehicleId != null && vehicles.none { it.id == selectedVehicleId }) {
            selectedVehicleId = vehicles.firstOrNull()?.id
        }
    }

    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
    val typeOptions = remember(selectedVehicle?.tripTypesJson, selectedVehicle?.id) {
        TripTypes.parse(selectedVehicle?.tripTypesJson)
    }

    LaunchedEffect(selectedVehicleId, typeOptions) {
        if (selectedTripType.isBlank() || typeOptions.none { it.equals(selectedTripType, ignoreCase = true) }) {
            selectedTripType = typeOptions.firstOrNull().orEmpty()
        }
    }

    val openTrip = remember(selectedVehicleId, fuelEntries) {
        selectedVehicleId?.let { TripTimeline.currentOpenTrip(it, fuelEntries) }
    }
    val openType = openTrip?.tripType
    val canClose = openTrip != null &&
        !openType.equals(TripTypes.PERSONAL, ignoreCase = true)

    val dateTimeFmt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dayMillis = state.selectedDateMillis ?: eventTimestamp
                        val calDay = Calendar.getInstance().apply { timeInMillis = dayMillis }
                        val existing = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
                        val merged = Calendar.getInstance().apply {
                            set(Calendar.YEAR, calDay.get(Calendar.YEAR))
                            set(Calendar.MONTH, calDay.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, calDay.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, existing.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, existing.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        eventTimestamp = merged.timeInMillis
                        showDatePicker = false
                        val c = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val withTime = Calendar.getInstance().apply {
                                    timeInMillis = eventTimestamp
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                eventTimestamp = withTime.timeInMillis
                            },
                            c.get(Calendar.HOUR_OF_DAY),
                            c.get(Calendar.MINUTE),
                            false,
                        ).show()
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showManageTypes && selectedVehicle != null) {
        ManageTripTypesDialog(
            vehicle = selectedVehicle,
            onDismiss = { showManageTypes = false },
            onSave = { updated ->
                scope.launch {
                    try {
                        vehicleViewModel.updateVehicle(updated)
                        showManageTypes = false
                        Toast.makeText(context, "Trip types saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Save types failed: ${e.message ?: e}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    fun saveTripStart(type: String, toastLabel: String) {
        val vehicleId = selectedVehicleId
        if (vehicleId == null) {
            Toast.makeText(context, "Select a vehicle", Toast.LENGTH_SHORT).show()
            return
        }
        val odo = odometer.trim().toIntOrNull()
        if (odo == null || odo <= 0) {
            Toast.makeText(context, "Odometer is required", Toast.LENGTH_SHORT).show()
            return
        }
        val tripType = type.trim()
        if (tripType.isEmpty()) {
            Toast.makeText(context, "Trip type is required", Toast.LENGTH_SHORT).show()
            return
        }
        val entry = TripTimeline.buildTripStart(
            vehicleId = vehicleId,
            odometer = odo,
            tripType = tripType,
            timestamp = eventTimestamp,
            latitude = latitude,
            longitude = longitude,
        )
        fuelViewModel.saveFuel(entry)
        statusLine = "$toastLabel: $tripType @ $odo mi"
        Toast.makeText(context, statusLine, Toast.LENGTH_SHORT).show()
        eventTimestamp = System.currentTimeMillis()
        latitude = null
        longitude = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trip Tracking", style = MaterialTheme.typography.titleLarge)
        Text(
            "Open-only: each Start (or Close→Personal) writes a fuel row with Trip Type. " +
                "Next open on this vehicle ends the prior segment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExposedDropdownMenuBox(
            expanded = vehicleMenuExpanded,
            onExpandedChange = { vehicleMenuExpanded = !vehicleMenuExpanded },
        ) {
            OutlinedTextField(
                value = selectedVehicle?.name ?: "Select vehicle",
                onValueChange = {},
                readOnly = true,
                label = { Text("Vehicle") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = vehicleMenuExpanded,
                onDismissRequest = { vehicleMenuExpanded = false },
            ) {
                vehicles.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(v.name) },
                        onClick = {
                            selectedVehicleId = v.id
                            vehicleMenuExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = odometer,
            onValueChange = { odometer = it.filter { ch -> ch.isDigit() } },
            label = { Text("Odometer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = !typeMenuExpanded },
        ) {
            OutlinedTextField(
                value = selectedTripType.ifBlank { typeOptions.firstOrNull().orEmpty() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Trip type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                typeOptions.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = {
                            selectedTripType = t
                            typeMenuExpanded = false
                        },
                    )
                }
            }
        }

        Text(
            text = "When: ${dateTimeFmt.format(Date(eventTimestamp))}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text("Set date/time")
            }
            OutlinedButton(
                onClick = { eventTimestamp = System.currentTimeMillis() },
            ) {
                Text("Use now")
            }
        }

        Text(
            text = when {
                openTrip == null -> "No open trip on this vehicle (implicit personal)."
                else -> "Open: ${openTrip.tripType} since odo ${openTrip.odometer}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Button(
            onClick = {
                saveTripStart(
                    type = selectedTripType.ifBlank { typeOptions.firstOrNull().orEmpty() },
                    toastLabel = "Started",
                )
            },
            enabled = selectedVehicleId != null && typeOptions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start trip")
        }

        OutlinedButton(
            onClick = {
                saveTripStart(type = TripTypes.PERSONAL, toastLabel = "Closed (Personal start)")
            },
            enabled = canClose && selectedVehicleId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close trip (Personal)")
        }

        OutlinedButton(
            onClick = {
                if (selectedVehicle == null) {
                    Toast.makeText(context, "Select a vehicle first", Toast.LENGTH_SHORT).show()
                } else {
                    showManageTypes = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Manage types…")
        }

        statusLine?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun isUnassigned(v: Vehicle): Boolean =
    v.id == VehicleRepository.UNASSIGNED_VEHICLE_ID ||
        v.syncId == VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageTripTypesDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit,
) {
    var types by remember(vehicle.id, vehicle.tripTypesJson) {
        mutableStateOf(TripTypes.parse(vehicle.tripTypesJson).toMutableList())
    }
    var newName by remember { mutableStateOf("") }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trip types — ${vehicle.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "First item is the Start-trip default. Renames do not rewrite past fuel Trip Type strings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                types.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${index + 1}. $name",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { types = TripTypes.moveUp(types, index).toMutableList() },
                            enabled = index > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = { types = TripTypes.moveDown(types, index).toMutableList() },
                            enabled = index < types.lastIndex,
                        ) { Text("↓") }
                        TextButton(
                            onClick = {
                                renameIndex = index
                                renameText = name
                            },
                        ) { Text("Rename") }
                    }
                }
                if (renameIndex != null) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val i = renameIndex ?: return@TextButton
                                types = TripTypes.rename(types, i, renameText).toMutableList()
                                renameIndex = null
                            },
                        ) { Text("Apply rename") }
                        TextButton(onClick = { renameIndex = null }) { Text("Cancel") }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Add type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        types = TripTypes.add(types, newName).toMutableList()
                        newName = ""
                    },
                ) { Text("Add") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val json = TripTypes.format(types)
                    onSave(vehicle.copy(tripTypesJson = json))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
