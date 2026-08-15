package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookup
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.location.StationMatch
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.CameraZoomControl
import com.davidlang.vehicleexpensesautomated.ui.components.CaptureButtonState
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField
import com.davidlang.vehicleexpensesautomated.ui.components.LocationConfirmBlock
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.RoundCaptureButton
import com.davidlang.vehicleexpensesautomated.ui.components.StationPickerDialog
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
import com.davidlang.vehicleexpensesautomated.ui.util.CaptureLocation
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.NetworkStatus
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifWriter
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Currency
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class CaptureViewState { Live, Processing, Results }

private fun convertVolumeForSave(value: Double, fromUnit: String, toUnit: String): Double =
    VolumeUnits.convert(value, fromUnit, toUnit)

/** In-memory photo pointer until FuelEntry Save (tag dash|pump). */
private data class SessionPhoto(val uri: String, val ts: Long)

/** Map captureMode odo→dash, pump→pump. */
private fun photoTagForCaptureMode(captureMode: String): String {
    return if (captureMode == "pump") "pump" else "dash"
}

/** Surface.ROTATION_* → degrees for EXIF orientation. */
private fun surfaceRotationToDegrees(rotation: Int): Int = when (rotation) {
    android.view.Surface.ROTATION_0 -> 0
    android.view.Surface.ROTATION_90 -> 90
    android.view.Surface.ROTATION_180 -> 180
    android.view.Surface.ROTATION_270 -> 270
    else -> 0
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

    val vehiclesRaw by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    // Exclude system Unassigned bucket from Quick Fill picker
    val vehicles = remember(vehiclesRaw) { vehiclesRaw.forUserPicker() }
    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var gallons by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    /** Session photos keyed by tag (dash/pump); written to DB only on Save as JSON. */
    val sessionPhotos = remember { mutableStateMapOf<String, SessionPhoto>() }
    /** Row lat/lon for save (camera path = once-per-screen device fix). */
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    /** Held device fix for EXIF stamping on CameraX JPEGs; once per screen visit. */
    var deviceLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var locationStatus by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf("") }
    var placeAddress by remember { mutableStateOf("") }
    var locationLookupDone by remember { mutableStateOf(false) }
    /** Last successful live lookup (for confirm provenance: overpass/nominatim vs user edit). */
    var lookupName by remember { mutableStateOf<String?>(null) }
    var lookupAddress by remember { mutableStateOf<String?>(null) }
    var lookupSource by remember { mutableStateOf<String?>(null) }
    var showStationPicker by remember { mutableStateOf(false) }

    // One-shot device GPS on enter (not per shutter); odo+pump+row share this fix.
    LaunchedEffect(Unit) {
        val fix = CaptureLocation.captureLocationOrNull(context)
        deviceLocation = fix
        if (fix != null) {
            lat = fix.latitude
            lon = fix.longitude
        }
    }

    // Non-blocking POI when coords available (cancel/re-run if lat/lon change).
    LaunchedEffect(lat, lon) {
        val la = lat
        val lo = lon
        if (la == null || lo == null) {
            locationStatus = ""
            locationLookupDone = false
            lookupName = null
            lookupAddress = null
            lookupSource = null
            return@LaunchedEffect
        }
        locationStatus = "Looking up place…"
        locationLookupDone = false
        val acc = deviceLocation?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()
        val online = NetworkStatus.hasUsableNetwork(context)
        val tableMatch = fuelViewModel.matchKnownStation(la, lo)
        when (tableMatch) {
            is StationMatch.Unique -> {
                val station = tableMatch.station
                placeName = station.name
                placeAddress = station.address
                lookupName = station.name
                lookupAddress = station.address
                lookupSource = KnownStation.SOURCE_STATIONS
                locationStatus = "Known station: ${
                    LocationLookup.fromKnownStation(station, tableMatch.distanceM).displayLine()
                }"
                locationLookupDone = true
                return@LaunchedEffect
            }
            is StationMatch.Ambiguous -> {
                lookupName = null
                lookupAddress = null
                lookupSource = null
                locationStatus = "Ambiguous — pick a station"
                locationLookupDone = true
                return@LaunchedEffect
            }
            is StationMatch.None -> Unit
        }
        if (!online) {
            locationStatus = "Offline — no known station nearby"
            locationLookupDone = true
            lookupName = null
            lookupAddress = null
            lookupSource = null
            return@LaunchedEffect
        }
        val result = LocationLookup.lookup(
            lat = la,
            lon = lo,
            kind = LocationLookupKind.FUEL_STATION,
            accuracyM = acc,
            uiTimeout = true,
            stationStore = fuelViewModel.knownStationStore,
        )
        if (result != null && result.hasPlace()) {
            placeName = result.name
            placeAddress = result.address
            lookupName = result.name
            lookupAddress = result.address
            lookupSource = result.source
            locationStatus = "Network: ${result.displayLine()}"
        } else {
            lookupName = null
            lookupAddress = null
            lookupSource = null
            locationStatus = "No place found (will retry after save if online)"
        }
        locationLookupDone = true
    }

    var captureViewState by rememberSaveable { mutableStateOf(CaptureViewState.Live) }
    var capturePending by remember { mutableStateOf(false) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val isProcessing = captureViewState == CaptureViewState.Processing
    val hasResults = captureViewState == CaptureViewState.Results
    var stageLabel by remember { mutableStateOf("") }
    var instructionLine by remember {
        mutableStateOf("Aim at odometer. Tap shutter to capture.")
    }
    var isPhotoSaving by remember { mutableStateOf(false) }

    RegisterPageHelp(
        title = "Quick Fill controls",
        "White circle — shutter (capture dash or pump).",
        "Disk — save the fill when fields are ready.",
        "↕ — switch odometer mode ↔ pump (cost/volume) mode.",
        "↔ — swap cost and volume fields.",
        "Type odo/cost/volume anytime (custom keypad). Menu → Help for the full walkthrough.",
    )
    /** Null when idle/ok; set while saving or after a failed Camera-roll save. */
    var photoSaveStatus by remember { mutableStateOf<String?>(null) }
    var zoomControl by remember { mutableStateOf<CameraZoomControl?>(null) }

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember {
        if (prefs.contains("debug_quick_fill")) {
            prefs.getBoolean("debug_quick_fill", false)
        } else {
            prefs.getBoolean("debug_ocr_pipeline", false)
        }
    }

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
    val keyboardController = LocalSoftwareKeyboardController.current
    var isOdoFocused by remember { mutableStateOf(false) }
    var isVolumeFocused by remember { mutableStateOf(false) }
    var isCostFocused by remember { mutableStateOf(false) }
    /** Active numeric field for custom keypad: odo | cost | volume */
    var editingField by rememberSaveable { mutableStateOf<String?>(null) }
    // Caret indices for keypad insert (clamped to field length)
    var odoCaret by remember { mutableIntStateOf(0) }
    var costCaret by remember { mutableIntStateOf(0) }
    var volumeCaret by remember { mutableIntStateOf(0) }
    val odoFocusRequester = remember { FocusRequester() }
    val costFocusRequester = remember { FocusRequester() }
    val volumeFocusRequester = remember { FocusRequester() }

    val isNumericEditing = editingField == "odo" || editingField == "cost" || editingField == "volume"
    // Keypad active in both orientations when a numeric field is selected
    val isEditing = isNumericEditing

    LaunchedEffect(editingField) {
        if (editingField != null) {
            keyboardController?.hide()
        }
    }

    fun caretFor(field: String?): Int = when (field) {
        "odo" -> odoCaret.coerceIn(0, odometer.length)
        "cost" -> costCaret.coerceIn(0, cost.length)
        "volume" -> volumeCaret.coerceIn(0, gallons.length)
        else -> 0
    }

    fun textFor(field: String?): String = when (field) {
        "odo" -> odometer
        "cost" -> cost
        "volume" -> gallons
        else -> ""
    }

    fun setNumericField(field: String, text: String, caret: Int) {
        val c = caret.coerceIn(0, text.length)
        when (field) {
            "odo" -> {
                odometer = text
                odoCaret = c
            }
            "cost" -> {
                cost = text
                costCaret = c
            }
            "volume" -> {
                gallons = text
                volumeCaret = c
            }
        }
    }

    fun insertAtCaret(field: String, insert: String, maxLen: Int, allowDot: Boolean) {
        val t = textFor(field)
        val caret = caretFor(field)
        if (insert == ".") {
            if (!allowDot || t.contains('.')) return
            val piece = if (t.isEmpty() || caret == 0) "0." else "."
            if (t.length + piece.length > maxLen) return
            val nt = t.take(caret) + piece + t.drop(caret)
            setNumericField(field, nt, caret + piece.length)
            return
        }
        if (!insert.all { it.isDigit() }) return
        if (t.length >= maxLen) return
        val nt = t.take(caret) + insert + t.drop(caret)
        if (nt.length > maxLen) return
        setNumericField(field, nt, caret + insert.length)
    }

    fun backspaceAtCaret(field: String) {
        val t = textFor(field)
        val caret = caretFor(field)
        if (caret <= 0 || t.isEmpty()) return
        val nt = t.take(caret - 1) + t.drop(caret)
        setNumericField(field, nt, caret - 1)
    }

    fun moveCaret(field: String, delta: Int) {
        val t = textFor(field)
        val next = (caretFor(field) + delta).coerceIn(0, t.length)
        when (field) {
            "odo" -> odoCaret = next
            "cost" -> costCaret = next
            "volume" -> volumeCaret = next
        }
    }

    val onKeypadDigit: (String) -> Unit = { digit ->
        when (editingField) {
            "odo" -> insertAtCaret("odo", digit, maxLen = 7, allowDot = false)
            "cost" -> insertAtCaret("cost", digit, maxLen = 10, allowDot = true)
            "volume" -> insertAtCaret("volume", digit, maxLen = 10, allowDot = true)
        }
    }

    val onKeypadBackspace: () -> Unit = {
        editingField?.let { backspaceAtCaret(it) }
    }

    val onKeypadCaretLeft: () -> Unit = {
        editingField?.let { moveCaret(it, -1) }
    }

    val onKeypadCaretRight: () -> Unit = {
        editingField?.let { moveCaret(it, 1) }
    }

    val onKeypadDismiss: () -> Unit = {
        editingField = null
        focusManager.clearFocus()
    }

    val onKeypadNextField: () -> Unit = {
        when (editingField) {
            "odo" -> {
                editingField = "cost"
                costCaret = cost.length
                isCostFocused = true
                isOdoFocused = false
                costFocusRequester.requestFocus()
            }
            "cost" -> {
                editingField = "volume"
                volumeCaret = gallons.length
                isVolumeFocused = true
                isCostFocused = false
                volumeFocusRequester.requestFocus()
            }
            "volume" -> {
                editingField = null
                isVolumeFocused = false
                focusManager.clearFocus()
            }
            else -> onKeypadDismiss()
        }
    }

    fun beginNumericEdit(field: String) {
        editingField = field
        when (field) {
            "odo" -> {
                odoCaret = odometer.length
                isOdoFocused = true
            }
            "cost" -> {
                costCaret = cost.length
                isCostFocused = true
            }
            "volume" -> {
                volumeCaret = gallons.length
                isVolumeFocused = true
            }
        }
        keyboardController?.hide()
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
                                        val lastByVehicle = vehicles.associate { v ->
                                            v.id to fuelViewModel.getLastOdometerForVehicle(v.id)
                                        }
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
                                            },
                                            lastTrackingByVehicleId = lastByVehicle,
                                        )
                                        
                                        scope.launch(Dispatchers.Main) {
                                            if (result.error != null) {
                                                instructionLine = result.error
                                                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                            } else {
                                                result.vehicleId?.let { selectedVehicleId = it }
                                                // Tracking miles for FuelEntry.odometer
                                                result.odometer?.let { odometer = it }
                                                val vid = result.vehicleId
                                                val newRoll = result.newRolloverCount
                                                if (vid != null && newRoll != null) {
                                                    val v = vehicles.find { it.id == vid }
                                                        ?: vehicleViewModel.getVehicleById(vid)
                                                    if (v != null && newRoll > v.odometerRolloverCount) {
                                                        scope.launch(Dispatchers.IO) {
                                                            vehicleViewModel.updateVehicle(
                                                                v.copy(odometerRolloverCount = newRoll),
                                                            )
                                                        }
                                                    }
                                                }
                                                instructionLine = when {
                                                    result.odometer != null ->
                                                        "Odometer: ${result.odometer} (stored tracking). Review, then Save."
                                                    else -> "Dash captured. Enter odometer if needed, or switch to pump."
                                                }
                                            }

                                            if (debugMode && result.debugJson != null) {
                                                val vId = result.vehicleId ?: selectedVehicleId
                                                val vName = vehicles.find { it.id == vId }?.name
                                                scope.launch(Dispatchers.IO) {
                                                    QuickFillDebugStore.saveSession(
                                                        context = context,
                                                        mode = "odo",
                                                        debugJson = result.debugJson,
                                                        vehicleId = vId,
                                                        vehicleName = vName,
                                                        odometer = result.odometer,
                                                        error = result.error,
                                                    )
                                                }
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
                                                instructionLine = result.error
                                                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                            } else {
                                                result.volume?.let { gallons = it }
                                                result.cost?.let { cost = it }
                                                val parts = buildList {
                                                    result.cost?.let { add("cost $it") }
                                                    result.volume?.let { add("volume $it") }
                                                }
                                                instructionLine = if (parts.isNotEmpty()) {
                                                    "Pump: ${parts.joinToString(", ")}. Review fields, then Save."
                                                } else {
                                                    "Pump photo captured. Enter cost/volume if needed, then Save."
                                                }
                                            }
                                            if (debugMode && result.debugJson != null) {
                                                val vId = selectedVehicleId
                                                val vName = vehicles.find { it.id == vId }?.name
                                                scope.launch(Dispatchers.IO) {
                                                    QuickFillDebugStore.saveSession(
                                                        context = context,
                                                        mode = "pump",
                                                        debugJson = result.debugJson,
                                                        vehicleId = vId,
                                                        vehicleName = vName,
                                                        cost = result.cost,
                                                        volume = result.volume,
                                                        error = result.error,
                                                    )
                                                }
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
                        // Explicit partial only (default false). Incomplete = missing fields,
                        // not isPartialFill=true. Full-fill anchors use field presence.
                        val rawVolume = galTrim.toDoubleOrNull() ?: 0.0
                        val saveVolume = if (rawVolume == 0.0) {
                            0.0
                        } else {
                            convertVolumeForSave(rawVolume, volumeUnit, preferredVolumeUnit)
                        }
                        val photoUrlJson = sessionPhotosToJson(sessionPhotos)
                        val storedCurrency = CurrencyCodes.fromSymbolOrCode(currencySymbol)
                        val baseBlob = FuelLocationJson.fromLocation(deviceLocation)
                            ?: FuelLocationJson.fromCoords(lat, lon, source = "device")
                            ?: FuelLocationJson.Blob()
                        // Non-blank place → implicit confirmed=true; blank → coords-only.
                        val placeBlank = placeName.isBlank() && placeAddress.isBlank()
                        val saveBlob = if (!placeBlank) {
                            baseBlob.withPlace(
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
                                kind = LocationLookupKind.FUEL_STATION.blobKindTag(),
                                lookedUpAt = System.currentTimeMillis(),
                            )
                        } else {
                            baseBlob.coordsOnly()
                        }
                        fuelViewModel.saveFuel(
                            FuelEntry(
                                vehicleId = vehicleId,
                                odometer = odoTrim.toIntOrNull() ?: 0,
                                gallons = saveVolume,
                                cost = costTrim.toDoubleOrNull() ?: 0.0,
                                currency = storedCurrency,
                                timestamp = System.currentTimeMillis(),
                                photoUrl = photoUrlJson,
                                location = FuelLocationJson.encode(saveBlob),
                                notes = notes.trim().ifBlank { null },
                                isPartialFill = false,
                            )
                        )
                        if (saveBlob.hasCoordsWithoutPlace()) {
                            LocationLookupScheduler.enqueueSoon(context)
                        }
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
                        notes = ""
                        sessionPhotos.clear()
                        photoSaveStatus = null
                        capturePending = false
                        captureViewState = CaptureViewState.Live
                        Toast.makeText(
                            context,
                            "Fill-up saved",
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
        // Width comes from parent (portrait fillMaxWidth / landscape max cap).
        // Do not wrapContentWidth on long help text — that crushed landscape A.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackedPump = maxWidth < 340.dp

            val textMeasurer = rememberTextMeasurer()
            val longestVehicle = vehicles.maxOfOrNull { it.name } ?: "Select vehicle"
            val density = LocalDensity.current
            val vehicleTextWidth = with(density) {
                textMeasurer.measure(longestVehicle, style = MaterialTheme.typography.bodyLarge).size.width.toDp()
            } + 48.dp
            val vehicleFieldWidth = vehicleTextWidth.coerceIn(80.dp, 172.dp)

            val odoBorder = if (captureMode == "odo") {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium).padding(8.dp)
            } else {
                Modifier.padding(8.dp)
            }

            // Parent Panel C owns verticalScroll; no nested scroll here.
            Column(modifier = Modifier.fillMaxWidth()) {
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
                CaretEnabledOutlinedTextField(
                    value = odometer,
                    onValueChange = {
                        // Tracking odo may exceed face width after rollover (e.g. 7+ digits).
                        if (it.length <= 10 && it.all { c -> c.isDigit() }) odometer = it
                    },
                    label = { Text("Odo") },
                    // Custom NumericKeypad is the only soft digit UI (both orientations).
                    showCaretButtons = false,
                    caretIndex = odoCaret,
                    onCaretIndexChange = { odoCaret = it },
                    modifier = Modifier
                        .widthIn(min = 64.dp, max = 88.dp)
                        .focusRequester(odoFocusRequester)
                        .onFocusChanged {
                            isOdoFocused = it.isFocused
                            if (it.isFocused) beginNumericEdit("odo")
                            else if (editingField == "odo") editingField = null
                        },
                    readOnly = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onKeypadNextField() }
                    ),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Group 2: Volume + Cost
        val pumpBorder = if (captureMode == "pump") {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium).padding(8.dp)
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
                CaretEnabledOutlinedTextField(
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
                    modifier = modifier
                        .focusRequester(costFocusRequester)
                        .onFocusChanged {
                            isCostFocused = it.isFocused
                            if (it.isFocused) beginNumericEdit("cost")
                            else if (editingField == "cost") editingField = null
                        },
                    readOnly = true,
                    showCaretButtons = false,
                    caretIndex = costCaret,
                    onCaretIndexChange = { costCaret = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onKeypadNextField() },
                        onDone = { onKeypadNextField() }
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
                CaretEnabledOutlinedTextField(
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
                    modifier = modifier
                        .focusRequester(volumeFocusRequester)
                        .onFocusChanged {
                            isVolumeFocused = it.isFocused
                            if (it.isFocused) beginNumericEdit("volume")
                            else if (editingField == "volume") editingField = null
                        },
                    readOnly = true,
                    showCaretButtons = false,
                    caretIndex = volumeCaret,
                    onCaretIndexChange = { volumeCaret = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onKeypadNextField() }
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
            val panelCTextWidth = if (isLandscape) {
                Modifier.widthIn(min = 80.dp, max = vehicleFieldWidth.coerceAtLeast(172.dp))
            } else {
                Modifier.fillMaxWidth()
            }
            com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                // Landscape: match Panel C sibling width (not full remaining A+B space).
                // Portrait: full width of Panel C.
                modifier = panelCTextWidth,
                singleLine = false,
                maxLines = 3,
            )
            if (lat != null && lon != null) {
                LocationConfirmBlock(
                    statusLine = locationStatus,
                    name = placeName,
                    address = placeAddress,
                    onNameChange = { placeName = it },
                    onAddressChange = { placeAddress = it },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .then(panelCTextWidth),
                    pickerKind = LocationLookupKind.FUEL_STATION,
                    hasCoords = true,
                    onWrongStationClick = { showStationPicker = true },
                )
            }
            if (showStationPicker) {
                val pla = lat
                val plo = lon
                if (pla != null && plo != null) {
                    StationPickerDialog(
                        lat = pla,
                        lon = plo,
                        kind = LocationLookupKind.FUEL_STATION,
                        stationStore = fuelViewModel.knownStationStore,
                        onSelect = { picked ->
                            placeName = picked.name
                            placeAddress = picked.address
                            lookupName = picked.name
                            lookupAddress = picked.address
                            lookupSource = picked.source.ifBlank { KnownStation.SOURCE_USER }
                            showStationPicker = false
                            val acc = deviceLocation?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()
                            scope.launch {
                                fuelViewModel.upsertKnownStation(
                                    name = picked.name,
                                    address = picked.address,
                                    lat = pla,
                                    lon = plo,
                                    accuracyM = acc,
                                    source = KnownStation.SOURCE_USER,
                                )
                            }
                        },
                        onManual = { showStationPicker = false },
                        onDismiss = { showStationPicker = false },
                    )
                } else {
                    showStationPicker = false
                }
            }
            } // pump Column
            } // fields Column
        } // BoxWithConstraints
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
            var rotationDegrees = 0
            try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                }
                val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
                imageCapture.targetRotation = rotation
                rotationDegrees = surfaceRotationToDegrees(rotation)
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

                val captureMetadata = ImageCapture.Metadata().apply {
                    location = deviceLocation
                }
                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    resolver,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).setMetadata(captureMetadata).build()

                val locForExif = deviceLocation
                val orientForExif = rotationDegrees
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
                                val captureTs = System.currentTimeMillis()
                                PhotoExifWriter.writeGpsAndOrientation(
                                    context,
                                    savedUri,
                                    locForExif,
                                    orientForExif,
                                    timestampMs = captureTs,
                                    userComment = "ve:tag=$photoTag",
                                )
                                sessionPhotos[photoTag] = SessionPhoto(
                                    uri = savedUri.toString(),
                                    ts = captureTs
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
                                val captureTs = System.currentTimeMillis()
                                PhotoExifWriter.writeGpsAndOrientation(
                                    context,
                                    fallbackUri,
                                    locForExif,
                                    orientForExif,
                                    timestampMs = captureTs,
                                    userComment = "ve:tag=$photoTag",
                                )
                                sessionPhotos[photoTag] = SessionPhoto(
                                    uri = fallbackUri.toString(),
                                    ts = captureTs
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
            instructionLine = if (captureMode == "pump") {
                "Aim at pump display (cost/volume). Tap shutter to capture."
            } else {
                "Aim at odometer. Tap shutter to capture."
            }
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

        val statusLine = photoSaveStatus ?: instructionLine
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
                    RoundCaptureButton(
                        viewState = mainButtonState.toCaptureButtonState(),
                        onClick = onMainButtonClick,
                    )
                    // Save in B — landscape branch
                    saveButtonContent(Modifier.wrapContentWidth())
                }
            } else {
                // Save in B — portrait branch: single horizontal row (save, shutter, mode)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    saveButtonContent(Modifier.wrapContentWidth())
                    RoundCaptureButton(
                        viewState = mainButtonState.toCaptureButtonState(),
                        onClick = onMainButtonClick,
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
    }

    // 3-panel layout: A (camera) ≥ half, B controls, C fields (scroll, width-capped in landscape).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isEditing) {
                    // Landscape editing: 4×4 keypad replaces A+B space
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        NumericKeypad(
                            onDigit = onKeypadDigit,
                            onBackspace = onKeypadBackspace,
                            onCaretLeft = onKeypadCaretLeft,
                            onCaretRight = onKeypadCaretRight,
                            onNextField = onKeypadNextField,
                            onDismiss = onKeypadDismiss
                        )
                    }
                } else {
                    // Panel A — ≥ ~half width (weight 1.2 vs C 1.0)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .widthIn(min = 200.dp)
                    ) {
                        panelAContent(false)
                    }
                    // Panel B — navigation controls + Save
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
                // Panel C — width-capped so long lines never crush A
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 280.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fieldsContent()
                }
            }
        } else {
            // Portrait: A ≥ 50% height; B fixed; C remaining + verticalScroll only.
            // Keypad editing: keypad in A slot; C keeps remaining scrollable band.
            Column(modifier = Modifier.fillMaxSize()) {
                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NumericKeypad(
                            onDigit = onKeypadDigit,
                            onBackspace = onKeypadBackspace,
                            onCaretLeft = onKeypadCaretLeft,
                            onCaretRight = onKeypadCaretRight,
                            onNextField = onKeypadNextField,
                            onDismiss = onKeypadDismiss
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth()
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
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
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

private fun CaptureViewState.toCaptureButtonState(): CaptureButtonState = when (this) {
    CaptureViewState.Live -> CaptureButtonState.Live
    CaptureViewState.Processing -> CaptureButtonState.Processing
    CaptureViewState.Results -> CaptureButtonState.Results
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

/**
 * Quick Fill 4×4 numeric keypad (both orientations).
 *
 * ```
 * 1  2  3  ⌫
 * 4  5  6  ◀
 * 7  8  9  ▶
 * .  0  OK  blank
 * ```
 * OK = next field (odo→cost→volume→dismiss). blank = dismiss only.
 */
@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCaretLeft: () -> Unit,
    onCaretRight: () -> Unit,
    onNextField: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 48.dp
) {
    val gap = 4.dp
    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "◀"),
        listOf("7", "8", "9", "▶"),
        listOf(".", "0", "OK", "blank"),
    )
    Column(
        modifier = modifier.width(keySize * 4 + gap * 3),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "◀" -> onCaretLeft()
                                "▶" -> onCaretRight()
                                "OK" -> onNextField()
                                "blank" -> onDismiss()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.size(keySize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        when (key) {
                            "blank" -> Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Dismiss keypad"
                            )
                            "◀" -> Text("◀", style = MaterialTheme.typography.titleMedium)
                            "▶" -> Text("▶", style = MaterialTheme.typography.titleMedium)
                            else -> Text(key, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
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

