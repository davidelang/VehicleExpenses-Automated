package com.davidlang.vehicleexpensesautomated.ui.import

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportPendingStore
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportProgress
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportResult
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingItem
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingKind
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage A UI: batch import + clickable pending questions.
 * Stage B: explicit **Run merge** (+ optional merge-after-import, default off).
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
    /** Optional: run Stage B after Stage A finishes. Default **off**. */
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
                Toast.makeText(
                    context,
                    "$toastPrefix: ${result.message}",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                mergeStatus = "failed: ${e.message}"
                Toast.makeText(context, "$toastPrefix failed: ${e.message}", Toast.LENGTH_LONG)
                    .show()
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
                Toast.makeText(context, "$toastLabel failed: ${e.message}", Toast.LENGTH_LONG)
                    .show()
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
                val msg = withContext(Dispatchers.Default) {
                    coordinator.applyPendingAnswer(item, vehicles, action)
                }
                reloadPending()
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
            "Batch OCR from experiment archives (no gallery picker yet). " +
                "Dash: filesDir/experiment_photos · Pump: externalFiles/pump_photos. " +
                "Set J odo + Set I cost/vol; partials written to DB. " +
                "Pump rows use vehicleId=0 until merge pairs by time/location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium)
                Text("Dash photos: $dashCount  (${dashDir.absolutePath})")
                Text("Pump photos: $pumpCount  (${pumpDir.absolutePath})")
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
            Text(
                "Inserted dash=${p.dashInserted} pump=${p.pumpInserted} · " +
                    "pending=${p.pendingCount} · errors=${p.errors}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        lastResult?.let { r ->
            Text(
                "Last run: dash=${r.dashInserted} pump=${r.pumpInserted} " +
                    "pending=${r.pending.size} errors=${r.errors.size}" +
                    if (r.cancelled) " (cancelled)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (r.errors.isNotEmpty()) {
                Text(
                    "Errors (first 5):\n" + r.errors.take(5).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
            OutlinedButton(
                onClick = { coordinator.requestCancel() },
                enabled = running,
            ) {
                Text("Cancel")
            }
        }

        OutlinedButton(
            onClick = {
                startIngest(
                    maxDash = limitN,
                    maxPump = limitN,
                    toastLabel = "Limited ($limitN+$limitN)",
                )
            },
            enabled = !busy && (dashCount + pumpCount) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (running) "Running…"
                else "First $limitN dash + first $limitN pump",
            )
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
            Text(
                "Merge after import (default off)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { runMerge() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (merging) "Merging…" else "Run merge")
        }

        mergeStatus?.let { s ->
            Text("Merge: $s", style = MaterialTheme.typography.bodySmall)
        }
        lastMerge?.let { m ->
            Text(
                "Last merge: ${m.message} · total pending=${m.totalPending}",
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
                "Answer each item: assign vehicle (re-runs OCR where needed), skip, or retry pump.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingSnapshot.isEmpty()) {
                Text("No pending items.")
            }
            pendingSnapshot.forEach { item ->
                PendingQuestionCard(
                    item = item,
                    vehicles = activeVehicles,
                    enabled = !busy,
                    onAssign = { vid -> applyAnswer(item, PendingAnswerAction.AssignVehicle(vid)) },
                    onSkip = { applyAnswer(item, PendingAnswerAction.Skip) },
                    onRetryPump = { applyAnswer(item, PendingAnswerAction.RetryPump) },
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
    onAssign: (Int) -> Unit,
    onSkip: () -> Unit,
    onRetryPump: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.kind.name, style = MaterialTheme.typography.labelLarge)
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
            val path = item.photoPath ?: item.durablePhotoPath
            if (path != null) {
                Text(
                    path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val needsVehicle = item.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                item.kind == BatchPendingKind.ASSIGN_VEHICLE ||
                item.kind == BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE

            if (needsVehicle && vehicles.isNotEmpty()) {
                Text("Assign vehicle:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    vehicles.forEach { v ->
                        OutlinedButton(
                            onClick = { onAssign(v.id) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(v.name)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Skip")
                }
                if (item.kind == BatchPendingKind.UNREADABLE_PUMP) {
                    Button(
                        onClick = onRetryPump,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Retry pump")
                    }
                }
            }
        }
    }
}
