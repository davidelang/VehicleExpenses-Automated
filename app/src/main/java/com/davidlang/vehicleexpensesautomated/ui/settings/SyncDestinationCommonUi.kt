package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ConsentRecoveryHandle internal constructor(
    val launch: (Intent, () -> Unit) -> Unit,
)

@Composable
fun rememberConsentRecoveryHandle(): ConsentRecoveryHandle {
    var pendingRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        val retry = pendingRetry
        pendingRetry = null
        retry?.invoke()
    }
    return remember {
        ConsentRecoveryHandle { intent, retry ->
            pendingRetry = retry
            launcher.launch(intent)
        }
    }
}

@Composable
fun SyncDestinationListLayout(
    title: String,
    description: String,
    statusText: String,
    destinationCount: Int,
    maxDestinations: Int,
    addButtonLabel: String,
    onAdd: () -> Unit,
    syncNowLabel: String,
    onSyncNow: () -> Unit,
    docLinkLabel: String? = null,
    onDocLinkClick: (() -> Unit)? = null,
    listContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodySmall)
        if (docLinkLabel != null && onDocLinkClick != null) {
            TextButton(onClick = onDocLinkClick) {
                Text(docLinkLabel)
            }
        }
        if (statusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        listContent()
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = destinationCount < maxDestinations,
        ) {
            Text(addButtonLabel)
        }
        Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(syncNowLabel)
        }
    }
}

@Composable
fun SyncDestinationSummaryCard(
    title: String,
    detailLines: List<String>,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            detailLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SyncProviderChoiceScreen(
    title: String,
    choices: List<Pair<String, () -> Unit>>,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        choices.forEach { (label, onClick) ->
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
fun SyncDestinationEditScaffold(
    onBack: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
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
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun SyncDestinationDisplayNameField(
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean,
) {
    Text("Destination", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                if (required) "Display name (required)" else "Display name (optional)",
            )
        },
        modifier = Modifier.fillMaxWidth(),
        isError = required && value.isBlank(),
    )
}

@Composable
fun SyncDestinationEditFooter(
    testButtonLabel: String,
    onTest: () -> Unit,
    showRemove: Boolean,
    onRemove: () -> Unit,
    statusText: String,
) {
    Button(
        onClick = onTest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(testButtonLabel)
    }
    if (showRemove) {
        OutlinedButton(
            onClick = onRemove,
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