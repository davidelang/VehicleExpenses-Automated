package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.DriveRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoDestination
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoProvider
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneDestConfig
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoBackupScreen(
    navController: NavHostController,
    viewModel: PhotoBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val store = remember { SyncDestinationStore(context) }
    var destinations by remember { mutableStateOf(store.allPhoto()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pickingProvider by remember { mutableStateOf(false) }

    fun refreshList() {
        destinations = store.allPhoto()
    }

    when {
        pickingProvider -> PhotoProviderPicker(
            onPick = { provider ->
                pickingProvider = false
                editingId = when (provider) {
                    PhotoProvider.OTHER -> "new:other"
                    PhotoProvider.ONEDRIVE -> "new:onedrive"
                    PhotoProvider.S3 -> "new:s3"
                    else -> "new:drive"
                }
            },
            onCancel = { pickingProvider = false },
        )
        editingId == null -> PhotoDestList(
            destinations = destinations,
            onAdd = {
                if (destinations.size >= SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE) {
                    Toast.makeText(
                        context,
                        "Maximum ${SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE} photo destinations",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pickingProvider = true
                }
            },
            onEdit = { editingId = it },
            viewModel = viewModel,
        )
        else -> PhotoDestEditForm(
            destId = editingId!!,
            totalDestCount = destinations.size + if (editingId!!.startsWith("new")) 1 else 0,
            store = store,
            viewModel = viewModel,
            onBack = {
                editingId = null
                refreshList()
            },
            onRemoved = {
                editingId = null
                refreshList()
            },
        )
    }
}

@Composable
private fun PhotoDestList(
    destinations: List<PhotoDestination>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: PhotoBackupViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    val consentRecovery = rememberConsentRecoveryHandle()

    SyncDestinationListLayout(
        title = "Photo Backup",
        description = "Configure Google Drive, OneDrive, S3, or Other storage. Manual and background backup run all enabled destinations.",
        statusText = statusText,
        destinationCount = destinations.size,
        maxDestinations = SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE,
        addButtonLabel = "Add photo destination",
        onAdd = onAdd,
        syncNowLabel = "Sync now (all enabled)",
        onSyncNow = {
            fun runSync(allowRecovery: Boolean) {
                scope.launch {
                    val enabled = SyncDestinationStore(context).enabledPhoto()
                    if (enabled.isEmpty()) {
                        Toast.makeText(context, "No enabled photo destinations", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    try {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncNow("")
                        }
                        statusText = result.message
                        if (result.success) {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        } else if (result.needsRemoteConsent && result.recoveryIntent != null && allowRecovery) {
                            consentRecovery.launch(result.recoveryIntent) { runSync(allowRecovery = false) }
                        } else {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: DriveRecoverableAuthException) {
                        statusText = e.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                        if (allowRecovery) {
                            consentRecovery.launch(e.recoveryIntent) { runSync(allowRecovery = false) }
                        } else {
                            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        statusText = DriveAuthRecovery.userMessage(e)
                        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            runSync(allowRecovery = true)
        },
        listContent = {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(destinations, key = { it.id }) { dest ->
                    PhotoDestCard(dest = dest, onClick = { onEdit(dest.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
    )
}

@Composable
private fun PhotoDestCard(
    dest: PhotoDestination,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val name = when {
        dest.displayName.isNotBlank() -> dest.displayName
        dest.provider == PhotoProvider.ONEDRIVE ->
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "OneDrive" } ?: "OneDrive"
        dest.provider == PhotoProvider.S3 ->
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "S3" } ?: "S3"
        dest.provider == PhotoProvider.OTHER ->
            RcloneDestConfig.parse(dest.configJson)?.remote?.ifBlank { "Other" } ?: "Other"
        dest.folderName.isNotBlank() -> dest.folderName
        else -> "Google Drive"
    }
    val account = when (dest.provider) {
        PhotoProvider.ONEDRIVE -> dest.accountHint.ifBlank { "Microsoft account" }
        PhotoProvider.S3 -> "S3 storage"
        PhotoProvider.OTHER -> "Other storage"
        else -> dest.accountHint.ifBlank { "No account" }
    }
    val enabledLabel = if (dest.enabled) "On" else "Off"
    val configured = SyncDestinationStore.isPhotoConfigured(dest, context)
    val detailLines = buildList {
        add("Account: $account")
        add(if (configured) "Enabled: $enabledLabel" else "Not configured")
        if (configured) {
            add(SyncDestinationStore.photoSummaryLine(dest))
        }
    }
    SyncDestinationSummaryCard(
        title = name,
        detailLines = detailLines,
        onClick = onClick,
    )
}

@Composable
private fun PhotoProviderPicker(
    onPick: (PhotoProvider) -> Unit,
    onCancel: () -> Unit,
) {
    SyncProviderChoiceScreen(
        title = "Add photo destination",
        choices = listOf(
            "Google Drive" to { onPick(PhotoProvider.GOOGLE_DRIVE) },
            "OneDrive" to { onPick(PhotoProvider.ONEDRIVE) },
            "S3" to { onPick(PhotoProvider.S3) },
            "Other" to { onPick(PhotoProvider.OTHER) },
        ),
        onCancel = onCancel,
    )
}