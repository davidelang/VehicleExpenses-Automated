package com.davidlang.vehicleexpensesautomated.ui.import

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportPendingStore
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportProgress
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportResult
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingItem
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingKind
import com.davidlang.vehicleexpensesautomated.data.batch.FuelEconomyOutliers
import com.davidlang.vehicleexpensesautomated.data.batch.FuelOdoReorder
import com.davidlang.vehicleexpensesautomated.data.batch.FuelRowMergeEngine
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.batch.StageCAnswerJournal
import com.davidlang.vehicleexpensesautomated.data.batch.StageCPhase
import com.davidlang.vehicleexpensesautomated.data.batch.StageCPhaseStore
import com.davidlang.vehicleexpensesautomated.data.batch.dashPhotoPaths
import com.davidlang.vehicleexpensesautomated.data.batch.dedupePhotoPaths
import com.davidlang.vehicleexpensesautomated.data.batch.isDashPathHint
import com.davidlang.vehicleexpensesautomated.data.batch.isPumpPathHint
import com.davidlang.vehicleexpensesautomated.data.batch.pendingPhotoUris
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField
import com.davidlang.vehicleexpensesautomated.ui.components.ZoomablePhotoDialog
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasArchiveIdentity
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.util.formatTimeDelta
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.davidlang.vehicleexpensesautomated.data.batch.requestCancel
import com.davidlang.vehicleexpensesautomated.data.batch.runIngest
import com.davidlang.vehicleexpensesautomated.data.batch.runLocationEnhance
import com.davidlang.vehicleexpensesautomated.data.batch.applyMerge
import com.davidlang.vehicleexpensesautomated.data.batch.advancePhaseAndRebuild
import com.davidlang.vehicleexpensesautomated.data.batch.analyzeOdoDisorder
import com.davidlang.vehicleexpensesautomated.data.batch.applyOdoReorder
import com.davidlang.vehicleexpensesautomated.data.batch.getFuelEntry
import com.davidlang.vehicleexpensesautomated.data.batch.nearestFillBefore
import com.davidlang.vehicleexpensesautomated.data.batch.nearestFillAfter
import com.davidlang.vehicleexpensesautomated.data.batch.resolvePendingPhotoUris
import com.davidlang.vehicleexpensesautomated.data.batch.PerVehicleNeighbor
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

/**
 * Stage A/B/C: batch import, merge, image-first questions with manual entry.
 */
