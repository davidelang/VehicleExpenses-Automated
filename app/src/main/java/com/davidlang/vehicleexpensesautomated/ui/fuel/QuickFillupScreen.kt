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
    var odometerAutoFilled by remember { mutableStateOf(false) }

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
            },
            onDataExtracted = { _, extractedOdo, _, _ ->
                if (isPumpPhoto) {
                    navController.navigate("reports/${vehicleId ?: 0}")
                } else {
                    extractedOdo?.let { odometer = it.toString(); odometerAutoFilled = true }
                    showAnalysis = false
                }
            },
            onManualEntry = { showAnalysis = false }
        )
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("Quick Fillup") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Vehicle: $vehicleName", style = MaterialTheme.typography.titleMedium)

                Row {
                    Button(onClick = { /* vehicle picker stub */ }) { Text("Select Vehicle") }
                    Button(onClick = {
                        val uri = Uri.fromFile(context.cacheDir.resolve("dash_${System.currentTimeMillis()}.jpg"))
                        currentUri = uri
                        isPumpPhoto = false
                        cameraLauncher.launch(uri)
                    }) { Text("📸 Dashboard Photo") }
                }

                Spacer(Modifier.height(16.dp))

                if (!odometerAutoFilled) {
                    OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer Reading") })
                    Button(onClick = { /* confirm */ }, modifier = Modifier.fillMaxWidth()) { Text("Confirm Odometer") }
                } else {
                    Text("Odometer auto-filled: $odometer", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(24.dp))

                Text("Pump Fill (gallons + dollars)", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val uri = Uri.fromFile(context.cacheDir.resolve("pump_${System.currentTimeMillis()}.jpg"))
                    currentUri = uri
                    isPumpPhoto = true
                    cameraLauncher.launch(uri)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("⛽ Take Pump Photo")
                }
                Button(onClick = { /* manual gallons/cost */ }, modifier = Modifier.fillMaxWidth()) { Text("Manual Volume & Cost") }
                Button(onClick = { vehicleId?.let { navController.navigate("reports/$it") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip to Reports")
                }
            }
        }
    }
}
