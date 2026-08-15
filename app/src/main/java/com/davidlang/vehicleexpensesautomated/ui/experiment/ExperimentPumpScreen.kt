package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

import android.graphics.RectF
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import org.opencv.imgproc.Imgproc
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.*
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ExperimentPump"

/**
 * Pump experiment precision pack for phone vs emulator A/B:
 * - Emulator (x86 / ranchu / sdk fingerprint): [prod_u8fp32_u8] true fp32 mid-graph
 * - Physical phone (arm): [prod_u8fp16] true fp16 mid-graph
 */
private fun experimentPumpProductDir(): String {
    val isEmu =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.FINGERPRINT.contains("sdk_", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.SUPPORTED_ABIS.any { it.startsWith("x86") }
    return if (isEmu) "prod_u8fp32_u8" else "prod_u8fp16"
}

private fun pruneRedPixelsTopN(rects: MutableList<Rect>, context: Context, imgH: Int = 0) {
    PumpCostVolUtils.pruneRectsToTopN(rects, PumpOcrSettings.maxRedBoxes(context), imgH)
}

private fun safeTraceName(fileName: String): String =
    fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun rectJson(r: Rect): JSONObject =
    JSONObject().put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom)

private fun samplesJson(samples: List<ContentExpandUtils.VertEnergySample>): JSONArray {
    val arr = JSONArray()
    samples.forEach { s ->
        arr.put(
            JSONArray()
                .put(s.dy)
                .put(s.energy)
                .put(s.ratio)
                .put(s.width)
                .put(s.count),
        )
    }
    return arr
}

private fun countPullJson(p: ContentExpandUtils.CountPullInfo?): Any {
    if (p == null) return JSONObject.NULL
    val counts = JSONArray()
    p.counts.forEach { counts.put(it) }
    return JSONObject()
        .put("pulledTop", p.pulledTop)
        .put("pulledBot", p.pulledBot)
        .put("cSeed", p.cSeed)
        .put("countThr", p.countThr)
        .put("gxThr", p.gxThr)
        .put("tBefore", p.tBefore)
        .put("bBefore", p.bBefore)
        .put("tAfter", p.tAfter)
        .put("bAfter", p.bAfter)
        .put("y0", p.y0)
        .put("counts", counts)
        .put("axis", p.axis)
        .put("vNegBefore", p.vNegBefore)
        .put("vPosBefore", p.vPosBefore)
        .put("vNegAfter", p.vNegAfter)
        .put("vPosAfter", p.vPosAfter)
}

private fun rawRoiJson(roi: ContentExpandUtils.EnergyPixelRoi?): Any {
    if (roi == null) return JSONObject.NULL
    return JSONObject()
        .put("l", roi.l)
        .put("t", roi.t)
        .put("w", roi.w)
        .put("h", roi.h)
        .put("look", roi.look)
        .put("hPad", roi.hPad)
        .put("sobelScale", roi.sobelScale.toDouble())
        .put("gray_u8_zlib_b64", Base64.encodeToString(roi.grayU8Zlib, Base64.NO_WRAP))
        .put("sobel_u16le_zlib_b64", Base64.encodeToString(roi.sobelU16leZlib, Base64.NO_WRAP))
}

/** Sidecar lossless energy grow traces (P4-jump and P4-rot, every photo). */
private fun writeExpandEnergyTrace(
    out: File,
    fileName: String,
    maxFrac: Float,
    traces: List<ContentExpandUtils.VertEnergyTrace>,
    column: String,
) {
    val boxes = JSONArray()
    traces.forEachIndexed { i, tr ->
        val box = JSONObject()
            .put("i", i)
            .put("seed", rectJson(tr.seed))
            .put("final", rectJson(tr.final))
            .put("base", tr.base)
            .put("thr", tr.thr)
            .put("energyRatio", tr.energyRatio.toDouble())
            .put("stopUp", tr.stopUp)
            .put("stopDown", tr.stopDown)
            .put("stopEnergyUp", tr.stopEnergyUp)
            .put("stopEnergyDown", tr.stopEnergyDown)
            .put("up", samplesJson(tr.up))
            .put("down", samplesJson(tr.down))
            .put("inside", samplesJson(tr.inside))
            .put("scanUp", samplesJson(tr.scanUp))
            .put("scanDown", samplesJson(tr.scanDown))
            .put("afterUp", samplesJson(tr.afterUp))
            .put("afterDown", samplesJson(tr.afterDown))
            .put("lookAhead", tr.lookAhead)
            .put("raw", rawRoiJson(tr.rawRoi))
            .put("countPull", countPullJson(tr.countPull))
        tr.finalCount?.let { box.put("finalCount", rectJson(it)) }
        boxes.put(box)
    }
    val root = JSONObject()
        .put("file", fileName)
        .put("column", column)
        .put("maxFrac", maxFrac.toDouble())
        .put("sample", "[dy, energy, energy/base, width, gxRunCount]")
        .put(
            "scan",
            "inside/scanUp/scanDown/after*: every 1px at stop width, no 0.45 gate. " +
                "up/down = grow-accepted only. scan* from seed edge through grow+lookAhead " +
                "(min ${ContentExpandUtils.VERT_ENERGY_LOOKAHEAD_MIN_PX} or seedH). " +
                "after* = scan* past the stop. Fifth sample is smoothed gx-run-count.",
        )
        .put(
            "raw",
            "Lossless zlib only (not JPEG): raw.gray_u8_zlib_b64 + raw.sobel_u16le_zlib_b64 " +
                "are deskewed gray and Sobel mag, row-major, seed ± " +
                "${ContentExpandUtils.VERT_ENERGY_RAW_LOOK_FRAC}·seedH vert and ± " +
                "${ContentExpandUtils.VERT_ENERGY_RAW_HPAD_FRAC}·seedW horiz. " +
                "sobel_u16 = round(mag*${ContentExpandUtils.VERT_ENERGY_RAW_SCALE}). " +
                "Replay stop rules and edge-count from gray; do not use only the strip mean.",
        )
        .put("boxes", boxes)
    out.parentFile?.mkdirs()
    out.writeText(root.toString())
}

private fun getPhotoFragmentFile(reportDir: File, ts: String, idx: Int): File {
    val fragDir = File(reportDir, "fragments")
    if (!fragDir.exists()) fragDir.mkdirs()
    return File(fragDir, "photo_${ts}_${String.format(Locale.US, "%04d", idx)}.jsonfrag")
}

