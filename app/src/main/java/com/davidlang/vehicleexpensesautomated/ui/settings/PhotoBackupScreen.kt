package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.DriveRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowserClient
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoProvider
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneConfStorage
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneDestConfig
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneS3Setup
import com.davidlang.vehicleexpensesautomated.data.sync.S3ProviderPreset
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoBackupScreen(
    navController: NavHostController,
    viewModel: PhotoBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val store = remember { SyncDestinationStore(context) }
    var destinations by remember { mutableStateOf(store.allPhoto()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pickingProvider by remember { mutableStateOf(false) }

    fun refreshList() {
        destinations = store.allPhoto()
    }

    when {
        pickingProvider -> PhotoProviderPicker(
            onPick = { provider ->
                pickingProvider = false
                editingId = when (provider) {
                    PhotoProvider.OTHER -> "new:other"
                    PhotoProvider.ONEDRIVE -> "new:onedrive"
                    PhotoProvider.S3 -> "new:s3"
                    else -> "new:drive"
                }
            },
            onCancel = { pickingProvider = false },
        )
        editingId == null -> PhotoDestList(
            destinations = destinations,
            onAdd = {
                if (destinations.size >= SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE) {
                    Toast.makeText(
                        context,
                        "Maximum ${SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE} photo destinations",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pickingProvider = true
                }
            },
            onEdit = { editingId = it },
            viewModel = viewModel,
        )
        else -> PhotoDestEditForm(
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
private fun PhotoDestList(
    destinations: List<PhotoDestination>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: PhotoBackupViewModel,
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
        Text("Photo Backup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Configure Google Drive, OneDrive, S3, or Other storage. Manual and background backup run all enabled destinations.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (statusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(destinations, key = { it.id }) { dest ->
                PhotoDestCard(dest = dest, onClick = { onEdit(dest.id) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = destinations.size < SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE,
        ) {
            Text("Add photo destination")
        }

        Button(
            onClick = {
                fun runSync(allowRecovery: Boolean) {
                    scope.launch {
                        val enabled = SyncDestinationStore(context).enabledPhoto()
                        if (enabled.isEmpty()) {
                            Toast.makeText(context, "No enabled photo destinations", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            val result = withContext(Dispatchers.IO) {
                                viewModel.syncNow("")
                            }
                            statusText = result.message
                            if (result.success) {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            } else if (result.needsRemoteConsent && result.recoveryIntent != null && allowRecovery) {
                                launchConsentRecovery(result.recoveryIntent) { runSync(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: DriveRecoverableAuthException) {
                            statusText = e.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                            if (allowRecovery) {
                                launchConsentRecovery(e.recoveryIntent) { runSync(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            statusText = DriveAuthRecovery.userMessage(e)
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        }
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
private fun PhotoDestCard(
    dest: PhotoDestination,
    onClick: () -> Unit,
) {
    val name = when {
        dest.displayName.isNotBlank() -> dest.displayName
        dest.provider == PhotoProvider.ONEDRIVE ->
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "OneDrive" } ?: "OneDrive"
        dest.provider == PhotoProvider.S3 ->
            RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "S3" } ?: "S3"
        dest.provider == PhotoProvider.OTHER ->
            RcloneDestConfig.parse(dest.configJson)?.remote?.ifBlank { "Other" } ?: "Other"
        dest.folderName.isNotBlank() -> dest.folderName
        else -> "Google Drive"
    }
    val account = when (dest.provider) {
        PhotoProvider.ONEDRIVE -> dest.accountHint.ifBlank { "Microsoft account" }
        PhotoProvider.S3 -> "S3 storage"
        PhotoProvider.OTHER -> "Other storage"
        else -> dest.accountHint.ifBlank { "No account" }
    }
    val enabledLabel = if (dest.enabled) "On" else "Off"
    val context = LocalContext.current
    val configured = SyncDestinationStore.isPhotoConfigured(dest, context)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text("Account: $account", style = MaterialTheme.typography.bodySmall)
            Text(
                if (configured) "Enabled: $enabledLabel" else "Not configured",
                style = MaterialTheme.typography.bodySmall,
            )
            if (configured) {
                Text(
                    SyncDestinationStore.photoSummaryLine(dest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoProviderPicker(
    onPick: (PhotoProvider) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Add photo destination", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onPick(PhotoProvider.GOOGLE_DRIVE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Google Drive")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(PhotoProvider.ONEDRIVE) }, modifier = Modifier.fillMaxWidth()) {
            Text("OneDrive")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(PhotoProvider.S3) }, modifier = Modifier.fillMaxWidth()) {
            Text("S3")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPick(PhotoProvider.OTHER) }, modifier = Modifier.fillMaxWidth()) {
            Text("Other")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PhotoDestEditForm(
    destId: String,
    totalDestCount: Int,
    store: SyncDestinationStore,
    viewModel: PhotoBackupViewModel,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = destId.startsWith("new")
    val newProvider = when (destId) {
        "new:other" -> PhotoProvider.OTHER
        "new:onedrive" -> PhotoProvider.ONEDRIVE
        "new:s3" -> PhotoProvider.S3
        else -> PhotoProvider.GOOGLE_DRIVE
    }
    val existing = remember(destId) {
        if (isNew) null else store.allPhoto().find { it.id == destId }
    }

    var id by remember { mutableStateOf(existing?.id ?: java.util.UUID.randomUUID().toString()) }
    var provider by remember { mutableStateOf(existing?.provider ?: newProvider) }
    var folderName by remember { mutableStateOf(existing?.folderName ?: "") }
    var folderUrl by remember {
        mutableStateOf(
            existing?.let { dest ->
                if (dest.folderId.isNotBlank()) {
                    GoogleDriveBrowserClient.folderUrlFromId(dest.folderId)
                } else {
                    ""
                }
            } ?: "",
        )
    }
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var accountHint by remember {
        mutableStateOf(
            existing?.accountHint ?: when (newProvider) {
                PhotoProvider.ONEDRIVE -> viewModel.oneDriveAuth.getPersistedAccountEmail().orEmpty()
                else -> viewModel.auth.getPersistedAccountEmail().orEmpty()
            },
        )
    }
    var rcloneRemote by remember {
        mutableStateOf(RcloneDestConfig.parse(existing?.configJson)?.remote ?: "")
    }
    var rclonePrefix by remember {
        mutableStateOf(
            RcloneDestConfig.parse(existing?.configJson)?.pathPrefix
                ?: if (newProvider == PhotoProvider.S3) RcloneS3Setup.DEFAULT_PATH_PREFIX else "VehicleExpenses/photos",
        )
    }
    var s3AccessKey by remember { mutableStateOf("") }
    var s3SecretKey by remember { mutableStateOf("") }
    var s3Region by remember { mutableStateOf("") }
    var s3Endpoint by remember { mutableStateOf("") }
    var s3Bucket by remember { mutableStateOf("") }
    var s3ProviderPreset by remember { mutableStateOf(S3ProviderPreset.AWS) }
    var confImported by remember {
        mutableStateOf(
            existing?.let { dest ->
                RcloneDestConfig.parse(dest.configJson)?.let { cfg ->
                    RcloneConfStorage.hasConf(context, dest.id, cfg)
                }
            } ?: false,
        )
    }
    LaunchedEffect(existing?.id, existing?.configJson) {
        if (existing?.provider == PhotoProvider.S3) {
            RcloneDestConfig.parse(existing.configJson)?.let { cfg ->
                rcloneRemote = cfg.remote
                val (bucket, prefix) = viewModel.splitS3BucketAndPrefix(cfg.pathPrefix)
                s3Bucket = bucket
                if (prefix.isNotBlank()) rclonePrefix = prefix
                confImported = RcloneConfStorage.hasConf(context, existing.id, cfg)
            }
        }
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
    var folderId by remember { mutableStateOf(existing?.folderId ?: "") }
    var showBrowseDialog by remember { mutableStateOf(false) }
    var showRcloneRemotesDialog by remember { mutableStateOf(false) }
    var showRcloneWizard by remember { mutableStateOf(false) }
    var rcloneWizardMode by remember { mutableStateOf(RcloneWizardMode.CREATE) }
    var rcloneEditRemote by remember { mutableStateOf<String?>(null) }
    var pendingRecoveryRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val displayNameRequired = totalDestCount > 1

    val configJson = remember(provider, rcloneRemote, rclonePrefix, s3Bucket, id) {
        if (!provider.usesRcloneBackend()) return@remember ""
        val remote = when (provider) {
            PhotoProvider.ONEDRIVE -> rcloneRemote.trim().ifBlank { viewModel.managedOneDriveRemoteName(id) }
            PhotoProvider.S3 -> rcloneRemote.trim().ifBlank { viewModel.managedS3RemoteName(id) }
            else -> rcloneRemote.trim()
        }
        if (remote.isBlank()) "" else {
            val prefix = when (provider) {
                PhotoProvider.S3 -> {
                    val bucket = s3Bucket.trim()
                    val path = rclonePrefix.trim().trim('/')
                    when {
                        bucket.isBlank() -> path
                        path.isBlank() -> bucket
                        else -> "$bucket/$path"
                    }
                }
                else -> rclonePrefix.trim()
            }
            RcloneDestConfig(remote = remote, pathPrefix = prefix).toJson()
        }
    }

    LaunchedEffect(folderUrl) {
        if (folderUrl.isNotBlank()) {
            GoogleDriveBrowserClient.parseFolderIdFromUrl(folderUrl)?.let { parsed ->
                if (parsed != folderId) folderId = parsed
            }
        }
    }

    LaunchedEffect(
        provider, folderName, displayName, accountHint, enabled, wifiOnly, chargingOnly,
        frequencyHours, id, folderId, configJson,
    ) {
        val candidate = PhotoDestination(
            id = id,
            provider = provider,
            displayName = displayName,
            folderName = folderName,
            configJson = if (provider.usesRcloneBackend()) configJson else "",
            accountHint = accountHint,
            enabled = enabled,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            frequencyMinutes = SyncFrequencyUi.hoursToMinutes(frequencyHours),
            folderId = folderId,
        )
        if (isNew && !SyncDestinationStore.isPhotoConfigured(candidate)) return@LaunchedEffect
        if (displayNameRequired && displayName.isBlank()) return@LaunchedEffect
        store.upsertPhoto(candidate)
        viewModel.rescheduleBackgroundBackup()
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

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: ""
            viewModel.auth.persistAccountEmail(email)
            accountHint = email
            statusText = "Signed in as $email"
            Toast.makeText(context, "Signed in as $email (Drive)", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            statusText = "Sign-in failed — try again or pick a Google account with Drive access"
            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
        }
    }

    val importConfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cfg = RcloneDestConfig(remote = rcloneRemote.trim(), pathPrefix = rclonePrefix.trim())
        val ok = RcloneConfStorage.importConf(context, id, uri, cfg.confFileName)
        confImported = ok
        statusText = if (ok) "rclone.conf imported" else "Failed to import rclone.conf"
        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
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
            when {
                isNew && provider == PhotoProvider.OTHER -> "Add Other destination"
                isNew && provider == PhotoProvider.ONEDRIVE -> "Add OneDrive destination"
                isNew && provider == PhotoProvider.S3 -> "Add S3 destination"
                isNew -> "Add Google Drive folder"
                else -> "Edit destination"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (provider == PhotoProvider.GOOGLE_DRIVE) {
            OutlinedButton(
                onClick = { signInLauncher.launch(viewModel.auth.signInIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (accountHint.isBlank()) "Sign in with Google (Drive)" else "Signed in: $accountHint")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (provider == PhotoProvider.ONEDRIVE) {
            val msHints = remember { viewModel.oneDriveAuth.deviceMicrosoftAccountHints() }
            OutlinedButton(
                onClick = {
                    val activity = context as? ComponentActivity
                    if (activity == null) {
                        Toast.makeText(context, "Cannot start Microsoft sign-in", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    scope.launch {
                        try {
                            val authResult = withContext(Dispatchers.Main) {
                                viewModel.oneDriveAuth.signInInteractive(activity, accountHint.ifBlank { null })
                            }
                            val cfg = withContext(Dispatchers.IO) {
                                viewModel.setupOneDriveRemote(id, authResult, rclonePrefix)
                            }
                            rcloneRemote = cfg.remote
                            confImported = true
                            accountHint = authResult.email
                            statusText = "Signed in as ${authResult.email}"
                            Toast.makeText(context, "Signed in as ${authResult.email} (OneDrive)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            statusText = e.message ?: "Microsoft sign-in failed"
                            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (accountHint.isBlank()) "Sign in with Microsoft (OneDrive)"
                    else "Using: $accountHint",
                )
            }
            if (msHints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Device accounts: ${msHints.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Destination", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = when (provider) {
                PhotoProvider.ONEDRIVE -> "OneDrive"
                PhotoProvider.S3 -> "S3"
                PhotoProvider.OTHER -> "Other"
                else -> "Google Drive"
            },
            onValueChange = {},
            label = { Text("Provider") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
        )
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
        if (provider == PhotoProvider.GOOGLE_DRIVE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = folderUrl,
                    onValueChange = { folderUrl = it },
                    label = { Text("Folder URL (optional)") },
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
                    Icon(Icons.Default.Search, contentDescription = "Browse folders")
                }
            }
            if (folderName.isNotBlank() || folderId.isNotBlank()) {
                OutlinedTextField(
                    value = folderName.ifBlank { folderId.take(12) + if (folderId.length > 12) "…" else "" },
                    onValueChange = {},
                    label = { Text("Selected folder") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    readOnly = true,
                    enabled = false,
                )
            }
            if (showBrowseDialog) {
                GoogleDriveBrowserDialog(
                    mode = GoogleDriveBrowserMode.FOLDERS,
                    accountHint = accountHint,
                    onDismiss = { showBrowseDialog = false },
                    onSelect = { item ->
                        folderId = item.id
                        folderName = item.name
                        folderUrl = GoogleDriveBrowserClient.folderUrlFromId(item.id)
                        if (displayName.isBlank()) displayName = item.name
                        showBrowseDialog = false
                        statusText = "Selected: ${item.name}"
                    },
                    listItems = { search ->
                        try {
                            viewModel.listFoldersForBrowse(accountHint, search)
                        } catch (e: DriveRecoverableAuthException) {
                            throw e
                        } catch (e: Exception) {
                            throw DriveAuthRecovery.wrapIfRecoverable(e)
                        }
                    },
                    createItem = { name ->
                        try {
                            viewModel.createFolderForBrowse(accountHint, name)
                        } catch (e: DriveRecoverableAuthException) {
                            throw e
                        } catch (e: Exception) {
                            throw DriveAuthRecovery.wrapIfRecoverable(e)
                        }
                    },
                    emptyMessage =
                        "No folders visible. Create one here or open an existing folder in this app first.",
                )
            }
        } else if (provider == PhotoProvider.S3) {
            Text("S3 storage", style = MaterialTheme.typography.titleMedium)
            Text(
                "S3 and compatible services (Wasabi, Cloudflare R2, MinIO, …).",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { SyncSetupDocs.open(context, SyncSetupDocs.photo("minio-s3-compatible")) },
            ) {
                Text("Setup help — MinIO / S3-compatible")
            }
            Spacer(modifier = Modifier.height(8.dp))
            S3ProviderDropdown(
                selected = s3ProviderPreset,
                onSelected = { s3ProviderPreset = it },
            )
            OutlinedTextField(
                value = s3AccessKey,
                onValueChange = { s3AccessKey = it },
                label = { Text("Access Key ID") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3SecretKey,
                onValueChange = { s3SecretKey = it },
                label = { Text("Secret Access Key") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = s3Region,
                onValueChange = { s3Region = it },
                label = { Text("Region") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3Endpoint,
                onValueChange = { s3Endpoint = it },
                label = { Text("Endpoint (advanced)") },
                supportingText = { Text("Required for MinIO and custom S3-compatible hosts") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3Bucket,
                onValueChange = { s3Bucket = it },
                label = { Text("Bucket") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text("Path prefix") },
                supportingText = { Text("e.g. VehicleExpenses/photos") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank() && s3Bucket.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val cfg = withContext(Dispatchers.IO) {
                                    viewModel.setupS3Remote(
                                        destId = id,
                                        accessKeyId = s3AccessKey,
                                        secretAccessKey = s3SecretKey,
                                        region = s3Region,
                                        endpoint = s3Endpoint,
                                        bucket = s3Bucket,
                                        pathPrefix = rclonePrefix,
                                        providerPreset = s3ProviderPreset,
                                    )
                                }
                                rcloneRemote = cfg.remote
                                rclonePrefix = cfg.pathPrefix
                                confImported = true
                                statusText = "S3 credentials saved"
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                statusText = e.message ?: "Failed to save S3 credentials"
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save S3 credentials")
                }
            }
        } else if (provider == PhotoProvider.ONEDRIVE) {
            Text("Folder on OneDrive", style = MaterialTheme.typography.titleMedium)
            Text(
                "Photos upload under this path on your signed-in OneDrive.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text("Folder / path") },
                supportingText = { Text("e.g. VehicleExpenses/photos") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!accountHint.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val activity = context as? ComponentActivity ?: return@OutlinedButton
                        scope.launch {
                            try {
                                val authResult = withContext(Dispatchers.Main) {
                                    viewModel.oneDriveAuth.signInInteractive(activity, accountHint)
                                }
                                val cfg = withContext(Dispatchers.IO) {
                                    viewModel.setupOneDriveRemote(id, authResult, rclonePrefix)
                                }
                                rcloneRemote = cfg.remote
                                confImported = true
                                statusText = "OneDrive account refreshed"
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                statusText = e.message ?: "Re-authentication failed"
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Re-authenticate Microsoft account")
                }
            }
        } else {
            Text("Remotes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create a remote on this device or pick one from the conf. Powered by rclone for WebDAV, SFTP, Azure, and more.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.photosReadme()) }) {
                Text("Self-hosted photo backup setup")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = rcloneRemote,
                    onValueChange = { rcloneRemote = it },
                    label = { Text("Remote name") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRcloneRemotesDialog = true }) {
                    Icon(Icons.Default.List, contentDescription = "List remotes")
                }
            }
            OutlinedButton(
                onClick = {
                    rcloneWizardMode = RcloneWizardMode.CREATE
                    rcloneEditRemote = null
                    showRcloneWizard = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Create new remote")
            }
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text("Path prefix on remote") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = { importConfLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(if (confImported) "Import existing conf… (re-import)" else "Import existing conf…")
            }
            if (showRcloneRemotesDialog) {
                val cfg = RcloneDestConfig(
                    remote = rcloneRemote.trim().ifBlank { "placeholder" },
                    pathPrefix = rclonePrefix.trim(),
                )
                RcloneRemotesListDialog(
                    destId = id,
                    config = cfg,
                    viewModel = viewModel,
                    onDismiss = { showRcloneRemotesDialog = false },
                    onSelect = { remote ->
                        rcloneRemote = remote
                        confImported = true
                        showRcloneRemotesDialog = false
                        statusText = "Selected remote: $remote"
                    },
                    onEdit = { remote ->
                        rcloneWizardMode = RcloneWizardMode.EDIT
                        rcloneEditRemote = remote
                        rcloneRemote = remote
                        showRcloneRemotesDialog = false
                        showRcloneWizard = true
                    },
                    onDeleted = {
                        if (rcloneEditRemote != null) rcloneEditRemote = null
                    },
                )
            }
            if (showRcloneWizard) {
                val cfg = RcloneDestConfig(
                    remote = rcloneRemote.trim().ifBlank { "placeholder" },
                    pathPrefix = rclonePrefix.trim(),
                )
                RcloneRemoteWizardDialog(
                    destId = id,
                    config = cfg,
                    viewModel = viewModel,
                    mode = rcloneWizardMode,
                    existingRemoteName = rcloneEditRemote,
                    onDismiss = { showRcloneWizard = false },
                    onSaved = { name ->
                        rcloneRemote = name
                        confImported = true
                        showRcloneWizard = false
                        statusText = "Remote saved: $name"
                        Toast.makeText(context, "Remote $name saved", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Background backup", style = MaterialTheme.typography.titleMedium)
        PhotoSwitchSetting("Enable background backup", enabled) { enabled = it }
        PhotoSwitchSetting("Wi-Fi only", wifiOnly) { wifiOnly = it }
        PhotoSwitchSetting("Charging only", chargingOnly) { chargingOnly = it }
        PhotoSliderSetting(
            "Background backup interval (hours)",
            frequencyHours,
            SpreadsheetDestination.MIN_FREQUENCY_HOURS..
                SpreadsheetDestination.MAX_FREQUENCY_HOURS,
        ) {
            frequencyHours = SyncFrequencyUi.snapHours(it)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                fun runTest(allowRecovery: Boolean) {
                    scope.launch {
                        val dest = PhotoDestination(
                            id = id,
                            provider = provider,
                            folderName = folderName,
                            configJson = if (provider.usesRcloneBackend()) configJson else "",
                            accountHint = accountHint,
                            folderId = folderId,
                        )
                        if (!SyncDestinationStore.isPhotoConfigured(dest)) {
                            Toast.makeText(context, "Configure a destination first", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (provider == PhotoProvider.GOOGLE_DRIVE &&
                            accountHint.isBlank() && viewModel.auth.getLastAccount() == null
                        ) {
                            Toast.makeText(context, "Sign in first", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (provider == PhotoProvider.ONEDRIVE) {
                            if (accountHint.isBlank()) {
                                Toast.makeText(context, "Sign in with Microsoft first", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val cfg = RcloneDestConfig.parse(configJson)
                            if (cfg == null) {
                                Toast.makeText(context, "Set a folder path first", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            withContext(Dispatchers.IO) {
                                viewModel.refreshOneDriveToken(id, cfg, accountHint)
                            }
                        } else if (provider == PhotoProvider.S3) {
                            val cfg = RcloneDestConfig.parse(configJson)
                            val hasExistingConf = cfg != null &&
                                confImported &&
                                RcloneConfStorage.hasConf(context, id, cfg) &&
                                SyncDestinationStore.isPhotoConfigured(dest, context)
                            val secretsEntered = s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank()
                            when {
                                secretsEntered && s3Bucket.isNotBlank() -> {
                                    val applied = withContext(Dispatchers.IO) {
                                        viewModel.setupS3Remote(
                                            destId = id,
                                            accessKeyId = s3AccessKey,
                                            secretAccessKey = s3SecretKey,
                                            region = s3Region,
                                            endpoint = s3Endpoint,
                                            bucket = s3Bucket,
                                            pathPrefix = rclonePrefix,
                                            providerPreset = s3ProviderPreset,
                                        )
                                    }
                                    rcloneRemote = applied.remote
                                    rclonePrefix = applied.pathPrefix
                                    confImported = true
                                }
                                hasExistingConf -> {
                                    // Use credentials already in managed rclone.conf
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        "Enter access key and secret, or save S3 credentials first",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                            }
                        } else if (provider == PhotoProvider.OTHER) {
                            val cfg = RcloneDestConfig.parse(configJson)
                            if (cfg == null || rcloneRemote.isBlank()) {
                                Toast.makeText(context, "Create or select a remote first", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            if (!RcloneConfStorage.hasConf(context, id, cfg)) {
                                RcloneConfStorage.ensureEmptyConf(context, id, cfg.confFileName)
                            }
                        }
                        try {
                            val result = withContext(Dispatchers.IO) {
                                viewModel.testConnection(accountHint, dest)
                            }
                            statusText = result.message
                            if (result.success) {
                                store.allPhoto().find { it.id == id }?.folderId?.let { folderId = it }
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            } else if (result.needsRemoteConsent && result.recoveryIntent != null && allowRecovery) {
                                launchConsentRecovery(result.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: DriveRecoverableAuthException) {
                            statusText = e.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                            if (allowRecovery) {
                                launchConsentRecovery(e.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            statusText = DriveAuthRecovery.userMessage(e)
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                runTest(allowRecovery = true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (provider) {
                    PhotoProvider.GOOGLE_DRIVE -> "Test upload (this destination)"
                    else -> "Test connection (this destination)"
                },
            )
        }

        if (!isNew) {
            OutlinedButton(
                onClick = {
                    store.removePhoto(id)
                    viewModel.rescheduleBackgroundBackup()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun S3ProviderDropdown(
    selected: S3ProviderPreset,
    onSelected: (S3ProviderPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            S3ProviderPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelected(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PhotoSwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PhotoSliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
        Text(SyncFrequencyUi.formatHoursLabel(value), style = MaterialTheme.typography.labelSmall)
    }
}