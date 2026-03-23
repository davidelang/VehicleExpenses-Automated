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
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.photo.PhotoAnalysisScreen
import kotlinx.coroutines.launch

@Composable
fun FuelEntryScreen(vehicleId: Int, vehicleName: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var gallons by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                // ← Photo storage hook (fuel photos respect the Settings toggle)
                coroutineScope.launch {
                    val manager = PhotoStorageManager(context)
                    val filename = "fuel_${vehicleId}_${System.currentTimeMillis()}.jpg"
                    val archiveUrl = manager.savePhoto(uri, filename, PhotoType.FUEL)
                    // archiveUrl is now available if you want to store it in the database
                }
                showAnalysis = true
            }
        }
    }

    if (showAnalysis && photoUri != null) {
        PhotoAnalysisScreen(
            photoUri = photoUri!!,
            onDataExtracted = { _, extractedOdometer, _, _ ->
                extractedOdometer?.let { odometer = it.toString() }
                showAnalysis = false
            },
            onManualEntry = { showAnalysis = false }
        )
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("New Fuel Fill - $vehicleName") }) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = {
                    val uri = Uri.fromFile(context.cacheDir.resolve("photo_${System.currentTimeMillis()}.jpg"))
                    photoUri = uri
                    cameraLauncher.launch(uri)
                }) {
                    Text("Take Dash / Pump Photo")
                }
                OutlinedTextField(value = gallons, onValueChange = { gallons = it }, label = { Text("Gallons") })
                OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer") })
                Button(onClick = { /* save fuel fill */ }) {
                    Text("Save Fill")
                }
            }
        }
    }
}
