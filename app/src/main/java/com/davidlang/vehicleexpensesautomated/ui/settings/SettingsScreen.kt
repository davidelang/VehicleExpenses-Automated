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

    val currentProvider: PhotoStorageProvider = remember(photoProviderPref, signedInAccount) {
        when (photoProviderPref) {
            "google_drive" -> GoogleDriveProvider(null)
            else -> NoOpStorageProvider()
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

        if (syncEnabled && sheetId.isNotBlank()) {
            schedulePeriodicSync(context, wifiOnly, chargingOnly, frequencyHours)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("vehicle_sync")
        }
    }

    fun showToast(message: String) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("YOUR_WEB_CLIENT_ID") // ← replace later
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"), Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                signedInAccount = account
                status = "Signed in as ${account.email}"
                showToast("Signed in successfully")
            } catch (e: ApiException) {
                status = "Sign-in failed"
                showToast("Sign-in failed")
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings & Sync") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Google Sheets Sync", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Sync")
                Spacer(Modifier.width(8.dp))
                Switch(checked = syncEnabled, onCheckedChange = { syncEnabled = it })
            }

            OutlinedTextField(value = sheetId, onValueChange = { sheetId = it }, label = { Text("Sheet ID or URL") }, modifier = Modifier.fillMaxWidth())

            if (signedInAccount == null) {
                Button(onClick = { signInLauncher.launch(googleSignInClient.signInIntent) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            } else {
                Text("Signed in as ${signedInAccount?.email}")
                Button(onClick = { /* sign out logic */ }, modifier = Modifier.fillMaxWidth()) { Text("Sign Out") }
            }

            Spacer(Modifier.height(24.dp))

            // === PHOTO STORAGE (exactly what you asked for) ===
            Text("Photo Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("Storage Provider")
            Row {
                listOf("Google Drive" to "google_drive", "None" to "none").forEach { (label, key) ->
                    FilterChip(
                        selected = photoProviderPref == key,
                        onClick = { photoProviderPref = key },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            OutlinedTextField(value = driveFolder, onValueChange = { driveFolder = it }, label = { Text("Drive Folder Name") }, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Save fuel photos to archive")
                Spacer(Modifier.width(8.dp))
                Switch(checked = saveFuelPhotos, onCheckedChange = { saveFuelPhotos = it })
            }
            Text("Expense photos are ALWAYS archived (fuel optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)

            Spacer(Modifier.height(32.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun schedulePeriodicSync(context: Context, wifiOnly: Boolean, chargingOnly: Boolean, frequencyHours: Int) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(chargingOnly)
        .build()

    val request = PeriodicWorkRequestBuilder<SyncWorker>(frequencyHours.toLong(), TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "vehicle_sync",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
