package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ManageVehiclesScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicleId by remember { mutableStateOf<Int?>(null) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var isNewVehicle by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var landmarkCropRect by remember { mutableStateOf<Rect?>(null) }
    var isEditingOcrArea by remember { mutableStateOf(false) }
    var isEditingLandmark by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var showEnlargedCrop by remember { mutableStateOf(false) }
    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }

    Log.d("CropDebug", "ManageVehiclesScreen recomposed — isEditingOcrArea=$isEditingOcrArea, odometerCropRect=$odometerCropRect, imageSize=$imageSize")

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

    LaunchedEffect(selectedVehicleId) {
        selectedVehicleId?.let { id ->
            val vehicle = vehicleViewModel.getVehicleById(id)
            editingVehicle = vehicle
            isNewVehicle = false
            vehicle?.let {
                name = it.name
                make = it.make ?: ""
                model = it.model ?: ""
                year = it.year?.toString() ?: ""
                licensePlate = it.licensePlate ?: ""
                odometerReading = ""
                referencePhotoUrl = it.referenceDashPhotoUrl
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f)
                }
                landmarkCropRect = it.landmarkCropLeft?.let { left ->
                    Rect(left, it.landmarkCropTop ?: 0f, it.landmarkCropRight ?: 1f, it.landmarkCropBottom ?: 1f)
                }
                isEditingOcrArea = false
                isEditingLandmark = false
            }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                Log.d("CropDebug", "Try OCR clicked — odometerCropRect=$odometerCropRect")
                try {
                    var finalPath = photoPathOrUri
                    if (photoPathOrUri.startsWith("content://")) {
                        val tempFile = File.createTempFile("ocr_vehicle", ".jpg", context.cacheDir)
                        context.contentResolver.openInputStream(Uri.parse(photoPathOrUri))?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        finalPath = tempFile.absolutePath
                    }
                    val cropRect = odometerCropRect?.let { r ->
                        android.graphics.RectF(r.left, r.top, r.right, r.bottom)
                    }
                    Log.d("CropDebug", "Calling extractFromPhoto with cropRect=$cropRect (null = full image)")
                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, cropRect)
                    lastOcrDebugResult = result
                    showEnlargedCrop = true
                    Log.d("CropDebug", "OCR result received — odometer=${result.odometer}, possible=${result.possibleOdometers.size}")

                    if (result.possibleOdometers.size > 1) {
                        showOdometerConfirmation = true
                    } else {
                        result.odometer?.let { odometerReading = it }
                    }
                } catch (e: Exception) {
                    Log.e("CropDebug", "OCR exception", e)
                    Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Toast.makeText(context, "No photo selected", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Manage Vehicles", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                selectedVehicleId = null
                editingVehicle = null
                isNewVehicle = true
                name = ""
                make = ""
                model = ""
                year = ""
                licensePlate = ""
                odometerReading = ""
                referencePhotoUrl = null
                odometerCropRect = null
                landmarkCropRect = null
                isEditingOcrArea = false
                isEditingLandmark = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New Vehicle")
        }

        Spacer(modifier = Modifier.height(8.dp))

        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = if (isNewVehicle) "New Vehicle" else (editingVehicle?.name ?: "Select vehicle"),
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
                            isNewVehicle = false
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isNewVehicle || editingVehicle != null) {
            PhotoPicker(
                photoStorageManager = settingsViewModel.photoStorageManager,
                photoType = PhotoType.FUEL,
                currentPhotoUrl = referencePhotoUrl,
                onPhotoUrlChanged = { referencePhotoUrl = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (referencePhotoUrl != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    dragOffset = Offset.Zero
                                    Log.d("CropDebug", "Drag START at $offset — isEditingOcrArea=$isEditingOcrArea")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    if (start != null) {
                                        val end = Offset(start.x + dragOffset.x, start.y + dragOffset.y)
                                        val left = minOf(start.x, end.x) / maxWidth.value
                                        val top = minOf(start.y, end.y) / maxHeight.value
                                        val right = maxOf(start.x, end.x) / maxWidth.value
                                        val bottom = maxOf(start.y, end.y) / maxHeight.value
                                        val newRect = Rect(left, top, right, bottom)
                                        Log.d("CropDebug", "Drag END — normalized Rect=$newRect")
                                        if (isEditingOcrArea) {
                                            odometerCropRect = newRect
                                            Log.d("CropDebug", "✅ Committed normalized odometerCropRect=$odometerCropRect")
                                        } else if (isEditingLandmark) {
                                            landmarkCropRect = newRect
                                            Log.d("CropDebug", "✅ Committed normalized landmarkCropRect=$landmarkCropRect")
                                        }
                                    }
                                    dragStart = null
                                    dragOffset = Offset.Zero
                                }
                            )
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(referencePhotoUrl),
                        contentDescription = "Reference dash photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        odometerCropRect?.let { rect ->
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                                size = androidx.compose.ui.geometry.Size(rect.width * size.width, rect.height * size.height),
                                style = Stroke(width = 4f)
                            )
                        }
                        landmarkCropRect?.let { rect ->
                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                                size = androidx.compose.ui.geometry.Size(rect.width * size.width, rect.height * size.height),
                                style = Stroke(width = 4f)
                            )
                        }
                        if (dragStart != null && isEditingOcrArea) {
                            val end = Offset(dragStart!!.x + dragOffset.x, dragStart!!.y + dragOffset.y)
                            val left = minOf(dragStart!!.x, end.x)
                            val top = minOf(dragStart!!.y, end.y)
                            val right = maxOf(dragStart!!.x, end.x)
                            val bottom = maxOf(dragStart!!.y, end.y)
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = Stroke(
                                    width = 4f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isEditingOcrArea = !isEditingOcrArea; isEditingLandmark = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEditingOcrArea) "Done Editing Odometer" else "Edit Odometer Crop")
                }
                Button(
                    onClick = { isEditingLandmark = !isEditingLandmark; isEditingOcrArea = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEditingLandmark) "Done Editing Landmark" else "Edit Landmark Crop")
                }
            }

            if (odometerCropRect != null) {
                Button(
                    onClick = { odometerCropRect = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Clear Odometer Crop Box")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = tryOcr, modifier = Modifier.fillMaxWidth()) {
                Text("Try OCR Now")
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                label = { Text("Make") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Year") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = licensePlate,
                onValueChange = { licensePlate = it },
                label = { Text("License Plate") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = odometerReading,
                onValueChange = { odometerReading = it },
                label = { Text("Initial Odometer") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isNewVehicle) {
                        vehicleViewModel.createNewVehicleWithReference(
                            name = name,
                            make = make,
                            model = model,
                            year = year.toIntOrNull(),
                            licensePlate = licensePlate,
                            referenceDashPhotoUrl = referencePhotoUrl,
                            odometerCropRect = odometerCropRect,
                            initialOdometer = odometerReading.toIntOrNull() ?: 0
                        )
                        Toast.makeText(context, "New vehicle created", Toast.LENGTH_SHORT).show()
                    } else {
                        editingVehicle?.let { vehicle ->
                            vehicleViewModel.updateVehicle(
                                vehicle.copy(
                                    name = name,
                                    make = make,
                                    model = model,
                                    year = year.toIntOrNull(),
                                    licensePlate = licensePlate,
                                    referenceDashPhotoUrl = referencePhotoUrl,
                                    odometerCropLeft = odometerCropRect?.left,
                                    odometerCropTop = odometerCropRect?.top,
                                    odometerCropRight = odometerCropRect?.right,
                                    odometerCropBottom = odometerCropRect?.bottom,
                                    landmarkCropLeft = landmarkCropRect?.left,
                                    landmarkCropTop = landmarkCropRect?.top,
                                    landmarkCropRight = landmarkCropRect?.right,
                                    landmarkCropBottom = landmarkCropRect?.bottom
                                )
                            )
                            Toast.makeText(context, "Vehicle updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isNewVehicle) "Create Vehicle" else "Save Changes")
            }
        }
    }

    if (showEnlargedCrop && lastOcrDebugResult != null) {
        AlertDialog(
            onDismissRequest = { showEnlargedCrop = false },
            title = { Text("OCR Debug") },
            text = {
                Column {
                    Text(lastOcrDebugResult!!.debugText)
                }
            },
            confirmButton = {
                Button(onClick = { showEnlargedCrop = false }) {
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
                                odometerReading = candidate
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
