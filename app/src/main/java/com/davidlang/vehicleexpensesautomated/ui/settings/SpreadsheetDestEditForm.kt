package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.FirebaseTabularConfig
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetConfig
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SpreadsheetDestEditForm(
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
    val consentRecovery = rememberConsentRecoveryHandle()
    val displayNameRequired = totalDestCount > 1
    val formTitle = if (isNew) "Add ${provider.displayLabel()}" else "Edit ${provider.displayLabel()}"

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

    SyncDestinationEditScaffold(onBack = onBack, title = formTitle) {
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

        SyncDestinationDisplayNameField(
            value = displayName,
            onValueChange = { displayName = it },
            required = displayNameRequired,
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
        SyncDestinationEditFooter(
            testButtonLabel = "Test connection (this destination)",
            onTest = {
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
                                consentRecovery.launch(e.recoveryIntent) { runTest(allowRecovery = false) }
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
            showRemove = !isNew,
            onRemove = {
                store.removeSpreadsheet(id)
                viewModel.rescheduleBackgroundSync()
                Toast.makeText(context, "Destination removed", Toast.LENGTH_SHORT).show()
                onRemoved()
            },
            statusText = statusText,
        )
    }
}