// Legacy batch-combine helper; main pump path streams per-row JSON directly and deletes frags immediately.
private fun Appendable.jsonAppend(s: String): Appendable {
    try {
        append(s)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
    return this
}

private fun Appendable.jsonAppend(c: Char): Appendable {
    try {
        append(c)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
    return this
}

private fun logHeapState(context: Context, label: String) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val runtime = Runtime.getRuntime()
    Log.i(
        TAG,
        "heap[$label] memoryClass=${am.memoryClass}MB largeMemoryClass=${am.largeMemoryClass}MB " +
            "runtime max=${runtime.maxMemory()} total=${runtime.totalMemory()} free=${runtime.freeMemory()} " +
            "availMem=${mi.availMem} threshold=${mi.threshold} lowMemory=${mi.lowMemory}",
    )
    // Same PSS / sys / swap lines as multi-scale det (compare first paddle detect jump).
    ProcessMemProbe.log("pump_$label")
}

@Immutable
data class PumpPhotoResultSummary(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val odometer: String?
)

// PumpHunk, PumpRectOcrLists, RedBoxOcrCandidate, PathResult, CostVolClassifyResult: see PumpCostVolUtils.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentPumpScreen(
    navController: NavHostController,
    autoFirst10: Boolean = false,
    /** Deep link: vehicleexpenses://experiment/pump?auto=l1debug — dump L1 SO buffers only. */
    autoL1Debug: Boolean = false,
    /** Deep link: vehicleexpenses://experiment/pump?auto=horiz — horiz-affected subset. */
    autoHorizAffected: Boolean = false,
    /** Deep link: vehicleexpenses://experiment/pump?auto=selected — coverage selected sample (pump only). */
    autoSelectedSample: Boolean = false,
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()
    val jobState by ExperimentJobRunner.state.collectAsState()

    var status by remember { mutableStateOf("Ready to run experiment") }
    var detailLog by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var currentPhotoName by remember { mutableStateOf("") }
    var totalPhotos by remember { mutableIntStateOf(0) }
    val resultsList = remember { mutableStateListOf<PumpPhotoResultSummary>() }
    var autoStarted by remember { mutableStateOf(false) }
    val isRunning = jobState.active && jobState.kind == "pump"

    val experimentDir = File(context.getExternalFilesDir(null), "pump_photos")
    experimentDir.mkdirs()
    val reportDir = File(context.getExternalFilesDir(null), "pump_reports")

    if (!reportDir.exists()) reportDir.mkdirs()

    fun startPumpJob(subsetNames: List<String>?, label: String) {
        val n = if (subsetNames != null) {
            subsetNames.size
        } else {
            experimentDir.listFiles { f ->
                f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
            }?.size ?: 0
        }
        totalPhotos = n
        resultsList.clear()
        val ok = ExperimentJobRunner.start(context.applicationContext, kind = "pump") { progressCb, log, statusLine ->
            statusLine(label)
            val out = runPumpExperiment(
                experimentDir,
                reportDir,
                context.applicationContext,
                log,
                subsetNames,
            ) { res, p ->
                val done = (p * n.toFloat()).toInt().coerceIn(1, n.coerceAtLeast(1))
                progressCb(done, n.coerceAtLeast(1), res.photoName)
            }
            out?.absolutePath
        }
        if (!ok) {
            status = "Another experiment is already running (${ExperimentJobRunner.state.value.kind})"
        } else {
            status = label
        }
    }

    val runFirst10: () -> Unit = {
        val allFiles = experimentDir.listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        } ?: emptyArray()
        val first10Names = allFiles.sortedBy { it.name }.take(10).map { it.name }
        Log.d(TAG, "First 10 listFiles: dir=${experimentDir.absolutePath} count=${first10Names.size}")
        startPumpJob(first10Names, "First 10 (${first10Names.size})…")
    }

    /** Coverage selected sample — pump domain only (see [SelectedSamplePhotos.PUMP]). */
    val runSelectedSample: () -> Unit = {
        val names = SelectedSamplePhotos.presentInOrder(experimentDir, SelectedSamplePhotos.PUMP)
        val missing = SelectedSamplePhotos.PUMP.size - names.size
        Log.d(
            TAG,
            "Selected sample (pump): dir=${experimentDir.absolutePath} " +
                "matched=${names.size}/${SelectedSamplePhotos.PUMP.size} missing=$missing",
        )
        if (names.isEmpty()) {
            status =
                "Selected sample: 0 pump photos present " +
                    "(need up to ${SelectedSamplePhotos.PUMP.size} in pump_photos)"
        } else {
            startPumpJob(
                names,
                "Selected sample (${names.size} pump)…" +
                    if (missing > 0) " ($missing not on device)" else "",
            )
        }
    }

    /** Photos whose exact-pool min_v changed between horiz 0.5 and 1.0 (phone 08-08 pair). */
    val runHorizAffected: () -> Unit = {
        val allFiles = experimentDir.listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        } ?: emptyArray()
        val want = HORIZ_REACH_AFFECTED_FILENAMES.toSet()
        val names = allFiles.map { it.name }.filter { it in want }.sorted()
        val missing = want.size - names.size
        Log.d(
            TAG,
            "Horiz-affected listFiles: dir=${experimentDir.absolutePath} " +
                "matched=${names.size}/${want.size} missing=$missing",
        )
        if (names.isEmpty()) {
            status = "Horiz-affected: 0 photos present (need ${want.size} in pump_photos)"
        } else {
            startPumpJob(
                names,
                "Horiz-affected (${names.size})…" +
                    if (missing > 0) " ($missing not on device)" else "",
            )
        }
    }

    val runL1SoDebug: () -> Unit = {
        val ok = ExperimentJobRunner.start(context.applicationContext, kind = "pump") { _, log, statusLine ->
            statusLine("L1 SO debug dump…")
            val label = BuildConfig.VERSION_NAME
            val res = PumpSoDebugDump.run(
                context = context.applicationContext,
                photoName = PumpSoDebugDump.DEFAULT_L1_NAME,
                label = label,
                onLog = log,
            )
            res.message
        }
        if (!ok) {
            status = "Another experiment is already running (${ExperimentJobRunner.state.value.kind})"
        } else {
            totalPhotos = 1
            currentPhotoName = PumpSoDebugDump.DEFAULT_L1_NAME
            status = "L1 SO debug dump…"
        }
    }

    LaunchedEffect(jobState) {
        if (jobState.kind != "pump" && jobState.kind != "") return@LaunchedEffect
        if (jobState.kind != "pump") return@LaunchedEffect
        when (jobState.status) {
            "running", "starting" -> {
                status = jobState.status
                currentPhotoName = jobState.current
                progress = jobState.progress
                if (jobState.detail.isNotEmpty()) detailLog = jobState.detail.takeLast(800)
            }
            "done" -> {
                progress = 1f
                status = "Complete! ${jobState.resultPath.ifEmpty { "Reports saved." }}"
            }
            "failed" -> status = "FAILED: ${jobState.error}"
        }
    }

    // Deep link: vehicleexpenses://experiment/pump?auto=first10 | auto=l1debug | auto=horiz | auto=selected
    LaunchedEffect(autoFirst10, autoL1Debug, autoHorizAffected, autoSelectedSample) {
        if (autoStarted || ExperimentJobRunner.isRunning()) return@LaunchedEffect
        when {
            autoL1Debug -> {
                autoStarted = true
                Log.i(TAG, "autoL1Debug starting PumpSoDebugDump")
                runL1SoDebug()
            }
            autoHorizAffected -> {
                autoStarted = true
                Log.i(TAG, "autoHorizAffected starting horiz-affected subset")
                runHorizAffected()
            }
            autoSelectedSample -> {
                autoStarted = true
                Log.i(TAG, "autoSelectedSample starting pump coverage subset")
                runSelectedSample()
            }
            autoFirst10 -> {
                autoStarted = true
                Log.i(TAG, "autoFirst10 starting pump First 10")
                runFirst10()
            }
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { u ->
            try {
                context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable URI permission", e)
            }
            scope.launch { status = "Extracting ZIP..."; val success = pExtractZipToPhotos(u, experimentDir, context); status = if (success) "ZIP extracted!" else "Failed to extract ZIP." }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "Jobs use ExperimentJobRunner + FGS — screen lock / leaving this page does not cancel.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (detailLog.isNotEmpty()) { Text(detailLog, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (isRunning) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = jobState.current.ifEmpty {
                        "${minOf(resultsList.size + 1, totalPhotos)} of $totalPhotos"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentPhotoName, style = MaterialTheme.typography.labelSmall)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { zipLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Extract Downloaded ZIP") }
        Button(onClick = {
            val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
            Log.d(TAG, "Run Test listFiles: dir=${experimentDir.absolutePath} count=${allFiles.size}")
            startPumpJob(null, "Run Test (${allFiles.size})…")
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
        Button(
            onClick = runFirst10,
            enabled = !isRunning && experimentDir.exists(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("First 10") }
        Button(
            onClick = runSelectedSample,
            enabled = !isRunning && experimentDir.exists(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Selected sample (${SelectedSamplePhotos.PUMP.size} pump)", fontWeight = FontWeight.Bold)
        }
        Text(
            "Selected sample = coverage subset (small→large text, GT-matched; 34 pump). " +
                "Pump photos only — no dash. Deep link: vehicleexpenses://experiment/pump?auto=selected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Button(
            onClick = runHorizAffected,
            enabled = !isRunning && experimentDir.exists(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Horiz-affected (${HORIZ_REACH_AFFECTED_FILENAMES.size})")
        }
        Text(
            "Horiz-affected = photos where exact min_v changed between horiz 0.5 and 1.0 " +
                "(phone 08-08). Columns: G-- / G4 / P4-jump / m65 / gx / xycut / rot / Prod. " +
                "Deep link: vehicleexpenses://experiment/pump?auto=horiz",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Button(
            onClick = runL1SoDebug,
            enabled = !isRunning && experimentDir.exists(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("L1 SO debug dump (buffers + heatmaps)")
        }
        Text(
            "L1 dump writes under files/pump_so_debug/ — pull after pin SO and after new SO. " +
                "Target: ${PumpSoDebugDump.DEFAULT_L1_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(resultsList) { index, res ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.width(8.dp))
                        Column { Text(res.photoName, style = MaterialTheme.typography.labelSmall); Text("Match: ${res.matchedVehicle}", color = MaterialTheme.colorScheme.primary); Text("Odo: ${res.odometer ?: "FAILED"}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

data class PumpBranch(
    val name: String,
    val images: MutableMap<String, String> = mutableMapOf(),
    val pathResults: MutableMap<String, PathResult> = mutableMapOf(),
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val subBranches: MutableMap<String, PumpBranch> = mutableMapOf(),
    var discoveryDetails: JSONObject? = null
) {
    fun getBranch(name: String): PumpBranch = subBranches.getOrPut(name) { PumpBranch(name) }

    fun serializeToJson(): JSONObject {
        val root = JSONObject()
        val imgObj = JSONObject(); images.forEach { (k, v) -> imgObj.put(k, v) }; root.put("images", imgObj)
        val resObj = JSONObject(); pathResults.forEach { (k, v) ->
            val p = JSONObject(); p.put("cost", v.cost); p.put("vol", v.vol); resObj.put(k, p)
        }; root.put("results", resObj)
        val metaObj = JSONObject(); metadata.forEach { (k, v) -> metaObj.put(k, v) }; root.put("metadata", metaObj)
        if (discoveryDetails != null) root.put("discovery_details", discoveryDetails)
        val subObj = JSONObject(); subBranches.forEach { (k, v) -> subObj.put(k, v.serializeToJson()) }; root.put("branches", subObj)
        return root
    }
}

/**
 * Full pump experiment. Returns the main JSON results file, or null if no photos.
 * Package-visible for [ExperimentPrecisionAbScreen].
 */
suspend fun runPumpExperiment(
    experimentDir: File,
    reportDir: File,
    context: Context,
    onLog: (String) -> Unit,
    subsetNames: List<String>?,
    onProgress: (PumpPhotoResultSummary, Float) -> Unit
): File? = withContext(Dispatchers.IO) {
    logHeapState(context, "runPumpExperiment:start")
    val allPhotos = experimentDir.listFiles { f ->
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
    }?.sortedBy { it.name } ?: return@withContext null
    Log.d(TAG, "runPumpExperiment listFiles: dir=${experimentDir.absolutePath} count=${allPhotos.size}")

    val photos = if (subsetNames != null) {
        allPhotos.filter { it.name in subsetNames }
    } else allPhotos

    val total = photos.size
    if (total == 0) return@withContext null
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    // Precision A/B for parallel phone+emu runs: true fp16 pack on device, true fp32 pack on emulator.
    // ABI-split APKs: x86_64 ships prod_u8fp32_u8; arm64 ships prod_u8fp16.
    val experimentProdDir = experimentPumpProductDir()
    onLog("loadProductionModels forceProdDir=$experimentProdDir (fp16 phone / fp32 emu)")
    Log.i(TAG, "experiment product pack: $experimentProdDir")
    NativePaddleEngine.loadProductionModels(context, forceProdDir = experimentProdDir)
    val paddleEngine = NativePaddleEngine(context)

    val jsonFile = File(reportDir, "pump_results_$timestamp.json")
    val deviceModel = Build.MODEL
    val jsonHeader = "{\n  \"timestamp\": \"$timestamp\",\n${ExperimentReportMeta.jsonFields()},\n  \"device\": \"$deviceModel\",\n  \"total_photos\": $total,\n  \"results\": [\n"
    val jsonFooter = "\n  ]\n}"
    var firstPhoto = true
    val jsonWriter = jsonFile.bufferedWriter()
    jsonWriter.write(jsonHeader)
    logHeapState(context, "after-json-header-write")
    Log.i("PUMP_JSON", "wrote header early, total_photos=$total")

    var partCount = 1
    val maxSizeBytes = 50 * 1024 * 1024 // 50MB HTML parts (JPEG previews only; JSON streamed to main file, frags deleted per row)
    var currentSize = 0
    val footer = "</table></body></html>"
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentRecSet1024x48 = BufferSet(1024, 48)  // per plan for D/E (and mirrors) OCR: larger for garbage tolerance + 4px buffer
    val experimentDetSet512x128 = BufferSet(512, 128)
    val masterBuffer = BufferSet(1, 1)

    val flows = listOf(
        "Set G-- (4 pass, none, calculated)",
        "Set G4 (v4 det, calculated 0.0-2.5)",
        "Set P4-jump (v4 + energy + jump, S OCR)",
        "Set P4-m65 (v4 + mean0.65 frozen + jump)",
        "Set P4-gx (v4 + gx0.55 frozen + jump)",
        "Set P4-xycut (v4 + xycut-gx frozen + jump)",
        "Set P4-rot-jump (v4 oriented + jump, S OCR)",
        "Set Prod-jump (product + energy + jump, S OCR)",
        "Set Prod-m65 (product + mean0.65 frozen + jump)",
        "Set Prod-rot (product oriented + jump, S OCR)",
    )
    val heatDumpRoot = File(reportDir, "pump_heats_$timestamp").also { it.mkdirs() }
    val energyTraceRoot = File(reportDir, "expand_energy_$timestamp").also { it.mkdirs() }

    fun pStartNewFile(): File {
        val f = File(reportDir, "pump_report_${timestamp}_part${partCount++}.html")
        f.writeText(pBuildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, deviceModel, flows))
        return f
    }

    var currentFile = pStartNewFile()

    photos.forEachIndexed { index, file ->
        try {
            onLog("Processing ${index + 1}/$total: ${file.name}")

            val (probedW, probedH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            if (probedW <= 0 || probedH <= 0) {
                android.util.Log.e("ExperimentPump", "Invalid probe ${probedW}x$probedH for ${file.name}; skipping photo")
                return@forEachIndexed
            }
            val imgW = probedW
            val imgH = probedH
            masterBuffer.resize(imgW, imgH)
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, masterBuffer.p)

            val root = PumpBranch("Root")
            val (beforeB64, tSnapOrig) = OcrUtils.takeSnapshot(masterBuffer.p, null, PUMP_SMALL_TARGET_W, 0, emptyList(), null, masterBuffer)
            root.images["before"] = beforeB64
            root.images["hist1"] = generateHistogramB64(masterBuffer.p.mat, 0.40f)

            var originalHistogram = JSONArray()


            // --- Pure helper functions (no closure on loop variables) ---

            fun stackVertically(b64List: List<String>): String {
                if (b64List.isEmpty()) return ""
                val bitmaps = mutableListOf<android.graphics.Bitmap>()
                try {
                    b64List.forEach { b64 ->
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) bitmaps.add(bmp)
                    }
                    if (bitmaps.isEmpty()) return ""
                    val w = bitmaps.maxOf { it.width }
                    val totalH = bitmaps.sumOf { it.height }
                    val stacked = android.graphics.Bitmap.createBitmap(w, totalH, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(stacked)
                    canvas.drawColor(android.graphics.Color.BLACK)
                    var y = 0
                    bitmaps.forEach { bmp ->
                        val scale = w.toFloat() / bmp.width.toFloat()
                        val nh = (bmp.height * scale).toInt()
                        val sb = android.graphics.Bitmap.createScaledBitmap(bmp, w, nh, true)
                        canvas.drawBitmap(sb, 0f, y.toFloat(), null)
                        y += nh
                        if (sb != bmp) sb.recycle()
                        bmp.recycle()
                    }
                    val res = OcrUtils.bitmapToBase64(stacked, 70)
                    stacked.recycle()
                    return res
                } catch (e: Exception) {
                    bitmaps.forEach { it.recycle() }
                    return ""
                }
            }

            fun doCrossScaleRedboxFilterPixel(redRects: MutableList<android.graphics.Rect>) {
                PumpCostVolUtils.doCrossScaleRedboxFilterPixel(redRects)
            }

            fun labelWithText(b64: String, text: String): String {
                return try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return b64
                    val mutable = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    bmp.recycle()
                    bmp = mutable
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.YELLOW
                        textSize = (bmp.height * 0.06f).coerceAtLeast(18f)
                        isAntiAlias = true
                        setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
                    }
                    canvas.drawText(text, 8f, paint.textSize + 4f, paint)
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                    val out = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                    bmp.recycle()
                    out
                } catch (e: Exception) {
                    b64
                }
            }

            fun doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
                PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
            }


            // Dynamic Flow Processing
            // Phase 2 dispatch: list-based (flowName, processor) pairs — not index-aligned. Only entries in `flows`
            // are run; the catalog below maps every defined processor by exact flow name.
            flows.forEach { flowName ->
                val branch = root.getBranch(flowName)
                val tFlowStart = System.currentTimeMillis()
                val tSetupStart = System.currentTimeMillis()
                val workspace = NativePaddleEngine.bufferSetA
                masterBuffer.p.mat.copyTo(workspace.p.mat)
                masterBuffer.p.uvMat.copyTo(workspace.p.uvMat)

                val discoveryDetails = mutableMapOf<String, MutableMap<Int, List<PumpHunk>>>().apply {
                    put("Paddle Raw", mutableMapOf())
                    put("Paddle Expanded", mutableMapOf())
                    put("Paddle Max Extent", mutableMapOf())
                    put("Paddle Native", mutableMapOf())
                }
                branch.metadata["t_setup_ms"] = (System.currentTimeMillis() - tSetupStart).toString()
                // t_setup_ms covers buffer copy + discoveryDetails map (common high-level phase for A/B/C gap analysis)

                // Setup logic and tilt variables are now completely moved into flow processors.
                // t_deskew_ms covers calculateAverageTextAngle + rotate + tilt metadata write (common high-level phase)

                // Hoisted decls (Phase 1 small step of approved refactor plan): declared before the local helper funs
                // (stackVertically, runPaddleDiscovery) that close over them (and before the inline discovery).
                // This resolves forward-ref compile issues for 'scales', the pd*Totals, mlBlocksRaw etc that the
                // helpers reference. (The processedScales for the inline remains at its site for now.)
                val scales = listOf(224, 608, 1024)
                val mlBlocksRaw = mutableListOf<PumpHunk>()
                val pdHunksRawTotal = mutableListOf<PumpHunk>()
                val pdHunksExpTotal = mutableListOf<PumpHunk>()
                val pdHunksMaxTotal = mutableListOf<PumpHunk>()
                val pdHunksNativeTotal = mutableListOf<PumpHunk>()
                val pdHunksDetectedTotal = mutableListOf<PumpHunk>()  // pre-redbox raw detected hunks (tFullB equiv); for Set C white 1px + blue/orange derivation from hunks (see alignment Set J tRawB vs tFullB)



                // fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan + PUMP_COST_VOLUME_CLASSIFIER_SPEC.md: clean text only; probs separate for decisions
                fun pumpOcrCleanAndProbs(debugText: String, perCharProbs: String): Pair<String, String> {
                    val cleanText = debugText
                    val probStr = if (perCharProbs.isNotEmpty()) perCharProbs else ""
                    return cleanText to probStr
                }

                // fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: leading/trailing '.' is noise; >=2 internal '.' is bad OCR
                fun cleanDecimal(s: String): String {
                    var t = s.trim()
                    while (t.startsWith(".")) t = t.substring(1)
                    while (t.endsWith(".")) t = t.substring(0, t.length - 1)
                    return t
                }

                fun hasBadInternalDecimals(s: String): Boolean = cleanDecimal(s).count { it == '.' } >= 2

                // fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: probs score correctness likelihood (not role)
                fun probCorrectness(p: String): Float {
                    if (p.isEmpty()) return 0.5f
                    val vals = p.split(",").mapNotNull { part ->
                        val colon = part.indexOf(':')
                        if (colon < 0) null else part.substring(colon + 1).trim().toFloatOrNull()
                    }
                    return if (vals.isEmpty()) 0.5f else vals.average().toFloat()
                }

                fun yOverlapHeight(a: android.graphics.Rect, b: android.graphics.Rect): Int {
                    val interTop = maxOf(a.top, b.top)
                    val interBottom = minOf(a.bottom, b.bottom)
                    return maxOf(0, interBottom - interTop)
                }

                // Significant overlap: Y-overlap height > 50% of preferred box height
                fun significantYOverlap(preferred: android.graphics.Rect, other: android.graphics.Rect): Boolean {
                    val overlap = yOverlapHeight(preferred, other)
                    val prefH = preferred.height().coerceAtLeast(1)
                    return overlap > prefH * 0.5f
                }

                // Role-based conditional decimal repair: only when clean value lacks a good decimal
                fun repairDecimalForRole(clean: String, role: String): String {
                    if ("." in clean) return clean
                    val dstr = clean.filter { it.isDigit() }
                    if (role == "cost" && dstr.length >= 3) {
                        val n = dstr.length
                        return dstr.substring(0, n - 2) + "." + dstr.substring(n - 2)
                    }
                    if (role == "vol" && dstr.length >= 4) {
                        val n = dstr.length
                        return dstr.substring(0, n - 3) + "." + dstr.substring(n - 3)
                    }
                    return clean
                }

                fun rectToJson(r: android.graphics.Rect): JSONObject =
                    JSONObject().put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom)

                fun redBoxOcrCandidateToJson(c: RedBoxOcrCandidate): JSONObject {
                    val j = JSONObject()
                        .put("label", c.label)
                        .put("asis", c.asis)
                        .put("digits", c.digits)
                        .put("asisProbs", c.asisProbs)
                        .put("digitsProbs", c.digitsProbs)
                    if (c.recB64.isNotEmpty()) j.put("recB64", c.recB64)
                    if (c.recW > 0) j.put("recW", c.recW)
                    if (c.recH > 0) j.put("recH", c.recH)
                    c.rect?.let { j.put("rect", rectToJson(it)) }
                    return j
                }

                // fix-remaining-report-issues-20260619-plan: cand.rect from ocr rect list (blue/orange/retracted), not pdHunksRawTotal reds
                // fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: probs stored separately from clean text
                fun buildRedBoxCandidates(
                    boxRects: List<android.graphics.Rect>,
                    asisList: List<String>,
                    digitsList: List<String>,
                    asisProbsList: List<String> = emptyList(),
                    digitsProbsList: List<String> = emptyList(),
                    recB64List: List<String> = emptyList(),
                    recWList: List<Int> = emptyList(),
                    recHList: List<Int> = emptyList(),
                ): List<RedBoxOcrCandidate> {
                    val n = minOf(boxRects.size, asisList.size, digitsList.size)
                    return (0 until n).map { i ->
                        RedBoxOcrCandidate(
                            "Red${i+1}",
                            asisList[i],
                            digitsList[i],
                            asisProbsList.getOrElse(i) { "" },
                            digitsProbsList.getOrElse(i) { "" },
                            boxRects[i],
                            recB64List.getOrElse(i) { "" },
                            recWList.getOrElse(i) { 0 },
                            recHList.getOrElse(i) { 0 },
                        )
                    }
                }

                suspend fun getFinal(
                    hunks: List<PumpHunk>,
                    engine: String,
                    tilt: Float,
                    pdRawForAnns: List<PumpHunk>,
                    ws: BufferSet,
                    recBuf: BufferSet,
                    paddleEng: NativePaddleEngine,
                    ctx: Context,
                    imgW: Int,
                    imgH: Int,
                    candidates: List<RedBoxOcrCandidate> = emptyList()
                ): PathResult {
                    // complete-real-4box-per-column-wiring plan + docs/specs/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md: per-column top-4 candidates drive cost/vol (~8 independent column/engine invocations); pathResults unchanged
                    if (candidates.isNotEmpty()) {
                        // fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: classify returns distinct clean digit strings (probs never in PathResult); crops from each cand's ocr rect
                        val cv = PumpCostVolUtils.classifyCostVolFromBoxOcr(candidates)
                        val costCrop = cv.costCand.rect?.let { r ->
                            OcrUtils.takeSnapshot(ws.p, r, PUMP_CROP_TARGET_W, PUMP_CROP_TARGET_H, emptyList(), null, ws).first
                        } ?: ""
                        val volCrop = cv.volCand.rect?.let { r ->
                            OcrUtils.takeSnapshot(ws.p, r, PUMP_CROP_TARGET_W, PUMP_CROP_TARGET_H, emptyList(), null, ws).first
                        } ?: ""
                        return PathResult(cv.cost, cv.vol, costCrop, volCrop)
                    }
                    // legacy fallback
                    val stitched = stitchHunksHorizontally(hunks)
                    val (top, bottom) = groupLanesByVerticalGap(stitched)
                    val pair = findBestLanePair(top, bottom) ?: return PathResult("N/A", "N/A", "", "")
                    val expT = expandHunkContext(pair.first, imgW, imgH); val expB = expandHunkContext(pair.second, imgW, imgH)
                    val res = performHunkRecognition(listOf(expT, expB), ws, recBuf, engine, paddleEng, ctx, tilt)

                    suspend fun takeCrop(exp: PumpHunk, orig: PumpHunk): String {
                        // A final crop path: direct integer Rect from (now integer-valued) exp.rect (from expand integer); no float in rect construction for takeSnapshot; anns also from A integer pdRaw
                        val el = exp.rect.left.toInt(); val et = exp.rect.top.toInt(); val er = exp.rect.right.toInt(); val eb = exp.rect.bottom.toInt()
                        val rect = android.graphics.Rect(el, et, er, eb)
                        val anns = mutableListOf<SnapshotAnnotation>()
                        if (engine == "Paddle") {
                            // RED: Raw detections only (blue/orange removed to focus on red boxes for debugging)
                            pdRawForAnns.forEach { h ->
                                anns.add(SnapshotAnnotation(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt(), Shape.RECTANGLE, Color.RED, 2))
                            }
                            // BLUE and ORANGE temporarily disabled
                            // pdHunksExpTotal.forEach { ... BLUE }
                            // ... ORANGE for the specific
                        }
                        return OcrUtils.takeSnapshot(ws.p, rect, PUMP_CROP_TARGET_W, PUMP_CROP_TARGET_H, anns, null, ws).first
                    }
                    val cropT = takeCrop(expT, pair.first); val cropB = takeCrop(expB, pair.second)
                    return PathResult(res[0].text, res[1].text, cropT, cropB)
                }



                // Phase 2 of approved refactor plan: the array of processor functions (one per flow, in same order as
                // the flows list) that we iterate over (forEachIndexed or zip). Each is a self-contained lambda whose
                // body is the linear list of steps for that path (no if(flowName) inside). Common setup (ws copy,
                // discoveryDetails map) happens at the dispatch site; processors receive ws/br/det/w/h and populate
                // only their branch (images, pathResults, metadata["tilt"]). Old tangled forEach body remains
                // temporarily (will be removed as logic is moved into the processors in subsequent phases).
                // Set C valley (bin-test) will be fully implemented in its processor (Phase 3).
                // Red-box-hist polarity fix for Set C/E (after tilt/rotate, before processors/body discovery; uses runPaddleDiscovery probe which is now defined).
                // Looks at 64-bin hist *only inside the initial red boxes* (text regions) on the (deskewed, same-hist-as-B stretched) mat to decide dark text on light bg vs light on dark.
                // If dark text, inverts the mat (bitwise_not) so subsequent detection/rec + PD snapshot for C/E always see light text on dark bg.
                // E mirrors C per plan (valley + per-red on the pruned 6 + blue via E).
                /* pre-proc C/E polarity block retired (Phase 4 tiny step 2: removed per granular retirement; pre-proc C/E no longer drives; dispatch + procs sole) */

                // Phase 0 hoist (per granular plan + failure lessons): timing vars referenced in remnant/procs logic hoisted to scope before proc lambdas (with initial) so visible inside proc bodies + after retirement of remnant decl sites. (tDiscoveryWrapperStart was declared inside else after proc defs.)
                var tDiscoveryWrapperStart = 0L
                var tProbeStart = 0L
                var tPolDecStart = 0L
                var tG0 = 0L
                var tG1 = 0L
                // (more t* for C/E valley/blue etc hoisted in later substeps or covered by early tFlowStart; assignments below use reassign or original inner vals where block scoped)

                // Phase 0 hoist of getAnns (small local used by A viz + inside doBOrD*/doCOrE* helpers): moved early before proc defs so visible to proc lambdas (when full logic incl calls is duplicated into them) + do* (per plan "hoist ... getAnns, the doBOrD*/doCOrE* defs if referenced from procs"; do* large bodies left in place, copies included at dupe time per plan wording).
                fun getAnns(list: List<PumpHunk>, color: Int, width: Int) = list.map { h ->
                    SnapshotAnnotation(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt(), Shape.RECTANGLE, color, width)
                }

                fun createBlueAndOrangeHunksFromReds(
                    reds: List<PumpHunk>,
                    imgW: Int,
                    imgH: Int,
                    vertFactors: List<Float> = listOf(0.2f),
                    horizFactor: Float = 0.5f
                ): Pair<List<PumpHunk>, List<PumpHunk>> {
                    val blues = mutableListOf<PumpHunk>()
                    val oranges = mutableListOf<PumpHunk>()
                    reds.forEach { h ->
                        val r = android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                        val hgt = r.height()
                        vertFactors.forEach { v ->
                            var nt = (r.top - (v * hgt)).toInt().coerceIn(0, imgH - 1)
                            var nb = (r.bottom + (v * hgt)).toInt().coerceIn(nt + 1, imgH)
                            val newH = nb - nt
                            val horiz = (horizFactor * newH).toInt()
                            var nl = (r.left - horiz).toInt().coerceIn(0, imgW - 1)
                            var nr = (r.right + horiz).toInt().coerceIn(nl + 1, imgW)
                            val bRect = android.graphics.Rect(nl, nt, nr, nb)
                            val oExt = (0.1 * newH).toInt()
                            val ol = (nl - oExt).coerceIn(0, imgW - 1)
                            val orr = (nr + oExt).coerceIn(0, imgW)
                            val oRect = android.graphics.Rect(ol, nt, orr, nb)
                            blues.add(PumpHunk("", RectF(bRect.left.toFloat(), bRect.top.toFloat(), bRect.right.toFloat(), bRect.bottom.toFloat())))
                            oranges.add(PumpHunk("", RectF(oRect.left.toFloat(), oRect.top.toFloat(), oRect.right.toFloat(), oRect.bottom.toFloat())))
                        }
                    }
                    return blues to oranges
                }

                // Hoisted data-only capture for per-red redbox histograms (stat JSON with index/h/w/area/histBins) for *all 7 sets* (A/B/C/D/E/F/G).
                // Called after the (now top-6) prune in every proc. Reuses the existing createCrop + direct calcHist + stat pattern from C/E visuals (no visuals/longLived here; data only for JSON/metadata "redboxData" + "n_per_red_hists").
                // C/E continue to use their specific visual capture (redboxRectC_*/redboxHistC_* + longLived) + redboxDataC; this adds the common "redboxData" for all.
                fun captureRedboxData(reds: List<PumpHunk>, workspace: BufferSet, branch: PumpBranch) {
                    val redboxData = JSONArray()
                    reds.forEachIndexed { i, hunk ->
                        val rw = (hunk.rect.right - hunk.rect.left).toInt()
                        val rh = (hunk.rect.bottom - hunk.rect.top).toInt()
                        val rarea = rw * rh
                        val cropId = workspace.createCrop(hunk.rect.left.toInt(), hunk.rect.top.toInt(), (hunk.rect.right - hunk.rect.left).toInt(), (hunk.rect.bottom - hunk.rect.top).toInt())
                        val hmat = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.c[cropId].mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hmat, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                        val rbins = FloatArray(64); hmat.get(0, 0, rbins); hmat.release()
                        val stat = JSONObject().put("index", i).put("h", rh).put("w", rw).put("area", rarea)
                        val binsArr = JSONArray(); rbins.forEach { binsArr.put(it.toDouble()) }; stat.put("histBins", binsArr)
                        redboxData.put(stat)
                        workspace.c[cropId].release()
                    }
                    branch.metadata["redboxData"] = redboxData.toString()
                    branch.metadata["n_per_red_hists"] = reds.size.toString()
                    // Combined union histogram over all red rects (OR mask, no double-counting overlaps).
                    val unionMask = org.opencv.core.Mat.zeros(workspace.p.mat.rows(), workspace.p.mat.cols(), org.opencv.core.CvType.CV_8UC1)
                    reds.forEach { hunk ->
                        val pt1 = org.opencv.core.Point(hunk.rect.left.toDouble(), hunk.rect.top.toDouble())
                        val pt2 = org.opencv.core.Point(hunk.rect.right.toDouble(), hunk.rect.bottom.toDouble())
                        org.opencv.imgproc.Imgproc.rectangle(unionMask, pt1, pt2, org.opencv.core.Scalar(255.0), -1)
                    }
                    val combinedHist = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.p.mat), org.opencv.core.MatOfInt(0), unionMask, combinedHist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                    val combinedBins = FloatArray(64); combinedHist.get(0, 0, combinedBins); combinedHist.release(); unionMask.release()
                    val combinedArr = JSONArray(); combinedBins.forEach { combinedArr.put(it.toDouble()) }
                    branch.metadata["combinedRedboxHistBins"] = combinedArr.toString()
                }

                fun buildCostVolDecisionDataJson(
                    reds: List<android.graphics.Rect>,
                    ocrSourceRects: List<android.graphics.Rect>,
                    candidates: List<RedBoxOcrCandidate>,
                    costCand: RedBoxOcrCandidate,
                    volCand: RedBoxOcrCandidate,
                    finalCost: String,
                    finalVol: String,
                    assembly: Map<String, Any?> = emptyMap(),
                    oranges: List<android.graphics.Rect> = emptyList(),
                    ocrQuads: List<ContentExpandUtils.OrientedQuad> = emptyList(),
                    seedQuads: List<ContentExpandUtils.OrientedQuad> = emptyList(),
                    scaleVariants: JSONArray = JSONArray(),
                ): String {
                    val redsArr = JSONArray()
                    reds.forEach { redsArr.put(rectToJson(it)) }
                    val ocrArr = JSONArray()
                    ocrSourceRects.forEach { ocrArr.put(rectToJson(it)) }
                    val candsArr = JSONArray()
                    candidates.forEach { candsArr.put(redBoxOcrCandidateToJson(it)) }
                    val chosen = JSONObject()
                        .put("cost", redBoxOcrCandidateToJson(costCand))
                        .put("vol", redBoxOcrCandidateToJson(volCand))
                    val finalObj = JSONObject()
                        .put("cost", finalCost)
                        .put("vol", finalVol)
                    val assemblyObj = JSONObject()
                    assembly.forEach { (k, v) ->
                        when (v) {
                            is List<*> -> {
                                val arr = JSONArray()
                                v.forEach { item -> arr.put(item) }
                                assemblyObj.put(k, arr)
                            }
                            else -> assemblyObj.put(k, v)
                        }
                    }
                    val orangesArr = JSONArray()
                    oranges.forEach { orangesArr.put(rectToJson(it)) }
                    fun quadsToJson(qs: List<ContentExpandUtils.OrientedQuad>): JSONArray {
                        val arr = JSONArray()
                        qs.forEach { q ->
                            val pts = JSONArray()
                            q.pts.take(8).forEach { pts.put(it.toDouble()) }
                            arr.put(
                                JSONObject()
                                    .put("pts", pts)
                                    .put("angleDeg", pumpQuadLongAngleDeg(q).toDouble()),
                            )
                        }
                        return arr
                    }
                    return JSONObject()
                        .put("reds", redsArr)
                        .put("ocrSourceRects", ocrArr)
                        .put("ocrSourceQuads", quadsToJson(ocrQuads))
                        .put("seedQuads", quadsToJson(seedQuads))
                        .put("candidates", candsArr)
                        .put("chosen", chosen)
                        .put("final", finalObj)
                        .put("assembly", assemblyObj)
                        .put("oranges", orangesArr)
                        .put("scaleVariants", scaleVariants)
                        .toString()
                }

                // Height-only OCR variants after one detect+expand (`scaleVariants` / rec buffers).
                // Live: S=1.0 only. On v0.98-212 167×2 non-cap successes, S>1 lost more
                // fields than it gained. Put values back here to re-enable a sweep.
                val pJumpOcrScales = listOf(1.0f)
                // Walk budget per side / seedH. 0.4 so energy cannot glue the other line;
                // G-on-cap (0/0.05/0.15) takes over if it still hits this leash.
                val alignedExpandMaxFrac = 0.4f
                val rotExpandMaxFrac = 0.4f
                /** Parked. Was 0..0.50 / 0.05 on every seed. Rot now G-on-cap only. */
                val rotVertSweep: List<Float> = emptyList()

                fun ocrScaleVariantJson(
                    s: Float,
                    rects: List<android.graphics.Rect>,
                    quads: List<ContentExpandUtils.OrientedQuad>,
                    cands: List<RedBoxOcrCandidate>,
                    cv: CostVolClassifyResult,
                    kind: String = "s",
                    v: Float? = null,
                    hitCaps: List<Boolean> = emptyList(),
                ): JSONObject {
                    val rectArr = JSONArray()
                    rects.forEach { rectArr.put(rectToJson(it)) }
                    val candArr = JSONArray()
                    cands.forEach { candArr.put(redBoxOcrCandidateToJson(it)) }
                    val qArr = JSONArray()
                    quads.forEach { q ->
                        val pts = JSONArray()
                        q.pts.take(8).forEach { pts.put(it.toDouble()) }
                        qArr.put(
                            JSONObject()
                                .put("pts", pts)
                                .put("angleDeg", pumpQuadLongAngleDeg(q).toDouble()),
                        )
                    }
                    val j = JSONObject()
                        .put("s", s.toDouble())
                        .put("kind", kind)
                        .put("ocrSourceRects", rectArr)
                        .put("ocrSourceQuads", qArr)
                        .put("candidates", candArr)
                        .put(
                            "chosen",
                            JSONObject()
                                .put("cost", redBoxOcrCandidateToJson(cv.costCand))
                                .put("vol", redBoxOcrCandidateToJson(cv.volCand)),
                        )
                        .put(
                            "final",
                            JSONObject().put("cost", cv.cost).put("vol", cv.vol),
                        )
                    if (v != null) j.put("v", v.toDouble())
                    if (hitCaps.isNotEmpty()) {
                        val arr = JSONArray()
                        hitCaps.forEach { arr.put(it) }
                        j.put("hitVertCap", arr)
                    }
                    return j
                }

                // Phase 0 other visibility: hoist processedScales decl (the remnant inline one) early before procs so visible inside proc bodies after dupe + for the reinit in remnant discovery (per "any other visibility fixes for vars/lists (pdHunks*Total, mlBlocksRaw, scales, processedScales, experimentRec* buffers, etc.)").
                var processedScales = mutableSetOf<Int>()

                // Per-column top-4 box OCR (PUMP_COST_VOLUME_CLASSIFIER_SPEC.md): as-is (golden Y-band only) + digits on pixel rects; fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: clean text only; probs returned separately
                suspend fun ocrPumpRectsAsisAndDigits(rects: List<android.graphics.Rect>): PumpRectOcrLists {
                    // Delegate to shared util (source-border rec feed, same as Quick Fill / batch).
                    return PumpCostVolUtils.ocrPumpRectsAsisAndDigits(
                        workspace, paddleEngine, experimentRecSet1024x48, rects, imgW, imgH,
                    )
                }

                suspend fun doBOrDRedOnlyImage() {
                    // Red-only image for Set B/D (per approved plan): clean view of post-filter reds only (no blue, no orange) so user can inspect redbox merging state without other annotations overlaid. Full image remains exactly "as is happening now". D mirrors B.
                    val redAnnsOnly = getAnns(pdHunksRawTotal, Color.RED, 2)
                    val redOnlyB64 = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, redAnnsOnly, null, workspace).first
                    branch.images["PD_red_only"] = redOnlyB64
                }

                // Explicit vert-factor pass lists for retained Set I stages (see flows comment)
                val iGVert = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.1f, 1.5f)
                val iDVert = listOf(0.1f, 0.2f)
                val iEVert = listOf(0.3f, 0.7f)

                fun makeGProc(
                    gVertFactors: List<Float>,
                    assemblyNote: String,
                    boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats: Boolean = false,
                    /** Side pad as fraction of expanded blue height (each side). G-- = 0.5; dense/K trial = 1.0. */
                    horizFactor: Float = SET_G_HORIZ_FACTOR,
                    /** Heat thr: HEAT_THR_U8_GE1 (u≥1) or HEAT_THR_U8_GE2 (u≥2). */
                    hmThresh: Float = HEAT_THR_U8_GE1,
                    /** 3x3 heat-mask dilate passes before CC (L=2, M=5). Merges nearby heat. */
                    maskDilatePasses: Int = 0,
                    /** Null = product det. G4 uses PP-OCRv4_mobile_det. */
                    expDetAsset: String? = null,
                ): suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p, longEdgeTarget = 256)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = if (boxMode == NativeImageUtils.HEATMAP_BOX_AABB) "aabb" else "minAreaRect"
                    branch.metadata["hm_thresh"] = hmThresh.toString()
                    branch.metadata["hm_thresh_note"] =
                        if (hmThresh <= 0f) "u8>=1" else if (kotlin.math.abs(hmThresh - HEAT_THR_U8_GE2) < 1e-6f) "u8>=2" else "custom"
                    branch.metadata["mask_dilate_passes"] = maskDilatePasses.toString()
                    branch.metadata["heatmap_cell_px"] = NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    if (expDetAsset != null) {
                        NativePaddleEngine.loadExperimentDetTiers(context, expDetAsset)
                        branch.metadata["det_model"] = expDetAsset
                    } else {
                        branch.metadata["det_model"] = "product_det"
                    }
                    try {
                    val photoHeatDir = if (dumpHeats) {
                        File(heatDumpRoot, file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")).also { it.mkdirs() }
                    } else null
                    if (photoHeatDir != null) {
                        branch.metadata["heat_dump_dir"] = photoHeatDir.absolutePath
                    }
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale ->
                    val srcW = workspace.p.width
                    val srcH = workspace.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()
                    val targetLongEdge = max(targetW, targetH)

                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val heatFile = photoHeatDir?.let { File(it, "scale${scale}_heatmap.u8z") }
                    val paddleResults = runDiscoveryPaddle(
                        workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata,
                        boxMode = boxMode,
                        heatDumpU8z = heatFile,
                        hmThresh = hmThresh,
                        maskDilatePasses = maskDilatePasses,
                    )
                    val detected = paddleResults[0]
                    val raw = paddleResults[1]
                    val exp = paddleResults[2]
                    val maxExt = paddleResults[3]
                    val native = paddleResults[4]

                    pdHunksDetectedTotal.addAll(detected)
                    pdHunksRawTotal.addAll(raw)
                    pdHunksExpTotal.addAll(exp)
                    pdHunksMaxTotal.addAll(maxExt)
                    pdHunksNativeTotal.addAll(native)

                    workspace.c[innerId].release()
                    workspace.c[outerId].release()

                    discoveryDetails["Paddle Raw"]!![scale] = raw
                    discoveryDetails["Paddle Expanded"]!![scale] = exp
                    discoveryDetails["Paddle Max Extent"]!![scale] = maxExt
                    discoveryDetails["Paddle Native"]!![scale] = native
                }
                branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)

                // doCrossScaleRedboxFilter hoisted earlier (before flowProcessors list) so it is visible inside the
                // C processor lambda (the per-path valley for Set C calls the global cross-scale nested removal
                // after each version's runPaddleDiscovery).

                // Global cross-scale removal of entirely contained raw red boxes (in final image pixel space).
                // The +1 expand + inset de-nest inside runDiscoveryPaddle (per scale) is the port from alignment Set J
                // and cleans nesting *within* one pyramid level's detection. Because pump discovery is multi-scale
                // (prepareScale + detect at 224/608/1024/2560), a final pass on the union (after ICRS mapping to common
                // full-res pixels) is required to remove any raw red that is entirely contained in another across scales.
                // This ensures the RED raw boxes shown in the PD column images (and overlaid in the cost/vol crops via
                // takeCrop) have no entirely-contained nested boxes, matching the intent.
                // (Now via shared helper; body unchanged.)
                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                // Propagate the dedup: filter exp and max totals too, so that blue/orange boxes
                // (derived from expansions of the raw reds) are not created from redundants that
                // were filtered out of the raw list. Filtered reds must not "exist" for downstream
                // blue derivation.
                doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
                doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
                branch.metadata["t_filter_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()  // reuse start as approx for filter delta; finer per-phase in later granular
                branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                // t_filter_ms + n_reds_after_filter (common; for C also explicit redBoxes filter in blue path)

                // Direct pixel from ingest (runDiscoveryPaddle explicit upscale); no roundtrip. Pixel filter + prune6 on rects; direct rebuild. Early probe sees full; post-prune 6 for all sets (C/E display only).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                pruneRedPixelsTopN(redPixelList, context, imgH)
                // Rebuild pdHunksRawTotal from the final <=4 pixel rects (full img ICRS only for kept)
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                // Propagate prune to exp/max (blue/orange sources in B/C paths)
                val expPixel = pdHunksExpTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                pruneRedPixelsTopN(expPixel, context, imgH)
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                pruneRedPixelsTopN(maxPixel, context, imgH)
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for G (redboxData + n_per_red_hists)

                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 6 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                // Set G-family calculated: single blue/orange create from post-prune kept reds + dual OCR + one store
                val (customBlueG, customOrangeG) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, gVertFactors, horizFactor)
                val customBluePixelG = customBlueG.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val orangePixelG = customOrangeG.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val tOcr0 = System.currentTimeMillis()
                val ocrG = ocrPumpRectsAsisAndDigits(customBluePixelG)
                val tOcr = (System.currentTimeMillis() - tOcr0).toString()
                branch.metadata["t_ocr_ms"] = tOcr
                branch.metadata["n_ocr_energy"] = customBluePixelG.size.toString()
                branch.metadata["n_ocr_g"] = "0"
                branch.metadata["t_ocr_energy_ms"] = tOcr
                branch.metadata["t_ocr_g_ms"] = "0"
                branch.metadata["t_expand_ms"] = "0"
                val gCands = buildRedBoxCandidates(
                    customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs, ocrG.digitsProbs, ocrG.recB64,
                    recWList = ocrG.recW, recHList = ocrG.recH,
                )
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, gCands)
                val redPixelG = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val cvG = PumpCostVolUtils.classifyCostVolFromBoxOcr(gCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelG,
                    ocrSourceRects = customBluePixelG,
                    candidates = gCands,
                    costCand = cvG.costCand,
                    volCand = cvG.volCand,
                    finalCost = cvG.cost,
                    finalVol = cvG.vol,
                    assembly = mapOf(
                        "method" to "calculated",
                        "vertFactors" to gVertFactors,
                        "heightMultiples" to gVertFactors.map { 1f + 2f * it },
                        "horizFactor" to horizFactor,
                        "orangeSideExt" to 0.1,
                        "heatmapBoxMode" to if (boxMode == NativeImageUtils.HEATMAP_BOX_AABB) "aabb" else "minAreaRect",
                        "hmThresh" to hmThresh,
                        "hmThreshNote" to (if (hmThresh <= 0f) "u8>=1" else if (kotlin.math.abs(hmThresh - HEAT_THR_U8_GE2) < 1e-6f) "u8>=2" else "custom"),
                        "maskDilatePasses" to maskDilatePasses,
                        "heatmapCellPx" to NativeImageUtils.PADDLE_DET_HEAT_CELL_PX,
                        "note" to assemblyNote
                    ),
                    oranges = orangePixelG
                )
                doBOrDRedOnlyImage()
                val aPdG = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(customBlueG, Color.BLUE, 4) + getAnns(customOrangeG, Color.rgb(255, 165, 0), 2)
                val baseB64G = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdG, null, workspace).first
                branch.images["PD"] = baseB64G
                    } finally {
                        if (expDetAsset != null) {
                            try {
                                NativePaddleEngine.restoreProductionDetTiers(context)
                            } catch (t: Throwable) {
                                Log.e(TAG, "restoreProductionDetTiers failed", t)
                            }
                        }
                    }
            }
                val procGMinusMinus = makeGProc(
                    SET_G_MINUS_MINUS_VERT_FACTORS,
                    "G-- shared k=4 [0.1,0.3,0.4,1.1]; Quick Fill ref; u8≥1; horiz=0.5; dumps heats",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = true,
                    horizFactor = SET_G_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE1,
                )
                val procG4 = makeGProc(
                    SET_G4_VERT_FACTORS,
                    "G4: v4 mobile det + G-style calculated verts 0.0/0.1/0.3 (best triple vs 0–0.3 union on v0.98-212 167×2); horiz=0.5; deskew; u8≥1",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = false,
                    horizFactor = SET_G_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE1,
                    expDetAsset = "PP-OCRv4_mobile_det",
                )
                // Horiz-reach A/B: same discovery as G-- (verts, thr, box mode); only horizFactor changes.
                val procHorizByFactor: Map<Float, suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit> =
                    SET_HORIZ_REACH_FACTORS.associateWith { h ->
                        makeGProc(
                            SET_G_MINUS_MINUS_VERT_FACTORS,
                            "horiz A/B: fixed G-- verts [0.1,0.3,0.4,1.1]; thr u8≥1; horiz=$h",
                            boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                            dumpHeats = false,
                            horizFactor = h,
                            hmThresh = HEAT_THR_U8_GE1,
                        )
                    }
                // Parked (re-enable via docs/PUMP_EXPERIMENT_FLOWS.md): G-dense, K thr, L/M dilate, N–Q content.
                @Suppress("unused")
                val procGDense = makeGProc(
                    SET_G_DENSE_VERT_FACTORS,
                    "G-dense v fine 0…0.8 + coarse 1.0…2.5; minAreaRect; u8≥1; horiz=1.0",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = false,
                    horizFactor = SET_G_DENSE_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE1,
                )
                @Suppress("unused")
                val procK = makeGProc(
                    SET_G_DENSE_VERT_FACTORS,
                    "K: same as G-dense but heat thr u8≥2 (minAreaRect, same verts/horiz)",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = false,
                    horizFactor = SET_G_DENSE_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE2,
                )
                val procL = makeGProc(
                    SET_G_MINUS_MINUS_VERT_FACTORS,
                    "L: heat mask dilate 2 passes then minAreaRect; no rect+1; G-- verts; horiz=0.5",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = false,
                    horizFactor = SET_G_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE1,
                    maskDilatePasses = 2,
                )
                val procM = makeGProc(
                    SET_G_MINUS_MINUS_VERT_FACTORS,
                    "M: heat mask dilate 4 passes then minAreaRect; no rect+1; G-- verts; horiz=0.5 (was 5; 5 merged cost+vol too often)",
                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats = false,
                    horizFactor = SET_G_HORIZ_FACTOR,
                    hmThresh = HEAT_THR_U8_GE1,
                    maskDilatePasses = 4,
                )

                /** OCR oriented quads: warp each to horizontal 48px strip then recognize. */
                suspend fun ocrPumpOrientedQuads(
                    quads: List<ContentExpandUtils.OrientedQuad>,
                    gray: org.opencv.core.Mat,
                    imgW: Int,
                    imgH: Int,
                ): PumpRectOcrLists {
                    // Inflate oriented quads in source so warp margin is real pixels, then
                    // place strip at (0,0) without black 4px createCrop inset (same as odo Raw).
                    data class OcrOne(
                        val asis: Pair<String, String>,
                        val digits: Pair<String, String>,
                        val snap: String,
                        val recW: Int,
                        val recH: Int,
                    )
                    suspend fun ocrOne(q: ContentExpandUtils.OrientedQuad): OcrOne {
                        val aabb = q.toAabb()
                        if (aabb.width() < 2 || aabb.height() < 2) {
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val rSc = 48f / aabb.height().coerceAtLeast(1)
                        val pad = kotlin.math.ceil(4.0 / rSc.toDouble()).toInt().coerceAtLeast(1)
                        val qInfl = q.inflate(pad, imgW, imgH)
                        experimentRecSet1024x48.p.clear()
                        val dest = org.opencv.core.Mat()
                        val ok = ContentExpandUtils.warpQuadToHorizontalStrip(
                            gray, qInfl, dest, targetH = 48, maxW = 320,
                        )
                        if (!ok || dest.empty()) {
                            dest.release()
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val fed = RecBufferFeed.feedPreparedStripNoBlackPad(dest, experimentRecSet1024x48)
                        dest.release()
                        val snap = PumpCostVolUtils.snapRecCrop(
                            experimentRecSet1024x48, fed.recCropId, fed.targetW, fed.targetH,
                        )
                        val asisRes = paddleEngine.recognize(experimentRecSet1024x48.c[fed.recCropId])
                        val digitsRes = paddleEngine.recognizeNumericDecimal(
                            experimentRecSet1024x48.c[fed.recCropId],
                        )
                        experimentRecSet1024x48.c[fed.recCropId].release()
                        val asis = pumpOcrCleanAndProbs(asisRes.debugText, asisRes.perCharProbs)
                        val digs = pumpOcrCleanAndProbs(digitsRes.debugText, digitsRes.perCharProbs)
                        return OcrOne(asis, digs, snap, fed.targetW, fed.targetH)
                    }
                    val asis = ArrayList<String>(quads.size)
                    val digits = ArrayList<String>(quads.size)
                    val asisProbs = ArrayList<String>(quads.size)
                    val digitsProbs = ArrayList<String>(quads.size)
                    val recB64 = ArrayList<String>(quads.size)
                    val recW = ArrayList<Int>(quads.size)
                    val recH = ArrayList<Int>(quads.size)
                    for (q in quads) {
                        val one = ocrOne(q)
                        asis.add(one.asis.first); asisProbs.add(one.asis.second)
                        digits.add(one.digits.first); digitsProbs.add(one.digits.second)
                        recB64.add(one.snap)
                        recW.add(one.recW)
                        recH.add(one.recH)
                    }
                    return PumpRectOcrLists(
                        asis = asis,
                        digits = digits,
                        asisProbs = asisProbs,
                        digitsProbs = digitsProbs,
                        recB64 = recB64,
                        recW = recW,
                        recH = recH,
                    )
                }

                /**
                 * Rot column: independent of AABB discovery. One minAreaRect detect per
                 * scale, keep 8-corners, oriented prune (sides move along their normals),
                 * expandOriented, OCR each ocrScales. Does **not** call [runDiscoveryPaddle]
                 * or convert a merge to an AABB quad.
                 */
                suspend fun runIndependentOrientedColumn(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    imgW: Int,
                    imgH: Int,
                    enableJump: Boolean,
                    jumpFrac: Float,
                    ocrScales: List<Float>,
                    tilt: Float,
                    assemblyNote: String,
                    expDetAsset: String?,
                    orientedQuadsOut: MutableList<ContentExpandUtils.OrientedQuad>,
                    maxFrac: Float = 1.0f,
                    fallbackVerts: List<Float> = emptyList(),
                    vertSweep: List<Float> = emptyList(),
                    energyRatio: Float = 0.45f,
                    freezeHorzDuringVert: Boolean = false,
                    vertPadFrac: Float = 0.0f,
                    energyTraceOut: File? = null,
                ) {
                    fun hunkFromAabb(r: android.graphics.Rect): PumpHunk =
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )

                    val collected = ArrayList<ContentExpandUtils.OrientedQuad>()
                    scales.forEach { scale ->
                        val srcW = workspace.p.width
                        val srcH = workspace.p.height
                        val currentLongEdge = max(srcW, srcH)
                        val scaleFactor =
                            if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                        val targetW = (srcW * scaleFactor).toInt().coerceAtLeast(2)
                        val targetH = (srcH * scaleFactor).toInt().coerceAtLeast(2)
                        val (outerId, innerId) = prepareScale(workspace, scale)
                        val outer = workspace.c[outerId]
                        val masterW = outer.width.coerceAtLeast(1)
                        val masterH = outer.height.coerceAtLeast(1)
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        Log.i(
                            TAG,
                            "pump_rot_detect scale=$scale content=${targetW}x$targetH " +
                                "slice=${masterW}x$masterH",
                        )
                        val detRes = paddleEngine.detect(
                            outer,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val q = FloatArray(8)
                            for (i in 0 until 4) {
                                q[i * 2] = p[i * 2] * fullW / masterW
                                q[i * 2 + 1] = p[i * 2 + 1] * fullH / masterH
                            }
                            val oq = ContentExpandUtils.orientedFromPoints8(q)
                            collected.add(oq)
                            scaleHunks.add(hunkFromAabb(oq.toAabb()))
                        }
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = scaleHunks
                        workspace.c[innerId].release()
                        workspace.c[outerId].release()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)

                    val maxN = PumpOcrSettings.maxRedBoxes(context)
                    // Oriented merge: grow the keeper's sides along their normals so a
                    // smaller poke-out is absorbed without converting the tilt to an AABB.
                    val kept = ContentExpandUtils.pruneOrientedQuads(collected, maxN, imgH)
                    val keptAabb = kept.map { it.toAabb() }
                    orientedQuadsOut.clear()
                    orientedQuadsOut.addAll(kept)

                    val seedHunks = keptAabb.map { hunkFromAabb(it) }
                    pdHunksRawTotal.clear()
                    pdHunksRawTotal.addAll(seedHunks)
                    pdHunksDetectedTotal.clear()
                    pdHunksDetectedTotal.addAll(seedHunks)
                    captureRedboxData(pdHunksRawTotal, workspace, branch)

                    val gray = workspace.p.mat
                    val expandOpts = ContentExpandUtils.ExpandOptions(
                        maxFrac = maxFrac,
                        enableJump = enableJump,
                        jumpFrac = jumpFrac,
                        energyRatio = energyRatio,
                        freezeHorzDuringVert = freezeHorzDuringVert,
                        vertPadFrac = vertPadFrac,
                        recordVertEnergy = energyTraceOut != null,
                    )
                    val seedQuads = kept.toList()
                    val tExpand0 = System.currentTimeMillis()
                    val expDiag = seedQuads.map { seed ->
                        ContentExpandUtils.expandOrientedDiagnose(gray, seed, expandOpts)
                    }
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    val expandedQuads = expDiag.map { it.quad }
                    val hitCaps = expDiag.map { it.hitVertCap }
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedQuads.size.toString()
                    if (energyTraceOut != null) {
                        try {
                            writeExpandEnergyTrace(
                                energyTraceOut, file.name, maxFrac,
                                expDiag.mapNotNull { it.energyTrace },
                                column = "P4-rot",
                            )
                            branch.metadata["content_expand_energy_trace"] = energyTraceOut.name
                        } catch (t: Throwable) {
                            Log.e(TAG, "rot energy trace write failed for ${file.name}", t)
                        }
                    }
                    val seedAngs = seedQuads.map { pumpQuadLongAngleDeg(it) }
                    val expAngs = expandedQuads.map { pumpQuadLongAngleDeg(it) }
                    if (expAngs.isNotEmpty()) {
                        val sorted = expAngs.sorted()
                        branch.metadata["quad_angle_med"] =
                            "%.2f".format(sorted[sorted.size / 2])
                    }
                    if (seedAngs.isNotEmpty()) {
                        val sorted = seedAngs.sorted()
                        branch.metadata["seed_quad_angle_med"] =
                            "%.2f".format(sorted[sorted.size / 2])
                    }
                    branch.metadata["content_expand_max_frac"] = maxFrac.toString()
                    branch.metadata["content_expand_hit_vert_cap"] =
                        hitCaps.joinToString(",") { if (it) "1" else "0" }
                    branch.metadata["content_expand_fallback_verts"] =
                        fallbackVerts.joinToString(",")
                    branch.metadata["content_expand_vert_sweep"] =
                        vertSweep.joinToString(",")

                    val variants = JSONArray()
                    // Energy-only (even cap hits) — comparison, not final.
                    val energyRects = expandedQuads.map { it.toAabb() }
                    val tOcrE0 = System.currentTimeMillis()
                    val energyOcr = ocrPumpOrientedQuads(expandedQuads, gray, imgW, imgH)
                    branch.metadata["t_ocr_energy_ms"] =
                        (System.currentTimeMillis() - tOcrE0).toString()
                    val energyCands = buildRedBoxCandidates(
                        energyRects, energyOcr.asis, energyOcr.digits,
                        energyOcr.asisProbs, energyOcr.digitsProbs, energyOcr.recB64,
                        recWList = energyOcr.recW, recHList = energyOcr.recH,
                    )
                    val energyCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(energyCands)
                    variants.put(
                        ocrScaleVariantJson(
                            1.0f, energyRects, expandedQuads, energyCands, energyCv,
                            kind = "energy", hitCaps = hitCaps,
                        ),
                    )

                    val countQuads = expDiag.map { it.countQuad }
                    val countRects = countQuads.map { it.toAabb() }
                    val tOcrC0 = System.currentTimeMillis()
                    val countOcr = ocrPumpOrientedQuads(countQuads, gray, imgW, imgH)
                    branch.metadata["t_ocr_count_ms"] =
                        (System.currentTimeMillis() - tOcrC0).toString()
                    branch.metadata["n_ocr_count"] = countQuads.size.toString()
                    branch.metadata["n_count_pull"] =
                        expDiag.count { it.countPull?.pulled == true }.toString()
                    branch.metadata["count_pulled"] = expDiag.joinToString(",") { d ->
                        val c = d.countPull
                        when {
                            c == null -> "0"
                            c.pulledTop && c.pulledBot -> "tb"
                            c.pulledTop -> "t"
                            c.pulledBot -> "b"
                            else -> "0"
                        }
                    }
                    val countCands = buildRedBoxCandidates(
                        countRects, countOcr.asis, countOcr.digits,
                        countOcr.asisProbs, countOcr.digitsProbs, countOcr.recB64,
                        recWList = countOcr.recW, recHList = countOcr.recH,
                    )
                    val countCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(countCands)
                    variants.put(
                        ocrScaleVariantJson(
                            1.0f, countRects, countQuads, countCands, countCv,
                            kind = "energy_count", hitCaps = hitCaps,
                        ),
                    )

                    // Hybrid final: energy if it stopped short of cap, else G-style verts.
                    // Do not re-OCR energy quads. Second recognize pass is only the G replacements.
                    val anyCap = fallbackVerts.isNotEmpty() && hitCaps.any { it }
                    val hybridQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                    val hybridCands: List<RedBoxOcrCandidate>
                    val hybridCv: CostVolClassifyResult
                    if (!anyCap) {
                        hybridQuads.addAll(expandedQuads)
                        hybridCands = energyCands
                        hybridCv = energyCv
                        branch.metadata["t_ocr_g_ms"] = "0"
                        branch.metadata["n_ocr_g"] = "0"
                    } else {
                        val extraQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                        seedQuads.forEachIndexed { i, seed ->
                            if (hitCaps[i]) {
                                fallbackVerts.forEach { vv ->
                                    extraQuads.add(
                                        ContentExpandUtils.calculatedOriented(
                                            seed, vv, SET_G_HORIZ_FACTOR,
                                        ),
                                    )
                                }
                            }
                        }
                        val extraRects = extraQuads.map { it.toAabb() }
                        val tOcrG0 = System.currentTimeMillis()
                        val extraOcr = ocrPumpOrientedQuads(extraQuads, gray, imgW, imgH)
                        branch.metadata["t_ocr_g_ms"] =
                            (System.currentTimeMillis() - tOcrG0).toString()
                        branch.metadata["n_ocr_g"] = extraQuads.size.toString()
                        val extraCands = buildRedBoxCandidates(
                            extraRects, extraOcr.asis, extraOcr.digits,
                            extraOcr.asisProbs, extraOcr.digitsProbs, extraOcr.recB64,
                            recWList = extraOcr.recW, recHList = extraOcr.recH,
                        )
                        var extraI = 0
                        val stitched = ArrayList<RedBoxOcrCandidate>()
                        seedQuads.forEachIndexed { i, seed ->
                            if (!hitCaps[i]) {
                                hybridQuads.add(expandedQuads[i])
                                if (i < energyCands.size) stitched.add(energyCands[i])
                            } else {
                                repeat(fallbackVerts.size) {
                                    hybridQuads.add(extraQuads[extraI])
                                    if (extraI < extraCands.size) stitched.add(extraCands[extraI])
                                    extraI++
                                }
                            }
                        }
                        hybridCands = stitched
                        hybridCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(hybridCands)
                    }
                    val hybridRects = hybridQuads.map { it.toAabb() }
                    variants.put(
                        ocrScaleVariantJson(
                            1.0f, hybridRects, hybridQuads, hybridCands, hybridCv,
                            kind = "energy_or_g", hitCaps = hitCaps,
                        ),
                    )
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    val tOcrG = branch.metadata["t_ocr_g_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = (tOcrE + tOcrG).toString()

                    // Calculated-vert sweep on every seed (G-style), for combo cover.
                    for (vv in vertSweep) {
                        val qV = seedQuads.map {
                            ContentExpandUtils.calculatedOriented(it, vv, SET_G_HORIZ_FACTOR)
                        }
                        val rV = qV.map { it.toAabb() }
                        val oV = ocrPumpOrientedQuads(qV, gray, imgW, imgH)
                        val cV = buildRedBoxCandidates(
                            rV, oV.asis, oV.digits, oV.asisProbs, oV.digitsProbs, oV.recB64,
                            recWList = oV.recW, recHList = oV.recH,
                        )
                        val cvV = PumpCostVolUtils.classifyCostVolFromBoxOcr(cV)
                        variants.put(
                            ocrScaleVariantJson(
                                1.0f + 2f * vv, rV, qV, cV, cvV,
                                kind = "vert", v = vv,
                            ),
                        )
                    }

                    val primaryRects = hybridRects
                    val primaryQuads = hybridQuads
                    val primaryCands = hybridCands
                    val primaryCv = hybridCv
                    val scalesToOcr = ocrScales.ifEmpty { listOf(1.0f) }
                    val pdHunksMerged = mergeGeometryIntoHunks(pdHunksRawTotal)
                    branch.pathResults["Paddle"] = getFinal(
                        pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet320x48, paddleEngine, context, imgW, imgH, primaryCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = keptAabb,
                        ocrSourceRects = primaryRects,
                        candidates = primaryCands,
                        costCand = primaryCv.costCand,
                        volCand = primaryCv.volCand,
                        finalCost = primaryCv.cost,
                        finalVol = primaryCv.vol,
                        assembly = mapOf(
                            "method" to "oriented_independent",
                            "contentExpandMode" to ContentExpandUtils.Mode.INTERIOR_ENERGY.name,
                            "maxFrac" to maxFrac,
                            "enableJump" to enableJump,
                            "jumpFrac" to jumpFrac,
                            "ocrScales" to scalesToOcr,
                            "finalOcrScale" to 1.0f,
                            "doDeskew" to false,
                            "useOriented" to true,
                            "orientedMerge" to "normal_sides",
                            "detModel" to (expDetAsset ?: "product_det"),
                            "vertFactors" to fallbackVerts,
                            "vertSweep" to vertSweep,
                            "finalKind" to if (fallbackVerts.isEmpty()) "energy" else "energy_or_g",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to energyRatio,
                            "freezeHorzDuringVert" to freezeHorzDuringVert,
                            "vertPadFrac" to vertPadFrac,
                            "countPull" to "gx-run-count valley; scaleVariants kind=energy_count",
                            "note" to assemblyNote,
                        ),
                        oranges = emptyList(),
                        ocrQuads = primaryQuads,
                        seedQuads = seedQuads,
                        scaleVariants = variants,
                    )
                    doBOrDRedOnlyImage()
                    val aPd = seedQuads.flatMap { pumpQuadEdgeAnns(it, Color.RED, 2) } +
                        primaryQuads.flatMap { pumpQuadEdgeAnns(it, Color.BLUE, 4) }
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }

                /**
                 * Content-expand column (P family).
                 * @param doDeskew full-image paddle deskew before det (false for *-rot columns)
                 * @param useOriented independent rot path (one detect, keep quads, warp rec)
                 */
                fun makeContentExpandProc(
                    mode: ContentExpandUtils.Mode,
                    assemblyNote: String,
                    expDetAsset: String? = null,
                    enableJump: Boolean = false,
                    jumpFrac: Float = 0.40f,
                    doDeskew: Boolean = true,
                    useOriented: Boolean = false,
                    ocrScales: List<Float> = listOf(1.0f),
                    maxFrac: Float = 1.0f,
                    fallbackVerts: List<Float> = emptyList(),
                    vertSweep: List<Float> = emptyList(),
                    energyTraceOut: File? = null,
                    energyRatio: Float = 0.45f,
                    freezeHorzDuringVert: Boolean = false,
                    vertEnergy: ContentExpandUtils.VertEnergyKind =
                        ContentExpandUtils.VertEnergyKind.MAGNITUDE,
                    vertPadFrac: Float = 0.0f,
                ): suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit =
                    { ws, br, det, w, h ->
                        val workspace = ws
                        val branch = br
                        val discoveryDetails = det
                        val imgW = w
                        val imgH = h
                        pdHunksDetectedTotal.clear()
                        pdHunksRawTotal.clear()
                        pdHunksExpTotal.clear()
                        pdHunksMaxTotal.clear()
                        pdHunksNativeTotal.clear()
                        val orientedQuads = mutableListOf<ContentExpandUtils.OrientedQuad>()
                        val tDeskewStart = System.currentTimeMillis()
                        val tilt: Float
                        if (doDeskew) {
                            val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p, longEdgeTarget = 256)
                            tilt = -deskewRes.paddleCppAngle
                            OdometerOcrUtils.rotate(workspace, tilt)
                            branch.metadata["tilt"] = "%.2f".format(tilt)
                        } else {
                            tilt = 0f
                            branch.metadata["tilt"] = "0"
                            branch.metadata["deskew"] = "skipped"
                        }
                        branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
                        branch.metadata["heatmap_box_mode"] = "minAreaRect"
                        branch.metadata["content_expand_mode"] = mode.name
                        branch.metadata["content_expand_jump"] = enableJump.toString()
                        branch.metadata["content_expand_jump_frac"] = jumpFrac.toString()
                        branch.metadata["content_expand_oriented"] = useOriented.toString()
                        branch.metadata["content_expand_ocr_scales"] = ocrScales.joinToString(",")
                        branch.metadata["content_expand_max_frac"] = maxFrac.toString()
                        branch.metadata["content_expand_energy_ratio"] = energyRatio.toString()
                        branch.metadata["content_expand_freeze_horz"] = freezeHorzDuringVert.toString()
                        branch.metadata["content_expand_vert_energy"] = vertEnergy.name
                        branch.metadata["content_expand_vert_pad"] = vertPadFrac.toString()
                        branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                        if (expDetAsset != null) {
                            NativePaddleEngine.loadExperimentDetTiers(context, expDetAsset)
                            branch.metadata["det_model"] = expDetAsset
                            branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                        } else {
                            branch.metadata["det_model"] = "product_det"
                            branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                        }
                        try {
                            if (useOriented) {
                                runIndependentOrientedColumn(
                                    workspace = workspace,
                                    branch = branch,
                                    discoveryDetails = discoveryDetails,
                                    imgW = imgW,
                                    imgH = imgH,
                                    enableJump = enableJump,
                                    jumpFrac = jumpFrac,
                                    ocrScales = ocrScales,
                                    tilt = tilt,
                                    assemblyNote = assemblyNote,
                                    expDetAsset = expDetAsset,
                                    orientedQuadsOut = orientedQuads,
                                    maxFrac = maxFrac,
                                    fallbackVerts = fallbackVerts,
                                    vertSweep = vertSweep,
                                    energyRatio = energyRatio,
                                    freezeHorzDuringVert = freezeHorzDuringVert,
                                    vertPadFrac = vertPadFrac,
                                    energyTraceOut = energyTraceOut,
                                )
                            } else {
                            scales.forEach { scale ->
                                val srcW = workspace.p.width
                                val srcH = workspace.p.height
                                val currentLongEdge = max(srcW, srcH)
                                val scaleFactor =
                                    if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                                val targetW = (srcW * scaleFactor).toInt()
                                val targetH = (srcH * scaleFactor).toInt()
                                val (outerId, innerId) = prepareScale(workspace, scale)
                                val paddleResults = runDiscoveryPaddle(
                                    workspace, outerId, paddleEngine, targetW, targetH,
                                    scale, branch.metadata,
                                    boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                                    hmThresh = HEAT_THR_U8_GE1,
                                    maskDilatePasses = 0,
                                )
                                pdHunksDetectedTotal.addAll(paddleResults[0])
                                pdHunksRawTotal.addAll(paddleResults[1])
                                pdHunksExpTotal.addAll(paddleResults[2])
                                pdHunksMaxTotal.addAll(paddleResults[3])
                                pdHunksNativeTotal.addAll(paddleResults[4])
                                workspace.c[innerId].release()
                                workspace.c[outerId].release()
                                discoveryDetails["Paddle Raw"]!![scale] = paddleResults[1]
                                discoveryDetails["Paddle Expanded"]!![scale] = paddleResults[2]
                                discoveryDetails["Paddle Max Extent"]!![scale] = paddleResults[3]
                                discoveryDetails["Paddle Native"]!![scale] = paddleResults[4]
                            }
                            branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                            doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                            doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
                            doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
                            val redPixelList = pdHunksRawTotal.map { hh ->
                                android.graphics.Rect(
                                    hh.rect.left.toInt(), hh.rect.top.toInt(),
                                    hh.rect.right.toInt(), hh.rect.bottom.toInt(),
                                )
                            }.toMutableList()
                            doCrossScaleRedboxFilterPixel(redPixelList)
                            pruneRedPixelsTopN(redPixelList, context, imgH)
                            pdHunksRawTotal.clear()
                            pdHunksRawTotal.addAll(redPixelList.map { r ->
                                PumpHunk(
                                    "",
                                    RectF(
                                        r.left.toFloat(), r.top.toFloat(),
                                        r.right.toFloat(), r.bottom.toFloat(),
                                    ),
                                )
                            })
                            captureRedboxData(pdHunksRawTotal, workspace, branch)

                            val gray = workspace.p.mat
                            val expandOpts = ContentExpandUtils.ExpandOptions(
                                maxFrac = maxFrac,
                                enableJump = enableJump,
                                jumpFrac = jumpFrac,
                                energyRatio = energyRatio,
                                freezeHorzDuringVert = freezeHorzDuringVert,
                                vertEnergy = vertEnergy,
                                vertPadFrac = vertPadFrac,
                                recordVertEnergy = energyTraceOut != null,
                            )
                            val tExpand0 = System.currentTimeMillis()
                            val expDiag = redPixelList.map { seed ->
                                ContentExpandUtils.expandDiagnose(gray, seed, mode, expandOpts)
                            }
                            branch.metadata["t_expand_ms"] =
                                (System.currentTimeMillis() - tExpand0).toString()
                            val expandedBase = expDiag.map { it.rect }
                            val hitCaps = expDiag.map { it.hitVertCap }
                            branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                            branch.metadata["n_ocr_energy"] = expandedBase.size.toString()
                            if (energyTraceOut != null) {
                                try {
                                    writeExpandEnergyTrace(
                                        energyTraceOut, file.name, maxFrac,
                                        expDiag.mapNotNull { it.energyTrace },
                                        column = "P4-jump",
                                    )
                                    branch.metadata["content_expand_energy_trace"] = energyTraceOut.name
                                } catch (t: Throwable) {
                                    Log.e(TAG, "energy trace write failed for ${file.name}", t)
                                }
                            }
                            branch.metadata["content_expand_hit_vert_cap"] =
                                hitCaps.joinToString(",") { if (it) "1" else "0" }
                            branch.metadata["content_expand_fallback_verts"] =
                                fallbackVerts.joinToString(",")
                            val variants = JSONArray()
                            val tOcrE0 = System.currentTimeMillis()
                            val energyOcr = ocrPumpRectsAsisAndDigits(expandedBase)
                            branch.metadata["t_ocr_energy_ms"] =
                                (System.currentTimeMillis() - tOcrE0).toString()
                            val energyCands = buildRedBoxCandidates(
                                expandedBase, energyOcr.asis, energyOcr.digits,
                                energyOcr.asisProbs, energyOcr.digitsProbs, energyOcr.recB64,
                                recWList = energyOcr.recW, recHList = energyOcr.recH,
                            )
                            val energyCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(energyCands)
                            variants.put(
                                ocrScaleVariantJson(
                                    1.0f, expandedBase, emptyList(), energyCands, energyCv,
                                    kind = "energy", hitCaps = hitCaps,
                                ),
                            )
                            val countRects = expDiag.map { it.rectCount }
                            val tOcrC0 = System.currentTimeMillis()
                            val countOcr = ocrPumpRectsAsisAndDigits(countRects)
                            branch.metadata["t_ocr_count_ms"] =
                                (System.currentTimeMillis() - tOcrC0).toString()
                            branch.metadata["n_ocr_count"] = countRects.size.toString()
                            branch.metadata["n_count_pull"] =
                                expDiag.count { it.countPull?.pulled == true }.toString()
                            branch.metadata["count_pulled"] = expDiag.joinToString(",") { d ->
                                val c = d.countPull
                                when {
                                    c == null -> "0"
                                    c.pulledTop && c.pulledBot -> "tb"
                                    c.pulledTop -> "t"
                                    c.pulledBot -> "b"
                                    else -> "0"
                                }
                            }
                            val countCands = buildRedBoxCandidates(
                                countRects, countOcr.asis, countOcr.digits,
                                countOcr.asisProbs, countOcr.digitsProbs, countOcr.recB64,
                                recWList = countOcr.recW, recHList = countOcr.recH,
                            )
                            val countCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(countCands)
                            variants.put(
                                ocrScaleVariantJson(
                                    1.0f, countRects, emptyList(), countCands, countCv,
                                    kind = "energy_count", hitCaps = hitCaps,
                                ),
                            )
                            val useG = fallbackVerts.isNotEmpty()
                            val hybridRects = ArrayList<android.graphics.Rect>()
                            if (useG) {
                                redPixelList.forEachIndexed { i, seed ->
                                    if (!hitCaps[i]) {
                                        hybridRects.add(expandedBase[i])
                                    } else {
                                        fallbackVerts.forEach { vv ->
                                            hybridRects.add(
                                                ContentExpandUtils.calculatedAabb(
                                                    seed, vv, SET_G_HORIZ_FACTOR, gray.cols(), gray.rows(),
                                                ),
                                            )
                                        }
                                    }
                                }
                            } else {
                                hybridRects.addAll(expandedBase)
                            }
                            val hybridPair = if (useG && hitCaps.any { it }) {
                                val extraRects = ArrayList<android.graphics.Rect>()
                                redPixelList.forEachIndexed { i, seed ->
                                    if (hitCaps[i]) {
                                        fallbackVerts.forEach { vv ->
                                            extraRects.add(
                                                ContentExpandUtils.calculatedAabb(
                                                    seed, vv, SET_G_HORIZ_FACTOR,
                                                    gray.cols(), gray.rows(),
                                                ),
                                            )
                                        }
                                    }
                                }
                                val tOcrG0 = System.currentTimeMillis()
                                val extraOcr = ocrPumpRectsAsisAndDigits(extraRects)
                                branch.metadata["t_ocr_g_ms"] =
                                    (System.currentTimeMillis() - tOcrG0).toString()
                                branch.metadata["n_ocr_g"] = extraRects.size.toString()
                                val extraCands = buildRedBoxCandidates(
                                    extraRects, extraOcr.asis, extraOcr.digits,
                                    extraOcr.asisProbs, extraOcr.digitsProbs, extraOcr.recB64,
                                    recWList = extraOcr.recW, recHList = extraOcr.recH,
                                )
                                var extraI = 0
                                val stitched = ArrayList<RedBoxOcrCandidate>()
                                redPixelList.forEachIndexed { i, _ ->
                                    if (!hitCaps[i]) {
                                        if (i < energyCands.size) stitched.add(energyCands[i])
                                    } else {
                                        repeat(fallbackVerts.size) {
                                            if (extraI < extraCands.size) stitched.add(extraCands[extraI])
                                            extraI++
                                        }
                                    }
                                }
                                val cv = PumpCostVolUtils.classifyCostVolFromBoxOcr(stitched)
                                variants.put(
                                    ocrScaleVariantJson(
                                        1.0f, hybridRects, emptyList(), stitched, cv,
                                        kind = "energy_or_g", hitCaps = hitCaps,
                                    ),
                                )
                                stitched to cv
                            } else if (useG) {
                                branch.metadata["t_ocr_g_ms"] = "0"
                                branch.metadata["n_ocr_g"] = "0"
                                variants.put(
                                    ocrScaleVariantJson(
                                        1.0f, hybridRects, emptyList(), energyCands, energyCv,
                                        kind = "energy_or_g", hitCaps = hitCaps,
                                    ),
                                )
                                energyCands to energyCv
                            } else {
                                branch.metadata["t_ocr_g_ms"] = "0"
                                branch.metadata["n_ocr_g"] = "0"
                                energyCands to energyCv
                            }
                            val tOcrEa = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                            val tOcrGa = branch.metadata["t_ocr_g_ms"]?.toLongOrNull() ?: 0L
                            branch.metadata["t_ocr_ms"] = (tOcrEa + tOcrGa).toString()
                            val primaryRects = hybridRects
                            val primaryCands = hybridPair.first
                            val primaryCv = hybridPair.second
                            val finalKind = if (useG) "energy_or_g" else "energy"
                            val scalesToOcr = ocrScales.ifEmpty { listOf(1.0f) }
                            val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)
                            branch.pathResults["Paddle"] = getFinal(
                                pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace,
                                experimentRecSet320x48, paddleEngine, context, imgW, imgH, primaryCands,
                            )
                            branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                                reds = redPixelList,
                                ocrSourceRects = primaryRects,
                                candidates = primaryCands,
                                costCand = primaryCv.costCand,
                                volCand = primaryCv.volCand,
                                finalCost = primaryCv.cost,
                                finalVol = primaryCv.vol,
                                assembly = mapOf(
                                    "method" to "content_expand",
                                    "contentExpandMode" to mode.name,
                                    "maxFrac" to maxFrac,
                                    "enableJump" to enableJump,
                                    "jumpFrac" to jumpFrac,
                                    "ocrScales" to scalesToOcr,
                                    "finalOcrScale" to 1.0f,
                                    "doDeskew" to doDeskew,
                                    "useOriented" to false,
                                    "detModel" to (expDetAsset ?: "product_det"),
                                    "vertFactors" to fallbackVerts,
                                    "finalKind" to finalKind,
                                    "hitVertCap" to hitCaps,
                                    "energyRatio" to energyRatio,
                                    "freezeHorzDuringVert" to freezeHorzDuringVert,
                                    "vertEnergy" to vertEnergy.name,
                                    "vertPadFrac" to vertPadFrac,
                                    "countPull" to "gx-run-count valley; scaleVariants kind=energy_count",
                                    "note" to assemblyNote,
                                ),
                                oranges = emptyList(),
                                scaleVariants = variants,
                            )
                            doBOrDRedOnlyImage()
                            val blueHunks = primaryRects.map { r ->
                                PumpHunk(
                                    "",
                                    RectF(
                                        r.left.toFloat(), r.top.toFloat(),
                                        r.right.toFloat(), r.bottom.toFloat(),
                                    ),
                                )
                            }
                            val aPd = getAnns(pdHunksRawTotal, Color.RED, 2) +
                                getAnns(blueHunks, Color.BLUE, 4)
                            branch.images["PD"] = OcrUtils.takeSnapshot(
                                workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                                aPd, null, workspace,
                            ).first
                            }
                        } finally {
                            if (expDetAsset != null) {
                                try {
                                    NativePaddleEngine.restoreProductionDetTiers(context)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "restoreProductionDetTiers failed", t)
                                }
                            }
                        }
                    }

                val procP = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P: product det + interior-energy expand (jump off, deskew)",
                    expDetAsset = null,
                    enableJump = false,
                    doDeskew = true,
                    useOriented = false,
                )
                val procPJump = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P-jump: product det + interior-energy + height jump/retract",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                )
                val procP4 = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4: PP-OCRv4_mobile det + interior-energy expand (jump off, deskew)",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = false,
                    doDeskew = true,
                    useOriented = false,
                )
                val procP4Jump = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-jump: energy maxFrac=0.4; G4 verts if hit cap",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    fallbackVerts = SET_G4_VERT_FACTORS,
                    energyTraceOut = File(
                        energyTraceRoot,
                        "jump_" + safeTraceName(file.name) + ".json",
                    ),
                )
                val procP4M65 = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-m65: frozen-width mean |∇| 0.65 + 0.08·seedH pad + L/R jump; G 0/0.05/0.15 if hit cap",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertEnergy = ContentExpandUtils.VertEnergyKind.MAGNITUDE,
                    vertPadFrac = 0.08f,
                )
                val procP4Gx = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-gx: frozen-width mean |∂I/∂x| 0.55 + 0.08·seedH pad + L/R jump; G 0/0.05/0.15 if hit cap",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    energyRatio = 0.55f,
                    freezeHorzDuringVert = true,
                    vertEnergy = ContentExpandUtils.VertEnergyKind.GX,
                    vertPadFrac = 0.08f,
                )
                val procP4Xycut = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-xycut: frozen-width XY-cut on |∂I/∂x| + 0.15·seedH pad + L/R jump; G 0/0.05/0.15 if hit cap",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    freezeHorzDuringVert = true,
                    vertEnergy = ContentExpandUtils.VertEnergyKind.XYCUT_GX,
                    vertPadFrac = 0.15f,
                )
                val procPRot = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P-rot: one minAreaRect detect, no deskew, expandOriented, warp rec",
                    expDetAsset = null,
                    enableJump = false,
                    doDeskew = false,
                    useOriented = true,
                )
                val procP4Rot = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-rot: one v4 minAreaRect detect, expandOriented, warp rec",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = false,
                    doDeskew = false,
                    useOriented = true,
                )
                val procP4RotJump = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P4-rot: m65 energy (0.65 frozen + 0.08 pad) + G 0/0.05/0.15 on cap; no vert sweep",
                    expDetAsset = "PP-OCRv4_mobile_det",
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    vertSweep = rotVertSweep,
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.08f,
                    energyTraceOut = File(
                        energyTraceRoot,
                        "rot_" + safeTraceName(file.name) + ".json",
                    ),
                )
                val procProdJump = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "Prod-jump: energy maxFrac=0.4; G-- verts if hit cap",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    fallbackVerts = SET_G_MINUS_MINUS_VERT_FACTORS,
                )
                val procProdM65 = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "Prod-m65: product det + frozen-width mean |∇| 0.65 + 0.08 pad + L/R jump; maxFrac=2.5 so a short seed can grow; G 0/0.05/0.15 if hit cap",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = 2.5f,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertEnergy = ContentExpandUtils.VertEnergyKind.MAGNITUDE,
                    vertPadFrac = 0.08f,
                )
                val procProdRot = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "Prod-rot: m65 energy (0.65 frozen + 0.08 pad) + G 0/0.05/0.15 on cap; no vert sweep",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    fallbackVerts = SET_M65_CAP_VERT_FACTORS,
                    vertSweep = rotVertSweep,
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.08f,
                )
                // Hybrid helpers: current-pass discovery+filter+prune; append stage blue OCR to combined lists.
                suspend fun hybridRunDiscoveryStage(
                    workspace: BufferSet,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    branch: PumpBranch,
                    imgW: Int,
                    imgH: Int
                ): List<PumpHunk> {
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    scales.forEach { scale ->
                        val srcW = workspace.p.width
                        val srcH = workspace.p.height
                        val currentLongEdge = max(srcW, srcH)
                        val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                        val targetW = (srcW * scaleFactor).toInt()
                        val targetH = (srcH * scaleFactor).toInt()
                        val targetLongEdge = max(targetW, targetH)
                        val (outerId, innerId) = prepareScale(workspace, scale)
                        val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
                        pdHunksDetectedTotal.addAll(paddleResults[0])
                        pdHunksRawTotal.addAll(paddleResults[1])
                        pdHunksExpTotal.addAll(paddleResults[2])
                        pdHunksMaxTotal.addAll(paddleResults[3])
                        pdHunksNativeTotal.addAll(paddleResults[4])
                        workspace.c[innerId].release()
                        workspace.c[outerId].release()
                        discoveryDetails["Paddle Raw"]!![scale] = paddleResults[1]
                        discoveryDetails["Paddle Expanded"]!![scale] = paddleResults[2]
                        discoveryDetails["Paddle Max Extent"]!![scale] = paddleResults[3]
                        discoveryDetails["Paddle Native"]!![scale] = paddleResults[4]
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
                    doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
                    val redPixelList = pdHunksRawTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }.toMutableList()
                    doCrossScaleRedboxFilterPixel(redPixelList)
                    pruneRedPixelsTopN(redPixelList, context, imgH)
                    pdHunksRawTotal.clear()
                    pdHunksRawTotal.addAll(redPixelList.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    })
                    val expPixel = pdHunksExpTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }.toMutableList()
                    doCrossScaleRedboxFilterPixel(expPixel)
                    pruneRedPixelsTopN(expPixel, context, imgH)
                    pdHunksExpTotal.clear()
                    pdHunksExpTotal.addAll(expPixel.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    })
                    val maxPixel = pdHunksMaxTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }.toMutableList()
                    doCrossScaleRedboxFilterPixel(maxPixel)
                    pruneRedPixelsTopN(maxPixel, context, imgH)
                    pdHunksMaxTotal.clear()
                    pdHunksMaxTotal.addAll(maxPixel.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    })
                    return pdHunksRawTotal.toList()
                }

                suspend fun hybridAppendStageOcr(
                    reds: List<PumpHunk>,
                    vertFactors: List<Float>,
                    combinedBluePixel: MutableList<android.graphics.Rect>,
                    combinedAsis: MutableList<String>,
                    combinedDigits: MutableList<String>,
                    combinedAsisProbs: MutableList<String>,
                    combinedDigitsProbs: MutableList<String>,
                    lastBlueHunks: MutableList<PumpHunk>,
                    lastOrangeHunks: MutableList<PumpHunk>,
                    imgW: Int,
                    imgH: Int
                ) {
                    val (customBlue, customOrange) = createBlueAndOrangeHunksFromReds(reds, imgW, imgH, vertFactors, 0.5f)
                    lastBlueHunks.clear()
                    lastBlueHunks.addAll(customBlue)
                    lastOrangeHunks.clear()
                    lastOrangeHunks.addAll(customOrange)
                    val bluePixel = customBlue.map { bh ->
                        android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                    }
                    val ocr = ocrPumpRectsAsisAndDigits(bluePixel)
                    combinedBluePixel.addAll(bluePixel)
                    combinedAsis.addAll(ocr.asis)
                    combinedDigits.addAll(ocr.digits)
                    combinedAsisProbs.addAll(ocr.asisProbs)
                    combinedDigitsProbs.addAll(ocr.digitsProbs)
                }

                val procI: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p, longEdgeTarget = 256)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
                    val combinedBluePixel = mutableListOf<android.graphics.Rect>()
                    val combinedAsis = mutableListOf<String>()
                    val combinedDigits = mutableListOf<String>()
                    val combinedAsisProbs = mutableListOf<String>()
                    val combinedDigitsProbs = mutableListOf<String>()
                    val allVertFactors = iGVert + iDVert + iEVert
                    val lastBlueHunks = mutableListOf<PumpHunk>()
                    val lastOrangeHunks = mutableListOf<PumpHunk>()
                    var lastReds = listOf<PumpHunk>()
                    val tGStart = System.currentTimeMillis()
                    lastReds = hybridRunDiscoveryStage(workspace, discoveryDetails, branch, imgW, imgH)
                    captureRedboxData(lastReds, workspace, branch)
                    hybridAppendStageOcr(lastReds, iGVert, combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs, lastBlueHunks, lastOrangeHunks, imgW, imgH)
                    branch.metadata["t_hybrid_g_ms"] = (System.currentTimeMillis() - tGStart).toString()
                    val tHistStart = System.currentTimeMillis()
                    val (valleyGrays, peakGrays) = OdometerOcrUtils.getValleyPeakGrays(workspace.p.mat)
                    val (intensityLow, intensityHigh) = OdometerOcrUtils.getClipStretchLowHigh(workspace.p.mat)
                    OdometerOcrUtils.applyContrastStretch(workspace.p.mat, intensityLow, intensityHigh)
                    val stretchSpan = intensityHigh - intensityLow
                    fun adjustGrayForStretch(g: Int): Int =
                        if (stretchSpan > 0) ((g - intensityLow) * 255.0 / stretchSpan).toInt().coerceIn(0, 255) else g
                    val adjustedValleyGrays = valleyGrays.map { adjustGrayForStretch(it) }
                    val adjustedPeakGrays = peakGrays.map { adjustGrayForStretch(it) }
                    branch.metadata["t_hybrid_hist_ms"] = (System.currentTimeMillis() - tHistStart).toString()
                    val tDStart = System.currentTimeMillis()
                    lastReds = hybridRunDiscoveryStage(workspace, discoveryDetails, branch, imgW, imgH)
                    hybridAppendStageOcr(lastReds, iDVert, combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs, lastBlueHunks, lastOrangeHunks, imgW, imgH)
                    branch.metadata["t_hybrid_d_ms"] = (System.currentTimeMillis() - tDStart).toString()
                    val tPushStart = System.currentTimeMillis()
                    OdometerOcrUtils.applyValleyPushWithGrays(workspace.p.mat, adjustedValleyGrays, adjustedPeakGrays)
                    branch.metadata["t_hybrid_push_ms"] = (System.currentTimeMillis() - tPushStart).toString()
                    val tEStart = System.currentTimeMillis()
                    lastReds = hybridRunDiscoveryStage(workspace, discoveryDetails, branch, imgW, imgH)
                    hybridAppendStageOcr(lastReds, iEVert, combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs, lastBlueHunks, lastOrangeHunks, imgW, imgH)
                    branch.metadata["t_hybrid_e_ms"] = (System.currentTimeMillis() - tEStart).toString()
                    val allCands = buildRedBoxCandidates(combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs)
                    val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)
                    branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, lastReds, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, allCands)
                    val redPixelI = lastReds.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }
                    val orangePixelI = lastOrangeHunks.map { bh ->
                        android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                    }
                    val cvI = PumpCostVolUtils.classifyCostVolFromBoxOcr(allCands)
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelI,
                        ocrSourceRects = combinedBluePixel,
                        candidates = allCands,
                        costCand = cvI.costCand,
                        volCand = cvI.volCand,
                        finalCost = cvI.cost,
                        finalVol = cvI.vol,
                        assembly = mapOf(
                            "method" to "calculated-hybrid",
                            "hybrid" to "D+E+G k=10",
                            "vertFactors" to allVertFactors,
                            "gVert" to iGVert,
                            "dVert" to iDVert,
                            "eVert" to iEVert,
                            "horizFactor" to 0.5f,
                            "orangeSideExt" to 0.1,
                            "note" to "G raw, clip+adjust p/v, D, valley push, E; one combined classify"
                        ),
                        oranges = orangePixelI
                    )
                    doBOrDRedOnlyImage()
                    val aPdI = getAnns(lastReds, Color.RED, 2) + getAnns(lastBlueHunks, Color.BLUE, 4) + getAnns(lastOrangeHunks, Color.rgb(255, 165, 0), 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdI, null, workspace).first
                }
                val flowProcessors = buildList {
                    add("Set G-- (4 pass, none, calculated)" to procGMinusMinus)
                    add("Set G4 (v4 det, calculated 0.0-2.5)" to procG4)
                    add("Set P4-jump (v4 + energy + jump, S OCR)" to procP4Jump)
                    add("Set P4-m65 (v4 + mean0.65 frozen + jump)" to procP4M65)
                    add("Set P4-gx (v4 + gx0.55 frozen + jump)" to procP4Gx)
                    add("Set P4-xycut (v4 + xycut-gx frozen + jump)" to procP4Xycut)
                    add("Set P4-rot-jump (v4 oriented + jump, S OCR)" to procP4RotJump)
                    add("Set Prod-jump (product + energy + jump, S OCR)" to procProdJump)
                    add("Set Prod-m65 (product + mean0.65 frozen + jump)" to procProdM65)
                    add("Set Prod-rot (product oriented + jump, S OCR)" to procProdRot)
                }
                // Parked (compiled, not scheduled): P/P-jump/P4/P-rot, H*, L/M, G-dense/K.
                @Suppress("UNUSED_VARIABLE")
                val parked = listOf(
                    procGDense, procK, procP, procPJump, procP4,
                    procPRot, procP4Rot,
                ) +
                    procHorizByFactor.values + listOf(procL, procM)
                val processor = flowProcessors.firstOrNull { it.first == flowName }?.second
                    ?: error("No processor registered for flow: $flowName")

                tDiscoveryWrapperStart = System.currentTimeMillis()
                processor(workspace, branch, discoveryDetails, imgW, imgH)
                branch.metadata["t_discovery_wrapper_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                // t_discovery_wrapper_ms covers the main body processor / 4-scale discovery call (distinct from inner per-scale t_pd_inference_* / t_pd_native_post_*) for A/B gap attribution

            branch.metadata["t_total_flow_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
            // Additional lightweight context for interpreting the granular timings (cheap, high value, no extra run needed)
            branch.metadata["n_reds_at_probe"] = "see Set C probe for actual when flow==C (pre-filter 30 in example JSON)"
            branch.metadata["img_w"] = imgW.toString()
            branch.metadata["img_h"] = imgH.toString()
            }  // end of per-flow special handling (B/C thin calls to extracted helpers; A baseline)

            // Final Reporting
            // Pump experiment deskew long-edge 256 (multi-scale: low wild rate on pump mid-scales).
            val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(
                masterBuffer.p,
                longEdgeTarget = 256,
            )
            val deskewHtml = deskewResA.engines.map { (k, v) -> "$k: ${v.angle}&deg; (${v.timesMs.sum()}ms)" }.joinToString("<br>")

            val rowHtml = pBuildHtmlRowDynamic(
                rowIndex = index + 1,
                fileName = file.name,
                imgW = imgW,
                imgH = imgH,
                isDegraded = meta.isDegraded,
                root = root,
                tDeskew = 0L, // Combined in flows
                tilt = deskewResA.angle,
                deskewHtml = deskewHtml,
                diagnostic = meta.diagnostic
            )

            Log.d("PUMP_HTML", "row=${index + 1} rowHtml.len=${rowHtml.length} currentSize=$currentSize (part=$partCount)")
            if (currentSize + rowHtml.length > maxSizeBytes) {
                currentFile.appendText(footer)
                Log.i("PUMP_HTML", "starting new HTML part $partCount at row ${index + 1}")
                currentFile = pStartNewFile()
                currentSize = 0
            }
            currentFile.appendText(rowHtml)
            currentSize += rowHtml.length

            val photoJson = pSerializePhotoResultToJson(
                index + 1, imgW, imgH, imgW, imgH, meta.isDegraded, meta.diagnostic, deskewResA, tSnapOrig, 0L, file.name, root, originalHistogram
            )

            logHeapState(context, "before-photo-json-serialize")
            Log.i("PUMP_FRAG", "row=${index + 1} photoJson keys=${photoJson.length()}, writing frag...")
            val fragFile = getPhotoFragmentFile(reportDir, timestamp, index + 1)
            fragFile.bufferedWriter().use { writer ->
                appendJsonObject(writer, photoJson, 2, 0)
            }
            val fragSize = fragFile.length()
            Log.i("PUMP_FRAG", "row=${index + 1} frag size=$fragSize bytes")

            if (!firstPhoto) jsonWriter.write(",\n") else firstPhoto = false
            appendJsonObject(jsonWriter, photoJson, 2, 0)
            jsonWriter.flush()

            fragFile.delete()
            Log.i("PUMP_FRAG", "streamed row ${index + 1} to main JSON, deleted frag (size was $fragSize)")
            logHeapState(context, "after-photo-json-stream")

            val summaryText = flows.map { f ->
                val br = root.getBranch(f)
                if (br.pathResults.containsKey("ML")) {
                    "$f: ${br.pathResults["ML"]?.cost ?: "F"}"
                } else {
                    "$f Paddle: ${br.pathResults["Paddle"]?.cost ?: "F"}"
                }
            }.joinToString(" | ")
            val resultSummary = PumpPhotoResultSummary(file.name, summaryText, 1.0f, "")
            onProgress(resultSummary, (index + 1).toFloat() / total)
            delay(50)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Experiment failed for row ${index + 1} (${file.name}):\n" + Log.getStackTraceString(e))
            Log.w("PUMP_FRAG", "partial run - JSON may be incomplete (no final footer) at row ${index + 1}")
        }
    }
    currentFile.appendText(footer)

    jsonWriter.write(jsonFooter)
    jsonWriter.close()
    logHeapState(context, "after-json-close")
    Log.i("PUMP_JSON", "wrote JSON footer and closed main JSON file")

    experimentRecSet320x48.release()
    experimentRecSet1024x48.release()
    experimentDetSet512x128.release()
    masterBuffer.release()
    Log.i(TAG, "runPumpExperiment:end json=${jsonFile.absolutePath} total=$total")
    jsonFile
}

