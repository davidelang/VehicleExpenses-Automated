package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.photo.PhotoAnalysisScreen
import kotlinx.coroutines.launch

@Composable
fun QuickFillupScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var vehicleId by remember { mutableStateOf<Int?>(null) }
    var vehicleName by remember { mutableStateOf("No vehicle selected") }
    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var showAnalysis by remember { mutableStateOf(false) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var isPumpPhoto by remember { mutableStateOf(false) }
    var showVehiclePicker by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentUri?.let { uri ->
                coroutineScope.launch {
                    val manager = PhotoStorageManager(context)
                    val filename = "${if (isPumpPhoto) "pump" else "dash"}_${System.currentTimeMillis()}.jpg"
                    manager.savePhoto(uri, filename, PhotoType.FUEL)
                }
                showAnalysis = true
            }
        }
    }

    if (showAnalysis && currentUri != null) {
        PhotoAnalysisScreen(
            photoUri = currentUri!!,
            isVehicleDetection = !isPumpPhoto,
            onVehicleDetected = { id, name ->
                vehicleId = id
                vehicleName = name
                showAnalysis = false
            },
            onDataExtracted = { _, extractedOdo, _, _ ->
                if (isPumpPhoto) {
                    // pump photo success → go to reports
                    navController.navigate("reports/${vehicleId ?: 0}")
                } else {
                    showAnalysis = false
                }
            },
            onManualEntry = { showAnalysis = false }
        )
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Quick Fillup") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Vehicle: $vehicleName", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Vehicle selection row
                Row {
                    Button(onClick = { showVehiclePicker = true }) { Text("Select Vehicle") }
                    Button(onClick = {
                        val uri = Uri.fromFile(context.cacheDir.resolve("dash_${System.currentTimeMillis()}.jpg"))
                        currentUri = uri
                        isPumpPhoto = false
                        cameraLauncher.launch(uri)
                    }) { Text("📸 Dashboard") }
                }

                Spacer(Modifier.height(24.dp))

                // Odometer section
                OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer Reading") })
                Button(onClick = { /* save odometer if needed */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm Odometer")
                }

                Spacer(Modifier.height(24.dp))

                // Pump section
                Text("Pump / Fill Info", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Button(onClick = {
                    val uri = Uri.fromFile(context.cacheDir.resolve("pump_${System.currentTimeMillis()}.jpg"))
                    currentUri = uri
                    isPumpPhoto = true
                    cameraLauncher.launch(uri)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("⛽ Take Pump Photo (gallons + cost)")
                }

                Button(onClick = { /* show manual fields */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Manual Volume & Cost")
                }

                Button(onClick = {
                    if (vehicleId != null) navController.navigate("reports/${vehicleId}")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip to Summary/Reports")
                }
            }
        }
    }

    if (showVehiclePicker) {
        AlertDialog(
            onDismissRequest = { showVehiclePicker = false },
            title = { Text("Select Vehicle") },
            text = {
                Column {
                    listOf("Toyota Camry (1)", "Honda Civic (2)", "Ford F-150 (3)").forEach { v ->
                        Button(onClick = {
                            vehicleId = v.split("(")[1].removeSuffix(")").toInt()
                            vehicleName = v.split(" (")[0]
                            showVehiclePicker = false
                        }) { Text(v) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
