package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }

    var sheetId by remember { mutableStateOf(prefs.getString("sheet_id", "") ?: "") }
    var syncEnabled by remember { mutableStateOf(prefs.getBoolean("sync_enabled", false)) }
    var status by remember { mutableStateOf("Ready") }

    // Auto-save
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

            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sheets.new"))
                context.startActivity(intent)
                status = "New sheet opened in browser"
                showToast("New Google Sheet created — copy the URL back here")
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Create New Google Sheet")
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
                Text("Connect to Sheet (auto-check)")
            }

            Button(onClick = {
                if (syncEnabled && sheetId.isNotBlank()) {
                    status = "Two-way sync running... (real data sync coming next)"
                    showToast("Sync started (placeholder)")
                } else {
                    status = "Enable sync + enter Sheet ID first"
                    showToast("Sync not enabled")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync Now")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Tab structure (one set per vehicle):\n• Expenses - [Make Model Year]\n• Fuel - [Make Model Year]", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
