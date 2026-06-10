package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
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

    var isCapturing by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val debugMode = remember { prefs.getBoolean("debug_ocr_pipeline", false) }

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
        // Camera Preview (Top Half)
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onImageCaptured = { imageProxy ->
                    if (isCapturing) {
                        isCapturing = false
                        val currentVehicle = vehicles.find { it.id == selectedVehicleId }
                        if (currentVehicle != null) {
                            scope.launch {
                                try {
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

                                    val result = OcrHarness.runAutoFillPipeline(context, bufferSet, vehicles, debugMode)
                                    
                                    if (result.error != null) {
                                        Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                    }

                                    result.vehicleId?.let { selectedVehicleId = it }
                                    result.odometer?.let { odometer = it }

                                    if (debugMode && result.debugJson != null) {
                                        val timestamp = System.currentTimeMillis()
                                        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "debug_ocr_odometer_$timestamp.json")
                                        file.writeText(result.debugJson)
                                        Toast.makeText(context, "Debug saved to Documents", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("QuickFill", "OCR Pipeline failed", e)
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }
            )
            
            Button(
                onClick = { isCapturing = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            ) {
                Text("Capture Odometer")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        PhotoPicker(
            photoStorageManager = settingsViewModel.photoStorageManager,
            photoType = PhotoType.FUEL,
            currentPhotoUrl = photoUrl,
            onPhotoUrlChanged = { photoUrl = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer Reading") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = gallons, onValueChange = { gallons = it }, label = { Text("Gallons") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Total Cost") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Fill-up")
        }
    }
}
