package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
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

    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }
    var odometerCropRect by remember { mutableStateOf<Rect?>(null) }   // normalized 0.0-1.0

    // Drag-to-draw state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var currentDrag by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(referencePhotoUrl, odometerCropRect) {
        referencePhotoUrl?.let { photoPathOrUri ->
            scope.launch {
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
        Text("Manage Vehicles", style = MaterialTheme.typography.headlineMedium)
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
                    contentDescription = "Reference dash photo - drag across the odometer area",
                    modifier = Modifier.fillMaxSize()
                )

                // Live drag rectangle preview
                if (dragStart != null && currentDrag != null) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val start = dragStart!!
                        val end = currentDrag!!
                        drawRect(
                            color = Color.Blue.copy(alpha = 0.4f),
                            topLeft = Offset(start.x.coerceAtMost(end.x), start.y.coerceAtMost(end.y)),
                            size = androidx.compose.ui.geometry.Size(
                                (end.x - start.x).coerceAtLeast(0f).coerceAtMost(size.width),
                                (end.y - start.y).coerceAtLeast(0f).coerceAtMost(size.height)
                            )
                        )
                        drawRect(
                            color = Color.Blue,
                            topLeft = Offset(start.x.coerceAtMost(end.x), start.y.coerceAtMost(end.y)),
                            size = androidx.compose.ui.geometry.Size(
                                (end.x - start.x).coerceAtLeast(0f).coerceAtMost(size.width),
                                (end.y - start.y).coerceAtLeast(0f).coerceAtMost(size.height)
                            ),
                            style = Stroke(width = 4f)
                        )
                    }
                }

                // Saved crop rectangle overlay (green)
                odometerCropRect?.let { crop ->
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
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

                if (odometerCropRect == null && dragStart == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp)
                            .background(Color.Blue.copy(alpha = 0.9f), shape = MaterialTheme.shapes.medium)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "DRAG ACROSS THE ODOMETER NUMBERS",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
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
                    Toast.makeText(context, "Crop region reset", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Region")
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
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
                        navController.popBackStack()
                    } else {
                        Toast.makeText(context, "Vehicle name is required", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Vehicle")
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
}
