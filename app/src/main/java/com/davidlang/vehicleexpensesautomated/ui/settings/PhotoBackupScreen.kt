package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

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
                        AppStrings.get(R.string.settings_maximum_1_s_photo_destinations, SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE),
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
    RegisterPageHelp(
        title = stringResource(R.string.nav_photo_backup),
        stringResource(R.string.settings_add_google_drive_or_other_destinations_sync_now_),
        stringResource(R.string.settings_open_a_destination_for_test_connection_and_sync_),
        stringResource(R.string.settings_leaving_the_screen_mid_sync_does_not_cancel_the_),
    )
    var statusText by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    val syncInProgress by viewModel.manualSyncInProgress.collectAsState()
    val vmStatus by viewModel.manualSyncStatus.collectAsState()
    val vmIsError by viewModel.manualSyncIsError.collectAsState()
    val syncResult by viewModel.manualSyncResult.collectAsState()
    val consentRecovery = rememberConsentRecoveryHandle()

    // Prefer ViewModel status while a ViewModel-scoped sync is active or just finished.
    val displayStatus = if (syncInProgress || vmStatus.isNotBlank()) vmStatus else statusText
    val displayIsError = if (syncInProgress || vmStatus.isNotBlank()) vmIsError else statusIsError

    LaunchedEffect(syncResult) {
        val result = syncResult ?: return@LaunchedEffect
        statusText = result.message
        statusIsError = !result.success
        if (result.needsRemoteConsent && result.recoveryIntent != null) {
            consentRecovery.launch(result.recoveryIntent) {
                viewModel.startManualSync("")
            }
        }
        viewModel.clearManualSyncResult()
    }

    SyncDestinationListLayout(
        title = stringResource(R.string.nav_photo_backup),
        description = stringResource(R.string.settings_add_a_destination_google_drive_is_common_s),
        statusText = displayStatus,
        syncInProgress = syncInProgress,
        statusIsError = displayIsError,
        destinationCount = destinations.size,
        maxDestinations = SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE,
        addButtonLabel = stringResource(R.string.settings_add_photo_destination),
        onAdd = onAdd,
        syncNowLabel = stringResource(R.string.settings_sync_now_all_configured),
        onSyncNow = {
            val configured = SyncDestinationStore(context).configuredPhoto()
            if (configured.isEmpty()) {
                statusIsError = true
                statusText = AppStrings.get(R.string.settings_no_configured_photo_destinations)
                return@SyncDestinationListLayout
            }
            viewModel.startManualSync("")
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
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { AppStrings.get(R.string.settings_onedrive) } ?: stringResource(R.string.settings_onedrive)
        dest.provider == PhotoProvider.S3 ->
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "S3" } ?: "S3"
        dest.provider == PhotoProvider.OTHER ->
            RcloneDestConfig.parse(dest.configJson)?.remote?.ifBlank { AppStrings.get(R.string.reports_other_category) } ?: stringResource(R.string.reports_other_category)
        dest.folderName.isNotBlank() -> dest.folderName
        else -> stringResource(R.string.settings_google_drive)
    }
    val account = when (dest.provider) {
        PhotoProvider.ONEDRIVE -> dest.accountHint.ifBlank { AppStrings.get(R.string.settings_microsoft_account) }
        PhotoProvider.S3 -> stringResource(R.string.settings_s3_storage)
        PhotoProvider.OTHER -> stringResource(R.string.settings_other_storage)
        else -> dest.accountHint.ifBlank { AppStrings.get(R.string.settings_no_account) }
    }
    val enabledLabel = if (dest.enabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off)
    val configured = SyncDestinationStore.isPhotoConfigured(dest, context)
    val detailLines = buildList {
        add(AppStrings.get(R.string.settings_account_1_s, account))
        add(if (configured) AppStrings.get(R.string.settings_enabled_1_s, enabledLabel) else AppStrings.get(R.string.settings_not_configured))
        if (configured) {
            add(SyncDestinationStore(context).photoSummaryLine(dest))
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
        title = stringResource(R.string.settings_add_photo_destination),
        choices = listOf(
            stringResource(R.string.settings_google_drive) to { onPick(PhotoProvider.GOOGLE_DRIVE) },
            stringResource(R.string.settings_onedrive) to { onPick(PhotoProvider.ONEDRIVE) },
            "S3" to { onPick(PhotoProvider.S3) },
            stringResource(R.string.reports_other_category) to { onPick(PhotoProvider.OTHER) },
        ),
        onCancel = onCancel,
    )
}