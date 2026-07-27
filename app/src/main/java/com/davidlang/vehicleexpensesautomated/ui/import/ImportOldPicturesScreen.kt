package com.davidlang.vehicleexpensesautomated.ui.import

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.batch.isDngPath
import com.davidlang.vehicleexpensesautomated.data.batch.pendingPhotoUris
import com.davidlang.vehicleexpensesautomated.data.batch.photoPathExists
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.batch.BatchImportViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stage A: batch import + pending questions.
 * Stage B: **Run merge** (+ optional merge-after-import, default off).
 * Stage C: image-first question cards, conflict resolve, re-merge after answers.
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
            "Batch OCR from experiment archives (no gallery picker yet). " +
                "Dash: filesDir/experiment_photos · Pump: externalFiles/pump_photos. " +
                "Set J odo + Set I cost/vol; partials written to DB. " +
                "Pump rows use vehicleId=0 until merge pairs by time/location. " +
                "Questions show photos first so you can read the display.",
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
                "Read the photo(s), then assign vehicle, resolve odo conflict, skip, or retry pump. " +
                    "Successful edits re-run merge automatically.",
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
                    coordinator = coordinator,
                    onAssign = { vid -> applyAnswer(item, PendingAnswerAction.AssignVehicle(vid)) },
                    onSkip = { applyAnswer(item, PendingAnswerAction.Skip) },
                    onRetryPump = { applyAnswer(item, PendingAnswerAction.RetryPump) },
                    onKeepOdo = { odo ->
                        applyAnswer(item, PendingAnswerAction.ResolveConflictOdo(odo))
                    },
                    onKeepBoth = { applyAnswer(item, PendingAnswerAction.KeepBothNoMerge) },
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
    onAssign: (Int) -> Unit,
    onSkip: () -> Unit,
    onRetryPump: () -> Unit,
    onKeepOdo: (Int) -> Unit,
    onKeepBoth: () -> Unit,
) {
    var photoPaths by remember(item.id) { mutableStateOf(pendingPhotoUris(item)) }
    var zoomPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        photoPaths = coordinator.resolvePendingPhotoUris(item)
    }

    val conflictOdos = remember(item) {
        item.extra["odos"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.distinct()
            .orEmpty()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.kind.name, style = MaterialTheme.typography.labelLarge)
            Text(item.message, style = MaterialTheme.typography.bodyMedium)

            PendingPhotoRow(
                paths = photoPaths,
                conflict = item.kind == BatchPendingKind.CONFLICT_ODO ||
                    item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                onTap = { zoomPath = it },
            )

            if (item.kind == BatchPendingKind.CONFLICT_ODO && conflictOdos.isNotEmpty()) {
                Text(
                    "Conflicting odometers — pick the correct reading:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    conflictOdos.forEach { odo ->
                        Button(
                            onClick = { onKeepOdo(odo) },
                            enabled = enabled,
                        ) {
                            Text("Keep odo $odo")
                        }
                    }
                    OutlinedButton(
                        onClick = onKeepBoth,
                        enabled = enabled,
                    ) {
                        Text("Keep both (no merge)")
                    }
                }
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

    zoomPath?.let { path ->
        FullscreenPhotoDialog(path = path, onDismiss = { zoomPath = null })
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
            Text(
                "Photo unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        if (conflict || paths.size > 1) Modifier.width(160.dp) else Modifier.fillMaxWidth(),
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
        loadState = when {
            result != null -> "ok"
            isDngPath(path) -> "dng"
            !photoPathExists(path) -> "missing"
            else -> "fail"
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.08f))
                .clickable(enabled = bitmap != null || loadState == "dng") { onTap() },
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
                loadState == "loading" -> {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall)
                }
                loadState == "dng" -> {
                    Text(
                        "DNG — open full viewer / reprocess\n(tap for path)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                loadState == "missing" -> {
                    Text(
                        "Photo unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    Text(
                        "Photo unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        // Secondary debug only — never primary UI
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
private fun FullscreenPhotoDialog(path: String, onDismiss: () -> Unit) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
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
                    path.substringAfterLast('/'),
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
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Full photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    isDngPath(path) -> {
                        Text(
                            "DNG preview not available as bitmap.\nPath:\n$path\n\nUse assign/reprocess if this is a dash/pump question.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> {
                        Text(
                            "Photo unavailable\n$path",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Downsample-friendly decode for jpg/png. DNG returns null (UI shows DNG affordance).
 */
private fun decodePendingPreview(path: String, maxSide: Int): Bitmap? {
    if (isDngPath(path)) return null
    val filePath = when {
        path.startsWith("file://") -> path.removePrefix("file://")
        else -> path
    }
    val f = File(filePath)
    if (!f.isFile) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / sample > maxSide || h / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(filePath, opts)
    } catch (_: Exception) {
        null
    }
}
