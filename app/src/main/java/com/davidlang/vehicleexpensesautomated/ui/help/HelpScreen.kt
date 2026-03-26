package com.davidlang.vehicleexpensesautomated.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun HelpScreen(navController: NavHostController? = null) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("Help / User Manual", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Adding a fill-up (recommended workflow)")
        Text("• Open the app — the camera starts automatically")
        Text("• Point at your dashboard and tap 'Take Dash Picture'")
        Text("• The app reads the odometer automatically")
        Text("• If this is a missed fill (unknown gas added), check the box — no second picture needed")
        Text("• Otherwise point at the pump and tap 'Take Pump Picture'")
        Text("• The app reads gallons and cost automatically")
        Text("• Data is saved and you return to the Reports screen")

        Spacer(modifier = Modifier.height(24.dp))

        Text("Adding an expense")
        Text("• Select the vehicle")
        Text("• Take a picture of the receipt")
        Text("• Data is saved automatically")

        Spacer(modifier = Modifier.height(24.dp))

        Text("Advanced mode (for testing)")
        Text("• On each step you will see an extra button to pick an existing picture instead of taking a new one")

        Spacer(modifier = Modifier.height(24.dp))

        Text("Data backup and sharing")
        Text("• All photos and data can be backed up to Google Drive")
        Text("• Everything can be exported/imported via CSV ZIP")
        Text("• Full bidirectional sync with Google Sheets is available in Settings")

        Spacer(modifier = Modifier.height(24.dp))

        Text("Full documentation and screenshots:")
        Text("https://github.com/davidelang/VehicleExpenses-Automated/blob/master/docs/user-manual.md")
    }
}
