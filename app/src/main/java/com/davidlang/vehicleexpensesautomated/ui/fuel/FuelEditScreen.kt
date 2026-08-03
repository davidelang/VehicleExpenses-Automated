package com.davidlang.vehicleexpensesautomated.ui.fuel

import com.davidlang.vehicleexpensesautomated.R

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.components.AppDateTimeField
import com.davidlang.vehicleexpensesautomated.ui.components.AppOutlinedBack
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
import com.davidlang.vehicleexpensesautomated.ui.components.ZoomablePhotoThumb
import com.davidlang.vehicleexpensesautomated.ui.components.firstReadableFuelPhotoUri
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasArchiveIdentity
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasDeadLocalOnly
import com.davidlang.vehicleexpensesautomated.ui.components.photoUrisFromJsonOrPath
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEditScreen(
    navController: NavHostController,
    fuelId: Long,
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val photoStorage = settingsViewModel.photoStorageManager
    val scope = rememberCoroutineScope()
    val allVehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val vehicles = remember(allVehicles) { allVehicles.forUserPicker() }
    val destId = remember { SyncDestinationStore(context).photoDestination()?.id }
    val defaultCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        } catch (_: Exception) {
            "$"
        }
    }
    val multiColumn = LocalConfiguration.current.screenWidthDp >= 480

    var loaded by remember { mutableStateOf<FuelEntry?>(null) }
    var vehicleId by rememberSaveable { mutableStateOf(0) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrencySymbol) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tripType by rememberSaveable { mutableStateOf("") }
    /** Loaded trip type was non-blank — keep field visible even if user clears. */
    var showTripType by rememberSaveable { mutableStateOf(false) }
    var isPartialFill by rememberSaveable { mutableStateOf(false) }
    var economyIgnored by rememberSaveable { mutableStateOf(false) }
    var timestampMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var photoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var vehicleDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }
    var locationExpanded by rememberSaveable { mutableStateOf(false) }
    var locLat by rememberSaveable { mutableStateOf("") }
    var locLon by rememberSaveable { mutableStateOf("") }
    var locAccuracy by rememberSaveable { mutableStateOf("") }
    var locName by rememberSaveable { mutableStateOf("") }
    var locAddress by rememberSaveable { mutableStateOf("") }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(fuelId) {
        val entry = fuelViewModel.getFuelById(fuelId)
        if (entry == null) {
            Toast.makeText(context, context.getString(R.string.fuel_fill_not_found), Toast.LENGTH_LONG).show()
            navController.popBackStack()
            return@LaunchedEffect
        }
        var e = entry
        if (fuelHasDeadLocalOnly(e.photoUrl, photoStorage)) {
            e = fuelViewModel.scrubUnreadableFuelPhotos(e)
        }
        loaded = e
        vehicleId = e.vehicleId
        odometer = if (e.odometer == 0) "" else e.odometer.toString()
        volume = if (e.gallons == 0.0) "" else e.gallons.toString()
        cost = if (e.cost == 0.0) "" else e.cost.toString()
        currencySymbol = CurrencyCodes.displaySymbol(e.currency, defaultCurrencySymbol)
        notes = e.notes.orEmpty()
        tripType = e.tripType
        showTripType = e.tripType.isNotBlank()
        isPartialFill = e.isPartialFill
        economyIgnored = e.economyIgnored
        timestampMs = e.timestamp
        photoUrl = e.photoUrl
        val blob = FuelLocationJson.parseBlob(e.location) ?: FuelLocationJson.Blob(
            name = e.location?.takeIf { !it.trim().startsWith("{") }.orEmpty(),
        )
        locLat = blob.lat?.toString().orEmpty()
        locLon = blob.lon?.toString().orEmpty()
        locAccuracy = blob.accuracyM?.toString().orEmpty()
        locName = blob.name
        locAddress = blob.address
        locationExpanded = blob.hasCoords() || blob.hasPlace()
    }

    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val thumbUri = firstReadableFuelPhotoUri(photoUrl, photoStorage)
    val canFetch = thumbUri == null && fuelHasArchiveIdentity(loaded, destId)
    val locationSummary = remember(locName, locAddress, locLat, locLon) {
        when {
            locName.isNotBlank() && locAddress.isNotBlank() -> "$locName — $locAddress"
            locName.isNotBlank() -> locName
            locAddress.isNotBlank() -> locAddress
            locLat.isNotBlank() && locLon.isNotBlank() -> "$locLat, $locLon"
            else -> "No location"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureScreenHeader("Edit fill")

        when {
            thumbUri != null -> {
                val uris = photoUrisFromJsonOrPath(photoUrl).ifEmpty { listOf(thumbUri) }
                ZoomablePhotoThumb(uris = uris, contentDescription = stringResource(R.string.fuel_fill_photo))
            }
            canFetch -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.fuel_photo_in_archive_only), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            val entry = loaded ?: return@Button
                            scope.launch {
                                isFetching = true
                                try {
                                    val scrubbed = fuelViewModel.scrubUnreadableFuelPhotos(entry)
                                    val local = fuelViewModel.downloadFuelPhoto(scrubbed)
                                    val refreshed = fuelViewModel.getFuelById(fuelId)
                                    if (refreshed != null) {
                                        loaded = refreshed
                                        photoUrl = refreshed.photoUrl
                                    }
                                    if (local != null) {
                                        Toast.makeText(context, context.getString(R.string.fuel_image_fetched), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.fuel_could_not_fetch_image), Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isFetching = false
                                }
                            }
                        },
                        enabled = !isFetching,
                    ) {
                        Text(if (isFetching) "Fetching…" else "Fetch image from archive")
                    }
                }
            }
            else -> Text(stringResource(R.string.fuel_no_photo), style = MaterialTheme.typography.bodyMedium)
        }

        // Vehicle | Odometer
        if (multiColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VehiclePickerField(
                    vehicles = vehicles,
                    vehicleId = vehicleId,
                    expanded = vehicleDropdown,
                    onExpandedChange = { vehicleDropdown = it },
                    onSelect = { vehicleId = it },
                    modifier = Modifier.weight(1f),
                )
                CaretEnabledOutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Odometer (${UnitFormat.distanceUnitShortLabel(context)})") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    showCaretButtons = true,
                )
            }
        } else {
            VehiclePickerField(
                vehicles = vehicles,
                vehicleId = vehicleId,
                expanded = vehicleDropdown,
                onExpandedChange = { vehicleDropdown = it },
                onSelect = { vehicleId = it },
                modifier = Modifier.fillMaxWidth(),
            )
            CaretEnabledOutlinedTextField(
                value = odometer,
                onValueChange = { odometer = it.filter { ch -> ch.isDigit() } },
                label = { Text("Odometer (${UnitFormat.distanceUnitShortLabel(context)})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                showCaretButtons = true,
            )
        }

        // F2: trip type only when loaded row had a type (trip-start edit)
        if (showTripType) {
            CaretEnabledOutlinedTextField(
                value = tripType,
                onValueChange = { tripType = it },
                label = { Text(stringResource(R.string.fuel_trip_type)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Currency | Cost | Volume (currency before cost)
        if (multiColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaretEnabledOutlinedTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = { Text(stringResource(R.string.fuel_currency)) },
                    modifier = Modifier.weight(0.9f),
                    singleLine = true,
                )
                CaretEnabledOutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text(stringResource(R.string.fuel_cost)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    showCaretButtons = true,
                )
                CaretEnabledOutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it },
                    label = {
                        Text(
                            "Vol (${VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))})",
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    showCaretButtons = true,
                )
            }
        } else {
            CaretEnabledOutlinedTextField(
                value = currencySymbol,
                onValueChange = { currencySymbol = it },
                label = { Text(stringResource(R.string.fuel_currency)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CaretEnabledOutlinedTextField(
                value = cost,
                onValueChange = { cost = it },
                label = { Text(stringResource(R.string.fuel_cost)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                showCaretButtons = true,
            )
            CaretEnabledOutlinedTextField(
                value = volume,
                onValueChange = { volume = it },
                label = {
                    Text(
                        "Volume (${VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))})",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                showCaretButtons = true,
            )
        }

        AppDateTimeField(
            label = "Date/time: ${dateFmt.format(Date(timestampMs))}",
            onClick = { showDatePicker = true },
        )
        CaretEnabledOutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.fuel_notes)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
        )

        // Location: summary + expand to structured fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.fuel_location), style = MaterialTheme.typography.titleSmall)
                Text(
                    locationSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                )
            }
            TextButton(onClick = { locationExpanded = !locationExpanded }) {
                Text(if (locationExpanded) "Hide details" else "Location details")
            }
        }
        if (locationExpanded) {
            if (multiColumn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CaretEnabledOutlinedTextField(
                        value = locLat,
                        onValueChange = { locLat = it },
                        label = { Text(stringResource(R.string.fuel_latitude)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    CaretEnabledOutlinedTextField(
                        value = locLon,
                        onValueChange = { locLon = it },
                        label = { Text(stringResource(R.string.fuel_longitude)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            } else {
                CaretEnabledOutlinedTextField(
                    value = locLat,
                    onValueChange = { locLat = it },
                    label = { Text(stringResource(R.string.fuel_latitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CaretEnabledOutlinedTextField(
                    value = locLon,
                    onValueChange = { locLon = it },
                    label = { Text(stringResource(R.string.fuel_longitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            CaretEnabledOutlinedTextField(
                value = locAccuracy,
                onValueChange = { locAccuracy = it },
                label = { Text(stringResource(R.string.fuel_accuracy_m)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CaretEnabledOutlinedTextField(
                value = locName,
                onValueChange = { locName = it },
                label = { Text(stringResource(R.string.fuel_place_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CaretEnabledOutlinedTextField(
                value = locAddress,
                onValueChange = { locAddress = it },
                label = { Text(stringResource(R.string.fuel_address)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = { isPartialFill = it })
            Text(stringResource(R.string.fuel_partial_fill_not_a_full_fill_anchor))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = economyIgnored, onCheckedChange = { economyIgnored = it })
            Text(stringResource(R.string.fuel_ignore_for_economy_metrics))
        }

        Button(
            onClick = {
                val base = loaded ?: return@Button
                isSaving = true
                val blob = FuelLocationJson.Blob(
                    lat = locLat.toDoubleOrNull(),
                    lon = locLon.toDoubleOrNull(),
                    accuracyM = locAccuracy.toDoubleOrNull(),
                    name = locName.trim(),
                    address = locAddress.trim(),
                )
                val locationJson = FuelLocationJson.encode(blob)
                val updated = base.copy(
                    vehicleId = vehicleId,
                    odometer = odometer.toIntOrNull() ?: 0,
                    gallons = volume.toDoubleOrNull() ?: 0.0,
                    cost = cost.toDoubleOrNull() ?: 0.0,
                    currency = CurrencyCodes.fromSymbolOrCode(currencySymbol),
                    timestamp = timestampMs,
                    location = locationJson,
                    notes = notes.trim().ifBlank { null },
                    tripType = if (showTripType) tripType.trim() else "",
                    isPartialFill = isPartialFill,
                    economyIgnored = economyIgnored,
                    photoUrl = photoUrl,
                )
                fuelViewModel.updateFuel(updated)
                Toast.makeText(context, context.getString(R.string.fuel_fill_saved), Toast.LENGTH_SHORT).show()
                isSaving = false
                navController.popBackStack()
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSaving) "Saving…" else "Save")
        }
        AppOutlinedBack(onClick = { navController.popBackStack() })
    }

    if (showDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val state = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day ->
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val min = cal.get(Calendar.MINUTE)
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = day
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, min)
                        }
                        timestampMs = merged.timeInMillis
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclePickerField(
    vehicles: List<com.davidlang.vehicleexpensesautomated.data.model.Vehicle>,
    vehicleId: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        val name = vehicles.firstOrNull { it.id == vehicleId }?.name ?: "Vehicle $vehicleId"
        OutlinedTextField(
            value = name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.fuel_vehicle)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.name) },
                    onClick = {
                        onSelect(v.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
