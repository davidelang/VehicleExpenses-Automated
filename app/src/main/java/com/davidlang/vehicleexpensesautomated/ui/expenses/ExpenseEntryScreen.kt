package com.davidlang.vehicleexpensesautomated.ui.expenses

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
fun ExpenseEntryScreen(vehicleId: Int, vehicleName: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                // ← Photo storage hook (expenses are ALWAYS archived)
                coroutineScope.launch {
                    val manager = PhotoStorageManager(context)
                    val filename = "expense_${vehicleId}_${System.currentTimeMillis()}.jpg"
                    val archiveUrl = manager.savePhoto(uri, filename, PhotoType.EXPENSE)
                    // archiveUrl is now available if you want to store it in the database
                }
                showAnalysis = true
            }
        }
    }

    if (showAnalysis && photoUri != null) {
        PhotoAnalysisScreen(
            photoUri = photoUri!!,
            onDataExtracted = { extractedAmount, _, _, extractedDesc ->
                extractedAmount?.let { amount = it.toString() }
                extractedDesc?.let { description = it }
                showAnalysis = false
            },
            onManualEntry = { showAnalysis = false }
        )
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("New Expense - $vehicleName") }) }) { padding ->
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
                    Text("Take Receipt Photo")
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                Button(onClick = { /* save expense */ }) {
                    Text("Save Expense")
                }
            }
        }
    }
}
