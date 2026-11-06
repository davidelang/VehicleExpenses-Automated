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
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.S

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
        title = S("Settings"),
        S("Units, photo save toggles, debug Fill, and experiment screens live here."),
        S("Spreadsheet and photo destinations are under Menu → Syncing (not only this page)."),
        S("Show experiment screens reveals Alignment, Pump Experiment, and Import Old Pictures in the drawer."),
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
        if (sym == "system") S("System default (%1\$s)", systemCurrencySymbol) else sym
    } + S("See more")
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
            S("System default (Liters)")
        } else {
            S("System default (Gallons)")
        }
    }
    val volumeOptions = listOf(systemVolumeLabel, S("Gallons (G)"), S("Liters (L)"))

    fun resolveVolumePref(pref: String): String {
        if (pref == VolumeUnits.GALLONS || pref == VolumeUnits.LITERS) return pref
        return VolumeUnits.systemDefaultUnit()
    }

    fun volumeDisplayLabel(pref: String): String = when (pref) {
        "system" -> systemVolumeLabel
        VolumeUnits.GALLONS -> S("Gallons (G)")
        VolumeUnits.LITERS -> S("Liters (L)")
        else -> systemVolumeLabel
    }

    fun volumePrefFromLabel(label: String): String = when (label) {
        systemVolumeLabel -> "system"
        S("Gallons (G)") -> VolumeUnits.GALLONS
        S("Liters (L)") -> VolumeUnits.LITERS
        else -> VolumeUnits.LITERS
    }

    var status by remember { mutableStateOf(S("Ready")) }
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
                S("Photos permission denied. Grant Photos access in App info → Permissions so fuel photos can save to gallery."),
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
        uri?.let { scope.launch { csvManager.exportToZip(); status = S("Exported"); Toast.makeText(context, S("CSV ZIP exported"), Toast.LENGTH_LONG).show() } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { csvManager.importFromZip(uri); status = S("Imported"); Toast.makeText(context, S("CSV import complete"), Toast.LENGTH_LONG).show() } }
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
        Text(S("General Settings"), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            S("Sync & backup lives under Menu → Syncing (spreadsheet + photo destinations, Sync now, failures)."),
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
                        Toast.makeText(context, S("Gmail account: %1\$s", email), Toast.LENGTH_SHORT).show()
                    }
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        S("Gmail sign-in failed: %1\$s", e.message?.take(80).orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            Text(S("Email receipts (Shell + Sam's Club)"), style = MaterialTheme.typography.titleMedium)
            Text(
                (if (experimentsEnabled) S("Offline: packaged samples → Unassigned fuel. ") else "") +
                    S("Live: Gmail label (OAuth) and/or generic IMAP folder (host + app password). ") +
                    S("Same label/folder may hold multiple vendors; parser auto-detects. ") +
                    S("Rows: vehicle Unassigned (id 0), odometer 0, Partial Fill off. ") +
                    S("IMAP uses TLS (port 993). Passwords are not written to logcat."),
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
                                    S("Offline ingest failed: %1\$s", e.message?.take(100).orEmpty()),
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
                        if (emailOfflineBusy) S("Ingesting sample receipts…")
                        else S("Ingest sample receipts (offline goldens)"),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            SwitchSetting(S("Enable scheduled email poll"), emailPollEnabled) { enabled ->
                emailPollEnabled = enabled
                persistEmailSource()
            }
            SwitchSetting(S("Use Gmail (OAuth + label)"), emailGmailEnabled) { on ->
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
                label = { Text(S("Gmail label name")) },
                singleLine = true,
                enabled = emailGmailEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (emailAccount.isBlank()) S("Gmail account: (not signed in)")
                else S("Gmail account: %1\$s", emailAccount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { emailSignInLauncher.launch(viewModel.emailReceiptSignInIntent()) },
                enabled = emailGmailEnabled,
            ) {
                Text(S("Sign in for Gmail"))
            }
            Spacer(modifier = Modifier.height(12.dp))
            SwitchSetting(S("Use IMAP (folder)"), emailImapEnabled) { on ->
                emailImapEnabled = on
                if (!on && !emailGmailEnabled) emailGmailEnabled = true
                persistEmailSource()
            }
            OutlinedTextField(
                value = imapHost,
                onValueChange = { imapHost = it },
                label = { Text(S("IMAP host (e.g. imap.gmail.com)")) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapPort,
                onValueChange = { imapPort = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(S("IMAP port (993 TLS)")) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapUser,
                onValueChange = { imapUser = it },
                label = { Text(S("IMAP username")) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapPassword,
                onValueChange = { imapPassword = it },
                label = { Text(S("IMAP password / app password")) },
                singleLine = true,
                enabled = emailImapEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imapFolder,
                onValueChange = { imapFolder = it },
                label = { Text(S("IMAP folder (e.g. INBOX or Receipts)")) },
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
                    Toast.makeText(context, S("Email receipt poll queued"), Toast.LENGTH_SHORT).show()
                    emailLastSummary = emailPrefs.lastRunSummary
                    emailPollWatchToken++
                },
                enabled = emailPollEnabled && (
                    (emailGmailEnabled && emailLabel.isNotBlank()) ||
                        (emailImapEnabled && imapHost.isNotBlank() && imapUser.isNotBlank())
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(S("Poll now"))
            }
            TextButton(onClick = {
                emailLastSummary = viewModel.readEmailReceiptLastSummary()
            }) {
                Text(S("Refresh last run"))
            }
            if (emailLastSummary.isNotBlank()) {
                Text(
                    S("Last run: %1\$s", emailLastSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsEmailReceipts()
        Spacer(modifier = Modifier.height(16.dp))

        SwitchSetting(S("Save fill and trip photos"), saveFuelPhotos) { enabled ->
            saveFuelPhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting(S("Save receipt photos"), saveExpensePhotos) { enabled ->
            saveExpensePhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting(S("Play Shutter Sound"), shutterSounds) { shutterSounds = it }
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
                            contentDescription = S("Delete debug reports"),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!hasDebugData) {
                                Toast.makeText(context, S("No debug reports to send"), Toast.LENGTH_SHORT).show()
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
                            contentDescription = S("Send debug reports"),
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
                            S("Debug Fill"),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            softWrap = true,
                        )
                        IconButton(
                            onClick = { showDebugInfoDialog = true },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = S("About Debug Fill"))
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
                                S("Debug Fill"),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                softWrap = true,
                            )
                            IconButton(
                                onClick = { showDebugInfoDialog = true },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                Icon(Icons.Default.Info, contentDescription = S("About Debug Fill"))
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
            Text(S("Setup tips"), style = MaterialTheme.typography.titleSmall)
            Text(
                S("First-run walkthroughs for adding a vehicle or connecting sync (also under Help)."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { navController.navigate("tutorial/tutorial_add_vehicle") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(S("Tutorial: Add a vehicle"))
            }
            OutlinedButton(
                onClick = { navController.navigate("tutorial/tutorial_setup_sync") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(S("Tutorial: Connect existing setup"))
            }
            OutlinedButton(
                onClick = { navController.navigate("onboarding") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            ) {
                Text(S("Show first-run welcome"))
            }
        }
        SettingsSetupTips()
        if (experimentsAvailable) {
        SwitchSetting(S("Show experiment screens (dev)"), showExperimentScreens) { showExperimentScreens = it }
        if (experimentsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(S("Pump OCR (advanced)"), style = MaterialTheme.typography.titleSmall)
            Text(
                S("Label Y-band uses smallest value-cluster rect height × extra fraction (resolution-independent). ") +
                    S("Ratio lo/hi are band-gated extreme checks (not a target \$/gal)."),
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
                label = { Text(S("Pump max red boxes kept (%1\$s–%2\$s)", PumpOcrSettings.MIN_MAX_RED_BOXES, PumpOcrSettings.MAX_MAX_RED_BOXES)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpLabelYBandExtra,
                onValueChange = { pumpLabelYBandExtra = it },
                label = { Text(S("Label Y-band extra (fraction of smallest value rect height, 0–1)")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandLo,
                onValueChange = { pumpRatioBandLo = it },
                label = { Text(S("Cost/vol ratio band low (\$/gal)")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandHi,
                onValueChange = { pumpRatioBandHi = it },
                label = { Text(S("Cost/vol ratio band high (\$/gal)")) },
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
                    title = { Text(S("Debug Fill")) },
                    text = {
                        Text(
                            S("This captures images and OCR details during a fill that can be emailed for debugging."),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showDebugInfoDialog = false }) {
                            Text(S("OK"))
                        }
                    },
                )
            }
            if (showSendReportPicker) {
                AlertDialog(
                    onDismissRequest = { showSendReportPicker = false },
                    title = { Text(S("Select items to attach")) },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            if (debugSessions.isNotEmpty()) {
                                item { Text(S("Fill sessions"), style = MaterialTheme.typography.titleSmall) }
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
                                item { Text(S("Crash reports"), style = MaterialTheme.typography.titleSmall) }
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
                                Toast.makeText(context, S("Select at least one item"), Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val launched = FillDebugStore.launchFailureReport(context, attachments)
                            if (launched) {
                                showSendReportPicker = false
                            } else {
                                Toast.makeText(
                                    context,
                                    S("No email app found to send report"),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }) {
                            Text(S("Send"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSendReportPicker = false }) {
                            Text(S("Cancel"))
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
                    title = { Text(S("Convert fuel volumes?")) },
                    text = {
                        Text(
                            S("Convert existing fuel volumes between G and L?\n\n") +
                                S("If you choose No, historical numbers keep their current values but ") +
                                S("display labels will change without conversion."),
                        )
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = {
                                pendingVolumeUnit = null
                                showVolumeConvertDialog = false
                            }) {
                                Text(S("Cancel"))
                            }
                            TextButton(onClick = {
                                volumeUnit = targetPref
                                pendingVolumeUnit = null
                                showVolumeConvertDialog = false
                            }) {
                                Text(S("No"))
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
                                Text(S("Yes"))
                            }
                        }
                    },
                )
            }
            if (showClearDebugConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearDebugConfirm = false },
                    title = { Text(S("Clear debug data?")) },
                    text = { Text(S("Deletes all Fill debug sessions and crash reports.")) },
                    confirmButton = {
                        TextButton(onClick = {
                            FillDebugStore.clearAllDebugData(context)
                            debugDataRefreshKey++
                            showClearDebugConfirm = false
                            Toast.makeText(context, S("Debug data cleared"), Toast.LENGTH_SHORT).show()
                        }) {
                            Text(S("Clear"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDebugConfirm = false }) {
                            Text(S("Cancel"))
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
            Text(S("Localization & Units"), style = MaterialTheme.typography.titleMedium)
            Text(S("Language"), style = MaterialTheme.typography.titleSmall)
            val languageLabel = when (appLanguagePref) {
                AppLanguage.SYSTEM -> S("System default")
                else -> {
                    val loc = AppLanguage.SUPPORTED.firstOrNull { it.prefTag == appLanguagePref }
                    if (loc != null) S(loc.displayName) else S("English")
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
                    label = { Text(S("Language")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(S("System default")) },
                        onClick = {
                            appLanguagePref = AppLanguage.SYSTEM
                            AppLanguage.setPref(context, AppLanguage.SYSTEM)
                            languageMenuExpanded = false
                        },
                    )
                    AppLanguage.SUPPORTED.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(S(loc.displayName)) },
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
                S("Language applies immediately to the app UI."),
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
                        label = S("Default Currency"),
                        selectedValue = if (currencySymbol == "system") {
                            S("System default (%1\$s)", systemCurrencySymbol)
                        } else {
                            currencySymbol
                        },
                        options = shortCurrencyOptions,
                        onValueChange = { selected ->
                            when (selected) {
                                S("System default (%1\$s)", systemCurrencySymbol) ->
                                    currencySymbol = "system"
                                S("See more") -> showMoreCurrencies = true
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
                        title = { Text(S("All currencies")) },
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
                                Text(S("Close"))
                            }
                        },
                    )
                }
                Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    DropdownSetting(
                        label = S("Default Unit"),
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
            Text(S("Dark Mode"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = darkModePref == "system", onClick = { darkModePref = "system" })
                Text(S("System"))
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(selected = darkModePref == "off", onClick = { darkModePref = "off" })
                Text(S("Light"))
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(selected = darkModePref == "on", onClick = { darkModePref = "on" })
                Text(S("Dark"))
            }
        }
        SettingsDarkMode()

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { exportLauncher.launch("vehicle_expenses_backup.zip") }, modifier = Modifier.fillMaxWidth()) { Text(S("Export to CSV (ZIP)")) }
        Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(S("Import from CSV ZIP")) }
        if (status != S("Ready")) {
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


