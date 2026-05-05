package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVehiclesScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()
    
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    // Phase 55: Anchor source engine is now implicitly ML Kit

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

    var showLandmarkCheck by remember { mutableStateOf(false) }
    var discoveryResults by remember { mutableStateOf<OcrResult?>(null) }
    var isLoadingDiscovery by remember { mutableStateOf(false) }

    // AUTO-SELECT FIRST VEHICLE
    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty() && !isNewVehicle) {
            selectedVehicleId = vehicles.first().id
        }
    }

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
                    originalImageSize = Offset(options.outWidth.toFloat(), options.outHeight.toFloat())
                } catch (e: Exception) { Log.e("ManageVehicles", "Failed dimensions", e) }
            }
            odometerCropRect = it.odometerCropLeft?.let { left -> Rect(left, it.odometerCropTop ?: 0f, it.odometerCropRight ?: 1f, it.odometerCropBottom ?: 1f) }
            otherTextCropRect = it.otherTextCropLeft?.let { left -> Rect(left, it.otherTextCropTop ?: 0f, it.otherTextCropRight ?: 1f, it.otherTextCropBottom ?: 1f) }
            
            // Hydration handled by the "Show Landmarks" button
            discoveryResults = null 
        }
    }

    fun processImportedPhoto(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoadingDiscovery = true }
                val rawBmp = OdometerOcrUtils.decodeBitmapSafely(context, url) ?: return@launch
                val rotatedBmp = OdometerOcrUtils.rotateImageIfRequired(rawBmp, url)
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(rotatedBmp, NativePaddleEngine.sharedBmp2048)
                val leveledBmp = if (Math.abs(deskewRes.angle) > 0.2f) {
                    OdometerOcrUtils.rotateBitmap(rotatedBmp, -deskewRes.angle)
                } else rotatedBmp
                
                val leveledFile = File(context.filesDir, "vehicle_ref_${System.currentTimeMillis()}.jpg")
                leveledFile.outputStream().use { leveledBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                
                // Phase 55: Single ML Kit Discovery pass
                val rawResult = OcrHarness.runDiscovery(leveledBmp, context)
                
                // EXCLUDE CROP AREAS
                val odoRectF = odometerCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                val otherRectF = otherTextCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                val filteredResult = rawResult.filterByCrops(odoRectF, otherRectF)

                // Store manifest immediately
                val landmarkJson = OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to filteredResult))
                
                withContext(Dispatchers.Main) {
                    pickedPhotoUrl = leveledFile.absolutePath
                    referencePhotoUrl = leveledFile.absolutePath
                    landmarkTextBlocksJson = landmarkJson
                    discoveryResults = filteredResult
                    originalImageSize = Offset(leveledBmp.width.toFloat(), leveledBmp.height.toFloat())
                    isLoadingDiscovery = false
                }
                if (leveledBmp != rotatedBmp) leveledBmp.recycle()
                if (rotatedBmp != rawBmp) rotatedBmp.recycle()
                rawBmp.recycle()
            } catch (e: Exception) { 
                Log.e("ManageVehicles", "Photo processing failed", e) 
                withContext(Dispatchers.Main) { isLoadingDiscovery = false }
            }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                try {
                    isLoadingDiscovery = true
                    val rawBmp = withContext(Dispatchers.IO) { 
                        OdometerOcrUtils.decodeBitmapSafely(context, photoPathOrUri) 
                    } ?: return@launch
                    
                    // Create a mutable copy and process in-place
                    val leveledBmp = rawBmp.copy(Bitmap.Config.ARGB_8888, true)
                    OdometerOcrUtils.applyGrayscaleInPlace(leveledBmp)
                    rawBmp.recycle()
                    
                    val rawResult = withContext(Dispatchers.Default) {
                        OcrHarness.runDiscovery(leveledBmp, context)
                    }
                    
                    val odoRectF = odometerCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                    val otherRectF = otherTextCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
                    val filteredResult = rawResult.filterByCrops(odoRectF, otherRectF)

                    landmarkTextBlocksJson = OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to filteredResult))
                    
                    discoveryResults = filteredResult
                    showLandmarkCheck = true
                    leveledBmp.recycle()
                    isLoadingDiscovery = false
                } catch (e: Exception) { 
                    Log.e("ManageVehicles", "OCR failed", e) 
                    isLoadingDiscovery = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
            OutlinedTextField(
                value = if (isNewVehicle) "Add New Vehicle" else (editingVehicle?.name ?: "Select vehicle"),
                onValueChange = {}, label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true
            )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                vehicles.forEach { vehicle -> DropdownMenuItem(text = { Text(vehicle.name) }, onClick = { selectedVehicleId = vehicle.id; isNewVehicle = false; dropdownExpanded = false }) }
                DropdownMenuItem(text = { Text("Add New Vehicle") }, onClick = { selectedVehicleId = null; editingVehicle = null; isNewVehicle = true; name = ""; make = ""; model = ""; year = ""; licensePlate = ""; odometerReading = ""; pickedPhotoUrl = null; referencePhotoUrl = null; landmarkTextBlocksJson = null; odometerCropRect = null; otherTextCropRect = null; dropdownExpanded = false })
            }
        }
        
        if (isNewVehicle || editingVehicle != null) {
            PhotoPicker(photoStorageManager = hiltViewModel<SettingsViewModel>().photoStorageManager, photoType = PhotoType.FUEL, currentPhotoUrl = pickedPhotoUrl, onPhotoUrlChanged = { url -> if (url != null) processImportedPhoto(url) })
            
            if (referencePhotoUrl != null) {
                EditCropsView(referencePhotoUrl!!, odometerCropRect, otherTextCropRect, originalImageSize, isEditingOcrArea, isEditingOtherText, 
                    onSizeChanged = { imageSize = it },
                    onCropChanged = { odo, other -> odometerCropRect = odo; otherTextCropRect = other })
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = { isEditingOcrArea = !isEditingOcrArea; isEditingOtherText = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOcrArea) "Done Odo" else "Mark Odo") }
                    Button(onClick = { isEditingOtherText = !isEditingOtherText; isEditingOcrArea = false }, modifier = Modifier.weight(1f)) { Text(if (isEditingOtherText) "Done Ignore" else "Mark to Ignore") }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = tryOcr, 
                        modifier = Modifier.weight(1f), 
                        enabled = !isLoadingDiscovery
                    ) { 
                        if (isLoadingDiscovery) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Run Discovery") 
                        }
                    }
                    
                    Button(
                        onClick = {
                            if (!landmarkTextBlocksJson.isNullOrEmpty()) {
                                val map = OdometerOcrUtils.deserializeMultiEngineLandmarks(
                                    landmarkTextBlocksJson!!, 
                                    originalImageSize.x.toInt(), 
                                    originalImageSize.y.toInt()
                                )
                                // Fallback to whatever engine was cached, or default
                                discoveryResults = map["ML Kit"] ?: map.values.firstOrNull()
                                showLandmarkCheck = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !landmarkTextBlocksJson.isNullOrEmpty() && !isLoadingDiscovery
                    ) {
                        Text("Show Landmarks")
                    }
                }
                
                if (discoveryResults != null) {
                    Text("Discovery Results (Excluding Crop Areas):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Text("Tap card to see full debug pass", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            val result = discoveryResults!!
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showLandmarkCheck = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(result.engineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Landmarks found: ${result.textBlocks.size}", style = MaterialTheme.typography.labelMedium)
                                    Text(result.debugText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Vehicle Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { scope.launch { 
                
                if (isNewVehicle) { 
                    vehicleViewModel.createNewVehicleWithReference(name, make, model, year.toIntOrNull() ?: 0, licensePlate, referencePhotoUrl, referencePhotoUrl, odometerCropRect, otherTextCropRect, odometerReading.toIntOrNull() ?: 0, landmarkTextBlocksJson) 
                } else { 
                    editingVehicle?.let { val updated = it.copy(name = name, make = make, model = model, year = year.toIntOrNull() ?: 0, licensePlate = licensePlate, referenceDashPhotoUrl = referencePhotoUrl, cleanedReferenceDashPhotoUrl = referencePhotoUrl, odometerCropLeft = odometerCropRect?.left, odometerCropTop = odometerCropRect?.top, odometerCropRight = odometerCropRect?.right, odometerCropBottom = odometerCropRect?.bottom, otherTextCropLeft = otherTextCropRect?.left, otherTextCropTop = otherTextCropRect?.top, otherTextCropRight = otherTextCropRect?.right, otherTextCropBottom = otherTextCropRect?.bottom, landmarkTextBlocksJson = landmarkTextBlocksJson); vehicleViewModel.updateVehicle(updated) } 
                } 
                navController.popBackStack() 
            } }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && referencePhotoUrl != null) { Text(if (isNewVehicle) "Create Vehicle" else "Save Changes") }
        }
    }

    if (showLandmarkCheck && referencePhotoUrl != null && discoveryResults != null) {
        val res = discoveryResults!!
        LandmarkDebugDialog(
            photoPath = referencePhotoUrl,
            odometerCrop = odometerCropRect,
            otherTextCrop = otherTextCropRect,
            landmarks = res.textBlocks,
            rawDiscoveryBoxes = res.rawDiscoveryBoxes,
            odometerText = "N/A",
            engineName = res.engineName,
            sourceWidth = res.imageWidth,
            sourceHeight = res.imageHeight,
            discoveryTimeMs = res.discoveryTimeMs,
            totalTimeMs = res.executionTimeMs,
            onDismiss = { showLandmarkCheck = false },
            onLandmarksChanged = { updatedList ->
                scope.launch(Dispatchers.Default) {
                    val updatedRes = res.copy(
                        textBlocks = updatedList,
                        debugText = updatedList.joinToString(" ") { it.text }
                    )
                    withContext(Dispatchers.Main) {
                        discoveryResults = updatedRes
                        landmarkTextBlocksJson = OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to updatedRes))
                    }
                }
            }
        )
    }
}