private fun pSerializePhotoResultToJson(
    lineNumber: Int, probedW: Int, probedH: Int, decodedW: Int, decodedH: Int,
    isDegraded: Boolean, nativeProbe: String, deskewResA: OdometerOcrUtils.DeskewResult? = null,
    tSnapOrig: Long = 0, tSnapDeskew: Long = 0, fileName: String = "",
    root: PumpBranch,
    originalHistogram: JSONArray
): JSONObject {
    val rootJson = JSONObject()
    rootJson.apply {
        put("line_number", lineNumber); put("file", fileName)
        put("probedWidth", probedW); put("probedHeight", probedH)
        put("imageWidth", decodedW); put("imageHeight", decodedH)
        put("isDegraded", isDegraded); put("nativeProbe", nativeProbe)
        put("t_thumb_orig_ms", tSnapOrig); put("t_snap_deskew_ms", tSnapDeskew)
        put("original_histogram", originalHistogram)

        val scaleTelemetry = JSONObject()
        root.subBranches.values.forEach { branch ->
            branch.metadata.forEach { (k, v) ->
                if (k.startsWith("t_pd_scale_")) {
                    scaleTelemetry.put(k.removePrefix("t_pd_scale_"), v)
                }
            }
        }
        put("scale_telemetry", scaleTelemetry)

        put("tree", root.serializeToJson())

        val d = JSONObject()
        d.pPutSafe("angle_a", (deskewResA?.angle ?: 0f).toDouble())
        deskewResA?.engines?.get("Paddle V3")?.metadata?.forEach { (k, v) ->
            if (k.contains("chk") || k.contains("count")) d.put(k, v)
        }
        put("deskew", d)
    }
    return rootJson
}

