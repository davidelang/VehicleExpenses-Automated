package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.CameraZoomControl
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Currency
import java.util.Locale

private enum class CaptureViewState { Live, Processing, Results }

private const val LITERS_PER_GALLON = 3.785411784

private fun convertVolumeForSave(value: Double, fromUnit: String, toUnit: String): Double {
    if (fromUnit == toUnit) return value
    return when {
        fromUnit == "G" && toUnit == "L" -> value * LITERS_PER_GALLON
        fromUnit == "L" && toUnit == "G" -> value / LITERS_PER_GALLON
        else -> value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFillupScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var gallons by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var loc by remember { mutableStateOf<String?>(null) }

    var captureViewState by rememberSaveable { mutableStateOf(CaptureViewState.Live) }
    var capturePending by remember { mutableStateOf(false) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val isProcessing = captureViewState == CaptureViewState.Processing
    val hasResults = captureViewState == CaptureViewState.Results
    var stageLabel by remember { mutableStateOf("") }
    var isPhotoSaving by remember { mutableStateOf(false) }
    var zoomControl by remember { mutableStateOf<CameraZoomControl?>(null) }

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember { prefs.getBoolean("debug_ocr_pipeline", false) }
    val saveFuelPhotos = remember { prefs.getBoolean("save_fuel_photos", true) }

    // TODO: Settings should surface "use system" as the default option for currency/volume.
    val systemCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        } catch (_: Exception) {
            "$"
        }
    }
    val systemVolumeUnit = remember {
        if (Locale.getDefault().country in setOf("US", "LR", "MM")) "G" else "L"
    }
    val prefCurrency = remember { prefs.getString("currency_symbol", null) }
    val prefVolume = remember { prefs.getString("volume_unit", null) }
    val defaultCurrency = remember {
        when {
            prefCurrency.isNullOrBlank() || prefCurrency == "system" -> systemCurrencySymbol
            else -> prefCurrency
        }
    }
    val defaultVolumeUnit = remember {
        when {
            prefVolume.isNullOrBlank() || prefVolume == "system" -> systemVolumeUnit
            else -> prefVolume
        }
    }
    val preferredVolumeUnit = remember {
        prefs.getString("volume_unit", null)?.takeIf { it.isNotBlank() && it != "system" }
            ?: systemVolumeUnit
    }
    var captureMode by rememberSaveable { mutableStateOf("odo") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrency) }
    var volumeUnit by rememberSaveable { mutableStateOf(defaultVolumeUnit) }
    var lastCaptureType by rememberSaveable { mutableStateOf("odo") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val focusManager = LocalFocusManager.current
    var isOdoFocused by remember { mutableStateOf(false) }
    var isVolumeFocused by remember { mutableStateOf(false) }
    var isCostFocused by remember { mutableStateOf(false) }
    var editingField by rememberSaveable { mutableStateOf<String?>(null) }
    val isPortraitFieldFocused = isOdoFocused || isVolumeFocused || isCostFocused
    val isLandscapeEditing = isLandscape && (isPortraitFieldFocused || editingField != null)
    val isEditing = if (isLandscape) isLandscapeEditing else isPortraitFieldFocused

    LaunchedEffect(isOdoFocused, isVolumeFocused, isCostFocused) {
        if (!isOdoFocused && !isVolumeFocused && !isCostFocused) {
            editingField = null
        }
    }

    val appendToDecimalField: (String, String) -> String = { current, digit ->
        when (digit) {
            "." -> if (current.contains(".")) current else if (current.isEmpty()) "0." else "$current."
            else -> if (current.length < 10) current + digit else current
        }
    }

    val onKeypadDigit: (String) -> Unit = { digit ->
        when (editingField) {
            "odo" -> if (digit.all { it.isDigit() } && odometer.length < 7) odometer += digit
            "cost" -> cost = appendToDecimalField(cost, digit)
            "volume" -> gallons = appendToDecimalField(gallons, digit)
        }
    }

    val onKeypadBackspace: () -> Unit = {
        when (editingField) {
            "odo" -> if (odometer.isNotEmpty()) odometer = odometer.dropLast(1)
            "cost" -> if (cost.isNotEmpty()) cost = cost.dropLast(1)
            "volume" -> if (gallons.isNotEmpty()) gallons = gallons.dropLast(1)
        }
    }

    val onKeypadDismiss: () -> Unit = {
        editingField = null
        focusManager.clearFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            NativePaddleEngine.releaseAllOdoBuffers()
            NativePaddleEngine.bufferSetA.unborrow()
            NativePaddleEngine.bufferSetA.clearCrops()
            // Reset buffer to 4:3 full-sensor size (4000x3072 ≈ 4080x3072 init)
            NativePaddleEngine.bufferSetA.resize(4000, 3072)
            displayBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
            displayBitmap = null
        }
    }

    val imageCapture: ImageCapture = remember {
        // 4:3 full sensor aspect (2048x1536 ~2000 wide fine for odo; long side wide in landscape; avoids 13:9)
        val resSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(
                android.util.Size(2048, 1536),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
            ))
            .build()
        ImageCapture.Builder()
            .setResolutionSelector(resSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

    val cameraOrCropArea = @Composable {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap!!.asImageBitmap(),
                    contentDescription = "Odometer Crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (isProcessing) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            text = "STAGE: $stageLabel",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture,
                    onZoomControlChanged = { zoomControl = it },
                    onImageCaptured = { imageProxy ->
                        if (capturePending) {
                            scope.launch(Dispatchers.Main.immediate) {
                                capturePending = false
                            }

                            val planes = imageProxy.planes
                            val isDirect = planes.all { it.buffer.isDirect }
                            if (!isDirect) {
                                imageProxy.close()
                                scope.launch(Dispatchers.Main) {
                                    captureViewState = CaptureViewState.Live
                                    Toast.makeText(context, "Error: Image buffer is not direct", Toast.LENGTH_LONG).show()
                                }
                                return@CameraPreview
                            }

                            val bufferSet = NativePaddleEngine.bufferSetA
                            // receives 4:3 ~2000w grab; resized to full sensor 4:3 buffer
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
                                planes[2].pixelStride
                            )
                            bufferSet.normalizeYUV()

                            val rotation = imageProxy.imageInfo.rotationDegrees
                            imageProxy.close()

                            if (captureMode == "odo") {
                                scope.launch(Dispatchers.Default) {
                                    try {
                                        val result = OcrHarness.runAutoFillPipeline(
                                            context = context,
                                            masterBuffer = bufferSet,
                                            allVehicles = vehicles,
                                            debug = debugMode,
                                            cameraRotationDegrees = rotation,
                                            onStage = { stage, bmp ->
                                                scope.launch(Dispatchers.Main) {
                                                    stageLabel = stage
                                                    displayBitmap = bmp
                                                }
                                            }
                                        )
                                        
                                        scope.launch(Dispatchers.Main) {
                                            if (result.error != null) {
                                                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                            }

                                            result.vehicleId?.let { selectedVehicleId = it }
                                            result.odometer?.let { odometer = it }

                                            if (debugMode && result.debugJson != null) {
                                                val timestamp = System.currentTimeMillis()
                                                val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "debug_ocr_odometer_$timestamp.json")
                                                file.writeText(result.debugJson)
                                                Toast.makeText(context, "Debug saved to Documents", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("QuickFill", "OCR Pipeline failed", e)
                                        scope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, "OCR failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        scope.launch(Dispatchers.Main) {
                                            captureViewState = if (displayBitmap != null) {
                                                CaptureViewState.Results
                                            } else {
                                                CaptureViewState.Live
                                            }
                                        }
                                    }
                                }
                            } else {
                                scope.launch(Dispatchers.Default) {
                                    try {
                                        val result = OcrHarness.runPumpCostVolPipeline(
                                            context = context,
                                            masterBuffer = bufferSet,
                                            debug = debugMode,
                                            cameraRotationDegrees = rotation,
                                            onStage = { stage, bmp ->
                                                scope.launch(Dispatchers.Main) {
                                                    stageLabel = stage
                                                    displayBitmap = bmp
                                                }
                                            }
                                        )
                                        scope.launch(Dispatchers.Main) {
                                            if (result.error != null) {
                                                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                            } else {
                                                result.volume?.let { gallons = it }
                                                result.cost?.let { cost = it }
                                                val filled = listOfNotNull(result.volume, result.cost)
                                                if (filled.isNotEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        "Pump: ${filled.joinToString(", ")}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(context, "Pump photo captured!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            if (debugMode && result.debugJson != null) {
                                                val timestamp = System.currentTimeMillis()
                                                val file = File(
                                                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                                                    "debug_ocr_pump_$timestamp.json"
                                                )
                                                file.writeText(result.debugJson)
                                                Toast.makeText(context, "Debug saved to Documents", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("QuickFill", "Pump OCR Pipeline failed", e)
                                        scope.launch(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Pump OCR failed: ${e.localizedMessage ?: "Unknown error"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } finally {
                                        scope.launch(Dispatchers.Main) {
                                            captureViewState = if (displayBitmap != null) {
                                                CaptureViewState.Results
                                            } else {
                                                CaptureViewState.Live
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                )
            }
            
            if (isProcessing && displayBitmap == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Full sensor 4:3 aspect (~2000 wide capture); used for A-panel letterbox sizing
    val captureAspectRatio = 2048f / 1536f

    val zoomButtonsContent = @Composable { modifier: Modifier ->
        zoomControl?.let { zoom ->
            if (zoom.availableRatios.size > 1 && displayBitmap == null) {
                Column(
                    modifier = modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    zoom.availableRatios.forEach { ratio ->
                        val selected = kotlin.math.abs(zoom.currentRatio - ratio) < 0.05f
                        FilledTonalButton(
                            onClick = { zoom.setZoomRatio(ratio) },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (ratio == ratio.toLong().toFloat()) {
                                    "${ratio.toLong()}x"
                                } else {
                                    "${ratio}x"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }

    val panelAContent = @Composable { zoomAllocatedToPanelD: Boolean ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.TopStart
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val fitsByHeight = maxWidth / maxHeight > captureAspectRatio
                val contentModifier = if (fitsByHeight) {
                    Modifier.fillMaxHeight().aspectRatio(captureAspectRatio)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(captureAspectRatio)
                }
                val contentWidth = if (fitsByHeight) maxHeight * captureAspectRatio else maxWidth
                val contentHeight = if (fitsByHeight) maxHeight else maxWidth / captureAspectRatio
                val hasRightBlank = contentWidth < maxWidth - 1.dp
                val hasBottomBlank = contentHeight < maxHeight - 1.dp

                Box(modifier = contentModifier.align(Alignment.TopStart)) {
                    cameraOrCropArea()
                }

                if (!zoomAllocatedToPanelD) {
                    zoomControl?.let { zoom ->
                        if (zoom.availableRatios.size > 1 && displayBitmap == null) {
                            when {
                                hasRightBlank -> zoomButtonsContent(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .width((maxWidth - contentWidth).coerceAtLeast(40.dp))
                                )
                                hasBottomBlank -> {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .padding(start = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        zoom.availableRatios.forEach { ratio ->
                                            val selected = kotlin.math.abs(zoom.currentRatio - ratio) < 0.05f
                                            FilledTonalButton(
                                                onClick = { zoom.setZoomRatio(ratio) },
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = if (ratio == ratio.toLong().toFloat()) {
                                                        "${ratio.toLong()}x"
                                                    } else {
                                                        "${ratio}x"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // No letterbox blanks — overlay only when D panel was not allocated.
                                    zoomButtonsContent(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val saveButtonContent = @Composable {
        val hasAnyData = odometer.isNotBlank() || cost.isNotBlank() || gallons.isNotBlank()
        val canSave = hasAnyData && selectedVehicleId != null && !isProcessing && !isPhotoSaving

        if (hasAnyData) {
            Button(
                onClick = {
                    selectedVehicleId?.let { vehicleId ->
                        val rawVolume = gallons.toDoubleOrNull() ?: 0.0
                        val saveVolume = if (rawVolume == 0.0) {
                            0.0
                        } else {
                            convertVolumeForSave(rawVolume, volumeUnit, preferredVolumeUnit)
                        }
                        // TODO future: persist non-default currency on FuelEntry (DB change later).
                        // Cost uses raw numeric value; currencySymbol is display-only this turn.
                        fuelViewModel.saveFuel(
                            FuelEntry(
                                vehicleId = vehicleId,
                                odometer = odometer.toIntOrNull() ?: 0,
                                gallons = saveVolume,
                                cost = cost.toDoubleOrNull() ?: 0.0,
                                timestamp = System.currentTimeMillis(),
                                photoUrl = photoUrl,
                                latitude = lat,
                                longitude = lon,
                                location = loc
                            )
                        )
                        NativePaddleEngine.releaseAllOdoBuffers()
                        NativePaddleEngine.bufferSetA.unborrow()
                        NativePaddleEngine.bufferSetA.clearCrops()
                        // Reset buffer to 4:3 full-sensor size after save
                        NativePaddleEngine.bufferSetA.resize(4000, 3072)
                        val oldBmp = displayBitmap
                        displayBitmap = null
                        oldBmp?.let {
                            if (!it.isRecycled) {
                                it.recycle()
                            }
                        }
                        navController.popBackStack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isPhotoSaving) "Saving Photo..." else "Save Fill-up")
            }
        }
    }

    val fieldsContent = @Composable {
        BoxWithConstraints(modifier = Modifier.wrapContentWidth()) {
            val stackedPump = maxWidth < 340.dp
            val cScrollState = rememberScrollState()
            val cScrollModifier = if (stackedPump) {
                Modifier.verticalScroll(cScrollState)
            } else {
                Modifier
            }

            val odoBorder = if (captureMode == "odo") {
                Modifier.border(2.dp, Color.Green, MaterialTheme.shapes.medium).padding(8.dp)
            } else {
                Modifier.padding(8.dp)
            }

            Column(modifier = Modifier.wrapContentWidth().then(cScrollModifier)) {
        // Group 1: Vehicle + Odo
        Column(modifier = Modifier.fillMaxWidth().then(odoBorder)) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                val vehicleName = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle"
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.widthIn(max = 160.dp)
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text("Vehicle") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        readOnly = true,
                        singleLine = true,
                        maxLines = 1,
                        prefix = {
                            Text(
                                text = vehicleName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.name) },
                                onClick = {
                                    selectedVehicleId = vehicle.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { if (it.length <= 7 && it.all { c -> c.isDigit() }) odometer = it },
                    label = { Text("Odo") },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 88.dp)
                        .onFocusChanged {
                            isOdoFocused = it.isFocused
                            if (it.isFocused) editingField = "odo"
                            else if (editingField == "odo") editingField = null
                        },
                    readOnly = isLandscape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Group 2: Volume + Cost
        val pumpBorder = if (captureMode == "pump") {
            Modifier.border(2.dp, Color.Green, MaterialTheme.shapes.medium).padding(8.dp)
        } else {
            Modifier.padding(8.dp)
        }

        Column(modifier = Modifier.fillMaxWidth().then(pumpBorder)) {
            val costField = @Composable { modifier: Modifier, imeAction: ImeAction ->
                var showCurrencyMenu by remember { mutableStateOf(false) }
                val currencySymbols = remember {
                    // TODO future: GPS-based default + local filter for symbol chooser
                    Currency.getAvailableCurrencies()
                        .map { it.getSymbol(Locale.getDefault()) }
                        .distinct()
                        .sorted()
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = {
                        Box {
                            SymbolLabel(
                                symbol = currencySymbol,
                                onClick = { showCurrencyMenu = true }
                            )
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                currencySymbols.forEach { symbol ->
                                    DropdownMenuItem(
                                        text = { Text(symbol) },
                                        onClick = {
                                            currencySymbol = symbol
                                            showCurrencyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = modifier.onFocusChanged {
                        isCostFocused = it.isFocused
                        if (it.isFocused) editingField = "cost"
                        else if (editingField == "cost") editingField = null
                    },
                    readOnly = isLandscape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        onDone = {
                            editingField = null
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true
                )
            }
            val swapButton = @Composable {
                IconButton(
                    onClick = {
                        val temp = cost
                        cost = gallons
                        gallons = temp
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    ArrowsIcon(
                        orientation = ArrowOrientation.Horizontal,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            val volumeField = @Composable { modifier: Modifier ->
                var showVolumeMenu by remember { mutableStateOf(false) }
                val volumeSymbols = remember { listOf("G", "L") }
                OutlinedTextField(
                    value = gallons,
                    onValueChange = { gallons = it },
                    label = {
                        Box {
                            SymbolLabel(
                                symbol = volumeUnit,
                                onClick = { showVolumeMenu = true }
                            )
                            DropdownMenu(
                                expanded = showVolumeMenu,
                                onDismissRequest = { showVolumeMenu = false }
                            ) {
                                volumeSymbols.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            volumeUnit = unit
                                            showVolumeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = modifier.onFocusChanged {
                        isVolumeFocused = it.isFocused
                        if (it.isFocused) editingField = "volume"
                        else if (editingField == "volume") editingField = null
                    },
                    readOnly = isLandscape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            editingField = null
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true
                )
            }

            if (stackedPump) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    costField(Modifier.widthIn(min = 80.dp), ImeAction.Next)
                    swapButton()
                }
                volumeField(Modifier.widthIn(min = 80.dp))
            } else {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    costField(Modifier.widthIn(min = 80.dp), ImeAction.Next)
                    swapButton()
                    volumeField(Modifier.widthIn(min = 80.dp))
                }
            }
        }
            }
        }
    }

    val onShutterClick = {
        lastCaptureType = captureMode
        captureViewState = CaptureViewState.Processing
        capturePending = true
        photoUrl = null
        
        val playSound = prefs.getBoolean("shutter_sounds", true)
        if (playSound) {
            try {
                android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
            } catch (e: Exception) {
                Log.e("QuickFill", "Failed to play shutter sound", e)
            }
        }

        if (saveFuelPhotos) {
            isPhotoSaving = true
            try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.display
                } else {
                    (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                }
                val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
                imageCapture.targetRotation = rotation
            } catch (e: Exception) {
                Log.e("QuickFill", "Failed to set target rotation", e)
            }

            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "fuel_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DCIM + "/Camera")
                }
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                resolver,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri
                        android.util.Log.i("QuickFill", "Photo saved directly to MediaStore: $savedUri")
                        photoUrl = savedUri?.toString()
                        isPhotoSaving = false
                    }
                    override fun onError(exception: ImageCaptureException) {
                        android.util.Log.e("QuickFill", "Photo capture failed", exception)
                        isPhotoSaving = false
                    }
                }
            )
        }
    }

    val onModeSwitchClick = {
        if (!isProcessing) {
            if (hasResults) {
                displayBitmap = null
                captureViewState = CaptureViewState.Live
            }
            captureMode = if (captureMode == "odo") "pump" else "odo"
        }
    }

    val cameraControlsContent = @Composable { isLand: Boolean ->
        val mainButtonState = when {
            isProcessing -> CaptureViewState.Processing
            displayBitmap != null -> CaptureViewState.Results
            else -> CaptureViewState.Live
        }
        val onMainButtonClick = {
            when (mainButtonState) {
                CaptureViewState.Live -> onShutterClick()
                CaptureViewState.Processing -> {
                    capturePending = false
                    captureViewState = CaptureViewState.Live
                }
                CaptureViewState.Results -> {
                    displayBitmap = null
                    captureViewState = CaptureViewState.Live
                }
            }
        }

        Box(
            modifier = if (isLand) Modifier.wrapContentSize() else Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLand) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onModeSwitchClick,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        ArrowsIcon(
                            orientation = ArrowOrientation.Vertical,
                            modifier = Modifier.size(24.dp),
                            tint = if (isProcessing) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                    RoundActionButton(
                        viewState = mainButtonState,
                        onClick = onMainButtonClick
                    )
                    // Save in B — landscape branch
                    saveButtonContent()
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onModeSwitchClick,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            ArrowsIcon(
                                orientation = ArrowOrientation.Vertical,
                                modifier = Modifier.size(24.dp),
                                tint = if (isProcessing) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                        RoundActionButton(
                            viewState = mainButtonState,
                            onClick = onMainButtonClick
                        )
                    }
                    // Save in B — portrait branch
                    saveButtonContent()
                }
            }
        }
    }

    // Save lives in B per spec; zoom prefers extra space after A+B+C (D) before overlay in A.
    // 3-panel layout: A (camera), B (controls), C (results), D (zoom when extra space)
    val bPanelSize = 150.dp // 150.dp to fit Save button text readably in B for both orientations
    // Fixed estimate for volume field (6 digits + decimal + unit label); measured width deferred.
    val cPanelMinWidth = 220.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Dimension-based layout: wide physical space triggers 3-panel Row regardless of config orientation
        val useLandscapeLayout = maxWidth > maxHeight * 1.2f
        val zoomDWidth = if (useLandscapeLayout && !isEditing) {
            val bAllocated = bPanelSize + 8.dp
            val cAllocated = cPanelMinWidth + 16.dp
            val dCandidate = 56.dp
            val aPanelMaxWidth = (maxWidth - bAllocated - cAllocated).coerceAtLeast(0.dp)
            val fitsByHeight = aPanelMaxWidth / maxHeight > captureAspectRatio
            val aContentWidth = if (fitsByHeight) maxHeight * captureAspectRatio else aPanelMaxWidth
            // Extra horizontal space after aspect-sized A content + B + C — reserve for D first.
            val extra = maxWidth - aContentWidth - bAllocated - cAllocated
            if (extra > dCandidate + 4.dp && zoomControl != null && displayBitmap == null) dCandidate else 0.dp
        } else {
            0.dp
        }
        if (useLandscapeLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isEditing) {
                    // Landscape editing: keypad replaces A+B space
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        NumericKeypad(
                            onDigit = onKeypadDigit,
                            onBackspace = onKeypadBackspace,
                            onDismiss = onKeypadDismiss
                        )
                    }
                } else {
                    // Panel A — camera/results (remaining space)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        panelAContent(zoomDWidth > 0.dp)
                    }
                    // Panel B — navigation controls + Save (bottom)
                    Box(
                        modifier = Modifier
                            .width(bPanelSize)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        cameraControlsContent(true)
                    }
                }
                // Panel C — fields only (Save lives in B)
                Column(
                    modifier = if (isEditing) {
                        Modifier
                            .wrapContentWidth()
                            .widthIn(min = cPanelMinWidth)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .wrapContentWidth()
                            .widthIn(min = cPanelMinWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsContent()
                }
                // Panel D — landscape non-editing only (portrait/editing unchanged)
                if (!isEditing && zoomDWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(zoomDWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        zoomButtonsContent(Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            // Portrait: A+B hidden during field edit (system keyboard); restored on focus clear
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isPortraitFieldFocused) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        panelAContent(false)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        cameraControlsContent(false)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(min = cPanelMinWidth)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(
                            if (isPortraitFieldFocused) Modifier.weight(1f, fill = false)
                            else Modifier.verticalScroll(rememberScrollState())
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsContent()
                }
            }
        }
    }
}

@Composable
private fun SymbolLabel(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = symbol.take(1),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 48.dp
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(
        modifier = modifier.width(keySize * 3),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.size(keySize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(keySize)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Dismiss keypad"
            )
        }
    }
}

@Composable
private fun RoundActionButton(
    viewState: CaptureViewState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .then(
                when (viewState) {
                    CaptureViewState.Live -> Modifier
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray, CircleShape)
                    CaptureViewState.Processing -> Modifier
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                    CaptureViewState.Results -> Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                }
            )
    ) {
        when (viewState) {
            CaptureViewState.Live -> Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            )
            CaptureViewState.Processing -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel processing",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(32.dp)
            )
            CaptureViewState.Results -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Retry",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private enum class ArrowOrientation { Vertical, Horizontal }

@Composable
private fun ArrowsIcon(
    orientation: ArrowOrientation,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeW = width * 0.15f

        when (orientation) {
            ArrowOrientation.Vertical -> {
                val leftX = width * 0.35f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(leftX, height * 0.8f),
                    end = androidx.compose.ui.geometry.Offset(leftX, height * 0.2f),
                    strokeWidth = strokeW
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(leftX - width * 0.15f, height * 0.4f)
                        lineTo(leftX, height * 0.2f)
                        lineTo(leftX + width * 0.15f, height * 0.4f)
                    },
                    color = tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )
                val rightX = width * 0.65f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(rightX, height * 0.2f),
                    end = androidx.compose.ui.geometry.Offset(rightX, height * 0.8f),
                    strokeWidth = strokeW
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(rightX - width * 0.15f, height * 0.6f)
                        lineTo(rightX, height * 0.8f)
                        lineTo(rightX + width * 0.15f, height * 0.6f)
                    },
                    color = tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )
            }
            ArrowOrientation.Horizontal -> {
                val topY = height * 0.35f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(width * 0.8f, topY),
                    end = androidx.compose.ui.geometry.Offset(width * 0.2f, topY),
                    strokeWidth = strokeW
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(width * 0.4f, topY - height * 0.15f)
                        lineTo(width * 0.2f, topY)
                        lineTo(width * 0.4f, topY + height * 0.15f)
                    },
                    color = tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )
                val bottomY = height * 0.65f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(width * 0.2f, bottomY),
                    end = androidx.compose.ui.geometry.Offset(width * 0.8f, bottomY),
                    strokeWidth = strokeW
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(width * 0.6f, bottomY - height * 0.15f)
                        lineTo(width * 0.8f, bottomY)
                        lineTo(width * 0.6f, bottomY + height * 0.15f)
                    },
                    color = tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )
            }
        }
    }
}

