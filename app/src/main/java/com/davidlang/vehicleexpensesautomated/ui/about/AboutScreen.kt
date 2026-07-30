package com.davidlang.vehicleexpensesautomated.ui.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs
import com.davidlang.vehicleexpensesautomated.ui.util.buildFeedbackSeedBody

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME

    Scaffold(topBar = { TopAppBar(title = { Text("About") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Vehicle Expenses Automated", style = MaterialTheme.typography.headlineMedium)
            Text("Version $version", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text("Open Source Libraries", style = MaterialTheme.typography.titleMedium)
            Text("• Room\n• Hilt\n• Jetpack Compose\n• WorkManager\n• Google Sign-In\n• kotlinx-serialization")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated"))) }) { Text("GitHub Repository") }
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated/blob/master/LICENSE"))) }) { Text("License (Apache 2.0)") }
            Button(onClick = { UserManualDocs.openFullManual(context) }) { Text("User Manual") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(QuickFillDebugStore.REPORT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "Vehicle Expenses feedback")
                        putExtra(Intent.EXTRA_TEXT, buildFeedbackSeedBody(context))
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // Fallback for clients that only handle ACTION_SEND
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(QuickFillDebugStore.REPORT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "Vehicle Expenses feedback")
                            putExtra(Intent.EXTRA_TEXT, buildFeedbackSeedBody(context))
                        }
                        if (send.resolveActivity(context.packageManager) != null) {
                            context.startActivity(Intent.createChooser(send, "Send feedback"))
                        } else {
                            Toast.makeText(
                                context,
                                "No email app found to send feedback",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send bug report/feedback")
            }
        }
    }
}
