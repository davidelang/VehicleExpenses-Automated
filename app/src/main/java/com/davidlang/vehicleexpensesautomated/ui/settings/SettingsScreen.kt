@file:Suppress("DEPRECATION")

package com.davidlang.vehicleexpensesautomated.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.storage.GoogleDriveProvider
import com.davidlang.vehicleexpensesautomated.data.storage.NoOpStorageProvider
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val client = remember { GoogleSheetsClient() }

    var sheetId by remember { mutableStateOf(prefs.getString("sheet_id", "") ?: "") }
    var syncEnabled by remember { mutableStateOf(prefs.getBoolean("sync_enabled", false)) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifi_only", true)) }
    var chargingOnly by remember { mutableStateOf(prefs.getBoolean("charging_only", false)) }
    var frequencyHours by remember { mutableStateOf(prefs.getInt("frequency_hours", 6)) }
    var driveFolder by remember { mutableStateOf(prefs.getString("drive_folder", "Vehicle Expenses Photos") ?: "") }
    var saveFuelPhotos by remember { mutableStateOf(prefs.getBoolean("save_fuel_photos", false)) }
    var photoProviderPref by remember { mutableStateOf(prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive") }
    var status by remember { mutableStateOf("Ready") }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    // Dynamic provider based on user selection
    val currentProvider: PhotoStorageProvider = remember(photoProviderPref, signedInAccount) {
        when (photoProviderPref) {
            "google_drive" -> GoogleDriveProvider(signedInAccount?.idToken)
            else -> NoOpStorageProvider()
        }
    }

    LaunchedEffect(sheetId, syncEnabled, wifiOnly, chargingOnly, frequencyHours, driveFolder, saveFuelPhotos, photoProviderPref) {
        prefs.edit()
            .putString("sheet_id", sheetId)
            .putBoolean("sync_enabled", syncEnabled)
            .putBoolean("wifi_only", wifiOnly)
            .putBoolean("charging_only", chargingOnly)
            .putInt("frequency_hours", frequencyHours)
            .putString("drive_folder", driveFolder)
            .putBoolean("save_fuel_photos", saveFuelPhotos)
            .putString("photo_storage_provider", photoProviderPref)
            .apply()

        if (syncEnabled && sheetId.isNotBlank()) {
            schedulePeriodicSync(context, wifiOnly, chargingOnly, frequencyHours)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("vehicle_sync")
        }
    }

    // ... (rest of the screen unchanged until Photo Storage section)

    // === Photo Storage Settings ===
    Text("Photo Storage", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    Text("Storage Provider")
    Row {
        listOf("Google Drive", "None").forEach { label ->
            val providerKey = if (label == "Google Drive") "google_drive" else "none"
            FilterChip(
                selected = photoProviderPref == providerKey,
                onClick = { photoProviderPref = providerKey },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }

    OutlinedTextField(
        value = driveFolder,
        onValueChange = { driveFolder = it },
        label = { Text("Drive Folder Name (only for Google Drive)") },
        modifier = Modifier.fillMaxWidth()
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Save fuel photos to archive")
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = saveFuelPhotos, onCheckedChange = { saveFuelPhotos = it })
    }

    Text("Expense photos are **always** archived", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)

    // ... rest of your existing screen code (Background Sync, Manual Sync button, etc.)
}
