package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.widget.Toast
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
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.ImageHashUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch

@Composable
fun QuickFillupScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState()
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Show "Is this a new vehicle?" dialog when photo has no match
    var showNewVehicleDialog by remember { mutableStateOf(false) }
    var newMake by remember { mutableStateOf("") }
    var newModel by remember { mutableStateOf("") }
    var newYear by remember { mutableStateOf("") }
    var newLicense by remember { mutableStateOf("") }
    var newOdometer by remember { mutableStateOf("") }   // user types the number they see on dash

    // Real DB load
    LaunchedEffect(vehicles) {
        if (selectedVehicle == null && vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        }
    }

    // 🔥 AUTO-MATCHING + NEW-VEHICLE PROMPT
    LaunchedEffect(photoUrl) {
        photoUrl?.let { newPhotoPath ->
            val newHash = ImageHashUtils.computeHashFromFilePath(newPhotoPath)
            if (newHash != null && vehicles.isNotEmpty()) {
                var bestVehicle: Vehicle? = null
                var bestSimilarity = 0.0

                for (vehicle in vehicles) {
                    val refPath = vehicle.referenceDashPhotoUrl
                    if (refPath != null) {
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
                    Toast.makeText(
                        context,
                        "🔍 Auto-matched ${bestVehicle.make} ${bestVehicle.model} (${(bestSimilarity * 100).toInt()}%)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // NO MATCH → ask if new vehicle
                    showNewVehicleDialog = true
                    Toast.makeText(context, "📸 Photo captured — no match found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Reference photo save (with odometer input)
    photoUrl?.let { capturedUrl ->
        selectedVehicle?.let { vehicle ->
            Button(
                onClick = {
                    // In real app you'd show a dialog to type the odometer number here
                    // For now we just save the photo (you can expand this later)
                    vehicleViewModel.updateReferenceDashPhoto(vehicle.id, capturedUrl)
                    Toast.makeText(context, "✅ Saved as reference dash photo for ${vehicle.make} ${vehicle.model}", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Save as Reference Dash Photo")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Quick Fill-up",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
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

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = { Text("${vehicle.make} ${vehicle.model} (${vehicle.year})") },
                        onClick = {
                            selectedVehicle = vehicle
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = odometer,
            onValueChange = { odometer = it },
            label = { Text("Odometer Reading") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = gallons,
            onValueChange = { gallons = it },
            label = { Text("Gallons") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            label = { Text("Total Cost") },
            modifier = Modifier.fillMaxWidth()
        )

        PhotoPicker(
            photoStorageManager = settingsViewModel.photoStorageManager,
            photoType = PhotoType.FUEL,
            currentPhotoUrl = photoUrl,
            onPhotoUrlChanged = { photoUrl = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val vehicleId = selectedVehicle?.id ?: 1
                    scope.launch {
                        val entry = FuelEntry(
                            vehicleId = vehicleId,
                            odometer = odometer.toIntOrNull() ?: 0,
                            gallons = gallons.toDoubleOrNull() ?: 0.0,
                            cost = cost.toDoubleOrNull() ?: 0.0,
                            timestamp = System.currentTimeMillis(),
                            photoUrl = photoUrl
                        )
                        fuelViewModel.saveFuel(entry)
                        Toast.makeText(context, "✅ Fill-up saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Fill-up")
            }

            Button(
                onClick = {
                    val vehicleId = selectedVehicle?.id ?: 1
                    navController.navigate("expense/$vehicleId")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add Expense")
            }
        }
    }

    // NEW VEHICLE DIALOG (unmatched photo)
    if (showNewVehicleDialog) {
        AlertDialog(
            onDismissRequest = { showNewVehicleDialog = false },
            title = { Text("New Vehicle?") },
            text = {
                Column {
                    Text("No matching reference photo found.\nIs this photo for a new vehicle?")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newMake, onValueChange = { newMake = it }, label = { Text("Make") })
                    OutlinedTextField(value = newModel, onValueChange = { newModel = it }, label = { Text("Model") })
                    OutlinedTextField(value = newYear, onValueChange = { newYear = it }, label = { Text("Year") })
                    OutlinedTextField(value = newLicense, onValueChange = { newLicense = it }, label = { Text("License Plate") })
                    OutlinedTextField(value = newOdometer, onValueChange = { newOdometer = it }, label = { Text("Odometer reading on photo") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vehicleViewModel.createNewVehicleWithReference(
                        make = newMake,
                        model = newModel,
                        year = newYear.toIntOrNull() ?: 2025,
                        licensePlate = newLicense,
                        referenceDashPhotoUrl = photoUrl ?: "",
                        initialOdometer = newOdometer.toIntOrNull() ?: 0
                    )
                    Toast.makeText(context, "✅ New vehicle created with this dash photo as reference", Toast.LENGTH_LONG).show()
                    showNewVehicleDialog = false
                }) {
                    Text("Yes — Create New Vehicle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewVehicleDialog = false }) {
                    Text("No — Just use current photo")
                }
            }
        )
    }
}