@Composable
fun ImportOldPicturesScreen(
    navController: NavHostController,
    /** When true (yellow title-bar → import?review=1), auto-expand Review questions. */
    expandReview: Boolean = false,
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val batchImportViewModel: BatchImportViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val coordinator = batchImportViewModel.coordinator

    var running by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<BatchImportProgress?>(null) }
    var mergeStatus by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<BatchImportResult?>(null) }
    var lastMerge by remember { mutableStateOf<MergeApplyResult?>(null) }
    var mergeAfterImport by remember { mutableStateOf(false) }
    var locationEnhanceWithImport by remember { mutableStateOf(false) }
    var enhancingLocation by remember { mutableStateOf(false) }
    var pendingSnapshot by remember {
        mutableStateOf(BatchImportPendingStore.load(context).toList())
    }
    var stagePhase by remember {
        mutableStateOf(StageCPhaseStore.currentPhase(context))
    }
    var showQuestions by remember {
        // Always allow opening the phase panel (empty pending still needs Next phase).
        mutableStateOf(expandReview)
    }
    // Pending store is phase-scoped (only current phase kinds)
    val phasePending = pendingSnapshot
    var showReorderDialog by remember { mutableStateOf(false) }
    var odoDisorders by remember {
        mutableStateOf<List<FuelOdoReorder.VehicleDisorder>>(emptyList())
    }
    var reorderVehicleId by remember { mutableStateOf<Int?>(null) } // null = all
    var reorderStrategy by remember {
        mutableStateOf(FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS)
    }

    val dashDir = remember { BatchFuelImportCoordinator.dashPhotoDir(context) }
    val pumpDir = remember { BatchFuelImportCoordinator.pumpPhotoDir(context) }
    val dashCount = remember(dashDir) {
        dashDir.listFiles()?.count {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "dng")
        } ?: 0
    }
    val pumpCount = remember(pumpDir) {
        pumpDir.listFiles()?.count {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "dng")
        } ?: 0
    }

    val activeVehicles = vehicles.filter { !it.deleted }

    fun reloadPending() {
        pendingSnapshot = BatchImportPendingStore.load(context).toList()
        stagePhase = StageCPhaseStore.currentPhase(context)
    }

    fun runMerge(toastPrefix: String = AppStrings.get(R.string.import_merge)) {
        if (running || merging || answering) return
        merging = true
        mergeStatus = AppStrings.get(R.string.import_planning)
        scope.launch {
            try {
                val result = coordinator.applyMerge { msg -> mergeStatus = msg }
                lastMerge = result
                reloadPending()
                Toast.makeText(context, "$toastPrefix: ${result.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                mergeStatus = AppStrings.get(R.string.import_failed_1_s, e.message)
                Toast.makeText(context, AppStrings.get(R.string.import_s_1_s_failed_2_s, toastPrefix, e.message), Toast.LENGTH_LONG).show()
            } finally {
                merging = false
            }
        }
    }

    fun startIngest(maxDash: Int?, maxPump: Int?, toastLabel: String) {
        if (running || merging || enhancingLocation) return
        running = true
        lastResult = null
        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    NativePaddleEngine.initializeGlobalBuffers(context.applicationContext)
                }
                val result = coordinator.runIngest(
                    vehicles = vehicles,
                    onProgress = { p -> progress = p },
                    maxDash = maxDash,
                    maxPump = maxPump,
                    locationEnhanceWithImport = locationEnhanceWithImport,
                )
                lastResult = result
                pendingSnapshot = result.pending
                var toast =
                    if (result.cancelled) AppStrings.get(R.string.import_s_1_s_cancelled, toastLabel)
                    else AppStrings.get(R.string.import_s_1_s_done_2_s_rows, toastLabel, result.dashInserted + result.pumpInserted) +
                        AppStrings.get(R.string.import_pending_1_s, result.pending.size)
                if (mergeAfterImport && !result.cancelled) {
                    mergeStatus = AppStrings.get(R.string.import_merge_after_import)
                    val mergeResult = coordinator.applyMerge { msg -> mergeStatus = msg }
                    lastMerge = mergeResult
                    reloadPending()
                    toast += AppStrings.get(R.string.import_merge_1_s, mergeResult.message)
                }
                Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, AppStrings.get(R.string.import_s_1_s_failed_2_s, toastLabel, e.message), Toast.LENGTH_LONG).show()
            } finally {
                running = false
            }
        }
    }

    fun applyAnswer(item: BatchPendingItem, action: PendingAnswerAction) {
        if (answering || running || merging) return
        answering = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    coordinator.applyPendingAnswer(item, vehicles, action)
                }
                reloadPending()
                var toast = result.message
                if (result.success && result.remerge) {
                    mergeStatus = AppStrings.get(R.string.import_re_merge_after_answer)
                    val mergeResult = coordinator.applyMerge { msg -> mergeStatus = msg }
                    lastMerge = mergeResult
                    reloadPending()
                    toast += AppStrings.get(R.string.import_merge_1_s, mergeResult.message)
                }
                Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, AppStrings.get(R.string.import_answer_failed_1_s, e.message), Toast.LENGTH_LONG).show()
            } finally {
                answering = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nav_import_old_pictures), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.import_batch_ocr_merge_image_first_questions_with) +
                stringResource(R.string.import_tap_photo_to_enlarge_zoom_unknown_vehicle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.import_sources), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.import_dash_photos_1_s, dashCount))
                Text(stringResource(R.string.import_pump_photos_1_s, pumpCount))
                Text(stringResource(R.string.import_vehicles_in_db_1_s, activeVehicles.size))
                Text(
                    "${StageCPhaseStore.label(stagePhase)} · " +
                        stringResource(R.string.import_s_1_s_questions_this_phase_only, pendingSnapshot.size),
                )
            }
        }

        progress?.let { p ->
            Text("${p.phase}: ${p.message}")
            if (p.total > 0) {
                LinearProgressIndicator(
                    progress = { p.current.toFloat() / p.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        lastResult?.let { r ->
            Text(
                "Last run: dash=${r.dashInserted} pump=${r.pumpInserted} pending=${r.pending.size}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val limitN = BatchFuelImportCoordinator.LIMITED_IMPORT_COUNT
        val busy = running || answering || merging || enhancingLocation

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { startIngest(null, null, AppStrings.get(R.string.import_batch)) },
                enabled = !busy && (dashCount + pumpCount) > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) stringResource(R.string.import_running) else stringResource(R.string.import_run_batch_import))
            }
            OutlinedButton(onClick = { coordinator.requestCancel() }, enabled = running) {
                Text(stringResource(R.string.settings_cancel))
            }
        }

        OutlinedButton(
            onClick = {
                startIngest(limitN, limitN, AppStrings.get(R.string.import_limited_1_s_2_s, limitN, limitN))
            },
            enabled = !busy && (dashCount + pumpCount) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) stringResource(R.string.import_running) else stringResource(R.string.import_first_1_s_dash_first_2_s_pump, limitN, limitN))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = mergeAfterImport,
                onCheckedChange = { mergeAfterImport = it },
                enabled = !busy,
            )
            Text(stringResource(R.string.import_merge_after_import_default_off), style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = { runMerge() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (merging) stringResource(R.string.import_merging) else stringResource(R.string.import_run_merge))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = locationEnhanceWithImport,
                onCheckedChange = { locationEnhanceWithImport = it },
                enabled = !busy,
            )
            Text(
                stringResource(R.string.import_location_enhance_with_import_default_off),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = {
                if (busy) return@Button
                enhancingLocation = true
                mergeStatus = AppStrings.get(R.string.import_location_enhance)
                scope.launch {
                    try {
                        val msg = coordinator.runLocationEnhance { m -> mergeStatus = m }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        mergeStatus = AppStrings.get(R.string.import_location_enhance_failed_1_s, e.message)
                        Toast.makeText(
                            context,
                            AppStrings.get(R.string.import_location_enhance_failed_1_s_2, e.message),
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        enhancingLocation = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enhancingLocation) stringResource(R.string.import_enhancing) else stringResource(R.string.import_run_location_enhance))
        }

        OutlinedButton(
            onClick = {
                if (running || merging || answering || enhancingLocation) return@OutlinedButton
                merging = true
                mergeStatus = AppStrings.get(R.string.import_clearing_pending)
                scope.launch {
                    try {
                        val result = coordinator.clearPendingAndRescan { msg -> mergeStatus = msg }
                        lastMerge = result
                        reloadPending()
                        Toast.makeText(
                            context,
                            AppStrings.get(R.string.import_clear_re_scan_1_s, result.message),
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, AppStrings.get(R.string.import_re_scan_failed_1_s, e.message), Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        merging = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_clear_questions_re_scan))
        }

        OutlinedButton(
            onClick = {
                if (running || merging || answering) return@OutlinedButton
                answering = true
                scope.launch {
                    try {
                        val n = coordinator.clearAutoPartialFlags(allComplete = true)
                        Toast.makeText(
                            context,
                            AppStrings.get(R.string.import_cleared_1_s_auto_illegal_partial_flags, n),
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, AppStrings.get(R.string.import_repair_failed_1_s, e.message), Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        answering = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_repair_clear_partial_flags))
        }

        mergeStatus?.let { Text(AppStrings.get(R.string.import_merge_1_s_2, it), style = MaterialTheme.typography.bodySmall) }
        lastMerge?.let {
            Text(
                AppStrings.get(R.string.import_last_merge_1_s_total_pending_2_s, it.message, it.totalPending),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Stage C chrome: Next phase / Reset always reachable (not gated on pending).
        Text(
            StageCPhaseStore.label(stagePhase),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.import_phase_scoped_queue_only_this_phase_is_gene) +
                stringResource(R.string.import_next_phase_rebuilds_from_room_after_your_a),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (phasePending.isEmpty()) {
            Text(
                stringResource(R.string.import_no_questions_in_this_phase_tap_next_phase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (busy || stagePhase >= StageCPhase.MAX) return@Button
                    merging = true
                    mergeStatus = AppStrings.get(R.string.import_next_phase)
                    scope.launch {
                        try {
                            val result = coordinator.advancePhaseAndRebuild { msg ->
                                mergeStatus = msg
                            }
                            lastMerge = result
                            reloadPending()
                            stagePhase = StageCPhaseStore.currentPhase(context)
                            showQuestions = true
                            Toast.makeText(
                                context,
                                AppStrings.get(R.string.import_phase_1_s_2_s_questions, stagePhase, result.totalPending),
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                AppStrings.get(R.string.import_next_phase_failed_1_s, e.message),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            merging = false
                        }
                    }
                },
                enabled = !busy && stagePhase < StageCPhase.MAX,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.import_next_phase_2))
            }
            OutlinedButton(
                onClick = {
                    StageCPhaseStore.resetToPhase1(context)
                    stagePhase = StageCPhaseStore.currentPhase(context)
                    runMerge(toastPrefix = AppStrings.get(R.string.import_rescan_phase_1))
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.import_reset_to_phase_1))
            }
        }

        val hasOdoPending = phasePending.any {
            it.kind == BatchPendingKind.ODO_SUSPECT ||
                it.kind == BatchPendingKind.CONFLICT_ODO
        }
        val reorderGatePhaseOk = stagePhase > StageCPhase.COMPLEX_ODO.number ||
            (stagePhase >= StageCPhase.COMPLEX_ODO.number && !hasOdoPending)
        OutlinedButton(
            onClick = {
                if (busy) return@OutlinedButton
                merging = true
                scope.launch {
                    try {
                        odoDisorders = withContext(Dispatchers.IO) {
                            coordinator.analyzeOdoDisorder()
                        }
                        if (odoDisorders.none { it.anchorCount >= 2 }) {
                            Toast.makeText(
                                context,
                                "Need a vehicle with ≥2 fills (odo > 0)",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            reorderVehicleId = null
                            reorderStrategy = FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS
                            showReorderDialog = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, AppStrings.get(R.string.import_preview_failed_1_s, e.message), Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        merging = false
                    }
                }
            },
            enabled = !busy && reorderGatePhaseOk,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (reorderGatePhaseOk) {
                    stringResource(R.string.import_reorder_by_odometer)
                } else {
                    stringResource(R.string.import_reorder_by_odometer_finish_odo_phases_firs)
                },
            )
        }

        OutlinedButton(
            onClick = {
                reloadPending()
                showQuestions = !showQuestions
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (showQuestions) stringResource(R.string.import_hide_questions_1_s, phasePending.size)
                else stringResource(R.string.import_review_questions_1_s_in_phase_2_s, phasePending.size, stagePhase),
            )
        }

        if (showQuestions) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            val cache = java.io.File(context.cacheDir, "stage_c_answer_journal.jsonl")
                            val out = withContext(Dispatchers.IO) {
                                StageCAnswerJournal.exportCopy(context, cache)
                            }
                            val n = StageCAnswerJournal.lineCount(context)
                            Toast.makeText(
                                context,
                                if (out != null) {
                                    AppStrings.get(R.string.import_journal_exported_1_s_lines_2_s, n, out.absolutePath)
                                } else {
                                    AppStrings.get(R.string.import_journal_empty_or_export_failed)
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, AppStrings.get(R.string.import_export_failed_1_s, e.message), Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_export_answer_journal))
            }
            if (phasePending.isEmpty()) {
                Text(
                    stringResource(R.string.import_no_questions_in_this_phase_tap_next_phase),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            phasePending.forEach { item ->
                PendingQuestionCard(
                    item = item,
                    vehicles = activeVehicles.filter {
                        it.id != BatchFuelImportCoordinator.UNASSIGNED_VEHICLE_ID
                    },
                    enabled = !busy,
                    coordinator = coordinator,
                    onAction = { action -> applyAnswer(item, action) },
                )
            }
        }

        if (showReorderDialog) {
            ReorderByOdometerDialog(
                vehicles = activeVehicles,
                disorders = odoDisorders,
                selectedVehicleId = reorderVehicleId,
                onVehicleChange = { reorderVehicleId = it },
                strategy = reorderStrategy,
                onStrategyChange = { reorderStrategy = it },
                enabled = !busy,
                onDismiss = { showReorderDialog = false },
                onConfirm = {
                    showReorderDialog = false
                    if (busy) return@ReorderByOdometerDialog
                    merging = true
                    mergeStatus = AppStrings.get(R.string.import_reorder_by_odometer)
                    val vid = reorderVehicleId
                    val strat = reorderStrategy
                    scope.launch {
                        try {
                            val result = coordinator.applyOdoReorder(vid, strat) { msg ->
                                mergeStatus = msg
                            }
                            lastMerge = result
                            reloadPending()
                            stagePhase = StageCPhaseStore.currentPhase(context)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                AppStrings.get(R.string.import_reorder_failed_1_s, e.message),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            merging = false
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_back))
        }
    }
}

@Composable
private fun ReorderByOdometerDialog(
    vehicles: List<Vehicle>,
    disorders: List<FuelOdoReorder.VehicleDisorder>,
    selectedVehicleId: Int?,
    onVehicleChange: (Int?) -> Unit,
    strategy: FuelOdoReorder.Strategy,
    onStrategyChange: (FuelOdoReorder.Strategy) -> Unit,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val nameOf: (Int) -> String = { vid ->
        vehicles.find { it.id == vid }?.name
            ?: if (vid == 0) AppStrings.get(R.string.ui_unknown) else AppStrings.get(R.string.expense_vehicle_1_s, vid)
    }
    val scoped = if (selectedVehicleId == null) {
        disorders
    } else {
        disorders.filter { it.vehicleId == selectedVehicleId }
    }
    val reverseTotal = scoped.sumOf { it.reverseSteps.size }
    val permuteVehicles = scoped.count { it.needsTimestampPermute }
    val confirmDestructive = strategy == FuelOdoReorder.Strategy.DELETE_OFFENDERS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_reorder_by_odometer_2)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.import_timestamps_may_be_wrong_bad_exif_odometer) +
                        stringResource(R.string.import_pick_a_vehicle_scope_and_strategy),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.import_vehicle_scope), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedVehicleId == null,
                        onClick = { onVehicleChange(null) },
                        enabled = enabled,
                    )
                    Text(stringResource(R.string.import_all_vehicles_with_anchors))
                }
                disorders.filter { it.anchorCount >= 2 }.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedVehicleId == d.vehicleId,
                            onClick = { onVehicleChange(d.vehicleId) },
                            enabled = enabled,
                        )
                        Text(
                            AppStrings.get(R.string.import_s_1_s_anchors_2_s, nameOf(d.vehicleId), d.anchorCount) +
                                AppStrings.get(R.string.import_reverse_1_s, d.reverseSteps.size) +
                                if (d.needsTimestampPermute) AppStrings.get(R.string.import_needs_permute) else "",
                        )
                    }
                }
                Text(stringResource(R.string.import_strategy), style = MaterialTheme.typography.titleSmall)
                StrategyRadio(
                    selected = strategy == FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS,
                    label = stringResource(R.string.import_a_reorder_timestamps_to_match_odo_recommen),
                    detail = stringResource(R.string.import_permute_existing_times_onto_odo_order_no_i),
                    enabled = enabled,
                    onClick = { onStrategyChange(FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS) },
                )
                StrategyRadio(
                    selected = strategy == FuelOdoReorder.Strategy.ECONOMY_IGNORE,
                    label = stringResource(R.string.import_b_leave_order_mark_economy_ignore),
                    detail = stringResource(R.string.import_economyignored_on_later_by_time_reverse_of, reverseTotal),
                    enabled = enabled,
                    onClick = { onStrategyChange(FuelOdoReorder.Strategy.ECONOMY_IGNORE) },
                )
                StrategyRadio(
                    selected = strategy == FuelOdoReorder.Strategy.DELETE_OFFENDERS,
                    label = stringResource(R.string.import_c_delete_out_of_order_rows),
                    detail = stringResource(R.string.import_soft_delete_later_by_time_reverse_offender, reverseTotal),
                    enabled = enabled,
                    onClick = { onStrategyChange(FuelOdoReorder.Strategy.DELETE_OFFENDERS) },
                )
                Text(
                    "Preview: vehicles needing permute=$permuteVehicles · reverse steps=$reverseTotal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = enabled && scoped.any { it.anchorCount >= 2 },
            ) {
                Text(if (confirmDestructive) stringResource(R.string.import_confirm_delete) else stringResource(R.string.import_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun StrategyRadio(
    selected: Boolean,
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

@Composable
private fun PendingQuestionCard(
    item: BatchPendingItem,
    vehicles: List<Vehicle>,
    enabled: Boolean,
    coordinator: BatchFuelImportCoordinator,
    onAction: (PendingAnswerAction) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val photoDestId = remember { SyncDestinationStore(context).photoDestination()?.id }
    var photoPaths by remember(item.id) { mutableStateOf(pendingPhotoUris(item)) }
    var zoomPath by remember { mutableStateOf<String?>(null) }
    var archiveFuel by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var fetchingArchive by remember(item.id) { mutableStateOf(false) }
    var costText by remember(item.id) { mutableStateOf(item.extra["parsedCost"] ?: "") }
    var volText by remember(item.id) { mutableStateOf(item.extra["parsedVol"] ?: "") }
    var odoText by remember(item.id) {
        mutableStateOf(item.extra["parsedOdo"] ?: "")
    }
    var freeOdoText by remember(item.id) { mutableStateOf("") }
    var expandNeighbors by remember(item.id) { mutableIntStateOf(0) }
    var neighbors by remember(item.id) { mutableStateOf<List<FuelEntry>>(emptyList()) }
    var perVehicleNeighbors by remember(item.id) {
        mutableStateOf<List<PerVehicleNeighbor>>(emptyList())
    }
    var selectedVehicleId by remember(item.id) {
        mutableStateOf(item.suggestedVehicleId)
    }
    var showAllDashVehicles by remember(item.id) { mutableStateOf(false) }
    /**
     * MPG_OUTLIER focus: true = **This fill** (leg end, default);
     * false = **Last fill** (leg start / prior full fill).
     */
    var mpgFocusThis by remember(item.id) { mutableStateOf(true) }
    var lastRow by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var thisRow by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var beforeLast by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var afterThis by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var lastPhotos by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    var thisPhotos by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    /** Explicit partial checkbox (only when focus row is field-complete). */
    var treatPartial by remember(item.id) { mutableStateOf(false) }

    // ODO_SUSPECT: per-fill odo fields + dash-only photos (prev / cur / next)
    var odoPrevText by remember(item.id) {
        mutableStateOf(item.extra["prevOdo"]?.takeIf { it.isNotBlank() } ?: "")
    }
    var odoCurText by remember(item.id) {
        mutableStateOf(item.extra["curOdo"]?.takeIf { it.isNotBlank() } ?: "")
    }
    var odoNextText by remember(item.id) {
        mutableStateOf(item.extra["nextOdo"]?.takeIf { it.isNotBlank() } ?: "")
    }
    var odoPrevPhotos by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    var odoCurPhotos by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    var odoNextPhotos by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    /** Peer FuelEntry rows for archive-identity checks (complex ODO Stage C). */
    var odoPrevEntry by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var odoCurEntry by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var odoNextEntry by remember(item.id) { mutableStateOf<FuelEntry?>(null) }
    var odoPrevId by remember(item.id) {
        mutableStateOf(item.extra["prevEntryId"]?.toLongOrNull())
    }
    var odoCurId by remember(item.id) {
        mutableStateOf(item.extra["curEntryId"]?.toLongOrNull())
    }
    var odoNextId by remember(item.id) {
        mutableStateOf(item.extra["nextEntryId"]?.toLongOrNull()?.takeIf { it > 0 })
    }
    var odoSuspectId by remember(item.id) {
        mutableStateOf(item.extra["suspectId"]?.toLongOrNull() ?: item.fuelEntryId)
    }

    val thisEntryId = item.extra["thisEntryId"]?.toLongOrNull()
        ?: item.extra["endEntryId"]?.toLongOrNull()
        ?: item.fuelEntryId
    val lastEntryId = item.extra["lastEntryId"]?.toLongOrNull()
        ?: item.extra["prevEntryId"]?.toLongOrNull()
    val focusEntryId: Long? =
        if (item.kind == BatchPendingKind.MPG_OUTLIER) {
            if (mpgFocusThis) thisEntryId else lastEntryId
        } else {
            item.fuelEntryId
                ?: item.extra["suspectId"]?.toLongOrNull()
                ?: thisEntryId
        }

    fun applyFocusPrefill(row: FuelEntry?) {
        if (row == null) return
        odoText = if (row.odometer > 0) row.odometer.toString() else ""
        costText = if (row.cost > 0) row.cost.toString() else ""
        volText = if (row.gallons > 0) row.gallons.toString() else ""
    }

    fun parsePathList(key: String): List<String> =
        item.extra[key]
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .let { dedupePhotoPaths(it) }

    LaunchedEffect(item.id, mpgFocusThis) {
        if (item.kind == BatchPendingKind.MPG_OUTLIER) {
            val thisId = thisEntryId
            val lastId = lastEntryId
            val thisE = thisId?.let { coordinator.getFuelEntry(it) }
            val lastE = lastId?.let { coordinator.getFuelEntry(it) }
            thisRow = thisE
            lastRow = lastE
            thisPhotos = parsePathList("thisPhotoPaths").ifEmpty {
                parsePathList("photoPaths").ifEmpty {
                    thisE?.let { FuelEconomyOutliers.photoPathsForEntry(it) }.orEmpty()
                }
            }
            lastPhotos = parsePathList("lastPhotoPaths").ifEmpty {
                parsePathList("prevPhotoPaths").ifEmpty {
                    lastE?.let { FuelEconomyOutliers.photoPathsForEntry(it) }.orEmpty()
                }
            }
            val focus = if (mpgFocusThis) thisE else lastE
            applyFocusPrefill(focus)
            treatPartial = focus?.isPartialFill == true

            val vid = item.suggestedVehicleId
                ?: thisE?.vehicleId
                ?: lastE?.vehicleId
                ?: 0
            val lastTs = lastE?.timestamp ?: item.extra["prevTs"]?.toLongOrNull()
            val thisTs = thisE?.timestamp
                ?: item.extra["endTs"]?.toLongOrNull()
                ?: item.timestampMs
            val exclude = setOfNotNull(thisId, lastId)
            if (vid > 0 && lastTs != null) {
                beforeLast = coordinator.nearestFillBefore(vid, lastTs, exclude)
            }
            if (vid > 0 && thisTs != null) {
                afterThis = coordinator.nearestFillAfter(vid, thisTs, exclude)
            }
        } else {
            photoPaths = coordinator.resolvePendingPhotoUris(item)
            val id = focusEntryId
            if (id != null) {
                val row = coordinator.getFuelEntry(id)
                if (row != null) {
                    applyFocusPrefill(row)
                    treatPartial = row.isPartialFill
                    archiveFuel = row
                }
            }
        }
    }

    LaunchedEffect(focusEntryId, thisEntryId, lastEntryId, mpgFocusThis) {
        val id = focusEntryId
        if (id != null && item.kind != BatchPendingKind.MPG_OUTLIER) {
            archiveFuel = coordinator.getFuelEntry(id)
        }
    }

    fun canFetchFor(entry: FuelEntry?): Boolean = fuelHasArchiveIdentity(entry, photoDestId)

    fun fetchArchiveFor(entryId: Long?, onPaths: (List<String>) -> Unit) {
        if (entryId == null || fetchingArchive) return
        scope.launch {
            fetchingArchive = true
            try {
                val row = coordinator.getFuelEntry(entryId) ?: return@launch
                val scrubbed = fuelViewModel.scrubUnreadableFuelPhotos(row)
                fuelViewModel.downloadFuelPhoto(scrubbed)
                val refreshed = fuelViewModel.getFuelById(entryId) ?: scrubbed
                archiveFuel = refreshed
                when (entryId) {
                    odoPrevId -> odoPrevEntry = refreshed
                    odoCurId -> odoCurEntry = refreshed
                    odoNextId -> odoNextEntry = refreshed
                    lastEntryId -> lastRow = refreshed
                    thisEntryId -> thisRow = refreshed
                }
                val uris = FuelPhotoJson.parse(refreshed.photoUrl).map { it.uri }
                if (uris.isNotEmpty()) {
                    onPaths(uris)
                    Toast.makeText(context, AppStrings.get(R.string.fuel_image_fetched), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, AppStrings.get(R.string.fuel_could_not_fetch_image), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: AppStrings.get(R.string.expense_fetch_failed), Toast.LENGTH_LONG).show()
            } finally {
                fetchingArchive = false
            }
        }
    }

    LaunchedEffect(item.id, item.fuelEntryId, expandNeighbors) {
        when (item.kind) {
            BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE -> {
                val ts = item.timestampMs
                    ?: item.fuelEntryId?.let { coordinator.getFuelEntry(it)?.timestamp }
                    ?: return@LaunchedEffect
                perVehicleNeighbors = coordinator.nearestNeighborsPerVehicle(
                    timestampMs = ts,
                    vehicles = vehicles,
                    expandExtra = expandNeighbors,
                    excludeEntryId = item.fuelEntryId,
                )
            }
            BatchPendingKind.ECONOMY_IGNORED -> {
                if (item.fuelEntryId == null && item.timestampMs == null) return@LaunchedEffect
                neighbors = coordinator.neighborContext(
                    fuelEntryId = item.fuelEntryId
                        ?: item.extra["suspectId"]?.toLongOrNull(),
                    timestampMs = item.timestampMs,
                    vehicleIdHint = item.suggestedVehicleId,
                    expandExtra = expandNeighbors,
                    allVehicles = false,
                )
            }
            BatchPendingKind.ODO_SUSPECT -> {
                fun paths(key: String): List<String> =
                    item.extra[key]
                        ?.split('|')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                        .let { dedupePhotoPaths(it) }

                odoPrevId = item.extra["prevEntryId"]?.toLongOrNull()
                odoCurId = item.extra["curEntryId"]?.toLongOrNull()
                odoNextId = item.extra["nextEntryId"]?.toLongOrNull()?.takeIf { it > 0 }
                odoSuspectId = item.extra["suspectId"]?.toLongOrNull() ?: item.fuelEntryId

                val prevE = odoPrevId?.let { coordinator.getFuelEntry(it) }
                val curE = odoCurId?.let { coordinator.getFuelEntry(it) }
                val nextE = odoNextId?.let { coordinator.getFuelEntry(it) }
                odoPrevEntry = prevE
                odoCurEntry = curE
                odoNextEntry = nextE

                odoPrevPhotos = paths("prevDashPaths").ifEmpty {
                    prevE?.let { dashPhotoPaths(it) }.orEmpty()
                }
                odoCurPhotos = paths("curDashPaths").ifEmpty {
                    curE?.let { dashPhotoPaths(it) }.orEmpty()
                }
                odoNextPhotos = paths("nextDashPaths").ifEmpty {
                    nextE?.let { dashPhotoPaths(it) }.orEmpty()
                }
                if (prevE != null && prevE.odometer > 0) {
                    odoPrevText = prevE.odometer.toString()
                } else if (odoPrevText.isBlank()) {
                    odoPrevText = item.extra["prevOdo"].orEmpty()
                }
                if (curE != null && curE.odometer > 0) {
                    odoCurText = curE.odometer.toString()
                } else if (odoCurText.isBlank()) {
                    odoCurText = item.extra["curOdo"].orEmpty()
                }
                if (nextE != null && nextE.odometer > 0) {
                    odoNextText = nextE.odometer.toString()
                } else if (odoNextText.isBlank()) {
                    odoNextText = item.extra["nextOdo"].orEmpty()
                }
            }
            else -> {}
        }
    }

    val conflictOdos = remember(item) {
        item.extra["odos"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.distinct()
            .orEmpty()
    }

    var tankMax by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    val thisTsAnchor = thisRow?.timestamp
        ?: item.extra["endTs"]?.toLongOrNull()
        ?: item.timestampMs

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.kindLabel(), style = MaterialTheme.typography.labelLarge)
            Text(item.message, style = MaterialTheme.typography.bodyMedium)

            if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                // Metrics header
                val mpg = item.extra["mpg"]?.toDoubleOrNull()
                val ref = item.extra["refMpg"]?.toDoubleOrNull()
                val lastTs = lastRow?.timestamp ?: item.extra["prevTs"]?.toLongOrNull()
                val thisTs = thisRow?.timestamp ?: item.extra["endTs"]?.toLongOrNull()
                val legDt = if (lastTs != null && thisTs != null) {
                    formatTimeDelta(thisTs - lastTs)
                } else {
                    "n/a"
                }
                if (mpg != null && ref != null) {
                    Text(
                        "Leg mpg=${"%.1f".format(mpg)} · ref=${"%.1f".format(ref)} · " +
                            "odoΔ=${item.extra["odoDelta"]} · vol=${item.extra["sumVol"]} · " +
                            stringResource(R.string.import_t_last_this_1_s, legDt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Window inventory (time-ordered fills between last → this)
                val windowLines = item.extra["windowSummary"]
                    ?.split('|')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                if (windowLines.isNotEmpty()) {
                    Text(
                        stringResource(R.string.import_fills_in_this_leg_time_order),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    windowLines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Last fill (photos above button) ──
                Text(stringResource(R.string.import_last_fill), style = MaterialTheme.typography.titleSmall)
                PendingPhotoRow(
                    paths = lastPhotos,
                    conflict = lastPhotos.size > 1,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(lastRow),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(lastEntryId) { lastPhotos = it }
                    },
                )
                if (mpgFocusThis) {
                    OutlinedButton(
                        onClick = { mpgFocusThis = false },
                        enabled = enabled && lastEntryId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_last_fill_2)) }
                } else {
                    Button(
                        onClick = { mpgFocusThis = false },
                        enabled = enabled && lastEntryId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_last_fill_selected)) }
                }

                // ── This fill (photos above button) ──
                Text(stringResource(R.string.import_this_fill), style = MaterialTheme.typography.titleSmall)
                PendingPhotoRow(
                    paths = thisPhotos,
                    conflict = thisPhotos.size > 1,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(thisRow),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(thisEntryId) { thisPhotos = it }
                    },
                )
                if (mpgFocusThis) {
                    Button(
                        onClick = { mpgFocusThis = true },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_this_fill_selected)) }
                } else {
                    OutlinedButton(
                        onClick = { mpgFocusThis = true },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_this_fill_2)) }
                }

                // ── Context (text only): before last, last, this, after this ──
                Text(stringResource(R.string.import_context_text_only), style = MaterialTheme.typography.titleSmall)
                beforeLast?.let { n ->
                    NeighborLine(
                        n, vehicles,
                        prefix = AppStrings.get(R.string.import_before_last),
                        anchorTs = thisTsAnchor,
                    )
                } ?: Text(
                    stringResource(R.string.import_before_last_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NeighborLine(
                    lastRow,
                    vehicles,
                    prefix = stringResource(R.string.import_last_fill_3),
                    anchorTs = thisTsAnchor,
                    fallback = mpgContextFallback(
                        context, item, isLast = true,
                    ),
                )
                NeighborLine(
                    thisRow,
                    vehicles,
                    prefix = stringResource(R.string.import_this_fill_3),
                    anchorTs = thisTsAnchor,
                    fallback = mpgContextFallback(
                        context, item, isLast = false,
                    ),
                )
                afterThis?.let { n ->
                    NeighborLine(
                        n, vehicles,
                        prefix = AppStrings.get(R.string.import_after_this),
                        anchorTs = thisTsAnchor,
                    )
                } ?: Text(
                    stringResource(R.string.import_after_this_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (item.kind == BatchPendingKind.ODO_SUSPECT &&
                item.extra["mode"] == "simple"
            ) {
                // Phase 1 short UI: one dash image + pre-filled guess (or blank for odo=0)
                val guess = item.extra["suggestedOdo"].orEmpty().ifBlank {
                    item.extra["parsedOdo"]?.takeIf { it != "0" }.orEmpty()
                }
                var simpleOdo by remember(item.id) { mutableStateOf(guess) }
                val missingZero = item.extra["reason"] == "missing_odo_dash" ||
                    (item.extra["parsedOdo"] == "0" && guess.isBlank())
                Text(
                    if (missingZero) {
                        stringResource(R.string.import_odometer_missing_read_dash_photo_enter_val)
                    } else {
                        stringResource(R.string.import_suggested_fix_length_ocr_edit_if_wrong_the)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PendingPhotoRow(
                    paths = photoPaths
                        .ifEmpty { listOfNotNull(item.photoPath ?: item.durablePhotoPath) }
                        .filter { isDashPathHint(it) }
                        .let { dedupePhotoPaths(it) },
                    conflict = false,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(archiveFuel),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(focusEntryId) { photoPaths = it.filter { p -> isDashPathHint(p) } }
                    },
                )
                com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                    value = simpleOdo,
                    onValueChange = { simpleOdo = it },
                    label = { Text(stringResource(R.string.import_odometer)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    showCaretButtons = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = {
                        val o = simpleOdo.toIntOrNull()
                        if (o != null && o > 0) {
                            onAction(
                                PendingAnswerAction.ManualEditFuelFields(
                                    odometer = o,
                                    cost = null,
                                    volume = null,
                                    entryId = item.fuelEntryId
                                        ?: item.extra["suspectId"]?.toLongOrNull(),
                                ),
                            )
                        }
                    },
                    enabled = enabled && (simpleOdo.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_odometer))
                }
                Text(
                    stringResource(R.string.import_gap_digit_flags_are_detect_only_if_the_odo) +
                        stringResource(R.string.import_acknowledge_so_this_chain_is_not_re_asked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        onAction(
                            PendingAnswerAction.AcknowledgeLooksCorrect(
                                kind = AppStrings.get(R.string.import_odo_suspect),
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_these_odometers_look_correct))
                }
            } else if (item.kind == BatchPendingKind.ODO_SUSPECT) {
                // Phase 2: per-fill dash-only blocks previous → cur → next
                val reason = item.extra["reason"] ?: "odo"
                val prevTs = item.extra["prevTs"]?.toLongOrNull()
                val curTs = item.extra["curTs"]?.toLongOrNull()
                val nextTs = item.extra["nextTs"]?.toLongOrNull()
                val dtLine = buildString {
                    if (prevTs != null && curTs != null) {
                        append(AppStrings.get(R.string.import_t_prev_cur_1_s, formatTimeDelta(curTs - prevTs)))
                    }
                    if (curTs != null && nextTs != null) {
                        if (isNotEmpty()) append(" · ")
                        append(AppStrings.get(R.string.import_t_cur_next_1_s, formatTimeDelta(nextTs - curTs)))
                    }
                }
                if (dtLine.isNotEmpty()) {
                    Text(dtLine, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.import_dash_photos_only_fix_any_wrong_odo_then_sa),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                OdoPeerBlock(
                    title = if (odoSuspectId == odoPrevId) {
                        stringResource(R.string.import_previous_fill_suspect)
                    } else {
                        stringResource(R.string.import_previous_fill)
                    },
                    emphasize = odoSuspectId == odoPrevId,
                    photos = odoPrevPhotos,
                    odoText = odoPrevText,
                    onOdoChange = { odoPrevText = it },
                    metaLine = odoPeerMetaLine(
                        context,
                        item.extra["prevTs"], item.extra["prevCost"], item.extra["prevVol"],
                    ),
                    enabled = enabled,
                    onPhotoTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(odoPrevEntry),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(odoPrevId) { uris ->
                            odoPrevPhotos = dedupePhotoPaths(uris.filter { isDashPathHint(it) })
                        }
                    },
                )
                OdoPeerBlock(
                    title = if (odoSuspectId == odoCurId || odoSuspectId == null) {
                        stringResource(R.string.import_this_fill_suspect)
                    } else {
                        stringResource(R.string.import_this_fill)
                    },
                    emphasize = odoSuspectId == odoCurId ||
                        (odoSuspectId != odoPrevId && odoSuspectId != odoNextId),
                    photos = odoCurPhotos,
                    odoText = odoCurText,
                    onOdoChange = { odoCurText = it },
                    metaLine = odoPeerMetaLine(
                        context,
                        item.extra["curTs"], item.extra["curCost"], item.extra["curVol"],
                    ),
                    enabled = enabled,
                    onPhotoTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(odoCurEntry),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(odoCurId) { uris ->
                            odoCurPhotos = dedupePhotoPaths(uris.filter { isDashPathHint(it) })
                        }
                    },
                )
                if (odoNextId != null) {
                    OdoPeerBlock(
                        title = if (odoSuspectId == odoNextId) {
                            stringResource(R.string.import_next_fill_suspect)
                        } else {
                            stringResource(R.string.import_next_fill)
                        },
                        emphasize = odoSuspectId == odoNextId,
                        photos = odoNextPhotos,
                        odoText = odoNextText,
                        onOdoChange = { odoNextText = it },
                        metaLine = odoPeerMetaLine(
                            context,
                            item.extra["nextTs"], item.extra["nextCost"], item.extra["nextVol"],
                        ),
                        enabled = enabled,
                        onPhotoTap = { zoomPath = it },
                        canFetchArchive = canFetchFor(odoNextEntry),
                        isFetchingArchive = fetchingArchive,
                        onFetchArchive = {
                            fetchArchiveFor(odoNextId) { uris ->
                                odoNextPhotos = dedupePhotoPaths(uris.filter { isDashPathHint(it) })
                            }
                        },
                    )
                }

                Button(
                    onClick = {
                        onAction(
                            PendingAnswerAction.SaveOdoPeers(
                                prevId = odoPrevId,
                                prevOdo = odoPrevText.toIntOrNull(),
                                curId = odoCurId,
                                curOdo = odoCurText.toIntOrNull(),
                                nextId = odoNextId,
                                nextOdo = odoNextText.toIntOrNull(),
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_odometers))
                }
                Text(
                    stringResource(R.string.import_gap_digit_flags_are_detect_only_if_all_pee) +
                        stringResource(R.string.import_acknowledge_so_this_chain_is_not_re_asked_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        onAction(
                            PendingAnswerAction.AcknowledgeLooksCorrect(
                                kind = AppStrings.get(R.string.import_odo_suspect),
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_these_odometers_look_correct))
                }
                // Optional partial on suspect if complete (cost+vol from extra)
                val suspectComplete = run {
                    val o = when (odoSuspectId) {
                        odoPrevId -> odoPrevText.toIntOrNull() ?: 0
                        odoNextId -> odoNextText.toIntOrNull() ?: 0
                        else -> odoCurText.toIntOrNull() ?: 0
                    }
                    val c = when (odoSuspectId) {
                        odoPrevId -> item.extra["prevCost"]?.toDoubleOrNull() ?: 0.0
                        odoNextId -> item.extra["nextCost"]?.toDoubleOrNull() ?: 0.0
                        else -> item.extra["curCost"]?.toDoubleOrNull() ?: 0.0
                    }
                    val v = when (odoSuspectId) {
                        odoPrevId -> item.extra["prevVol"]?.toDoubleOrNull() ?: 0.0
                        odoNextId -> item.extra["nextVol"]?.toDoubleOrNull() ?: 0.0
                        else -> item.extra["curVol"]?.toDoubleOrNull() ?: 0.0
                    }
                    o > 0 && c > 0 && v > 0
                }
                if (suspectComplete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = treatPartial,
                            onCheckedChange = { checked ->
                                treatPartial = checked
                                onAction(
                                    PendingAnswerAction.SetPartialFill(
                                        partial = checked,
                                        entryId = odoSuspectId,
                                    ),
                                )
                            },
                            enabled = enabled,
                        )
                        Text(
                            stringResource(R.string.import_treat_as_partial_fill_suspect_all_fields_p),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.import_tap_photo_to_enlarge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val displayPaths = when (item.kind) {
                    BatchPendingKind.BAD_PUMP_RATIO ->
                        photoPaths.filter { isPumpPathHint(it) }.let { dedupePhotoPaths(it) }
                    BatchPendingKind.ODO_SUSPECT ->
                        photoPaths.filter { isDashPathHint(it) }.let { dedupePhotoPaths(it) }
                    else -> photoPaths
                }
                PendingPhotoRow(
                    paths = displayPaths,
                    conflict = item.kind == BatchPendingKind.CONFLICT_ODO ||
                        item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(archiveFuel),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(focusEntryId) { fetched ->
                            photoPaths = when (item.kind) {
                                BatchPendingKind.BAD_PUMP_RATIO ->
                                    fetched.filter { isPumpPathHint(it) }
                                BatchPendingKind.ODO_SUSPECT ->
                                    fetched.filter { isDashPathHint(it) }
                                else -> fetched
                            }
                        }
                    },
                )
            }

            // Unknown: nearest before/after **per vehicle**
            if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE) {
                val anchorTs = item.timestampMs
                Text(
                    stringResource(R.string.import_nearest_fill_per_vehicle_before_after),
                    style = MaterialTheme.typography.labelMedium,
                )
                perVehicleNeighbors.forEach { pv ->
                    Text(pv.vehicleName, style = MaterialTheme.typography.labelLarge)
                    if (pv.before.isEmpty() && pv.after.isEmpty()) {
                        Text(
                            AppStrings.get(R.string.import_no_other_fills),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    pv.before.forEach { n ->
                        NeighborLine(n, vehicles, prefix = "  ← ", anchorTs = anchorTs)
                    }
                    pv.after.forEach { n ->
                        NeighborLine(n, vehicles, prefix = "  → ", anchorTs = anchorTs)
                    }
                }
                TextButton(onClick = { expandNeighbors += 1 }, enabled = enabled) {
                    Text(stringResource(R.string.import_show_more_2nd_3rd_nearest))
                }
            }

            if (item.kind == BatchPendingKind.ECONOMY_IGNORED) {
                Text(stringResource(R.string.import_context_fills), style = MaterialTheme.typography.labelMedium)
                neighbors.take(12).forEach { n ->
                    NeighborLine(n, vehicles)
                }
                TextButton(
                    onClick = { expandNeighbors += 1 },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.import_show_more_neighbors))
                }
            }

            // Manual pump
            if (item.kind == BatchPendingKind.UNREADABLE_PUMP) {
                Text(stringResource(R.string.import_manual_pump_entry), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CaretEnabledOutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text(stringResource(R.string.fuel_cost)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    CaretEnabledOutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text(stringResource(R.string.import_volume)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        val c = costText.toDoubleOrNull() ?: 0.0
                        val v = volText.toDoubleOrNull() ?: 0.0
                        onAction(PendingAnswerAction.ManualPumpEntry(c, v))
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_cost_vol_re_merge))
                }
                OutlinedButton(
                    onClick = { onAction(PendingAnswerAction.MarkAsGap()) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_mark_as_gap_blank_chain_breaker))
                }
            }

            // Manual dash
            if (item.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                item.kind == BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE
            ) {
                Text(stringResource(R.string.import_manual_odometer), style = MaterialTheme.typography.labelMedium)
                CaretEnabledOutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it },
                    label = { Text(stringResource(R.string.import_odometer)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    showCaretButtons = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                VehiclePickRow(
                    vehicles = vehicles,
                    enabled = enabled,
                    selectedId = selectedVehicleId,
                    onSelect = { selectedVehicleId = it },
                    pumpVol = null,
                    maxFillByVehicle = tankMax,
                )
                Button(
                    onClick = {
                        val odo = odoText.toIntOrNull() ?: 0
                        onAction(
                            PendingAnswerAction.ManualDashEntry(odo, selectedVehicleId),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_odo_re_merge))
                }
            }

            // Conflict free odo
            if (item.kind == BatchPendingKind.CONFLICT_ODO) {
                if (conflictOdos.isNotEmpty()) {
                    Text(stringResource(R.string.import_keep_odometer), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        conflictOdos.forEach { odo ->
                            Button(
                                onClick = {
                                    onAction(PendingAnswerAction.ResolveConflictOdo(odo))
                                },
                                enabled = enabled,
                            ) {
                                Text(AppStrings.get(R.string.import_keep_odo_1_s, odo))
                            }
                        }
                        OutlinedButton(
                            onClick = { onAction(PendingAnswerAction.KeepBothNoMerge) },
                            enabled = enabled,
                        ) {
                            Text(stringResource(R.string.import_keep_both))
                        }
                    }
                }
                CaretEnabledOutlinedTextField(
                    value = freeOdoText,
                    onValueChange = { freeOdoText = it },
                    label = { Text(stringResource(R.string.import_enter_different_odometer)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    showCaretButtons = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val o = freeOdoText.toIntOrNull()
                        if (o != null && o > 0) {
                            onAction(PendingAnswerAction.ResolveConflictOdo(o))
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_use_entered_odo_re_merge))
                }
                OutlinedButton(
                    onClick = { onAction(PendingAnswerAction.KeepBothNoMerge) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_looks_correct_don_t_ask_again))
                }
            }

            // Unknown vehicle assign
            if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE ||
                item.kind == BatchPendingKind.ASSIGN_VEHICLE
            ) {
                Text(stringResource(R.string.import_assign_vehicle), style = MaterialTheme.typography.labelMedium)
                VehiclePickRow(
                    vehicles = vehicles,
                    enabled = enabled,
                    selectedId = selectedVehicleId,
                    onSelect = { vid ->
                        selectedVehicleId = vid
                        if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE) {
                            onAction(PendingAnswerAction.AssignUnknownVehicle(vid))
                        } else {
                            onAction(PendingAnswerAction.AssignVehicle(vid))
                        }
                    },
                    pumpVol = null,
                    maxFillByVehicle = tankMax,
                )
            }

            // Phase 4: suggested vehicle button
            if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE) {
                val sugId = item.extra["suggestedVehicleId"]?.toIntOrNull()
                    ?: item.suggestedVehicleId
                val reason = item.extra["suggestReason"]
                val sugName = sugId?.let { id -> vehicles.find { it.id == id }?.name }
                if (sugId != null && sugId > 0 && sugName != null) {
                    Button(
                        onClick = {
                            onAction(PendingAnswerAction.AssignUnknownVehicle(sugId))
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_assign_to_1_s_suggested, sugName))
                    }
                    if (!reason.isNullOrBlank()) {
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Dash vehicle reprocess (OCR)
            if (item.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE && vehicles.isNotEmpty()) {
                val rawSurvivor = item.extra["survivorIds"]
                val parsedIds = rawSurvivor
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    .orEmpty()
                val cardIds = vehicles.map { it.id }.toSet()
                val parsedSet = parsedIds.toSet()
                val properSubset = rawSurvivor != null &&
                    parsedIds.isNotEmpty() &&
                    parsedSet.all { it in cardIds } &&
                    parsedSet != cardIds
                val shown = if (properSubset && !showAllDashVehicles) {
                    vehicles.filter { it.id in parsedSet }
                } else {
                    vehicles
                }
                Text(stringResource(R.string.import_or_reprocess_ocr_with_vehicle), style = MaterialTheme.typography.labelMedium)
                VehiclePickRow(
                    vehicles = shown,
                    enabled = enabled,
                    selectedId = null,
                    onSelect = { onAction(PendingAnswerAction.AssignVehicle(it)) },
                    pumpVol = null,
                    maxFillByVehicle = emptyMap(),
                )
                if (properSubset && !showAllDashVehicles) {
                    TextButton(
                        onClick = { showAllDashVehicles = true },
                        enabled = enabled,
                    ) {
                        Text(stringResource(R.string.vehicle_show_all_vehicles))
                    }
                }
            }

            // Phase 5: unreadable / ambiguous → may declare gap before MPG phase
            if (item.kind == BatchPendingKind.UNREADABLE_PUMP ||
                item.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP
            ) {
                OutlinedButton(
                    onClick = {
                        onAction(PendingAnswerAction.MarkAsGap(entryId = item.fuelEntryId))
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_this_is_a_gap_blank_breaker))
                }
            }
            if (item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP) {
                OutlinedButton(
                    onClick = {
                        onAction(
                            PendingAnswerAction.AcknowledgeLooksCorrect(
                                kind = "AMBIGUOUS_MULTI_PUMP",
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_looks_correct_don_t_ask_again))
                }
            }

            // Bad pump ratio (phase 3): cost/vol edit or unreadable → gap
            if (item.kind == BatchPendingKind.BAD_PUMP_RATIO) {
                Text(
                    stringResource(R.string.import_cost_volume_look_wrong_vol_outside_2_7_fix),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CaretEnabledOutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text(stringResource(R.string.fuel_cost)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    CaretEnabledOutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text(stringResource(R.string.import_vol)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Button(
                    onClick = {
                        onAction(
                            PendingAnswerAction.ManualEditFuelFields(
                                odometer = null,
                                cost = costText.toDoubleOrNull(),
                                volume = volText.toDoubleOrNull(),
                                entryId = item.fuelEntryId,
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_cost_vol))
                }
                OutlinedButton(
                    onClick = {
                        onAction(PendingAnswerAction.MarkAsGap(entryId = item.fuelEntryId))
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_unreadable_make_a_gap))
                }
                Text(
                    stringResource(R.string.import_blank_chain_breaker_at_this_fill_keeps_tim),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Economy / MPG outlier edit panel (ODO_SUSPECT has per-fill odo UI above)
            if (item.kind == BatchPendingKind.ECONOMY_IGNORED ||
                item.kind == BatchPendingKind.MPG_OUTLIER
            ) {
                val editLabel = when {
                    item.kind == BatchPendingKind.MPG_OUTLIER && mpgFocusThis ->
                        stringResource(R.string.import_editing_this_fill_id_1_s, focusEntryId ?: "?")
                    item.kind == BatchPendingKind.MPG_OUTLIER && !mpgFocusThis ->
                        stringResource(R.string.import_editing_last_fill_id_1_s, focusEntryId ?: "?")
                    else ->
                        stringResource(R.string.import_edit_fields_pre_filled_clears_ignore_on_sa)
                }
                Text(editLabel, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CaretEnabledOutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text(stringResource(R.string.fuel_odo)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    CaretEnabledOutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text(stringResource(R.string.fuel_cost)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    CaretEnabledOutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text(stringResource(R.string.import_vol)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        showCaretButtons = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                // Vertical stack — avoids Save/gap/Ignore clipped in horizontal Row
                // (Screenshot_20260727_183016: only partial checkbox visible)
                Button(
                    onClick = {
                        onAction(
                            PendingAnswerAction.ManualEditFuelFields(
                                odometer = odoText.toIntOrNull(),
                                cost = costText.toDoubleOrNull(),
                                volume = volText.toDoubleOrNull(),
                                entryId = focusEntryId,
                            ),
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_save_edit))
                }
                if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                    val focusComplete = run {
                        val o = odoText.toIntOrNull() ?: 0
                        val c = costText.toDoubleOrNull() ?: 0.0
                        val v = volText.toDoubleOrNull() ?: 0.0
                        o > 0 && c > 0 && v > 0
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = treatPartial && focusComplete,
                            onCheckedChange = { checked ->
                                if (!focusComplete && checked) return@Checkbox
                                treatPartial = checked
                                onAction(
                                    PendingAnswerAction.SetPartialFill(
                                        partial = checked,
                                        entryId = focusEntryId,
                                    ),
                                )
                            },
                            enabled = enabled && focusComplete,
                        )
                        Text(
                            if (focusComplete) {
                                stringResource(R.string.import_treat_as_partial_fill_do_not_use_as_full_f)
                            } else {
                                stringResource(R.string.import_treat_as_partial_need_odo_cost_vol)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            // entryId ignored for MPG — coordinator inserts mid-leg blank
                            onAction(PendingAnswerAction.MarkAsGap(entryId = focusEntryId))
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_missing_data_between_last_this))
                    }
                    Text(
                        stringResource(R.string.import_inserts_a_blank_chain_breaker_between_last),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            onAction(
                                PendingAnswerAction.SetEconomyIgnored(
                                    ignored = true,
                                    entryId = focusEntryId,
                                ),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_ignore))
                    }
                    OutlinedButton(
                        onClick = {
                            onAction(
                                PendingAnswerAction.AcknowledgeLooksCorrect(
                                    kind = AppStrings.get(R.string.import_mpg_outlier),
                                ),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_looks_correct_don_t_ask_again))
                    }
                }
                if (item.kind == BatchPendingKind.ECONOMY_IGNORED) {
                    OutlinedButton(
                        onClick = {
                            onAction(
                                PendingAnswerAction.SetEconomyIgnored(
                                    ignored = false,
                                    entryId = focusEntryId,
                                ),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_unignore))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = { onAction(PendingAnswerAction.Skip) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.import_skip))
                }
                if (item.kind == BatchPendingKind.UNREADABLE_PUMP) {
                    Button(
                        onClick = { onAction(PendingAnswerAction.RetryPump) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.import_retry_pump))
                    }
                }
            }
        }
    }

    zoomPath?.let { path ->
        // Shared zoom chrome (+/−, pinch, Close); edit actions stay on the card
        ZoomablePhotoDialog(
            uris = listOf(path),
            title = item.kindLabel() + " · " + path.substringAfterLast('/'),
            onDismiss = { zoomPath = null },
        )
    }
}

@Composable
private fun OdoPeerBlock(
    title: String,
    emphasize: Boolean,
    photos: List<String>,
    odoText: String,
    onOdoChange: (String) -> Unit,
    metaLine: String,
    enabled: Boolean,
    onPhotoTap: (String) -> Unit,
    canFetchArchive: Boolean = false,
    isFetchingArchive: Boolean = false,
    onFetchArchive: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Empty + no archive identity → "No dash photo"; empty + archive → fetch button via PendingPhotoRow.
        if (photos.isEmpty() && !(canFetchArchive && onFetchArchive != null)) {
            Text(
                stringResource(R.string.import_no_dash_photo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PendingPhotoRow(
                paths = photos,
                conflict = photos.size > 1,
                onTap = onPhotoTap,
                canFetchArchive = canFetchArchive,
                isFetchingArchive = isFetchingArchive,
                onFetchArchive = onFetchArchive,
            )
        }
        if (metaLine.isNotBlank()) {
            Text(
                metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CaretEnabledOutlinedTextField(
            value = odoText,
            onValueChange = onOdoChange,
            label = { Text(stringResource(R.string.fuel_odo)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            showCaretButtons = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

private fun odoPeerMetaLine(
    context: android.content.Context,
    tsStr: String?,
    costStr: String?,
    volStr: String?,
    currency: String = "",
): String {
    val ts = tsStr?.toLongOrNull()
    val whenStr = ts?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    } ?: return ""
    val cost = costStr?.toDoubleOrNull() ?: 0.0
    val vol = volStr?.toDoubleOrNull() ?: 0.0
    val defaultSymbol = CurrencyCodes.settingsDefaultSymbol(context)
    val unit = VolumeUnits.resolvedPreferredVolumeUnit(context)
    return "$whenStr · ${CurrencyCodes.formatAmount(cost, currency, defaultSymbol)} · " +
        VolumeUnits.formatVolume(vol, unit)
}

/** Fallback text when live FuelEntry is missing but pending extra has field snapshots. */
private fun mpgContextFallback(
    context: android.content.Context,
    item: BatchPendingItem,
    isLast: Boolean,
): String {
    val tsKey = if (isLast) "prevTs" else "endTs"
    val odoKey = if (isLast) "prevOdo" else "endOdo"
    val costKey = if (isLast) "prevCost" else "endCost"
    val volKey = if (isLast) "prevVol" else "endVol"
    val ts = item.extra[tsKey]?.toLongOrNull()
    val whenStr = ts?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "?"
    val odo = item.extra[odoKey] ?: "?"
    val cost = item.extra[costKey]?.toDoubleOrNull() ?: 0.0
    val vol = item.extra[volKey]?.toDoubleOrNull() ?: 0.0
    val defaultSymbol = CurrencyCodes.settingsDefaultSymbol(context)
    val unit = VolumeUnits.resolvedPreferredVolumeUnit(context)
    return context.getString(R.string.import_s_1_s_odo_2_s_3_s, whenStr, odo, CurrencyCodes.formatAmount(cost, "", defaultSymbol)) +
        VolumeUnits.formatVolume(vol, unit)
}

@Composable
private fun NeighborLine(
    n: FuelEntry?,
    vehicles: List<Vehicle>,
    prefix: String = "",
    anchorTs: Long? = null,
    badge: String = "",
    fallback: String? = null,
) {
    val context = LocalContext.current
    if (n == null) {
        Text(
            prefix + (fallback ?: "—"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val name = when {
        n.vehicleId == 0 -> stringResource(R.string.ui_unknown)
        else -> vehicles.find { it.id == n.vehicleId }?.name ?: stringResource(R.string.expense_vehicle_1_s, n.vehicleId)
    }
    val flags = buildList {
        if (n.isPartialFill) add("p")
        if (n.economyIgnored) add("ign")
    }.joinToString(",")
    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(n.timestamp))
    val delta = if (anchorTs != null) {
        " · " + formatTimeDelta(n.timestamp - anchorTs)
    } else {
        ""
    }
    val defaultSymbol = CurrencyCodes.settingsDefaultSymbol(context)
    val unit = VolumeUnits.resolvedPreferredVolumeUnit(context)
    Text(
        stringResource(R.string.import_s_1_s_2_s_3_s_odo_4_s, prefix, ts, name, n.odometer) +
            "${CurrencyCodes.formatAmount(n.cost, n.currency, defaultSymbol)} · " +
            VolumeUnits.formatVolume(n.gallons, unit) +
            delta +
            badge +
            if (flags.isNotEmpty()) " [$flags]" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VehiclePickRow(
    vehicles: List<Vehicle>,
    enabled: Boolean,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    pumpVol: Double?,
    maxFillByVehicle: Map<Int, Double>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        vehicles.forEach { v ->
            val maxFill = maxFillByVehicle[v.id]
            val eliminated = pumpVol != null && pumpVol > 0 && maxFill != null &&
                pumpVol > maxFill + FuelRowMergeEngine.TANK_SLACK_GAL
            OutlinedButton(
                onClick = { onSelect(v.id) },
                enabled = enabled && !eliminated,
            ) {
                Text(
                    if (eliminated) {
                        AppStrings.get(R.string.import_s_1_s_tank, v.name)
                    } else if (selectedId == v.id) {
                        "✓ ${v.name}"
                    } else {
                        v.name
                    },
                )
            }
        }
    }
}

@Composable
private fun PendingPhotoRow(
    paths: List<String>,
    conflict: Boolean,
    onTap: (String) -> Unit,
    canFetchArchive: Boolean = false,
    isFetchingArchive: Boolean = false,
    onFetchArchive: (() -> Unit)? = null,
) {
    if (paths.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (canFetchArchive && onFetchArchive != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.import_photo_not_local), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onFetchArchive,
                        enabled = !isFetchingArchive,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(if (isFetchingArchive) stringResource(R.string.fuel_fetching) else stringResource(R.string.fuel_fetch_image_from_archive))
                    }
                }
            } else {
                Text(stringResource(R.string.ui_photo_unavailable), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val thumbH = if (wide) 220.dp else 160.dp
        val multiW = if (wide) 200.dp else 160.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (conflict || paths.size > 1) Modifier.horizontalScroll(scroll) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            paths.forEach { path ->
                PendingPhotoThumb(
                    path = path,
                    modifier = Modifier
                        .then(
                            if (conflict || paths.size > 1) {
                                Modifier.width(multiW)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                        )
                        .height(thumbH),
                    onTap = { onTap(path) },
                    canFetchArchive = canFetchArchive,
                    isFetchingArchive = isFetchingArchive,
                    onFetchArchive = onFetchArchive,
                )
            }
        }
    }
}

@Composable
private fun PendingPhotoThumb(
    path: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    canFetchArchive: Boolean = false,
    isFetchingArchive: Boolean = false,
    onFetchArchive: (() -> Unit)? = null,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var loadState by remember(path) { mutableStateOf("loading") }

    LaunchedEffect(path) {
        loadState = "loading"
        val result = withContext(Dispatchers.IO) { decodePendingPreview(path, maxSide = 512) }
        bitmap = result
        loadState = if (result != null) "ok" else "fail"
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.08f))
                .clickable { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = AppStrings.get(R.string.import_pending_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                loadState == "loading" -> Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodySmall)
                canFetchArchive && onFetchArchive != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppStrings.get(R.string.ui_photo_unavailable), style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = onFetchArchive,
                            enabled = !isFetchingArchive,
                        ) {
                            Text(
                                if (isFetchingArchive) AppStrings.get(R.string.fuel_fetching) else AppStrings.get(R.string.fuel_fetch_image_from_archive),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                else -> Text(stringResource(R.string.ui_photo_unavailable), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            path.substringAfterLast('/'),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

