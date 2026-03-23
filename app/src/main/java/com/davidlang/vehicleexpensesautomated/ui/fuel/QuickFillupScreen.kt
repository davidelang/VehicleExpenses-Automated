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

enum class FillupPhase { DASH, PUMP, COMPLETE }

@Composable
fun QuickFillupScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(FillupPhase.DASH) }
    var vehicleId by remember { mutableStateOf<Int?>(null) }
    var vehicleName by remember { mutableStateOf("Unknown Vehicle") }
    var dashUri by remember { mutableStateOf<Uri?>(null) }
    var pumpUri by remember { mutableStateOf<Uri?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentPhotoType by remember { mutableStateOf(PhotoType.FUEL) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                coroutineScope.launch {
                    val manager = PhotoStorageManager(context)
                    val filename = "${if (phase == FillupPhase.DASH) "dash" else "pump"}_${System.currentTimeMillis()}.jpg"
                    manager.savePhoto(uri, filename, currentPhotoType)
                }
                showAnalysis = true
            }
        }
    }

    if (showAnalysis && currentPhotoUri != null) {
        PhotoAnalysisScreen(
            photoUri = currentPhotoUri!!,
            isVehicleDetection = (phase == FillupPhase.DASH),
            onVehicleDetected = { detectedId, detectedName ->
                vehicleId = detectedId
                vehicleName = detectedName
                phase = FillupPhase.PUMP
                showAnalysis = false
            },
            onDataExtracted = { _, extractedOdometer, _, _ ->
                // TODO: save fillup with extracted data + vehicleId
                phase = FillupPhase.COMPLETE
                showAnalysis = false
            },
            onManualEntry = { showAnalysis = false }
        )
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Quick Fillup") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Text("💰") },
                        label = { Text("Expense") },
                        selected = false,
                        onClick = { navController.navigate("expenses/0/Expense") }
                    )
                    NavigationBarItem(
                        icon = { Text("🚗") },
                        label = { Text("Vehicles") },
                        selected = false,
                        onClick = { navController.navigate("vehicles") }
                    )
                    NavigationBarItem(
                        icon = { Text("⚙️") },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = { navController.navigate("settings") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Vehicle: $vehicleName", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(32.dp))

                when (phase) {
                    FillupPhase.DASH -> {
                        Text("Step 1: Take dashboard photo", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val uri = Uri.fromFile(context.cacheDir.resolve("dash_${System.currentTimeMillis()}.jpg"))
                            dashUri = uri
                            currentPhotoUri = uri
                            currentPhotoType = PhotoType.FUEL
                            cameraLauncher.launch(uri)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("📸 Take Dashboard Photo")
                        }
                    }
                    FillupPhase.PUMP -> {
                        Text("Step 2: Take pump photo", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val uri = Uri.fromFile(context.cacheDir.resolve("pump_${System.currentTimeMillis()}.jpg"))
                            pumpUri = uri
                            currentPhotoUri = uri
                            currentPhotoType = PhotoType.FUEL
                            cameraLauncher.launch(uri)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("⛽ Take Pump Photo")
                        }
                    }
                    FillupPhase.COMPLETE -> {
                        Text("✅ Fillup saved!", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { phase = FillupPhase.DASH; vehicleId = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("New Fillup")
                        }
                    }
                }
            }
        }
    }
}
