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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
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
import androidx.compose.ui.geometry.Rect

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
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Load vehicle crop box when vehicle is selected
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
                val result = OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath)
                lastOcrDebugResult = result
                showAlignedDialog = true
                tempFile.delete()
            }
        }
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
                            captureDashPhoto(context, imageCapture, cameraExecutor, selectedVehicleId, vehicles, step, scope, { lastCropDebug = it }, { lastOcrResult = it }, { lastOpenCVDebug = it }, { odometer = it }, { possibleOdometers = it }, { showOdometerConfirmation = it }, { gallons = it }, { cost = it })
                        },
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
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
                        .height(200.dp)
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                            captureDashPhoto(context, imageCapture, cameraExecutor, selectedVehicleId, vehicles, step, scope, { lastCropDebug = it }, { lastOcrResult = it }, { lastOpenCVDebug = it }, { odometer = it }, { possibleOdometers = it }, { showOdometerConfirmation = it }, { gallons = it }, { cost = it })
                        },
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
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
        AlertDialog(
            onDismissRequest = { showAlignedDialog = false },
            title = { Text("OCR Debug") },
            text = {
                Column {
                    Text(lastOcrDebugResult!!.debugText)
                }
            },
            confirmButton = {
                Button(onClick = { showAlignedDialog = false }) {
                    Text("Close")
                }
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
private fun ControlsContent(
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
    onShowConfirmationChange: (Boolean) -> Unit,
    onPossibleOdometersChange: (List<String>) -> Unit,
    onOdometerConfirmed: (String) -> Unit,
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
    updateCost: (Double) -> Unit
) {
    val result = OdometerOcrUtils.extractFromPhoto(path, null)
    updateOcrResult("Odometer: ${result.odometer ?: "—"} | Gallons: ${result.gallons ?: "—"} | Cost: ${result.cost ?: "—"}")
    result.odometer?.toIntOrNull()?.let { updateOdometer(it) }
    updatePossibleOdometers(result.possibleOdometers)
    updateGallons(result.gallons?.toDoubleOrNull() ?: 0.0)
    updateCost(result.cost?.toDoubleOrNull() ?: 0.0)
    updateCropDebug("Crop sent to OCR")
    updateOpenCVDebug("OpenCV completed")
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
    updateCost: (Double) -> Unit
) {
    val photoFile = File.createTempFile("dash_", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Camera error", Toast.LENGTH_SHORT).show()
            }
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                scope.launch {
                    processPhoto(context, photoFile.absolutePath, selectedVehicleId, vehicles, step, updateCropDebug, updateOcrResult, updateOpenCVDebug, updateOdometer, updatePossibleOdometers, updateShowConfirmation, updateGallons, updateCost)
                }
            }
        }
    )
}
