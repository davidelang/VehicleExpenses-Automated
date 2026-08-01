package com.davidlang.vehicleexpensesautomated.ui.settings

import com.davidlang.vehicleexpensesautomated.R

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherKind
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderInfo
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SpreadsheetOtherKindPicker(
    onPick: (TabularOtherKind) -> Unit,
    onCancel: () -> Unit,
) {
    SyncProviderChoiceScreen(
        title = stringResource(R.string.settings_other_pick_category),
        choices = TabularOtherProviderCatalog.KIND_GROUPS.map { kind ->
            kind.label to { onPick(kind) }
        },
        onCancel = onCancel,
    )
}

@Composable
internal fun SpreadsheetOtherProviderPicker(
    kind: TabularOtherKind,
    onPick: (TabularOtherProviderInfo) -> Unit,
    onCancel: () -> Unit,
) {
    val providers = TabularOtherProviderCatalog.providersForKind(kind.id)
    SyncProviderChoiceScreen(
        title = kind.label,
        choices = providers.map { info ->
            (if (info.implemented) info.label else "${info.label} (coming soon)") to { onPick(info) }
        },
        onCancel = onCancel,
    )
}

@Composable
internal fun SpreadsheetProviderPicker(
    onPick: (SpreadsheetProvider) -> Unit,
    onCancel: () -> Unit,
) {
    SyncProviderChoiceScreen(
        title = stringResource(R.string.settings_add_spreadsheet_destination),
        choices = listOf(
            "Google Sheets" to { onPick(SpreadsheetProvider.GOOGLE_SHEETS) },
            "Excel" to { onPick(SpreadsheetProvider.EXCEL) },
            "EtherCalc" to { onPick(SpreadsheetProvider.ETHERCALC) },
            "Other" to { onPick(SpreadsheetProvider.OTHER) },
        ),
        onCancel = onCancel,
    )
}

@Composable
internal fun SpreadsheetDestList(
    destinations: List<SpreadsheetDestination>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SpreadsheetSyncViewModel,
) {
    val context = LocalContext.current
    RegisterPageHelp(
        title = stringResource(R.string.nav_spreadsheet_sync),
        stringResource(R.string.settings_add_destinations_google_sheets_is_common_sync_no),
        stringResource(R.string.settings_open_a_destination_for_test_connection_sync_now_),
        stringResource(R.string.settings_background_sync_uses_destinations_with_backgroun),
    )
    var statusText by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    val syncInProgress by viewModel.manualSyncInProgress.collectAsState()
    val vmStatus by viewModel.manualSyncStatus.collectAsState()
    val vmIsError by viewModel.manualSyncIsError.collectAsState()
    val syncResult by viewModel.manualSyncResult.collectAsState()
    val consentRecovery = rememberConsentRecoveryHandle()

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
            viewModel.clearManualSyncResult()
            return@LaunchedEffect
        }
        if (result.success) {
            val unmatched = withContext(Dispatchers.IO) {
                viewModel.hasUnmatchedFuelPartials()
            }
            if (unmatched) {
                Toast.makeText(context, context.getString(R.string.settings_unmatched_partials_may_need_run_merge_import_old),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        viewModel.clearManualSyncResult()
    }

    SyncDestinationListLayout(
        title = stringResource(R.string.nav_spreadsheet_sync),
        description = "Add a destination (Google Sheets is the common choice: Sign in → Sheet URL or 🔍 browse → save → Sync now). Manual sync runs all configured destinations; background sync runs enabled ones only.",
        statusText = displayStatus,
        syncInProgress = syncInProgress,
        statusIsError = displayIsError,
        destinationCount = destinations.size,
        maxDestinations = SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE,
        addButtonLabel = "Add spreadsheet destination",
        onAdd = onAdd,
        syncNowLabel = "Sync now (all configured)",
        onSyncNow = {
            val configured = SyncDestinationStore(context).configuredSpreadsheet()
            if (configured.isEmpty()) {
                statusIsError = true
                statusText = "No configured spreadsheet destinations"
                return@SyncDestinationListLayout
            }
            viewModel.startManualSync("")
        },
        docLinkLabel = "Self-hosted spreadsheet servers",
        onDocLinkClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabularReadme()) },
        listContent = {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(destinations, key = { it.id }) { dest ->
                    SpreadsheetDestCard(dest = dest, onClick = { onEdit(dest.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
    )
}

@Composable
internal fun SpreadsheetDestCard(
    dest: SpreadsheetDestination,
    onClick: () -> Unit,
) {
    val name = when {
        dest.displayName.isNotBlank() -> dest.displayName
        dest.targetId.isNotBlank() -> dest.targetId.take(12) + if (dest.targetId.length > 12) "…" else ""
        else -> dest.provider.displayLabel()
    }
    val account = dest.accountHint.ifBlank {
        if (dest.provider == SpreadsheetProvider.ETHERCALC) "No account required" else "No account"
    }
    val enabledLabel = if (dest.enabled) "On" else "Off"
    val configured = SyncDestinationStore.isSpreadsheetConfigured(dest)
    val detailLines = buildList {
        add("Account: $account")
        add(if (configured) "Enabled: $enabledLabel" else "Not configured")
        if (configured) {
            add(SyncDestinationStore.spreadsheetSummaryLine(dest))
        }
    }
    SyncDestinationSummaryCard(
        title = name,
        subtitle = dest.provider.displayLabel(),
        detailLines = detailLines,
        onClick = onClick,
    )
}