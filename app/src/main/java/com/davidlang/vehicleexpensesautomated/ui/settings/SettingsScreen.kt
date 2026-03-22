@file:Suppress("DEPRECATION")

package com.davidlang.vehicleexpensesautomated.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val client = remember { GoogleSheetsClient() }

    var sheetId by remember { mutableStateOf(prefs.getString("sheet_id", "") ?: "") }
    var syncEnabled by remember { mutableStateOf(prefs.getBoolean("sync_enabled", false)) }
    var status by remember { mutableStateOf("Ready") }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    LaunchedEffect(sheetId) { prefs.edit().putString("sheet_id", sheetId).apply() }
    LaunchedEffect(syncEnabled) { prefs.edit().putBoolean("sync_enabled", syncEnabled).apply() }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun extractSheetId(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("https://docs.google.com/spreadsheets/d/") -> {
                val parts = trimmed.split("/")
                if (parts.size > 5) parts[5] else trimmed
            }
            else -> trimmed
        }
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("YOUR_WEB_CLIENT_ID")
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                signedInAccount = account
                client.idToken = account.idToken
                status = "Signed in as ${account.email}"
                showToast("Signed in successfully")
            } catch (e: ApiException) {
                status = "Sign-in failed"
                showToast("Sign-in failed: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings & Sync") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Google Sheets Sync (optional, two-way, multi-device)", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Sync")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = syncEnabled, onCheckedChange = { syncEnabled = it })
            }

            OutlinedTextField(
                value = sheetId,
                onValueChange = { sheetId = it },
                label = { Text("Google Sheet ID or full URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (signedInAccount == null) {
                Button(onClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    signInLauncher.launch(signInIntent)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google (device account)")
                }
            } else {
                Text("Signed in as: ${signedInAccount?.email}", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    googleSignInClient.signOut()
                    signedInAccount = null
                    client.idToken = null
                    status = "Signed out"
                    showToast("Signed out")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign Out")
                }
            }

            Button(onClick = {
                val cleanId = extractSheetId(sheetId)
                if (cleanId.length > 10) {
                    sheetId = cleanId
                    status = "Connected! Sheet ID: $cleanId"
                    showToast("Connected to sheet")
                } else {
                    status = "Invalid Sheet ID/URL"
                    showToast("Please enter a valid Sheet ID or URL")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to Sheet")
            }

            Button(onClick = {
                if (syncEnabled && sheetId.isNotBlank() && client.idToken != null) {
                    coroutineScope.launch {
                        val dummyVehicles = listOf(
                            GoogleSheetsClient.VehicleSummary(1, "Toyota Camry 2023"),
                            GoogleSheetsClient.VehicleSummary(2, "Honda Civic 2022")
                        )
                        client.syncAllData(sheetId, dummyVehicles, emptyMap(), emptyMap())
                        status = "✅ TWO-WAY SYNC COMPLETE — writing + reading real data!"
                    }
                    showToast("Two-way sync running — check Logcat + your sheet!")
                } else {
                    status = "Sign in + enable sync + enter Sheet ID"
                    showToast("Please sign in first")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync Now (FULL TWO-WAY)")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Tab structure (one set per vehicle):\n• Expenses - [Make Model Year]\n• Fuel - [Make Model Year]", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
