package com.davidlang.vehicleexpensesautomated.ui.settings

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
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
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
    listItems: suspend (searchQuery: String) -> List<DriveBrowserItem>,
    createItem: suspend (name: String) -> DriveBrowserItem,
    emptyMessage: String =
        "No items visible. With Drive access limited to this app, open or create files here first.",
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<DriveBrowserItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }
    var showCreatePrompt by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

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

    fun reload() {
        scope.launch {
            loading = true
            errorText = ""
            try {
                items = listItems(searchQuery)
            } catch (e: Exception) {
                errorText = e.message ?: "Failed to load"
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(accountHint, searchQuery) {
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
                                errorText = e.message ?: "Create failed"
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
                    IconButton(
                        onClick = {
                            createName = createDefaultName
                            showCreatePrompt = true
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = createLabel)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
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
                        OutlinedButton(onClick = { reload() }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Retry")
                        }
                    }
                    items.isEmpty() -> {
                        Text(emptyMessage, style = MaterialTheme.typography.bodySmall)
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