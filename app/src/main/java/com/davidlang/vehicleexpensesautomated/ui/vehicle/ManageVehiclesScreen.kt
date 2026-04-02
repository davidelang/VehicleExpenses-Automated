package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.components.OcrDebugDialog
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import kotlinx.coroutines.launch
import java.io.File

private fun calculateFitImageRect(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Rect {
    if (imageWidth <= 0f || imageHeight <= 0f) return Rect(0f, 0f, containerWidth, containerHeight)
    val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale
    val left = (containerWidth - scaledWidth) / 2f
    val top = (containerHeight - scaledHeight) / 2f
    return Rect(left, top, left + scaledWidth, top + scaledHeight)
}

@Composable
fun ManageVehiclesScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val diagnosticVariants by vehicleViewModel.diagnosticVariants.collectAsState()
    val radialSteps by vehicleViewModel.radialSteps.collectAsState()

    var selectedVehicleId by remember { mutableStateOf<Int?>(null) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var isNewVehicle by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }

    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }

    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var landmarkCropRect by remember { mutableStateOf<Rect?>(null) }

    var isEditingOcrArea by remember { mutableStateOf(false) }
    var isEditingLandmark by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var originalImageSize by remember { mutableStateOf(Offset.Zero) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }

    var showEnlargedCrop by remember { mutableStateOf(false) }
    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }

    Log.d("CropDebug", "ManageVehiclesScreen recomposed — isEditingOcrArea=$isEditingOcrArea, odometerCropRect=$odometerCropRect, variants=${diagnosticVariants.size}, radialSteps=${radialSteps.size}")

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

    LaunchedEffect(selectedVehicleId, vehicles) {
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
                pickedPhotoUrl = it.referenceDashPhotoUrl
                referencePhotoUrl = it.cleanedReferenceDashPhotoUrl ?: it.referenceDashPhotoUrl
                Log.i("CropDebug", "Loaded vehicle ${it.id} — picked=$pickedPhotoUrl, cleaned=$referencePhotoUrl")
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f)
                }
                landmarkCropRect = it.landmarkCropLeft?.let { left ->
                    Rect(left, it.landmarkCropTop ?: 0f, it.landmarkCropRight ?: 1f, it.landmarkCropBottom ?: 1f)
                }
                isEditingOcrArea = false
                isEditingLandmark = false
                currentDragRect = null
            }
        }
    }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            Log.i("CropDebug", "pickedPhotoUrl changed → loading diagnostic grid for $url")
            vehicleViewModel.loadDiagnosticGrid(url)
            referencePhotoUrl = url
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
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        finalPath = tempFile.absolutePath
                    }
                    val cropRect = odometerCropRect?.let { r ->
                        android.graphics.RectF(r.left, r.top, r.right, r.bottom)
                    }
                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, cropRect)
                    lastOcrDebugResult = result
                    showEnlargedCrop = true
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
                pickedPhotoUrl = null
                referencePhotoUrl = null
                odometerCropRect = null
                landmarkCropRect = null
                currentDragRect = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New Vehicle")
        }
        Spacer(modifier = Modifier.height(8.dp))
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
            OutlinedTextField(
                value = if (isNewVehicle) "New Vehicle" else (editingVehicle?.name ?: "Select vehicle"),
                onValueChange = {},
                label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                readOnly = true
            )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
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
                currentPhotoUrl = pickedPhotoUrl,
                onPhotoUrlChanged = { pickedPhotoUrl = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (pickedPhotoUrl != null || referencePhotoUrl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pickedPhotoUrl != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Original Picked", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Box(modifier = Modifier.height(220.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(pickedPhotoUrl),
                                    contentDescription = "Original",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                    if (referencePhotoUrl != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CLEANED (ticks removed)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            Box(modifier = Modifier.height(220.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(referencePhotoUrl),
                                    contentDescription = "Cleaned",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // === OLD GRID (shrunk as requested) ===
            if (diagnosticVariants.isNotEmpty()) {
                Text("Old Tic-Removal Grid (Variants 1-3 only)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(700.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(diagnosticVariants) { bmp ->
                        val index = diagnosticVariants.indexOf(bmp)
                        Column {
                            Text(
                                text = if (index == 0) "Original" else "Variant $index",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == 0) Color.Gray else Color(0xFF4CAF50)
                            )
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // === RADIAL LINE METHOD (only remaining tic-removal method) ===
            if (radialSteps.isNotEmpty()) {
                Text("Radial Line Subtraction Method — 4 Steps", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2196F3))
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(700.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(radialSteps) { bmp ->
                        val index = radialSteps.indexOf(bmp)
                        val label = when (index) {
                            0 -> "Original"
                            1 -> "Extracted Radial Lines"
                            2 -> "Inverted Radial Lines"
                            3 -> "Final Merged"
                            else -> "Step $index"
                        }
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2196F3)
                            )
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (referencePhotoUrl != null) {
                Log.i("CropDebug", "Using reference for crop/edit: $referencePhotoUrl")
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .onSizeChanged { size ->
                            imageSize = Offset(size.width.toFloat(), size.height.toFloat())
                            if (originalImageSize.x == 0f) originalImageSize = imageSize
                            Log.d("CropDebug", "BoxWithConstraints measured: $imageSize")
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    dragOffset = Offset.Zero
                                    currentDragRect = null
                                    Log.d("CropDebug", "Drag START at $offset")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                                    val start = dragStart
                                    if (start != null && imageSize.x > 0 && imageSize.y > 0 && originalImageSize.x > 0 && originalImageSize.y > 0) {
                                        val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                                        val end = Offset(start.x + dragOffset.x, start.y + dragOffset.y)
                                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        currentDragRect = Rect(left, top, right, bottom)
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    if (start != null && imageSize.x > 0 && imageSize.y > 0 && originalImageSize.x > 0 && originalImageSize.y > 0) {
                                        val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                                        val end = Offset(start.x + dragOffset.x, start.y + dragOffset.y)
                                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val newRect = Rect(left, top, right, bottom)
                                        Log.d("CropDebug", "Drag END — normalized Rect=$newRect")
                                        if (isEditingOcrArea) {
                                            odometerCropRect = newRect
                                        } else if (isEditingLandmark) {
                                            landmarkCropRect = newRect
                                        }
                                    }
                                    currentDragRect = null
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
                    val fitRect = if (originalImageSize.x > 0f && originalImageSize.y > 0f) {
                        calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                    } else Rect(0f, 0f, imageSize.x, imageSize.y)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        currentDragRect?.let { rect ->
                            val left = fitRect.left + rect.left * fitRect.width
                            val top = fitRect.top + rect.top * fitRect.height
                            val width = rect.width * fitRect.width
                            val height = rect.height * fitRect.height
                            drawRect(Color.Red, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                        }
                        odometerCropRect?.let { rect ->
                            val left = fitRect.left + rect.left * fitRect.width
                            val top = fitRect.top + rect.top * fitRect.height
                            val width = rect.width * fitRect.width
                            val height = rect.height * fitRect.height
                            drawRect(Color.Blue, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                        }
                        landmarkCropRect?.let { rect ->
                            val left = fitRect.left + rect.left * fitRect.width
                            val top = fitRect.top + rect.top * fitRect.height
                            val width = rect.width * fitRect.width
                            val height = rect.height * fitRect.height
                            drawRect(Color.Green, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { isEditingOcrArea = !isEditingOcrArea; isEditingLandmark = false }, modifier = Modifier.weight(1f)) {
                    Text(if (isEditingOcrArea) "Done Editing Odometer" else "Edit Odometer Crop")
                }
                Button(onClick = { isEditingLandmark = !isEditingLandmark; isEditingOcrArea = false }, modifier = Modifier.weight(1f)) {
                    Text(if (isEditingLandmark) "Done Editing Landmark" else "Edit Landmark Crop")
                }
            }
            if (odometerCropRect != null) {
                Button(onClick = { odometerCropRect = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Clear Odometer Crop Box")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = tryOcr, modifier = Modifier.fillMaxWidth()) {
                Text("Try OCR Now")
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = odometerReading, onValueChange = { odometerReading = it }, label = { Text("Initial Odometer") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        scope.launch {
                            try {
                                Log.i("VehicleSave", "Save button pressed — calling ViewModel (isNewVehicle=$isNewVehicle)")
                                if (isNewVehicle) {
                                    vehicleViewModel.createNewVehicleWithReference(
                                        name = name,
                                        make = make,
                                        model = model,
                                        year = year.toIntOrNull(),
                                        licensePlate = licensePlate,
                                        referenceDashPhotoUrl = pickedPhotoUrl,
                                        odometerCropRect = odometerCropRect,
                                        initialOdometer = odometerReading.toIntOrNull() ?: 0
                                    )
                                    Toast.makeText(context, "New vehicle created with crop box", Toast.LENGTH_LONG).show()
                                } else {
                                    editingVehicle?.let { vehicle ->
                                        vehicleViewModel.updateVehicle(
                                            vehicle.copy(
                                                name = name,
                                                make = make,
                                                model = model,
                                                year = year.toIntOrNull(),
                                                licensePlate = licensePlate,
                                                referenceDashPhotoUrl = pickedPhotoUrl,
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
                                        Toast.makeText(context, "Vehicle updated with crop box", Toast.LENGTH_LONG).show()
                                    }
                                }
                                Log.i("VehicleSave", "Save completed successfully")
                            } catch (e: Exception) {
                                Log.e("VehicleSave", "Save failed", e)
                                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSaving) "Saving..." else if (isNewVehicle) "Create Vehicle" else "Save Changes")
                }
                Button(
                    onClick = {
                        editingVehicle?.let {
                            vehicleViewModel.deleteVehicle(it)
                            Toast.makeText(context, "Vehicle deleted", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete this vehicle")
                }
            }
        }
    }
    if (showEnlargedCrop && lastOcrDebugResult != null) {
        OcrDebugDialog(
            ocrResult = lastOcrDebugResult!!,
            originalPhotoPath = referencePhotoUrl,
            onDismiss = {
                lastOcrDebugResult?.croppedBitmap?.recycle()
                lastOcrDebugResult = null
                showEnlargedCrop = false
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
                        Button(onClick = { odometerReading = candidate; showOdometerConfirmation = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(candidate)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showOdometerConfirmation = false }) { Text("Cancel") } }
        )
    }
}
