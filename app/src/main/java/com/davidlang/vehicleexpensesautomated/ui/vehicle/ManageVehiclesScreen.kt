package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }

    // Zoom / pan / rotate state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, rotationChange ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
        rotation += rotationChange
    }

    // Drag-to-draw state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDrag by remember { mutableStateOf<Offset?>(null) }

    var showEnlargedCrop by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Load selected vehicle into form
    LaunchedEffect(selectedVehicleId) {
        selectedVehicleId?.let { id ->
            val vehicle = vehicleViewModel.getVehicleById(id)
            editingVehicle = vehicle
            vehicle?.let {
                name = it.name
                make = it.make ?: ""
                model = it.model ?: ""
                year = it.year?.toString() ?: ""
                licensePlate = it.licensePlate ?: ""
                odometerReading = ""  // OCR will fill this
                referencePhotoUrl = it.referenceDashPhotoUrl
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(
                        left = left,
                        top = it.odometerCropTop ?: 0f,
                        right = it.odometerCropRight ?: 1f,
                        bottom = it.odometerCropBottom ?: 1f
                    )
                }
            }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
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
                    val crop = odometerCropRect?.let { RectF(it.left, it.top, it.right, it.bottom) }
                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, crop)
                    result.odometer?.let { odometerReading = it }

                    val candidates = if (result.possibleOdometers.isNotEmpty()) {
                        "Candidates: ${result.possibleOdometers.joinToString()}"
                    } else "No candidates found — try selecting a LARGER area around the numbers"

                    val cropDebug = odometerCropRect?.let { 
                        "Crop: L=${"%.2f".format(it.left)} T=${"%.2f".format(it.top)} R=${"%.2f".format(it.right)} B=${"%.2f".format(it.bottom)}" 
                    } ?: "No crop selected"

                    Toast.makeText(
                        context,
                        "OCR: ${result.odometer ?: "—"} ($candidates) — $cropDebug",
                        Toast.LENGTH_LONG
                    ).show()

                    showEnlargedCrop = true
                } catch (e: Exception) {
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

        // Vehicle dropdown at top
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = editingVehicle?.name ?: "New Vehicle",
                onValueChange = {},
                label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("New Vehicle") },
                    onClick = {
                        selectedVehicleId = null
                        editingVehicle = null
                        name = ""
                        make = ""
                        model = ""
                        year = ""
                        licensePlate = ""
                        odometerReading = ""
                        referencePhotoUrl = null
                        odometerCropRect = null
                        dropdownExpanded = false
                    }
                )
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

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Vehicle Name (required)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = make,
            onValueChange = { make = it },
            label = { Text("Make (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = year,
            onValueChange = { year = it },
            label = { Text("Year (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = licensePlate,
            onValueChange = { licensePlate = it },
            label = { Text("License Plate (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        PhotoPicker(
            photoStorageManager = settingsViewModel.photoStorageManager,
            photoType = PhotoType.FUEL,
            currentPhotoUrl = referencePhotoUrl,
            onPhotoUrlChanged = { referencePhotoUrl = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (referencePhotoUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotation
                        )
                        .transformable(state = transformState)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    currentDrag = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentDrag = (currentDrag ?: dragStart)!! + dragAmount
                                },
                                onDragEnd = {
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val start = dragStart ?: Offset.Zero
                                    val end = currentDrag ?: start
                                    val left = (start.x.coerceAtMost(end.x) / w).coerceIn(0f, 1f)
                                    val top = (start.y.coerceAtMost(end.y) / h).coerceIn(0f, 1f)
                                    val right = (start.x.coerceAtLeast(end.x) / w).coerceIn(0f, 1f)
                                    val bottom = (start.y.coerceAtLeast(end.y) / h).coerceIn(0f, 1f)
                                    odometerCropRect = Rect(left, top, right, bottom)
                                    Toast.makeText(context, "Odometer region calibrated", Toast.LENGTH_SHORT).show()
                                    dragStart = null
                                    currentDrag = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    currentDrag = null
                                }
                            )
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(referencePhotoUrl),
                        contentDescription = "Reference dash photo — single finger drag to mark, two fingers to zoom/pan/rotate",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    if (dragStart != null && currentDrag != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val start = dragStart!!
                            val end = currentDrag!!
                            val width = (end.x - start.x).coerceAtLeast(0f)
                            val height = (end.y - start.y).coerceAtLeast(0f)
                            drawRect(
                                color = Color.Blue.copy(alpha = 0.4f),
                                topLeft = Offset(start.x.coerceAtMost(end.x), start.y.coerceAtMost(end.y)),
                                size = androidx.compose.ui.geometry.Size(width, height)
                            )
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(start.x.coerceAtMost(end.x), start.y.coerceAtMost(end.y)),
                                size = androidx.compose.ui.geometry.Size(width, height),
                                style = Stroke(width = 4f)
                            )
                        }
                    }

                    odometerCropRect?.let { crop ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val leftPx = crop.left * w
                            val topPx = crop.top * h
                            val rightPx = crop.right * w
                            val bottomPx = crop.bottom * h
                            drawRect(
                                color = Color.Green.copy(alpha = 0.3f),
                                topLeft = Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx)
                            )
                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                style = Stroke(width = 6f)
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                Text("No dash photo yet", modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    odometerCropRect = null
                    dragStart = null
                    currentDrag = null
                    scale = 1f
                    offset = Offset.Zero
                    rotation = 0f
                    Toast.makeText(context, "Region & view reset", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset All")
            }

            Button(
                onClick = tryOcr,
                modifier = Modifier.weight(1f)
            ) {
                Text("Try OCR Now")
            }
        }

        OutlinedTextField(
            value = odometerReading,
            onValueChange = { odometerReading = it },
            label = { Text("Odometer reading (auto-filled by OCR)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (name.isNotBlank()) {
                    if (editingVehicle != null) {
                        // Update existing
                        val updated = editingVehicle!!.copy(
                            name = name,
                            make = make.ifBlank { null },
                            model = model.ifBlank { null },
                            year = year.toIntOrNull(),
                            licensePlate = licensePlate.ifBlank { null },
                            referenceDashPhotoUrl = referencePhotoUrl,
                            odometerCropLeft = odometerCropRect?.left,
                            odometerCropTop = odometerCropRect?.top,
                            odometerCropRight = odometerCropRect?.right,
                            odometerCropBottom = odometerCropRect?.bottom
                        )
                        vehicleViewModel.updateVehicle(updated)
                        Toast.makeText(context, "Vehicle updated", Toast.LENGTH_LONG).show()
                    } else {
                        // Create new
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
                        Toast.makeText(context, "Vehicle saved with odometer calibration", Toast.LENGTH_LONG).show()
                    }
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "Vehicle name is required", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (editingVehicle != null) "Update Vehicle" else "Save Vehicle")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Delete button (only shown when editing)
        if (editingVehicle != null) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Delete Vehicle")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && editingVehicle != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Vehicle?") },
            text = { Text("This will permanently delete ${editingVehicle!!.name} and all associated fuel entries.") },
            confirmButton = {
                Button(
                    onClick = {
                        vehicleViewModel.deleteVehicle(editingVehicle!!)
                        Toast.makeText(context, "Vehicle deleted", Toast.LENGTH_LONG).show()
                        showDeleteConfirm = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Enlarged crop region preview dialog
    if (showEnlargedCrop && referencePhotoUrl != null && odometerCropRect != null) {
        AlertDialog(
            onDismissRequest = { showEnlargedCrop = false },
            title = { Text("Enlarged Crop Region Preview (what OCR actually sees)") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(referencePhotoUrl),
                        contentDescription = "Enlarged crop preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    odometerCropRect?.let { crop ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val leftPx = crop.left * w
                            val topPx = crop.top * h
                            val rightPx = crop.right * w
                            val bottomPx = crop.bottom * h
                            drawRect(
                                color = Color.Red.copy(alpha = 0.3f),
                                topLeft = Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx)
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                style = Stroke(width = 8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showEnlargedCrop = false }) {
                    Text("Close Preview")
                }
            }
        )
    }
}
