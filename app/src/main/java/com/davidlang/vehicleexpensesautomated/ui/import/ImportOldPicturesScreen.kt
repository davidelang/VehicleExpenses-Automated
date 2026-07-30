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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.davidlang.vehicleexpensesautomated.data.batch.FuelRowMergeEngine
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.batch.StageCAnswerJournal
import com.davidlang.vehicleexpensesautomated.data.batch.StageCPhase
import com.davidlang.vehicleexpensesautomated.data.batch.StageCPhaseStore
import com.davidlang.vehicleexpensesautomated.data.batch.dashPhotoPaths
import com.davidlang.vehicleexpensesautomated.data.batch.dedupePhotoPaths
import com.davidlang.vehicleexpensesautomated.data.batch.pendingPhotoUris
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
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
    var pendingSnapshot by remember {
        mutableStateOf(BatchImportPendingStore.load(context).toList())
    }
    var stagePhase by remember {
        mutableStateOf(StageCPhaseStore.currentPhase(context))
    }
    var showQuestions by remember {
        mutableStateOf(expandReview && pendingSnapshot.isNotEmpty())
    }
    // Pending store is phase-scoped (only current phase kinds)
    val phasePending = pendingSnapshot

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

    fun runMerge(toastPrefix: String = "Merge") {
        if (running || merging || answering) return
        merging = true
        mergeStatus = "Planning…"
        scope.launch {
            try {
                val result = coordinator.applyMerge { msg -> mergeStatus = msg }
                lastMerge = result
                reloadPending()
                Toast.makeText(context, "$toastPrefix: ${result.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                mergeStatus = "failed: ${e.message}"
                Toast.makeText(context, "$toastPrefix failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                merging = false
            }
        }
    }

    fun startIngest(maxDash: Int?, maxPump: Int?, toastLabel: String) {
        if (running || merging) return
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
                )
                lastResult = result
                pendingSnapshot = result.pending
                var toast =
                    if (result.cancelled) "$toastLabel cancelled"
                    else "$toastLabel done: +${result.dashInserted + result.pumpInserted} rows" +
                        " · pending ${result.pending.size}"
                if (mergeAfterImport && !result.cancelled) {
                    mergeStatus = "Merge after import…"
                    val mergeResult = coordinator.applyMerge { msg -> mergeStatus = msg }
                    lastMerge = mergeResult
                    reloadPending()
                    toast += " · merge ${mergeResult.message}"
                }
                Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "$toastLabel failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                    mergeStatus = "Re-merge after answer…"
                    val mergeResult = coordinator.applyMerge { msg -> mergeStatus = msg }
                    lastMerge = mergeResult
                    reloadPending()
                    toast += " · merge ${mergeResult.message}"
                }
                Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Answer failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        Text("Import Old Pictures", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Batch OCR · merge · image-first questions with manual entry. " +
                "Tap photo to enlarge (+/− zoom). Unknown vehicle never labeled as id 0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium)
                Text("Dash photos: $dashCount")
                Text("Pump photos: $pumpCount")
                Text("Vehicles in DB: ${activeVehicles.size}")
                Text(
                    "${StageCPhaseStore.label(stagePhase)} · " +
                        "${pendingSnapshot.size} questions (this phase only)",
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
        val busy = running || answering || merging

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { startIngest(null, null, "Batch") },
                enabled = !busy && (dashCount + pumpCount) > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "Running…" else "Run batch import")
            }
            OutlinedButton(onClick = { coordinator.requestCancel() }, enabled = running) {
                Text("Cancel")
            }
        }

        OutlinedButton(
            onClick = {
                startIngest(limitN, limitN, "Limited ($limitN+$limitN)")
            },
            enabled = !busy && (dashCount + pumpCount) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) "Running…" else "First $limitN dash + first $limitN pump")
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
            Text("Merge after import (default off)", style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = { runMerge() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (merging) "Merging…" else "Run merge")
        }

        OutlinedButton(
            onClick = {
                if (running || merging || answering) return@OutlinedButton
                merging = true
                mergeStatus = "Clearing pending…"
                scope.launch {
                    try {
                        val result = coordinator.clearPendingAndRescan { msg -> mergeStatus = msg }
                        lastMerge = result
                        reloadPending()
                        Toast.makeText(
                            context,
                            "Clear & re-scan: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Re-scan failed: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        merging = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear questions & re-scan")
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
                            "Cleared $n auto/illegal partial flags",
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Repair failed: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        answering = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Repair: clear partial flags")
        }

        mergeStatus?.let { Text("Merge: $it", style = MaterialTheme.typography.bodySmall) }
        lastMerge?.let {
            Text(
                "Last merge: ${it.message} · total pending=${it.totalPending}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedButton(
            onClick = {
                reloadPending()
                showQuestions = !showQuestions
            },
            enabled = pendingSnapshot.isNotEmpty() || showQuestions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (showQuestions) "Hide questions (${phasePending.size})"
                else "Review questions (${phasePending.size} in phase $stagePhase)",
            )
        }

        if (showQuestions) {
            Text(
                StageCPhaseStore.label(stagePhase),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Phase-scoped queue: only this phase is generated. " +
                    "Next phase rebuilds from Room after your answers. Skip hides for this phase only. " +
                    "Tap photo to enlarge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (busy || stagePhase >= StageCPhase.MAX) return@Button
                        merging = true
                        mergeStatus = "Next phase…"
                        scope.launch {
                            try {
                                val result = coordinator.advancePhaseAndRebuild { msg ->
                                    mergeStatus = msg
                                }
                                lastMerge = result
                                reloadPending()
                                val p = StageCPhaseStore.currentPhase(context)
                                Toast.makeText(
                                    context,
                                    "Phase $p: ${result.totalPending} questions",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Next phase failed: ${e.message}",
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
                    Text("Next phase")
                }
                OutlinedButton(
                    onClick = {
                        StageCPhaseStore.resetToPhase1(context)
                        stagePhase = 1
                        runMerge(toastPrefix = "Rescan phase 1")
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset to phase 1")
                }
            }
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
                                    "Journal exported ($n lines) → ${out.absolutePath}"
                                } else {
                                    "Journal empty or export failed"
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export answer journal")
            }
            if (phasePending.isEmpty()) {
                Text("No pending items in this phase. Tap Next phase when ready.")
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

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
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
        mutableStateOf<List<BatchFuelImportCoordinator.PerVehicleNeighbor>>(emptyList())
    }
    var selectedVehicleId by remember(item.id) {
        mutableStateOf(item.suggestedVehicleId)
    }
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
                val uris = FuelPhotoJson.parse(refreshed.photoUrl).map { it.uri }
                if (uris.isNotEmpty()) {
                    onPaths(uris)
                    Toast.makeText(context, "Image fetched", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not fetch image", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Fetch failed", Toast.LENGTH_LONG).show()
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
            Text(item.kind.name, style = MaterialTheme.typography.labelLarge)
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
                            "Δt last→this $legDt",
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
                        "Fills in this leg (time order)",
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
                Text("── Last fill ──", style = MaterialTheme.typography.titleSmall)
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
                    ) { Text("Last fill") }
                } else {
                    Button(
                        onClick = { mpgFocusThis = false },
                        enabled = enabled && lastEntryId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Last fill (selected)") }
                }

                // ── This fill (photos above button) ──
                Text("── This fill ──", style = MaterialTheme.typography.titleSmall)
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
                    ) { Text("This fill (selected)") }
                } else {
                    OutlinedButton(
                        onClick = { mpgFocusThis = true },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("This fill") }
                }

                // ── Context (text only): before last, last, this, after this ──
                Text("── Context (text only) ──", style = MaterialTheme.typography.titleSmall)
                beforeLast?.let { n ->
                    NeighborLine(
                        n, vehicles,
                        prefix = "Before last: ",
                        anchorTs = thisTsAnchor,
                    )
                } ?: Text(
                    "Before last: —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NeighborLine(
                    lastRow,
                    vehicles,
                    prefix = "Last fill: ",
                    anchorTs = thisTsAnchor,
                    fallback = mpgContextFallback(
                        context, item, isLast = true,
                    ),
                )
                NeighborLine(
                    thisRow,
                    vehicles,
                    prefix = "This fill: ",
                    anchorTs = thisTsAnchor,
                    fallback = mpgContextFallback(
                        context, item, isLast = false,
                    ),
                )
                afterThis?.let { n ->
                    NeighborLine(
                        n, vehicles,
                        prefix = "After this: ",
                        anchorTs = thisTsAnchor,
                    )
                } ?: Text(
                    "After this: —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (item.kind == BatchPendingKind.ODO_SUSPECT &&
                item.extra["mode"] == "simple"
            ) {
                // Phase 1 short UI: one dash image + pre-filled guess
                val guess = item.extra["suggestedOdo"] ?: item.extra["parsedOdo"].orEmpty()
                var simpleOdo by remember(item.id) { mutableStateOf(guess) }
                Text(
                    "Suggested fix (length OCR). Edit if wrong, then Save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PendingPhotoRow(
                    paths = photoPaths.ifEmpty {
                        listOfNotNull(item.photoPath ?: item.durablePhotoPath)
                    },
                    conflict = false,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(archiveFuel),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(focusEntryId) { photoPaths = it }
                    },
                )
                OutlinedTextField(
                    value = simpleOdo,
                    onValueChange = { simpleOdo = it },
                    label = { Text("Odometer") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
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
                    Text("Save odometer")
                }
            } else if (item.kind == BatchPendingKind.ODO_SUSPECT) {
                // Phase 2: per-fill dash-only blocks previous → cur → next
                val reason = item.extra["reason"] ?: "odo"
                val prevTs = item.extra["prevTs"]?.toLongOrNull()
                val curTs = item.extra["curTs"]?.toLongOrNull()
                val nextTs = item.extra["nextTs"]?.toLongOrNull()
                val dtLine = buildString {
                    if (prevTs != null && curTs != null) {
                        append("Δt prev→cur ${formatTimeDelta(curTs - prevTs)}")
                    }
                    if (curTs != null && nextTs != null) {
                        if (isNotEmpty()) append(" · ")
                        append("Δt cur→next ${formatTimeDelta(nextTs - curTs)}")
                    }
                }
                if (dtLine.isNotEmpty()) {
                    Text(dtLine, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Dash photos only — fix any wrong odo, then Save odometers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                OdoPeerBlock(
                    title = if (odoSuspectId == odoPrevId) {
                        "── Previous fill (suspect) ──"
                    } else {
                        "── Previous fill ──"
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
                )
                OdoPeerBlock(
                    title = if (odoSuspectId == odoCurId || odoSuspectId == null) {
                        "── This fill (suspect) ──"
                    } else {
                        "── This fill ──"
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
                )
                if (odoNextId != null) {
                    OdoPeerBlock(
                        title = if (odoSuspectId == odoNextId) {
                            "── Next fill (suspect) ──"
                        } else {
                            "── Next fill ──"
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
                    Text("Save odometers")
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
                            "Treat as partial fill (suspect, all fields present)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text(
                    "Tap photo to enlarge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PendingPhotoRow(
                    paths = photoPaths,
                    conflict = item.kind == BatchPendingKind.CONFLICT_ODO ||
                        item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                    onTap = { zoomPath = it },
                    canFetchArchive = canFetchFor(archiveFuel),
                    isFetchingArchive = fetchingArchive,
                    onFetchArchive = {
                        fetchArchiveFor(focusEntryId) { photoPaths = it }
                    },
                )
            }

            // Unknown: nearest before/after **per vehicle**
            if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE) {
                val anchorTs = item.timestampMs
                Text(
                    "Nearest fill per vehicle (before / after):",
                    style = MaterialTheme.typography.labelMedium,
                )
                perVehicleNeighbors.forEach { pv ->
                    Text(pv.vehicleName, style = MaterialTheme.typography.labelLarge)
                    if (pv.before.isEmpty() && pv.after.isEmpty()) {
                        Text(
                            "  (no other fills)",
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
                    Text("Show more (2nd/3rd nearest)")
                }
            }

            if (item.kind == BatchPendingKind.ECONOMY_IGNORED) {
                Text("Context fills:", style = MaterialTheme.typography.labelMedium)
                neighbors.take(12).forEach { n ->
                    NeighborLine(n, vehicles)
                }
                TextButton(
                    onClick = { expandNeighbors += 1 },
                    enabled = enabled,
                ) {
                    Text("Show more neighbors")
                }
            }

            // Manual pump
            if (item.kind == BatchPendingKind.UNREADABLE_PUMP) {
                Text("Manual pump entry:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text("Volume") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
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
                    Text("Save cost/vol + re-merge")
                }
                OutlinedButton(
                    onClick = { onAction(PendingAnswerAction.MarkAsGap()) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mark as gap (blank chain-breaker)")
                }
            }

            // Manual dash
            if (item.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                item.kind == BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE
            ) {
                Text("Manual odometer:", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it },
                    label = { Text("Odometer") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
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
                    Text("Save odo + re-merge")
                }
            }

            // Conflict free odo
            if (item.kind == BatchPendingKind.CONFLICT_ODO) {
                if (conflictOdos.isNotEmpty()) {
                    Text("Keep odometer:", style = MaterialTheme.typography.labelMedium)
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
                                Text("Keep odo $odo")
                            }
                        }
                        OutlinedButton(
                            onClick = { onAction(PendingAnswerAction.KeepBothNoMerge) },
                            enabled = enabled,
                        ) {
                            Text("Keep both")
                        }
                    }
                }
                OutlinedTextField(
                    value = freeOdoText,
                    onValueChange = { freeOdoText = it },
                    label = { Text("Enter different odometer") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
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
                    Text("Use entered odo + re-merge")
                }
                OutlinedButton(
                    onClick = { onAction(PendingAnswerAction.KeepBothNoMerge) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Looks correct — don't ask again")
                }
            }

            // Unknown vehicle assign
            if (item.kind == BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE ||
                item.kind == BatchPendingKind.ASSIGN_VEHICLE
            ) {
                Text("Assign vehicle:", style = MaterialTheme.typography.labelMedium)
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
                        Text("Assign to $sugName (suggested)")
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
                Text("Or reprocess OCR with vehicle:", style = MaterialTheme.typography.labelMedium)
                VehiclePickRow(
                    vehicles = vehicles,
                    enabled = enabled,
                    selectedId = null,
                    onSelect = { onAction(PendingAnswerAction.AssignVehicle(it)) },
                    pumpVol = null,
                    maxFillByVehicle = emptyMap(),
                )
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
                    Text("This is a gap (blank breaker)")
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
                    Text("Looks correct — don't ask again")
                }
            }

            // Bad pump ratio (phase 3): cost/vol edit or unreadable → gap
            if (item.kind == BatchPendingKind.BAD_PUMP_RATIO) {
                Text(
                    "Cost/volume look wrong (\$/vol outside 2–7). Fix numbers or mark as gap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text("Vol") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
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
                    Text("Save cost / vol")
                }
                OutlinedButton(
                    onClick = {
                        onAction(PendingAnswerAction.MarkAsGap(entryId = item.fuelEntryId))
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Unreadable — make a gap")
                }
                Text(
                    "Blank chain-breaker at this fill (keeps timestamp/vehicle).",
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
                        "Editing: This fill (id=${focusEntryId ?: "?"})"
                    item.kind == BatchPendingKind.MPG_OUTLIER && !mpgFocusThis ->
                        "Editing: Last fill (id=${focusEntryId ?: "?"})"
                    else ->
                        "Edit fields (pre-filled; clears ignore on save)"
                }
                Text(editLabel, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text("Odo") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text("Vol") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        singleLine = true,
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
                    Text("Save edit")
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
                                "Treat as partial fill (do not use as full-fill anchor)"
                            } else {
                                "Treat as partial (need odo+cost+vol)"
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
                        Text("Missing data between last & this")
                    }
                    Text(
                        "Inserts a blank chain-breaker between last and this fills; keeps both fills.",
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
                        Text("Ignore")
                    }
                    OutlinedButton(
                        onClick = {
                            onAction(
                                PendingAnswerAction.AcknowledgeLooksCorrect(
                                    kind = "MPG_OUTLIER",
                                ),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Looks correct — don't ask again")
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
                        Text("Unignore")
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
                    Text("Skip")
                }
                if (item.kind == BatchPendingKind.UNREADABLE_PUMP) {
                    Button(
                        onClick = { onAction(PendingAnswerAction.RetryPump) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Retry pump")
                    }
                }
            }
        }
    }

    zoomPath?.let { path ->
        FullscreenPhotoDialog(
            path = path,
            item = item,
            vehicles = vehicles,
            enabled = enabled,
            costText = costText,
            volText = volText,
            odoText = odoText,
            freeOdoText = freeOdoText,
            onCost = { costText = it },
            onVol = { volText = it },
            onOdo = { odoText = it },
            onFreeOdo = { freeOdoText = it },
            onAction = onAction,
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
        if (photos.isEmpty()) {
            Text(
                "No dash photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PendingPhotoRow(
                paths = photos,
                conflict = photos.size > 1,
                onTap = onPhotoTap,
            )
        }
        if (metaLine.isNotBlank()) {
            Text(
                metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = odoText,
            onValueChange = onOdoChange,
            label = { Text("Odo") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
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
    return "$whenStr · odo $odo · ${CurrencyCodes.formatAmount(cost, "", defaultSymbol)} · " +
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
        n.vehicleId == 0 -> "Unknown"
        else -> vehicles.find { it.id == n.vehicleId }?.name ?: "Vehicle ${n.vehicleId}"
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
        "$prefix$ts · $name · odo ${n.odometer} · " +
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
                        "${v.name} (tank)"
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
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (canFetchArchive && onFetchArchive != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Photo not local", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onFetchArchive,
                        enabled = !isFetchingArchive,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(if (isFetchingArchive) "Fetching…" else "Fetch image from archive")
                    }
                }
            } else {
                Text("Photo unavailable", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val scroll = rememberScrollState()
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
                            Modifier.width(160.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    .height(160.dp),
                onTap = { onTap(path) },
                canFetchArchive = canFetchArchive,
                isFetchingArchive = isFetchingArchive,
                onFetchArchive = onFetchArchive,
            )
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
                        contentDescription = "Pending photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                loadState == "loading" -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                canFetchArchive && onFetchArchive != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Photo unavailable", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = onFetchArchive,
                            enabled = !isFetchingArchive,
                        ) {
                            Text(
                                if (isFetchingArchive) "Fetching…" else "Fetch image from archive",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                else -> Text("Photo unavailable", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun FullscreenPhotoDialog(
    path: String,
    item: BatchPendingItem,
    vehicles: List<Vehicle>,
    enabled: Boolean,
    costText: String,
    volText: String,
    odoText: String,
    freeOdoText: String,
    onCost: (String) -> Unit,
    onVol: (String) -> Unit,
    onOdo: (String) -> Unit,
    onFreeOdo: (String) -> Unit,
    onAction: (PendingAnswerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(path) {
        scale = 1f
        offset = Offset.Zero
        bitmap = withContext(Dispatchers.IO) { decodePendingPreview(path, maxSide = 2048) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                Text(
                    item.kind.name + " · " + path.substringAfterLast('/'),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 80.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RectangleShape)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 10f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Full photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("Photo unavailable", color = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scale = (scale * 1.2f).coerceIn(1f, 10f) },
                            containerColor = Color.White.copy(alpha = 0.75f),
                        ) { Text("+") }
                        SmallFloatingActionButton(
                            onClick = {
                                scale = (scale / 1.2f).coerceIn(1f, 10f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                            containerColor = Color.White.copy(alpha = 0.75f),
                        ) { Text("−") }
                    }
                }

                // Sticky actions under image (clipped image area above)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .background(Color(0xEE222222))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(item.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                when (item.kind) {
                    BatchPendingKind.UNREADABLE_PUMP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = costText,
                                onValueChange = onCost,
                                label = { Text("Cost") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = volText,
                                onValueChange = onVol,
                                label = { Text("Vol") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        Button(
                            onClick = {
                                onAction(
                                    PendingAnswerAction.ManualPumpEntry(
                                        costText.toDoubleOrNull() ?: 0.0,
                                        volText.toDoubleOrNull() ?: 0.0,
                                    ),
                                )
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save cost/vol") }
                        OutlinedButton(
                            onClick = {
                                onAction(PendingAnswerAction.MarkAsGap())
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Mark as gap") }
                        Button(
                            onClick = {
                                onAction(PendingAnswerAction.RetryPump)
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Retry pump") }
                    }
                    BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE -> {
                        OutlinedTextField(
                            value = odoText,
                            onValueChange = onOdo,
                            label = { Text("Odometer") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        vehicles.forEach { v ->
                            OutlinedButton(
                                onClick = {
                                    val odo = odoText.toIntOrNull()
                                    if (odo != null && odo > 0) {
                                        onAction(PendingAnswerAction.ManualDashEntry(odo, v.id))
                                    } else {
                                        onAction(PendingAnswerAction.AssignVehicle(v.id))
                                    }
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(v.name) }
                        }
                    }
                    BatchPendingKind.CONFLICT_ODO -> {
                        item.extra["odos"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                            ?.forEach { odo ->
                                Button(
                                    onClick = {
                                        onAction(PendingAnswerAction.ResolveConflictOdo(odo))
                                        onDismiss()
                                    },
                                    enabled = enabled,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Keep odo $odo") }
                            }
                        OutlinedTextField(
                            value = freeOdoText,
                            onValueChange = onFreeOdo,
                            label = { Text("Different odo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                freeOdoText.toIntOrNull()?.takeIf { it > 0 }?.let {
                                    onAction(PendingAnswerAction.ResolveConflictOdo(it))
                                    onDismiss()
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Use entered odo") }
                        OutlinedButton(
                            onClick = {
                                onAction(PendingAnswerAction.KeepBothNoMerge)
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Looks correct — don't ask again") }
                    }
                    BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE -> {
                        vehicles.forEach { v ->
                            Button(
                                onClick = {
                                    onAction(PendingAnswerAction.AssignUnknownVehicle(v.id))
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(v.name) }
                        }
                    }
                    BatchPendingKind.MPG_OUTLIER,
                    BatchPendingKind.ODO_SUSPECT,
                    BatchPendingKind.BAD_PUMP_RATIO,
                    BatchPendingKind.UNREADABLE_PUMP,
                    BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
                    BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                    -> {
                        if (item.kind == BatchPendingKind.MPG_OUTLIER ||
                            item.kind == BatchPendingKind.ODO_SUSPECT
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onAction(
                                        PendingAnswerAction.SetPartialFill(
                                            partial = true,
                                            entryId = item.fuelEntryId
                                                ?: item.extra["suspectId"]?.toLongOrNull()
                                                ?: item.extra["endEntryId"]?.toLongOrNull(),
                                        ),
                                    )
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Treat as partial (if complete)") }
                        }
                        if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                            OutlinedButton(
                                onClick = {
                                    onAction(
                                        PendingAnswerAction.AcknowledgeLooksCorrect(
                                            kind = "MPG_OUTLIER",
                                        ),
                                    )
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Looks correct — don't ask again") }
                        }
                        if (item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP) {
                            OutlinedButton(
                                onClick = {
                                    onAction(
                                        PendingAnswerAction.AcknowledgeLooksCorrect(
                                            kind = "AMBIGUOUS_MULTI_PUMP",
                                        ),
                                    )
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Looks correct — don't ask again") }
                        }
                        OutlinedButton(
                            onClick = {
                                onAction(
                                    PendingAnswerAction.MarkAsGap(
                                        entryId = item.fuelEntryId
                                            ?: item.extra["suspectId"]?.toLongOrNull()
                                            ?: item.extra["endEntryId"]?.toLongOrNull(),
                                    ),
                                )
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (item.kind) {
                                    BatchPendingKind.MPG_OUTLIER ->
                                        "Missing data between last & this"
                                    BatchPendingKind.BAD_PUMP_RATIO ->
                                        "Unreadable — make a gap"
                                    else -> "This is a gap"
                                },
                            )
                        }
                        if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                            Button(
                                onClick = {
                                    onAction(PendingAnswerAction.SetEconomyIgnored(true))
                                    onDismiss()
                                },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Ignore in economy") }
                        }
                    }
                    BatchPendingKind.ECONOMY_IGNORED -> {
                        Button(
                            onClick = {
                                onAction(PendingAnswerAction.SetEconomyIgnored(false))
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Unignore") }
                    }
                    else -> {}
                }
                    OutlinedButton(
                        onClick = {
                            onAction(PendingAnswerAction.Skip)
                            onDismiss()
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip") }
                }
            }
            // Close always on top (not under zoomed image)
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}
