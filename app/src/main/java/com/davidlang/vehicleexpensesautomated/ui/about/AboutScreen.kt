package com.davidlang.vehicleexpensesautomated.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.BuildConfig

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("About") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Vehicle Expenses Automated", style = MaterialTheme.typography.headlineMedium)
            Text("Version ${BuildConfig.GIT_VERSION}", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(32.dp))

            Text("Open Source Libraries", style = MaterialTheme.typography.titleMedium)
            Text("• Room\n• Hilt\n• Compose\n• WorkManager\n• OkHttp (via system)\n• kotlinx-serialization")

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated")))
            }) {
                Text("GitHub Repository")
            }

            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated/blob/master/PRIVACY.md")))
            }) {
                Text("Privacy Policy")
            }
        }
    }
}
