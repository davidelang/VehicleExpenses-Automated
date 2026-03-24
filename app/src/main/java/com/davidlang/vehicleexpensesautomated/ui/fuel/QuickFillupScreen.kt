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
import kotlinx.coroutines.launch

@Composable
fun QuickFillupScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Placeholder vehicle list (real load + dash reference matching added next)
    LaunchedEffect(Unit) {
        if (vehicles.isEmpty()) {
            selectedVehicle = vehicles.firstOrNull()
        }
    }

    // Auto-match vehicle the moment the NEW dash/odometer photo is taken
    // (PhotoPicker is camera-first — takes a fresh picture immediately, no gallery default)
    LaunchedEffect(photoUrl) {
        photoUrl?.let {
            if (vehicles.isNotEmpty()) {
                selectedVehicle = vehicles.first()
                Toast.makeText(context, "📸 Dash photo captured", Toast.LENGTH_SHORT).show()
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

        // SAME PhotoPicker you already use — now camera-first for new dash photos
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
}
