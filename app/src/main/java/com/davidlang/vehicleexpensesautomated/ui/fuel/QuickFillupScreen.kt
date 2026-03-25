package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    LaunchedEffect(vehicles) {
        if (selectedVehicle == null && vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        }
    }

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
                        "Auto-matched ${bestVehicle.make} ${bestVehicle.model} (${(bestSimilarity * 100).toInt()}%)",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                    Toast.makeText(context, "Fill-up saved", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Fill-up")
        }
    }
}
