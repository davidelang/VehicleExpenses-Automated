package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.components.firstReadableFuelPhotoUri
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasArchiveIdentity
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasDeadLocalOnly
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.components.AppDateTimeField
import com.davidlang.vehicleexpensesautomated.ui.components.AppOutlinedBack
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
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
    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val destId = remember { SyncDestinationStore(context).photoDestination()?.id }
    val defaultCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        } catch (_: Exception) {
            "$"
        }
    }

    var loaded by remember { mutableStateOf<FuelEntry?>(null) }
    var vehicleId by rememberSaveable { mutableStateOf(0) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrencySymbol) }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var tripType by rememberSaveable { mutableStateOf("") }
    var isPartialFill by rememberSaveable { mutableStateOf(false) }
    var economyIgnored by rememberSaveable { mutableStateOf(false) }
    var timestampMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var photoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var vehicleDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(fuelId) {
        val entry = fuelViewModel.getFuelById(fuelId)
        if (entry == null) {
            Toast.makeText(context, "Fill not found", Toast.LENGTH_LONG).show()
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
        location = e.location.orEmpty()
        notes = e.notes.orEmpty()
        tripType = e.tripType
        isPartialFill = e.isPartialFill
        economyIgnored = e.economyIgnored
        timestampMs = e.timestamp
        photoUrl = e.photoUrl
    }

    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val thumbUri = firstReadableFuelPhotoUri(photoUrl, photoStorage)
    val canFetch = thumbUri == null && fuelHasArchiveIdentity(loaded, destId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureScreenHeader("Edit fill")

        // Photos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumbUri != null -> {
                    Image(
                        painter = rememberAsyncImagePainter(thumbUri),
                        contentDescription = "Fill photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (FuelPhotoJson.parse(photoUrl).size > 1) {
                        Text(
                            "${FuelPhotoJson.parse(photoUrl).size} photos",
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                canFetch -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Photo in archive only", style = MaterialTheme.typography.bodyMedium)
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
                                            Toast.makeText(context, "Image fetched", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not fetch image", Toast.LENGTH_LONG).show()
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
                else -> Text("No photo", style = MaterialTheme.typography.bodyMedium)
            }
        }

        ExposedDropdownMenuBox(
            expanded = vehicleDropdown,
            onExpandedChange = { vehicleDropdown = !vehicleDropdown },
        ) {
            val name = vehicles.firstOrNull { it.id == vehicleId }?.name ?: "Vehicle $vehicleId"
            OutlinedTextField(
                value = name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Vehicle") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = vehicleDropdown,
                onDismissRequest = { vehicleDropdown = false },
            ) {
                vehicles.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(v.name) },
                        onClick = {
                            vehicleId = v.id
                            vehicleDropdown = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = odometer,
            onValueChange = { odometer = it.filter { ch -> ch.isDigit() } },
            label = { Text("Odometer (${UnitFormat.distanceUnitShortLabel()})") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = tripType,
            onValueChange = { tripType = it },
            label = { Text("Trip type (blank = normal fill)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = volume,
            onValueChange = { volume = it },
            label = {
                Text(
                    "Volume (${VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))})",
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            label = { Text("Cost") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = currencySymbol,
            onValueChange = { currencySymbol = it },
            label = { Text("Currency") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        AppDateTimeField(
            label = "Date/time: ${dateFmt.format(Date(timestampMs))}",
            onClick = { showDatePicker = true },
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = { isPartialFill = it })
            Text("Partial fill (not a full-fill anchor)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = economyIgnored, onCheckedChange = { economyIgnored = it })
            Text("Ignore for economy metrics")
        }

        Button(
            onClick = {
                val base = loaded ?: return@Button
                isSaving = true
                val updated = base.copy(
                    vehicleId = vehicleId,
                    odometer = odometer.toIntOrNull() ?: 0,
                    gallons = volume.toDoubleOrNull() ?: 0.0,
                    cost = cost.toDoubleOrNull() ?: 0.0,
                    currency = CurrencyCodes.fromSymbolOrCode(currencySymbol),
                    timestamp = timestampMs,
                    location = location.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    tripType = tripType.trim(),
                    isPartialFill = isPartialFill,
                    economyIgnored = economyIgnored,
                    photoUrl = photoUrl,
                )
                fuelViewModel.updateFuel(updated)
                Toast.makeText(context, "Fill saved", Toast.LENGTH_SHORT).show()
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
