package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var loc by remember { mutableStateOf<String?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var capturePending by remember { mutableStateOf(false) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stageLabel by remember { mutableStateOf("") }
    var isPhotoSaving by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember { prefs.getBoolean("debug_ocr_pipeline", false) }
    val saveFuelPhotos = remember { prefs.getBoolean("save_fuel_photos", true) }

    val defaultCurrency = remember { prefs.getString("currency_symbol", "$") ?: "$" }
    val defaultVolumeUnit = remember { prefs.getString("volume_unit", "G") ?: "G" }
    var captureMode by remember { mutableStateOf("odo") }
    var currencySymbol by remember { mutableStateOf(defaultCurrency) }
    var volumeUnit by remember { mutableStateOf(defaultVolumeUnit) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Camera Preview / Final Crop Display Area
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.Black)) {
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
                            capturePending = false
                            val bufferSet = NativePaddleEngine.bufferSetA
                            if (bufferSet.width != imageProxy.width || bufferSet.height != imageProxy.height) {
                                bufferSet.resize(imageProxy.width, imageProxy.height)
                            }
                            
                            val planes = imageProxy.planes
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
                                                withContext(Dispatchers.Main) {
                                                    stageLabel = stage
                                                    displayBitmap = bmp
                                                }
                                            }
                                        )
                                        
                                        withContext(Dispatchers.Main) {
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
                                    } finally {
                                        imageProxy.close()
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                        }
                                    }
                                }
                            } else {
                                imageProxy.close()
                                scope.launch(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(context, "Pump photo captured!", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(8.dp))

        // Camera Controls Box (Out of the live video)
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (displayBitmap != null) {
                if (!isProcessing) {
                    Button(
                        onClick = { displayBitmap = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again", color = MaterialTheme.colorScheme.onError)
                    }
                }
            } else {
                if (!isProcessing) {
                    IconButton(
                        onClick = {
                            isProcessing = true
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
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                            .border(4.dp, Color.Gray, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Vehicle Dropdown Selector
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle",
                onValueChange = {},
                label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                readOnly = true
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

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Single-Line Input Row with Local Overrides
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = odometer,
                onValueChange = { if (it.length <= 7 && it.all { c -> c.isDigit() }) odometer = it },
                label = { Text("Odo") },
                modifier = Modifier.weight(1.0f),
                singleLine = true
            )
            Button(
                onClick = { captureMode = if (captureMode == "odo") "pump" else "odo" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (captureMode == "odo") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.weight(0.9f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (captureMode == "odo") "Odo" else "Pump",
                    style = MaterialTheme.typography.labelMedium
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
                modifier = Modifier.weight(1.0f),
                singleLine = true
            )
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
                modifier = Modifier.weight(1.0f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    navController.popBackStack()
                }
            },
            enabled = !isProcessing && !isPhotoSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isPhotoSaving) "Saving Photo..." else "Save Fill-up")
        }
    }
}

