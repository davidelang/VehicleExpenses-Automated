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
import com.davidlang.vehicleexpensesautomated.ui.util.S

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
        S("No items visible. With Drive access limited to this app, open or create files here first."),
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
        GoogleDriveBrowserMode.SPREADSHEETS -> S("Browse spreadsheets")
        GoogleDriveBrowserMode.FOLDERS -> S("Browse folders")
    }
    val createLabel = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> S("New spreadsheet")
        GoogleDriveBrowserMode.FOLDERS -> S("New folder")
    }
    val createDefaultName = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> S("Vehicle Expenses")
        GoogleDriveBrowserMode.FOLDERS -> S("Vehicle Expenses Photos")
    }
    val allCatalogEmptyMessage = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS ->
            S("No spreadsheets found. Try search, or paste a sheet URL if it is shared with your account.")
        GoogleDriveBrowserMode.FOLDERS ->
            S("No folders found. Try search, or paste a folder URL if it is shared with your account.")
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
                    label = { Text(S("Name")) },
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
                    Text(if (creating) S("Creating…") else S("Create"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!creating) showCreatePrompt = false },
                    enabled = !creating,
                ) {
                    Text(S("Cancel"))
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
                            label = { Text(S("This app")) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = catalog == GoogleDriveBrowseCatalog.ALL,
                            onClick = { catalog = GoogleDriveBrowseCatalog.ALL },
                            label = { Text(S("All & shared")) },
                        )
                    }
                    val itemLabel = when (mode) {
                        GoogleDriveBrowserMode.SPREADSHEETS -> "spreadsheets"
                        GoogleDriveBrowserMode.FOLDERS -> "folders"
                    }
                    Text(
                        text = when (catalog) {
                            GoogleDriveBrowseCatalog.APP ->
                                S("%1\$s created or opened in this app.", itemLabel.replaceFirstChar { it.uppercase() })
                            GoogleDriveBrowseCatalog.ALL ->
                                S("All %1\$s your Google account can view, including shared files.", itemLabel)
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
                        label = { Text(S("Search")) },
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
                            S("Allow Drive read access to list all %1\$s (owned and shared with you).", browseItemLabel),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = onRequestReadonlyAccess,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(S("Grant browse access"))
                        }
                    }
                    loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text(S("Loading…"))
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
                            Text(S("Retry"))
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
                Text(S("Cancel"))
            }
        },
    )
}