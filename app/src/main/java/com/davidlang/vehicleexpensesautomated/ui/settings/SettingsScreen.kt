package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val viewModel: SettingsViewModel = hiltViewModel()
    val csvManager = viewModel.csvManager
    val photoStorageManager = viewModel.photoStorageManager
    val scope = rememberCoroutineScope()

    var sheetId by remember { mutableStateOf(prefs.getString("sheet_id", "") ?: "") }
    var syncEnabled by remember { mutableStateOf(prefs.getBoolean("sync_enabled", false)) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifi_only", true)) }
    var chargingOnly by remember { mutableStateOf(prefs.getBoolean("charging_only", false)) }
    var frequencyHours by remember { mutableStateOf(prefs.getInt("frequency_hours", 6)) }
    var driveFolder by remember { mutableStateOf(prefs.getString("drive_folder", "Vehicle Expenses Photos") ?: "") }
    var saveFuelPhotos by remember { mutableStateOf(prefs.getBoolean("save_fuel_photos", false)) }
    var photoProviderPref by remember { mutableStateOf(prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive") }
    var ocrConfidenceThreshold by remember { mutableStateOf(prefs.getFloat("ocr_confidence_threshold", 0.75f)) }
    var darkModePref by remember { mutableStateOf(prefs.getString("dark_mode", "system") ?: "system") }

    var status by remember { mutableStateOf("Ready") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch { csvManager.exportToZip(); status = "Exported"; Toast.makeText(context, "CSV ZIP exported", Toast.LENGTH_LONG).show() } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { csvManager.importFromZip(uri); status = "Imported"; Toast.makeText(context, "CSV import complete", Toast.LENGTH_LONG).show() } }
    }

    LaunchedEffect(sheetId, syncEnabled, wifiOnly, chargingOnly, frequencyHours, driveFolder, saveFuelPhotos, photoProviderPref, ocrConfidenceThreshold, darkModePref) {
        prefs.edit().apply {
            putString("sheet_id", sheetId)
            putBoolean("sync_enabled", syncEnabled)
            putBoolean("wifi_only", wifiOnly)
            putBoolean("charging_only", chargingOnly)
            putInt("frequency_hours", frequencyHours)
            putString("drive_folder", driveFolder)
            putBoolean("save_fuel_photos", saveFuelPhotos)
            putString("photo_storage_provider", photoProviderPref)
            putFloat("ocr_confidence_threshold", ocrConfidenceThreshold)
            putString("dark_mode", darkModePref)
            apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = sheetId, onValueChange = { sheetId = it }, label = { Text("Google Sheet ID") }, modifier = Modifier.fillMaxWidth())
        SwitchSetting("Enable Background Sync", syncEnabled) { syncEnabled = it }
        SwitchSetting("Wi-Fi Only", wifiOnly) { wifiOnly = it }
        SwitchSetting("Charging Only", chargingOnly) { chargingOnly = it }
        SliderSetting("Sync Frequency (hours)", frequencyHours.toFloat(), 1f..24f) { frequencyHours = it.toInt() }
        SwitchSetting("Save Fuel Receipt Photos", saveFuelPhotos) { saveFuelPhotos = it }
        SliderSetting("OCR Confidence Threshold", ocrConfidenceThreshold, 0.5f..1.0f) { ocrConfidenceThreshold = it }

        Text("Photo Storage Provider", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = photoProviderPref == "google_drive", onClick = { photoProviderPref = "google_drive" })
            Text("Google Drive")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = photoProviderPref == "none", onClick = { photoProviderPref = "none" })
            Text("None")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = darkModePref == "system", onClick = { darkModePref = "system" })
            Text("System")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = darkModePref == "off", onClick = { darkModePref = "off" })
            Text("Light")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = darkModePref == "on", onClick = { darkModePref = "on" })
            Text("Dark")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { exportLauncher.launch("vehicle_expenses_backup.zip") }) { Text("Export to CSV (ZIP)") }
        Button(onClick = { importLauncher.launch("*/*") }) { Text("Import from CSV ZIP") }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { scope.launch { status = "Testing..."; val testUri = Uri.parse("content://com.davidlang.vehicleexpensesautomated.test/fake.jpg"); val url = photoStorageManager.savePhoto(testUri, "test.jpg", PhotoType.FUEL); status = if (url != null) "Upload test succeeded" else "Upload test failed" } }) { Text("Test Photo Upload") }
        Text(status, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Text("%.2f".format(value))
    }
}
