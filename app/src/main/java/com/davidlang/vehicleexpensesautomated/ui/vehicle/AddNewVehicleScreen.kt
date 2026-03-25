package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.widget.Toast
import androidx.compose.foundation.layout.*
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

@Composable
fun AddNewVehicleScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var odometerReading by remember { mutableStateOf("") }
    var referencePhotoUrl by remember { mutableStateOf<String?>(null) }

    // Automatic OCR on reference photo capture (odometer only for new vehicle setup)
    LaunchedEffect(referencePhotoUrl) {
        referencePhotoUrl?.let { photoPath ->
            scope.launch {
                val result = OdometerOcrUtils.extractFromPhoto(photoPath)
                result.odometer?.let { odometerReading = it }
                Toast.makeText(context, "Auto-detected odometer: ${result.odometer ?: "—"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Add New Vehicle",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = make,
            onValueChange = { make = it },
            label = { Text("Make") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
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

        OutlinedTextField(
            value = odometerReading,
            onValueChange = { odometerReading = it },
            label = { Text("Odometer reading (auto-filled by OCR)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (make.isNotBlank() && model.isNotBlank() && referencePhotoUrl != null) {
                    vehicleViewModel.createNewVehicleWithReference(
                        make = make,
                        model = model,
                        year = year.toIntOrNull() ?: 2025,
                        licensePlate = licensePlate,
                        referenceDashPhotoUrl = referencePhotoUrl!!,
                        initialOdometer = odometerReading.toIntOrNull() ?: 0
                    )
                    Toast.makeText(context, "New vehicle created with reference dash photo", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "Make, model, and reference photo required", Toast.LENGTH_SHORT).show()
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
