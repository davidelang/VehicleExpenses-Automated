package com.davidlang.vehicleexpensesautomated.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.email.EmailReceiptPrefs
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.PumpOcrSettings
import com.davidlang.vehicleexpensesautomated.ui.util.FillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Currency
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

private fun mediaImagesPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun hasMediaImagesPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, mediaImagesPermission()) ==
        PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    experimentsAvailable: Boolean = false,
    ingestOfflineFixtures: (suspend () -> String)? = null,
    experimentExtras: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val viewModel: SettingsViewModel = hiltViewModel()
    RegisterPageHelp(
        title = AppStrings.get(R.string.nav_settings),
        AppStrings.get(R.string.settings_units_photo_save_toggles_debug_fill_and_ex),
        AppStrings.get(R.string.settings_spreadsheet_and_photo_destinations_are_under_men),
        AppStrings.get(R.string.settings_show_experiment_screens_reveals_alignment_pump_e),
    )
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState(initial = emptyList())
    val hasFuelData = fuelEntries.isNotEmpty()
    val csvManager = viewModel.csvManager
    val scope = rememberCoroutineScope()

    var saveFuelPhotos by remember { mutableStateOf(prefs.getBoolean("save_fuel_photos", true)) }
    var saveExpensePhotos by remember { mutableStateOf(prefs.getBoolean("save_expense_photos", true)) }
    var debugFill by remember {
        mutableStateOf(
            prefs.getBoolean("debug_capture", false)
        )
    }
    var showExperimentScreens by remember {
        mutableStateOf(prefs.getBoolean("show_experiment_screens", false))
    }
    val experimentsEnabled = experimentsAvailable && showExperimentScreens
    var debugMaxSessions by remember { mutableIntStateOf(prefs.getInt("debug_fill_max_sessions", 10)) }
    var pumpMaxRedBoxes by remember {
        mutableIntStateOf(prefs.getInt(PumpOcrSettings.KEY_MAX_RED_BOXES, PumpOcrSettings.DEFAULT_MAX_RED_BOXES))
    }
    var pumpLabelYBandExtra by remember {
        mutableStateOf(
            prefs.getFloat(
                PumpOcrSettings.KEY_LABEL_Y_BAND_EXTRA_FRACTION,
                PumpOcrSettings.DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION,
            ).toString(),
        )
    }
    var pumpRatioBandLo by remember {
        mutableStateOf(
            prefs.getFloat(PumpOcrSettings.KEY_RATIO_BAND_LO, PumpOcrSettings.DEFAULT_RATIO_BAND_LO).toString(),
        )
    }
    var pumpRatioBandHi by remember {
        mutableStateOf(
            prefs.getFloat(PumpOcrSettings.KEY_RATIO_BAND_HI, PumpOcrSettings.DEFAULT_RATIO_BAND_HI).toString(),
        )
    }
    var darkModePref by remember { mutableStateOf(prefs.getString("dark_mode", "system") ?: "system") }
    var shutterSounds by remember { mutableStateOf(prefs.getBoolean("shutter_sounds", true)) }
    var currencySymbol by remember {
        mutableStateOf(
            if (prefs.contains("currency_symbol")) {
                prefs.getString("currency_symbol", "system") ?: "system"
            } else {
                "system"
            }
        )
    }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    val systemCurrencySymbol = remember {
        try {
            Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
        // kept: failure stays on this call so the screen or caller can continue
        } catch (_: Exception) {
            "$"
        }
    }
    val shortCurrencySymbols = listOf("system", "$", "€", "£", "CA$", "A$", "¥")
    val shortCurrencyOptions = shortCurrencySymbols.map { sym ->
        if (sym == "system") AppStrings.get(R.string.settings_system_default_currency, systemCurrencySymbol) else sym
    } + AppStrings.get(R.string.settings_see_more_currencies)
    var volumeUnit by remember {
        mutableStateOf(
            if (prefs.contains("volume_unit")) {
                prefs.getString("volume_unit", "system") ?: "system"
            } else {
                "system"
            }
        )
    }
    var pendingVolumeUnit by remember { mutableStateOf<String?>(null) }
    var showVolumeConvertDialog by remember { mutableStateOf(false) }
    val systemVolumeLabel = remember {
        if (VolumeUnits.systemDefaultUnit() == VolumeUnits.LITERS) {
            AppStrings.get(R.string.settings_volume_system_default_liters)
        } else {
            AppStrings.get(R.string.settings_volume_system_default_gallons)
        }
    }
    val volumeOptions = listOf(systemVolumeLabel, AppStrings.get(R.string.settings_volume_gallons_g), AppStrings.get(R.string.settings_volume_liters_l))

    fun resolveVolumePref(pref: String): String {
        if (pref == VolumeUnits.GALLONS || pref == VolumeUnits.LITERS) return pref
        return VolumeUnits.systemDefaultUnit()
    }

    fun volumeDisplayLabel(pref: String): String = when (pref) {
        "system" -> systemVolumeLabel
        VolumeUnits.GALLONS -> AppStrings.get(R.string.settings_volume_gallons_g)
        VolumeUnits.LITERS -> AppStrings.get(R.string.settings_volume_liters_l)
        else -> systemVolumeLabel
    }

    fun volumePrefFromLabel(label: String): String = when (label) {
        systemVolumeLabel -> "system"
        AppStrings.get(R.string.settings_volume_gallons_g) -> VolumeUnits.GALLONS
        AppStrings.get(R.string.settings_volume_liters_l) -> VolumeUnits.LITERS
        else -> VolumeUnits.LITERS
    }

    var status by remember { mutableStateOf(AppStrings.get(R.string.settings_ready)) }
    var showClearDebugConfirm by remember { mutableStateOf(false) }
    var showDebugInfoDialog by remember { mutableStateOf(false) }
    var debugDataRefreshKey by remember { mutableIntStateOf(0) }
    val debugSessions = remember(debugDataRefreshKey) { FillDebugStore.listSessions(context) }
    val crashReports = remember(debugDataRefreshKey) { FillDebugStore.listCrashReports(context) }
    val hasDebugData = debugSessions.isNotEmpty() || crashReports.isNotEmpty()
    val debugReportCount = debugSessions.size + crashReports.size
    var showSendReportPicker by remember { mutableStateOf(false) }
    var selectedSessionPaths by remember { mutableStateOf(setOf<String>()) }
    var selectedCrashPaths by remember { mutableStateOf(setOf<String>()) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                AppStrings.get(R.string.settings_photos_permission_denied_grant_photos_acce),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestMediaPermissionIfNeeded() {
        if (hasMediaImagesPermission(context)) return
        mediaPermissionLauncher.launch(mediaImagesPermission())
    }

    // Once when Settings opens with save-photos already on and permission missing (no spam loop).
    LaunchedEffect(Unit) {
        if (saveFuelPhotos && !hasMediaImagesPermission(context)) {
            requestMediaPermissionIfNeeded()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch { csvManager.exportToZip(); status = AppStrings.get(R.string.settings_exported); Toast.makeText(context, AppStrings.get(R.string.settings_csv_zip_exported), Toast.LENGTH_LONG).show() } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { csvManager.importFromZip(uri); status = AppStrings.get(R.string.settings_imported); Toast.makeText(context, AppStrings.get(R.string.settings_csv_import_complete), Toast.LENGTH_LONG).show() } }
    }

    LaunchedEffect(debugMaxSessions) {
        prefs.edit().putInt("debug_fill_max_sessions", debugMaxSessions.coerceIn(1, 50)).apply()
        FillDebugStore.pruneToMax(context)
    }

    LaunchedEffect(
        saveFuelPhotos,
        saveExpensePhotos,
        debugFill,
        showExperimentScreens,
        darkModePref,
        shutterSounds,
        currencySymbol,
        volumeUnit,
        pumpMaxRedBoxes,
        pumpLabelYBandExtra,
        pumpRatioBandLo,
        pumpRatioBandHi,
    ) {
        val yExtra = pumpLabelYBandExtra.toFloatOrNull()
            ?.coerceIn(0f, 1f) ?: PumpOcrSettings.DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION
        val rLo = pumpRatioBandLo.toFloatOrNull()
            ?.coerceIn(PumpOcrSettings.MIN_RATIO_BAND, PumpOcrSettings.MAX_RATIO_BAND)
            ?: PumpOcrSettings.DEFAULT_RATIO_BAND_LO
        val rHi = pumpRatioBandHi.toFloatOrNull()
            ?.coerceIn(PumpOcrSettings.MIN_RATIO_BAND, PumpOcrSettings.MAX_RATIO_BAND)
            ?: PumpOcrSettings.DEFAULT_RATIO_BAND_HI
        prefs.edit().apply {
            putBoolean("save_fuel_photos", saveFuelPhotos)
            putBoolean("save_expense_photos", saveExpensePhotos)
            putBoolean("debug_capture", debugFill)
            putBoolean("show_experiment_screens", showExperimentScreens)
            putString("dark_mode", darkModePref)
            putBoolean("shutter_sounds", shutterSounds)
            putString("currency_symbol", currencySymbol)
            putString("volume_unit", volumeUnit)
            putInt(
                PumpOcrSettings.KEY_MAX_RED_BOXES,
                pumpMaxRedBoxes.coerceIn(
                    PumpOcrSettings.MIN_MAX_RED_BOXES,
                    PumpOcrSettings.MAX_MAX_RED_BOXES,
                ),
            )
            putFloat(PumpOcrSettings.KEY_LABEL_Y_BAND_EXTRA_FRACTION, yExtra)
            putFloat(PumpOcrSettings.KEY_RATIO_BAND_LO, rLo)
            putFloat(PumpOcrSettings.KEY_RATIO_BAND_HI, maxOf(rLo, rHi))
            apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(AppStrings.get(R.string.settings_general), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            AppStrings.get(R.string.settings_sync_lives_under),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsEmailReceipts() {
            // Email loyalty receipts (Gmail label → Room Unassigned fuel rows)
            val emailPrefs = remember { EmailReceiptPrefs(context) }
            var emailPollEnabled by remember { mutableStateOf(emailPrefs.enabled) }
            var emailGmailEnabled by remember {
                mutableStateOf(
                    emailPrefs.source != EmailReceiptPrefs.SOURCE_IMAP || !emailPrefs.imapEnabled,
                )
            }
            var emailImapEnabled by remember {
                mutableStateOf(
                    emailPrefs.imapEnabled ||
                        emailPrefs.source == EmailReceiptPrefs.SOURCE_IMAP ||
                        emailPrefs.source == EmailReceiptPrefs.SOURCE_BOTH,
                )
            }
            var emailLabel by remember {
                mutableStateOf(
                    emailPrefs.labelName.ifBlank { EmailReceiptPrefs.DEFAULT_LABEL },
                )
            }
            var emailAccount by remember { mutableStateOf(emailPrefs.accountEmail.orEmpty()) }
            var imapHost by remember { mutableStateOf(emailPrefs.imapHost) }
            var imapPort by remember { mutableStateOf(emailPrefs.imapPort.toString()) }
            var imapUser by remember { mutableStateOf(emailPrefs.imapUsername) }
            var imapPassword by remember { mutableStateOf(emailPrefs.imapPassword) }
            var imapFolder by remember {
                mutableStateOf(emailPrefs.imapFolder.ifBlank { EmailReceiptPrefs.DEFAULT_IMAP_FOLDER })
            }
            var emailLastSummary by remember { mutableStateOf(emailPrefs.lastRunSummary) }
            var emailOfflineBusy by remember { mutableStateOf(false) }
            var emailPollWatchToken by remember { mutableIntStateOf(0) }
            fun persistEmailSource() {
                emailPrefs.enabled = emailPollEnabled
                emailPrefs.imapEnabled = emailImapEnabled
                emailPrefs.source = when {
                    emailGmailEnabled && emailImapEnabled -> EmailReceiptPrefs.SOURCE_BOTH
                    emailImapEnabled -> EmailReceiptPrefs.SOURCE_IMAP
                    else -> EmailReceiptPrefs.SOURCE_GMAIL
                }
                emailPrefs.labelName = emailLabel
                emailPrefs.imapHost = imapHost
                emailPrefs.imapPort = imapPort.toIntOrNull() ?: EmailReceiptPrefs.DEFAULT_IMAP_PORT
                emailPrefs.imapUsername = imapUser
                emailPrefs.imapPassword = imapPassword
                emailPrefs.imapFolder = imapFolder
                viewModel.rescheduleEmailReceiptPoll()
            }
            // Reload last-run when Settings is shown / recomposed after nav
            LaunchedEffect(Unit) {
                emailLastSummary = emailPrefs.lastRunSummary
            }
            // After Poll now: light delayed reloads so async worker summary appears without leaving
            LaunchedEffect(emailPollWatchToken) {
                if (emailPollWatchToken == 0) return@LaunchedEffect
                val delaysMs = longArrayOf(500L, 1500L, 3000L, 5000L)
                for (d in delaysMs) {
                    kotlinx.coroutines.delay(d)
                    emailLastSummary = emailPrefs.lastRunSummary
                }
            }
            val emailSignInLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                try {
                    val account = viewModel.parseEmailReceiptSignIn(result.data)
                    val email = account.email?.trim().orEmpty()
                    if (email.isNotEmpty()) {
                        emailPrefs.accountEmail = email
                        emailAccount = email
                        Toast.makeText(context, AppStrings.get(R.string.settings_gmail_account_1_s, email), Toast.LENGTH_SHORT).show()
                    }
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        AppStrings.get(R.string.settings_gmail_sign_in_failed_1_s, e.message?.take(80).orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            Text(stringResource(R.string.settings_email_receipts_shell_sam_s_club), style = MaterialTheme.typography.titleMedium)
            Text(
                (if (experimentsEnabled) stringResource(R.string.settings_offline_packaged_samples_unassigned_fuel) else "") +
                    stringResource(R.string.settings_live_gmail_label_oauth_and_or_generic_imap) +
                    stringResource(R.string.settings_same_label_folder_may_hold_multiple_vendor) +
                    stringResource(R.string.settings_rows_vehicle_unassigned_id_0_odometer_0_pa) +
                    stringResource(R.string.settings_imap_uses_tls_port_993_passwords_are_not_w),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val ingestOffline = ingestOfflineFixtures
            if (experimentsEnabled && ingestOffline != null) {
                OutlinedButton(
                    onClick = {
                        if (emailOfflineBusy) return@OutlinedButton
                        emailOfflineBusy = true
                        scope.launch {
                            try {
                                val summary = withContext(Dispatchers.IO) {
                                    ingestOffline()
                                }
                                emailLastSummary = summary
                                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                            // kept: failure stays on this call so the screen or caller can continue
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    AppStrings.get(R.string.settings_offline_ingest_failed_1_s, e.message?.take(100).orEmpty()),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                emailOfflineBusy = false
                            }
                        }
                    },
                    enabled = !emailOfflineBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (emailOfflineBusy) stringResource(R.string.settings_ingesting_sample_receipts)
                        else stringResource(R.string.settings_ingest_sample_receipts_offline_goldens),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            SwitchSetting(stringResource(R.string.settings_enable_scheduled_email_poll), emailPollEnabled) { enabled ->
                emailPollEnabled = enabled
                persistEmailSource()
            }
            SwitchSetting(stringResource(R.string.settings_use_gmail_oauth_label), emailGmailEnabled) { on ->
                emailGmailEnabled = on
                if (!on && !emailImapEnabled) emailImapEnabled = true
                persistEmailSource()
            }
            OutlinedTextField(
                value = emailLabel,
                onValueChange = {
                    emailLabel = it
                    emailPrefs.labelName = it
                },
                label = { Text(stringResource(R.string.settings_gmail_label_name)) },
                singleLine = true,
                enabled = emailGmailEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (emailAccount.isBlank()) stringResource(R.string.settings_gmail_account_not_signed_in)
                else stringResource(R.string.settings_gmail_account_1_s, emailAccount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { emailSignInLauncher.launch(viewModel.emailReceiptSignInIntent()) },
                enabled = emailGmailEnabled,
            ) {
                Text(stringResource(R.string.settings_sign_in_for_gmail))
            }
            Spacer(modifier = Modifier.height(12.dp))
            SwitchSetting(stringResource(R.string.settings_use_imap_folder), emailImapEnabled) { on ->
                emailImapEnabled = on
                if (!on && !emailGmailEnabled) emailGmailEnabled = true
                persistEmailSource()
            }
            OutlinedTextField(
                value = imapHost,
                onValueChange = { imapHost = it },
                label = { Text(stringResource(R.string.settings_imap_host_e_g_imap_gmail_com)) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapPort,
                onValueChange = { imapPort = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.settings_imap_port_993_tls)) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapUser,
                onValueChange = { imapUser = it },
                label = { Text(stringResource(R.string.settings_imap_username)) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapPassword,
                onValueChange = { imapPassword = it },
                label = { Text(stringResource(R.string.settings_imap_password_app_password)) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapFolder,
                onValueChange = { imapFolder = it },
                label = { Text(stringResource(R.string.settings_imap_folder_e_g_inbox_or_receipts)) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    persistEmailSource()
                    emailPrefs.enabled = true
                    emailPollEnabled = true
                    viewModel.pollEmailReceiptsNow()
                    Toast.makeText(context, AppStrings.get(R.string.settings_email_receipt_poll_queued), Toast.LENGTH_SHORT).show()
                    emailLastSummary = emailPrefs.lastRunSummary
                    emailPollWatchToken++
                },
                enabled = emailPollEnabled && (
                    (emailGmailEnabled && emailLabel.isNotBlank()) ||
                        (emailImapEnabled && imapHost.isNotBlank() && imapUser.isNotBlank())
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_poll_now))
            }
            TextButton(onClick = {
                emailLastSummary = viewModel.readEmailReceiptLastSummary()
            }) {
                Text(stringResource(R.string.settings_refresh_last_run))
            }
            if (emailLastSummary.isNotBlank()) {
                Text(
                    stringResource(R.string.settings_last_run_1_s, emailLastSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsEmailReceipts()
        Spacer(modifier = Modifier.height(16.dp))

        SwitchSetting(AppStrings.get(R.string.settings_save_fill_and_trip_photos), saveFuelPhotos) { enabled ->
            saveFuelPhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting(AppStrings.get(R.string.settings_save_receipt_photos), saveExpensePhotos) { enabled ->
            saveExpensePhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting(AppStrings.get(R.string.settings_play_shutter_sound), shutterSounds) { shutterSounds = it }
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsFillDebugRow() {
            // Order: title · Info · count/max · Delete · Send · toggle
            // Wide: one line. Narrow: line1 title·Info·toggle; line2 count·Delete·Send end-aligned.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wideEnough = maxWidth >= 420.dp
                val countMaxBlock: @Composable RowScope.() -> Unit = {
                    Text(
                        "$debugReportCount /",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    OutlinedTextField(
                        value = debugMaxSessions.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.coerceIn(1, 50)?.let { debugMaxSessions = it }
                        },
                        modifier = Modifier
                            .widthIn(min = 48.dp, max = 72.dp)
                            .weight(1f, fill = false),
                        singleLine = true,
                    )
                }
                val deleteSendBlock: @Composable () -> Unit = {
                    IconButton(
                        onClick = { showClearDebugConfirm = true },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_delete_debug_reports),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!hasDebugData) {
                                Toast.makeText(context, AppStrings.get(R.string.settings_no_debug_reports_to_send), Toast.LENGTH_SHORT).show()
                            } else {
                                selectedSessionPaths = emptySet()
                                selectedCrashPaths = emptySet()
                                showSendReportPicker = true
                            }
                        },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.settings_send_debug_reports),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (wideEnough) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_debug_fill),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            softWrap = true,
                        )
                        IconButton(
                            onClick = { showDebugInfoDialog = true },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.settings_about_debug_fill))
                        }
                        countMaxBlock()
                        deleteSendBlock()
                        Switch(checked = debugFill, onCheckedChange = { debugFill = it })
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_debug_fill),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                softWrap = true,
                            )
                            IconButton(
                                onClick = { showDebugInfoDialog = true },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.settings_about_debug_fill))
                            }
                            Switch(checked = debugFill, onCheckedChange = { debugFill = it })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        ) {
                            countMaxBlock()
                            deleteSendBlock()
                        }
                    }
                }
            }
        }
        SettingsFillDebugRow()
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsSetupTips() {
            Text(stringResource(R.string.nav_setup_tips), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_first_run_walkthroughs_for_adding_a_vehicle_or_c),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { navController.navigate("tutorial/tutorial_add_vehicle") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_tutorial_add_a_vehicle))
            }
            OutlinedButton(
                onClick = { navController.navigate("tutorial/tutorial_setup_sync") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_tutorial_connect_existing_setup))
            }
            OutlinedButton(
                onClick = { navController.navigate("onboarding") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.settings_show_first_run_welcome))
            }
        }
        SettingsSetupTips()
        if (experimentsAvailable) {
        SwitchSetting(AppStrings.get(R.string.settings_show_experiment_screens_dev), showExperimentScreens) { showExperimentScreens = it }
        if (experimentsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(AppStrings.get(R.string.settings_pump_ocr_advanced), style = MaterialTheme.typography.titleSmall)
            Text(
                AppStrings.get(R.string.settings_label_y_band_uses_smallest_value_cluster_r) +
                    AppStrings.get(R.string.settings_ratio_lo_hi_are_band_gated_extreme_checks),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = pumpMaxRedBoxes.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.coerceIn(
                        PumpOcrSettings.MIN_MAX_RED_BOXES,
                        PumpOcrSettings.MAX_MAX_RED_BOXES,
                    )?.let { pumpMaxRedBoxes = it }
                },
                label = { Text(AppStrings.get(R.string.settings_pump_max_red_boxes_kept_1_s_2_s, PumpOcrSettings.MIN_MAX_RED_BOXES, PumpOcrSettings.MAX_MAX_RED_BOXES)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpLabelYBandExtra,
                onValueChange = { pumpLabelYBandExtra = it },
                label = { Text(AppStrings.get(R.string.settings_label_y_band_extra_fraction_of_smallest_value_re)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandLo,
                onValueChange = { pumpRatioBandLo = it },
                label = { Text(AppStrings.get(R.string.settings_cost_vol_ratio_band_low_gal)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandHi,
                onValueChange = { pumpRatioBandHi = it },
                label = { Text(AppStrings.get(R.string.settings_cost_vol_ratio_band_high_gal)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            experimentExtras()
        }
        }
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsDialogs() {
            if (showDebugInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showDebugInfoDialog = false },
                    title = { Text(stringResource(R.string.settings_debug_fill)) },
                    text = {
                        Text(
                            stringResource(R.string.settings_this_captures_images_and_ocr_details_durin),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showDebugInfoDialog = false }) {
                            Text(stringResource(R.string.settings_ok))
                        }
                    },
                )
            }
            if (showSendReportPicker) {
                AlertDialog(
                    onDismissRequest = { showSendReportPicker = false },
                    title = { Text(stringResource(R.string.settings_select_items_to_attach)) },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            if (debugSessions.isNotEmpty()) {
                                item { Text(stringResource(R.string.settings_fill_sessions), style = MaterialTheme.typography.titleSmall) }
                                items(debugSessions, key = { it.absolutePath }) { session ->
                                    val path = session.absolutePath
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Checkbox(
                                            checked = path in selectedSessionPaths,
                                            onCheckedChange = { checked ->
                                                selectedSessionPaths = if (checked) {
                                                    selectedSessionPaths + path
                                                } else {
                                                    selectedSessionPaths - path
                                                }
                                            },
                                        )
                                        Text(
                                            FillDebugStore.readSessionSummary(session),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            if (crashReports.isNotEmpty()) {
                                item { Text(stringResource(R.string.settings_crash_reports), style = MaterialTheme.typography.titleSmall) }
                                items(crashReports, key = { it.absolutePath }) { crash ->
                                    val path = crash.absolutePath
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Checkbox(
                                            checked = path in selectedCrashPaths,
                                            onCheckedChange = { checked ->
                                                selectedCrashPaths = if (checked) {
                                                    selectedCrashPaths + path
                                                } else {
                                                    selectedCrashPaths - path
                                                }
                                            },
                                        )
                                        Text(crash.name, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val attachments = FillDebugStore.collectAttachmentFiles(
                                selectedSessionPaths,
                                selectedCrashPaths,
                            )
                            if (attachments.isEmpty()) {
                                Toast.makeText(context, AppStrings.get(R.string.settings_select_at_least_one_item), Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val launched = FillDebugStore.launchFailureReport(context, attachments)
                            if (launched) {
                                showSendReportPicker = false
                            } else {
                                Toast.makeText(
                                    context,
                                    AppStrings.get(R.string.settings_no_email_app_found_to_send_report),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }) {
                            Text(stringResource(R.string.settings_send))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSendReportPicker = false }) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    },
                )
            }
            if (showVolumeConvertDialog && pendingVolumeUnit != null) {
                val targetPref = pendingVolumeUnit!!
                AlertDialog(
                    onDismissRequest = {
                        pendingVolumeUnit = null
                        showVolumeConvertDialog = false
                    },
                    title = { Text(stringResource(R.string.settings_convert_fuel_volumes)) },
                    text = {
                        Text(
                            stringResource(R.string.settings_convert_existing_fuel_volumes_between_g_an) +
                                stringResource(R.string.settings_if_you_choose_no_historical_numbers_keep_t) +
                                stringResource(R.string.settings_display_labels_will_change_without_conversion),
                        )
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = {
                                pendingVolumeUnit = null
                                showVolumeConvertDialog = false
                            }) {
                                Text(stringResource(R.string.settings_cancel))
                            }
                            TextButton(onClick = {
                                volumeUnit = targetPref
                                pendingVolumeUnit = null
                                showVolumeConvertDialog = false
                            }) {
                                Text(stringResource(R.string.settings_no))
                            }
                            TextButton(onClick = {
                                val fromUnit = resolveVolumePref(volumeUnit)
                                val toUnit = resolveVolumePref(targetPref)
                                fuelViewModel.convertAllVolumes(fromUnit, toUnit) {
                                    volumeUnit = targetPref
                                    pendingVolumeUnit = null
                                    showVolumeConvertDialog = false
                                }
                            }) {
                                Text(stringResource(R.string.settings_yes))
                            }
                        }
                    },
                )
            }
            if (showClearDebugConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearDebugConfirm = false },
                    title = { Text(stringResource(R.string.settings_clear_debug_data)) },
                    text = { Text(stringResource(R.string.settings_deletes_all_fill_debug_sessions_and_crash)) },
                    confirmButton = {
                        TextButton(onClick = {
                            FillDebugStore.clearAllDebugData(context)
                            debugDataRefreshKey++
                            showClearDebugConfirm = false
                            Toast.makeText(context, AppStrings.get(R.string.settings_debug_data_cleared), Toast.LENGTH_SHORT).show()
                        }) {
                            Text(stringResource(R.string.settings_clear))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDebugConfirm = false }) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    },
                )
            }
        }
        SettingsDialogs()

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsLocalization() {
            var appLanguagePref by remember { mutableStateOf(AppLanguage.readPrefForUi(context)) }
            var languageMenuExpanded by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.settings_localization_units), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            val languageLabel = when (appLanguagePref) {
                AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                else -> {
                    val loc = AppLanguage.SUPPORTED.firstOrNull { it.prefTag == appLanguagePref }
                    if (loc != null) stringResource(loc.displayNameRes) else stringResource(R.string.lang_name_en)
                }
            }
            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded,
                onExpandedChange = { languageMenuExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = languageLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_language_system)) },
                        onClick = {
                            appLanguagePref = AppLanguage.SYSTEM
                            AppLanguage.setPref(context, AppLanguage.SYSTEM)
                            languageMenuExpanded = false
                        },
                    )
                    AppLanguage.SUPPORTED.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(stringResource(loc.displayNameRes)) },
                            onClick = {
                                appLanguagePref = loc.prefTag
                                AppLanguage.setPref(context, loc.prefTag)
                                languageMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.settings_language_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    DropdownSetting(
                        label = stringResource(R.string.settings_default_currency_2),
                        selectedValue = if (currencySymbol == "system") {
                            stringResource(R.string.settings_system_default_currency, systemCurrencySymbol)
                        } else {
                            currencySymbol
                        },
                        options = shortCurrencyOptions,
                        onValueChange = { selected ->
                            when (selected) {
                                AppStrings.get(R.string.settings_system_default_currency, systemCurrencySymbol) ->
                                    currencySymbol = "system"
                                AppStrings.get(R.string.settings_see_more_currencies) -> showMoreCurrencies = true
                                else -> currencySymbol = selected
                            }
                        },
                    )
                }
                if (showMoreCurrencies) {
                    val allCurrencies = remember {
                        Currency.getAvailableCurrencies().sortedBy { it.currencyCode }
                    }
                    AlertDialog(
                        onDismissRequest = { showMoreCurrencies = false },
                        title = { Text(stringResource(R.string.settings_all_currencies)) },
                        text = {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(allCurrencies, key = { it.currencyCode }) { currency ->
                                    val sym = try {
                                        currency.getSymbol(Locale.getDefault())
                                    // kept: failure stays on this call so the screen or caller can continue
                                    } catch (_: Exception) {
                                        currency.currencyCode
                                    }
                                    TextButton(onClick = {
                                        currencySymbol = sym
                                        showMoreCurrencies = false
                                    }) {
                                        Text("${currency.currencyCode} ($sym)")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showMoreCurrencies = false }) {
                                Text(stringResource(R.string.settings_close))
                            }
                        },
                    )
                }
                Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    DropdownSetting(
                        label = stringResource(R.string.settings_default_unit),
                        selectedValue = volumeDisplayLabel(volumeUnit),
                        options = volumeOptions,
                        onValueChange = { label ->
                            val newPref = volumePrefFromLabel(label)
                            if (newPref == volumeUnit) return@DropdownSetting
                            val oldResolved = resolveVolumePref(volumeUnit)
                            val newResolved = resolveVolumePref(newPref)
                            if (hasFuelData && oldResolved != newResolved) {
                                pendingVolumeUnit = newPref
                                showVolumeConvertDialog = true
                            } else {
                                volumeUnit = newPref
                            }
                        },
                    )
                }
            }
        }
        SettingsLocalization()

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsDarkMode() {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.settings_dark_mode), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = darkModePref == "system", onClick = { darkModePref = "system" })
                Text(stringResource(R.string.settings_system))
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(selected = darkModePref == "off", onClick = { darkModePref = "off" })
                Text(stringResource(R.string.settings_light))
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(selected = darkModePref == "on", onClick = { darkModePref = "on" })
                Text(stringResource(R.string.settings_dark))
            }
        }
        SettingsDarkMode()

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { exportLauncher.launch("vehicle_expenses_backup.zip") }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.get(R.string.settings_export_to_csv_zip)) }
        Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.get(R.string.settings_import_from_csv_zip)) }
        if (status != AppStrings.get(R.string.settings_ready)) {
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(label: String, selectedValue: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onValueChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


