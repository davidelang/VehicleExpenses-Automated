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
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedVehicleId by remember { mutableStateOf<Int?>(null) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var isNewVehicle by remember { mutableStateOf(false) }

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
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var originalImageSize by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }

    var showOdometerConfirmation by remember { mutableStateOf(false) }
    var showLandmarkCheck by remember { mutableStateOf(false) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }
    var discoveredLandmarks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }

    LaunchedEffect(selectedVehicleId) {
        editingVehicle = vehicles.find { it.id == selectedVehicleId }
        editingVehicle?.let {
            name = it.name; make = it.make ?: ""; model = it.model ?: ""; year = it.year?.toString() ?: ""; licensePlate = it.licensePlate ?: ""; odometerReading = ""
            pickedPhotoUrl = it.referenceDashPhotoUrl
            referencePhotoUrl = it.referenceDashPhotoUrl
            landmarkTextBlocksJson = it.landmarkTextBlocksJson
            
            referencePhotoUrl?.let { path ->
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        originalImageSize = Offset(options.outWidth.toFloat(), options.outHeight.toFloat())
                    }
                } catch (e: Exception) { Log.e("ManageVehicles", "Failed dimensions", e) }
            }

            odometerCropRect = it.odometerCropLeft?.let { left -> Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f) }
            otherTextCropRect = it.otherTextCropLeft?.let { left -> Rect(left, it.otherTextCropTop ?: 0f, it.otherTextCropRight ?: 1f, it.otherTextCropBottom ?: 1f) }
        }
    }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            try {
                if (url.startsWith("http")) return@let 
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (url.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(url))?.use { input -> BitmapFactory.decodeStream(input, null, options) }
                } else {
                    BitmapFactory.decodeFile(url, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    originalImageSize = Offset(options.outWidth.toFloat(), options.outHeight.toFloat())
                }
            } catch (e: Exception) { Log.e("ManageVehicles", "Dimension load failed", e) }
        }
    }

    fun processImportedPhoto(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val rawBmp = OdometerOcrUtils.decodeBitmapSafely(context, url) ?: return@launch
                val rotatedBmp = OdometerOcrUtils.rotateImageIfRequired(rawBmp, url)
                
                // 1. Detect tilt and level the image
                val tilt = OdometerOcrUtils.calculateAverageTextAngle(rotatedBmp)
                val leveledBmp = if (Math.abs(tilt) > 0.2f) {
                    Log.i("ManageVehicles", "Auto-leveling photo by ${-tilt} degrees")
                    OdometerOcrUtils.rotateBitmap(rotatedBmp, -tilt)
                } else rotatedBmp
                
                // 2. Save the leveled image as the new internal reference
                val leveledFile = File(context.filesDir, "vehicle_ref_${System.currentTimeMillis()}.jpg")
                leveledFile.outputStream().use { leveledBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                
                // 3. AUTOMATIC LANDMARK DISCOVERY (Mandatory on Import)
                val landmarks = OdometerOcrUtils.discoverLandmarksFromBitmap(leveledBmp, null, null)
                val landmarkJson = serializeLandmarks(landmarks)
                
                // Cleanup: Delete the previous temporary file if it was a vehicle_ref
                pickedPhotoUrl?.let { oldPath ->
                    if (oldPath.contains("vehicle_ref_")) {
                        val oldFile = File(oldPath)
                        if (oldFile.exists()) oldFile.delete()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    pickedPhotoUrl = leveledFile.absolutePath
                    referencePhotoUrl = leveledFile.absolutePath
                    landmarkTextBlocksJson = landmarkJson
                    originalImageSize = Offset(leveledBmp.width.toFloat(), leveledBmp.height.toFloat())
                }
                
                if (leveledBmp != rotatedBmp) leveledBmp.recycle()
                if (rotatedBmp != rawBmp) rotatedBmp.recycle()
                rawBmp.recycle()
            } catch (e: Exception) { Log.e("ManageVehicles", "Photo processing failed", e) }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                try {
                    var finalPath: String = photoPathOrUri
                    if (photoPathOrUri.startsWith("content://")) {
                        val tempFile = File.createTempFile("ocr_vehicle", ".jpg", context.cacheDir)
                        context.contentResolver.openInputStream(Uri.parse(photoPathOrUri))?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                        finalPath = tempFile.absolutePath
                    }
                    val cropRect = odometerCropRect?.let { r -> android.graphics.RectF(r.left, r.top, r.right, r.bottom) }
                    val otherCrop = otherTextCropRect?.let { r -> android.graphics.RectF(r.left, r.top, r.right, r.bottom) }
                    
                    val result = OdometerOcrUtils.extractFromPhoto(finalPath, cropRect)
                    lastOcrDebugResult = result
                    
                    // Re-run discovery if crops changed (more specific landmark filtering)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Manage Vehicles", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
            OutlinedTextField(
                value = if (isNewVehicle) "New Vehicle" else (editingVehicle?.name ?: "Select vehicle"),
                onValueChange = {}, label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                readOnly = true
            )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                vehicles.forEach { vehicle -> DropdownMenuItem(text = { Text(vehicle.name) }, onClick = { selectedVehicleId = vehicle.id; isNewVehicle = false; dropdownExpanded = false }) }
                DropdownMenuItem(text = { Text("Add New Vehicle") }, onClick = { selectedVehicleId = null; editingVehicle = null; isNewVehicle = true; name = ""; make = ""; model = ""; year = ""; licensePlate = ""; odometerReading = ""; pickedPhotoUrl = null; referencePhotoUrl = null; landmarkTextBlocksJson = null; odometerCropRect = null; otherTextCropRect = null; dropdownExpanded = false })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (isNewVehicle || editingVehicle != null) {
            PhotoPicker(photoStorageManager = settingsViewModel.photoStorageManager, photoType = PhotoType.FUEL, currentPhotoUrl = pickedPhotoUrl, onPhotoUrlChanged = { url -> if (url != null) processImportedPhoto(url) })
            Spacer(modifier = Modifier.height(16.dp))
            if (referencePhotoUrl != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(300.dp).onSizeChanged { size -> imageSize = Offset(size.width.toFloat(), size.height.toFloat()) }.pointerInput(isEditingOcrArea, isEditingOtherText, imageSize, originalImageSize) {
                    if (!isEditingOcrArea && !isEditingOtherText) return@pointerInput
                    detectDragGestures(onDragStart = { offset -> dragStart = offset; currentDragRect = null }, onDrag = { change, _ ->
                        change.consume(); val start = dragStart; val end = change.position
                        if (start != null && imageSize.x > 0 && imageSize.y > 0 && originalImageSize.x > 0 && originalImageSize.y > 0) {
                            val fitRect = calculateFitImageRect(imageSize.x, imageSize.y, originalImageSize.x, originalImageSize.y)
                            val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                            val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                            val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                            val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                            currentDragRect = Rect(left, top, right, bottom)
                        }
                    }, onDragEnd = { val finalDrag = currentDragRect; if (finalDrag != null) { if (isEditingOcrArea) odometerCropRect = finalDrag else if (isEditingOtherText) otherTextCropRect = finalDrag }; currentDragRect = null; dragStart = null })
                }) {
                    Image(painter = rememberAsyncImagePainter(referencePhotoUrl), contentDescription = "Cleaned reference dash photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    val pxW = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                    val pxH = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                    val fitRect = if (originalImageSize.x > 0f && originalImageSize.y > 0f) calculateFitImageRect(pxW, pxH, originalImageSize.x, originalImageSize.y) else Rect(0f, 0f, pxW, pxH)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        currentDragRect?.let { rect -> drawRect(Color.Red, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f)) }
                        odometerCropRect?.let { rect -> drawRect(Color.Blue, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f)) }
                        otherTextCropRect?.let { rect -> drawRect(Color.Green, Offset(fitRect.left + rect.left * fitRect.width, fitRect.top + rect.top * fitRect.height), androidx.compose.ui.geometry.Size(rect.width * fitRect.width, rect.height * fitRect.height), style = Stroke(4f)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { isEditingOcrArea = !isEditingOcrArea; isEditingOtherText = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOcrArea) "Done Odometer" else "Edit Odometer") }
                Button(onClick = { isEditingOtherText = !isEditingOtherText; isEditingOcrArea = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOtherText) "Done Other" else "Edit Other Text") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = tryOcr, modifier = Modifier.fillMaxWidth()) { Text("Check Reference OCR & Landmarks") }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Vehicle Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = odometerReading, onValueChange = { odometerReading = it }, label = { Text("Current Odometer") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { scope.launch { if (isNewVehicle) { vehicleViewModel.createNewVehicleWithReference(name, make, model, year.toIntOrNull() ?: 0, licensePlate, referencePhotoUrl, referencePhotoUrl, odometerCropRect, otherTextCropRect, odometerReading.toIntOrNull() ?: 0, landmarkTextBlocksJson) } else { editingVehicle?.let { val updated = it.copy(name = name, make = make, model = model, year = year.toIntOrNull() ?: 0, licensePlate = licensePlate, referenceDashPhotoUrl = referencePhotoUrl, cleanedReferenceDashPhotoUrl = referencePhotoUrl, odometerCropLeft = odometerCropRect?.left, odometerCropTop = odometerCropRect?.top, odometerCropRight = odometerCropRect?.right, odometerCropBottom = odometerCropRect?.bottom, otherTextCropLeft = otherTextCropRect?.left, otherTextCropTop = otherTextCropRect?.top, otherTextCropRight = otherTextCropRect?.right, otherTextCropBottom = otherTextCropRect?.bottom, landmarkTextBlocksJson = landmarkTextBlocksJson); vehicleViewModel.updateVehicle(updated) } }; navController.popBackStack() } }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && referencePhotoUrl != null) { Text(if (isNewVehicle) "Create Vehicle" else "Save Changes") }
            if (!isNewVehicle) { 
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { 
                        editingVehicle?.let { v ->
                            // Delete actual image file
                            v.referenceDashPhotoUrl?.let { path ->
                                val f = File(path)
                                if (f.exists()) f.delete()
                            }
                            vehicleViewModel.deleteVehicle(v) 
                        }
                        navController.popBackStack() 
                    }, 
                    modifier = Modifier.fillMaxWidth(), 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Vehicle") } 
            }
        }
    }

    if (showOdometerConfirmation && lastOcrDebugResult != null) {
        AlertDialog(onDismissRequest = { showOdometerConfirmation = false }, title = { Text("Select Odometer") }, text = { Column { lastOcrDebugResult?.possibleOdometers?.forEach { odo -> Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = odometerReading == odo, onClick = { odometerReading = odo; showOdometerConfirmation = false }); Text(odo, modifier = Modifier.padding(start = 8.dp)) } } } }, confirmButton = { TextButton(onClick = { showOdometerConfirmation = false }) { Text("Close") } })
    }

    if (showLandmarkCheck && referencePhotoUrl != null && lastOcrDebugResult != null) {
        LandmarkDebugDialog(photoPath = referencePhotoUrl, odometerCrop = odometerCropRect, otherTextCrop = otherTextCropRect, landmarks = discoveredLandmarks, odometerText = lastOcrDebugResult?.odometer ?: "None", onDismiss = { showLandmarkCheck = false })
    }
}

private fun calculateFitImageRect(viewW: Float, viewH: Float, imgW: Float, imgH: Float): Rect {
    val aspect = imgW / imgH
    val viewAspect = viewW / viewH
    return if (aspect > viewAspect) { val fitH = viewW / aspect; Rect(0f, (viewH - fitH) / 2f, viewW, (viewH + fitH) / 2f) } else { val fitW = viewH * aspect; Rect((viewW - fitW) / 2f, 0f, (viewW + fitW) / 2f, viewH) }
}

private fun serializeLandmarks(landmarks: List<TextBlock>): String {
    val array = JSONArray()
    landmarks.forEach { block ->
        val obj = JSONObject()
        obj.put("text", block.text)
        val box = block.boundingBox
        obj.put("cx", box.centerX())
        obj.put("cy", box.centerY())
        obj.put("h", box.height())
        obj.put("w", box.width())
        array.put(obj)
    }
    return array.toString()
}
