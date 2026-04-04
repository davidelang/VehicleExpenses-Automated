package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
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
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.launch
import java.io.File

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
    var referenceTextBlocks by remember { mutableStateOf<String?>(null) } // pre-extracted
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

    // Single-pass cleaning + text extraction when photo is selected
    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            isCleaning = true
            try {
                val bmp = BitmapFactory.decodeFile(url) ?: return@let
                val (cleanedBmp, textBlocks) = ImageAlignmentUtils.createCleanedReference(bmp)
                if (cleanedBmp != null) {
                    val tempFile = File(context.cacheDir, "temp_cleaned_${System.currentTimeMillis()}.jpg")
                    val out = java.io.FileOutputStream(tempFile)
                    cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    referencePhotoUrl = tempFile.absolutePath
                    referenceTextBlocks = textBlocks
                    cleanedBmp.recycle()
                }
            } catch (e: Exception) {
            } finally {
                isCleaning = false
            }
        }
    }

    LaunchedEffect(referencePhotoUrl, odometerCropRect) {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
                var finalPath = photoPathOrUri
                if (photoPathOrUri.startsWith("content://")) {
                    val tempFile = File.createTempFile("ocr_vehicle", ".jpg", context.cacheDir)
                    context.contentResolver.openInputStream(Uri.parse(photoPathOrUri))?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    finalPath = tempFile.absolutePath
                }
                val crop = odometerCropRect?.let { RectF(it.left, it.top, it.right, it.bottom) }
                val result = OdometerOcrUtils.extractFromPhoto(finalPath, crop)
                result.odometer?.let { odometerReading = it }
                Toast.makeText(context, "Auto-detected odometer: ${result.odometer ?: "—"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Add New Vehicle", style = MaterialTheme.typography.headlineMedium)
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
            currentPhotoUrl = pickedPhotoUrl,
            onPhotoUrlChanged = { pickedPhotoUrl = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isCleaning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
                Text(text = "Cleaning image...", modifier = Modifier.padding(top = 16.dp))
            }
        } else if (referencePhotoUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val left = (offset.x - 80f).coerceAtLeast(0f) / w
                            val top = (offset.y - 40f).coerceAtLeast(0f) / h
                            val right = (offset.x + 80f).coerceAtMost(w) / w
                            val bottom = (offset.y + 40f).coerceAtMost(h) / h
                            odometerCropRect = Rect(left, top, right, bottom)
                            Toast.makeText(context, "Odometer region calibrated", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(referencePhotoUrl),
                    contentDescription = "Reference dash photo - tap the odometer area",
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "TAP the odometer reading area",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Text("No dash photo yet", modifier = Modifier.align(Alignment.Center))
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
                    scope.launch {
                        vehicleViewModel.createNewVehicleWithReference(
                            name = name,
                            make = make,
                            model = model,
                            year = year.toIntOrNull() ?: 2025,
                            licensePlate = licensePlate,
                            cleanedReferenceDashPhotoUrl = referencePhotoUrl,
                            odometerCropRect = odometerCropRect,
                            initialOdometer = odometerReading.toIntOrNull() ?: 0,
                            referenceTextBlocks = referenceTextBlocks
                        )
                        Toast.makeText(context, "New vehicle created with odometer calibration", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                } else {
                    Toast.makeText(context, "Vehicle name is required", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Vehicle + Reference Photo")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
