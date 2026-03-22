package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
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

    // Auto-save whenever values change
    LaunchedEffect(sheetId) {
        prefs.edit().putString("sheet_id", sheetId).apply()
    }
    LaunchedEffect(syncEnabled) {
        prefs.edit().putBoolean("sync_enabled", syncEnabled).apply()
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
                label = { Text("Google Sheet ID or URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { 
                status = "Creating new Google Sheet... (placeholder)" 
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Create New Google Sheet")
            }
            
            Button(onClick = { 
                status = "Auto-checking sheet connection... (placeholder)" 
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to Sheet (auto-check)")
            }
            
            Button(onClick = { 
                status = if (syncEnabled && sheetId.isNotBlank()) "Two-way sync running... (placeholder)" 
                         else "Please enable sync and enter Sheet ID" 
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