private fun appendJsonValue(out: Appendable, value: Any?, indent: Int, indentLevel: Int) {
    if (out is StringBuilder && out.length > PER_PHOTO_FRAGMENT_BUFFER_BYTES) {
        throw IllegalStateException("JSON fragment exceeded ${PER_PHOTO_FRAGMENT_BUFFER_BYTES / (1024 * 1024)}MB ceiling")
    }
    when (value) {
        null -> out.jsonAppend("null")
        JSONObject.NULL -> out.jsonAppend("null")
        is JSONObject -> appendJsonObject(out, value, indent, indentLevel)
        is JSONArray -> appendJsonArray(out, value, indent, indentLevel)
        is String -> {
            out.jsonAppend('"')
            escapeJsonString(out, value)
            out.jsonAppend('"')
        }
        is Boolean -> out.jsonAppend(value.toString())
        is Number -> out.jsonAppend(value.toString())
        else -> {
            out.jsonAppend('"')
            escapeJsonString(out, value.toString())
            out.jsonAppend('"')
        }
    }
}

private fun appendJsonObject(out: Appendable, json: JSONObject, indent: Int, indentLevel: Int) {
    out.jsonAppend("{\n")
    val keys = json.keys()
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    var first = true
    while (keys.hasNext()) {
        if (!first) {
            out.jsonAppend(",\n")
        }
        first = false
        val key = keys.next()
        val value = json.get(key)
        out.jsonAppend(indentStr).jsonAppend('"').jsonAppend(key).jsonAppend("\": ")
        appendJsonValue(out, value, indent, nextLevel)
    }
    out.jsonAppend("\n").jsonAppend(" ".repeat(indentLevel * indent)).jsonAppend("}")
}

