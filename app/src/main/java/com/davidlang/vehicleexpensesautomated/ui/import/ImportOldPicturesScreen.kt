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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportPendingStore
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportProgress
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportResult
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage A UI: run batch import from hard-coded experiment photo dirs.
 * Stage B/C (merge + question apply) come later; Review questions lists pending for now.
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
    var progress by remember { mutableStateOf<BatchImportProgress?>(null) }
    var lastResult by remember { mutableStateOf<BatchImportResult?>(null) }
    var showQuestions by remember { mutableStateOf(false) }
    var pendingSnapshot by remember {
        mutableStateOf<List<com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingItem>>(
            BatchImportPendingStore.load(context),
        )
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
                "Pump rows are stored without a vehicle (vehicleId=0); merge will pair by time/location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium)
                Text("Dash photos: $dashCount  (${dashDir.absolutePath})")
                Text("Pump photos: $pumpCount  (${pumpDir.absolutePath})")
                Text("Vehicles in DB: ${vehicles.count { !it.deleted }}")
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

        fun startIngest(maxDash: Int?, maxPump: Int?, toastLabel: String) {
            if (running) return
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
                    Toast.makeText(
                        context,
                        if (result.cancelled) "$toastLabel cancelled"
                        else "$toastLabel done: +${result.dashInserted + result.pumpInserted} rows",
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "$toastLabel failed: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                } finally {
                    running = false
                }
            }
        }

        val limitN = BatchFuelImportCoordinator.LIMITED_IMPORT_COUNT

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { startIngest(null, null, "Batch") },
                enabled = !running && (dashCount + pumpCount) > 0,
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

        // Like experiment Golden / Problem subset buttons — first N by name, not full corpus.
        OutlinedButton(
            onClick = {
                startIngest(
                    maxDash = limitN,
                    maxPump = limitN,
                    toastLabel = "Limited ($limitN+$limitN)",
                )
            },
            enabled = !running && (dashCount + pumpCount) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (running) "Running…"
                else "First $limitN dash + first $limitN pump",
            )
        }

        OutlinedButton(
            onClick = {
                pendingSnapshot = BatchImportPendingStore.load(context)
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
                "Stage C apply actions not implemented yet — list only. Assign vehicle / merge next.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pendingSnapshot.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(item.kind.name, style = MaterialTheme.typography.labelLarge)
                        Text(item.message, style = MaterialTheme.typography.bodyMedium)
                        item.photoPath?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (pendingSnapshot.isEmpty()) {
                Text("No pending items.")
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
