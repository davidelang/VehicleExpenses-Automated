package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SyncingScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val syncStore = remember { SyncDestinationStore(context) }
    val failureStore = remember { SyncFailureStore(context) }
    var pendingBadge by remember { mutableStateOf(syncStore.pendingBadgeText()) }
    var spreadsheetError by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry = navController.currentBackStackEntry
    val destinations = remember(navBackStackEntry) { syncStore.load() }

    LaunchedEffect(navBackStackEntry) {
        pendingBadge = syncStore.pendingBadgeText()
        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
        photoError = failureStore.photoFailureSummary(syncStore)
        withContext(Dispatchers.IO) {
            viewModel.recountPendingBadge()
        }
        pendingBadge = syncStore.pendingBadgeText()
    }
    val spreadsheetDests = destinations.spreadsheet
    val photoDests = destinations.photo
    val spreadsheetConfigured = syncStore.configuredSpreadsheet().isNotEmpty()
    val photoConfigured = syncStore.configuredPhoto().isNotEmpty()

    var spreadsheetSyncStatus by remember { mutableStateOf("") }
    var photoSyncStatus by remember { mutableStateOf("") }
    var spreadsheetSyncInProgress by remember { mutableStateOf(false) }
    var photoSyncInProgress by remember { mutableStateOf(false) }
    var spreadsheetSyncIsError by remember { mutableStateOf(false) }
    var photoSyncIsError by remember { mutableStateOf(false) }
    val spreadsheetProgress = rememberMainThreadSyncProgress {
        spreadsheetSyncStatus = it
        spreadsheetSyncIsError = false
    }
    val photoProgress = rememberMainThreadSyncProgress {
        photoSyncStatus = it
        photoSyncIsError = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Syncing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap Spreadsheet sync or Photo backup to add destinations (e.g. Google Sheets + Google Drive). " +
                "Use Sync now on each row after setup. A red ! in the title bar means a recent failure — open this screen. " +
                "Menu → Help for Google setup steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Sync & backup", style = MaterialTheme.typography.titleMedium)
        SyncSummaryRow(
            title = "Spreadsheet sync",
            summary = SyncDestinationStore.spreadsheetSummaryLine(spreadsheetDests),
            pendingBadge = pendingBadge,
            errorText = spreadsheetError,
            syncStatusText = spreadsheetSyncStatus,
            syncInProgress = spreadsheetSyncInProgress,
            syncStatusIsError = spreadsheetSyncIsError,
            showSyncNow = spreadsheetConfigured,
            onRowClick = { navController.navigate("settings/spreadsheet_sync") },
            onSyncNow = {
                scope.launch {
                    spreadsheetSyncInProgress = true
                    spreadsheetSyncIsError = false
                    spreadsheetSyncStatus = "Starting spreadsheet sync…"
                    try {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncSpreadsheet(spreadsheetProgress)
                        }
                        spreadsheetSyncStatus = result.message
                        spreadsheetSyncIsError = !result.success
                        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
                    } catch (e: Exception) {
                        spreadsheetSyncIsError = true
                        spreadsheetSyncStatus = e.message ?: "Sync failed"
                        Toast.makeText(context, spreadsheetSyncStatus, Toast.LENGTH_LONG).show()
                    } finally {
                        spreadsheetSyncInProgress = false
                    }
                }
            },
        )
        SyncSummaryRow(
            title = "Photo backup",
            summary = syncStore.photoSummaryLine(photoDests),
            pendingBadge = pendingBadge,
            errorText = photoError,
            syncStatusText = photoSyncStatus,
            syncInProgress = photoSyncInProgress,
            syncStatusIsError = photoSyncIsError,
            showSyncNow = photoConfigured,
            onRowClick = { navController.navigate("settings/photo_backup") },
            onSyncNow = {
                scope.launch {
                    photoSyncInProgress = true
                    photoSyncIsError = false
                    photoSyncStatus = "Starting photo backup…"
                    try {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncPhotoBackup(photoProgress)
                        }
                        photoSyncStatus = result.message
                        photoSyncIsError = !result.success
                        pendingBadge = syncStore.pendingBadgeText()
                        photoError = failureStore.photoFailureSummary(syncStore)
                    } catch (e: Exception) {
                        photoSyncIsError = true
                        photoSyncStatus = e.message ?: "Photo sync failed"
                        Toast.makeText(context, photoSyncStatus, Toast.LENGTH_LONG).show()
                    } finally {
                        photoSyncInProgress = false
                    }
                }
            },
        )
    }
}

@Composable
internal fun SyncSummaryRow(
    title: String,
    summary: String,
    pendingBadge: String,
    errorText: String? = null,
    syncStatusText: String = "",
    syncInProgress: Boolean = false,
    syncStatusIsError: Boolean = false,
    showSyncNow: Boolean,
    onRowClick: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onRowClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall)
                Text(
                    pendingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (syncStatusText.isNotBlank() || syncInProgress) {
                    SyncStatusDisplay(
                        statusText = syncStatusText,
                        syncInProgress = syncInProgress,
                        isError = syncStatusIsError,
                        textStyle = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (showSyncNow) {
                TextButton(
                    onClick = onSyncNow,
                    enabled = !syncInProgress,
                ) {
                    Text("Sync")
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
