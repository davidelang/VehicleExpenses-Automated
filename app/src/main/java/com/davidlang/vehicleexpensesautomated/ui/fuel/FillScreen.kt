package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.VehicleNotIdentifiedDialog
import com.davidlang.vehicleexpensesautomated.ui.components.CameraZoomControl
import com.davidlang.vehicleexpensesautomated.ui.components.CaptureButtonState
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField
import com.davidlang.vehicleexpensesautomated.ui.components.LocationConfirmBlock
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.RoundCaptureButton
import com.davidlang.vehicleexpensesautomated.ui.components.StationPickerDialog
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
import com.davidlang.vehicleexpensesautomated.ui.util.deviceHasCamera
import com.davidlang.vehicleexpensesautomated.ui.util.FillFrameIngest
import com.davidlang.vehicleexpensesautomated.ui.util.CaptureLocation
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.NetworkStatus
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifWriter
import com.davidlang.vehicleexpensesautomated.ui.util.FillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File
import java.util.Currency
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import com.davidlang.vehicleexpensesautomated.ui.util.S

private enum class CaptureViewState { Live, Processing, Results }

private class UnresolvedVehiclePick(
    val survivorIds: List<Int>,
    val cont: CancellableContinuation<Int?>,
)

/** In-memory photo pointer until FuelEntry Save (tag dash|pump). */
private data class SessionPhoto(val uri: String, val ts: Long, val fallback: Boolean = false)

/** Outlives Fill composition so a Camera-roll write is not cancelled on leave. */
private val fillPhotoSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                if (p.fallback) put("fallback", true)
            }
        )
    }
    return arr.toString()
}

private fun saveBitmapToGallery(
    context: android.content.Context,
    mgr: com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager,
    bitmap: Bitmap,
    location: android.location.Location?,
    rotationDegrees: Int,
    userComment: String,
): android.net.Uri? {
    val bytes = java.io.ByteArrayOutputStream()
    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, bytes)) return null
    val file = java.io.File(context.cacheDir, "fuel_${System.currentTimeMillis()}.jpg")
    return try {
        file.writeBytes(bytes.toByteArray())
        PhotoExifWriter.writeGpsAndOrientation(
            context,
            file.absolutePath,
            location,
            rotationDegrees,
            userComment = userComment,
        )
        mgr.saveGalleryPhoto(file.name, "image/jpeg", file)
    } catch (e: Exception) {
        Log.e("Fill", "gallery save failed", e)
        null
    } finally {
        file.delete()
    }
}

private fun saveJpegBytesToGallery(
    context: android.content.Context,
    mgr: com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager,
    jpeg: ByteArray,
    location: android.location.Location?,
    rotationDegrees: Int,
    userComment: String,
): android.net.Uri? {
    if (jpeg.isEmpty()) return null
    val file = java.io.File(context.cacheDir, "fuel_${System.currentTimeMillis()}.jpg")
    return try {
        file.writeBytes(jpeg)
        PhotoExifWriter.writeGpsAndOrientation(
            context,
            file.absolutePath,
            location,
            rotationDegrees,
            userComment = userComment,
        )
        mgr.saveGalleryPhoto(file.name, "image/jpeg", file)
    } catch (e: Exception) {
        Log.e("Fill", "gallery jpeg save failed", e)
        null
    } finally {
        file.delete()
    }
}


private class FillCaptureArgs(
    val captureModeState: MutableState<String>,
    val debugMode: Boolean,
    val context: android.content.Context,
    val scope: CoroutineScope,
    val vehicles: List<Vehicle>,
    val fuelViewModel: FuelViewModel,
    val vehicleViewModel: VehicleViewModel,
    val bufferSetABusy: AtomicBoolean,
    val vehicleDialogUp: AtomicBoolean,
    val pumpOriginalJpeg: MutableState<ByteArray>,
    val displayBitmapState: MutableState<Bitmap?>,
    val stageLabelState: MutableState<String>,
    val instructionLineState: MutableState<String>,
    val selectedVehicleIdState: MutableState<Int?>,
    val odometerState: MutableState<String>,
    val gallonsState: MutableState<String>,
    val costState: MutableState<String>,
    val pumpViewRotationState: MutableIntState,
    val watchdogUpState: MutableState<Boolean>,
    val unresolvedVehicleState: MutableState<UnresolvedVehiclePick?>,
    val capturePendingState: MutableState<Boolean>,
    val captureViewStateState: MutableState<CaptureViewState>,
    val sessionPhotos: MutableMap<String, SessionPhoto>,
)

