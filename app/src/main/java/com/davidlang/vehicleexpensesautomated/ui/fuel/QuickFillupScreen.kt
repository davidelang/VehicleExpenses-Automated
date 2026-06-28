package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class CaptureViewState { Live, Processing, Results }

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
    var selectedVehicleId by remember { mutableStateOf<Int?>(null) }
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

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember { prefs.getBoolean("debug_ocr_pipeline", false) }
    val saveFuelPhotos = remember { prefs.getBoolean("save_fuel_photos", true) }

    val defaultCurrency = remember { prefs.getString("currency_symbol", "$") ?: "$" }
    val defaultVolumeUnit = remember { prefs.getString("volume_unit", "G") ?: "G" }
    var captureMode by rememberSaveable { mutableStateOf("odo") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrency) }
    var volumeUnit by rememberSaveable { mutableStateOf(defaultVolumeUnit) }
    var lastCaptureType by rememberSaveable { mutableStateOf("odo") }

    val focusManager = LocalFocusManager.current
    var isOdoFocused by remember { mutableStateOf(false) }
    var isVolumeFocused by remember { mutableStateOf(false) }
    var isCostFocused by remember { mutableStateOf(false) }
    val isEditing = isOdoFocused || isVolumeFocused || isCostFocused

    DisposableEffect(Unit) {
        onDispose {
            NativePaddleEngine.releaseAllOdoBuffers()
            NativePaddleEngine.bufferSetA.unborrow()
            NativePaddleEngine.bufferSetA.clearCrops()
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
        val resSelector = ResolutionSelector.Builder()
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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

    val fieldsAndSaveContent = @Composable {
        val odoBorder = if (captureMode == "odo") {
            Modifier.border(2.dp, Color.Green, MaterialTheme.shapes.medium).padding(8.dp)
        } else {
            Modifier.padding(8.dp)
        }
        
        // Group 1: Vehicle + Odo
        Column(modifier = Modifier.fillMaxWidth().then(odoBorder)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle",
                        onValueChange = {},
                        label = { Text("Vehicle") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        readOnly = true,
                        singleLine = true
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
                        .weight(1.0f)
                        .onFocusChanged { isOdoFocused = it.isFocused },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text(currencySymbol) },
                    trailingIcon = {
                        var showCurrencyMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showCurrencyMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("⚙️", style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                listOf("$", "€", "£", "¥", "C$").forEach { symbol ->
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
                    modifier = Modifier
                        .weight(1.0f)
                        .onFocusChanged { isCostFocused = it.isFocused },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    singleLine = true
                )
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
                OutlinedTextField(
                    value = gallons,
                    onValueChange = { gallons = it },
                    label = { Text(volumeUnit) },
                    trailingIcon = {
                        IconButton(
                            onClick = { volumeUnit = if (volumeUnit == "G") "L" else "G" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(if (volumeUnit == "G") "L" else "G", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier
                        .weight(1.0f)
                        .onFocusChanged { isVolumeFocused = it.isFocused },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save Button
        Button(
            onClick = {
                selectedVehicleId?.let { vehicleId ->
                    fuelViewModel.saveFuel(
                        FuelEntry(
                            vehicleId = vehicleId,
                            odometer = odometer.toIntOrNull() ?: 0,
                            gallons = gallons.toDoubleOrNull() ?: 0.0,
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
            enabled = !isProcessing && !isPhotoSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isPhotoSaving) "Saving Photo..." else "Save Fill-up")
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RoundActionButton(
                        viewState = mainButtonState,
                        onClick = onMainButtonClick
                    )
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
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RoundActionButton(
                        viewState = mainButtonState,
                        onClick = onMainButtonClick
                    )
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
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    // 3-panel layout: A (camera), B (controls), C (results)
    val bPanelSize = 72.dp // icon-size guided, one button wide
    val cPanelMinWidth = 220.dp // volume field 6+decimal+label estimate

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Panel A — camera/results (remaining space)
                if (!isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        cameraOrCropArea()
                    }
                    // Panel B — navigation controls
                    Box(
                        modifier = Modifier
                            .width(bPanelSize)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        cameraControlsContent(true)
                    }
                }
                // Panel C — fields + save
                Column(
                    modifier = if (isEditing) {
                        Modifier
                            .fillMaxSize()
                            .widthIn(min = cPanelMinWidth)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .widthIn(min = cPanelMinWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsAndSaveContent()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Panel A — camera/results
                if (!isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        cameraOrCropArea()
                    }
                    // Panel B — navigation controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        cameraControlsContent(false)
                    }
                }
                // Panel C — fields + save
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(min = cPanelMinWidth)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(if (isEditing) Modifier else Modifier.verticalScroll(rememberScrollState())),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsAndSaveContent()
                }
            }
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

