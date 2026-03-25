package com.davidlang.vehicleexpensesautomated.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Vehicle Expenses Automated",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Version v0.9.2")
        Spacer(modifier = Modifier.height(16.dp))
        Text("Built with camera-first OCR, automatic fuel/expense tracking, and Google Sheet sync.")
        Spacer(modifier = Modifier.height(24.dp))
        Text("© David Lang – All rights reserved")
    }
}
