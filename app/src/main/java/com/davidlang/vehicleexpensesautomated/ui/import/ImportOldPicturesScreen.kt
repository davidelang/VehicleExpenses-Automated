package com.davidlang.vehicleexpensesautomated.ui.import

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.ImageHashUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch

@Composable
fun ImportOldPicturesScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState()
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val copiedPath = settingsViewModel.photoStorageManager.savePhotoFromUri(it, PhotoType.FUEL)
                photoPath = copiedPath
                Toast.makeText(context, "📸 Old photo imported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Failed to import photo", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 🔥 FULLY AUTOMATIC OCR
    LaunchedEffect(photoPath) {
        photoPath?.let { path ->
            scope.launch {
                val result = OdometerOcrUtils.extractFromPhoto(path)
                result.odometer?.let { odometer = it }
                result.gallons?.let { gallons = it }
                result.cost?.let { cost = it }

                Toast.makeText(
                    context,
                    "📸 Auto-detected: odometer ${result.odometer ?: "—"} | gallons ${result.gallons ?: "—"} | cost ${result.cost ?: "—"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Auto-match vehicle
    LaunchedEffect(photoPath) {
        photoPath?.let { newPath ->
            val newHash = ImageHashUtils.computeHashFromFilePath(newPath)
            if (newHash != null && vehicles.isNotEmpty()) {
                var bestVehicle: Vehicle? = null
                var bestSimilarity = 0.0

                for (vehicle in vehicles) {
                    vehicle.referenceDashPhotoUrl?.let { refPath ->
                        val refHash = ImageHashUtils.computeHashFromFilePath(refPath)
                        if (refHash != null) {
                            val sim = ImageHashUtils.similarity(newHash, refHash)
                            if (sim > bestSimilarity) {
                                bestSimilarity = sim
                                bestVehicle = vehicle
                            }
                        }
                    }
                }
                if (bestVehicle != null && bestSimilarity > 0.75) {
                    selectedVehicle = bestVehicle
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Import Old Pictures", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("📂 Pick Old Photo from Gallery")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedVehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Select vehicle",
                onValueChange = {},
                label = { Text("Vehicle") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = { Text("${vehicle.make} ${vehicle.model} (${vehicle.year})") },
                        onClick = { selectedVehicle = vehicle; expanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer (auto-filled)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = gallons, onValueChange = { gallons = it }, label = { Text("Gallons (auto-filled)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Total Cost (auto-filled)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val vehicleId = selectedVehicle?.id ?: run {
                    Toast.makeText(context, "Select a vehicle first", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    val entry = FuelEntry(
                        vehicleId = vehicleId,
                        odometer = odometer.toIntOrNull() ?: 0,
                        gallons = gallons.toDoubleOrNull() ?: 0.0,
                        cost = cost.toDoubleOrNull() ?: 0.0,
                        timestamp = System.currentTimeMillis(),
                        photoUrl = photoPath
                    )
                    fuelViewModel.saveFuel(entry)
                    Toast.makeText(context, "✅ Old fill-up imported and saved", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Import & Save as Fuel Entry")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
