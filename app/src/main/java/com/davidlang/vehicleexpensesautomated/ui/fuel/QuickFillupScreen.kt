package com.davidlang.vehicleexpensesautomated.ui.fuel
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.components.OcrDebugDialog
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils

@Composable
fun QuickFillupScreen(navController: NavHostController) {
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var step by remember { mutableStateOf(1) }
    var isMissedFill by remember { mutableStateOf(false) }
    var isPartialFill by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf(0) }
    var gallons by remember { mutableStateOf(0.0) }
    var cost by remember { mutableStateOf(0.0) }
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    var selectedVehicleId by remember { mutableStateOf<Int?>(null) }
    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var possibleOdometers by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastCropDebug by remember { mutableStateOf("No crop info yet") }
    var lastOcrResult by remember { mutableStateOf("No OCR run yet") }
    var lastOpenCVDebug by remember { mutableStateOf("OpenCV: N/A") }
    var showAlignedDialog by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }
    var lastPhotoPath by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var referenceTextBlocks by remember { mutableStateOf<String?>(null) }

    // Location state
    var currentLatitude by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(selectedVehicleId) {
        selectedVehicleId?.let { id ->
            val vehicle = vehicleViewModel.getVehicleById(id)
            vehicle?.let {
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f)
                }
                Log.d("CropDebug", "QuickFillupScreen loaded vehicle crop rect: $odometerCropRect")
            }
        }
    }

    // Single-pass text extraction when photo is selected
    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            try {
                val result = OdometerOcrUtils.extractFromPhoto(url)
                referenceTextBlocks = result.textBlocks.joinToString("|") { "${it.text}:${it.boundingBox.left},${it.boundingBox.top},${it.boundingBox.right},${it.boundingBox.bottom}" }
            } catch (e: Exception) {
                Log.e("QuickFill", "Text extraction failed", e)
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            Toast.makeText(context, "Gallery image selected — running OCR...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val tempFile = File.createTempFile("ocr_gallery", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                lastPhotoPath = tempFile.absolutePath
                Log.d("OcrDebug", "Gallery: lastPhotoPath set to: $lastPhotoPath")
                val cropRectF = odometerCropRect?.let { r ->
                    android.graphics.RectF(r.left, r.top, r.right, r.bottom)
                }
                val result = OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath, cropRectF)
                lastOcrDebugResult = result
                currentLatitude = result.latitude
                currentLongitude = result.longitude
                result.odometer?.toIntOrNull()?.let { odometer = it }
                Log.d("OcrDebug", "Gallery: showing dialog with lastPhotoPath = $lastPhotoPath")
                showAlignedDialog = true
            }
        }
    }

    fun saveEntry() {
        if (selectedVehicleId == null) {
            Toast.makeText(context, "Please select a vehicle", Toast.LENGTH_SHORT).show()
            return
        }
        val entry = FuelEntry(
            vehicleId = selectedVehicleId!!,
            odometer = odometer,
            gallons = gallons,
            cost = cost,
            timestamp = System.currentTimeMillis(),
            photoUrl = lastPhotoPath,
            isPartialFill = isPartialFill,
            latitude = currentLatitude,
            longitude = currentLongitude
        )
        fuelViewModel.saveFuel(entry)
        Toast.makeText(context, "Fill-up saved!", Toast.LENGTH_SHORT).show()
        navController.popBackStack()
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.60f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsContent(
                        step = step,
                        isMissedFill = isMissedFill,
                        isPartialFill = isPartialFill,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        selectedVehicleId = selectedVehicleId,
                        vehicles = vehicles,
                        onVehicleChange = { selectedVehicleId = it },
                        onMissedChange = { isMissedFill = it },
                        onPartialChange = { isPartialFill = it },
                        onOdometerChange = { odometer = it },
                        onGallonsChange = { gallons = it },
                        onCostChange = { cost = it },
                        onStepChange = { step = it },
                        onTakeDashPicture = {
                            captureDashPhoto(context, imageCapture, cameraExecutor, selectedVehicleId, vehicles, step, scope, { lastCropDebug = it }, { lastOcrResult = it }, { lastOpenCVDebug = it }, { odometer = it }, { possibleOdometers = it }, { showOdometerConfirmation = it }, { gallons = it }, { cost = it }, odometerCropRect, { lastPhotoPath = it }, { lat, lon -> currentLatitude = lat; currentLongitude = lon })
                        },
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onExperimentClick = { navController.navigate("experiment") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
                        onSaveClick = { saveEntry() },
                        lastCropDebug = lastCropDebug,
                        lastOcrResult = lastOcrResult,
                        lastOpenCVDebug = lastOpenCVDebug,
                        dropdownExpanded = dropdownExpanded,
                        onDropdownExpandedChange = { dropdownExpanded = it }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp)
                        .weight(0.35f)
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsContent(
                        step = step,
                        isMissedFill = isMissedFill,
                        isPartialFill = isPartialFill,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        selectedVehicleId = selectedVehicleId,
                        vehicles = vehicles,
                        onVehicleChange = { selectedVehicleId = it },
                        onMissedChange = { isMissedFill = it },
                        onPartialChange = { isPartialFill = it },
                        onOdometerChange = { odometer = it },
                        onGallonsChange = { gallons = it },
                        onCostChange = { cost = it },
                        onStepChange = { step = it },
                        onTakeDashPicture = {
                            captureDashPhoto(context, imageCapture, cameraExecutor, selectedVehicleId, vehicles, step, scope, { lastCropDebug = it }, { lastOcrResult = it }, { lastOpenCVDebug = it }, { odometer = it }, { possibleOdometers = it }, { showOdometerConfirmation = it }, { gallons = it }, { cost = it }, odometerCropRect, { lastPhotoPath = it }, { lat, lon -> currentLatitude = lat; currentLongitude = lon })
                        },
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onExperimentClick = { navController.navigate("experiment") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
                        onSaveClick = { saveEntry() },
                        lastCropDebug = lastCropDebug,
                        lastOcrResult = lastOcrResult,
                        lastOpenCVDebug = lastOpenCVDebug,
                        dropdownExpanded = dropdownExpanded,
                        onDropdownExpandedChange = { dropdownExpanded = it }
                    )
                }
            }
        }
    }

    if (showAlignedDialog && lastOcrDebugResult != null) {
        OcrDebugDialog(
            ocrResult = lastOcrDebugResult!!,
            originalPhotoPath = lastPhotoPath,
            onDismiss = {
                lastOcrDebugResult?.croppedBitmap?.recycle()
                lastOcrDebugResult?.openCvProcessedBitmap?.recycle()
                lastOcrDebugResult = null
                showAlignedDialog = false
            }
        )
    }

    if (showOdometerConfirmation && lastOcrDebugResult != null && lastOcrDebugResult!!.possibleOdometers.size > 1) {
        AlertDialog(
            onDismissRequest = { showOdometerConfirmation = false },
            title = { Text("Confirm Odometer") },
            text = {
                Column {
                    lastOcrDebugResult!!.possibleOdometers.forEach { candidate ->
                        Button(
                            onClick = {
                                odometer = candidate.toIntOrNull() ?: odometer
                                showOdometerConfirmation = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(candidate)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOdometerConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
@Composable
private fun ColumnScope.ControlsContent(
    step: Int,
    isMissedFill: Boolean,
    isPartialFill: Boolean,
    odometer: Int,
    gallons: Double,
    cost: Double,
    selectedVehicleId: Int?,
    vehicles: List<Vehicle>,
    onVehicleChange: (Int?) -> Unit,
    onMissedChange: (Boolean) -> Unit,
    onPartialChange: (Boolean) -> Unit,
    onOdometerChange: (Int) -> Unit,
    onGallonsChange: (Double) -> Unit,
    onCostChange: (Double) -> Unit,
    onStepChange: (Int) -> Unit,
    onTakeDashPicture: () -> Unit,
    onAdvancedPick: () -> Unit,
    onExperimentClick: () -> Unit,
    onShowConfirmationChange: (Boolean) -> Unit,
    onPossibleOdometersChange: (List<String>) -> Unit,
    onOdometerConfirmed: (String) -> Unit,
    onSaveClick: () -> Unit,
    lastCropDebug: String,
    lastOcrResult: String,
    lastOpenCVDebug: String,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit
) {
    Text("Quick Fill-up — Step $step", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(
        expanded = dropdownExpanded,
        onExpandedChange = onDropdownExpandedChange
    ) {
        OutlinedTextField(
            value = selectedVehicleId?.let { vehicles.find { it.id == selectedVehicleId }?.name } ?: "Select vehicle",
            onValueChange = {},
            label = { Text("Vehicle") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            readOnly = true
        )
        ExposedDropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { onDropdownExpandedChange(false) }
        ) {
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.name) },
                    onClick = {
                        onVehicleChange(vehicle.id)
                        onDropdownExpandedChange(false)
                    }
                )
            }
        }
    }
    OutlinedTextField(
        value = odometer.toString(),
        onValueChange = { onOdometerChange(it.toIntOrNull() ?: 0) },
        label = { Text("Odometer") },
        modifier = Modifier.fillMaxWidth()
    )
    Row {
        Checkbox(checked = isMissedFill, onCheckedChange = onMissedChange)
        Text("Missed fill (unknown gas added)")
    }
    Row {
        Checkbox(checked = isPartialFill, onCheckedChange = onPartialChange)
        Text("Partial fill")
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onTakeDashPicture, modifier = Modifier.fillMaxWidth()) {
        Text("Take Dash Picture")
    }
    Button(onClick = onAdvancedPick, modifier = Modifier.fillMaxWidth()) {
        Text("Advanced: Pick Existing Picture")
    }
    Button(onClick = onExperimentClick, modifier = Modifier.fillMaxWidth()) {
        Text("Run Alignment Experiment (test new function)")
    }
    Spacer(modifier = Modifier.weight(1f))
    Button(onClick = onSaveClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Text("Save Fill-up")
    }
}
private fun captureDashPhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: java.util.concurrent.Executor,
    selectedVehicleId: Int?,
    vehicles: List<Vehicle>,
    step: Int,
    scope: CoroutineScope,
    updateCropDebug: (String) -> Unit,
    updateOcrResult: (String) -> Unit,
    updateOpenCVDebug: (String) -> Unit,
    updateOdometer: (Int) -> Unit,
    updatePossibleOdometers: (List<String>) -> Unit,
    updateShowConfirmation: (Boolean) -> Unit,
    updateGallons: (Double) -> Unit,
    updateCost: (Double) -> Unit,
    cropRect: Rect?,
    updateLastPhotoPath: (String) -> Unit,
    updateLocation: (Double?, Double?) -> Unit
) {
    val paths = mutableListOf<String>()
    
    // We'll take 3 photos in sequence: Flash ON -> Flash OFF -> Flash ON
    fun takeNext(index: Int) {
        if (index >= 3) {
            // All photos captured, process the best one (or consensus)
            val bestPath = paths.firstOrNull() ?: return
            updateLastPhotoPath(bestPath)
            scope.launch {
                processBurstPhotos(context, paths, selectedVehicleId, vehicles, step, updateCropDebug, updateOcrResult, updateOpenCVDebug, updateOdometer, updatePossibleOdometers, updateShowConfirmation, updateGallons, updateCost, cropRect, updateLocation)
            }
            return
        }

        // Configure flash for this shot
        imageCapture.flashMode = when(index) {
            0 -> ImageCapture.FLASH_MODE_ON
            1 -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_ON
        }

        val photoFile = File.createTempFile("burst_${index}_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("BurstCamera", "Photo $index failed", exception)
                    takeNext(index + 1) // Continue burst even if one fails
                }
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    paths.add(photoFile.absolutePath)
                    // Small delay to allow for slight hand movement/exposure adjustment
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        takeNext(index + 1)
                    }, 250)
                }
            }
        )
    }

    Toast.makeText(context, "Capturing burst...", Toast.LENGTH_SHORT).show()
    takeNext(0)
}

