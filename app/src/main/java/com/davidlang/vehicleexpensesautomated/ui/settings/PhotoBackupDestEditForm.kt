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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.DriveRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowserClient
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoDestination
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoProvider
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneConfStorage
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneDestConfig
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneS3Setup
import com.davidlang.vehicleexpensesautomated.data.sync.S3ProviderPreset
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi
import com.davidlang.vehicleexpensesautomated.data.sync.SyncRateLimit
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

@Composable
internal fun PhotoDestEditForm(
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
    var statusIsError by remember { mutableStateOf(false) }
    val failureStore = remember { SyncFailureStore(context) }
    var storedFailureDetail by remember {
        mutableStateOf(if (isNew) null else failureStore.photoFailure(id)?.message)
    }
    var folderId by remember { mutableStateOf(existing?.folderId ?: "") }
    var showBrowseDialog by remember { mutableStateOf(false) }
    var showRcloneRemotesDialog by remember { mutableStateOf(false) }
    var showRcloneWizard by remember { mutableStateOf(false) }
    var rcloneWizardMode by remember { mutableStateOf(RcloneWizardMode.CREATE) }
    var rcloneEditRemote by remember { mutableStateOf<String?>(null) }
    val consentRecovery = rememberConsentRecoveryHandle()
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
        storedFailureDetail = failureStore.photoFailure(id)?.message
        Toast.makeText(
            context,
            if (result.success) {
                AppStrings.get(R.string.settings_backup_complete)
            } else {
                SyncRateLimit.shortTitle(result.message, forSheets = false)
                    ?: AppStrings.get(R.string.settings_backup_failed_open_details)
            },
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.clearManualSyncResult()
    }
    val displayNameRequired = totalDestCount > 1
    val formTitle = when {
        isNew && provider == PhotoProvider.OTHER -> stringResource(R.string.settings_add_other_destination)
        isNew && provider == PhotoProvider.ONEDRIVE -> stringResource(R.string.settings_add_onedrive_destination)
        isNew && provider == PhotoProvider.S3 -> stringResource(R.string.settings_add_s3_destination)
        isNew -> stringResource(R.string.settings_add_google_drive_folder)
        else -> stringResource(R.string.settings_edit_destination)
    }

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

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = viewModel.auth.parseSignInResult(result.data)
            val email = account.email ?: ""
            viewModel.auth.persistAccountEmail(email)
            accountHint = email
            statusText = AppStrings.get(R.string.settings_signed_in_as_1_s, email)
            Toast.makeText(context, AppStrings.get(R.string.settings_signed_in_as_1_s_drive, email), Toast.LENGTH_SHORT).show()
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: ApiException) {
            statusText = AppStrings.get(R.string.settings_sign_in_failed_try_again_or_pick_a_google)
            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
        }
    }

    var driveReadonlyGranted by remember { mutableStateOf(viewModel.auth.hasReadonlyBrowseScope()) }

    val driveReadonlyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = viewModel.auth.parseSignInResult(result.data)
            val email = account.email ?: ""
            if (email.isNotBlank()) {
                viewModel.auth.persistAccountEmail(email)
            }
            driveReadonlyGranted = viewModel.auth.hasReadonlyBrowseScope()
            if (driveReadonlyGranted) {
                statusText = AppStrings.get(R.string.settings_drive_browse_access_granted)
            } else {
                statusText = AppStrings.get(R.string.settings_drive_browse_access_not_granted_try_again)
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: ApiException) {
            statusText = AppStrings.get(R.string.settings_drive_browse_sign_in_failed)
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
        statusText = if (ok) AppStrings.get(R.string.settings_rclone_conf_imported) else AppStrings.get(R.string.settings_failed_to_import_rclone_conf)
        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
    }

    SyncDestinationEditScaffold(onBack = onBack, title = formTitle) {
        if (provider == PhotoProvider.GOOGLE_DRIVE) {
            OutlinedButton(
                onClick = { signInLauncher.launch(viewModel.auth.signInIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (accountHint.isBlank()) stringResource(R.string.settings_sign_in_with_google_drive) else stringResource(R.string.settings_signed_in_1_s, accountHint))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (provider == PhotoProvider.ONEDRIVE) {
            val msHints = remember { viewModel.oneDriveAuth.deviceMicrosoftAccountHints() }
            OutlinedButton(
                onClick = {
                    val activity = context as? ComponentActivity
                    if (activity == null) {
                        Toast.makeText(context, AppStrings.get(R.string.settings_cannot_start_microsoft_sign_in), Toast.LENGTH_SHORT).show()
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
                            statusText = AppStrings.get(R.string.settings_signed_in_as_1_s, authResult.email)
                            Toast.makeText(context, AppStrings.get(R.string.settings_signed_in_as_1_s_onedrive, authResult.email), Toast.LENGTH_SHORT).show()
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            statusText = e.message ?: AppStrings.get(R.string.settings_microsoft_sign_in_failed)
                            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (accountHint.isBlank()) stringResource(R.string.settings_sign_in_with_microsoft_onedrive)
                    else stringResource(R.string.settings_using_1_s, accountHint),
                )
            }
            if (msHints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_device_accounts_1_s, msHints.joinToString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(stringResource(R.string.settings_destination), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = when (provider) {
                PhotoProvider.ONEDRIVE -> stringResource(R.string.settings_onedrive)
                PhotoProvider.S3 -> "S3"
                PhotoProvider.OTHER -> stringResource(R.string.reports_other_category)
                else -> stringResource(R.string.settings_google_drive)
            },
            onValueChange = {},
            label = { Text(stringResource(R.string.settings_provider)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = {
                Text(
                    if (displayNameRequired) stringResource(R.string.settings_display_name_required) else stringResource(R.string.settings_display_name_optional),
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
                    label = { Text(stringResource(R.string.settings_folder_url_optional)) },
                    supportingText = { Text(AppStrings.get(R.string.settings_paste_a_link_or_use_browse)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (accountHint.isBlank() && viewModel.auth.getLastAccount() == null) {
                            Toast.makeText(context, AppStrings.get(R.string.settings_sign_in_first), Toast.LENGTH_SHORT).show()
                        } else {
                            showBrowseDialog = true
                        }
                    },
                ) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.settings_browse_folders))
                }
            }
            if (folderName.isNotBlank() || folderId.isNotBlank()) {
                OutlinedTextField(
                    value = folderName.ifBlank { folderId.take(12) + if (folderId.length > 12) "…" else "" },
                    onValueChange = {},
                    label = { Text(stringResource(R.string.settings_selected_folder)) },
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
                        statusText = AppStrings.get(R.string.settings_selected_1_s, item.name)
                    },
                    enableHybridCatalog = true,
                    readonlyAccessGranted = driveReadonlyGranted,
                    onRequestReadonlyAccess = {
                        driveReadonlyLauncher.launch(viewModel.auth.readonlyBrowseSignInIntent())
                    },
                    listItems = { search, catalog ->
                        try {
                            viewModel.listFoldersForBrowse(accountHint, search, catalog)
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: DriveRecoverableAuthException) {
                            throw e
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            throw DriveAuthRecovery.wrapIfRecoverable(e)
                        }
                    },
                    createItem = { name ->
                        try {
                            viewModel.createFolderForBrowse(accountHint, name)
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: DriveRecoverableAuthException) {
                            throw e
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            throw DriveAuthRecovery.wrapIfRecoverable(e)
                        }
                    },
                    emptyMessage =
                        stringResource(R.string.settings_no_folders_visible_create_one_here_or_open_an_ex),
                )
            }
        } else if (provider == PhotoProvider.S3) {
            Text(stringResource(R.string.settings_s3_storage), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_s3_and_compatible_services_wasabi_cloudflare_r2_),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { SyncSetupDocs.open(context, SyncSetupDocs.photo("minio-s3-compatible")) },
            ) {
                Text(stringResource(R.string.settings_setup_help_minio_s3_compatible))
            }
            Spacer(modifier = Modifier.height(8.dp))
            S3ProviderDropdown(
                selected = s3ProviderPreset,
                onSelected = { s3ProviderPreset = it },
            )
            OutlinedTextField(
                value = s3AccessKey,
                onValueChange = { s3AccessKey = it },
                label = { Text(stringResource(R.string.settings_access_key_id)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3SecretKey,
                onValueChange = { s3SecretKey = it },
                label = { Text(stringResource(R.string.settings_secret_access_key)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = s3Region,
                onValueChange = { s3Region = it },
                label = { Text(stringResource(R.string.settings_region)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3Endpoint,
                onValueChange = { s3Endpoint = it },
                label = { Text(stringResource(R.string.settings_endpoint_advanced)) },
                supportingText = { Text(AppStrings.get(R.string.settings_required_for_minio_and_custom_s3_compatible_host)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = s3Bucket,
                onValueChange = { s3Bucket = it },
                label = { Text(stringResource(R.string.settings_bucket)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text(stringResource(R.string.settings_path_prefix)) },
                supportingText = { Text(AppStrings.get(R.string.settings_e_g_vehicleexpenses_photos)) },
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
                                statusText = AppStrings.get(R.string.settings_s3_credentials_saved)
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                            // kept: failure stays on this call so the screen or caller can continue
                            } catch (e: Exception) {
                                statusText = e.message ?: AppStrings.get(R.string.settings_failed_to_save_s3_credentials)
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_save_s3_credentials))
                }
            }
        } else if (provider == PhotoProvider.ONEDRIVE) {
            Text(stringResource(R.string.settings_folder_on_onedrive), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_photos_upload_under_this_path_on_your_signed_in_),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text(stringResource(R.string.settings_folder_path)) },
                supportingText = { Text(AppStrings.get(R.string.settings_e_g_vehicleexpenses_photos)) },
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
                                statusText = AppStrings.get(R.string.settings_onedrive_account_refreshed)
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                            // kept: failure stays on this call so the screen or caller can continue
                            } catch (e: Exception) {
                                statusText = e.message ?: AppStrings.get(R.string.settings_re_authentication_failed)
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_re_authenticate_microsoft_account))
                }
            }
        } else {
            Text(stringResource(R.string.settings_remotes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_create_a_remote_on_this_device_or_pick_one_from_),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.photosReadme()) }) {
                Text(stringResource(R.string.settings_self_hosted_photo_backup_setup))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = rcloneRemote,
                    onValueChange = { rcloneRemote = it },
                    label = { Text(stringResource(R.string.settings_remote_name)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRcloneRemotesDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.settings_list_remotes))
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
                Text(stringResource(R.string.settings_create_new_remote))
            }
            OutlinedTextField(
                value = rclonePrefix,
                onValueChange = { rclonePrefix = it },
                label = { Text(stringResource(R.string.settings_path_prefix_on_remote)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = { importConfLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(if (confImported) stringResource(R.string.settings_import_existing_conf_re_import) else stringResource(R.string.settings_import_existing_conf))
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
                        statusText = AppStrings.get(R.string.settings_selected_remote_1_s, remote)
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
                        statusText = AppStrings.get(R.string.settings_remote_saved_1_s, name)
                        Toast.makeText(context, AppStrings.get(R.string.settings_remote_1_s_saved, name), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        SyncBackgroundScheduleSection(
            title = stringResource(R.string.settings_background_backup),
            enableLabel = stringResource(R.string.settings_enable_background_backup),
            intervalSliderLabel = stringResource(R.string.settings_background_backup_interval_hours),
            state = SyncScheduleUiState(
                enabled = enabled,
                wifiOnly = wifiOnly,
                chargingOnly = chargingOnly,
                frequencyHours = frequencyHours,
            ),
            onEnabledChange = { enabled = it },
            onWifiOnlyChange = { wifiOnly = it },
            onChargingOnlyChange = { chargingOnly = it },
            onFrequencyHoursChange = { frequencyHours = SyncFrequencyUi.snapHours(it) },
        )

        Spacer(modifier = Modifier.height(16.dp))
        val canSyncThisDest = !isNew &&
            store.allPhoto().find { it.id == id }?.let {
                SyncDestinationStore.isPhotoConfigured(it, context)
            } == true
        SyncDestinationEditFooter(
            testButtonLabel = when (provider) {
                PhotoProvider.GOOGLE_DRIVE -> stringResource(R.string.settings_test_upload_this_destination)
                else -> stringResource(R.string.settings_test_connection_this_destination)
            },
            onTest = {
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
                            Toast.makeText(context, AppStrings.get(R.string.settings_configure_a_destination_first), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (provider == PhotoProvider.GOOGLE_DRIVE &&
                            accountHint.isBlank() && viewModel.auth.getLastAccount() == null
                        ) {
                            Toast.makeText(context, AppStrings.get(R.string.settings_sign_in_first), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (provider == PhotoProvider.ONEDRIVE) {
                            if (accountHint.isBlank()) {
                                Toast.makeText(context, AppStrings.get(R.string.settings_sign_in_with_microsoft_first), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val cfg = RcloneDestConfig.parse(configJson)
                            if (cfg == null) {
                                Toast.makeText(context, AppStrings.get(R.string.settings_set_a_folder_path_first), Toast.LENGTH_SHORT).show()
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
                                        AppStrings.get(R.string.settings_enter_access_key_and_secret_or_save_s3_credentia),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                            }
                        } else if (provider == PhotoProvider.OTHER) {
                            val cfg = RcloneDestConfig.parse(configJson)
                            if (cfg == null || rcloneRemote.isBlank()) {
                                Toast.makeText(context, AppStrings.get(R.string.settings_create_or_select_a_remote_first), Toast.LENGTH_SHORT).show()
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
                            statusIsError = !result.success
                            statusText = result.message
                            if (result.success) {
                                store.allPhoto().find { it.id == id }?.folderId?.let { folderId = it }
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            } else if (result.needsRemoteConsent && result.recoveryIntent != null && allowRecovery) {
                                consentRecovery.launch(result.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: DriveRecoverableAuthException) {
                            statusIsError = true
                            statusText = e.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE
                            if (allowRecovery) {
                                consentRecovery.launch(e.recoveryIntent) { runTest(allowRecovery = false) }
                            } else {
                                Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                            }
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            statusIsError = true
                            statusText = DriveAuthRecovery.userMessage(e)
                            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                runTest(allowRecovery = true)
            },
            onSyncNow = if (isNew) {
                null
            } else {
                {
                    if (!canSyncThisDest) {
                        Toast.makeText(
                            context,
                            AppStrings.get(R.string.settings_save_a_configured_destination_first),
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
            failureDialogTitle = displayName.ifBlank { AppStrings.get(R.string.settings_photo_backup_failure) },
            showRemove = !isNew,
            onRemove = {
                store.removePhoto(id)
                viewModel.rescheduleBackgroundBackup()
                Toast.makeText(context, AppStrings.get(R.string.settings_destination_removed), Toast.LENGTH_SHORT).show()
                onRemoved()
            },
            statusText = footerStatus,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun S3ProviderDropdown(
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
            label = { Text(stringResource(R.string.settings_provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
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
