package com.davidlang.vehicleexpensesautomated.ui.settings

import com.davidlang.vehicleexpensesautomated.R

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
import androidx.compose.material3.TextButton
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.DriveRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi
import com.davidlang.vehicleexpensesautomated.data.sync.SyncRateLimit
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderCatalog
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
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
    var statusIsError by remember { mutableStateOf(false) }
    val failureStore = remember { SyncFailureStore(context) }
    var storedFailureDetail by remember {
        mutableStateOf(if (isNew) null else failureStore.spreadsheetFailure(id)?.message)
    }
    val isDeferredStub = provider == SpreadsheetProvider.ONLYOFFICE ||
        provider == SpreadsheetProvider.COLLABORA
    val syncInProgress by viewModel.manualSyncInProgress.collectAsState()
    val vmSyncStatus by viewModel.manualSyncStatus.collectAsState()
    val vmSyncIsError by viewModel.manualSyncIsError.collectAsState()
    val syncResult by viewModel.manualSyncResult.collectAsState()
    val footerStatus = if (syncInProgress || vmSyncStatus.isNotBlank()) vmSyncStatus else statusText
    val footerIsError = if (syncInProgress || vmSyncStatus.isNotBlank()) vmSyncIsError else statusIsError

    LaunchedEffect(syncResult) {
        val result = syncResult ?: return@LaunchedEffect
        statusText = result.message
        statusIsError = !result.success
        storedFailureDetail = failureStore.spreadsheetFailure(id)?.message
        Toast.makeText(
            context,
            if (result.success) {
                "Sync complete"
            } else {
                SyncRateLimit.shortTitle(result.message) ?: "Sync failed — open Details"
            },
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.clearManualSyncResult()
    }

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

    var driveReadonlyGranted by remember { mutableStateOf(viewModel.driveAuth.hasReadonlyBrowseScope()) }

    val driveReadonlyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = viewModel.auth.parseSignInResult(result.data)
            val email = account.email ?: ""
            if (email.isNotBlank()) {
                viewModel.driveAuth.persistAccountEmail(email)
            }
            driveReadonlyGranted = viewModel.driveAuth.hasReadonlyBrowseScope()
            if (driveReadonlyGranted) {
                statusText = "Drive browse access granted"
            } else {
                statusText = "Drive browse access not granted — try again"
            }
        } catch (e: ApiException) {
            statusText = "Drive browse sign-in failed"
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
                            Toast.makeText(context, context.getString(R.string.settings_sign_in_requires_activity), Toast.LENGTH_SHORT).show()
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
                        label = { Text(stringResource(R.string.settings_sheet_url)) },
                        supportingText = { Text(stringResource(R.string.settings_paste_a_link_or_use_browse)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (accountHint.isBlank() && viewModel.auth.getLastAccount() == null) {
                                Toast.makeText(context, context.getString(R.string.settings_sign_in_first), Toast.LENGTH_SHORT).show()
                            } else {
                                showBrowseDialog = true
                            }
                        },
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.settings_browse_spreadsheets))
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
                        enableHybridCatalog = true,
                        readonlyAccessGranted = driveReadonlyGranted,
                        onRequestReadonlyAccess = {
                            driveReadonlyLauncher.launch(viewModel.driveAuth.readonlyBrowseSignInIntent())
                        },
                        listItems = { search, catalog ->
                            try {
                                viewModel.listSpreadsheetsForBrowse(accountHint, search, catalog)
                            } catch (e: DriveRecoverableAuthException) {
                                throw e
                            } catch (e: SheetsRecoverableAuthException) {
                                throw e
                            } catch (e: Exception) {
                                throw DriveAuthRecovery.wrapIfRecoverable(
                                    SheetsAuthRecovery.wrapIfRecoverable(e),
                                )
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
                            stringResource(R.string.settings_no_spreadsheets_visible_create_one_here_or_open_),
                    )
                }
            }
            SpreadsheetProvider.EXCEL -> {
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it },
                    label = { Text(stringResource(R.string.settings_workbook_item_id)) },
                    supportingText = { Text(stringResource(R.string.settings_onedrive_sharepoint_drive_item_id_for_the_xlsx_w)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text(stringResource(R.string.settings_workbook_url_optional)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SpreadsheetProvider.ETHERCALC -> {
                TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabular("ethercalc")) }) {
                    Text(stringResource(R.string.settings_setup_help_ethercalc))
                }
                OutlinedTextField(
                    value = etherCalcBaseUrl,
                    onValueChange = { etherCalcBaseUrl = it },
                    label = { Text(stringResource(R.string.settings_ethercalc_base_url)) },
                    supportingText = { Text(stringResource(R.string.settings_e_g_https_ethercalc_example_com)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = etherCalcRoomPrefix,
                    onValueChange = { etherCalcRoomPrefix = it },
                    label = { Text(stringResource(R.string.settings_room_prefix)) },
                    supportingText = { Text(stringResource(R.string.settings_each_tab_maps_to_a_room_prefix_tabname)) },
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
                            Toast.makeText(context, context.getString(R.string.settings_enter_oauth_client_id_first), Toast.LENGTH_SHORT).show()
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
            title = stringResource(R.string.settings_background_sync),
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
        val canSyncThisDest = !isNew &&
            !isDeferredStub &&
            store.allSpreadsheet().find { it.id == id }?.let {
                SyncDestinationStore.isSpreadsheetConfigured(it)
            } == true
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
                            Toast.makeText(context, context.getString(R.string.settings_configure_a_destination_first), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            val ok = withContext(Dispatchers.IO) {
                                viewModel.testConnection(testDest)
                            }
                            statusIsError = !ok
                            statusText = if (ok) "Connection test passed" else "Connection test failed"
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        } catch (e: SheetsRecoverableAuthException) {
                            statusIsError = true
                            statusText = e.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                            if (allowRecovery) {
                                consentRecovery.launch(e.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            statusIsError = true
                            statusText = SheetsAuthRecovery.userMessage(e)
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                runTest(allowRecovery = true)
            },
            onSyncNow = if (isNew || isDeferredStub) {
                null
            } else {
                {
                    if (!canSyncThisDest) {
                        Toast.makeText(context, context.getString(R.string.settings_save_a_configured_destination_first),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        viewModel.startManualSync(accountHint = accountHint, destId = id)
                    }
                }
            },
            syncInProgress = syncInProgress,
            syncNowEnabled = canSyncThisDest,
            statusIsError = footerIsError,
            failureDetailMessage = storedFailureDetail,
            failureDialogTitle = displayName.ifBlank { "Spreadsheet sync failure" },
            showRemove = !isNew,
            onRemove = {
                store.removeSpreadsheet(id)
                viewModel.rescheduleBackgroundSync()
                Toast.makeText(context, context.getString(R.string.settings_destination_removed), Toast.LENGTH_SHORT).show()
                onRemoved()
            },
            statusText = footerStatus,
        )
    }
}