private suspend fun processBurstPhotos(
    context: Context,
    paths: List<String>,
    selectedVehicleId: Int?,
    vehicles: List<Vehicle>,
    step: Int,
    updateCropDebug: (String) -> Unit,
    updateOcrResult: (String) -> Unit,
    updateOpenCVDebug: (String) -> Unit,
    updateOdometer: (Int) -> Unit,
    updatePossibleOdometers: (List<String>) -> Unit,
    updateShowConfirmation: (Boolean) -> Unit,
    updateGallons: (Double) -> Unit,
    updateCost: (Double) -> Unit,
    cropRect: Rect?,
    updateLocation: (Double?, Double?) -> Unit
) {
    val cropRectF = cropRect?.let { r ->
        android.graphics.RectF(r.left, r.top, r.right, r.bottom)
    }

    // Run OCR on all paths and look for consensus
    val results = paths.map { path ->
        OdometerOcrUtils.extractFromPhoto(path, cropRectF)
    }

    // For now, let's pick the "Best" result based on digit confidence/count
    val bestResult = results.maxByOrNull { it.odometer?.length ?: 0 } ?: results.first()
    
    // Aggregate possible odometers from all burst frames
    val allPossible = results.flatMap { it.possibleOdometers }.distinct()

    updateOcrResult("Burst Odo: ${bestResult.odometer ?: "—"} | Gallons: ${bestResult.gallons ?: "—"}")
    bestResult.odometer?.toIntOrNull()?.let { updateOdometer(it) }
    updatePossibleOdometers(allPossible)
    updateGallons(bestResult.gallons?.toDoubleOrNull() ?: 0.0)
    updateCost(bestResult.cost?.toDoubleOrNull() ?: 0.0)
    updateLocation(bestResult.latitude, bestResult.longitude)
    updateCropDebug("Burst processed (${results.size} frames)")
    updateOpenCVDebug("Burst consensus complete")
}