private fun fillPickPhoto(
    bitmap: Bitmap,
    pickedUri: String,
    savePhotos: Boolean,
    photoTag: String,
    args: FillCaptureArgs,
) {
    if (args.capturePendingState.value || !args.bufferSetABusy.compareAndSet(false, true)) {
        Toast.makeText(args.context, S("Still processing, try again"), Toast.LENGTH_LONG).show()
        bitmap.recycle()
        return
    }
    args.capturePendingState.value = true
    args.captureViewStateState.value = CaptureViewState.Processing
    val bufferSet = NativePaddleEngine.bufferSetA
    args.scope.launch {
        val error = try {
            withContext(Dispatchers.Default) {
                try {
                    FillFrameIngest.ingestFullBitmap(bufferSet, bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        if (error != null) {
            Log.e("Fill", "picked image ingest failed", error)
            args.bufferSetABusy.set(false)
            args.capturePendingState.value = false
            args.captureViewStateState.value = CaptureViewState.Live
            Toast.makeText(
                args.context,
                S("Could not use picture: %1\$s", error.message.orEmpty()),
                Toast.LENGTH_LONG,
            ).show()
            return@launch
        }
        val captureTimestamp = System.currentTimeMillis()
        if (savePhotos) {
            args.sessionPhotos[photoTag] = SessionPhoto(
                uri = pickedUri,
                ts = captureTimestamp,
            )
        }
        continueFillCapture(args, bufferSet, 0, captureTimestamp)
    }
}

private fun continueFillCapture(
    args: FillCaptureArgs,
    bufferSet: BufferSet,
    rotation: Int,
    captureTimestamp: Long,
) {
    val captureMode = args.captureModeState.value
    val debugMode = args.debugMode
    val context = args.context
    val scope = args.scope
    val vehicles = args.vehicles
    val fuelViewModel = args.fuelViewModel
    val vehicleViewModel = args.vehicleViewModel
    val bufferSetABusy = args.bufferSetABusy
    val vehicleDialogUp = args.vehicleDialogUp
    val pumpOriginalJpeg = args.pumpOriginalJpeg
    var displayBitmap by args.displayBitmapState
    var stageLabel by args.stageLabelState
    var instructionLine by args.instructionLineState
    var selectedVehicleId by args.selectedVehicleIdState
    var odometer by args.odometerState
    var gallons by args.gallonsState
    var cost by args.costState
    var pumpViewRotation by args.pumpViewRotationState
    var watchdogUp by args.watchdogUpState
    var unresolvedVehicle by args.unresolvedVehicleState
    var capturePending by args.capturePendingState
    var captureViewState by args.captureViewStateState
    if (captureMode == "odo") {
        scope.launch(Dispatchers.Default) {
            try {
                val inputY: ByteArray?
                val inputVu: ByteArray?
                val inputJson: String?
                if (debugMode) {
                    val w = bufferSet.width
                    val h = bufferSet.height
                    val yLen = w * h
                    val vuLen = w * (h / 2)
                    val y = ByteArray(yLen)
                    bufferSet.p.raw.get(y)
                    val vu = ByteArray(vuLen)
                    val nv21 = bufferSet.p.nv21
                    nv21.position(yLen)
                    nv21.get(vu)
                    inputY = y
                    inputVu = vu
                    inputJson = JSONObject()
                        .put("w", w)
                        .put("h", h)
                        .put("yRowStride", w)
                        .put("uvRowStride", w)
                        .put("uvPixelStride", 2)
                        .put("format", "NV21")
                        .put("rotationDegrees", rotation)
                        .put("timestamp", captureTimestamp)
                        .toString()
                } else {
                    inputY = null
                    inputVu = null
                    inputJson = null
                }
                val lastByVehicle = vehicles.associate { v ->
                    v.id to fuelViewModel.getLastOdometerForVehicle(v.id)
                }
                var originalBmp: Bitmap? = null
                val result = OcrHarness.runAutoFillPipeline(
                    context = context,
                    masterBuffer = bufferSet,
                    allVehicles = vehicles,
                    debug = debugMode,
                    cameraRotationDegrees = rotation,
                    onStage = { stage ->
                        scope.launch(Dispatchers.Main) {
                            stageLabel = stage
                        }
                    },
                    onOriginalFrame = { bmp ->
                        originalBmp = bmp
                        scope.launch(Dispatchers.Main) {
                            displayBitmap = bmp
                        }
                    },
                    lastTrackingByVehicleId = lastByVehicle,
                    onUnresolvedVehicle = { survivorIds, vetoDebugJson ->
                        val frame = originalBmp
                        FillDebugStore.saveSession(
                            context = context,
                            mode = "odo",
                            debugJson = vetoDebugJson,
                            error = S("Vehicle not identified"),
                            extraJpegBitmaps = emptyMap(),
                        )
                        suspendCancellableCoroutine { cont ->
                            scope.launch(Dispatchers.Main) {
                                instructionLine = S("Vehicle not identified")
                                NativePaddleEngine.heartbeat("vehicle_dialog_open")
                                vehicleDialogUp.set(true)
                                watchdogUp = false
                                unresolvedVehicle = UnresolvedVehiclePick(
                                    survivorIds,
                                    cont,
                                )
                            }
                        }
                    },
                )

                scope.launch(Dispatchers.Main) {
                    if (result.survivorIds != null) {
                        if (vehicles.isEmpty()) {
                            instructionLine = S("Vehicle not identified")
                            Toast.makeText(
                                context,
                                S("Vehicle not identified"),
                                Toast.LENGTH_LONG,
                            ).show()
                            if (result.debugJson != null) {
                                val frame = originalBmp
                                scope.launch(Dispatchers.IO) {
                                    FillDebugStore.saveSession(
                                        context = context,
                                        mode = "odo",
                                        debugJson = result.debugJson,
                                        error = S("Vehicle not identified"),
                                        extraJpegBitmaps = emptyMap(),
                                    )
                                }
                            }
                        }
                    } else if (result.error != null) {
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
                                S("Odometer: %1\$s (stored tracking). Review, then Save.", result.odometer)
                            else -> S("Dash captured. Enter odometer if needed, or switch to pump.")
                        }
                    }

                    if (debugMode && result.debugJson != null && result.survivorIds == null) {
                        val vId = result.vehicleId ?: selectedVehicleId
                        val vName = vehicles.find { it.id == vId }?.name
                        val previewSource = originalBmp
                        val extraText = buildMap {
                            result.detJson?.let { put("det.json", it) }
                            result.recJson?.let { put("rec.json", it) }
                            result.vetoJson?.let { put("veto.json", it) }
                        }
                        scope.launch(Dispatchers.IO) {
                            val sidecars = if (previewSource != null) {
                                mapOf("photo.jpg" to previewSource)
                            } else {
                                emptyMap()
                            }
                            FillDebugStore.saveSession(
                                context = context,
                                mode = "odo",
                                debugJson = result.debugJson,
                                vehicleId = vId,
                                vehicleName = vName,
                                odometer = result.odometer,
                                error = result.error,
                                extraJpegBitmaps = sidecars,
                                inputY = inputY,
                                inputVu = inputVu,
                                inputJson = inputJson,
                                extraText = extraText,
                            )
                        }
                    }
                }
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: CancellationException) {
                throw e
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Log.e("Fill", "OCR Pipeline failed", e)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, S("OCR failed: %1\$s", e.localizedMessage ?: S("Unknown error")), Toast.LENGTH_LONG).show()
                }
            } finally {
                scope.launch(Dispatchers.Main) {
                    bufferSetABusy.set(false)
                    capturePending = false
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
                val inputY: ByteArray?
                val inputVu: ByteArray?
                val inputJson: String?
                if (debugMode) {
                    val w = bufferSet.width
                    val h = bufferSet.height
                    val yLen = w * h
                    val vuLen = w * (h / 2)
                    val y = ByteArray(yLen)
                    bufferSet.p.raw.get(y)
                    val vu = ByteArray(vuLen)
                    val nv21 = bufferSet.p.nv21
                    nv21.position(yLen)
                    nv21.get(vu)
                    inputY = y
                    inputVu = vu
                    inputJson = JSONObject()
                        .put("w", w)
                        .put("h", h)
                        .put("yRowStride", w)
                        .put("uvRowStride", w)
                        .put("uvPixelStride", 2)
                        .put("format", "NV21")
                        .put("rotationDegrees", rotation)
                        .put("timestamp", captureTimestamp)
                        .toString()
                } else {
                    inputY = null
                    inputVu = null
                    inputJson = null
                }
                val viewRotation = rotation
                withContext(Dispatchers.Main) {
                    pumpViewRotation = viewRotation
                }
                var discoveryJpeg = ByteArray(0)
                val result = OcrHarness.runPumpCostVolPipeline(
                    context = context,
                    masterBuffer = bufferSet,
                    debug = debugMode,
                    onStage = { stage, jpeg ->
                        when (stage) {
                            "discovery" -> {
                                discoveryJpeg = jpeg
                                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                                withContext(Dispatchers.Main) {
                                    if (bmp != null) {
                                        displayBitmap = bmp
                                    }
                                    if (pumpOriginalJpeg.value.isEmpty()) {
                                        pumpOriginalJpeg.value = jpeg
                                    }
                                }
                            }
                            "final" -> {
                                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                                if (bmp != null) {
                                    withContext(Dispatchers.Main) {
                                        displayBitmap = bmp
                                    }
                                }
                            }
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
                            result.cost?.let { add(S("cost %1\$s", it)) }
                            result.volume?.let { add(S("volume %1\$s", it)) }
                        }
                        instructionLine = if (parts.isNotEmpty()) {
                            S("Pump: %1\$s. Review fields, then Save.", parts.joinToString(", "))
                        } else {
                            S("Pump photo captured. Enter cost/volume if needed, then Save.")
                        }
                    }
                    if (debugMode && result.debugJson != null) {
                        val vId = selectedVehicleId
                        val vName = vehicles.find { it.id == vId }?.name
                        val discoveryForPreview = discoveryJpeg
                        val extraText = buildMap {
                            result.classifyJson?.let { put("classify.json", it) }
                            result.scalesJson?.let { put("scales.json", it) }
                        }
                        scope.launch(Dispatchers.IO) {
                            val jpegSidecars = buildMap<String, ByteArray> {
                                if (discoveryForPreview.isNotEmpty()) {
                                    put("photo.jpg", discoveryForPreview)
                                }
                            }
                            val dir = FillDebugStore.saveSession(
                                context = context,
                                mode = "pump",
                                debugJson = result.debugJson,
                                vehicleId = vId,
                                vehicleName = vName,
                                cost = result.cost,
                                volume = result.volume,
                                error = result.error,
                                extraJpegBytes = jpegSidecars,
                                inputY = inputY,
                                inputVu = inputVu,
                                inputJson = inputJson,
                                extraText = extraText,
                            )
                            val preview = File(dir, "photo.jpg")
                            if (preview.isFile) {
                                PhotoExifWriter.writeGpsAndOrientation(
                                    context,
                                    preview.absolutePath,
                                    null,
                                    viewRotation,
                                )
                            }
                        }
                    }
                }
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: CancellationException) {
                throw e
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Log.e("Fill", "Pump OCR Pipeline failed", e)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        S("Pump OCR failed: %1\$s", e.localizedMessage ?: S("Unknown error")),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                scope.launch(Dispatchers.Main) {
                    bufferSetABusy.set(false)
                    capturePending = false
                    captureViewState = if (displayBitmap != null) {
                        CaptureViewState.Results
                    } else {
                        CaptureViewState.Live
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehiclesRaw by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    // Exclude system Unassigned bucket from Fill picker
    val vehicles = remember(vehiclesRaw) { vehiclesRaw.forUserPicker() }
    val selectedVehicleIdState = rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedVehicleId by selectedVehicleIdState
    val odometerState = rememberSaveable { mutableStateOf("") }
    var odometer by odometerState
    val gallonsState = rememberSaveable { mutableStateOf("") }
    var gallons by gallonsState
    val costState = rememberSaveable { mutableStateOf("") }
    var cost by costState
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
        locationStatus = S("Looking up place…")
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
                locationStatus = S("Known station: %1\$s", LocationLookup.fromKnownStation(station, tableMatch.distanceM).displayLine())
                locationLookupDone = true
                return@LaunchedEffect
            }
            is StationMatch.Ambiguous -> {
                lookupName = null
                lookupAddress = null
                lookupSource = null
                locationStatus = S("Ambiguous — pick a station")
                locationLookupDone = true
                return@LaunchedEffect
            }
            is StationMatch.None -> Unit
        }
        if (!online) {
            locationStatus = S("Offline — no known station nearby")
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
            locationStatus = S("Network: %1\$s", result.displayLine())
        } else {
            lookupName = null
            lookupAddress = null
            lookupSource = null
            locationStatus = S("No place found (will retry after save if online)")
        }
        locationLookupDone = true
    }

    val captureViewStateState = rememberSaveable { mutableStateOf(CaptureViewState.Live) }
    var captureViewState by captureViewStateState
    val capturePendingState = remember { mutableStateOf(false) }
    var capturePending by capturePendingState
    val bufferSetABusy = remember { AtomicBoolean(false) }
    val displayBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by displayBitmapState
    val pumpOriginalJpeg = remember { mutableStateOf(ByteArray(0)) }
    val pumpViewRotationState = remember { mutableIntStateOf(0) }
    var pumpViewRotation by pumpViewRotationState
    /** Latest analysis [androidx.camera.core.ImageInfo.rotationDegrees] for DCIM EXIF on shutter. */
    var latestAnalysisRotationDegrees by remember { mutableIntStateOf(0) }
    val isProcessing = captureViewState == CaptureViewState.Processing
    val hasResults = captureViewState == CaptureViewState.Results
    val watchdogUpState = remember { mutableStateOf(false) }
    var watchdogUp by watchdogUpState
    val vehicleDialogUp = remember { AtomicBoolean(false) }
    val unresolvedVehicleState = remember { mutableStateOf<UnresolvedVehiclePick?>(null) }
    var unresolvedVehicle by unresolvedVehicleState
    LaunchedEffect(isProcessing) {
        if (!isProcessing) {
            watchdogUp = false
            return@LaunchedEffect
        }
        while (true) {
            if (vehicleDialogUp.get()) {
                watchdogUp = false
                delay(250L)
                continue
            }
            val silentMs = System.currentTimeMillis() - NativePaddleEngine.lastStageMs
            watchdogUp = silentMs >= 15_000L
            delay(if (silentMs >= 15_000L) 250L else 15_000L - silentMs)
        }
    }
    val stageLabelState = remember { mutableStateOf("") }
    var stageLabel by stageLabelState
    val instructionLineState = remember {
        mutableStateOf(S("Aim at odometer. Tap shutter to capture."))
    }
    var instructionLine by instructionLineState
    var isPhotoSaving by remember { mutableStateOf(false) }

    RegisterPageHelp(
        title = S("Fill controls"),
        S("White circle — shutter (capture dash or pump)."),
        S("Disk — save the fill when fields are ready."),
        S("↕ — switch odometer mode ↔ pump (cost/volume) mode."),
        S("↔ — swap cost and volume fields."),
        S("Type odo/cost/volume anytime (custom keypad). Menu → Help for the full walkthrough."),
    )
    /** Null when idle/ok; set while saving or after a failed Camera-roll save. */
    var photoSaveStatus by remember { mutableStateOf<String?>(null) }
    var zoomControl by remember { mutableStateOf<CameraZoomControl?>(null) }

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember {
        prefs.getBoolean("debug_capture", false)
    }

    // TODO: Settings should surface "use system" as the default option for currency/volume.
    val systemCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        // kept: failure stays on this call so the screen or caller can continue
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
    // Stored volume is US gallons. The G/L control starts at the locale unit and does not rewrite typed digits.
    val defaultVolumeUnit = VolumeUnits.systemDefaultUnit()
    val captureModeState = rememberSaveable { mutableStateOf("odo") }
    var captureMode by captureModeState
    var odoUserEdited by rememberSaveable { mutableStateOf(false) }
    var dashPhotoTaken by rememberSaveable { mutableStateOf(false) }
    var userPickedVehicle by rememberSaveable { mutableStateOf(false) }
    var torqueAppliedVehicleId by rememberSaveable { mutableIntStateOf(-1) }
    var torqueRawTenths by rememberSaveable { mutableStateOf("") }
    var torqueMismatch by rememberSaveable { mutableStateOf("") }
    var torqueAttempted by rememberSaveable { mutableStateOf(false) }
    var fillGeneration by rememberSaveable { mutableIntStateOf(0) }
    val torqueClient = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TorqueLiveEntryPoint::class.java,
        ).torqueLiveClient()
    }
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
                if (text != odometer) odoUserEdited = true
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
    val hasCamera = remember { deviceHasCamera(context) }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }
    val torqueVehicles = rememberUpdatedState(vehicles)
    val torquePicked = rememberUpdatedState(userPickedVehicle)
    val torqueEdited = rememberUpdatedState(odoUserEdited)
    val torqueDash = rememberUpdatedState(dashPhotoTaken)
    val torqueSelectedId = rememberUpdatedState(selectedVehicleId)
    LaunchedEffect(fillGeneration) {
        if (torqueAttempted) return@LaunchedEffect
        torqueAttempted = true
        val attempt = coroutineContext[Job] ?: return@LaunchedEffect
        val generation = fillGeneration
        var retired = false
        var applied = false
        val started = torqueClient.start { reading ->
            if (!attempt.isActive || generation != fillGeneration || retired || applied) return@start
            applied = true
            if (reading == null || torqueEdited.value || torqueDash.value) return@start
            val decision = TorqueLiveDecode.prefill(
                reading,
                torqueVehicles.value,
                torqueSelectedId.value,
                torquePicked.value,
            )
            when (decision.kind) {
                TorqueLiveDecode.PrefillKind.FILL -> {
                    selectedVehicleId = decision.vehicleId
                    odometer = decision.odometer.toString()
                    odoCaret = odometer.length
                    torqueAppliedVehicleId = decision.vehicleId
                    torqueRawTenths = decision.rawTenths
                    torqueMismatch = ""
                    captureMode = "pump"
                    instructionLine = S("Aim at pump display (cost/volume). Tap shutter to capture.")
                }
                TorqueLiveDecode.PrefillKind.MISMATCH -> {
                    torqueMismatch = S("Torque VIN does not match this vehicle.")
                    instructionLine = torqueMismatch
                }
                TorqueLiveDecode.PrefillKind.NONE -> Unit
            }
        }
        if (!started) return@LaunchedEffect
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (!applied && SystemClock.elapsedRealtime() < deadline) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0L) break
            delay(left.coerceAtMost(200L))
        }
        retired = true
    }


    val fillCapture = FillCaptureArgs(
        captureModeState = captureModeState,
        debugMode = debugMode,
        context = context,
        scope = scope,
        vehicles = vehicles,
        fuelViewModel = fuelViewModel,
        vehicleViewModel = vehicleViewModel,
        bufferSetABusy = bufferSetABusy,
        vehicleDialogUp = vehicleDialogUp,
        pumpOriginalJpeg = pumpOriginalJpeg,
        displayBitmapState = displayBitmapState,
        stageLabelState = stageLabelState,
        instructionLineState = instructionLineState,
        selectedVehicleIdState = selectedVehicleIdState,
        odometerState = odometerState,
        gallonsState = gallonsState,
        costState = costState,
        pumpViewRotationState = pumpViewRotationState,
        watchdogUpState = watchdogUpState,
        unresolvedVehicleState = unresolvedVehicleState,
        capturePendingState = capturePendingState,
        captureViewStateState = captureViewStateState,
        sessionPhotos = sessionPhotos,
    )

    val cameraOrCropArea = @Composable {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap!!.asImageBitmap(),
                    contentDescription = S("Odometer Crop"),
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        if (captureMode != "odo") rotationZ = pumpViewRotation.toFloat()
                    },
                    contentScale = ContentScale.Fit
                )
                if (isProcessing && captureMode != "pump") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            text = S("STAGE: %1\$s", stageLabel),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            } else if (!hasCamera) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = S("No camera on this device"),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture,
                    onZoomControlChanged = { zoomControl = it },
                    onImageCaptured = { imageProxy ->
                        latestAnalysisRotationDegrees = imageProxy.imageInfo.rotationDegrees
                        if (capturePending) {
                            if (!bufferSetABusy.compareAndSet(false, true)) {
                                imageProxy.close()
                                return@CameraPreview
                            }

                            val bufferSet = NativePaddleEngine.bufferSetA
                            val planes = imageProxy.planes
                            val isDirect = planes.all { it.buffer.isDirect }
                            if (!isDirect) {
                                imageProxy.close()
                                scope.launch(Dispatchers.Main) {
                                    bufferSetABusy.set(false)
                                    capturePending = false
                                    captureViewState = CaptureViewState.Live
                                    Toast.makeText(context, S("Error: Image buffer is not direct"), Toast.LENGTH_LONG).show()
                                }
                                return@CameraPreview
                            }

                            bufferSet.ingestCameraFrame(
                                planes[0].buffer,
                                planes[1].buffer,
                                planes[2].buffer,
                                planes[0].rowStride,
                                planes[1].rowStride,
                                planes[1].pixelStride,
                                planes[2].pixelStride,
                                imageProxy.width,
                                imageProxy.height,
                            )

                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val captureTimestamp = imageProxy.imageInfo.timestamp
                            imageProxy.close()
                            continueFillCapture(
                                fillCapture,
                                bufferSet,
                                rotation,
                                captureTimestamp,
                            )
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
                                    S("%1\$sx", ratio.toLong())
                                } else {
                                    S("%1\$sx", ratio)
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
                                                        S("%1\$sx", ratio.toLong())
                                                    } else {
                                                        S("%1\$sx", ratio)
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
                        val saveVolume = VolumeUnits.toStoredGallons(rawVolume, volumeUnit)
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
                        val mark = TorqueLiveDecode.savedMark(
                            odometerText = odoTrim,
                            dashPhoto = dashPhotoTaken || sessionPhotos.containsKey("dash"),
                            userTyped = odoUserEdited,
                            torqueVehicleId = torqueAppliedVehicleId,
                            saveVehicleId = vehicleId,
                            torqueRaw = torqueRawTenths,
                        )
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
                                torqueOdometerRawKm = mark.rawTenthsKm,
                                odometerSource = mark.source,
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
                        // Stay on Fill for back-to-back fills: blank fields + live camera.
                        odometer = ""
                        cost = ""
                        gallons = ""
                        notes = ""
                        odoUserEdited = false
                        dashPhotoTaken = false
                        torqueAppliedVehicleId = -1
                        torqueRawTenths = ""
                        torqueMismatch = ""
                        torqueAttempted = false
                        fillGeneration += 1
                        sessionPhotos.clear()
                        photoSaveStatus = null
                        capturePending = false
                        captureViewState = CaptureViewState.Live
                        Toast.makeText(
                            context,
                            S("Fill-up saved"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.Save, contentDescription = S("Save"))
            }
        }
    }

    val fieldsContent = @Composable {
        // Width comes from parent (portrait fillMaxWidth / landscape max cap).
        // Do not wrapContentWidth on long help text — that crushed landscape A.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackedPump = maxWidth < 340.dp

            val textMeasurer = rememberTextMeasurer()
            val longestVehicle = vehicles.maxOfOrNull { it.name } ?: S("Select vehicle")
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
                val vehicleName = vehicles.find { it.id == selectedVehicleId }?.name ?: S("Select vehicle")
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.widthIn(max = vehicleFieldWidth)
                ) {
                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = {},
                        label = { Text(S("Vehicle")) },
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
                                    userPickedVehicle = true
                                    torqueMismatch = ""
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
                        if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                            if (it != odometer) odoUserEdited = true
                            odometer = it
                        }
                    },
                    label = {
                        Text(
                            S(
                                "Odo (%1\$s)",
                                UnitFormat.distanceUnitShortLabel(
                                    vehicles.find { it.id == selectedVehicleId }?.odometerUnit,
                                ),
                            ),
                        )
                    },
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
            if (torqueMismatch.isNotEmpty()) {
                Text(
                    text = torqueMismatch,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                label = { Text(S("Notes")) },
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

    val onShutterClick = shutter@{
        if (!hasCamera) return@shutter
        if (captureMode == "odo") dashPhotoTaken = true
        lastCaptureType = captureMode
        pumpOriginalJpeg.value = ByteArray(0)
        NativePaddleEngine.heartbeat("capture_accepted")
        captureViewState = CaptureViewState.Processing
        capturePending = true
        // Do not clear other tags' session photos on shutter; re-shot tag is replaced on success.
        photoSaveStatus = null
        val photoTag = photoTagForCaptureMode(captureMode)

        val playSound = prefs.getBoolean("shutter_sounds", true)
        if (playSound) {
            try {
                android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Log.e("Fill", "Failed to play shutter sound", e)
            }
        }

        // Live prefs each shutter so Settings toggle applies same session.
        val saveFuelPhotosNow = prefs.getBoolean("save_fuel_photos", true) || debugMode
        if (saveFuelPhotosNow) {
            isPhotoSaving = true
            photoSaveStatus = S("Saving photo…")
            val orientForExif = latestAnalysisRotationDegrees
            val captureFile = java.io.File(context.cacheDir, "fuel_${System.currentTimeMillis()}.jpg")

            try {
                val captureMetadata = ImageCapture.Metadata().apply {
                    location = deviceLocation
                }
                val outputOptions = ImageCapture.OutputFileOptions.Builder(captureFile)
                    .setMetadata(captureMetadata)
                    .build()

                val locForExif = deviceLocation
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val mainExecutor = ContextCompat.getMainExecutor(context)
                            fillPhotoSaveScope.launch {
                                try {
                                    val captureTs = System.currentTimeMillis()
                                    PhotoExifWriter.writeGpsAndOrientation(
                                        context,
                                        captureFile.absolutePath,
                                        locForExif,
                                        orientForExif,
                                        timestampMs = captureTs,
                                        userComment = "ve:tag=$photoTag",
                                    )
                                    val savedUri = settingsViewModel.photoStorageManager.saveGalleryPhoto(
                                        captureFile.name,
                                        "image/jpeg",
                                        captureFile,
                                    )
                                    android.util.Log.i("Fill", "Photo saved: $savedUri")
                                    mainExecutor.execute {
                                        sessionPhotos[photoTag] = SessionPhoto(
                                            uri = savedUri.toString(),
                                            ts = captureTs,
                                        )
                                        photoSaveStatus = null
                                        Toast.makeText(context, S("Photo saved"), Toast.LENGTH_SHORT).show()
                                        isPhotoSaving = false
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("Fill", "Photo save failed", e)
                                    mainExecutor.execute {
                                        photoSaveStatus = S("Photo save failed — entry will have no photo")
                                        Toast.makeText(
                                            context,
                                            S("Could not save fuel photo: %1\$s", e.message ?: e),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        isPhotoSaving = false
                                    }
                                } finally {
                                    captureFile.delete()
                                }
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            captureFile.delete()
                            android.util.Log.e("Fill", "Photo capture failed", exception)
                            val pumpBytes = if (photoTag == "pump") pumpOriginalJpeg.value else ByteArray(0)
                            val screenBmp = displayBitmap
                            val fallbackCopy = if (pumpBytes.isEmpty() && screenBmp != null && !screenBmp.isRecycled) {
                                screenBmp.copy(screenBmp.config ?: Bitmap.Config.ARGB_8888, false)
                            } else {
                                null
                            }
                            val mainExecutor = ContextCompat.getMainExecutor(context)
                            fillPhotoSaveScope.launch {
                                try {
                                    val gallery = settingsViewModel.photoStorageManager
                                    val tag = "ve:tag=$photoTag"
                                    val fallbackUri = if (pumpBytes.isNotEmpty()) {
                                        saveJpegBytesToGallery(
                                            context, gallery, pumpBytes, locForExif, orientForExif, tag,
                                        )
                                    } else if (fallbackCopy != null && !fallbackCopy.isRecycled) {
                                        saveBitmapToGallery(
                                            context, gallery, fallbackCopy, locForExif, orientForExif, tag,
                                        )
                                    } else {
                                        null
                                    }
                                    if (fallbackUri != null) {
                                        val captureTs = System.currentTimeMillis()
                                        mainExecutor.execute {
                                            sessionPhotos[photoTag] = SessionPhoto(
                                                uri = fallbackUri.toString(),
                                                ts = captureTs,
                                                fallback = true,
                                            )
                                            photoSaveStatus = null
                                            Toast.makeText(
                                                context,
                                                S("Photo saved to gallery (fallback frame)"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isPhotoSaving = false
                                        }
                                    } else {
                                        mainExecutor.execute {
                                            photoSaveStatus = S("Photo save failed — entry will have no photo")
                                            Toast.makeText(
                                                context,
                                                S("Could not save fuel photo to gallery: %1\$s", exception.message ?: exception),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isPhotoSaving = false
                                        }
                                    }
                                } finally {
                                    if (fallbackCopy != null && !fallbackCopy.isRecycled) {
                                        fallbackCopy.recycle()
                                    }
                                }
                            }
                        }
                    }
                )
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Log.e("Fill", "Photo takePicture setup failed", e)
                photoSaveStatus = S("Photo save failed — entry will have no photo")
                isPhotoSaving = false
                Toast.makeText(
                    context,
                    S("Could not save fuel photo to gallery: %1\$s", e.message ?: e),
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
                S("Aim at pump display (cost/volume). Tap shutter to capture.")
            } else {
                S("Aim at odometer. Tap shutter to capture.")
            }
        }
    }

    val cameraControlsContent = @Composable { isLand: Boolean ->
        val mainButtonState = when {
            isProcessing -> CaptureViewState.Processing
            displayBitmap != null -> CaptureViewState.Results
            else -> CaptureViewState.Live
        }
        val captureButtonState = if (isProcessing && watchdogUp) {
            CaptureButtonState.Restart
        } else {
            mainButtonState.toCaptureButtonState()
        }
        val onMainButtonClick = {
            when (mainButtonState) {
                CaptureViewState.Live -> onShutterClick()
                CaptureViewState.Processing -> {
                    if (watchdogUp) {
                        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        if (launch != null) {
                            val component = launch.component
                            if (component != null) {
                                context.startActivity(Intent.makeRestartActivityTask(component))
                                Process.killProcess(Process.myPid())
                            }
                        }
                    }
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
                        viewState = captureButtonState,
                        onClick = onMainButtonClick,
                    )
                    FillGalleryButton(enabled = !isProcessing) { uri, bmp ->
                        fillPickPhoto(
                            bitmap = bmp,
                            pickedUri = uri.toString(),
                            savePhotos = prefs.getBoolean("save_fuel_photos", true) || debugMode,
                            photoTag = photoTagForCaptureMode(captureMode),
                            args = fillCapture,
                        )
                    }
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
                        viewState = captureButtonState,
                        onClick = onMainButtonClick,
                    )
                    FillGalleryButton(enabled = !isProcessing) { uri, bmp ->
                        fillPickPhoto(
                            bitmap = bmp,
                            pickedUri = uri.toString(),
                            savePhotos = prefs.getBoolean("save_fuel_photos", true) || debugMode,
                            photoTag = photoTagForCaptureMode(captureMode),
                            args = fillCapture,
                        )
                    }
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
    unresolvedVehicle?.let { req ->
        VehicleNotIdentifiedDialog(
            vehicles = vehicles,
            survivorIds = req.survivorIds,
            onSelect = { id ->
                NativePaddleEngine.heartbeat("vehicle_dialog_close")
                vehicleDialogUp.set(false)
                watchdogUp = false
                unresolvedVehicle = null
                if (req.cont.isActive) req.cont.resume(id)
            },
            onDismiss = {
                NativePaddleEngine.heartbeat("vehicle_dialog_close")
                vehicleDialogUp.set(false)
                watchdogUp = false
                unresolvedVehicle = null
                if (req.cont.isActive) req.cont.resume(null)
            },
        )
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
 * Fill 4×4 numeric keypad (both orientations).
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
        listOf(".", "0", S("OK"), "blank"),
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
                                S("OK") -> onNextField()
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
                                contentDescription = S("Dismiss keypad")
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

