package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
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
fun AddNewVehicleScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
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
    var showLandmarkCheck by remember { mutableStateOf(false) }
    var discoveredLandmarks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var lastOcrDebugResult by remember { mutableStateOf<OcrResult?>(null) }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            try {
                referencePhotoUrl = url
            } catch (e: Exception) { Log.e("AddNewVehicle", "Image loading failed", e) }
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
                    result.odometer?.let { odometerReading = it }
                } catch (e: Exception) { Log.e("AddNewVehicle", "OCR exception", e); Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        } ?: run { Toast.makeText(context, "No photo selected", Toast.LENGTH_SHORT).show() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Add New Vehicle", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Vehicle Name (required)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        PhotoPicker(photoStorageManager = settingsViewModel.photoStorageManager, photoType = PhotoType.FUEL, currentPhotoUrl = pickedPhotoUrl, onPhotoUrlChanged = { pickedPhotoUrl = it })
        Spacer(modifier = Modifier.height(8.dp))
        if (referencePhotoUrl != null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        odometerCropRect = Rect((offset.x - 80f).coerceAtLeast(0f) / w, (offset.y - 40f).coerceAtLeast(0f) / h, (offset.x + 80f).coerceAtMost(w) / w, (offset.y + 40f).coerceAtMost(h) / h)
                        Toast.makeText(context, "Odometer region calibrated", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Image(painter = rememberAsyncImagePainter(referencePhotoUrl), contentDescription = "Reference photo", modifier = Modifier.fillMaxSize())
                Text(text = "TAP the odometer reading area", modifier = Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = tryOcr, modifier = Modifier.fillMaxWidth()) { Text("Check Reference OCR & Landmarks") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = odometerReading, onValueChange = { odometerReading = it }, label = { Text("Odometer reading") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        vehicleViewModel.createNewVehicleWithReference(
                            name = name, make = make, model = model, year = year.toIntOrNull() ?: 2025, licensePlate = licensePlate, referenceDashPhotoUrl = pickedPhotoUrl, cleanedReferenceDashPhotoUrl = null,
                            odometerCropRect = odometerCropRect, otherTextCropRect = otherTextCropRect, initialOdometer = odometerReading.toIntOrNull() ?: 0, landmarkTextBlocksJson = landmarkTextBlocksJson
                        )
                        navController.popBackStack()
                    }
                } else { Toast.makeText(context, "Vehicle name is required", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Vehicle + Reference Photo") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
    if (showLandmarkCheck) {
        LandmarkDebugDialog(photoPath = referencePhotoUrl, odometerCrop = odometerCropRect, otherTextCrop = otherTextCropRect, landmarks = discoveredLandmarks, odometerText = lastOcrDebugResult?.odometer ?: "FAILED", onDismiss = { showLandmarkCheck = false })
    }
}

private fun serializeLandmarks(landmarks: List<TextBlock>): String {
    val array = JSONArray()
    landmarks.forEach { lm ->
        val obj = JSONObject()
        obj.put("text", lm.text); obj.put("left", lm.boundingBox.left); obj.put("top", lm.boundingBox.top); obj.put("right", lm.boundingBox.right); obj.put("bottom", lm.boundingBox.bottom)
        array.put(obj)
    }
    return array.toString()
}
