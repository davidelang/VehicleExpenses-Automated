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
        "No items visible. With Drive access limited to this app, open or create files here first.",
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
        GoogleDriveBrowserMode.SPREADSHEETS -> "Browse spreadsheets"
        GoogleDriveBrowserMode.FOLDERS -> "Browse folders"
    }
    val createLabel = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> "New spreadsheet"
        GoogleDriveBrowserMode.FOLDERS -> "New folder"
    }
    val createDefaultName = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS -> "Vehicle Expenses"
        GoogleDriveBrowserMode.FOLDERS -> "Vehicle Expenses Photos"
    }
    val allCatalogEmptyMessage = when (mode) {
        GoogleDriveBrowserMode.SPREADSHEETS ->
            "No spreadsheets found. Try search, or paste a sheet URL if it is shared with your account."
        GoogleDriveBrowserMode.FOLDERS ->
            "No folders found. Try search, or paste a folder URL if it is shared with your account."
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
                    label = { Text("Name") },
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
                    Text(if (creating) "Creating…" else "Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!creating) showCreatePrompt = false },
                    enabled = !creating,
                ) {
                    Text("Cancel")
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
                            label = { Text("This app") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = catalog == GoogleDriveBrowseCatalog.ALL,
                            onClick = { catalog = GoogleDriveBrowseCatalog.ALL },
                            label = { Text("All & shared") },
                        )
                    }
                    val itemLabel = when (mode) {
                        GoogleDriveBrowserMode.SPREADSHEETS -> "spreadsheets"
                        GoogleDriveBrowserMode.FOLDERS -> "folders"
                    }
                    Text(
                        text = when (catalog) {
                            GoogleDriveBrowseCatalog.APP ->
                                "${itemLabel.replaceFirstChar { it.uppercase() }} created or opened in this app."
                            GoogleDriveBrowseCatalog.ALL ->
                                "All $itemLabel your Google account can view, including shared files."
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
                        label = { Text("Search") },
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
                            "Allow Drive read access to list all $browseItemLabel (owned and shared with you).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = onRequestReadonlyAccess,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Grant browse access")
                        }
                    }
                    loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text("Loading…")
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
                            Text("Retry")
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
                Text("Cancel")
            }
        },
    )
}