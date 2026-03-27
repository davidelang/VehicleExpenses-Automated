package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.RectF
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

    var isEditingOcrArea by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDrag by remember { mutableStateOf<Offset?>(null) }

    var showEnlargedCrop by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var lastOcrDebug by remember { mutableStateOf<String>("") }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

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
                odometerReading = ""
                referencePhotoUrl = it.referenceDashPhotoUrl
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f)
                }
                isEditingOcrArea = false
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

                    val cropRectF = odometerCropRect?.let { r ->
                        RectF(r.left, r.top, r.right, r.bottom)
                    }

                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, cropRectF)

                    result.odometer?.let { odometerReading = it }

                    val cropInfo = odometerCropRect?.let { r ->
                        "Normalized crop sent to OCR: [${"%.3f".format(r.left)}, ${"%.3f".format(r.top)}, ${"%.3f".format(r.right)}, ${"%.3f".format(r.bottom)}]"
                    } ?: "No crop defined"

                    val candidatesMsg = if (result.possibleOdometers.isNotEmpty()) {
                        "Candidates: ${result.possibleOdometers.joinToString()}"
                    } else "No candidates"

                    val debugText = buildString {
                        appendLine("=== OCR DEBUG ===")
                        appendLine(cropInfo)
                        appendLine("Final odometer: ${result.odometer ?: "NONE"}")
                        appendLine(candidatesMsg)
                        appendLine("Raw text blocks found: ${result.possibleOdometers.size} total candidates before dedup")
                        appendLine("")
                        appendLine("Check Logcat (tag: OdometerOcr) for detailed crop/padding info.")
                    }

                    lastOcrDebug = debugText

                    Toast.makeText(
                        context,
                        if (result.odometer != null) "OCR Success: ${result.odometer}" else "Still no reading — check Logcat",
                        Toast.LENGTH_LONG
                    ).show()

                    showEnlargedCrop = true
                } catch (e: Exception) {
                    Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                    lastOcrDebug = "Exception: ${e.message}"
                }
            }
        } ?: run {
            Toast.makeText(context, "No photo selected", Toast.LENGTH_SHORT).show()
        }
    }

    val saveOcrArea = {
        isEditingOcrArea = false
        Toast.makeText(context, "Fixed reference crop saved", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Manage Vehicles", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = editingVehicle?.name ?: "New Vehicle",
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
                        isEditingOcrArea = false
                        dropdownExpanded = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Vehicle Name (required)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate (optional)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))

        PhotoPicker(
            photoStorageManager = settingsViewModel.photoStorageManager,
            photoType = PhotoType.FUEL,
            currentPhotoUrl = referencePhotoUrl,
            onPhotoUrlChanged = { referencePhotoUrl = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (referencePhotoUrl != null) {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                val imageModifier = if (isEditingOcrArea) {
                    Modifier
                        .fillMaxSize()
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
                                    Toast.makeText(context, "Crop updated — tap Try OCR Now", Toast.LENGTH_SHORT).show()
                                    dragStart = null
                                    currentDrag = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    currentDrag = null
                                }
                            )
                        }
                } else {
                    Modifier.fillMaxSize()
                }

                Box(modifier = imageModifier) {
                    Image(
                        painter = rememberAsyncImagePainter(referencePhotoUrl),
                        contentDescription = "Reference dash photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    if (isEditingOcrArea && dragStart != null && currentDrag != null) {
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

            if (isEditingOcrArea) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            odometerCropRect = null
                            dragStart = null
                            currentDrag = null
                            Toast.makeText(context, "Crop reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Crop")
                    }

                    Button(onClick = tryOcr, modifier = Modifier.weight(1f)) {
                        Text("Try OCR Now")
                    }

                    Button(onClick = saveOcrArea, modifier = Modifier.weight(1f)) {
                        Text("Save OCR Area")
                    }
                }
            } else {
                Button(
                    onClick = { isEditingOcrArea = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Fixed Reference Crop (drag a generous rectangle around the odometer)")
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                Text("No dash photo yet", modifier = Modifier.align(Alignment.Center))
            }
        }

        if (isEditingOcrArea) {
            OutlinedTextField(
                value = odometerReading,
                onValueChange = { odometerReading = it },
                label = { Text("Odometer reading (auto-filled by OCR)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (name.isNotBlank()) {
                    if (editingVehicle != null) {
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
                        Toast.makeText(context, "Vehicle updated with fixed reference crop", Toast.LENGTH_LONG).show()
                    } else {
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

        if (editingVehicle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Delete Vehicle")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }

    if (showDeleteConfirm && editingVehicle != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Vehicle?") },
            text = { Text("This will permanently delete ${editingVehicle!!.name} and all associated fuel entries.") },
            confirmButton = {
                Button(
                    onClick = {
                        vehicleViewModel.deleteVehicle(editingVehicle!!)
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

    if (showEnlargedCrop && referencePhotoUrl != null) {
        AlertDialog(
            onDismissRequest = { showEnlargedCrop = false },
            title = { Text("OCR Debug — Enlarged Crop Preview") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = lastOcrDebug.ifEmpty { "Run 'Try OCR Now' first" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEnlargedCrop = false }) {
                    Text("Close")
                }
            }
        )
    }
}
