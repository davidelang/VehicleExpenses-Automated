package com.davidlang.vehicleexpensesautomated.ui.expenses

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.ImageHashUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch

@Composable
fun ExpenseEntryScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState()
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    var cost by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }

    // Camera-first flow for new receipt
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val copiedPath = settingsViewModel.photoStorageManager.savePhotoFromBitmap(it, PhotoType.FUEL)
                photoPath = copiedPath
                Toast.makeText(context, "Receipt photo captured", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_LONG).show()
            }
        }
    }

    // FULLY AUTOMATIC OCR on every capture
    LaunchedEffect(photoPath) {
        photoPath?.let { path ->
            scope.launch {
                val result = OdometerOcrUtils.extractFromPhoto(path)
                result.cost?.let { cost = it }

                Toast.makeText(
                    context,
                    "Auto-detected cost: ${result.cost ?: "—"}",
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
        Text(text = "New Expense Entry (Receipt)", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Take Receipt Photo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedVehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Select vehicle (optional)",
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

        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Total Cost (auto-filled)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description / Merchant") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val entry = ExpenseEntry(
                        vehicleId = selectedVehicle?.id ?: 0,
                        amount = cost.toDoubleOrNull() ?: 0.0,
                        description = description,
                        date = System.currentTimeMillis(),
                        photoUrl = photoPath
                    )
                    expenseViewModel.saveExpense(entry)
                    Toast.makeText(context, "Receipt expense saved", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Expense Entry")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