private suspend fun processPhoto(
    context: Context,
    path: String,
    selectedVehicleId: Int?,
    vehicles: List<Vehicle>,
    step: Int,
    updateCropDebug: (String) -> Unit,
    updateOcrResult: (String) -> Unit,
    updateOpenCVDebug: (String) -> Unit,
    updateOdometer: (Int) -> Unit,
    updatePossibleOdometers: (List<String>) -> Unit,
    updateShowConfirmation: (Boolean) -> Unit,
    updateGallons: (Double) -> Unit,
    updateCost: (Double) -> Unit,
    cropRect: Rect?,
    updateLocation: (Double?, Double?) -> Unit
) {
    val cropRectF = cropRect?.let { r ->
        android.graphics.RectF(r.left, r.top, r.right, r.bottom)
    }
    val result = OdometerOcrUtils.extractFromPhoto(path, cropRectF)
    updateOcrResult("Odometer: ${result.odometer ?: "—"} | Gallons: ${result.gallons ?: "—"} | Cost: ${result.cost ?: "—"}")
    result.odometer?.toIntOrNull()?.let { updateOdometer(it) }
    updatePossibleOdometers(result.possibleOdometers)
    updateGallons(result.gallons?.toDoubleOrNull() ?: 0.0)
    updateCost(result.cost?.toDoubleOrNull() ?: 0.0)
    updateLocation(result.latitude, result.longitude)
    updateCropDebug("Crop sent to OCR")
    updateOpenCVDebug("OpenCV completed")
}
