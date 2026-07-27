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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.davidlang.vehicleexpensesautomated.data.batch.FuelRowMergeEngine
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.batch.pendingPhotoUris
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
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
    var showQuestions by remember { mutableStateOf(false) }
    var pendingSnapshot by remember {
        mutableStateOf(BatchImportPendingStore.load(context).toList())
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
                Text("Pending questions: ${pendingSnapshot.size}")
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
                if (showQuestions) "Hide questions (${pendingSnapshot.size})"
                else "Review questions (${pendingSnapshot.size})",
            )
        }

        if (showQuestions) {
            Text(
                "Photos first (deduped). Manual fields when OCR failed. " +
                    "Successful edits re-run merge. Tap photo to enlarge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingSnapshot.isEmpty()) Text("No pending items.")
            pendingSnapshot.forEach { item ->
                PendingQuestionCard(
                    item = item,
                    vehicles = activeVehicles,
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
    var photoPaths by remember(item.id) { mutableStateOf(pendingPhotoUris(item)) }
    var zoomPath by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(item.id) {
        photoPaths = coordinator.resolvePendingPhotoUris(item)
        // Pre-fill edit fields from live row when possible
        val id = item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: item.extra["endEntryId"]?.toLongOrNull()
        if (id != null) {
            val row = coordinator.getFuelEntry(id)
            if (row != null) {
                if (odoText.isBlank() && row.odometer > 0) odoText = row.odometer.toString()
                if (costText.isBlank() && row.cost > 0) costText = row.cost.toString()
                if (volText.isBlank() && row.gallons > 0) volText = row.gallons.toString()
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
            BatchPendingKind.MPG_OUTLIER,
            BatchPendingKind.ODO_SUSPECT,
            BatchPendingKind.ECONOMY_IGNORED,
            -> {
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.kind.name, style = MaterialTheme.typography.labelLarge)
            Text(item.message, style = MaterialTheme.typography.bodyMedium)

            Text(
                "Tap photo to enlarge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PendingPhotoRow(
                paths = photoPaths,
                conflict = item.kind == BatchPendingKind.CONFLICT_ODO ||
                    item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP ||
                    item.kind == BatchPendingKind.MPG_OUTLIER,
                onTap = { zoomPath = it },
            )

            // MPG outlier metrics
            if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                val mpg = item.extra["mpg"]
                val ref = item.extra["refMpg"]
                if (mpg != null && ref != null) {
                    Text(
                        "Leg mpg=$mpg · ref=$ref · odoΔ=${item.extra["odoDelta"]} · vol=${item.extra["sumVol"]}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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

            // Same-vehicle neighbor context (outliers / odo suspect)
            if (item.kind == BatchPendingKind.MPG_OUTLIER ||
                item.kind == BatchPendingKind.ODO_SUSPECT ||
                item.kind == BatchPendingKind.ECONOMY_IGNORED
            ) {
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

            // Economy / outlier / odo-suspect edit + ignore / flag partial
            if (item.kind == BatchPendingKind.ECONOMY_IGNORED ||
                item.kind == BatchPendingKind.MPG_OUTLIER ||
                item.kind == BatchPendingKind.ODO_SUSPECT
            ) {
                Text(
                    "Edit fields (pre-filled; clears ignore on save):",
                    style = MaterialTheme.typography.labelMedium,
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            onAction(
                                PendingAnswerAction.ManualEditFuelFields(
                                    odometer = odoText.toIntOrNull(),
                                    cost = costText.toDoubleOrNull(),
                                    volume = volText.toDoubleOrNull(),
                                ),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save edit")
                    }
                    if (item.kind == BatchPendingKind.MPG_OUTLIER ||
                        item.kind == BatchPendingKind.ODO_SUSPECT
                    ) {
                        OutlinedButton(
                            onClick = {
                                onAction(
                                    PendingAnswerAction.FlagPartial(
                                        entryId = item.fuelEntryId
                                            ?: item.extra["suspectId"]?.toLongOrNull()
                                            ?: item.extra["endEntryId"]?.toLongOrNull(),
                                    ),
                                )
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Flag as partial")
                        }
                    }
                    if (item.kind == BatchPendingKind.MPG_OUTLIER) {
                        OutlinedButton(
                            onClick = {
                                onAction(PendingAnswerAction.SetEconomyIgnored(true))
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Ignore")
                        }
                    }
                    if (item.kind == BatchPendingKind.ECONOMY_IGNORED) {
                        OutlinedButton(
                            onClick = {
                                onAction(PendingAnswerAction.SetEconomyIgnored(false))
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Unignore")
                        }
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
private fun NeighborLine(
    n: FuelEntry,
    vehicles: List<Vehicle>,
    prefix: String = "",
    anchorTs: Long? = null,
) {
    val name = when {
        n.vehicleId == 0 -> "Unknown"
        else -> vehicles.find { it.id == n.vehicleId }?.name ?: "Vehicle ${n.vehicleId}"
    }
    val flags = buildList {
        if (n.isPartialFill) add("p")
        if (n.economyIgnored) add("ign")
    }.joinToString(",")
    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(n.timestamp))
    val delta = if (anchorTs != null) {
        val dMs = n.timestamp - anchorTs
        val days = dMs / (24.0 * 60 * 60 * 1000)
        when {
            days <= -1 -> " · ${"%.0f".format(-days)}d earlier"
            days >= 1 -> " · ${"%.0f".format(days)}d later"
            else -> {
                val mins = dMs / 60_000.0
                if (mins < 0) " · ${"%.0f".format(-mins)}m earlier"
                else " · ${"%.0f".format(mins)}m later"
            }
        }
    } else ""
    Text(
        "$prefix$ts · $name · odo ${n.odometer} · \$${n.cost} · ${n.gallons}G" +
            delta +
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
) {
    if (paths.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("Photo unavailable", style = MaterialTheme.typography.bodyMedium)
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
            )
        }
    }
}

@Composable
private fun PendingPhotoThumb(
    path: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.kind.name + " · " + path.substringAfterLast('/'),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

            // Sticky actions under image
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                    -> {
                        Button(
                            onClick = {
                                onAction(
                                    PendingAnswerAction.FlagPartial(
                                        entryId = item.fuelEntryId
                                            ?: item.extra["suspectId"]?.toLongOrNull()
                                            ?: item.extra["endEntryId"]?.toLongOrNull(),
                                    ),
                                )
                                onDismiss()
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Flag as partial") }
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
    }
}
