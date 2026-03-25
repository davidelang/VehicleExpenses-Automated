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
        Text("• Camera-first flow for new photos")
        Text("• Automatic OCR runs on every capture")
        Text("• Import old pictures from gallery")
        Text("• Hamburger menu accesses all screens")
        Spacer(modifier = Modifier.height(24.dp))
        Text("Full documentation: https://github.com/davidelang/VehicleExpenses-Automated/blob/master/user-manual.md")
    }
}
