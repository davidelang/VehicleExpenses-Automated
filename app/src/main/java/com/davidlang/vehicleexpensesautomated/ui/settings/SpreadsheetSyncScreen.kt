package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.activity.ComponentActivity
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherKind
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderInfo
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.FirebaseTabularConfig
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetConfig
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpreadsheetSyncScreen(
    navController: NavHostController,
    viewModel: SpreadsheetSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val store = remember { SyncDestinationStore(context) }
    var destinations by remember { mutableStateOf(store.allSpreadsheet()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pickingProvider by remember { mutableStateOf(false) }
    var pickingOtherKind by remember { mutableStateOf(false) }
    var pickingOtherProvider by remember { mutableStateOf<TabularOtherKind?>(null) }

    fun refreshList() {
        destinations = store.allSpreadsheet()
    }

    when {
        pickingOtherProvider != null -> SpreadsheetOtherProviderPicker(
            kind = pickingOtherProvider!!,
            onPick = { info ->
                pickingOtherProvider = null
                if (info.implemented) {
                    editingId = TabularOtherProviderCatalog.newDestIdFor(info.provider)
                } else {
                    Toast.makeText(
                        context,
                        "${info.label} is not yet implemented",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onCancel = { pickingOtherProvider = null },
        )
        pickingOtherKind -> SpreadsheetOtherKindPicker(
            onPick = { kind ->
                pickingOtherKind = false
                pickingOtherProvider = kind
            },
            onCancel = { pickingOtherKind = false },
        )
        pickingProvider -> SpreadsheetProviderPicker(
            onPick = { provider ->
                pickingProvider = false
                if (provider == SpreadsheetProvider.OTHER) {
                    pickingOtherKind = true
                } else {
                    editingId = when (provider) {
                        SpreadsheetProvider.EXCEL -> "new:excel"
                        SpreadsheetProvider.ETHERCALC -> "new:ethercalc"
                        else -> "new:sheets"
                    }
                }
            },
            onCancel = { pickingProvider = false },
        )
        editingId == null -> SpreadsheetDestList(
            destinations = destinations,
            onAdd = {
                if (destinations.size >= SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE) {
                    Toast.makeText(
                        context,
                        "Maximum ${SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE} spreadsheet destinations",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pickingProvider = true
                }
            },
            onEdit = { editingId = it },
            viewModel = viewModel,
        )
        else -> SpreadsheetDestEditForm(
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
private fun SpreadsheetOtherKindPicker(
    onPick: (TabularOtherKind) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Other — pick category", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        TabularOtherProviderCatalog.KIND_GROUPS.forEach { kind ->
            Button(onClick = { onPick(kind) }, modifier = Modifier.fillMaxWidth()) {
                Text(kind.label)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SpreadsheetOtherProviderPicker(
    kind: TabularOtherKind,
    onPick: (TabularOtherProviderInfo) -> Unit,
    onCancel: () -> Unit,
) {
    val providers = TabularOtherProviderCatalog.providersForKind(kind.id)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(kind.label, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        providers.forEach { info ->
            Button(onClick = { onPick(info) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (info.implemented) info.label else "${info.label} (coming soon)")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SpreadsheetProviderPicker(
    onPick: (SpreadsheetProvider) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Add spreadsheet destination", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onPick(SpreadsheetProvider.GOOGLE_SHEETS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Google Sheets")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(SpreadsheetProvider.EXCEL) }, modifier = Modifier.fillMaxWidth()) {
            Text("Excel")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(SpreadsheetProvider.ETHERCALC) }, modifier = Modifier.fillMaxWidth()) {
            Text("EtherCalc")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(SpreadsheetProvider.OTHER) }, modifier = Modifier.fillMaxWidth()) {
            Text("Other")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SpreadsheetDestList(
    destinations: List<SpreadsheetDestination>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SpreadsheetSyncViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    var pendingRecoveryRetry by remember { mutableStateOf<(() -> Unit)?>(null) }

    val consentRecoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        val retry = pendingRecoveryRetry
        pendingRecoveryRetry = null
        retry?.invoke()
    }

    fun launchConsentRecovery(intent: Intent, retry: () -> Unit) {
        pendingRecoveryRetry = retry
        consentRecoveryLauncher.launch(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Spreadsheet Sync", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Configure spreadsheet destinations (Google Sheets, Excel, EtherCalc, Other). Manual and background sync run all enabled destinations.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabularReadme()) }) {
            Text("Self-hosted spreadsheet servers")
        }
        if (statusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(destinations, key = { it.id }) { dest ->
                SpreadsheetDestCard(
                    dest = dest,
                    onClick = { onEdit(dest.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = destinations.size < SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE,
        ) {
            Text("Add spreadsheet destination")
        }

        Button(
            onClick = {
                fun runSync(allowRecovery: Boolean) {
                    scope.launch {
                        val enabled = SyncDestinationStore(context).enabledSpreadsheet()
                        if (enabled.isEmpty()) {
                            Toast.makeText(context, "No enabled spreadsheet destinations", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncNow("")
                        }
                        if (result.needsRemoteConsent && result.recoveryIntent != null && allowRecovery) {
                            statusText = result.message
                            launchConsentRecovery(result.recoveryIntent) { runSync(allowRecovery = false) }
                            return@launch
                        }
                        statusText = result.message
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
                runSync(allowRecovery = true)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Sync now (all enabled)")
        }
    }
}

@Composable
private fun SpreadsheetDestCard(
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(dest.provider.displayLabel(), style = MaterialTheme.typography.labelSmall)
            Text("Account: $account", style = MaterialTheme.typography.bodySmall)
            Text(
                if (configured) "Enabled: $enabledLabel" else "Not configured",
                style = MaterialTheme.typography.bodySmall,
            )
            if (configured) {
                Text(
                    SyncDestinationStore.spreadsheetSummaryLine(dest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpreadsheetDestEditForm(
    destId: String,
    totalDestCount: Int,
    store: SyncDestinationStore,
    viewModel: SpreadsheetSyncViewModel,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = destId.startsWith("new")
    val newProvider = TabularOtherProviderCatalog.providerFromNewDestId(destId)
        ?: when (destId) {
            "new:excel" -> SpreadsheetProvider.EXCEL
            "new:ethercalc" -> SpreadsheetProvider.ETHERCALC
            else -> SpreadsheetProvider.GOOGLE_SHEETS
        }
    val existing = remember(destId) {
        if (isNew) null else store.allSpreadsheet().find { it.id == destId }
    }
    val provider = existing?.provider ?: newProvider

    var id by remember { mutableStateOf(existing?.id ?: java.util.UUID.randomUUID().toString()) }
    var targetId by remember { mutableStateOf(existing?.targetId ?: "") }
    var targetUrl by remember {
        mutableStateOf(
            existing?.let { dest ->
                dest.targetUrl.ifBlank {
                    if (dest.targetId.isNotBlank()) {
                        GoogleSheetsClient.spreadsheetUrlFromId(dest.targetId)
                    } else {
                        ""
                    }
                }
            } ?: "",
        )
    }
    var configJson by remember { mutableStateOf(existing?.configJson ?: "") }
    val rowDbInitial = remember(destId, existing?.configJson) {
        hydrateRowDbFormFields(existing?.configJson.orEmpty(), existing?.targetUrl.orEmpty(), provider)
    }
    var rowDbBaseUrl by remember { mutableStateOf(rowDbInitial.baseUrl) }
    var rowDbToken by remember { mutableStateOf(rowDbInitial.token) }
    var rowDbDatabaseId by remember { mutableStateOf(rowDbInitial.databaseId) }
    var rowDbProjectId by remember { mutableStateOf(rowDbInitial.projectId) }
    var rowDbBaseId by remember { mutableStateOf(rowDbInitial.baseId) }
    var rowDbVehiclesTableId by remember { mutableStateOf(rowDbInitial.vehiclesTableId) }
    var rowDbExpensesTableId by remember { mutableStateOf(rowDbInitial.expensesTableId) }
    var rowDbFuelTableIds by remember { mutableStateOf(rowDbInitial.fuelTableIds) }
    val firebaseInitial = remember(destId, existing?.configJson) {
        FirebaseTabularConfig.hydrateFormState(existing?.configJson.orEmpty(), existing?.targetUrl.orEmpty())
    }
    var firebaseProjectId by remember { mutableStateOf(firebaseInitial.projectId) }
    var firebaseToken by remember { mutableStateOf(firebaseInitial.token) }
    var firebaseVehiclesCollection by remember { mutableStateOf(firebaseInitial.vehiclesCollection) }
    var firebaseExpensesCollection by remember { mutableStateOf(firebaseInitial.expensesCollection) }
    var firebaseFuelCollections by remember { mutableStateOf(firebaseInitial.fuelCollections) }
    val zohoInitial = remember(destId, existing?.configJson) {
        ZohoSheetConfig.hydrateFormState(existing?.configJson.orEmpty(), existing?.targetId.orEmpty())
    }
    var zohoWorkbookId by remember { mutableStateOf(zohoInitial.workbookId) }
    var zohoClientId by remember { mutableStateOf(zohoInitial.clientId) }
    var zohoClientSecret by remember { mutableStateOf(zohoInitial.clientSecret) }
    var zohoAccessToken by remember { mutableStateOf(zohoInitial.accessToken) }
    var zohoRefreshToken by remember { mutableStateOf(zohoInitial.refreshToken) }
    var zohoApiDomain by remember { mutableStateOf(zohoInitial.apiDomain) }
    var zohoAccountsServer by remember { mutableStateOf(zohoInitial.accountsServer) }
    var zohoVehiclesSheet by remember { mutableStateOf(zohoInitial.vehiclesSheet) }
    var zohoExpensesSheet by remember { mutableStateOf(zohoInitial.expensesSheet) }
    var zohoFuelSheets by remember { mutableStateOf(zohoInitial.fuelSheets) }
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var accountHint by remember {
        mutableStateOf(
            existing?.accountHint ?: when (provider) {
                SpreadsheetProvider.EXCEL -> viewModel.msAuth.getPersistedAccountEmail().orEmpty()
                SpreadsheetProvider.GOOGLE_SHEETS -> viewModel.auth.getPersistedAccountEmail().orEmpty()
                else -> ""
            },
        )
    }
    var etherCalcBaseUrl by remember {
        mutableStateOf(
            existing?.let {
                if (it.configJson.isNotBlank()) {
                    try {
                        JSONObject(it.configJson).optString("baseUrl", it.targetUrl)
                    } catch (_: Exception) {
                        it.targetUrl
                    }
                } else {
                    it.targetUrl
                }
            }.orEmpty(),
        )
    }
    var etherCalcRoomPrefix by remember {
        mutableStateOf(
            existing?.let {
                if (it.configJson.isNotBlank()) {
                    try {
                        JSONObject(it.configJson).optString("roomPrefix", "ve")
                    } catch (_: Exception) {
                        "ve"
                    }
                } else {
                    it.targetId.ifBlank { "ve" }
                }
            } ?: "ve",
        )
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: false) }
    var wifiOnly by remember { mutableStateOf(existing?.wifiOnly ?: true) }
    var chargingOnly by remember { mutableStateOf(existing?.chargingOnly ?: false) }
    var frequencyHours by remember {
        mutableFloatStateOf(
            SyncFrequencyUi.minutesToDisplayHours(existing?.resolvedFrequencyMinutes() ?: 60),
        )
    }
    var statusText by remember { mutableStateOf("") }
    val isDeferredStub = provider == SpreadsheetProvider.ONLYOFFICE ||
        provider == SpreadsheetProvider.COLLABORA

    LaunchedEffect(isDeferredStub) {
        if (isDeferredStub && enabled) {
            enabled = false
        }
    }
    var showBrowseDialog by remember { mutableStateOf(false) }
    var pendingRecoveryRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val displayNameRequired = totalDestCount > 1

    LaunchedEffect(targetUrl) {
        if (targetUrl.isNotBlank()) {
            GoogleSheetsClient.parseSpreadsheetIdFromUrl(targetUrl)?.let { parsed ->
                if (parsed != targetId) targetId = parsed
            }
        }
    }

    LaunchedEffect(
        targetId, targetUrl, configJson, displayName, accountHint, enabled, wifiOnly, chargingOnly,
        frequencyHours, id, provider, etherCalcBaseUrl, etherCalcRoomPrefix,
        rowDbBaseUrl, rowDbToken, rowDbDatabaseId, rowDbProjectId, rowDbBaseId,
        rowDbVehiclesTableId, rowDbExpensesTableId, rowDbFuelTableIds,
        firebaseProjectId, firebaseToken, firebaseVehiclesCollection, firebaseExpensesCollection, firebaseFuelCollections,
        zohoWorkbookId, zohoClientId, zohoClientSecret, zohoAccessToken, zohoRefreshToken,
        zohoApiDomain, zohoAccountsServer, zohoVehiclesSheet, zohoExpensesSheet, zohoFuelSheets,
    ) {
        val resolvedConfig = when (provider) {
            SpreadsheetProvider.ETHERCALC -> JSONObject().apply {
                put("baseUrl", etherCalcBaseUrl.trim())
                put("roomPrefix", etherCalcRoomPrefix.trim().ifBlank { "ve" })
            }.toString()
            SpreadsheetProvider.BASEROW,
            SpreadsheetProvider.NOCODB,
            SpreadsheetProvider.POCKETBASE,
            SpreadsheetProvider.SUPABASE,
            SpreadsheetProvider.AIRTABLE,
            -> buildRowDbConfigJson(
                provider,
                rowDbBaseUrl,
                rowDbToken,
                rowDbDatabaseId,
                rowDbProjectId,
                rowDbBaseId,
                rowDbVehiclesTableId,
                rowDbExpensesTableId,
                rowDbFuelTableIds,
            )
            SpreadsheetProvider.FIREBASE -> FirebaseTabularConfig.toJson(
                firebaseProjectId,
                firebaseToken,
                firebaseVehiclesCollection,
                firebaseExpensesCollection,
                firebaseFuelCollections,
            )
            SpreadsheetProvider.ZOHO_SHEET -> ZohoSheetConfig.buildJson(
                zohoWorkbookId,
                zohoClientId,
                zohoClientSecret,
                zohoAccessToken,
                zohoRefreshToken,
                zohoApiDomain,
                zohoAccountsServer,
                zohoVehiclesSheet,
                zohoExpensesSheet,
                zohoFuelSheets,
            )
            else -> configJson
        }
        val candidate = SpreadsheetDestination(
            id = id,
            provider = provider,
            targetId = when (provider) {
                SpreadsheetProvider.ETHERCALC -> etherCalcRoomPrefix.trim().ifBlank { "ve" }
                SpreadsheetProvider.BASEROW,
                SpreadsheetProvider.NOCODB,
                SpreadsheetProvider.POCKETBASE,
                SpreadsheetProvider.SUPABASE,
                SpreadsheetProvider.AIRTABLE,
                -> rowDbDatabaseId.ifBlank { rowDbBaseUrl.trim() }
                SpreadsheetProvider.FIREBASE -> firebaseProjectId.trim()
                SpreadsheetProvider.ZOHO_SHEET -> zohoWorkbookId.trim()
                else -> targetId
            },
            targetUrl = when (provider) {
                SpreadsheetProvider.ETHERCALC -> etherCalcBaseUrl.trim()
                SpreadsheetProvider.BASEROW,
                SpreadsheetProvider.NOCODB,
                SpreadsheetProvider.POCKETBASE,
                SpreadsheetProvider.SUPABASE,
                SpreadsheetProvider.AIRTABLE,
                -> rowDbBaseUrl.trim()
                SpreadsheetProvider.FIREBASE -> firebaseProjectId.trim()
                SpreadsheetProvider.ZOHO_SHEET -> ZohoSheetConfig.DEFAULT_API_DOMAIN
                else -> targetUrl
            },
            configJson = resolvedConfig,
            displayName = displayName,
            accountHint = accountHint,
            enabled = enabled && !isDeferredStub,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            frequencyMinutes = SyncFrequencyUi.hoursToMinutes(frequencyHours),
        )
        if (isNew && !SyncDestinationStore.isSpreadsheetConfigured(candidate)) return@LaunchedEffect
        if (displayNameRequired && displayName.isBlank()) return@LaunchedEffect
        store.upsertSpreadsheet(candidate)
        viewModel.rescheduleBackgroundSync()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = viewModel.auth.parseSignInResult(result.data)
            val email = account.email ?: ""
            viewModel.auth.persistAccountEmail(email)
            accountHint = email
            statusText = "Signed in as $email"
            Toast.makeText(context, "Signed in as $email", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            statusText = "Sign-in failed — try again or pick a Google account with Sheets access"
            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
        }
    }

    val consentRecoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        val retry = pendingRecoveryRetry
        pendingRecoveryRetry = null
        retry?.invoke()
    }

    fun launchConsentRecovery(intent: Intent, retry: () -> Unit) {
        pendingRecoveryRetry = retry
        consentRecoveryLauncher.launch(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back to list")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isNew) "Add ${provider.displayLabel()}" else "Edit ${provider.displayLabel()}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (provider) {
            SpreadsheetProvider.GOOGLE_SHEETS -> {
                OutlinedButton(
                    onClick = { signInLauncher.launch(viewModel.auth.signInIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accountHint.isBlank()) "Sign in with Google" else "Signed in: $accountHint")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            SpreadsheetProvider.EXCEL -> {
                val activity = context as? ComponentActivity
                OutlinedButton(
                    onClick = {
                        if (activity == null) {
                            Toast.makeText(context, "Sign-in requires activity", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                try {
                                    val result = viewModel.msAuth.signInInteractive(activity, accountHint.ifBlank { null })
                                    accountHint = result.email
                                    statusText = "Signed in as ${result.email}"
                                } catch (e: Exception) {
                                    statusText = e.message ?: "Microsoft sign-in failed"
                                    Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accountHint.isBlank()) "Sign in with Microsoft" else "Signed in: $accountHint")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            SpreadsheetProvider.ETHERCALC -> Unit
            SpreadsheetProvider.BASEROW,
            SpreadsheetProvider.NOCODB,
            SpreadsheetProvider.POCKETBASE,
            SpreadsheetProvider.SUPABASE,
            SpreadsheetProvider.AIRTABLE,
            SpreadsheetProvider.FIREBASE,
            -> Unit
            SpreadsheetProvider.ZOHO_SHEET -> Unit
            SpreadsheetProvider.ONLYOFFICE,
            SpreadsheetProvider.COLLABORA,
            SpreadsheetProvider.OTHER,
            -> {
                Text(
                    "${provider.displayLabel()} is not yet implemented. Pick an implemented provider under Other.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Text("Destination", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = {
                Text(
                    if (displayNameRequired) "Display name (required)" else "Display name (optional)",
                )
            },
            modifier = Modifier.fillMaxWidth(),
            isError = displayNameRequired && displayName.isBlank(),
        )
        when (provider) {
            SpreadsheetProvider.GOOGLE_SHEETS -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = { Text("Sheet URL") },
                        supportingText = { Text("Paste a link or use browse") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (accountHint.isBlank() && viewModel.auth.getLastAccount() == null) {
                                Toast.makeText(context, "Sign in first", Toast.LENGTH_SHORT).show()
                            } else {
                                showBrowseDialog = true
                            }
                        },
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Browse spreadsheets")
                    }
                }

                if (showBrowseDialog) {
                    GoogleDriveBrowserDialog(
                        mode = GoogleDriveBrowserMode.SPREADSHEETS,
                        accountHint = accountHint,
                        onDismiss = { showBrowseDialog = false },
                        onSelect = { item ->
                            targetId = item.id
                            targetUrl = GoogleSheetsClient.spreadsheetUrlFromId(item.id)
                            if (displayName.isBlank()) displayName = item.name
                            showBrowseDialog = false
                            statusText = "Selected: ${item.name}"
                        },
                        listItems = { search ->
                            try {
                                viewModel.listSpreadsheetsForBrowse(accountHint, search)
                            } catch (e: SheetsRecoverableAuthException) {
                                throw e
                            } catch (e: Exception) {
                                throw SheetsAuthRecovery.wrapIfRecoverable(e)
                            }
                        },
                        createItem = { title ->
                            try {
                                viewModel.createSpreadsheetForBrowse(accountHint, title)
                            } catch (e: SheetsRecoverableAuthException) {
                                throw e
                            } catch (e: Exception) {
                                throw SheetsAuthRecovery.wrapIfRecoverable(e)
                            }
                        },
                        emptyMessage =
                            "No spreadsheets visible. Create one here or open an existing sheet in this app first.",
                    )
                }
            }
            SpreadsheetProvider.EXCEL -> {
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it },
                    label = { Text("Workbook item ID") },
                    supportingText = { Text("OneDrive/SharePoint drive item id for the .xlsx workbook") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("Workbook URL (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SpreadsheetProvider.ETHERCALC -> {
                TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabular("ethercalc")) }) {
                    Text("Setup help — EtherCalc")
                }
                OutlinedTextField(
                    value = etherCalcBaseUrl,
                    onValueChange = { etherCalcBaseUrl = it },
                    label = { Text("EtherCalc base URL") },
                    supportingText = { Text("e.g. https://ethercalc.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = etherCalcRoomPrefix,
                    onValueChange = { etherCalcRoomPrefix = it },
                    label = { Text("Room prefix") },
                    supportingText = { Text("Each tab maps to a room: prefix-tabname") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SpreadsheetProvider.BASEROW,
            SpreadsheetProvider.NOCODB,
            SpreadsheetProvider.POCKETBASE,
            SpreadsheetProvider.SUPABASE,
            SpreadsheetProvider.AIRTABLE,
            -> {
                RowDbSpreadsheetForm(
                    provider = provider,
                    baseUrl = rowDbBaseUrl,
                    onBaseUrlChange = { rowDbBaseUrl = it },
                    token = rowDbToken,
                    onTokenChange = { rowDbToken = it },
                    databaseId = rowDbDatabaseId,
                    onDatabaseIdChange = { rowDbDatabaseId = it },
                    projectId = rowDbProjectId,
                    onProjectIdChange = { rowDbProjectId = it },
                    baseId = rowDbBaseId,
                    onBaseIdChange = { rowDbBaseId = it },
                    vehiclesTableId = rowDbVehiclesTableId,
                    onVehiclesTableIdChange = { rowDbVehiclesTableId = it },
                    expensesTableId = rowDbExpensesTableId,
                    onExpensesTableIdChange = { rowDbExpensesTableId = it },
                    fuelTableIds = rowDbFuelTableIds,
                    onFuelTableIdsChange = { rowDbFuelTableIds = it },
                )
            }
            SpreadsheetProvider.FIREBASE -> {
                FirebaseSpreadsheetForm(
                    projectId = firebaseProjectId,
                    onProjectIdChange = { firebaseProjectId = it },
                    token = firebaseToken,
                    onTokenChange = { firebaseToken = it },
                    vehiclesCollection = firebaseVehiclesCollection,
                    onVehiclesCollectionChange = { firebaseVehiclesCollection = it },
                    expensesCollection = firebaseExpensesCollection,
                    onExpensesCollectionChange = { firebaseExpensesCollection = it },
                    fuelCollections = firebaseFuelCollections,
                    onFuelCollectionsChange = { firebaseFuelCollections = it },
                )
            }
            SpreadsheetProvider.ZOHO_SHEET -> {
                ZohoSheetSpreadsheetForm(
                    workbookId = zohoWorkbookId,
                    onWorkbookIdChange = { zohoWorkbookId = it },
                    clientId = zohoClientId,
                    onClientIdChange = { zohoClientId = it },
                    clientSecret = zohoClientSecret,
                    onClientSecretChange = { zohoClientSecret = it },
                    accessToken = zohoAccessToken,
                    onAccessTokenChange = { zohoAccessToken = it },
                    refreshToken = zohoRefreshToken,
                    onRefreshTokenChange = { zohoRefreshToken = it },
                    vehiclesSheet = zohoVehiclesSheet,
                    onVehiclesSheetChange = { zohoVehiclesSheet = it },
                    expensesSheet = zohoExpensesSheet,
                    onExpensesSheetChange = { zohoExpensesSheet = it },
                    fuelSheets = zohoFuelSheets,
                    onFuelSheetsChange = { zohoFuelSheets = it },
                    signedIn = zohoAccessToken.isNotBlank(),
                    onSignIn = {
                        if (zohoClientId.isBlank()) {
                            Toast.makeText(context, "Enter OAuth client id first", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                try {
                                    val authUrl = viewModel.zohoAuth.buildAuthorizationUrl(
                                        zohoClientId,
                                        zohoAccountsServer.ifBlank { ZohoSheetConfig.DEFAULT_ACCOUNTS_SERVER },
                                    )
                                    CustomTabsIntent.Builder().build()
                                        .launchUrl(context, Uri.parse(authUrl))
                                    val result = viewModel.zohoAuth.awaitRedirectResult()
                                    zohoAccessToken = result.accessToken
                                    if (result.refreshToken.isNotBlank()) {
                                        zohoRefreshToken = result.refreshToken
                                    }
                                    if (result.apiDomain.isNotBlank()) {
                                        zohoApiDomain = result.apiDomain
                                    }
                                    zohoAccountsServer = result.accountsServer
                                    accountHint = "zoho"
                                    statusText = "Signed in with Zoho"
                                    Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    statusText = e.message ?: "Zoho sign-in failed"
                                    Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                )
            }
            SpreadsheetProvider.ONLYOFFICE,
            SpreadsheetProvider.COLLABORA,
            SpreadsheetProvider.OTHER,
            -> Unit
        }

        SyncBackgroundScheduleSection(
            title = "Background sync",
            enableLabel = "Enable background sync",
            intervalSliderLabel = "Background sync interval (hours)",
            state = SyncScheduleUiState(
                enabled = enabled,
                wifiOnly = wifiOnly,
                chargingOnly = chargingOnly,
                frequencyHours = frequencyHours,
            ),
            onEnabledChange = { if (!isDeferredStub) enabled = it },
            onWifiOnlyChange = { wifiOnly = it },
            onChargingOnlyChange = { chargingOnly = it },
            onFrequencyHoursChange = { frequencyHours = SyncFrequencyUi.snapHours(it) },
            enableSwitchEnabled = !isDeferredStub,
            deferredStubMessage = if (isDeferredStub) {
                "${provider.displayLabel()} is not yet available — background sync cannot be enabled."
            } else {
                null
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                fun runTest(allowRecovery: Boolean) {
                    scope.launch {
                        val testDest = SpreadsheetDestination(
                            provider = provider,
                            targetId = when (provider) {
                                SpreadsheetProvider.ETHERCALC -> etherCalcRoomPrefix.trim().ifBlank { "ve" }
                                SpreadsheetProvider.BASEROW,
                                SpreadsheetProvider.NOCODB,
                                SpreadsheetProvider.POCKETBASE,
                                SpreadsheetProvider.SUPABASE,
                                SpreadsheetProvider.AIRTABLE,
                                -> rowDbDatabaseId.ifBlank { rowDbBaseUrl.trim() }
                                SpreadsheetProvider.FIREBASE -> firebaseProjectId.trim()
                                SpreadsheetProvider.ZOHO_SHEET -> zohoWorkbookId.trim()
                                else -> targetId
                            },
                            targetUrl = when (provider) {
                                SpreadsheetProvider.ETHERCALC -> etherCalcBaseUrl.trim()
                                SpreadsheetProvider.BASEROW,
                                SpreadsheetProvider.NOCODB,
                                SpreadsheetProvider.POCKETBASE,
                                SpreadsheetProvider.SUPABASE,
                                SpreadsheetProvider.AIRTABLE,
                                -> rowDbBaseUrl.trim()
                                SpreadsheetProvider.FIREBASE -> firebaseProjectId.trim()
                                SpreadsheetProvider.ZOHO_SHEET -> zohoApiDomain.ifBlank { ZohoSheetConfig.DEFAULT_API_DOMAIN }
                                else -> targetUrl
                            },
                            configJson = when (provider) {
                                SpreadsheetProvider.ETHERCALC -> JSONObject().apply {
                                    put("baseUrl", etherCalcBaseUrl.trim())
                                    put("roomPrefix", etherCalcRoomPrefix.trim().ifBlank { "ve" })
                                }.toString()
                                SpreadsheetProvider.BASEROW,
                                SpreadsheetProvider.NOCODB,
                                SpreadsheetProvider.POCKETBASE,
                                SpreadsheetProvider.SUPABASE,
                                SpreadsheetProvider.AIRTABLE,
                                -> buildRowDbConfigJson(
                                    provider,
                                    rowDbBaseUrl,
                                    rowDbToken,
                                    rowDbDatabaseId,
                                    rowDbProjectId,
                                    rowDbBaseId,
                                    rowDbVehiclesTableId,
                                    rowDbExpensesTableId,
                                    rowDbFuelTableIds,
                                )
                                SpreadsheetProvider.FIREBASE -> FirebaseTabularConfig.toJson(
                                    firebaseProjectId,
                                    firebaseToken,
                                    firebaseVehiclesCollection,
                                    firebaseExpensesCollection,
                                    firebaseFuelCollections,
                                )
                                SpreadsheetProvider.ZOHO_SHEET -> ZohoSheetConfig.buildJson(
                                    zohoWorkbookId,
                                    zohoClientId,
                                    zohoClientSecret,
                                    zohoAccessToken,
                                    zohoRefreshToken,
                                    zohoApiDomain,
                                    zohoAccountsServer,
                                    zohoVehiclesSheet,
                                    zohoExpensesSheet,
                                    zohoFuelSheets,
                                )
                                else -> configJson
                            },
                            accountHint = accountHint,
                        )
                        if (!SyncDestinationStore.isSpreadsheetConfigured(testDest)) {
                            Toast.makeText(context, "Configure a destination first", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            val ok = withContext(Dispatchers.IO) {
                                viewModel.testConnection(testDest)
                            }
                            statusText = if (ok) "Connection test passed" else "Connection test failed"
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        } catch (e: SheetsRecoverableAuthException) {
                            statusText = e.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                            if (allowRecovery) {
                                launchConsentRecovery(e.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            statusText = SheetsAuthRecovery.userMessage(e)
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                runTest(allowRecovery = true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test connection (this destination)")
        }

        if (!isNew) {
            OutlinedButton(
                onClick = {
                    store.removeSpreadsheet(id)
                    viewModel.rescheduleBackgroundSync()
                    Toast.makeText(context, "Destination removed", Toast.LENGTH_SHORT).show()
                    onRemoved()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text("Remove destination")
            }
        }

        if (statusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
