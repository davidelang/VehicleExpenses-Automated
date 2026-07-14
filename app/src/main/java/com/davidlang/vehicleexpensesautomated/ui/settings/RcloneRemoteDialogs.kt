package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneConfigOption
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneConfigQuestion
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneDestConfig
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneException
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderInfo
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderKind
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RcloneRemotesListDialog(
    destId: String,
    config: RcloneDestConfig,
    viewModel: PhotoBackupViewModel,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var remotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            errorText = ""
            try {
                remotes = withContext(Dispatchers.IO) {
                    viewModel.listRcloneRemotes(destId, config)
                }
            } catch (e: Exception) {
                errorText = e.message ?: "Failed to list remotes"
                remotes = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(destId) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select remote") },
        text = {
            Column {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else if (errorText.isNotBlank()) {
                    Text(errorText, color = MaterialTheme.colorScheme.error)
                } else if (remotes.isEmpty()) {
                    Text("No remotes in this conf yet. Create one with +.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(remotes, key = { it }) { remote ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(remote) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(remote, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { onEdit(remote) },
                                    enabled = deleting == null,
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit remote")
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            deleting = remote
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    viewModel.deleteRcloneRemote(destId, config, remote)
                                                }
                                                Toast.makeText(context, "Deleted $remote", Toast.LENGTH_SHORT).show()
                                                onDeleted()
                                                reload()
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    e.message ?: "Delete failed",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            } finally {
                                                deleting = null
                                            }
                                        }
                                    },
                                    enabled = deleting == null,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete remote")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

enum class RcloneWizardMode { CREATE, EDIT }

@Composable
fun RcloneRemoteWizardDialog(
    destId: String,
    config: RcloneDestConfig,
    viewModel: PhotoBackupViewModel,
    mode: RcloneWizardMode,
    existingRemoteName: String? = null,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (mode == RcloneWizardMode.EDIT) 2 else 0) }
    var providers by remember { mutableStateOf<List<RcloneProviderInfo>>(emptyList()) }
    var providerSearch by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<RcloneProviderKind?>(null) }
    var selectedProvider by remember { mutableStateOf<RcloneProviderInfo?>(null) }
    var remoteName by remember { mutableStateOf(existingRemoteName ?: "") }
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    var showAdvanced by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var oauthQuestion by remember { mutableStateOf<RcloneConfigQuestion?>(null) }
    var oauthAnswer by remember { mutableStateOf("") }
    var oauthState by remember { mutableStateOf("") }
    var pendingOAuthUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            providers = withContext(Dispatchers.IO) { viewModel.listRcloneProviders() }
            if (mode == RcloneWizardMode.EDIT && !existingRemoteName.isNullOrBlank()) {
                remoteName = existingRemoteName
                val type = withContext(Dispatchers.IO) {
                    viewModel.getRcloneRemoteType(destId, config, existingRemoteName)
                }
                selectedProvider = type?.let { t ->
                    providers.firstOrNull { it.name.equals(t, ignoreCase = true) }
                        ?: withContext(Dispatchers.IO) { viewModel.providerForRcloneType(t) }
                }
            }
        } catch (e: Exception) {
            errorText = e.message ?: "Failed to load providers"
        } finally {
            loading = false
        }
    }

    val kindProviders = remember(providers, selectedKind, providerSearch) {
        val kind = selectedKind ?: return@remember emptyList()
        val inKind = RcloneProviderCatalog.providersForKind(providers, kind.id)
        val q = providerSearch.trim().lowercase()
        if (q.isBlank()) inKind
        else inKind.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    fun openOAuthUrl(url: String) {
        try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open browser for sign-in", Toast.LENGTH_LONG).show()
        }
    }

    fun extractAuthUrl(help: String): String? {
        val regex = Regex("""https?://[^\s)]+""")
        return regex.find(help)?.value
    }

    suspend fun submitCreateOrUpdate(
        continueState: String? = null,
        continueResult: String? = null,
    ) {
        val provider = selectedProvider ?: throw RcloneException("Pick a backend type")
        val name = remoteName.trim()
        if (name.isBlank()) throw RcloneException("Remote name is required")
        val params = buildParameters(provider, fieldValues, showAdvanced)
        val result = if (mode == RcloneWizardMode.EDIT) {
            viewModel.updateRcloneRemote(
                destId = destId,
                config = config,
                name = name,
                parameters = params,
                continueState = continueState,
                continueResult = continueResult,
            )
        } else {
            viewModel.createRcloneRemote(
                destId = destId,
                config = config,
                name = name,
                type = provider.name,
                parameters = params,
                continueState = continueState,
                continueResult = continueResult,
            )
        }
        if (result.complete) {
            onSaved(name)
            return
        }
        val question = result.question ?: throw RcloneException("Incomplete config step")
        oauthState = result.state
        oauthQuestion = question
        oauthAnswer = question.defaultValue
        val authUrl = extractAuthUrl(question.help)
        if (authUrl != null) {
            pendingOAuthUrl = authUrl
            openOAuthUrl(authUrl)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Text(
                when (mode) {
                    RcloneWizardMode.CREATE -> when (step) {
                        0 -> "Other storage — pick kind"
                        1 -> selectedKind?.label?.let { "Create remote — $it" } ?: "Create remote — pick backend"
                        2 -> "Create remote — $remoteName"
                        else -> "Create remote — sign in"
                    }
                    RcloneWizardMode.EDIT -> "Edit remote — $remoteName"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (loading) {
                    CircularProgressIndicator()
                }
                if (errorText.isNotBlank()) {
                    Text(errorText, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                when {
                    oauthQuestion != null -> {
                        val q = oauthQuestion!!
                        if (q.error.isNotBlank()) {
                            Text(q.error, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(q.help, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (q.requiresOAuthStyle()) {
                            Text(
                                "Requires browser sign-in. Complete authorization in the browser, then continue.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            pendingOAuthUrl?.let { url ->
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { openOAuthUrl(url) }) {
                                    Text("Open sign-in page")
                                }
                            }
                        }
                        if (q.exclusive && q.examples.isNotEmpty()) {
                            OAuthAnswerDropdown(q, oauthAnswer) { oauthAnswer = it }
                        } else {
                            OutlinedTextField(
                                value = oauthAnswer,
                                onValueChange = { oauthAnswer = it },
                                label = { Text(q.name.ifBlank { "Answer" }) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (q.isPassword) {
                                    PasswordVisualTransformation()
                                } else {
                                    VisualTransformation.None
                                },
                            )
                        }
                    }
                    step == 0 -> {
                        Text(
                            "Choose a category, then pick a backend. Import conf on the destination screen for advanced setups.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(RcloneProviderCatalog.KIND_GROUPS, key = { it.id }) { kind ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedKind = kind
                                            providerSearch = ""
                                            step = 1
                                            errorText = ""
                                        }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(kind.label, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                    step == 1 -> {
                        OutlinedTextField(
                            value = providerSearch,
                            onValueChange = { providerSearch = it },
                            label = { Text("Search backends") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selectedKind?.id == "selfhost") {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.photosReadme()) }) {
                                Text("Self-hosted setup guide")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (kindProviders.isEmpty()) {
                            Text(
                                "No backends in this category. Try another kind or import rclone.conf.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(kindProviders, key = { it.name }) { provider ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedProvider = provider
                                                remoteName = remoteName.ifBlank { provider.name }
                                                step = 2
                                                errorText = ""
                                            }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                        Text(provider.description, style = MaterialTheme.typography.bodySmall)
                                        if (provider.requiresOAuth) {
                                            Text(
                                                "Requires browser sign-in",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    step >= 2 -> {
                        if (mode == RcloneWizardMode.CREATE) {
                            OutlinedTextField(
                                value = remoteName,
                                onValueChange = { remoteName = it },
                                label = { Text("Remote name") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = step == 2,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        selectedProvider?.let { provider ->
                            if (provider.requiresOAuth && provider.options.isEmpty()) {
                                Text(
                                    "This backend needs browser OAuth. You can try guided sign-in below, " +
                                        "or use Import existing conf… for a desktop-built remote.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (SyncSetupDocs.photoCheatsheetForRcloneType(provider.name) != null) {
                                TextButton(
                                    onClick = {
                                        SyncSetupDocs.open(context, SyncSetupDocs.photoUrlForRcloneType(provider.name))
                                    },
                                ) {
                                    Text("Setup help — ${provider.name}")
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Show advanced options", modifier = Modifier.weight(1f))
                                Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
                            }
                            val options = RcloneProviderCatalog.formOptions(provider, showAdvanced)
                            options.forEach { opt ->
                                ProviderOptionField(
                                    option = opt,
                                    value = fieldValues[opt.name].orEmpty(),
                                    onValueChange = { fieldValues[opt.name] = it },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (options.isEmpty() && !provider.requiresOAuth) {
                                Text(
                                    "No extra fields for ${provider.name}. Save to add the remote.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                oauthQuestion != null -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                errorText = ""
                                try {
                                    withContext(Dispatchers.IO) {
                                        submitCreateOrUpdate(oauthState, oauthAnswer)
                                    }
                                } catch (e: Exception) {
                                    errorText = e.message ?: "Failed"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                    ) { Text("Continue") }
                }
                step < 2 -> {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                else -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                errorText = ""
                                try {
                                    withContext(Dispatchers.IO) {
                                        submitCreateOrUpdate()
                                    }
                                } catch (e: Exception) {
                                    errorText = e.message ?: "Save failed"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && remoteName.isNotBlank(),
                    ) { Text(if (mode == RcloneWizardMode.EDIT) "Save" else "Create") }
                }
            }
        },
        dismissButton = {
            if (step > 0 && oauthQuestion == null) {
                TextButton(
                    onClick = {
                        when {
                            step == 2 && mode == RcloneWizardMode.CREATE -> step = 1
                            step == 1 && mode == RcloneWizardMode.CREATE -> {
                                selectedKind = null
                                step = 0
                            }
                            else -> onDismiss()
                        }
                    },
                    enabled = !loading,
                ) { Text("Back") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OAuthAnswerDropdown(
    question: RcloneConfigQuestion,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(question.name.ifBlank { "Answer" }) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            question.examples.forEach { (value, help) ->
                DropdownMenuItem(
                    text = { Text(if (help.isBlank()) value else "$value — $help") },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderOptionField(
    option: RcloneConfigOption,
    value: String,
    onValueChange: (String) -> Unit,
) {
    if (option.exclusive && option.examples.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = value.ifBlank { option.defaultValue.orEmpty() },
                onValueChange = {},
                readOnly = true,
                label = { Text(option.help.ifBlank { option.name }) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                option.examples.forEach { (exValue, exHelp) ->
                    DropdownMenuItem(
                        text = { Text(if (exHelp.isBlank()) exValue else "$exValue — $exHelp") },
                        onClick = {
                            onValueChange(exValue)
                            expanded = false
                        },
                    )
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    buildString {
                        append(option.help.ifBlank { option.name })
                        if (option.required) append(" *")
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (option.isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = if (option.isPassword) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            singleLine = !option.isPassword,
        )
    }
}

private fun buildParameters(
    provider: RcloneProviderInfo,
    values: Map<String, String>,
    includeAdvanced: Boolean,
): Map<String, String> {
    val params = mutableMapOf<String, String>()
    RcloneProviderCatalog.formOptions(provider, includeAdvanced).forEach { opt ->
        val v = values[opt.name]?.trim().orEmpty()
        if (v.isNotBlank()) {
            params[opt.name] = v
        } else if (!opt.defaultValue.isNullOrBlank()) {
            params[opt.name] = opt.defaultValue
        }
    }
    return params
}

private fun RcloneConfigQuestion.requiresOAuthStyle(): Boolean {
    val lower = help.lowercase()
    return lower.contains("http") || lower.contains("browser") || lower.contains("oauth") ||
        name.contains("token", ignoreCase = true)
}