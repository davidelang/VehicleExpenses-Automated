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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
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
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState(initial = emptyList())
    val hasFuelData = fuelEntries.isNotEmpty()
    val csvManager = viewModel.csvManager
    val scope = rememberCoroutineScope()
    val syncStore = remember { SyncDestinationStore(context) }
    val failureStore = remember { SyncFailureStore(context) }
    var pendingBadge by remember { mutableStateOf(syncStore.pendingBadgeText()) }
    var spreadsheetError by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry = navController.currentBackStackEntry
    val destinations = remember(navBackStackEntry) { syncStore.load() }

    LaunchedEffect(navBackStackEntry) {
        pendingBadge = syncStore.pendingBadgeText()
        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
        photoError = failureStore.photoFailureSummary(syncStore)
        withContext(Dispatchers.IO) {
            viewModel.recountPendingBadge()
        }
        pendingBadge = syncStore.pendingBadgeText()
    }
    val spreadsheetDests = destinations.spreadsheet
    val photoDests = destinations.photo
    val spreadsheetConfigured = syncStore.configuredSpreadsheet().isNotEmpty()
    val photoConfigured = syncStore.configuredPhoto().isNotEmpty()

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
    var spreadsheetSyncStatus by remember { mutableStateOf("") }
    var photoSyncStatus by remember { mutableStateOf("") }
    var spreadsheetSyncInProgress by remember { mutableStateOf(false) }
    var photoSyncInProgress by remember { mutableStateOf(false) }
    var spreadsheetSyncIsError by remember { mutableStateOf(false) }
    var photoSyncIsError by remember { mutableStateOf(false) }
    val spreadsheetProgress = rememberMainThreadSyncProgress {
        spreadsheetSyncStatus = it
        spreadsheetSyncIsError = false
    }
    val photoProgress = rememberMainThreadSyncProgress {
        photoSyncStatus = it
        photoSyncIsError = false
    }
    var showClearDebugConfirm by remember { mutableStateOf(false) }
    var debugDataRefreshKey by remember { mutableIntStateOf(0) }
    val debugSessions = remember(debugDataRefreshKey) { QuickFillDebugStore.listSessions(context) }
    val crashReports = remember(debugDataRefreshKey) { QuickFillDebugStore.listCrashReports(context) }
    val hasDebugData = debugSessions.isNotEmpty() || crashReports.isNotEmpty()
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
        Spacer(modifier = Modifier.height(16.dp))

        Text("Sync & backup", style = MaterialTheme.typography.titleMedium)
        SyncSummaryRow(
            title = "Spreadsheet sync",
            summary = SyncDestinationStore.spreadsheetSummaryLine(spreadsheetDests),
            pendingBadge = pendingBadge,
            errorText = spreadsheetError,
            syncStatusText = spreadsheetSyncStatus,
            syncInProgress = spreadsheetSyncInProgress,
            syncStatusIsError = spreadsheetSyncIsError,
            showSyncNow = spreadsheetConfigured,
            onRowClick = { navController.navigate("settings/spreadsheet_sync") },
            // Phase 15: Sync now via SpreadsheetSyncCoordinator
            onSyncNow = {
                scope.launch {
                    spreadsheetSyncInProgress = true
                    spreadsheetSyncIsError = false
                    spreadsheetSyncStatus = "Starting spreadsheet sync…"
                    try {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncSpreadsheet(spreadsheetProgress)
                        }
                        spreadsheetSyncStatus = result.message
                        spreadsheetSyncIsError = !result.success
                        spreadsheetError = failureStore.spreadsheetFailureSummary(syncStore)
                    } catch (e: Exception) {
                        spreadsheetSyncIsError = true
                        spreadsheetSyncStatus = e.message ?: "Sync failed"
                    } finally {
                        spreadsheetSyncInProgress = false
                    }
                }
            },
        )
        SyncSummaryRow(
            title = "Photo backup",
            summary = syncStore.photoSummaryLine(photoDests),
            pendingBadge = pendingBadge,
            errorText = photoError,
            syncStatusText = photoSyncStatus,
            syncInProgress = photoSyncInProgress,
            syncStatusIsError = photoSyncIsError,
            showSyncNow = photoConfigured,
            onRowClick = { navController.navigate("settings/photo_backup") },
            onSyncNow = {
                scope.launch {
                    photoSyncInProgress = true
                    photoSyncIsError = false
                    photoSyncStatus = "Starting photo backup…"
                    try {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.syncPhotoBackup(photoProgress)
                        }
                        photoSyncStatus = result.message
                        photoSyncIsError = !result.success
                        pendingBadge = syncStore.pendingBadgeText()
                        photoError = failureStore.photoFailureSummary(syncStore)
                    } catch (e: Exception) {
                        photoSyncIsError = true
                        photoSyncStatus = e.message ?: "Photo sync failed"
                    } finally {
                        photoSyncInProgress = false
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))

        SwitchSetting("Save Fuel Receipt Photos", saveFuelPhotos) { enabled ->
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
        SwitchSetting("Debug Quick Fill", debugQuickFill) { debugQuickFill = it }
        SwitchSetting("Show experiment screens (dev)", showExperimentScreens) { showExperimentScreens = it }
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
        if (showExperimentScreens) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Pump OCR (advanced)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Label Y-band uses smallest value-cluster rect height × extra fraction (resolution-independent). " +
                    "Ratio bands apply when experimental pairing is enabled.",
                style = MaterialTheme.typography.bodySmall,
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
        OutlinedTextField(
            value = debugMaxSessions.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.coerceIn(1, 50)?.let { debugMaxSessions = it }
            },
            label = { Text("Debug sessions to keep (1–50)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { showClearDebugConfirm = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Clear debug data")
        }
        if (hasDebugData) {
            Button(
                onClick = {
                    selectedSessionPaths = emptySet()
                    selectedCrashPaths = emptySet()
                    showSendReportPicker = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Send failure report")
            }
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

@Composable
private fun SyncSummaryRow(
    title: String,
    summary: String,
    pendingBadge: String,
    errorText: String? = null,
    syncStatusText: String = "",
    syncInProgress: Boolean = false,
    syncStatusIsError: Boolean = false,
    showSyncNow: Boolean,
    onRowClick: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onRowClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall)
                Text(
                    pendingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (syncStatusText.isNotBlank() || syncInProgress) {
                    SyncStatusDisplay(
                        statusText = syncStatusText,
                        syncInProgress = syncInProgress,
                        isError = syncStatusIsError,
                        textStyle = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (showSyncNow) {
                TextButton(
                    onClick = onSyncNow,
                    enabled = !syncInProgress,
                ) {
                    Text("Sync")
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
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


