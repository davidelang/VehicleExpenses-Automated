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
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Currency
import java.util.Locale

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
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE) }
    val viewModel: SettingsViewModel = hiltViewModel()
    RegisterPageHelp(
        title = "Settings",
        "Units, photo save toggles, debug Quick Fill, and experiment screens live here.",
        "Spreadsheet and photo destinations are under Menu → Syncing (not only this page).",
        "Show experiment screens reveals Alignment, Pump Experiment, and Import Old Pictures in the drawer.",
    )
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState(initial = emptyList())
    val hasFuelData = fuelEntries.isNotEmpty()
    val csvManager = viewModel.csvManager
    val scope = rememberCoroutineScope()

    var saveFuelPhotos by remember { mutableStateOf(prefs.getBoolean("save_fuel_photos", true)) }
    var saveExpensePhotos by remember { mutableStateOf(prefs.getBoolean("save_expense_photos", true)) }
    var debugQuickFill by remember {
        mutableStateOf(
            if (prefs.contains("debug_quick_fill")) {
                prefs.getBoolean("debug_quick_fill", false)
            } else {
                prefs.getBoolean("debug_ocr_pipeline", false)
            }
        )
    }
    var showExperimentScreens by remember {
        mutableStateOf(prefs.getBoolean("show_experiment_screens", false))
    }
    var debugMaxSessions by remember { mutableIntStateOf(prefs.getInt("debug_quick_fill_max_sessions", 10)) }
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
        } catch (_: Exception) {
            "$"
        }
    }
    val shortCurrencySymbols = listOf("system", "$", "€", "£", "CA$", "A$", "¥")
    val shortCurrencyOptions = shortCurrencySymbols.map { sym ->
        if (sym == "system") "System default ($systemCurrencySymbol)" else sym
    } + "See more"
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
            "System default (Liters)"
        } else {
            "System default (Gallons)"
        }
    }
    val volumeOptions = listOf(systemVolumeLabel, "Gallons (G)", "Liters (L)")

    fun resolveVolumePref(pref: String): String {
        if (pref == VolumeUnits.GALLONS || pref == VolumeUnits.LITERS) return pref
        return VolumeUnits.systemDefaultUnit()
    }

    fun volumeDisplayLabel(pref: String): String = when (pref) {
        "system" -> systemVolumeLabel
        VolumeUnits.GALLONS -> "Gallons (G)"
        VolumeUnits.LITERS -> "Liters (L)"
        else -> systemVolumeLabel
    }

    fun volumePrefFromLabel(label: String): String = when {
        label.startsWith("System default") -> "system"
        label.startsWith("Gallons") -> VolumeUnits.GALLONS
        else -> VolumeUnits.LITERS
    }

    var status by remember { mutableStateOf("Ready") }
    var showClearDebugConfirm by remember { mutableStateOf(false) }
    var showDebugInfoDialog by remember { mutableStateOf(false) }
    var debugDataRefreshKey by remember { mutableIntStateOf(0) }
    val debugSessions = remember(debugDataRefreshKey) { QuickFillDebugStore.listSessions(context) }
    val crashReports = remember(debugDataRefreshKey) { QuickFillDebugStore.listCrashReports(context) }
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
                "Photos permission denied. Grant Photos access in App info → Permissions so fuel photos can save to Camera roll.",
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
        uri?.let { scope.launch { csvManager.exportToZip(); status = "Exported"; Toast.makeText(context, "CSV ZIP exported", Toast.LENGTH_LONG).show() } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { csvManager.importFromZip(uri); status = "Imported"; Toast.makeText(context, "CSV import complete", Toast.LENGTH_LONG).show() } }
    }

    LaunchedEffect(debugMaxSessions) {
        prefs.edit().putInt("debug_quick_fill_max_sessions", debugMaxSessions.coerceIn(1, 50)).apply()
        QuickFillDebugStore.pruneToMax(context)
    }

    LaunchedEffect(
        saveFuelPhotos,
        saveExpensePhotos,
        debugQuickFill,
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
            putBoolean("debug_quick_fill", debugQuickFill)
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
        Text("General Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Sync & backup lives under Menu → Syncing (spreadsheet + photo destinations, Sync now, failures).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

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
                    Toast.makeText(context, "Gmail account: $email", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Gmail sign-in failed: ${e.message?.take(80)}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        Text("Email receipts (Shell + Sam's Club)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Offline: packaged samples → Unassigned fuel. " +
                "Live: Gmail label (OAuth) and/or generic IMAP folder (host + app password). " +
                "Same label/folder may hold multiple vendors; parser auto-detects. " +
                "Rows: vehicle Unassigned (id 0), odometer 0, Partial Fill off. " +
                "IMAP uses TLS (port 993). Passwords are not written to logcat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (emailOfflineBusy) return@OutlinedButton
                emailOfflineBusy = true
                scope.launch {
                    try {
                        val summary = withContext(Dispatchers.IO) {
                            viewModel.ingestOfflineShellFixtures()
                        }
                        emailLastSummary = summary
                        Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Offline ingest failed: ${e.message?.take(100)}",
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
                if (emailOfflineBusy) "Ingesting sample receipts…"
                else "Ingest sample receipts (offline)",
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SwitchSetting("Enable scheduled email poll", emailPollEnabled) { enabled ->
            emailPollEnabled = enabled
            persistEmailSource()
        }
        SwitchSetting("Use Gmail (OAuth + label)", emailGmailEnabled) { on ->
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
            label = { Text("Gmail label name") },
            singleLine = true,
            enabled = emailGmailEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (emailAccount.isBlank()) "Gmail account: (not signed in)"
            else "Gmail account: $emailAccount",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { emailSignInLauncher.launch(viewModel.emailReceiptSignInIntent()) },
            enabled = emailGmailEnabled,
        ) {
            Text("Sign in for Gmail")
        }
        Spacer(modifier = Modifier.height(12.dp))
        SwitchSetting("Use IMAP (folder)", emailImapEnabled) { on ->
            emailImapEnabled = on
            if (!on && !emailGmailEnabled) emailGmailEnabled = true
            persistEmailSource()
        }
        OutlinedTextField(
            value = imapHost,
            onValueChange = { imapHost = it },
            label = { Text("IMAP host (e.g. imap.gmail.com)") },
            singleLine = true,
            enabled = emailImapEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = imapPort,
            onValueChange = { imapPort = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text("IMAP port (993 TLS)") },
            singleLine = true,
            enabled = emailImapEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = imapUser,
            onValueChange = { imapUser = it },
            label = { Text("IMAP username") },
            singleLine = true,
            enabled = emailImapEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = imapPassword,
            onValueChange = { imapPassword = it },
            label = { Text("IMAP password / app password") },
            singleLine = true,
            enabled = emailImapEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = imapFolder,
            onValueChange = { imapFolder = it },
            label = { Text("IMAP folder (e.g. INBOX or Receipts)") },
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
                Toast.makeText(context, "Email receipt poll queued", Toast.LENGTH_SHORT).show()
                emailLastSummary = emailPrefs.lastRunSummary
                emailPollWatchToken++
            },
            enabled = emailPollEnabled && (
                (emailGmailEnabled && emailLabel.isNotBlank()) ||
                    (emailImapEnabled && imapHost.isNotBlank() && imapUser.isNotBlank())
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Poll now")
        }
        TextButton(onClick = {
            emailLastSummary = viewModel.readEmailReceiptLastSummary()
        }) {
            Text("Refresh last run")
        }
        if (emailLastSummary.isNotBlank()) {
            Text(
                "Last run: $emailLastSummary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        SwitchSetting("Save fuel fill photos locally", saveFuelPhotos) { enabled ->
            saveFuelPhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting("Save Expense Photos Locally", saveExpensePhotos) { enabled ->
            saveExpensePhotos = enabled
            if (enabled) {
                requestMediaPermissionIfNeeded()
            }
        }
        SwitchSetting("Play Shutter Sound", shutterSounds) { shutterSounds = it }
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
                        contentDescription = "Delete debug reports",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = {
                        if (!hasDebugData) {
                            Toast.makeText(context, "No debug reports to send", Toast.LENGTH_SHORT).show()
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
                        contentDescription = "Send debug reports",
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
                        "Debug Quick Fill",
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        softWrap = true,
                    )
                    IconButton(
                        onClick = { showDebugInfoDialog = true },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "About Debug Quick Fill")
                    }
                    countMaxBlock()
                    deleteSendBlock()
                    Switch(checked = debugQuickFill, onCheckedChange = { debugQuickFill = it })
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
                            "Debug Quick Fill",
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            softWrap = true,
                        )
                        IconButton(
                            onClick = { showDebugInfoDialog = true },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "About Debug Quick Fill")
                        }
                        Switch(checked = debugQuickFill, onCheckedChange = { debugQuickFill = it })
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
        Text("Setup tips", style = MaterialTheme.typography.titleSmall)
        Text(
            "First-run walkthroughs for adding a vehicle or connecting sync (also under Help).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { navController.navigate("tutorial/tutorial_add_vehicle") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text("Tutorial: Add a vehicle")
        }
        OutlinedButton(
            onClick = { navController.navigate("tutorial/tutorial_setup_sync") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text("Tutorial: Connect existing setup")
        }
        OutlinedButton(
            onClick = { navController.navigate("onboarding") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        ) {
            Text("Show first-run welcome")
        }
        SwitchSetting("Show experiment screens (dev)", showExperimentScreens) { showExperimentScreens = it }
        if (showExperimentScreens) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Pump OCR (advanced)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Label Y-band uses smallest value-cluster rect height × extra fraction (resolution-independent). " +
                    "Ratio lo/hi are band-gated extreme checks (not a target $/gal).",
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
                label = { Text("Pump max red boxes kept (${PumpOcrSettings.MIN_MAX_RED_BOXES}–${PumpOcrSettings.MAX_MAX_RED_BOXES})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpLabelYBandExtra,
                onValueChange = { pumpLabelYBandExtra = it },
                label = { Text("Label Y-band extra (fraction of smallest value rect height, 0–1)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandLo,
                onValueChange = { pumpRatioBandLo = it },
                label = { Text("Cost/vol ratio band low ($/gal)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pumpRatioBandHi,
                onValueChange = { pumpRatioBandHi = it },
                label = { Text("Cost/vol ratio band high ($/gal)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (showDebugInfoDialog) {
            AlertDialog(
                onDismissRequest = { showDebugInfoDialog = false },
                title = { Text("Debug Quick Fill") },
                text = {
                    Text(
                        "This captures images and OCR details during a quick fill that can be emailed for debugging.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDebugInfoDialog = false }) {
                        Text("OK")
                    }
                },
            )
        }
        if (showSendReportPicker) {
            AlertDialog(
                onDismissRequest = { showSendReportPicker = false },
                title = { Text("Select items to attach") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (debugSessions.isNotEmpty()) {
                            item { Text("Quick Fill sessions", style = MaterialTheme.typography.titleSmall) }
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
                                        QuickFillDebugStore.readSessionSummary(session),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        if (crashReports.isNotEmpty()) {
                            item { Text("Crash reports", style = MaterialTheme.typography.titleSmall) }
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
                        val attachments = QuickFillDebugStore.collectAttachmentFiles(
                            selectedSessionPaths,
                            selectedCrashPaths,
                        )
                        if (attachments.isEmpty()) {
                            Toast.makeText(context, "Select at least one item", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val launched = QuickFillDebugStore.launchFailureReport(context, attachments)
                        if (launched) {
                            showSendReportPicker = false
                        } else {
                            Toast.makeText(
                                context,
                                "No email app found to send report",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) {
                        Text("Send")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSendReportPicker = false }) {
                        Text("Cancel")
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
                title = { Text("Convert fuel volumes?") },
                text = {
                    Text(
                        "Convert existing fuel volumes between G and L?\n\n" +
                            "If you choose No, historical numbers keep their current values but " +
                            "display labels will change without conversion.",
                    )
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            pendingVolumeUnit = null
                            showVolumeConvertDialog = false
                        }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            volumeUnit = targetPref
                            pendingVolumeUnit = null
                            showVolumeConvertDialog = false
                        }) {
                            Text("No")
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
                            Text("Yes")
                        }
                    }
                },
            )
        }
        if (showClearDebugConfirm) {
            AlertDialog(
                onDismissRequest = { showClearDebugConfirm = false },
                title = { Text("Clear debug data?") },
                text = { Text("Deletes all Quick Fill debug sessions and crash reports.") },
                confirmButton = {
                    TextButton(onClick = {
                        QuickFillDebugStore.clearAllDebugData(context)
                        debugDataRefreshKey++
                        showClearDebugConfirm = false
                        Toast.makeText(context, "Debug data cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDebugConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Localization & Units", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                DropdownSetting(
                    label = "Default Currency",
                    selectedValue = if (currencySymbol == "system") {
                        "System default ($systemCurrencySymbol)"
                    } else {
                        currencySymbol
                    },
                    options = shortCurrencyOptions,
                    onValueChange = { selected ->
                        when {
                            selected.startsWith("System default") -> currencySymbol = "system"
                            selected == "See more" -> showMoreCurrencies = true
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
                    title = { Text("All currencies") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(allCurrencies, key = { it.currencyCode }) { currency ->
                                val sym = try {
                                    currency.getSymbol(Locale.getDefault())
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
                            Text("Close")
                        }
                    },
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                DropdownSetting(
                    label = "Default Unit",
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

        Spacer(modifier = Modifier.height(16.dp))
        Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = darkModePref == "system", onClick = { darkModePref = "system" })
            Text("System")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = darkModePref == "off", onClick = { darkModePref = "off" })
            Text("Light")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = darkModePref == "on", onClick = { darkModePref = "on" })
            Text("Dark")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { exportLauncher.launch("vehicle_expenses_backup.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Export to CSV (ZIP)") }
        Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("Import from CSV ZIP") }
        if (status != "Ready") {
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


