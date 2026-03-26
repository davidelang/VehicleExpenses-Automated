package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
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

    LaunchedEffect(referencePhotoUrl) {
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
                val result = OdometerOcrUtils.extractFromPhoto(finalPath)
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
            label = { Text("Year") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = licensePlate,
            onValueChange = { licensePlate = it },
            label = { Text("License Plate") },
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
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                Text("Reference Dash Photo (thumbnail placeholder)", modifier = Modifier.align(Alignment.Center))
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
                    vehicleViewModel.createNewVehicleWithReference(
                        name = name,
                        make = make,
                        model = model,
                        year = year.toIntOrNull() ?: 2025,
                        licensePlate = licensePlate,
                        referenceDashPhotoUrl = referencePhotoUrl,
                        initialOdometer = odometerReading.toIntOrNull() ?: 0
                    )
                    Toast.makeText(context, "Vehicle saved", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "Vehicle name is required", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Vehicle")
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