private fun appendJsonArray(out: Appendable, array: JSONArray, indent: Int, indentLevel: Int) {
    out.jsonAppend("[\n")
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    for (i in 0 until array.length()) {
        if (i > 0) {
            out.jsonAppend(",\n")
        }
        out.jsonAppend(indentStr)
        appendJsonValue(out, array.get(i), indent, nextLevel)
    }
    out.jsonAppend("\n").jsonAppend(" ".repeat(indentLevel * indent)).jsonAppend("]")
}

private fun escapeJsonString(out: Appendable, str: String) {
    for (i in 0 until str.length) {
        val ch = str[i]
        when (ch) {
            '"' -> out.jsonAppend("\\\"")
            '\\' -> out.jsonAppend("\\\\")
            '/' -> out.jsonAppend("\\/")
            '\b' -> out.jsonAppend("\\b")
            '\n' -> out.jsonAppend("\\n")
            '\r' -> out.jsonAppend("\\r")
            '\t' -> out.jsonAppend("\\t")
            else -> {
                if (ch.code < 32 || ch.code > 126) {
                    out.jsonAppend(String.format("\\u%04x", ch.code))
                } else {
                    out.jsonAppend(ch)
                }
            }
        }
    }
}


/** Long-edge angle of an oriented quad, normalized to [-90, 90] degrees. */
private fun pumpQuadLongAngleDeg(q: ContentExpandUtils.OrientedQuad): Float {
    val p = q.pts
    if (p.size < 8) return 0f
    var best = 0.0
    var ang = 0f
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        val dx = (p[j * 2] - p[i * 2]).toDouble()
        val dy = (p[j * 2 + 1] - p[i * 2 + 1]).toDouble()
        val len = kotlin.math.hypot(dx, dy)
        if (len > best) {
            best = len
            ang = Math.toDegrees(kotlin.math.atan2(dy, dx)).toFloat()
        }
    }
    var a = ang
    while (a > 90f) a -= 180f
    while (a < -90f) a += 180f
    return a
}

