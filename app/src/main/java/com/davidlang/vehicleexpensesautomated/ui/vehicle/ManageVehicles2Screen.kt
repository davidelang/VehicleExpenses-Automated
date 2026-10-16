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
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.davidlang.vehicleexpensesautomated.data.repository.forManageList
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.components.LandmarkDebugDialog
import com.davidlang.vehicleexpensesautomated.ui.components.LandmarkGlyphFont
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVehicles2Screen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val allVehicles by vehicleViewModel.vehicles.collectAsState()
    val vehicles = remember(allVehicles) {
        allVehicles.forManageList()
    }
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
    /** Face width in digits (default 6). */
    var odometerDigitCount by remember { mutableStateOf("6") }
    /** How many times the face has rolled past 10^digitCount. */
    var odometerRolloverCount by remember { mutableStateOf("0") }
    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }
    var landmarkTextBlocksJson by remember { mutableStateOf<String?>(null) }

    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var otherTextCropRect by remember { mutableStateOf<Rect?>(null) }
    var editMode by remember { mutableStateOf(CropEditMode.IDLE) }
    /** Odo / Ignore Crop on MV2: tap yellow seeds (not drag-draw). Mutually exclusive with each other. */
    var odoSeedSelectActive by remember { mutableStateOf(false) }
    var ignoreSeedSelectActive by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }
    var originalImageSize by remember { mutableStateOf(Offset.Zero) }

    var showLandmarkCheck by remember { mutableStateOf(false) }
    var discoveryResults by remember { mutableStateOf<OcrResult?>(null) }
    /** Full paddle seed list for overlay taps (unfiltered by odo/ignore crops). */
    var seedBlocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var isLoadingDiscovery by remember { mutableStateOf(false) }

    fun landmarkJsonForSave(): String? {
        val res = discoveryResults ?: return landmarkTextBlocksJson
        val odoRectF = odometerCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
        val otherRectF = otherTextCropRect?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
        val filtered = res.filterByCrops(odoRectF, otherRectF)
        return OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to filtered))
    }

    // AUTO-SELECT FIRST VEHICLE
    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty() && !isNewVehicle) {
            selectedVehicleId = vehicles.first().id
        }
    }

    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
    val vehicleHydrationKey = selectedVehicle?.let { v ->
        buildString {
            append(v.updatedAt)
            append('|')
            append(v.landmarkTextBlocksJson?.length ?: 0)
            append('|')
            append(v.landmarkTextBlocksJson?.hashCode() ?: 0)
            append('|')
            append(v.odometerCropLeft)
            append('|')
            append(v.odometerCropTop)
            append('|')
            append(v.odometerCropRight)
            append('|')
            append(v.odometerCropBottom)
            append('|')
            append(v.otherTextCropLeft)
            append('|')
            append(v.otherTextCropTop)
            append('|')
            append(v.otherTextCropRight)
            append('|')
            append(v.otherTextCropBottom)
            append('|')
            append(v.referenceDashPhotoUrl)
            append('|')
            append(v.cleanedReferenceDashPhotoUrl)
            append('|')
            append(v.cloudManifest?.length ?: 0)
        }
    } ?: ""

    fun hydrateFormFromVehicle(v: Vehicle) {
        editingVehicle = v
        name = v.name
        make = v.make ?: ""
        model = v.model ?: ""
        year = v.year?.toString() ?: ""
        licensePlate = v.licensePlate ?: ""
        odometerReading = ""
        odometerDigitCount = v.odometerDigitCount.coerceIn(3, 9).toString()
        odometerRolloverCount = v.odometerRolloverCount.coerceAtLeast(0).toString()
        pickedPhotoUrl = v.referenceDashPhotoUrl
        referencePhotoUrl = v.referenceDashPhotoUrl
        landmarkTextBlocksJson = v.landmarkTextBlocksJson

        originalImageSize = Offset.Zero
        referencePhotoUrl?.let { path ->
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                originalImageSize = Offset(options.outWidth.toFloat(), options.outHeight.toFloat())
            } catch (e: Exception) {
                Log.e("ManageVehicles2", "Failed dimensions", e)
            }
        }
        val (odo, other) = vehicleViewModel.getCrops(v)
        odometerCropRect = odo
        otherTextCropRect = other

        discoveryResults = null
        seedBlocks = emptyList()
    }

    LaunchedEffect(selectedVehicleId, vehicleHydrationKey) {
        val id = selectedVehicleId ?: return@LaunchedEffect
        var v = vehicles.find { it.id == id } ?: return@LaunchedEffect
        if (v.referenceDashPhotoUrl.isNullOrBlank() && !v.cloudManifest.isNullOrBlank()) {
            if (vehicleViewModel.ensureVehicleAssetsDownloaded(id)) {
                v = vehicleViewModel.getVehicleById(id) ?: v
            }
        }
        hydrateFormFromVehicle(v)

        val photoPath = v.referenceDashPhotoUrl
        if (photoPath.isNullOrBlank()) return@LaunchedEffect
        isLoadingDiscovery = true
        try {
            val rawResult = withContext(Dispatchers.Default) {
                val rawBmp = OdometerOcrUtils.decodeBitmapSafely(context, photoPath) ?: return@withContext null
                val uprightBmp = OdometerOcrUtils.rotateImageIfRequired(rawBmp, photoPath)
                try {
                    runMv2PaddleDiscovery(context, uprightBmp)
                } finally {
                    if (uprightBmp != rawBmp) uprightBmp.recycle()
                    rawBmp.recycle()
                }
            }
            if (rawResult != null) {
                discoveryResults = rawResult
                seedBlocks = rawResult.textBlocks
                originalImageSize = Offset(rawResult.imageWidth.toFloat(), rawResult.imageHeight.toFloat())
            }
        } catch (e: Exception) {
            Log.e("ManageVehicles2", "Hydrate paddle discovery failed", e)
        } finally {
            isLoadingDiscovery = false
        }
    }

    fun processImportedPhoto(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoadingDiscovery = true }
                val rawBmp = OdometerOcrUtils.decodeBitmapSafely(context, url) ?: return@launch
                val uprightBmp = OdometerOcrUtils.rotateImageIfRequired(rawBmp, url)

                val refFile = File(context.filesDir, "vehicle_ref_${System.currentTimeMillis()}.jpg")
                refFile.outputStream().use { uprightBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }

                val rawResult = runMv2PaddleDiscovery(context, uprightBmp)
                val landmarkJson = OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to rawResult))

                withContext(Dispatchers.Main) {
                    pickedPhotoUrl = refFile.absolutePath
                    referencePhotoUrl = refFile.absolutePath
                    landmarkTextBlocksJson = landmarkJson
                    discoveryResults = rawResult
                    seedBlocks = rawResult.textBlocks
                    originalImageSize = Offset(uprightBmp.width.toFloat(), uprightBmp.height.toFloat())
                    isLoadingDiscovery = false
                }
                if (uprightBmp != rawBmp) uprightBmp.recycle()
                rawBmp.recycle()
            } catch (e: Exception) {
                Log.e("ManageVehicles2", "Photo processing failed", e)
                withContext(Dispatchers.Main) { isLoadingDiscovery = false }
            }
        }
    }

    val tryOcr: () -> Unit = {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                try {
                    isLoadingDiscovery = true
                    val rawResult = withContext(Dispatchers.Default) {
                        val rawBmp = OdometerOcrUtils.decodeBitmapSafely(context, photoPathOrUri)
                            ?: return@withContext null
                        val uprightBmp = OdometerOcrUtils.rotateImageIfRequired(rawBmp, photoPathOrUri)
                        try {
                            runMv2PaddleDiscovery(context, uprightBmp)
                        } finally {
                            if (uprightBmp != rawBmp) uprightBmp.recycle()
                            rawBmp.recycle()
                        }
                    } ?: return@launch

                    landmarkTextBlocksJson =
                        OdometerOcrUtils.serializeMultiEngineLandmarks(mapOf("ML Kit" to rawResult))
                    discoveryResults = rawResult
                    seedBlocks = rawResult.textBlocks
                    showLandmarkCheck = true
                    isLoadingDiscovery = false
                } catch (e: Exception) {
                    Log.e("ManageVehicles2", "OCR failed", e)
                    isLoadingDiscovery = false
                }
            }
        }
    }

    fun onSeedTapped(block: TextBlock) {
        val imgW = originalImageSize.x.toInt()
        val imgH = originalImageSize.y.toInt()
        if (imgW <= 0 || imgH <= 0) return
        val icrs = mv2IcrsFromSeed(block, imgW, imgH)
        when {
            odoSeedSelectActive -> odometerCropRect = icrs
            ignoreSeedSelectActive -> otherTextCropRect = icrs
            else -> return
        }
        odoSeedSelectActive = false
        ignoreSeedSelectActive = false
        editMode = CropEditMode.EDIT_CROPS
    }

    RegisterPageHelp(
        title = "Manage Vehicles",
        "Reference photo (upright EXIF only) → Run Discovery (paddle seeds) → tap a seed for the odometer box → " +
            "Ignore Crop if needed → name → Save. Landmarks are stored under the existing JSON key. Menu → Help for walkthrough.",
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
            OutlinedTextField(
                value = if (isNewVehicle) "Add New Vehicle" else (editingVehicle?.name ?: "Select vehicle"),
                onValueChange = {}, label = { Text("Vehicle") },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
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
                var showFullPhoto by remember(referencePhotoUrl) { mutableStateOf(false) }
                TextButton(onClick = { showFullPhoto = true }) {
                    Text("View full photo (zoom +/−)")
                }
                if (showFullPhoto) {
                    com.davidlang.vehicleexpensesautomated.ui.components.ZoomablePhotoDialog(
                        uris = listOf(referencePhotoUrl!!),
                        title = "Reference dash photo",
                        onDismiss = { showFullPhoto = false },
                    )
                }
                EditCropsView(
                    referencePhotoUrl!!,
                    odometerCropRect,
                    otherTextCropRect,
                    originalImageSize,
                    editMode,
                    seedSelectActive = odoSeedSelectActive || ignoreSeedSelectActive,
                    seedBlocks = seedBlocks,
                    onSeedTapped = { block -> onSeedTapped(block) },
                    onSizeChanged = { imageSize = it },
                    onCropChanged = { odo, other -> odometerCropRect = odo; otherTextCropRect = other },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(
                        onClick = {
                            odoSeedSelectActive = !odoSeedSelectActive
                            if (odoSeedSelectActive) {
                                ignoreSeedSelectActive = false
                                editMode = CropEditMode.IDLE
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (odoSeedSelectActive) "Done Odo" else "Odo Crop")
                    }
                    Button(
                        onClick = {
                            odoSeedSelectActive = false
                            ignoreSeedSelectActive = false
                            editMode = if (editMode == CropEditMode.EDIT_CROPS) CropEditMode.IDLE else CropEditMode.EDIT_CROPS
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (editMode == CropEditMode.EDIT_CROPS) "Done Edit" else "Edit Crops")
                    }
                    Button(
                        onClick = {
                            ignoreSeedSelectActive = !ignoreSeedSelectActive
                            if (ignoreSeedSelectActive) {
                                odoSeedSelectActive = false
                                editMode = CropEditMode.IDLE
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (ignoreSeedSelectActive) "Done Ignore" else "Ignore Crop")
                    }
                }

                Button(
                    onClick = {
                        odometerCropRect = null
                        otherTextCropRect = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Clear Crops")
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
                    Text("Discovery Results (tap seed on photo for odo box):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
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
                                    Text(
                                        result.debugText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = LandmarkGlyphFont),
                                    )
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
            OutlinedTextField(
                value = odometerDigitCount,
                onValueChange = { v -> if (v.length <= 1 && v.all { it.isDigit() }) odometerDigitCount = v },
                label = { Text("Odometer digits (face width)") },
                supportingText = {
                    Text("Default 6. OCR prefers this length; near 9xxxxx allows one extra digit before rollover.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = odometerRolloverCount,
                onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) odometerRolloverCount = v },
                label = { Text("Odometer rollover count") },
                supportingText = {
                    Text("Times face rolled past max. Fills store tracking = rollover×10^digits + face.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val dig = odometerDigitCount.toIntOrNull()?.coerceIn(3, 9) ?: 6
                        val rolls = odometerRolloverCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val jsonToSave = landmarkJsonForSave()
                        if (isNewVehicle) {
                            vehicleViewModel.createNewVehicleWithReference(
                                name,
                                make,
                                model,
                                year.toIntOrNull() ?: 0,
                                licensePlate,
                                referencePhotoUrl,
                                referencePhotoUrl,
                                odometerCropRect,
                                otherTextCropRect,
                                odometerReading.toIntOrNull() ?: 0,
                                jsonToSave,
                                odometerDigitCount = dig,
                                odometerRolloverCount = rolls,
                            )
                        } else {
                            editingVehicle?.let {
                                val updated = it.copy(
                                    name = name,
                                    make = make,
                                    model = model,
                                    year = year.toIntOrNull() ?: 0,
                                    licensePlate = licensePlate,
                                    referenceDashPhotoUrl = referencePhotoUrl,
                                    cleanedReferenceDashPhotoUrl = referencePhotoUrl,
                                    odometerCropLeft = odometerCropRect?.left,
                                    odometerCropTop = odometerCropRect?.top,
                                    odometerCropRight = odometerCropRect?.right,
                                    odometerCropBottom = odometerCropRect?.bottom,
                                    otherTextCropLeft = otherTextCropRect?.left,
                                    otherTextCropTop = otherTextCropRect?.top,
                                    otherTextCropRight = otherTextCropRect?.right,
                                    otherTextCropBottom = otherTextCropRect?.bottom,
                                    landmarkTextBlocksJson = jsonToSave,
                                    odometerDigitCount = dig,
                                    odometerRolloverCount = rolls,
                                )
                                vehicleViewModel.updateVehicle(updated)
                            }
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && referencePhotoUrl != null,
            ) { Text(if (isNewVehicle) "Create Vehicle" else "Save Changes") }
        }
    }

    if (showLandmarkCheck && referencePhotoUrl != null && discoveryResults != null) {
        val res = discoveryResults!!
        LandmarkDebugDialog(
            photoPath = referencePhotoUrl,
            odometerCrop = odometerCropRect,
            otherTextCrop = otherTextCropRect,
            landmarks = res.textBlocks,
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
private fun EditCropsView(
    photoUrl: String,
    odoRect: Rect?,
    otherRect: Rect?,
    originalSize: Offset,
    editMode: CropEditMode,
    seedSelectActive: Boolean,
    seedBlocks: List<TextBlock>,
    onSeedTapped: (TextBlock) -> Unit,
    onSizeChanged: (Offset) -> Unit,
    onCropChanged: (Rect?, Rect?) -> Unit,
) {
    // Crop rects (odoRect, otherRect, currentDragRect) are **always ICRS**.
    // Screen interaction uses transient image pixel coordinates via fitRect.
    // Never create or pass a 0-1 normalized Rect for crops.
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDragRect by remember { mutableStateOf<Rect?>(null) }
    var viewSize by remember { mutableStateOf(Offset.Zero) }
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var activeCropIsOdo by remember { mutableStateOf(true) }

    BoxWithConstraints(modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
        .onSizeChanged { size -> viewSize = Offset(size.width.toFloat(), size.height.toFloat()); onSizeChanged(viewSize) }
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || pan != Offset.Zero) {
                        scale = (scale * zoom).coerceIn(1f, 10f)
                        offset += pan
                    }
                }
            }
            .pointerInput(seedSelectActive, viewSize, originalSize, scale, offset, seedBlocks) {
                if (!seedSelectActive || seedBlocks.isEmpty() || originalSize.x <= 0f) return@pointerInput
                detectTapGestures { tapOffset ->
                    val center = Offset(viewSize.x / 2f, viewSize.y / 2f)
                    val screenPos = (tapOffset - center - offset) / scale + center
                    val pxW = viewSize.x
                    val pxH = viewSize.y
                    val fitRect = calculateFitImageRect(pxW, pxH, originalSize.x, originalSize.y)
                    val imagePixel = adjustedScreenToImagePixel(screenPos, fitRect, originalSize)
                    val hit = seedBlocks.firstOrNull { block ->
                        val bb = block.boundingBox
                        imagePixel.x >= bb.left && imagePixel.x <= bb.right &&
                            imagePixel.y >= bb.top && imagePixel.y <= bb.bottom
                    }
                    hit?.let { onSeedTapped(it) }
                }
            }
            .pointerInput(editMode, viewSize, originalSize, scale, offset, odoRect, otherRect) {
                if (editMode == CropEditMode.IDLE) return@pointerInput
                detectDragGestures(
                    onDragStart = { startOffset -> 
                        val center = Offset(viewSize.x / 2f, viewSize.y / 2f)
                        val screenPos = (startOffset - center - offset) / scale + center
                        dragStart = screenPos
                        activeHandle = DragHandle.NONE

                        if (editMode == CropEditMode.EDIT_CROPS) {
                            val pxW = viewSize.x; val pxH = viewSize.y
                            val fitRect = calculateFitImageRect(pxW, pxH, originalSize.x, originalSize.y)
                            val hitRadius = 40f / scale // Screen pixels

                            fun getScreenRect(r: Rect): Rect {
                                // ICRS -> image pixels (icrsToPixel) -> screen (imagePixelToScreen). No 0-1 normalized crop rect.
                                val p1 = IcrsMath.icrsToPixel(r.left, r.top, originalSize.x.toInt(), originalSize.y.toInt())
                                val p2 = IcrsMath.icrsToPixel(r.right, r.bottom, originalSize.x.toInt(), originalSize.y.toInt())
                                val left = fitRect.left + (p1.x / originalSize.x) * fitRect.width
                                val top = fitRect.top + (p1.y / originalSize.y) * fitRect.height
                                val right = fitRect.left + (p2.x / originalSize.x) * fitRect.width
                                val bottom = fitRect.top + (p2.y / originalSize.y) * fitRect.height
                                return Rect(left, top, right, bottom)
                            }

                            listOf(true to odoRect, false to otherRect).forEach { (isOdo, rect) ->
                                rect?.let { r ->
                                    val sr = getScreenRect(r)
                                    val x = screenPos.x; val y = screenPos.y

                                    when {
                                        (Offset(sr.left, sr.top) - screenPos).getDistance() < hitRadius -> { activeHandle = DragHandle.TOP_LEFT; activeCropIsOdo = isOdo }
                                        (Offset(sr.right, sr.top) - screenPos).getDistance() < hitRadius -> { activeHandle = DragHandle.TOP_RIGHT; activeCropIsOdo = isOdo }
                                        (Offset(sr.left, sr.bottom) - screenPos).getDistance() < hitRadius -> { activeHandle = DragHandle.BOTTOM_LEFT; activeCropIsOdo = isOdo }
                                        (Offset(sr.right, sr.bottom) - screenPos).getDistance() < hitRadius -> { activeHandle = DragHandle.BOTTOM_RIGHT; activeCropIsOdo = isOdo }
                                        Math.abs(y - sr.top) < hitRadius && x > sr.left && x < sr.right -> { activeHandle = DragHandle.TOP; activeCropIsOdo = isOdo }
                                        Math.abs(y - sr.bottom) < hitRadius && x > sr.left && x < sr.right -> { activeHandle = DragHandle.BOTTOM; activeCropIsOdo = isOdo }
                                        Math.abs(x - sr.left) < hitRadius && y > sr.top && y < sr.bottom -> { activeHandle = DragHandle.LEFT; activeCropIsOdo = isOdo }
                                        Math.abs(x - sr.right) < hitRadius && y > sr.top && y < sr.bottom -> { activeHandle = DragHandle.RIGHT; activeCropIsOdo = isOdo }
                                    }
                                }
                                if (activeHandle != DragHandle.NONE) return@forEach
                            }
                        }
                    }, 
                    onDrag = { change, _ ->
                        change.consume()
                        val start = dragStart ?: return@detectDragGestures
                        val center = Offset(viewSize.x / 2f, viewSize.y / 2f)
                        val end = (change.position - center - offset) / scale + center
                        val pxW = viewSize.x; val pxH = viewSize.y
                        val fitRect = calculateFitImageRect(pxW, pxH, originalSize.x, originalSize.y)
                        val imgW = originalSize.x.toInt(); val imgH = originalSize.y.toInt()
                        if (editMode == CropEditMode.EDIT_CROPS && activeHandle != DragHandle.NONE) {
                            val currentRect = if (activeCropIsOdo) odoRect else otherRect
                            currentRect?.let { r ->
                                // ICRS rect -> image pixels via icrsToPixel -> screen via imagePixelToScreen (no 0-1 rect ever)
                                val tlPx = IcrsMath.icrsToPixel(r.left, r.top, imgW, imgH)
                                val brPx = IcrsMath.icrsToPixel(r.right, r.bottom, imgW, imgH)
                                val tl = imagePixelToScreen(Offset(tlPx.x, tlPx.y), fitRect, originalSize)
                                val br = imagePixelToScreen(Offset(brPx.x, brPx.y), fitRect, originalSize)
                                var newTl = tl; var newBr = br
                                when (activeHandle) {
                                    DragHandle.TOP_LEFT -> { newTl = end }
                                    DragHandle.TOP_RIGHT -> { newTl = Offset(newTl.x, end.y); newBr = Offset(end.x, newBr.y) }
                                    DragHandle.BOTTOM_LEFT -> { newTl = Offset(end.x, newTl.y); newBr = Offset(newBr.x, end.y) }
                                    DragHandle.BOTTOM_RIGHT -> { newBr = end }
                                    DragHandle.TOP -> { newTl = Offset(newTl.x, end.y) }
                                    DragHandle.BOTTOM -> { newBr = Offset(newBr.x, end.y) }
                                    DragHandle.LEFT -> { newTl = Offset(end.x, newTl.y) }
                                    DragHandle.RIGHT -> { newBr = Offset(end.x, newBr.y) }
                                    else -> {}
                                }
                                // screen positions -> image pixels -> ICRS via pixelToIcrs
                                val newTlImage = adjustedScreenToImagePixel(newTl, fitRect, originalSize)
                                val newBrImage = adjustedScreenToImagePixel(newBr, fitRect, originalSize)
                                val p1 = IcrsMath.pixelToIcrs(newTlImage.x, newTlImage.y, imgW, imgH)
                                val p2 = IcrsMath.pixelToIcrs(newBrImage.x, newBrImage.y, imgW, imgH)
                                currentDragRect = Rect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y))
                            }
                        }
                    }, 
                    onDragEnd = { 
                        currentDragRect?.let { final ->
                            when {
                                editMode == CropEditMode.EDIT_CROPS && activeCropIsOdo -> onCropChanged(final, otherRect)
                                editMode == CropEditMode.EDIT_CROPS && !activeCropIsOdo -> onCropChanged(odoRect, final)
                            }
                        }
                        currentDragRect = null; dragStart = null; activeHandle = DragHandle.NONE
                    }
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) {
                Image(painter = rememberAsyncImagePainter(photoUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pxW = size.width; val pxH = size.height
                    val fitRect = if (originalSize.x > 0f) calculateFitImageRect(pxW, pxH, originalSize.x, originalSize.y) else Rect(0f, 0f, pxW, pxH)
                    fun drawIcrsRect(rect: Rect, color: Color) {
                        // ICRS -> image pixels (icrsToPixel) -> screen. No lx formula / 0-1 step.
                        val origW = originalSize.x
                        val origH = originalSize.y
                        val p1 = IcrsMath.icrsToPixel(rect.left, rect.top, origW.toInt(), origH.toInt())
                        val p2 = IcrsMath.icrsToPixel(rect.right, rect.bottom, origW.toInt(), origH.toInt())
                        val left = fitRect.left + (p1.x / origW) * fitRect.width
                        val top = fitRect.top + (p1.y / origH) * fitRect.height
                        val w = ((p2.x - p1.x) / origW) * fitRect.width
                        val h = ((p2.y - p1.y) / origH) * fitRect.height
                        drawRect(color, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), style = Stroke(4f / scale))
                    }
                    fun drawPixelAabb(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
                        val origW = originalSize.x
                        val origH = originalSize.y
                        if (origW <= 0f || origH <= 0f) return
                        val sx1 = fitRect.left + (left / origW) * fitRect.width
                        val sy1 = fitRect.top + (top / origH) * fitRect.height
                        val sx2 = fitRect.left + (right / origW) * fitRect.width
                        val sy2 = fitRect.top + (bottom / origH) * fitRect.height
                        drawRect(
                            color,
                            Offset(minOf(sx1, sx2), minOf(sy1, sy2)),
                            androidx.compose.ui.geometry.Size(kotlin.math.abs(sx2 - sx1), kotlin.math.abs(sy2 - sy1)),
                            style = Stroke(2f / scale),
                        )
                    }
                    for (block in seedBlocks) {
                        val bb = block.boundingBox
                        drawPixelAabb(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat(), Color.Yellow)
                    }
                    currentDragRect?.let { drawIcrsRect(it, Color.Green) }
                    odoRect?.let { drawIcrsRect(it, Color.Blue) }
                    otherRect?.let { drawIcrsRect(it, Color.Red) }
                }
            }

            // ZOOM BUTTONS
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallFloatingActionButton(onClick = { scale = (scale * 1.2f).coerceIn(1f, 10f) }, containerColor = Color.White.copy(alpha = 0.7f)) { Text("+") }
                SmallFloatingActionButton(onClick = { scale = (scale / 1.2f).coerceIn(1f, 10f) }, containerColor = Color.White.copy(alpha = 0.7f)) { Text("-") }
            }
        }
    }
}

/**
 * Pure screen <-> image pixel helpers (NO crop Rects, never 0-1 normalized crop data).
 * Used to map gesture positions to absolute image pixels, then ICRS via pixelToIcrs.
 */
private fun adjustedScreenToImagePixel(screen: Offset, fitRect: Rect, original: Offset): Offset {
    if (fitRect.width == 0f || fitRect.height == 0f || original.x == 0f || original.y == 0f) return Offset.Zero
    val ix = ((screen.x - fitRect.left) / fitRect.width) * original.x
    val iy = ((screen.y - fitRect.top) / fitRect.height) * original.y
    return Offset(ix, iy)
}

private fun imagePixelToScreen(imagePixel: Offset, fitRect: Rect, original: Offset): Offset {
    if (fitRect.width == 0f || fitRect.height == 0f || original.x == 0f || original.y == 0f) return Offset.Zero
    val sx = imagePixel.x / original.x
    val sy = imagePixel.y / original.y
    return Offset(fitRect.left + sx * fitRect.width, fitRect.top + sy * fitRect.height)
}

private fun calculateFitImageRect(viewW: Float, viewH: Float, imgW: Float, imgH: Float): Rect {
    val aspect = imgW / imgH; val viewAspect = viewW / viewH
    return if (aspect > viewAspect) { val fitH = viewW / aspect; Rect(0f, (viewH - fitH) / 2f, viewW, (viewH + fitH) / 2f) } else { val fitW = viewH * aspect; Rect((viewW - fitW) / 2f, 0f, (viewW + fitW) / 2f, viewH) }
}

private fun mv2SeedQuad(block: TextBlock): ContentExpandUtils.OrientedQuad {
    if (block.points.size >= 4) {
        val p = FloatArray(8)
        for (i in 0 until 4) {
            p[i * 2] = block.points[i].x.toFloat()
            p[i * 2 + 1] = block.points[i].y.toFloat()
        }
        return ContentExpandUtils.orientedFromPoints8(p)
    }
    return ContentExpandUtils.orientedFromAabb(block.boundingBox)
}

/** Axis-aligned ICRS crop rect from seed via rot-G-style UV pad (0.6 v, 0.8 L/R). */
private fun mv2IcrsFromSeed(block: TextBlock, imgW: Int, imgH: Int): Rect {
    val padded = ContentExpandUtils.calculatedOrientedUv(mv2SeedQuad(block), 0.6f, 0.8f, 0.8f)
    val pts = padded.pts
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (i in 0 until 4) {
        val x = pts[i * 2]
        val y = pts[i * 2 + 1]
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }
    val p1 = IcrsMath.pixelToIcrs(minX, minY, imgW, imgH)
    val p2 = IcrsMath.pixelToIcrs(maxX, maxY, imgW, imgH)
    return Rect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y))
}

private fun bitmapToGrayMat(bitmap: Bitmap): Mat {
    val tmp = Mat()
    org.opencv.android.Utils.bitmapToMat(bitmap, tmp)
    val gray = Mat()
    if (tmp.channels() > 1) {
        Imgproc.cvtColor(tmp, gray, Imgproc.COLOR_BGR2GRAY)
    } else {
        tmp.copyTo(gray)
    }
    tmp.release()
    return gray
}

private suspend fun runMv2PaddleDiscovery(context: Context, bitmap: Bitmap): OcrResult {
    val t0 = System.currentTimeMillis()
    val work = OdometerOcrUtils.applyGrayscale(bitmap)
    val w = work.width
    val h = work.height
    val gray = bitmapToGrayMat(work)
    try {
        val blocks = MultiScaleDetRunner.productDetLandmarks2048(context, gray, 0.40f)
        val landmarks = OdometerOcrUtils.processRawLandmarks(blocks, null, null, w, h)
        return OcrResult(
            engineName = "ML Kit",
            debugText = landmarks.joinToString(" ") { it.text },
            textBlocks = landmarks,
            imageWidth = w,
            imageHeight = h,
            discoveryTimeMs = System.currentTimeMillis() - t0,
        )
    } finally {
        gray.release()
    }
}
