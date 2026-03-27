package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

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

    // Persistent debug state so toasts don't disappear too fast
    var lastCropDebug by remember { mutableStateOf("No crop info yet") }
    var lastOcrResult by remember { mutableStateOf("No OCR run yet") }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            Toast.makeText(context, "Image selected — processing...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val tempFile = File.createTempFile("ocr_gallery", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
                val crop = selectedVehicle?.let {
                    androidx.compose.ui.geometry.Rect(
                        it.odometerCropLeft ?: 0f,
                        it.odometerCropTop ?: 0f,
                        it.odometerCropRight ?: 1f,
                        it.odometerCropBottom ?: 1f
                    )
                }

                val cropDebug = if (crop != null) {
                    "Crop L=${"%.3f".format(crop.left)} T=${"%.3f".format(crop.top)} R=${"%.3f".format(crop.right)} B=${"%.3f".format(crop.bottom)} (w=${"%.3f".format(crop.right - crop.left)}, h=${"%.3f".format(crop.bottom - crop.top)})"
                } else {
                    "NO CROP — full image OCR (vehicleId=$selectedVehicleId)"
                }

                lastCropDebug = "Gallery: $cropDebug"

                val result = OdometerOcrUtils.extractFromPhoto(
                    tempFile.absolutePath,
                    crop?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                )

                val ocrMsg = "OCR RESULT (Step $step): odometer=${result.odometer} | possible=${result.possibleOdometers}"
                lastOcrResult = ocrMsg

                Toast.makeText(context, ocrMsg, Toast.LENGTH_LONG).show()

                if (step == 1) {
                    if (result.possibleOdometers.isNotEmpty()) {
                        possibleOdometers = result.possibleOdometers
                        showOdometerConfirmation = true
                    } else {
                        odometer = result.odometer?.toIntOrNull() ?: odometer
                    }
                } else {
                    gallons = result.gallons?.toDoubleOrNull() ?: gallons
                    cost = result.cost?.toDoubleOrNull() ?: cost
                }
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
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
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
                        context = context,
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
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        scope = scope,
                        viewModel = fuelViewModel,
                        navController = navController,
                        showOdometerConfirmation = showOdometerConfirmation,
                        possibleOdometers = possibleOdometers,
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
                        lastCropDebug = lastCropDebug,
                        lastOcrResult = lastOcrResult
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
                    Text(
                        text = "DEBUG: QuickFillupScreen.kt (portrait)",
                        color = Color.Red,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsContent(
                        context = context,
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
                        onAdvancedPick = { pickImageLauncher.launch("image/*") },
                        onShowConfirmationChange = { showOdometerConfirmation = it },
                        onPossibleOdometersChange = { possibleOdometers = it },
                        scope = scope,
                        viewModel = fuelViewModel,
                        navController = navController,
                        showOdometerConfirmation = showOdometerConfirmation,
                        possibleOdometers = possibleOdometers,
                        onOdometerConfirmed = { selected ->
                            odometer = selected.toIntOrNull() ?: odometer
                            showOdometerConfirmation = false
                        },
                        lastCropDebug = lastCropDebug,
                        lastOcrResult = lastOcrResult
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsContent(
    context: Context,
    step: Int,
    isMissedFill: Boolean,
    isPartialFill: Boolean,
    odometer: Int,
    gallons: Double,
    cost: Double,
    selectedVehicleId: Int?,
    vehicles: List<com.davidlang.vehicleexpensesautomated.data.model.Vehicle>,
    onVehicleChange: (Int?) -> Unit,
    onMissedChange: (Boolean) -> Unit,
    onPartialChange: (Boolean) -> Unit,
    onOdometerChange: (Int) -> Unit,
    onGallonsChange: (Double) -> Unit,
    onCostChange: (Double) -> Unit,
    onStepChange: (Int) -> Unit,
    onAdvancedPick: () -> Unit,
    onShowConfirmationChange: (Boolean) -> Unit,
    onPossibleOdometersChange: (List<String>) -> Unit,
    scope: CoroutineScope,
    viewModel: FuelViewModel,
    navController: NavHostController,
    showOdometerConfirmation: Boolean,
    possibleOdometers: List<String>,
    onOdometerConfirmed: (String) -> Unit,
    lastCropDebug: String,
    lastOcrResult: String
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle",
            onValueChange = {},
            label = { Text("Vehicle") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            readOnly = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.name) },
                    onClick = {
                        onVehicleChange(vehicle.id)
                        expanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Persistent debug panel
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("DEBUG CROP INFO", style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
            Text(lastCropDebug, fontSize = 12.sp, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(lastOcrResult, fontSize = 12.sp, color = Color.Cyan)
        }
    }

    if (step == 1) {
        Text("Step 1: Point at dashboard", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = odometer.toString(),
            onValueChange = { onOdometerChange(it.toIntOrNull() ?: 0) },
            label = { Text("Odometer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isMissedFill, onCheckedChange = onMissedChange)
            Text("Missed fill (unknown gas added)")
        }

        Button(
            onClick = {
                scope.launch {
                    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
                    val crop = selectedVehicle?.let {
                        androidx.compose.ui.geometry.Rect(
                            it.odometerCropLeft ?: 0f,
                            it.odometerCropTop ?: 0f,
                            it.odometerCropRight ?: 1f,
                            it.odometerCropBottom ?: 1f
                        )
                    }

                    val cropDebug = if (crop != null) {
                        "L=${"%.3f".format(crop.left)} T=${"%.3f".format(crop.top)} R=${"%.3f".format(crop.right)} B=${"%.3f".format(crop.bottom)}"
                    } else "NO CROP"
                    Toast.makeText(context, "Dash button — using crop: $cropDebug", Toast.LENGTH_LONG).show()

                    val result = OdometerOcrUtils.extractFromPhoto(
                        "dummy_dash.jpg",
                        crop?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                    )

                    Toast.makeText(
                        context,
                        "OCR RESULT (Step $step): odometer=${result.odometer} | possible=${result.possibleOdometers}",
                        Toast.LENGTH_LONG
                    ).show()

                    if (result.possibleOdometers.isNotEmpty()) {
                        onPossibleOdometersChange(result.possibleOdometers)
                        onShowConfirmationChange(true)
                    } else {
                        onOdometerChange(result.odometer?.toIntOrNull() ?: odometer)
                    }

                    if (isMissedFill) {
                        val entry = FuelEntry(
                            vehicleId = selectedVehicleId ?: 0,
                            odometer = odometer,
                            gallons = -1.0,
                            cost = -1.0,
                            timestamp = System.currentTimeMillis(),
                            isPartialFill = false
                        )
                        viewModel.saveFuel(entry)
                        navController.navigate("reports")
                    } else {
                        onStepChange(2)
                    }
                }
            },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Take Dash Picture")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = onPartialChange)
            Text("Partial fill")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAdvancedPick, modifier = Modifier.fillMaxWidth()) {
            Text("Advanced: Pick existing picture")
        }
    } else {
        Text("Step 2: Point at pump", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = gallons.toString(),
            onValueChange = { onGallonsChange(it.toDoubleOrNull() ?: 0.0) },
            label = { Text("Gallons") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = cost.toString(),
            onValueChange = { onCostChange(it.toDoubleOrNull() ?: 0.0) },
            label = { Text("Total Cost") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = onPartialChange)
            Text("Partial fill")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
                    val crop = selectedVehicle?.let {
                        androidx.compose.ui.geometry.Rect(
                            it.odometerCropLeft ?: 0f,
                            it.odometerCropTop ?: 0f,
                            it.odometerCropRight ?: 1f,
                            it.odometerCropBottom ?: 1f
                        )
                    }

                    val cropDebug = if (crop != null) "L=${"%.3f".format(crop.left)} ..." else "NO CROP"
                    Toast.makeText(context, "Pump button — using crop: $cropDebug", Toast.LENGTH_SHORT).show()

                    val result = OdometerOcrUtils.extractFromPhoto(
                        "dummy_pump.jpg",
                        crop?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                    )

                    Toast.makeText(
                        context,
                        "OCR RESULT (Step $step): gallons=${result.gallons} | cost=${result.cost}",
                        Toast.LENGTH_LONG
                    ).show()

                    onGallonsChange(result.gallons?.toDoubleOrNull() ?: gallons)
                    onCostChange(result.cost?.toDoubleOrNull() ?: cost)

                    val entry = FuelEntry(
                        vehicleId = selectedVehicleId ?: 0,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        timestamp = System.currentTimeMillis(),
                        isPartialFill = isPartialFill
                    )
                    viewModel.saveFuel(entry)
                    navController.navigate("reports")
                }
            },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Take Pump Picture")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onAdvancedPick, modifier = Modifier.fillMaxWidth()) {
            Text("Advanced: Pick existing picture")
        }
    }

    if (showOdometerConfirmation) {
        AlertDialog(
            onDismissRequest = { onShowConfirmationChange(false) },
            title = { Text("Confirm Odometer Reading") },
            text = {
                Column {
                    Text("Multiple possible readings found. Tap the correct one:")
                    Spacer(modifier = Modifier.height(8.dp))
                    possibleOdometers.forEach { candidate ->
                        Button(
                            onClick = {
                                onOdometerConfirmed(candidate)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(candidate)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onShowConfirmationChange(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