/** Four LINE annotations along the quad edges (photo pixels). */
private fun pumpQuadEdgeAnns(
    q: ContentExpandUtils.OrientedQuad,
    color: Int,
    width: Int,
): List<SnapshotAnnotation> {
    val p = q.pts
    if (p.size < 8) return emptyList()
    val edges = ArrayList<SnapshotAnnotation>(4)
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        edges.add(
            SnapshotAnnotation(
                p[i * 2].toInt(),
                p[i * 2 + 1].toInt(),
                p[j * 2].toInt(),
                p[j * 2 + 1].toInt(),
                Shape.LINE,
                color,
                width,
            ),
        )
    }
    return edges
}

private fun serializeDiscoveryDetails(details: Map<String, Map<Int, List<PumpHunk>>>): JSONObject {
    val root = JSONObject()
    details.forEach { (engine, scales) ->
        val engObj = JSONObject()
        scales.forEach { (scale, hunks) ->
            val arr = JSONArray()
            hunks.forEach { h ->
                arr.put(JSONObject().apply {
                    put("l", h.rect.left.toDouble()); put("t", h.rect.top.toDouble())
                    put("w", h.rect.width().toDouble()); put("h", h.rect.height().toDouble())
                    put("text", h.text)
                })
            }
            engObj.put(scale.toString(), arr)
        }
        root.put(engine, engObj)
    }
    return root
}


