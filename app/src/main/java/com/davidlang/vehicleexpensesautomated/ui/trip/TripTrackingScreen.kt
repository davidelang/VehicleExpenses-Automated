package com.davidlang.vehicleexpensesautomated.ui.trip

import android.app.TimePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.data.trip.TripTimeline
import com.davidlang.vehicleexpensesautomated.data.trip.TripTypes
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookup
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.ui.util.CaptureLocation
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.NetworkStatus
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.components.AppDateTimeField
import com.davidlang.vehicleexpensesautomated.ui.components.CaptureButtonState
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField
import com.davidlang.vehicleexpensesautomated.ui.components.DiskSaveIconButton
import com.davidlang.vehicleexpensesautomated.ui.components.ExposedDropdownMenuWithManageFooter
import com.davidlang.vehicleexpensesautomated.ui.components.LocationConfirmBlock
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.RoundCaptureButton
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "TripTracking"

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
        allVehicles.forUserPicker()
    }

    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var selectedTripType by rememberSaveable { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var eventTimestamp by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateOverrideActive by rememberSaveable { mutableStateOf(false) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var deviceAccuracyM by remember { mutableStateOf<Double?>(null) }
    var locationStatus by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf("") }
    var placeAddress by remember { mutableStateOf("") }
    var lookupName by remember { mutableStateOf<String?>(null) }
    var lookupAddress by remember { mutableStateOf<String?>(null) }
    var lookupSource by remember { mutableStateOf<String?>(null) }
    var showManageTypes by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf<String?>(null) }
    /** When true, event time is refreshed to now at save. */
    var timeIsNow by rememberSaveable { mutableStateOf(true) }

    RegisterPageHelp(
        title = "Start trip",
        "Open-only: each Start (or Close→Personal) writes a fuel row with Trip Type. " +
            "Next open on this vehicle ends the prior segment.",
        "White circle — capture odometer with the camera. Disk — start trip with selected type. " +
            "Stop — Personal now at this location (time is now + confirm location if GPS held).",
        "Time is now (default) stamps the save with wall-clock time; uncheck to pick a date/time.",
    )
    var capturePending by remember { mutableStateOf(false) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var ocrStage by remember { mutableStateOf("") }

    // One-shot device GPS per screen visit (not per OCR capture).
    LaunchedEffect(Unit) {
        val fix = CaptureLocation.captureLocationOrNull(context)
        if (fix != null) {
            latitude = fix.latitude
            longitude = fix.longitude
            deviceAccuracyM = if (fix.hasAccuracy()) fix.accuracy.toDouble() else null
        }
    }

    LaunchedEffect(latitude, longitude) {
        val la = latitude
        val lo = longitude
        if (la == null || lo == null) {
            locationStatus = ""
            lookupName = null
            lookupAddress = null
            lookupSource = null
            return@LaunchedEffect
        }
        if (!NetworkStatus.hasUsableNetwork(context)) {
            locationStatus = "Offline — place lookup when online"
            lookupName = null
            lookupAddress = null
            lookupSource = null
            return@LaunchedEffect
        }
        locationStatus = "Looking up address…"
        val result = LocationLookup.lookup(
            lat = la,
            lon = lo,
            kind = LocationLookupKind.ADDRESS_ONLY,
            accuracyM = deviceAccuracyM,
            uiTimeout = true,
        )
        if (result != null && result.hasPlace()) {
            placeName = result.name
            placeAddress = result.address
            lookupName = result.name
            lookupAddress = result.address
            lookupSource = result.source
            // No "Resolved:" banner — address fields show the place once (D6/B4).
            locationStatus = ""
        } else {
            lookupName = null
            lookupAddress = null
            lookupSource = null
            locationStatus = "No address found (will retry after save if online)"
        }
    }

    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                CameraResolutionPicker.resolutionSelector(CameraCaptureProfile.OCR_MEDIUM),
            )
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

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
                        timeIsNow = false
                        dateOverrideActive = true
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
                                dateOverrideActive = true
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
        val base = FuelLocationJson.fromCoords(
            latitude,
            longitude,
            deviceAccuracyM,
            source = "device",
        ) ?: FuelLocationJson.Blob()
        // Non-blank place → confirmed=true; blank → coords-only. No picker on address-only Trip.
        val placeBlank = placeName.isBlank() && placeAddress.isBlank()
        val saveBlob = if (!placeBlank) {
            base.withPlace(
                name = placeName,
                address = placeAddress,
                confirmed = true,
                source = FuelLocationJson.placeSourceForConfirm(
                    placeName,
                    placeAddress,
                    lookupName,
                    lookupAddress,
                    lookupSource,
                ),
                kind = LocationLookupKind.ADDRESS_ONLY.blobKindTag(),
                lookedUpAt = System.currentTimeMillis(),
            )
        } else {
            base.coordsOnly()
        }
        val ts = if (timeIsNow) System.currentTimeMillis() else eventTimestamp
        val entry = TripTimeline.buildTripStart(
            vehicleId = vehicleId,
            odometer = odo,
            tripType = tripType,
            timestamp = ts,
            latitude = null,
            longitude = null,
            accuracyM = null,
            photoUrl = null,
        ).copy(location = FuelLocationJson.encode(saveBlob))
        fuelViewModel.saveFuel(entry)
        if (saveBlob.hasCoordsWithoutPlace()) {
            LocationLookupScheduler.enqueueSoon(context)
        }
        statusLine = "$toastLabel: $tripType @ ${UnitFormat.odometerReadingLabel(odo)}"
        Toast.makeText(context, statusLine, Toast.LENGTH_SHORT).show()
        eventTimestamp = System.currentTimeMillis()
        timeIsNow = true
        // Keep once-per-screen device lat/lon while staying on Trip Tracking.
    }

    val canStart = selectedVehicleId != null &&
        typeOptions.isNotEmpty() &&
        (odometer.trim().toIntOrNull() ?: 0) > 0
    val canPersonalNow = selectedVehicleId != null &&
        (odometer.trim().toIntOrNull() ?: 0) > 0

    // QF-aligned: camera 45%, fields 55% + scroll.
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                imageCapture = imageCapture,
                onImageCaptured = { imageProxy ->
                    if (!capturePending) {
                        imageProxy.close()
                        return@CameraPreview
                    }
                    capturePending = false
                    isProcessingOcr = true
                    ocrStage = "Reading…"
                    val planes = imageProxy.planes
                    val isDirect = planes.all { it.buffer.isDirect }
                    if (!isDirect) {
                        imageProxy.close()
                        isProcessingOcr = false
                        Toast.makeText(context, "Error: Image buffer is not direct", Toast.LENGTH_LONG).show()
                        return@CameraPreview
                    }
                    val bufferSet = NativePaddleEngine.bufferSetA
                    if (bufferSet.width != imageProxy.width || bufferSet.height != imageProxy.height) {
                        bufferSet.resize(imageProxy.width, imageProxy.height)
                    }
                    bufferSet.borrowYuv(
                        planes[0].buffer,
                        planes[1].buffer,
                        planes[2].buffer,
                        planes[0].rowStride,
                        planes[1].rowStride,
                        planes[1].pixelStride,
                        planes[2].pixelStride,
                    )
                    bufferSet.normalizeYUV()
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close()

                    scope.launch(Dispatchers.Default) {
                        try {
                            val result = OcrHarness.runAutoFillPipeline(
                                context = context,
                                masterBuffer = bufferSet,
                                allVehicles = vehicles,
                                debug = false,
                                cameraRotationDegrees = rotation,
                                onStage = { stage, _ ->
                                    scope.launch(Dispatchers.Main) { ocrStage = stage }
                                },
                            )
                            withContext(Dispatchers.Main) {
                                if (result.error != null) {
                                    Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                    statusLine = result.error
                                } else {
                                    result.vehicleId?.let { matchedId ->
                                        if (vehicles.any { it.id == matchedId }) {
                                            selectedVehicleId = matchedId
                                        }
                                    }
                                    result.odometer?.let { odoStr ->
                                        val digits = odoStr.filter { it.isDigit() }
                                        if (digits.isNotEmpty()) odometer = digits
                                    }
                                    statusLine = buildString {
                                        append("Camera: ")
                                        result.odometer?.let { append("odo $it") }
                                        result.vehicleId?.let { vid ->
                                            val n = vehicles.find { it.id == vid }?.name
                                            if (n != null) append(" · $n")
                                        }
                                        if (isEmpty()) append("captured — enter odo if needed")
                                    }
                                    Toast.makeText(context, statusLine, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "OCR pipeline failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "OCR failed: ${e.localizedMessage ?: "Unknown"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isProcessingOcr = false
                                ocrStage = ""
                            }
                        }
                    }
                },
            )
            if (isProcessingOcr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        if (ocrStage.isNotBlank()) {
                            Text(
                                text = ocrStage,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // QF-style control row: save (start) · shutter · stop (Personal now)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiskSaveIconButton(
                onClick = {
                    saveTripStart(
                        type = selectedTripType.ifBlank { typeOptions.firstOrNull().orEmpty() },
                        toastLabel = "Started",
                    )
                },
                enabled = canStart && !isProcessingOcr,
                contentDescription = "Start trip",
            )
            RoundCaptureButton(
                viewState = if (isProcessingOcr) {
                    CaptureButtonState.Processing
                } else {
                    CaptureButtonState.Live
                },
                onClick = {
                    if (isProcessingOcr) {
                        capturePending = false
                        isProcessingOcr = false
                        ocrStage = ""
                        return@RoundCaptureButton
                    }
                    try {
                        android.media.MediaActionSound()
                            .play(android.media.MediaActionSound.SHUTTER_CLICK)
                    } catch (_: Exception) { /* optional */ }
                    capturePending = true
                    isProcessingOcr = true
                    ocrStage = "Capturing…"
                },
                contentDescriptionLive = "Capture odometer",
            )
            IconButton(
                onClick = {
                    // Personal now at this location: force timeIsNow + confirm location if coords.
                    timeIsNow = true
                    saveTripStart(
                        type = TripTypes.PERSONAL,
                        toastLabel = "Personal now at location",
                    )
                },
                enabled = canPersonalNow && !isProcessingOcr,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Personal now at this location",
                    tint = if (canPersonalNow && !isProcessingOcr) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        statusLine?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ExposedDropdownMenuBox(
            expanded = vehicleMenuExpanded,
            onExpandedChange = { vehicleMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedVehicle?.name ?: "Select vehicle",
                onValueChange = {},
                readOnly = true,
                label = { Text("Vehicle") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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

        com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
            value = odometer,
            onValueChange = { odometer = it.filter { ch -> ch.isDigit() } },
            showCaretButtons = true,
            label = { Text("Odometer (${UnitFormat.distanceUnitShortLabel()})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (latitude != null && longitude != null) {
            LocationConfirmBlock(
                statusLine = locationStatus,
                name = placeName,
                address = placeAddress,
                onNameChange = { placeName = it },
                onAddressChange = { placeAddress = it },
                // ADDRESS_ONLY: editable fields only (no Wrong station picker)
                pickerKind = LocationLookupKind.ADDRESS_ONLY,
                hasCoords = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = timeIsNow,
                    onCheckedChange = { checked ->
                        timeIsNow = checked
                        if (checked) eventTimestamp = System.currentTimeMillis()
                    },
                )
                Text("Time is now", style = MaterialTheme.typography.bodySmall)
            }
        }

        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedTripType.ifBlank { typeOptions.firstOrNull().orEmpty() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Trip type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenuWithManageFooter(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
                items = typeOptions,
                onItemClick = { t ->
                    selectedTripType = t
                    typeMenuExpanded = false
                },
                manageLabel = "Manage types…",
                onManageClick = {
                    typeMenuExpanded = false
                    if (selectedVehicle == null) {
                        Toast.makeText(context, "Select a vehicle first", Toast.LENGTH_SHORT).show()
                    } else {
                        showManageTypes = true
                    }
                },
            )
        }

        if (!timeIsNow) {
            AppDateTimeField(
                label = "Time: ${dateTimeFmt.format(Date(eventTimestamp))}",
                onClick = { showDatePicker = true },
            )
        }

        Text(
            text = when {
                openTrip == null -> "No open trip on this vehicle (implicit personal)."
                else ->
                    "Open: ${openTrip.tripType} since ${UnitFormat.odometerReadingLabel(openTrip.odometer)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (canClose) {
            Text(
                "Stop icon = Personal now (also closes open non-Personal). Disk = Start with selected type.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        } // scroll fields Column
    } // outer fillMaxSize Column
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
                        IconButton(
                            onClick = { types = TripTypes.moveUp(types, index).toMutableList() },
                            enabled = index > 0,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(
                            onClick = { types = TripTypes.moveDown(types, index).toMutableList() },
                            enabled = index < types.lastIndex,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                renameIndex = index
                                renameText = name
                            },
                        ) { Text("Rename") }
                        IconButton(
                            onClick = {
                                if (types.size <= 1) {
                                    // Keep at least one type
                                    return@IconButton
                                }
                                types = TripTypes.removeAt(types, index).toMutableList()
                                if (renameIndex == index) {
                                    renameIndex = null
                                } else if (renameIndex != null && renameIndex!! > index) {
                                    renameIndex = renameIndex!! - 1
                                }
                            },
                            enabled = types.size > 1,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete type",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                if (renameIndex != null) {
                    CaretEnabledOutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true,
                        showCaretButtons = false,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CaretEnabledOutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Type") },
                        singleLine = true,
                        showCaretButtons = false,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            types = TripTypes.add(types, newName).toMutableList()
                            newName = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add") }
                }
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
