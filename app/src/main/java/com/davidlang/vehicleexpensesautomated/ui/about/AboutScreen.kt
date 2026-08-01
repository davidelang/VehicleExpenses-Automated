package com.davidlang.vehicleexpensesautomated.ui.about

import com.davidlang.vehicleexpensesautomated.R

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs
import com.davidlang.vehicleexpensesautomated.ui.util.buildFeedbackSeedBody

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_about)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.app_name_long), style = MaterialTheme.typography.headlineMedium)
            Text("Version $version", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text(stringResource(R.string.about_open_source_libraries), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_room_hilt_jetpack_compose_workmanager_google_sig))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated"))) }) { Text(stringResource(R.string.about_github_repository)) }
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/davidelang/VehicleExpenses-Automated/blob/master/LICENSE"))) }) { Text(stringResource(R.string.about_license_apache_2_0)) }
            Button(onClick = { UserManualDocs.openFullManual(context) }) { Text(stringResource(R.string.user_manual_title)) }
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
                            Toast.makeText(context, context.getString(R.string.about_no_email_app_found_to_send_feedback),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_send_bug_report_feedback))
            }
        }
    }
}
