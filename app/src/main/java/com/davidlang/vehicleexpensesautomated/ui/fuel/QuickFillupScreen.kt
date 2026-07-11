package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

/** In-memory photo pointer until FuelEntry Save (tag dash|pump). */
private data class SessionPhoto(val uri: String, val ts: Long)

/** Map captureMode odo→dash, pump→pump. */
private fun photoTagForCaptureMode(captureMode: String): String {
    return if (captureMode == "pump") "pump" else "dash"
}

/** Compact JSON array for FuelEntry.photoUrl; null if empty. */
private fun sessionPhotosToJson(photos: Map<String, SessionPhoto>): String? {
    if (photos.isEmpty()) return null
    val arr = org.json.JSONArray()
    // Stable order: dash then pump then any other tags
    val keys = photos.keys.sortedWith(compareBy({ if (it == "dash") 0 else if (it == "pump") 1 else 2 }, { it }))
    for (tag in keys) {
        val p = photos[tag] ?: continue
        arr.put(
            org.json.JSONObject().apply {
                put("tag", tag)
                put("uri", p.uri)
                put("ts", p.ts)
            }
        )
    }
    return arr.toString()
}

/** One-shot fallback: write a JPEG of [bitmap] into DCIM/Camera as fuel_*.jpg (same roll as stock Camera). */
private fun saveBitmapToDcimCamera(context: android.content.Context, bitmap: Bitmap): android.net.Uri? {
    return try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "fuel_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DCIM + "/Camera"
                )
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                throw IllegalStateException("JPEG compress failed")
            }
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val done = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        }
        uri
    } catch (e: Exception) {
        Log.e("QuickFill", "Fallback DCIM/Camera save failed", e)
        null
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
    /** Session photos keyed by tag (dash/pump); written to DB only on Save as JSON. */
    val sessionPhotos = remember { mutableStateMapOf<String, SessionPhoto>() }
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
    /** Null when idle/ok; set while saving or after a failed Camera-roll save. */
    var photoSaveStatus by remember { mutableStateOf<String?>(null) }
    var zoomControl by remember { mutableStateOf<CameraZoomControl?>(null) }

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember { prefs.getBoolean("debug_ocr_pipeline", false) }

    // TODO: Settings should surface "use system" as the default option for currency/volume.
    val systemCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        } catch (_: Exception) {
            "$"
        }
    }
    val prefCurrency = remember { prefs.getString("currency_symbol", null) }
    val defaultCurrency = remember {
        when {
            prefCurrency.isNullOrBlank() || prefCurrency == "system" -> systemCurrencySymbol
            else -> prefCurrency
        }
    }
    // DB stores volume in preferred unit; convert UI unit → preferred at save.
    val preferredVolumeUnit = remember {
        com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.resolvedPreferredVolumeUnit(context)
    }
    val defaultVolumeUnit = preferredVolumeUnit
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
        // Use aspect strategy to prefer device's native/correct aspect (4:3 for this sensor); ~2000 wide fine for odo
        val resSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
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

    // 4:3 aspect for A-panel letterbox sizing (matches strategy-selected capture aspect)
    val captureAspectRatio = 4f / 3f

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
            contentAlignment = Alignment.BottomCenter
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val fitsByHeight = maxWidth / maxHeight > captureAspectRatio
                // Landscape: keep proven letterbox path (user-accepted).
                // Portrait: fill all of A; single-axis letterbox via FIT inside PreviewView/Image
                // (avoids stacked 4:3 box + FIT that left broad black on three sides).
                val contentModifier = if (isLandscape) {
                    if (fitsByHeight) {
                        Modifier.fillMaxHeight().aspectRatio(captureAspectRatio)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(captureAspectRatio)
                    }
                } else {
                    Modifier.fillMaxSize()
                }
                val contentWidth = if (isLandscape) {
                    if (fitsByHeight) maxHeight * captureAspectRatio else maxWidth
                } else {
                    // Active FIT area size (for zoom blank placement), not the full A box.
                    if (fitsByHeight) maxHeight * captureAspectRatio else maxWidth
                }
                val contentHeight = if (isLandscape) {
                    if (fitsByHeight) maxHeight else maxWidth / captureAspectRatio
                } else {
                    if (fitsByHeight) maxHeight else maxWidth / captureAspectRatio
                }
                val hasRightBlank = contentWidth < maxWidth - 1.dp
                val hasBottomBlank = contentHeight < maxHeight - 1.dp

                Box(
                    modifier = contentModifier.align(
                        if (isLandscape) Alignment.BottomCenter else Alignment.Center
                    )
                ) {
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
                                            .padding(bottom = 0.dp),
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
                                    // No letterbox blanks — overlay at bottom-end; camera size wins.
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

    val saveButtonContent: @Composable (Modifier) -> Unit = { modifier ->
        val hasAnyData = odometer.isNotBlank() || cost.isNotBlank() || gallons.isNotBlank()
        val canSave = hasAnyData && selectedVehicleId != null && !isProcessing && !isPhotoSaving

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            photoSaveStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPhotoSaving) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = {
                    selectedVehicleId?.let { vehicleId ->
                        val odoTrim = odometer.trim()
                        val costTrim = cost.trim()
                        val galTrim = gallons.trim()
                        // Auto partial: any of odo/cost/gallons blank after trim.
                        val isPartialFill = odoTrim.isBlank() || costTrim.isBlank() || galTrim.isBlank()
                        val rawVolume = galTrim.toDoubleOrNull() ?: 0.0
                        val saveVolume = if (rawVolume == 0.0) {
                            0.0
                        } else {
                            convertVolumeForSave(rawVolume, volumeUnit, preferredVolumeUnit)
                        }
                        // TODO future: persist non-default currency on FuelEntry (DB change later).
                        // Cost uses raw numeric value; currencySymbol is display-only this turn.
                        val photoUrlJson = sessionPhotosToJson(sessionPhotos)
                        fuelViewModel.saveFuel(
                            FuelEntry(
                                vehicleId = vehicleId,
                                odometer = odoTrim.toIntOrNull() ?: 0,
                                gallons = saveVolume,
                                cost = costTrim.toDoubleOrNull() ?: 0.0,
                                timestamp = System.currentTimeMillis(),
                                photoUrl = photoUrlJson,
                                latitude = lat,
                                longitude = lon,
                                location = loc,
                                isPartialFill = isPartialFill
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
                        // Stay on Quick Fill for back-to-back fills: blank fields + live camera.
                        odometer = ""
                        cost = ""
                        gallons = ""
                        sessionPhotos.clear()
                        photoSaveStatus = null
                        capturePending = false
                        captureViewState = CaptureViewState.Live
                        Toast.makeText(
                            context,
                            if (isPartialFill) "Partial fill-up saved" else "Fill-up saved",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
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

            val textMeasurer = rememberTextMeasurer()
            val longestVehicle = vehicles.maxOfOrNull { it.name } ?: "Select vehicle"
            val density = LocalDensity.current
            val vehicleTextWidth = with(density) {
                textMeasurer.measure(longestVehicle, style = MaterialTheme.typography.bodyLarge).size.width.toDp()
            } + 48.dp
            val vehicleFieldWidth = vehicleTextWidth.coerceIn(80.dp, 172.dp)

            val odoBorder = if (captureMode == "odo") {
                Modifier.border(2.dp, Color.Green, MaterialTheme.shapes.medium).padding(8.dp)
            } else {
                Modifier.padding(8.dp)
            }

            Column(modifier = Modifier.wrapContentWidth().then(cScrollModifier)) {
        // Group 1: Vehicle + Odo
        Column(modifier = Modifier.wrapContentWidth().then(odoBorder)) {
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
                    modifier = Modifier.widthIn(max = vehicleFieldWidth)
                ) {
                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = {},
                        label = { Text("Vehicle") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        readOnly = true,
                        singleLine = true,
                        maxLines = 1
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
                        .widthIn(min = 64.dp, max = 88.dp)
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

        Column(modifier = Modifier.wrapContentWidth().then(pumpBorder)) {
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
                    costField(Modifier.widthIn(min = 56.dp, max = 80.dp), ImeAction.Next)
                    swapButton()
                }
                // One-character volume bump: 84.dp max (was 76.dp) per 2026-06-28 portrait screenshot feedback.
                volumeField(Modifier.widthIn(min = 56.dp, max = 84.dp))
            } else {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    costField(Modifier.widthIn(min = 56.dp, max = 80.dp), ImeAction.Next)
                    swapButton()
                    // One-character volume bump: 84.dp max (was 76.dp) per 2026-06-28 portrait screenshot feedback.
                    volumeField(Modifier.widthIn(min = 56.dp, max = 84.dp))
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
        // Do not clear other tags' session photos on shutter; re-shot tag is replaced on success.
        photoSaveStatus = null
        val photoTag = photoTagForCaptureMode(captureMode)

        val playSound = prefs.getBoolean("shutter_sounds", true)
        if (playSound) {
            try {
                android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
            } catch (e: Exception) {
                Log.e("QuickFill", "Failed to play shutter sound", e)
            }
        }

        // Live prefs each shutter so Settings toggle applies same session.
        val saveFuelPhotosNow = prefs.getBoolean("save_fuel_photos", true)
        if (saveFuelPhotosNow) {
            isPhotoSaving = true
            photoSaveStatus = "Saving photo…"
            try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                }
                val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
                imageCapture.targetRotation = rotation
            } catch (e: Exception) {
                Log.e("QuickFill", "Failed to set target rotation", e)
            }

            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "fuel_${System.currentTimeMillis()}.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DCIM + "/Camera"
                        )
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
                            if (savedUri == null) {
                                photoSaveStatus = "Photo save failed — entry will have no photo"
                                Toast.makeText(
                                    context,
                                    "Could not save fuel photo to Camera roll: missing MediaStore URI",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                sessionPhotos[photoTag] = SessionPhoto(
                                    uri = savedUri.toString(),
                                    ts = System.currentTimeMillis()
                                )
                                photoSaveStatus = null
                                Toast.makeText(context, "Photo saved to Camera", Toast.LENGTH_SHORT).show()
                            }
                            isPhotoSaving = false
                        }
                        override fun onError(exception: ImageCaptureException) {
                            android.util.Log.e("QuickFill", "Photo capture failed", exception)
                            // Optional reliability: one fallback JPEG encode of last analysis/display frame.
                            val fallbackBmp = displayBitmap
                            val fallbackUri = if (fallbackBmp != null && !fallbackBmp.isRecycled) {
                                saveBitmapToDcimCamera(context, fallbackBmp)
                            } else {
                                null
                            }
                            if (fallbackUri != null) {
                                sessionPhotos[photoTag] = SessionPhoto(
                                    uri = fallbackUri.toString(),
                                    ts = System.currentTimeMillis()
                                )
                                photoSaveStatus = null
                                Toast.makeText(
                                    context,
                                    "Photo saved to Camera (fallback frame)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                photoSaveStatus = "Photo save failed — entry will have no photo"
                                Toast.makeText(
                                    context,
                                    "Could not save fuel photo to Camera roll: ${exception.message ?: exception}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            isPhotoSaving = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("QuickFill", "Photo takePicture setup failed", e)
                photoSaveStatus = "Photo save failed — entry will have no photo"
                isPhotoSaving = false
                Toast.makeText(
                    context,
                    "Could not save fuel photo to Camera roll: ${e.message ?: e}",
                    Toast.LENGTH_LONG
                ).show()
            }
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
                        .wrapContentWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    saveButtonContent(Modifier.wrapContentWidth())
                }
            } else {
                // Save in B — portrait branch: single horizontal row (save, shutter, mode)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    saveButtonContent(Modifier.wrapContentWidth())
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
            }
        }
    }

    // Save in B only. A gets weight(1) remainder after B+C for max camera; zoom in right-blank or bottom-blank inside A.
    // 3-panel layout: A (camera bottom-center fill), B (controls), C (results). Portrait C centered; landscape C content-sized.

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Config-based layout: device landscape orientation triggers 3-panel Row
        if (isLandscape) {
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
                        panelAContent(false)
                    }
                    // Panel B — navigation controls + Save (bottom)
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .padding(2.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        cameraControlsContent(true)
                    }
                }
                // Panel C — content-sized fields (wrapContentWidth)
                Column(
                    modifier = if (isEditing) {
                        Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .wrapContentWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsContent()
                }
            }
        } else {
            // Portrait: A+B hidden during field edit (system keyboard); restored on focus clear.
            // A uses weight remainder with fillMaxSize so camera preview expands to sides and down toward B.
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isPortraitFieldFocused) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        panelAContent(false)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        cameraControlsContent(false)
                    }
                }
                // Panel C — portrait: fields centered horizontally in available width
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(
                            if (isPortraitFieldFocused) Modifier.weight(1f, fill = false)
                            else Modifier.verticalScroll(rememberScrollState())
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        fieldsContent()
                    }
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

// material-icons-core lacks Save; local Filled.Save matches Material disk icon (Icons.Filled.Save usage).
private var _saveIcon: ImageVector? = null
val Icons.Filled.Save: ImageVector
    get() {
        if (_saveIcon != null) return _saveIcon!!
        _saveIcon = ImageVector.Builder(
            name = "Save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(17f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                lineToRelative(-4f, -4f)
                close()
                moveTo(12f, 19f)
                curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(4f)
                close()
            }
        }.build()
        return _saveIcon!!
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