/** Per-red object-based blue: components intersecting red, then union of all comps with Y-overlap to those seeds. */
/** vSW/hSW from run-length histogram on red areas of binarized image (native calculateHistogramWithThresholdH).
 *  Uses NativeImageUtils long-lived 8192-bin buffers; only meta (vSW/hSW) is consumed here. */
/** Shrink full blue union rect to 40px tall (centered) for OCR crop; 4px offset applied in rec buffer. */
private fun generateHistogramB64(mat: org.opencv.core.Mat, floorPercentile: Float, mask: org.opencv.core.Mat? = null): String {
    if (mat.empty()) return ""
    val hist = org.opencv.core.Mat()
    // Support optional mask for red-box histograms (per approved plan for Set C). When mask provided, calc is restricted to those pixels (exact reuse of polarity probe pattern).
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), mask ?: org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))

    val bins = FloatArray(64); hist.get(0, 0, bins)

    // 186px wide to exclude 0 and 63 bins
    val bmp = Bitmap.createBitmap(186, 300, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
    canvas.drawColor(Color.BLACK)
    val paint = Paint()

    // Display-only: ignore bins 0 and 63 for scaling/readability.
    val maxVal = (1..62).maxOf { bins[it] }.toDouble().coerceAtLeast(1.0)

    for (i in 1..62) {
        val h = (bins[i] / maxVal * 240.0).toInt().coerceAtMost(240)
        val x = ((i - 1) * 3).toFloat()
        paint.color = Color.WHITE; canvas.drawRect(x, (240 - h).toFloat(), x + 3f, 240f, paint)

        if (i % 8 == 0) { paint.color = Color.RED; canvas.drawRect(x, 246f, x + 3f, 270f, paint) }
        if (i == (floorPercentile * 63).toInt()) { paint.color = Color.YELLOW; canvas.drawRect(x, 246f, x + 3f, 270f, paint) }
    }
    val b64 = OcrUtils.bitmapToBase64(bmp, 80); bmp.recycle(); hist.release(); return b64
}

private const val PUMP_PD_TARGET_W = 340
private const val PUMP_PD_TARGET_H = 255
private const val PUMP_CROP_TARGET_W = 150
private const val PUMP_CROP_TARGET_H = 75
private const val PUMP_C_VISUAL_TARGET_W = 340
private const val PUMP_SMALL_TARGET_W = 180
private const val PUMP_PER_RED_TARGET_W = 120
private const val PER_PHOTO_FRAGMENT_BUFFER_BYTES = 4 * 1024 * 1024

/** Rec buffers from costVolDecisionData_Paddle (scaleVariants and/or candidates). */
private fun pRecBuffersHtml(br: PumpBranch): String {
    val raw = br.metadata["costVolDecisionData_Paddle"] ?: return ""
    val data = try {
        org.json.JSONObject(raw)
    } catch (_: Exception) {
        return ""
    }
    val sb = StringBuilder()
    fun emitCands(cands: org.json.JSONArray, heading: String) {
        var any = false
        val chunk = StringBuilder()
        chunk.append("<div><small>$heading</small></div>")
        chunk.append("<div style='display:flex;flex-wrap:wrap;gap:3px;'>")
        for (j in 0 until cands.length()) {
            val c = cands.optJSONObject(j) ?: continue
            val b64 = c.optString("recB64")
            if (b64.isNullOrEmpty()) continue
            any = true
            val lab = c.optString("label")
            val asis = c.optString("asis")
            val dig = c.optString("digits")
            chunk.append(
                "<div style='width:48%;font-size:9px;'>" +
                    "<img src='data:image/jpeg;base64,$b64' style='width:100%;image-rendering:pixelated;'>" +
                    "<br>$lab asis=$asis dig=$dig</div>",
            )
        }
        chunk.append("</div>")
        if (any) sb.append(chunk)
    }
    val variants = data.optJSONArray("scaleVariants")
    if (variants != null && variants.length() > 0) {
        sb.append("<div style='margin-top:6px;text-align:left;'><b>Rec buffers</b></div>")
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            val s = v.optDouble("s", Double.NaN)
            val cands = v.optJSONArray("candidates") ?: continue
            emitCands(cands, "S=${"%.2f".format(s)}")
        }
    } else {
        val cands = data.optJSONArray("candidates")
        if (cands != null && cands.length() > 0) {
            sb.append("<div style='margin-top:6px;text-align:left;'><b>Rec buffers</b></div>")
            emitCands(cands, "candidates")
        }
    }
    return sb.toString()
}

private fun pBuildHtmlHeader(time: String, total: Int, version: String, device: String, flows: List<String>): String = buildString {
    appendLine("<html><head><title>Pump Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .res-table { width: 100%; border: none; font-size: 20px; } .res-table th { background: #f0f0f0; }</style></head><body>")
    appendLine("<h1>Pump Extraction Experiment</h1><p><b>Run:</b> $time | <b>Device:</b> $device | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th>")
    val sorted = flows.toSortedSet()
    val hasML = if (sorted.isNotEmpty()) setOf(sorted.first()) else emptySet()  // data-driven from subBranches presence (ML only on first/A); no name if; matches row hasML intent
    sorted.forEach { flow ->
        if (flow in hasML) appendLine("<th style='width:350px;'>$flow ML</th>")
        appendLine("<th style='width:350px;'>$flow Paddle</th>")
    }
    appendLine("<th style='width:600px;'>Final Comparison</th></tr>")
}

private fun pBuildHtmlRowDynamic(
    rowIndex: Int,
    fileName: String,
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    root: PumpBranch,
    tDeskew: Long,
    tilt: Float,
    deskewHtml: String,
    diagnostic: String = ""
): String = buildString {
    // fix-remaining-report-issues-20260619-plan: whitelist t_total_flow_ms + minimal essentials only; full timing stays in JSON
    val htmlMetaWhitelist = setOf("t_total_flow_ms", "img_w", "img_h")
    val metaHtml = root.subBranches.values.flatMap { it.metadata.entries }.filter { (k, v) ->
        k in htmlMetaWhitelist && v.length <= 100
    }.joinToString("<br>") { (k, v) -> "<small>$k: $v</small>" }
    val rowHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty() || metaHtml.isNotEmpty()) "<br><small>Native: $diagnostic</small><br>$metaHtml" else ""
    val img = root.images

    val perSetTilts = root.subBranches.toSortedMap().entries
        .joinToString(" | ") { (name, br) ->
            val t = br.metadata["tilt"] ?: "?"
            val q = br.metadata["quad_angle_med"]
            if (q != null) "$name: $t° (quad $q°)" else "$name: $t°"
        }
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$rowHtml</small>$diagHtml<br><span style=\"font-size:6px\"><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt per set:</b> $perSetTilts<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["before"]}'><br><small>Orig</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist1"]}'><br><small>Hist 1</small></td></tr><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["after"]}'><br><small>Stretch</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist2"]}'><br><small>Hist 2</small></td></tr><tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:6px;'><small>$deskewHtml</small></td></tr></table></span></td>")

    val hasML = root.subBranches.filter { (_, br) -> br.images.containsKey("ML") && br.images["ML"]?.isNotEmpty() == true }.keys.toSet()  // data-driven from subBranches presence, no name if
    root.subBranches.toSortedMap().forEach { (name, br) ->
        if (name in hasML) {
            appendLine("<td><b>$name ML:</b><br><img src='data:image/jpeg;base64,${br.images["ML"]}'></td>")
        }
        val pdB64 = br.images["PD"] ?: ""
        if (br.images.containsKey("PD_red_only")) {
            // red-only + full PD pair (when branch populates the key from explicit helper call)
            val redOnly = br.images["PD_red_only"] ?: ""
            val full = br.images["PD"] ?: ""
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$redOnly' style='max-width:100%;'><br><small>Red boxes only (after filter)</small><br><img src='data:image/jpeg;base64,$full' style='max-width:100%;'><br><small>All annotations (red+blue+orange) as before</small>${pRecBuffersHtml(br)}</td>")
        } else if (br.images.containsKey("rawC")) {
            val raw = br.images["rawC"] ?: ""
            val pushed = br.images["pushedC"] ?: ""
            val hB = br.images["histBeforeC"] ?: ""
            val hA = br.images["histAfterC"] ?: ""
            // Per-redbox hists + labels from redboxDataC (h/w/area pixels + bins for analysis)
            // Dual visuals per red entry (red rect snapshot from crop + histogram snapshot from plot); 3-wide table, stacked h/w/area labels.
            // (Removed outdated "YUV direct jpeg visuals per plan" / "YUV direct is the target" note.)
            val rdataStr = br.metadata["redboxDataC"] ?: "[]"
            val rdata = try { org.json.JSONArray(rdataStr) } catch (e: Exception) { org.json.JSONArray() }
            // fix-4box-report-issues-20260619-plan: summary-only per-red text in HTML (full base64 in JSON metadata)
            val perRedHtml = StringBuilder()
            perRedHtml.append("<div style='margin-top:4px;'><b>Per Red Box Summary (${rdata.length()} boxes; see JSON for full data):</b></div>")
            val sortedData = (0 until rdata.length()).map { rdata.getJSONObject(it) }.sortedByDescending { it.getInt("area") }
            val numCols = 3
            perRedHtml.append("<table style='width:100%; border:none; font-size:10px;'><tr>")
            for (j in sortedData.indices) {
                val s = sortedData[j]
                val ii = s.getInt("index")
                val hh = s.getInt("h")
                val ww = s.getInt("w")
                val aa = s.getInt("area")
                perRedHtml.append("<td style='border:none; padding:2px; vertical-align:top; width:33%; text-align:center;'><small>Red${ii}: h=${hh} w=${ww} area=${aa}</small></td>")
                if ((j + 1) % numCols == 0 && j < sortedData.size - 1) {
                    perRedHtml.append("</tr><tr>")
                }
            }
            perRedHtml.append("</tr></table>")
            appendLine("<td><b>$name Paddle:</b><br><table style='width:100%; border:none; font-size:11px;'><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$raw' style='max-width:100%;'><br><small>Raw</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$pushed' style='max-width:100%;'><br><small>Valley-Pushed (few brightness vals)</small></td></tr><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hB' style='max-width:100%;'><br><small>Before</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hA' style='max-width:100%;'><br><small>After</small></td></tr></table>$perRedHtml<img src='data:image/jpeg;base64,$pdB64'></td>")
        } else {
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$pdB64'>${pRecBuffersHtml(br)}</td>")
        }
    }

    appendLine("<td><table class='res-table'><tr><th>Path</th><th>Cost</th><th>Volume</th></tr>")
    root.subBranches.toSortedMap().forEach { (name, br) ->
        br.pathResults.forEach { (eng, res) ->
            appendLine("<tr><td>$name:$eng</td>")
            appendLine("<td><b>${res.cost}</b>" + (if (res.costB64.isNotEmpty()) "<br><img src='data:image/jpeg;base64,${res.costB64}' style='width:150px;'>" else "") + "</td>")
            appendLine("<td><b>${res.vol}</b>" + (if (res.volB64.isNotEmpty()) "<br><img src='data:image/jpeg;base64,${res.volB64}' style='width:150px;'>" else "") + "</td>")
            appendLine("</tr>")
        }
    }
    appendLine("</table></td></tr>")
}

private suspend fun pExtractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        targetDir.mkdirs() // additive extract: do not wipe prior contents
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
        input.use {
            ZipInputStream(it).use { zis ->
                // flattenToBasename: images land flat at top-level of pump_photos (listFiles is non-recursive)
                ZipExtractUtils.extractZipStreamToDir(zis, targetDir, flattenToBasename = true)
            }
        }
    } catch (e: Exception) { Log.e(TAG, "Failed to extract zip", e); false }
}


