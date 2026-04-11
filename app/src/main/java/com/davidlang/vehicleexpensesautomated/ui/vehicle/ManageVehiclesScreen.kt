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
import com.davidlang.vehicleexpensesautomated.ui.components.LandmarkDebugDialog
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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
    var landmarkTextBlocksJson by remember { mutableStateOf<String?>(null) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var otherTextCropRect by remember { mutableStateOf<Rect?>(null) }
    var isEditingOcrArea by remember { mutableStateOf(false) }
    var isEditingOtherText by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var originalImageSize by remember { mutableStateOf(Offset.Zero) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }
    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }
    var showLandmarkCheck by remember { mutableStateOf(false) }
    var discoveredLandmarks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }

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
                referencePhotoUrl = it.referenceDashPhotoUrl
                landmarkTextBlocksJson = it.landmarkTextBlocksJson
                
                referencePhotoUrl?.let { path ->
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            val ei = android.media.ExifInterface(path)
                            val orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                            val isSwapped = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 || orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270
                            originalImageSize = if (isSwapped) Offset(options.outHeight.toFloat(), options.outWidth.toFloat()) else Offset(options.outWidth.toFloat(), options.outHeight.toFloat())
                        }
                    } catch (e: Exception) { Log.e("ManageVehicles", "Failed dimensions", e) }
                }

                odometerCropRect = it.odometerCropLeft?.let { left -> Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f) }
                otherTextCropRect = it.otherTextCropLeft?.let { left -> Rect(left, it.otherTextCropTop ?: 0f, it.otherTextCropRight ?: 1f, it.otherTextCropBottom ?: 1f) }
            }
        }
    }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            try {
                val bmp = BitmapFactory.decodeFile(url) ?: return@let
                referencePhotoUrl = url
                val ei = android.media.ExifInterface(url)
                val orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                val isSwapped = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 || orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270
                originalImageSize = if (isSwapped) Offset(bmp.height.toFloat(), bmp.width.toFloat()) else Offset(bmp.width.toFloat(), bmp.height.toFloat())
                bmp.recycle()
            } catch (e: Exception) { Log.e("ManageVehicles", "Image loading failed", e) }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                try {
                    var finalPath = photoPathOrUri
                    if (photoPathOrUri.startsWith("content://")) {
                        val tempFile = File.createTempFile("ocr_vehicle", ".jpg", context.cacheDir)
                        context.contentResolver.openInputStream(Uri.parse(photoPathOrUri))?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                        finalPath = tempFile.absolutePath
                    }
                    val cropRect = odometerCropRect?.let { r -> android.graphics.RectF(r.left, r.top, r.right, r.bottom) }
                    val otherCrop = otherTextCropRect?.let { r -> android.graphics.RectF(r.left, r.top, r.right, r.bottom) }
                    
                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, cropRect)
                    lastOcrDebugResult = result
                    
                    discoveredLandmarks = OdometerOcrUtils.discoverLandmarks(finalPath, cropRect, otherCrop)
                    landmarkTextBlocksJson = serializeLandmarks(discoveredLandmarks)
                    
                    showLandmarkCheck = true
                    
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
        } ?: run { Toast.makeText(context, "No photo selected", Toast.LENGTH_SHORT).show() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
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
                    DropdownMenuItem(text = { Text(vehicle.name) }, onClick = { selectedVehicleId = vehicle.id; isNewVehicle = false; dropdownExpanded = false })
                }
                DropdownMenuItem(
                    text = { Text("Add New Vehicle") },
                    onClick = {
                        selectedVehicleId = null; editingVehicle = null; isNewVehicle = true; name = ""; make = ""; model = ""; year = ""; licensePlate = ""; odometerReading = ""; pickedPhotoUrl = null; referencePhotoUrl = null; landmarkTextBlocksJson = null; odometerCropRect = null; otherTextCropRect = null; dropdownExpanded = false
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (isNewVehicle || editingVehicle != null) {
            PhotoPicker(photoStorageManager = settingsViewModel.photoStorageManager, photoType = PhotoType.FUEL, currentPhotoUrl = pickedPhotoUrl, onPhotoUrlChanged = { pickedPhotoUrl = it })
            Spacer(modifier = Modifier.height(16.dp))
            if (referencePhotoUrl != null) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(300.dp).onSizeChanged { size -> imageSize = Offset(size.width.toFloat(), size.height.toFloat()) }
                        .pointerInput(isEditingOcrArea, isEditingOtherText, imageSize, originalImageSize) {
                            if (!isEditingOcrArea && !isEditingOtherText) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset -> dragStart = offset; currentDragRect = null },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val start = dragStart
                                    val end = change.position
                                    if (start != null && imageSize.x > 0 && imageSize.y > 0 && originalImageSize.x > 0 && originalImageSize.y > 0) {
                                        val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                        currentDragRect = Rect(left, top, right, bottom)
                                    }
                                },
                                onDragEnd = {
                                    val finalDrag = currentDragRect
                                    if (finalDrag != null) {
                                        if (isEditingOcrArea) odometerCropRect = finalDrag else if (isEditingOtherText) otherTextCropRect = finalDrag
                                    }
                                    currentDragRect = null; dragStart = null
                                }
                            )
                        }
                ) {
                    Image(painter = rememberAsyncImagePainter(referencePhotoUrl), contentDescription = "Cleaned reference dash photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    val pxW = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                    val pxH = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                    val fitRect = if (originalImageSize.x > 0f && originalImageSize.y > 0f) calculateFitImageRect(pxW, pxH, originalImageSize.x, originalImageSize.y) else Rect(0f, 0f, pxW, pxH)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        currentDragRect?.let { rect ->
                            drawRect(Color.Red, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f))
                        }
                        odometerCropRect?.let { rect ->
                            drawRect(Color.Blue, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f))
                        }
                        otherTextCropRect?.let { rect ->
                            drawRect(Color.Green, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { isEditingOcrArea = !isEditingOcrArea; isEditingOtherText = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOcrArea) "Done Odometer" else "Edit Odometer") }
                Button(onClick = { isEditingOtherText = !isEditingOtherText; isEditingOcrArea = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOtherText) "Done Other" else "Edit Other Text") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = tryOcr, modifier = Modifier.fillMaxWidth()) { Text("Check Reference OCR & Landmarks") }
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
                                        name = name, make = make, model = model, year = year.toIntOrNull(), licensePlate = licensePlate, referenceDashPhotoUrl = pickedPhotoUrl, cleanedReferenceDashPhotoUrl = null,
                                        odometerCropRect = odometerCropRect, initialOdometer = odometerReading.toIntOrNull() ?: 0, landmarkTextBlocksJson = landmarkTextBlocksJson
                                    )
                                } else {
                                    editingVehicle?.let { vehicle ->
                                        vehicleViewModel.updateVehicle(vehicle.copy(
                                            name = name, make = make, model = model, year = year.toIntOrNull(), licensePlate = licensePlate, referenceDashPhotoUrl = pickedPhotoUrl, cleanedReferenceDashPhotoUrl = null,
                                            odometerCropLeft = odometerCropRect?.left, odometerCropTop = odometerCropRect?.top, odometerCropRight = odometerCropRect?.right, odometerCropBottom = odometerCropRect?.bottom,
                                            otherTextCropLeft = otherTextCropRect?.left, otherTextCropTop = otherTextCropRect?.top, otherTextCropRight = otherTextCropRect?.right, otherTextCropBottom = otherTextCropRect?.bottom,
                                            landmarkTextBlocksJson = landmarkTextBlocksJson
                                        ))
                                    }
                                }
                                navController.popBackStack()
                            } catch (e: Exception) { Log.e("VehicleSave", "Save failed", e); Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
                            finally { isSaving = false }
                        }
                    },
                    enabled = !isSaving, modifier = Modifier.weight(1f)
                ) { Text(if (isSaving) "Saving..." else if (isNewVehicle) "Create Vehicle" else "Save Changes") }
                Button(onClick = { editingVehicle?.let { vehicleViewModel.deleteVehicle(it); navController.popBackStack() } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") }
            }
        }
    }
    if (showLandmarkCheck) {
        LandmarkDebugDialog(photoPath = referencePhotoUrl, odometerCrop = odometerCropRect, otherTextCrop = otherTextCropRect, landmarks = discoveredLandmarks, odometerText = lastOcrDebugResult?.odometer ?: "FAILED", onDismiss = { showLandmarkCheck = false })
    }
}

private fun serializeLandmarks(landmarks: List<TextBlock>): String {
    val array = JSONArray()
    landmarks.forEach { lm ->
        val obj = JSONObject()
        obj.put("text", lm.text)
        obj.put("left", lm.boundingBox.left)
        obj.put("top", lm.boundingBox.top)
        obj.put("right", lm.boundingBox.right)
        obj.put("bottom", lm.boundingBox.bottom)
        array.put(obj)
    }
    return array.toString()
}

private fun calculateFitImageRect(containerWidth: Float, containerHeight: Float, imageWidth: Float, imageHeight: Float): Rect {
    if (imageWidth <= 0f || imageHeight <= 0f) return Rect(0f, 0f, containerWidth, containerHeight)
    val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    val dw = imageWidth * scale; val dh = imageHeight * scale
    return Rect((containerWidth - dw) / 2f, (containerHeight - dh) / 2f, (containerWidth + dw) / 2f, (containerHeight + dh) / 2f)
}
