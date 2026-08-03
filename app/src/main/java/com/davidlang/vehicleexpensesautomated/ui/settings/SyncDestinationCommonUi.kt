package com.davidlang.vehicleexpensesautomated.ui.settings

import com.davidlang.vehicleexpensesautomated.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.SyncProgressListener

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

/**
 * Best-effort progress for long-running sync. Uses a process main [Handler] so
 * disposing the composition never throws into the sync job
 * (`ForgottenCoroutineScopeException`).
 */
@Composable
fun rememberMainThreadSyncProgress(onUpdate: (String) -> Unit): SyncProgressListener {
    val latestUpdate by rememberUpdatedState(onUpdate)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    return remember {
        SyncProgressListener { message ->
            mainHandler.post {
                try {
                    latestUpdate(message)
                } catch (_: Throwable) {
                    // Composition/state gone — progress is optional
                }
            }
        }
    }
}

@Composable
fun SyncStatusDisplay(
    statusText: String,
    syncInProgress: Boolean = false,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    if (statusText.isBlank() && !syncInProgress) return
    val textColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(verticalAlignment = Alignment.Top) {
        if (syncInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Column {
            statusText.lines().filter { it.isNotBlank() }.forEach { line ->
                Text(line, style = textStyle, color = textColor)
            }
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
    syncInProgress: Boolean = false,
    statusIsError: Boolean = false,
    docLinkLabel: String? = null,
    onDocLinkClick: (() -> Unit)? = null,
    listContent: @Composable ColumnScope.() -> Unit,
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
        if (statusText.isNotBlank() || syncInProgress) {
            Spacer(modifier = Modifier.height(4.dp))
            SyncStatusDisplay(
                statusText = statusText,
                syncInProgress = syncInProgress,
                isError = statusIsError,
            )
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
            enabled = !syncInProgress,
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
            Text(stringResource(R.string.settings_cancel))
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
            Text(stringResource(R.string.settings_back_to_list))
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
    Text(stringResource(R.string.settings_destination), style = MaterialTheme.typography.titleMedium)
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

/**
 * Dest-edit footer: Test connection, optional Sync now (this dest), Remove, status, Details.
 */
@Composable
fun SyncDestinationEditFooter(
    testButtonLabel: String,
    onTest: () -> Unit,
    showRemove: Boolean,
    onRemove: () -> Unit,
    statusText: String,
    syncNowLabel: String = "Sync now (this destination)",
    onSyncNow: (() -> Unit)? = null,
    syncInProgress: Boolean = false,
    syncNowEnabled: Boolean = true,
    statusIsError: Boolean = false,
    failureDetailMessage: String? = null,
    failureDialogTitle: String = "Sync failure",
) {
    Button(
        onClick = onTest,
        modifier = Modifier.fillMaxWidth(),
        enabled = !syncInProgress,
    ) {
        Text(testButtonLabel)
    }
    if (onSyncNow != null) {
        Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = syncNowEnabled && !syncInProgress,
        ) {
            Text(syncNowLabel)
        }
    }
    if (showRemove) {
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !syncInProgress,
        ) {
            Text(stringResource(R.string.settings_remove_destination))
        }
    }
    if (statusText.isNotBlank() || syncInProgress) {
        Spacer(modifier = Modifier.height(8.dp))
        SyncStatusDisplay(
            statusText = statusText,
            syncInProgress = syncInProgress,
            isError = statusIsError,
        )
    }
    if (!failureDetailMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        SyncFailureDetailsButton(
            detailMessage = failureDetailMessage,
            title = failureDialogTitle,
        )
    }
}

/** Opens a scrollable dialog with full failure text + Copy. */
@Composable
fun SyncFailureDetailsButton(
    detailMessage: String,
    title: String = "Sync failure",
    buttonLabel: String = "Details",
) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) {
        Text(buttonLabel)
    }
    if (show) {
        SyncFailureDetailsDialog(
            title = title,
            detailMessage = detailMessage,
            onDismiss = { show = false },
        )
    }
}

@Composable
fun SyncFailureDetailsDialog(
    title: String,
    detailMessage: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(detailMessage, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("sync failure", detailMessage))
                    Toast.makeText(context, context.getString(R.string.settings_copied), Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(stringResource(R.string.settings_copy))
            }
        },
    )
}