private fun prepareScale(buffer: BufferSet, targetLongEdge: Int): Pair<Int, Int> {
    val srcW = buffer.p.width
    val srcH = buffer.p.height
    val currentLongEdge = max(srcW, srcH)

    val scale = if (currentLongEdge <= targetLongEdge) 1.0f else targetLongEdge.toFloat() / currentLongEdge
    val targetW = (srcW * scale).toInt()
    val targetH = (srcH * scale).toInt()

    val alignedW = ((targetW + 31) / 32) * 32
    val alignedH = ((targetH + 31) / 32) * 32

    Log.d(TAG, "prepareScale: target=$targetLongEdge -> ${targetW}x${targetH} (Aligned: ${alignedW}x${alignedH})")

    val outerId = buffer.s.createCrop(0, 0, alignedW, alignedH)
    buffer.c[outerId].clear()

    val innerId = buffer.s.createCrop(0, 0, targetW, targetH)
    Imgproc.resize(buffer.p.mat, buffer.c[innerId].mat, buffer.c[innerId].mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)

    return Pair(outerId, innerId)
}

private suspend fun runDiscoveryPaddle(
    buffer: BufferSet,
    id: Int,
    paddleEngine: NativePaddleEngine,
    contentW: Int,
    contentH: Int,
    scale: Int,
    metadata: MutableMap<String, String>? = null,
    boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
    heatDumpU8z: File? = null,
    hmThresh: Float = HEAT_THR_U8_GE1,
    maskDilatePasses: Int = 0,
): List<List<PumpHunk>> {
    Log.i(TAG, "pump_detect_call scale=$scale content=${contentW}x$contentH slice=${buffer.c[id].width}x${buffer.c[id].height}")
    ProcessMemProbe.log("pump_before_detect_scale=$scale")
    val res = paddleEngine.detect(
        buffer.c[id],
        copyHeatmap = false,
        boxMode = boxMode,
        heatDumpU8z = heatDumpU8z,
        hmThresh = hmThresh,
        maskDilatePasses = maskDilatePasses,
    ) ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    ProcessMemProbe.log("pump_after_detect_scale=$scale n_boxes=${res.nativeBoxes.size}")
    if (metadata != null) {
        metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
        metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
        metadata["heatmap_box_mode_${scale}"] = res.metadata["box_mode"] ?: boxMode.toString()
        metadata["heatmap_post_path_${scale}"] = res.metadata["heatmap_post_path"] ?: "unknown"
        metadata["hm_thresh_${scale}"] = res.metadata["hm_thresh"] ?: hmThresh.toString()
        metadata["mask_dilate_passes_${scale}"] = res.metadata["mask_dilate_passes"] ?: maskDilatePasses.toString()
        metadata["heatmap_cell_px_${scale}"] = NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
        if (heatDumpU8z != null) metadata["heat_dump_${scale}"] = heatDumpU8z.name
    }

    val masterW = buffer.c[id].width; val masterH = buffer.c[id].height

    val hist = res.heatmapHist ?: IntArray(0)
    if (metadata != null && hist.isNotEmpty()) metadata["heatmap_hist_${scale}"] = JSONArray(hist.toList()).toString()
    val rawRects = res.nativeBoxes.map { box ->
        val p = box.points
        val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
        val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
        val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
        val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
        android.graphics.Rect(minX, minY, maxX, maxY)
    }

    // Pre-redbox detected hunks (tFullB equivalent from alignment Set J runBinTrialsPaddle).
    // These are the raw objects from the detector (pre +1/denest/nonNested that produce the "raw red" tRawB-equivalent level).
    // Used only for Set C: 1px white anns (to show each detected hunk) + as the "hunks" source for per-red overlap + Y-extend derivation of blue/orange.
    // (The pdHunksRawTotal level remains the post-redbox "RED raw boxes" for display/anns/crops/mask.)
    val hunksDetected = mutableListOf<PumpHunk>()
    // Explicit pixel upscale once at ingest to full workspace/photo pixel space (using buffer full dims vs content/detect size).
    // Replaces the prior worthless ICRS roundtrip (content for pixelToIcrs + full for later icrsToPixel); direct scale here.
    // All pd* hunks now hold full pixel values in .rect from the start.
    val fullW = buffer.p.width; val fullH = buffer.p.height
    rawRects.forEach { r ->
        val ml = r.left.toInt().coerceIn(0, masterW - 1)
        val mt = r.top.toInt().coerceIn(0, masterH - 1)
        val mr = r.right.toInt().coerceIn(0, masterW - 1)
        val mb = r.bottom.toInt().coerceIn(0, masterH - 1)
        val fl = ml * fullW.toFloat() / contentW
        val ft = mt * fullH.toFloat() / contentH
        val fr = mr * fullW.toFloat() / contentW
        val fb = mb * fullH.toFloat() / contentH
        hunksDetected.add(PumpHunk("", RectF(fl, ft, fr, fb)))
    }

    // Nest filter on native packed boxes. Cell halo is applied in C++ packHeatmapBoxes
    // (kPaddleDetHeatCellPx), not a Kotlin AABB pad after the fact.
    val nonNestedRects = rawRects.filter { r1 ->
        rawRects.none { r2 -> r1 != r2 && r2.contains(r1.left + 5, r1.top + 5, r1.right - 5, r1.bottom - 5) }
    }

    // 1. Consolidate Raw Character Fragments (75% overlap rule) on de-nested native reds.
    val consolidated = OdometerOcrUtils.consolidateRects(nonNestedRects, 0.75f)

    val hunksRaw = mutableListOf<PumpHunk>()
    val hunksExpanded = mutableListOf<PumpHunk>()
    val hunksMaxExtent = mutableListOf<PumpHunk>()
    val hunksNative = mutableListOf<PumpHunk>()

    // Build raw hunks from the non-nested native rects (pre-consolidate) so the RED raw boxes
    // in reports are the packed detections. Explicit upscale (full/content) once for full photo pixels.
    nonNestedRects.forEach { rect ->
        val ml = rect.left.toInt().coerceIn(0, masterW - 1)
        val mt = rect.top.toInt().coerceIn(0, masterH - 1)
        val mr = rect.right.toInt().coerceIn(0, masterW - 1)
        val mb = rect.bottom.toInt().coerceIn(0, masterH - 1)
        val rawRect = android.graphics.Rect(ml, mt, mr, mb)

        val fl = ml * fullW.toFloat() / contentW
        val ft = mt * fullH.toFloat() / contentH
        val fr = mr * fullW.toFloat() / contentW
        val fb = mb * fullH.toFloat() / contentH
        hunksRaw.add(PumpHunk("", RectF(fl, ft, fr, fb)))
    }

    consolidated.forEach { rect ->
        // Convert to absolute master pixels (coords still in the outer/crop space)
        val ml = rect.left.toInt().coerceIn(0, masterW - 1)
        val mt = rect.top.toInt().coerceIn(0, masterH - 1)
        val mr = rect.right.toInt().coerceIn(0, masterW - 1)
        val mb = rect.bottom.toInt().coerceIn(0, masterH - 1)
        val rawRect = android.graphics.Rect(ml, mt, mr, mb)

        // 2. Perform Native Expansion (with Height-Relative Jump-Out and Retraction)
        val (retractedRect, maxExtentRect) = NativeImageUtils.expandByUniformity(buffer.c[id].mat, rawRect)

        // Capture Expanded/Retracted result -- explicit upscale to full pixel space (no content-size ICRS).
        val fl = retractedRect.left * fullW.toFloat() / contentW
        val ft = retractedRect.top * fullH.toFloat() / contentH
        val fr = retractedRect.right * fullW.toFloat() / contentW
        val fb = retractedRect.bottom * fullH.toFloat() / contentH
        hunksExpanded.add(PumpHunk("", RectF(fl, ft, fr, fb)))

        // Capture Max Extent reach (Yellow tier)
        val yfl = maxExtentRect.left * fullW.toFloat() / contentW
        val yft = maxExtentRect.top * fullH.toFloat() / contentH
        val yfr = maxExtentRect.right * fullW.toFloat() / contentW
        val yfb = maxExtentRect.bottom * fullH.toFloat() / contentH
        hunksMaxExtent.add(PumpHunk("", RectF(yfl, yft, yfr, yfb)))
    }

    // Capture Native Results (Phase 2 A/B) -- explicit upscale using full/content ratio (no ICRS).
    res.nativeBoxes.forEach { box ->
        // Points are in input Mat pixels (crop-relative)
        val scaleX = fullW.toFloat() / contentW
        val scaleY = fullH.toFloat() / contentH
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        box.points.toList().chunked(2).forEach { (px, py) ->
            val sx = px * scaleX; val sy = py * scaleY
            if (sx < minX) minX = sx; if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy; if (sy > maxY) maxY = sy
        }
        hunksNative.add(PumpHunk("Conf: %.2f".format(box.confidence), RectF(minX, minY, maxX, maxY)))
    }

    return listOf(hunksDetected, hunksRaw, hunksExpanded, hunksMaxExtent, hunksNative)
}


private fun mergeGeometryIntoHunks(allBlocks: List<PumpHunk>): List<PumpHunk> {
    if (allBlocks.isEmpty()) return emptyList()
    val merged = mutableListOf<PumpHunk>()
    val remaining = allBlocks.toMutableList()

    while (remaining.isNotEmpty()) {
        var current = remaining.removeAt(0)
        var changed = true
        while (changed) {
            changed = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                val interL = max(current.rect.left, next.rect.left); val interT = max(current.rect.top, next.rect.top)
                val interR = min(current.rect.right, next.rect.right); val interB = min(current.rect.bottom, next.rect.bottom)

                val overlapH = if (interB > interT) interB - interT else 0f
                val minH = min(current.rect.height(), next.rect.height())
                val significantOverlap = overlapH >= (minH * 0.3f)

                val isNested = current.rect.contains(next.rect) || next.rect.contains(current.rect)

                if (significantOverlap || isNested) {
                    val newRect = RectF(
                        min(current.rect.left, next.rect.left),
                        min(current.rect.top, next.rect.top),
                        max(current.rect.right, next.rect.right),
                        max(current.rect.bottom, next.rect.bottom)
                    )
                    val bestText = if (current.text.count { it.isDigit() } >= next.text.count { it.isDigit() }) current.text else next.text
                    current = PumpHunk(bestText, newRect)
                    iterator.remove()
                    changed = true
                }
            }
        }
        merged.add(current)
    }
    return merged
}

private suspend fun performHunkRecognition(hunks: List<PumpHunk>, buffer: BufferSet, recBuffer: BufferSet, engine: String, paddleEngine: NativePaddleEngine, context: Context, angle: Float = 0f): List<PumpHunk> {
    val masterW = buffer.p.width; val masterH = buffer.p.height
    // full pixel .rect already in master space (explicit upscale at ingest); no ICRS range/minEdge calc needed (relative math in callees equivalent)
    return hunks.map { hunk ->
        val l = hunk.rect.left
        val t = hunk.rect.top
        val r = hunk.rect.right
        val b = hunk.rect.bottom

        val pW = (r - l).toInt(); val pH = (b - t).toInt()

        if (pW < 2 || pH < 2) return@map hunk

        val cropId = buffer.createCrop(l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt())

        val targetH = 48; val scale = 48f / pH; val targetW = Math.min(320, (pW * scale).toInt())
        if (targetW <= 0 || targetH <= 0) return@map hunk  // guard for bad aspect / tiny derived box after prune to 4 largest (prevents OpenCV resize assertion inv_scale_x > 0 and NPE in downstream OCR for C/E on first/some photos)

        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, targetW, targetH)
        val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        org.opencv.imgproc.Imgproc.resize(buffer.c[cropId].mat, recBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)

        val res = if (engine == "ML Kit") {
                val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                recBuffer.p.nv21,
                recBuffer.p.width,
                recBuffer.p.height,
                0,
                com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                )
                val ocrRes = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
            // ML Kit 7-Segment Cleanup + Upside Down detection
            val cleaned = OdometerOcrUtils.clean7SegmentDigits(ocrRes.debugText, Math.abs(angle) > 135f)
            ocrRes.copy(debugText = cleaned)
        } else {
            paddleEngine.recognize(recBuffer.c[recCropId])
        }

        recBuffer.c[recCropId].release(); buffer.c[cropId].release()
        PumpHunk(res.debugText + if (res.perCharProbs.isNotEmpty()) " [probs:${res.perCharProbs}]" else "", hunk.rect)
    }
}


private fun stitchHunksHorizontally(hunks: List<PumpHunk>): List<PumpHunk> {
    if (hunks.isEmpty()) return emptyList()
    val sorted = hunks.sortedBy { it.rect.left }
    val result = mutableListOf<MutableList<PumpHunk>>()

    for (hunk in sorted) {
        var merged = false
        for (line in result) {
            val last = line.last()
            val h = min(hunk.rect.height(), last.rect.height())
            val vOverlap = max(0f, min(hunk.rect.bottom, last.rect.bottom) - max(hunk.rect.top, last.rect.top))
            val hGap = hunk.rect.left - last.rect.right

            if (vOverlap > 0.7f * h && hGap < 1.0f * h) {
                line.add(hunk)
                merged = true
                break
            }
        }
        if (!merged) result.add(mutableListOf(hunk))
    }

    return result.map { line ->
        val l = line.minOf { it.rect.left }
        val t = line.minOf { it.rect.top }
        val r = line.maxOf { it.rect.right }
        val b = line.maxOf { it.rect.bottom }
        val widest = r - l
        val shortest = line.minOf { it.rect.height() }
        val centerY = line.map { it.rect.centerY() }.average().toFloat()

        // Spec: inherit string with highest digit count
        val bestText = line.maxByOrNull { it.text.count { c -> c.isDigit() } }?.text ?: ""

        val fT = centerY - shortest / 2f; val fB = centerY + shortest / 2f
        PumpHunk(bestText, RectF(l, fT, r, fB))
    }
}

private fun groupLanesByVerticalGap(hunks: List<PumpHunk>): Pair<List<PumpHunk>, List<PumpHunk>> {
    if (hunks.isEmpty()) return Pair(emptyList(), emptyList())
    val sortedY = hunks.sortedBy { it.rect.centerY() }

    val lanes = mutableListOf<MutableList<PumpHunk>>()
    for (hunk in sortedY) {
        var found = false
        for (lane in lanes) {
            val anchor = lane.first()
            val h = anchor.rect.height()
            if (Math.abs(hunk.rect.centerY() - anchor.rect.centerY()) < 0.3f * h) {
                lane.add(hunk)
                found = true
                break
            }
        }
        if (!found) lanes.add(mutableListOf(hunk))
    }

    if (lanes.size < 2) return Pair(hunks, emptyList())

    // Sort lanes by centerY
    val sortedLanes = lanes.sortedBy { it.first().rect.centerY() }

    // Find largest gap between adjacent lanes
    var maxGap = -1f
    var splitIdx = 0
    for (i in 0 until sortedLanes.size - 1) {
        val gap = sortedLanes[i+1].first().rect.centerY() - sortedLanes[i].first().rect.centerY()
        if (gap > maxGap) {
            maxGap = gap
            splitIdx = i
        }
    }

    val top = sortedLanes.take(splitIdx + 1).flatten()
    val bottom = sortedLanes.drop(splitIdx + 1).flatten()
    return Pair(top, bottom)
}

private fun findBestLanePair(topLanes: List<PumpHunk>, bottomLanes: List<PumpHunk>): Pair<PumpHunk, PumpHunk>? {
    val pairs = mutableListOf<Pair<PumpHunk, PumpHunk>>()

    for (top in topLanes) {
        for (bottom in bottomLanes) {
            // integer pixel math for A exercised path (A data now has integer rects from prior Set A phases; no float height()/1.25f* etc here)
            val hB = (bottom.rect.bottom.toInt() - bottom.rect.top.toInt()).coerceAtLeast(1)
            val gap = (bottom.rect.top.toInt() - top.rect.bottom.toInt())
            val vO = min(top.rect.bottom.toInt(), bottom.rect.bottom.toInt()) - max(top.rect.top.toInt(), bottom.rect.top.toInt())
            val vOverlap = max(0, vO)
            val xO = min(top.rect.right.toInt(), bottom.rect.right.toInt()) - max(top.rect.left.toInt(), bottom.rect.left.toInt())
            val xOverlap = max(0, xO)

            val digitTop = top.text.count { it.isDigit() }
            val digitBottom = bottom.text.count { it.isDigit() }

            if (gap < (hB * 5) / 4 && vOverlap < (hB / 5) && xOverlap > 0 && digitTop >= 2 && digitBottom >= 2) {
                pairs.add(Pair(top, bottom))
            }
        }
    }

    if (pairs.isEmpty()) return null

    val goldenWords = listOf("Sale", "Total", "Gallon", "$", "Price")
    return pairs.maxByOrNull { (t, b) ->
        var score = 0
        if (goldenWords.any { t.text.contains(it, ignoreCase = true) }) score += 10
        if (goldenWords.any { b.text.contains(it, ignoreCase = true) }) score += 10
        score + t.text.count { it.isDigit() } + b.text.count { it.isDigit() }
    }
}

private fun expandHunkContext(hunk: PumpHunk, imgW: Int, imgH: Int): PumpHunk {
    // Set A exercised path only: integer pixel 1.5x expand (no float math, no 1.5f /2f, no .toFloat roundtrips in expand; direct int arith + clamp for min size; produces integer-valued rect for takeCrop/snapshot in A final crops)
    val l = hunk.rect.left.toInt()
    val t = hunk.rect.top.toInt()
    val r = hunk.rect.right.toInt()
    val b = hunk.rect.bottom.toInt()
    val h = (b - t).coerceAtLeast(1)
    val newH = (h * 3 + 1) / 2  // integer 1.5x
    val dy = newH / 2
    val dx = newH
    val nl = (l - dx).coerceIn(0, imgW - 1)
    val nt = (t - dy).coerceIn(0, imgH - 1)
    val nr = (r + dx).coerceIn(nl + 1, imgW)
    val nb = (b + dy).coerceIn(nt + 1, imgH)
    return PumpHunk(hunk.text, RectF(nl.toFloat(), nt.toFloat(), nr.toFloat(), nb.toFloat()))
}

private fun JSONObject.pPutSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.pPutSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }


                    // h/w/area kept from rect; collection to redboxDataC / redboxHistC_* / metadata unchanged.
                    val redboxDataC = JSONArray()