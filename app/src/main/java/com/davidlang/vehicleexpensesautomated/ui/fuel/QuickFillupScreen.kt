package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel   # ← new recommended import
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun QuickFillupScreen(navController: NavController) {
    val viewModel: FillupViewModel = hiltViewModel()
    val context = LocalContext.current
    val photoStorageManager = remember { PhotoStorageManager(context) }
    val scope = rememberCoroutineScope()

    var odometer by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Quick Fuel Fill-up", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = gallons, onValueChange = { gallons = it }, label = { Text("Gallons") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isUploading = true
                scope.launch {
                    val fakeUri = Uri.parse("content://com.davidlang.vehicleexpensesautomated.test/fuel-receipt.jpg")
                    val uploadedUrl = photoStorageManager.savePhoto(fakeUri, "fuel_${System.currentTimeMillis()}.jpg", PhotoType.FUEL)
                    photoUrl = uploadedUrl
                    isUploading = false
                }
            },
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isUploading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("📸 Take / Choose Receipt Photo")
        }

        if (photoUrl != null) Text("✅ Photo uploaded: $photoUrl", color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val entry = FuelEntry(
                        vehicleId = 0,
                        odometer = odometer.toIntOrNull() ?: 0,
                        gallons = gallons.toDoubleOrNull() ?: 0.0,
                        cost = cost.toDoubleOrNull() ?: 0.0,
                        timestamp = Instant.now().toEpochMilli(),
                        photoUrl = photoUrl
                    )
                    viewModel.saveFuelEntry(entry)
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Fuel Entry")
        }
    }
}
