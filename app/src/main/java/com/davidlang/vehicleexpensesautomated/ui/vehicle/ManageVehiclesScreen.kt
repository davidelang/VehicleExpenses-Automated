package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
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
    var isSaving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }
    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }
    var referenceTextBlocks by remember { mutableStateOf<String?>(null) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var otherTextCropRect by remember { mutableStateOf<Rect?>(null) }
    var isEditingOcrArea by remember { mutableStateOf(false) }
    var isEditingOtherText by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var originalImageSize by remember { mutableStateOf(Offset.Zero) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }
    var showEnlargedCrop by remember { mutableStateOf(false) }
    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

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
                referenceTextBlocks = it.referenceTextBlocks
                odometerCropRect = it.odometerCropLeft?.let { left ->
                    Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f)
                }
                otherTextCropRect = it.otherTextCropLeft?.let { left ->
                    Rect(left, it.otherTextCropTop ?: 0f, it.otherTextCropRight ?: 1f, it.otherTextCropBottom ?: 1f)
                }
            }
        }
    }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            isCleaning = true
            try {
                val bmp = BitmapFactory.decodeFile(url) ?: return@let
                originalImageSize = Offset(bmp.width.toFloat(), bmp.height.toFloat())
                val (cleanedBmp, textBlocks) = ImageAlignmentUtils.createCleanedReference(bmp)
                if (cleanedBmp != null) {
                    val tempFile = File(context.cacheDir, "temp_cleaned_${System.currentTimeMillis()}.jpg")
                    val out = java.io.FileOutputStream(tempFile)
                    cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    referencePhotoUrl = tempFile.absolutePath
                    referenceTextBlocks = textBlocks
                    cleanedBmp.recycle()
                    Log.i("ManageVehicles", "Immediate cleaned image + text blocks ready")
                }
            } catch (e: Exception) {
                Log.e("ManageVehicles", "Immediate cleaning failed", e)
            } finally {
                isCleaning = false
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
                DropdownMenuItem(
                    text = { Text("Add New Vehicle") },
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
                        referenceTextBlocks = null
                        odometerCropRect = null
                        otherTextCropRect = null
                        dropdownExpanded = false
                    }
                )
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
            Spacer(modifier = Modifier.height(16.dp))
            if (isCleaning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                    Text(text = "Cleaning image...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                }
            } else if (referencePhotoUrl != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .onSizeChanged { size ->
                            imageSize = Offset(size.width.toFloat(), size.height.toFloat())
                        }
                        .pointerInput(isEditingOcrArea, isEditingOtherText) {
                            if (!isEditingOcrArea && !isEditingOtherText) {
                                Log.i("CropDebug", "Drag ignored - no edit mode active")
                                return@pointerInput
                            }
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    dragOffset = Offset.Zero
                                    currentDragRect = null
                                    Log.i("CropDebug", "onDragStart - offset=$offset  (editOdometer=$isEditingOcrArea, editOtherText=$isEditingOtherText)")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                                    Log.i("CropDebug", "onDrag called - dragAmount=$dragAmount, dragOffset=$dragOffset  imageSize=$imageSize originalImageSize=$originalImageSize")
                                    val start = dragStart
                                    if (start != null) {
                                        val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                                        val end = Offset(start.x + dragOffset.x, start.y + dragOffset.y)
                                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        currentDragRect = Rect(left, top, right, bottom)
                                        Log.i("CropDebug", "Dragging - currentDragRect=$currentDragRect")
                                    } else {
                                        Log.i("CropDebug", "onDrag condition failed - start=$start imageSize=$imageSize originalImageSize=$originalImageSize")
                                    }
                                },
                                onDragEnd = {
                                    Log.i("CropDebug", "onDragEnd called  (editOdometer=$isEditingOcrArea, editOtherText=$isEditingOtherText)")
                                    val start = dragStart
                                    if (start != null) {
                                        val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                                        val end = Offset(start.x + dragOffset.x, start.y + dragOffset.y)
                                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val newRect = Rect(left, top, right, bottom)
                                        Log.i("CropDebug", "Drag ended - newRect=$newRect (original size)")
                                        if (isEditingOcrArea) {
                                            odometerCropRect = newRect
                                        } else if (isEditingOtherText) {
                                            otherTextCropRect = newRect
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
                        contentDescription = "Cleaned reference dash photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    val fitRect = if (originalImageSize.x > 0f && originalImageSize.y > 0f) {
                        calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                    } else Rect(0f, 0f, imageSize.x, imageSize.y)
                    key(currentDragRect) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            currentDragRect?.let { rect ->
                                val left = fitRect.left + rect.left * fitRect.width
                                val top = fitRect.top + rect.top * fitRect.height
                                val width = rect.width * fitRect.width
                                val height = rect.height * fitRect.height
                                drawRect(Color.Red, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                                Log.i("CropDebug", "Canvas drawing RED preview - screen coords left=$left top=$top width=$width height=$height")
                            }
                            odometerCropRect?.let { rect ->
                                val left = fitRect.left + rect.left * fitRect.width
                                val top = fitRect.top + rect.top * fitRect.height
                                val width = rect.width * fitRect.width
                                val height = rect.height * fitRect.height
                                drawRect(Color.Blue, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                            }
                            otherTextCropRect?.let { rect ->
                                val left = fitRect.left + rect.left * fitRect.width
                                val top = fitRect.top + rect.top * fitRect.height
                                val width = rect.width * fitRect.width
                                val height = rect.height * fitRect.height
                                drawRect(Color.Green, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = Stroke(4f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val wasEditing = isEditingOcrArea
                    isEditingOcrArea = !isEditingOcrArea
                    isEditingOtherText = false
                    Log.i("CropDebug", "Edit Odometer Crop button clicked - wasEditing=$wasEditing -> isEditingOcrArea=$isEditingOcrArea, isEditingOtherText=$isEditingOtherText")
                }, modifier = Modifier.weight(1f)) {
                    Text(if (isEditingOcrArea) "Done Editing Odometer" else "Edit Odometer Crop")
                }
                Button(onClick = {
                    val wasEditing = isEditingOtherText
                    isEditingOtherText = !isEditingOtherText
                    isEditingOcrArea = false
                    Log.i("CropDebug", "Edit Other Text Crop button clicked - wasEditing=$wasEditing -> isEditingOcrArea=$isEditingOcrArea, isEditingOtherText=$isEditingOtherText")
                }, modifier = Modifier.weight(1f)) {
                    Text(if (isEditingOtherText) "Done Editing Other Text" else "Edit Other Text Crop")
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
                                if (isNewVehicle) {
                                    vehicleViewModel.createNewVehicleWithReference(
                                        name = name,
                                        make = make,
                                        model = model,
                                        year = year.toIntOrNull(),
                                        licensePlate = licensePlate,
                                        cleanedReferenceDashPhotoUrl = referencePhotoUrl,
                                        odometerCropRect = odometerCropRect,
                                        initialOdometer = odometerReading.toIntOrNull() ?: 0,
                                        referenceTextBlocks = referenceTextBlocks
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
                                                cleanedReferenceDashPhotoUrl = referencePhotoUrl,
                                                odometerCropLeft = odometerCropRect?.left,
                                                odometerCropTop = odometerCropRect?.top,
                                                odometerCropRight = odometerCropRect?.right,
                                                odometerCropBottom = odometerCropRect?.bottom,
                                                otherTextCropLeft = otherTextCropRect?.left,
                                                otherTextCropTop = otherTextCropRect?.top,
                                                otherTextCropRight = otherTextCropRect?.right,
                                                otherTextCropBottom = otherTextCropRect?.bottom,
                                                referenceTextBlocks = referenceTextBlocks
                                            )
                                        )
                                        Toast.makeText(context, "Vehicle updated with crop box", Toast.LENGTH_LONG).show()
                                    }
                                }
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
