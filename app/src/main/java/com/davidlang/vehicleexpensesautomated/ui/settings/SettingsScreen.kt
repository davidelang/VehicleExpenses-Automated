package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var sheetId by remember { mutableStateOf("") }
    var syncEnabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not connected") }

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

            Button(onClick = { status = "Creating new sheet..." }, modifier = Modifier.fillMaxWidth()) {
                Text("Create New Google Sheet")
            }
            Button(onClick = { status = "Auto-checking sheet..." }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to Sheet (auto-check)")
            }
            Button(onClick = { status = "Two-way sync running..." }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync Now")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Tab structure (one set per vehicle):\n• Expenses - [Make Model Year]\n• Fuel - [Make Model Year]", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
