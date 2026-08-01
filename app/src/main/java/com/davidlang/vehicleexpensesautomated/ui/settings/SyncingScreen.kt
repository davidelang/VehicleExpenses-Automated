package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
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
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncingScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()
    RegisterPageHelp(
        title = "Syncing",
        "Spreadsheet sync and Photo backup open destination lists. Sync on each card runs all configured destinations.",
        "Red ! in the title bar means a stored failure — open Details on the card for the full API message.",
        "Leaving this screen during Sync now does not cancel the job (it continues in the background).",
    )
    val syncStore = remember { SyncDestinationStore(context) }
    val failureStore = remember { SyncFailureStore(context) }
    var pendingBadge by remember { mutableStateOf(syncStore.pendingBadgeText()) }
    var spreadsheetError by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var spreadsheetErrorDetails by remember { mutableStateOf<String?>(null) }
    var photoErrorDetails by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry = navController.currentBackStackEntry
    val destinations = remember(navBackStackEntry) { syncStore.load() }

    val spreadsheetSyncStatus by viewModel.spreadsheetSyncStatus.collectAsState()
    val spreadsheetSyncInProgress by viewModel.spreadsheetSyncInProgress.collectAsState()
    val spreadsheetSyncIsError by viewModel.spreadsheetSyncIsError.collectAsState()
    val spreadsheetSyncResult by viewModel.spreadsheetSyncResult.collectAsState()
    val photoSyncStatus by viewModel.photoSyncStatus.collectAsState()
    val photoSyncInProgress by viewModel.photoSyncInProgress.collectAsState()
    val photoSyncIsError by viewModel.photoSyncIsError.collectAsState()
    val photoSyncResult by viewModel.photoSyncResult.collectAsState()

    LaunchedEffect(navBackStackEntry) {
        pendingBadge = syncStore.pendingBadgeText()
        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
        photoError = failureStore.photoFailureSummary(syncStore)
        spreadsheetErrorDetails = failureStore.spreadsheetFailureDetails(syncStore)
        photoErrorDetails = failureStore.photoFailureDetails(syncStore)
        withContext(Dispatchers.IO) {
            viewModel.recountPendingBadge()
        }
        pendingBadge = syncStore.pendingBadgeText()
    }

    // Refresh failure lines when a ViewModel-scoped sync finishes
    LaunchedEffect(spreadsheetSyncResult) {
        val result = spreadsheetSyncResult ?: return@LaunchedEffect
        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
        spreadsheetErrorDetails = failureStore.spreadsheetFailureDetails(syncStore)
        if (!result.success) {
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
        viewModel.clearSpreadsheetSyncResult()
    }
    LaunchedEffect(photoSyncResult) {
        val result = photoSyncResult ?: return@LaunchedEffect
        pendingBadge = syncStore.pendingBadgeText()
        photoError = failureStore.photoFailureSummary(syncStore)
        photoErrorDetails = failureStore.photoFailureDetails(syncStore)
        if (!result.success) {
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
        viewModel.clearPhotoSyncResult()
    }

    val spreadsheetDests = destinations.spreadsheet
    val photoDests = destinations.photo
    val spreadsheetConfigured = syncStore.configuredSpreadsheet().isNotEmpty()
    val photoConfigured = syncStore.configuredPhoto().isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        FeatureScreenHeader(
            title = "Syncing",
            subtitle = "Tap Spreadsheet sync or Photo backup to add destinations. " +
                "Use Sync on each card after setup. A red ! in the title bar means a recent failure. " +
                "Menu → Help for Google setup steps.",
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Sync & backup", style = MaterialTheme.typography.titleMedium)
        SyncSummaryRow(
            title = "Spreadsheet sync",
            summary = SyncDestinationStore.spreadsheetSummaryLine(spreadsheetDests),
            pendingBadge = pendingBadge,
            errorText = spreadsheetError,
            errorDetails = spreadsheetErrorDetails,
            errorDetailsTitle = "Spreadsheet sync failure",
            syncStatusText = spreadsheetSyncStatus,
            syncInProgress = spreadsheetSyncInProgress,
            syncStatusIsError = spreadsheetSyncIsError,
            showSyncNow = spreadsheetConfigured,
            onRowClick = { navController.navigate("settings/spreadsheet_sync") },
            onSyncNow = {
                if (!spreadsheetConfigured) {
                    Toast.makeText(context, "No configured spreadsheet destinations", Toast.LENGTH_SHORT).show()
                    return@SyncSummaryRow
                }
                viewModel.startSpreadsheetSync()
            },
        )
        SyncSummaryRow(
            title = "Photo backup",
            summary = syncStore.photoSummaryLine(photoDests),
            pendingBadge = pendingBadge,
            errorText = photoError,
            errorDetails = photoErrorDetails,
            errorDetailsTitle = "Photo backup failure",
            syncStatusText = photoSyncStatus,
            syncInProgress = photoSyncInProgress,
            syncStatusIsError = photoSyncIsError,
            showSyncNow = photoConfigured,
            onRowClick = { navController.navigate("settings/photo_backup") },
            onSyncNow = {
                if (!photoConfigured) {
                    Toast.makeText(context, "No configured photo destinations", Toast.LENGTH_SHORT).show()
                    return@SyncSummaryRow
                }
                viewModel.startPhotoSync()
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
    errorDetails: String? = null,
    errorDetailsTitle: String = "Sync failure",
    syncStatusText: String = "",
    syncInProgress: Boolean = false,
    syncStatusIsError: Boolean = false,
    showSyncNow: Boolean,
    onRowClick: () -> Unit,
    onSyncNow: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    TappableCard(
        onClick = onRowClick,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, softWrap = true)
                Text(summary, style = MaterialTheme.typography.bodySmall, softWrap = true)
                Text(
                    pendingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!errorText.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            errorText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            softWrap = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!errorDetails.isNullOrBlank()) {
                            TextButton(
                                onClick = { showDetails = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("Details", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
    if (showDetails && !errorDetails.isNullOrBlank()) {
        SyncFailureDetailsDialog(
            title = errorDetailsTitle,
            detailMessage = errorDetails,
            onDismiss = { showDetails = false },
        )
    }
}
