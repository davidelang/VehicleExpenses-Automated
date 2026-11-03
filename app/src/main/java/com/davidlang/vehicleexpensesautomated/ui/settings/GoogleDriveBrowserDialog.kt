package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
import com.davidlang.vehicleexpensesautomated.data.sync.DriveRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowseCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import kotlinx.coroutines.launch
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

enum class GoogleDriveBrowserMode {
    SPREADSHEETS,
    FOLDERS,
}

@Composable
fun GoogleDriveBrowserDialog(
    mode: GoogleDriveBrowserMode,
    accountHint: String,
    onDismiss: () -> Unit,
    onSelect: (DriveBrowserItem) -> Unit,
    listItems: suspend (searchQuery: String, catalog: GoogleDriveBrowseCatalog) -> List<DriveBrowserItem>,
    createItem: suspend (name: String) -> DriveBrowserItem,
    enableHybridCatalog: Boolean = false,
    readonlyAccessGranted: Boolean = true,
    onRequestReadonlyAccess: () -> Unit = {},
    onRecoverableDriveAuth: ((Intent) -> Unit)? = null,
    reloadKey: Int = 0,
    emptyMessage: String =
        AppStrings.get(R.string.settings_no_items_visible_with_drive_access_limited_to_th),
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var catalog by remember {
        mutableStateOf(
            if (enableHybridCatalog && mode == GoogleDriveBrowserMode.SPREADSHEETS) {
                GoogleDriveBrowseCatalog.ALL
            } else {
                GoogleDriveBrowseCatalog.APP
            },
        )
    }
    var items by remember { mutableStateOf<List<DriveBrowserItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }
    var showCreatePrompt by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var needsReadonlyConsent by remember { mutableStateOf(false) }
    var driveConsentInFlight by remember { mutableStateOf(false) }

    fun browseFailureMessage(error: Throwable): String = when (error) {
        is SheetsRecoverableAuthException -> SheetsAuthRecovery.userMessage(error)
        else -> DriveAuthRecovery.userMessage(error)
    }

    fun maybeLaunchDriveConsent(error: Throwable) {
        val launch = onRecoverableDriveAuth ?: return
        if (error !is DriveRecoverableAuthException || driveConsentInFlight) return
        driveConsentInFlight = true
        launch(error.recoveryIntent)
    }

    val title = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> AppStrings.get(R.string.settings_browse_spreadsheets)
        GoogleDriveBrowserMode.FOLDERS -> AppStrings.get(R.string.settings_browse_folders)
    }
    val createLabel = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> AppStrings.get(R.string.settings_new_spreadsheet)
        GoogleDriveBrowserMode.FOLDERS -> AppStrings.get(R.string.settings_new_folder)
    }
    val createDefaultName = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> AppStrings.get(R.string.app_name)
        GoogleDriveBrowserMode.FOLDERS -> AppStrings.get(R.string.settings_vehicle_expenses_photos)
    }
    val allCatalogEmptyMessage = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS ->
            AppStrings.get(R.string.settings_no_spreadsheets_found_try_search_or_paste)
        GoogleDriveBrowserMode.FOLDERS ->
            AppStrings.get(R.string.settings_no_folders_found_try_search_or_paste_a_fol)
    }

    fun reload() {
        if (enableHybridCatalog &&
            catalog == GoogleDriveBrowseCatalog.ALL &&
            !readonlyAccessGranted
        ) {
            loading = false
            needsReadonlyConsent = true
            items = emptyList()
            errorText = ""
            return
        }
        needsReadonlyConsent = false
        scope.launch {
            loading = true
            errorText = ""
            try {
                items = listItems(searchQuery, catalog)
                driveConsentInFlight = false
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                errorText = browseFailureMessage(e)
                items = emptyList()
                maybeLaunchDriveConsent(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(accountHint, searchQuery, catalog, readonlyAccessGranted, reloadKey) {
        reload()
    }

    if (showCreatePrompt) {
        AlertDialog(
            onDismissRequest = {
                if (!creating) showCreatePrompt = false
            },
            title = { Text(createLabel) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text(AppStrings.get(R.string.settings_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = createName.trim()
                        if (name.isBlank() || creating) return@TextButton
                        scope.launch {
                            creating = true
                            errorText = ""
                            try {
                                val created = createItem(name)
                                showCreatePrompt = false
                                onSelect(created)
                            // kept: failure stays on this call so the screen or caller can continue
                            } catch (e: Exception) {
                                errorText = browseFailureMessage(e)
                                maybeLaunchDriveConsent(e)
                            } finally {
                                creating = false
                            }
                        }
                    },
                    enabled = createName.isNotBlank() && !creating,
                ) {
                    Text(if (creating) AppStrings.get(R.string.settings_creating) else AppStrings.get(R.string.settings_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!creating) showCreatePrompt = false },
                    enabled = !creating,
                ) {
                    Text(AppStrings.get(R.string.settings_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (enableHybridCatalog) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = catalog == GoogleDriveBrowseCatalog.APP,
                            onClick = { catalog = GoogleDriveBrowseCatalog.APP },
                            label = { Text(AppStrings.get(R.string.settings_this_app)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = catalog == GoogleDriveBrowseCatalog.ALL,
                            onClick = { catalog = GoogleDriveBrowseCatalog.ALL },
                            label = { Text(AppStrings.get(R.string.settings_all_shared)) },
                        )
                    }
                    val itemLabel = when (mode) {
                        GoogleDriveBrowserMode.SPREADSHEETS -> "spreadsheets"
                        GoogleDriveBrowserMode.FOLDERS -> "folders"
                    }
                    Text(
                        text = when (catalog) {
                            GoogleDriveBrowseCatalog.APP ->
                                AppStrings.get(R.string.settings_s_1_s_created_or_opened_in_this_app, itemLabel.replaceFirstChar { it.uppercase() })
                            GoogleDriveBrowseCatalog.ALL ->
                                AppStrings.get(R.string.settings_all_1_s_your_google_account_can_view_inclu, itemLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(AppStrings.get(R.string.settings_search)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    if (catalog == GoogleDriveBrowseCatalog.APP) {
                        IconButton(
                            onClick = {
                                createName = createDefaultName
                                showCreatePrompt = true
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = createLabel)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    needsReadonlyConsent -> {
                        val browseItemLabel = when (mode) {
                            GoogleDriveBrowserMode.SPREADSHEETS -> "spreadsheets"
                            GoogleDriveBrowserMode.FOLDERS -> "folders"
                        }
                        Text(
                            AppStrings.get(R.string.settings_allow_drive_read_access_to_list_all_1_s_ow, browseItemLabel),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = onRequestReadonlyAccess,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(AppStrings.get(R.string.settings_grant_browse_access))
                        }
                    }
                    loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text(AppStrings.get(R.string.settings_loading))
                        }
                    }
                    errorText.isNotBlank() -> {
                        Text(errorText, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(
                            onClick = {
                                driveConsentInFlight = false
                                reload()
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(AppStrings.get(R.string.settings_retry))
                        }
                    }
                    items.isEmpty() -> {
                        val message = if (catalog == GoogleDriveBrowseCatalog.ALL) {
                            allCatalogEmptyMessage
                        } else {
                            emptyMessage
                        }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                        if (catalog == GoogleDriveBrowseCatalog.APP) {
                            OutlinedButton(
                                onClick = {
                                    createName = createDefaultName
                                    showCreatePrompt = true
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(createLabel)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(items, key = { it.id }) { item ->
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(item) }
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get(R.string.settings_cancel))
            }
        },
    )
}