@Composable
private fun EditCropsView(photoUrl: String, odoRect: Rect?, otherRect: Rect?, originalSize: Offset, isOdo: Boolean, isOther: Boolean, onSizeChanged: (Offset) -> Unit, onCropChanged: (Rect?, Rect?) -> Unit) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }
    var viewSize by remember { mutableStateOf(Offset.Zero) }
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = Modifier
        .fillMaxWidth()
        .height(260.dp)
        .onSizeChanged { size -> viewSize = Offset(size.width.toFloat(), size.height.toFloat()); onSizeChanged(viewSize) }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val newOffset = offset + pan
                // Basic bounds checking for pan could be added here if desired
                offset = newOffset
            }
        }
        .pointerInput(isOdo, isOther, viewSize, originalSize, scale, offset) {
            if (!isOdo && !isOther) return@pointerInput
            detectDragGestures(
                onDragStart = { startOffset -> 
                    // Inverse transform the start coordinate
                    dragStart = (startOffset - offset) / scale
                    currentDragRect = null 
                }, 
                onDrag = { change, _ ->
                    change.consume()
                    val start = dragStart
                    // Inverse transform the current coordinate
                    val end = (change.position - offset) / scale
                    
                    if (start != null && viewSize.x > 0 && originalSize.x > 0) {
                        val fitRect = calculateFitImageRect(viewSize.x, viewSize.y, originalSize.x, originalSize.y)
                        val left = ((minOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                        val top = ((minOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                        val right = ((maxOf(start.x, end.x) - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                        val bottom = ((maxOf(start.y, end.y) - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                        currentDragRect = Rect(left, top, right, bottom)
                    }
                }, 
                onDragEnd = { 
                    val final = currentDragRect
                    if (final != null) { 
                        if (isOdo) onCropChanged(final, otherRect) else onCropChanged(odoRect, final) 
                    }
                    currentDragRect = null
                    dragStart = null 
                }
            )
        }
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
        ) {
            Image(painter = rememberAsyncImagePainter(photoUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            val scope = this@BoxWithConstraints
            val pxW = with(androidx.compose.ui.platform.LocalDensity.current) { scope.maxWidth.toPx() }; val pxH = with(androidx.compose.ui.platform.LocalDensity.current) { scope.maxHeight.toPx() }
            val fitRect = if (originalSize.x > 0f) calculateFitImageRect(pxW, pxH, originalSize.x, originalSize.y) else Rect(0f, 0f, pxW, pxH)
            Canvas(modifier = Modifier.fillMaxSize()) {
                currentDragRect?.let { r -> drawRect(Color.Red, Offset(fitRect.left + r.left * fitRect.width, fitRect.top + r.top * fitRect.height), androidx.compose.ui.geometry.Size(r.width * fitRect.width, r.height * fitRect.height), style = Stroke(4f / scale)) }
                odoRect?.let { r -> drawRect(Color.Blue, Offset(fitRect.left + r.left * fitRect.width, fitRect.top + r.top * fitRect.height), androidx.compose.ui.geometry.Size(r.width * fitRect.width, r.height * fitRect.height), style = Stroke(4f / scale)) }
                otherRect?.let { r -> drawRect(Color.Green, Offset(fitRect.left + r.left * fitRect.width, fitRect.top + r.top * fitRect.height), androidx.compose.ui.geometry.Size(r.width * fitRect.width, r.height * fitRect.height), style = Stroke(4f / scale)) }
            }
        }
    }
}

private fun calculateFitImageRect(viewW: Float, viewH: Float, imgW: Float, imgH: Float): Rect {
    val aspect = imgW / imgH; val viewAspect = viewW / viewH
    return if (aspect > viewAspect) { val fitH = viewW / aspect; Rect(0f, (viewH - fitH) / 2f, viewW, (viewH + fitH) / 2f) } else { val fitW = viewH * aspect; Rect((viewW - fitW) / 2f, 0f, (viewW + fitW) / 2f, viewH) }
}