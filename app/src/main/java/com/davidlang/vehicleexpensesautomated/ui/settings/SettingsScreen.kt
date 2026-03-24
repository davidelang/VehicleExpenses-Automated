package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import android.net.Uri
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
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.data.sync.CsvManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val photoStorageManager = remember { PhotoStorageManager(context) }
    val csvManager: CsvManager = remember { CsvManager(context) } // TODO: make Hilt later if needed
    val scope = rememberCoroutineScope()

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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            scope.launch {
                val exportedUri = csvManager.exportToZip()
                // TODO: copy to user-chosen uri if needed, for now we just show success
                status = "✅ Exported CSV zip to Downloads"
                Toast.makeText(context, "CSV exported to Downloads", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                csvManager.importFromZip(uri)
                status = "✅ Imported from CSV zip"
                Toast.makeText(context, "CSV import complete", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(sheetId, syncEnabled, wifiOnly, chargingOnly, frequencyHours, driveFolder, saveFuelPhotos, photoProviderPref) {
        prefs.edit().apply {
            putString("sheet_id", sheetId)
            putBoolean("sync_enabled", syncEnabled)
            putBoolean("wifi_only", wifiOnly)
            putBoolean("charging_only", chargingOnly)
            putInt("frequency_hours", frequencyHours)
            putString("drive_folder", driveFolder)
            putBoolean("save_fuel_photos", saveFuelPhotos)
            putString("photo_storage_provider", photoProviderPref)
            apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = sheetId,
            onValueChange = { sheetId = it },
            label = { Text("Google Sheet ID") },
            modifier = Modifier.fillMaxWidth()
        )

        // Sync options (unchanged)
        SwitchSetting("Enable Background Sync", syncEnabled) { syncEnabled = it }
        SwitchSetting("Wi-Fi Only", wifiOnly) { wifiOnly = it }
        SwitchSetting("Charging Only", chargingOnly) { chargingOnly = it }

        SliderSetting("Sync Frequency (hours)", frequencyHours.toFloat(), 1f..24f) {
            frequencyHours = it.toInt()
        }

        SwitchSetting("Save Fuel Receipt Photos", saveFuelPhotos) { saveFuelPhotos = it }

        Text("Photo Storage Provider", style = MaterialTheme.typography.titleMedium)
        Row {
            RadioButton(selected = photoProviderPref == "google_drive", onClick = { photoProviderPref = "google_drive" })
            Text("Google Drive")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = photoProviderPref == "none", onClick = { photoProviderPref = "none" })
            Text("None")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CSV buttons
        Button(onClick = { exportLauncher.launch("vehicle_expenses_backup.zip") }) {
            Text("Export to CSV (ZIP)")
        }

        Button(onClick = { importLauncher.launch("*/*") }) {
            Text("Import from CSV ZIP")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            scope.launch {
                status = "Testing upload..."
                val testUri = Uri.parse("content://com.davidlang.vehicleexpensesautomated.test/fake.jpg")
                val url = photoStorageManager.savePhoto(testUri, "test.jpg", PhotoType.FUEL)
                status = if (url != null) "✅ Upload test succeeded" else "❌ Upload test failed"
            }
        }) {
            Text("Test Photo Upload")
        }

        Text(status, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
