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
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "ExperimentPump"

private val SEED_INK_PROBE_FLOWS = listOf(
    "Set aabb-tight",
    "Set aabb-large",
    "Set rot-tight",
    "Set rot-large",
    "Set aabb-tight-color",
    "Set aabb-large-color",
    "Set rot-tight-color",
    "Set rot-large-color",
)

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

/** Keep [captureRedboxData]; off this plan. */
private const val CAPTURE_REDBOX_DATA = false

private fun countU8Value(plane: org.opencv.core.Mat, value: Int): Int {
    if (plane.empty() || plane.type() != org.opencv.core.CvType.CV_8UC1) return 0
    val w = plane.cols()
    val h = plane.rows()
    val row = ByteArray(w)
    var n = 0
    for (y in 0 until h) {
        plane.get(y, 0, row)
        for (x in 0 until w) {
            if (row[x].toInt() and 0xff == value) n++
        }
    }
    return n
}

private fun recordIncompleteLookIds(
    look: org.opencv.core.Mat,
    objImgRoot: File,
    fullRow: Int,
    col: Int,
    si: Int,
    nSeeds: Int,
    pd: ContentExpandUtils.PoisonDump,
    branch: PumpBranch,
    onLog: (String) -> Unit,
) {
    val dump = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}_incomplete.png")
    dumpObjectPlanePng(look, dump)
    val n255 = countU8Value(look, 255)
    val gap = "${pd.inkLo}:${pd.nextNonInk}"
    val phase = pd.phase.ifBlank { "poison/walk" }
    val msg = "object_id_incomplete col=$col seed=$si n=$nSeeds " +
        "inkLo=${pd.inkLo} nextNonInk=${pd.nextNonInk} n255=$n255 idGap=$gap phase=$phase"
    Log.e(TAG, msg)
    onLog(msg)
    branch.metadata["object_id_incomplete"] = msg
    branch.metadata["object_id_n255"] = n255.toString()
    branch.metadata["object_id_gap"] = gap
    branch.metadata["object_dump_incomplete_box${si + 1}"] = dump.name
    branch.metadata["object_abort_html"] =
        "<small style='color:#c00'>$msg</small>"
}

private fun dumpObjectPlanePng(plane: org.opencv.core.Mat, file: File): Boolean {
    if (plane.empty()) return false
    val rows = plane.rows()
    val cols = plane.cols()
    val type = plane.type()
    val bytes = plane.total() * plane.elemSize()
    Log.i("veAllocLog", "tag=dumpObjectPlanePng bytes=$bytes rows=$rows cols=$cols type=$type")
    if (bytes > 64L * 1024L * 1024L) {
        Log.e(
            "veAllocLog",
            "FAIL tag=dumpObjectPlanePng bytes=$bytes >64MiB rows=$rows cols=$cols type=$type",
        )
        return false
    }
    return try {
        org.opencv.imgcodecs.Imgcodecs.imwrite(file.absolutePath, plane)
    } catch (_: Throwable) {
        false
    }
}

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
        .put("grewTop", p.grewTop)
        .put("grewBot", p.grewBot)
        .put("padTop", p.padTop)
        .put("padBot", p.padBot)
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
    /** Deep link: vehicleexpenses://experiment/pump?auto=prodinkfail — prod-ink fail subset. */
    autoProdInkFail: Boolean = false,
    /** Deep link: vehicleexpenses://experiment/pump?auto=mixedfail — mixed-fail readable subset. */
    autoMixedFail: Boolean = false,
    /** Deep link: vehicleexpenses://experiment/pump?auto=detdump — prod deskew/rot discover, no expand/OCR. */
    autoDetDump: Boolean = false,
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

    fun startPumpJob(subsetNames: List<String>?, label: String, flowList: List<String>? = null) {
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
                flowList,
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

    /** Photos where ink-prod or rot-ink-prod is not exact (relax=fail) at k=0 (start-113). */
    val runProdInkFail: () -> Unit = {
        val allFiles = experimentDir.listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        } ?: emptyArray()
        val want = PROD_INK_FAIL_FILENAMES.toSet()
        val names = allFiles.map { it.name }.filter { it in want }.sorted()
        val missing = want.size - names.size
        Log.d(
            TAG,
            "Prod-ink fail listFiles: dir=${experimentDir.absolutePath} " +
                "matched=${names.size}/${want.size} missing=$missing",
        )
        if (names.isEmpty()) {
            status = "Prod-ink fail: 0 photos present (need ${want.size} in pump_photos)"
        } else {
            startPumpJob(
                names,
                "Prod-ink fail (${names.size})…" +
                    if (missing > 0) " ($missing not on device)" else "",
            )
        }
    }

    /** Trusted GT and not all-19 official exact on both devices (104 mixed-fail readable). */
    val runMixedFailReadable: () -> Unit = {
        val allFiles = experimentDir.listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        } ?: emptyArray()
        val want = MIXED_FAIL_READABLE_FILENAMES.toSet()
        val names = allFiles.map { it.name }.filter { it in want }.sorted()
        val missing = want.size - names.size
        Log.d(
            TAG,
            "Mixed-fail readable listFiles: dir=${experimentDir.absolutePath} " +
                "matched=${names.size}/${want.size} missing=$missing",
        )
        if (names.isEmpty()) {
            status = "Mixed fail: 0 photos present (need ${want.size} in pump_photos)"
        } else {
            startPumpJob(
                names,
                "Mixed fail (${names.size})…" +
                    if (missing > 0) " ($missing not on device)" else "",
            )
        }
    }

    val runDetDump: () -> Unit = {
        val allFiles = experimentDir.listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        } ?: emptyArray()
        val n = allFiles.size
        if (n == 0) {
            status = "Det dump: 0 photos in pump_photos"
        } else {
            totalPhotos = n
            val ok = ExperimentJobRunner.start(context.applicationContext, kind = "pump") { progressCb, log, statusLine ->
                statusLine("Det dump (prod × deskew/rot)…")
                val res = PumpDetDiscoverDump.run(
                    context = context.applicationContext,
                    photoDir = experimentDir,
                    reportDir = reportDir,
                    onLog = log,
                    onProgress = { done, total, name -> progressCb(done, total, name) },
                )
                res.outDir.absolutePath
            }
            if (!ok) {
                status = "Another experiment is already running (${ExperimentJobRunner.state.value.kind})"
            } else {
                status = "Det dump (prod × deskew/rot)…"
            }
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

    // Deep link: vehicleexpenses://experiment/pump?auto=first10 | auto=l1debug | auto=horiz | auto=selected | auto=prodinkfail | auto=mixedfail | auto=detdump
    LaunchedEffect(autoFirst10, autoL1Debug, autoHorizAffected, autoProdInkFail, autoMixedFail, autoSelectedSample, autoDetDump) {
        if (autoStarted || ExperimentJobRunner.isRunning()) return@LaunchedEffect
        when {
            autoL1Debug -> {
                autoStarted = true
                Log.i(TAG, "autoL1Debug starting PumpSoDebugDump")
                runL1SoDebug()
            }
            autoDetDump -> {
                autoStarted = true
                Log.i(TAG, "autoDetDump starting PumpDetDiscoverDump")
                runDetDump()
            }
            autoHorizAffected -> {
                autoStarted = true
                Log.i(TAG, "autoHorizAffected starting horiz-affected subset")
                runHorizAffected()
            }
            autoProdInkFail -> {
                autoStarted = true
                Log.i(TAG, "autoProdInkFail starting prod-ink fail subset")
                runProdInkFail()
            }
            autoMixedFail -> {
                autoStarted = true
                Log.i(TAG, "autoMixedFail starting mixed-fail readable subset")
                runMixedFailReadable()
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
        val zipLabel = "Extract Downloaded ZIP"
        val runTestLabel = "Run Test"
        val first10Label = "First 10"
        val seedInkLabel = "Seed ink probe"
        val seedInk10Label = "Seed ink first 10"
        val selectedLabel = "Selected sample (${SelectedSamplePhotos.PUMP.size} pump)"
        val horizLabel = "Horiz-affected (${HORIZ_REACH_AFFECTED_FILENAMES.size})"
        val prodInkLabel = "Prod-ink fail (${PROD_INK_FAIL_FILENAMES.size})"
        val mixedFailLabel = "Mixed fail (${MIXED_FAIL_READABLE_FILENAMES.size})"
        val detDumpLabel = "Det dump (prod × deskew/rot)"
        val l1Label = "L1 SO debug dump (buffers + heatmaps)"
        val gridLabels = listOf(
            zipLabel, runTestLabel, first10Label, seedInkLabel, seedInk10Label, selectedLabel,
            horizLabel, prodInkLabel, mixedFailLabel, detDumpLabel, l1Label,
        )
        val textMeasurer = rememberTextMeasurer()
        val buttonStyle = MaterialTheme.typography.labelLarge
        val density = LocalDensity.current
        val measuredW = with(density) {
            gridLabels.maxOf { textMeasurer.measure(it, style = buttonStyle).size.width }.toDp() + 24.dp
        }
        val cellPad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        val jobsEnabled = !isRunning && experimentDir.exists()
        data class GridBtn(
            val label: String,
            val enabled: Boolean,
            val bold: Boolean = false,
            val onClick: () -> Unit,
        )
        val gridBtns = listOf(
            GridBtn(zipLabel, true) { zipLauncher.launch(arrayOf("application/zip")) },
            GridBtn(runTestLabel, jobsEnabled) {
                val allFiles = experimentDir.listFiles { f ->
                    f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
                } ?: emptyArray()
                Log.d(TAG, "Run Test listFiles: dir=${experimentDir.absolutePath} count=${allFiles.size}")
                startPumpJob(null, "Run Test (${allFiles.size})…")
            },
            GridBtn(first10Label, jobsEnabled, onClick = runFirst10),
            GridBtn(seedInkLabel, jobsEnabled) {
                val allFiles = experimentDir.listFiles { f ->
                    f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
                } ?: emptyArray()
                Log.d(TAG, "Seed ink probe listFiles: dir=${experimentDir.absolutePath} count=${allFiles.size}")
                startPumpJob(null, "Seed ink probe (${allFiles.size})…", SEED_INK_PROBE_FLOWS)
            },
            GridBtn(seedInk10Label, jobsEnabled) {
                val allFiles = experimentDir.listFiles { f ->
                    f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
                } ?: emptyArray()
                val first10Names = allFiles.sortedBy { it.name }.take(10).map { it.name }
                Log.d(TAG, "Seed ink first 10 listFiles: dir=${experimentDir.absolutePath} count=${first10Names.size}")
                startPumpJob(first10Names, "Seed ink first 10 (${first10Names.size})…", SEED_INK_PROBE_FLOWS)
            },
            GridBtn(selectedLabel, jobsEnabled, bold = true, onClick = runSelectedSample),
            GridBtn(horizLabel, jobsEnabled, onClick = runHorizAffected),
            GridBtn(prodInkLabel, jobsEnabled, onClick = runProdInkFail),
            GridBtn(mixedFailLabel, jobsEnabled, onClick = runMixedFailReadable),
            GridBtn(detDumpLabel, jobsEnabled, onClick = runDetDump),
            GridBtn(l1Label, jobsEnabled, onClick = runL1SoDebug),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellW = minOf(measuredW, maxWidth)
            val perRow = max(1, ((maxWidth + 8.dp) / (cellW + 8.dp)).toInt())
            val cellMod = Modifier
                .width(cellW)
                .heightIn(min = ButtonDefaults.MinHeight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gridBtns.chunked(perRow).forEach { rowBtns ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowBtns.forEach { b ->
                            Button(
                                onClick = b.onClick,
                                enabled = b.enabled,
                                modifier = cellMod,
                                contentPadding = cellPad,
                            ) {
                                Text(
                                    b.label,
                                    style = buttonStyle,
                                    fontWeight = if (b.bold) FontWeight.Bold else FontWeight.Normal,
                                    softWrap = true,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
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
        val resObj = JSONObject(); pathResults.forEach { (k, v) ->
            val p = JSONObject(); p.put("cost", v.cost); p.put("vol", v.vol); resObj.put(k, p)
        }; root.put("results", resObj)
        val metaObj = JSONObject()
        metadata.forEach { (k, v) ->
            metaObj.put(k, stripJpegDupes(v)?.toString() ?: v)
        }
        root.put("metadata", metaObj)
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
    flowList: List<String>? = null,
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
    val jsonFos = FileOutputStream(jsonFile)
    fun jsonSyncStr(s: String) {
        jsonFos.write(s.toByteArray(Charsets.UTF_8))
        jsonFos.flush()
        jsonFos.fd.sync()
    }
    jsonSyncStr(jsonHeader)
    logHeapState(context, "after-json-header-write")
    Log.i("PUMP_JSON", "wrote header early, total_photos=$total")
    val journal = PumpProgressJournal(reportDir, timestamp)

    val experimentRecSet = NativePaddleEngine.recBufferSet
    val masterBuffer = BufferSet(4096, 4096)

    val flows = flowList ?: listOf(
        "Set G-- (4 pass, none, calculated)",
        "Set ink-energy-tight",
        "Set ink-energy-retract",
        "Set ink-color-tight",
        "Set ink-color-retract",
        "Set rot-energy-tight",
        "Set rot-energy-retract",
        "Set rot-color-tight",
        "Set rot-color-retract",
        "Set ink-color-tight-vsp",
        "Set ink-color-retract-vsp",
        "Set rot-color-tight-vsp",
        "Set rot-color-retract-vsp",
    )
    val heatDumpRoot by lazy {
        File(reportDir, "pump_heats_$timestamp").also { it.mkdirs() }
    }
    val objImgRoot by lazy {
        File(reportDir, "pump_imgs_$timestamp").also { it.mkdirs() }
    }

    val pumpColLabels = pumpColumnLabels(flows)
    val pumpMetaHtml =
        "<b>Run:</b> $timestamp | <b>Device:</b> $deviceModel | <b>Version:</b> ${BuildConfig.VERSION_NAME} | <b>Total:</b> $total"
    NativeImageUtils.veRssSetPath(File(reportDir, "ve-rss.log").absolutePath)
    val footer = ExperimentReportHtml.footer(
        ExperimentReportHtml.Kind.PUMP, pumpColLabels, pumpMetaHtml,
    )
    val cellsDir = File(reportDir, "pump_cells_$timestamp").also { it.mkdirs() }
    val cursorFile = File(reportDir, "pump_cursor_$timestamp.txt")
    cursorFile.writeText("0")
    val nKeepSlots = PumpOcrSettings.maxRedBoxes(context).coerceIn(
        PumpOcrSettings.MIN_MAX_RED_BOXES, PumpOcrSettings.MAX_MAX_RED_BOXES,
    )
    val flowSorted = flows.toSortedSet().toList()
    val slotNames = pumpSlotNames(nKeepSlots, flowSorted.size)
    val nSlots = slotNames.size
    val nCells = total * nSlots
    val cellOrder = (1..nCells).map { ReportCellRef(it, sortA = it) }
    val imgRel = "pump_imgs_$timestamp"
    val rowsPerFile = ExperimentReportHtml.htmlRowsPerFile(context)
    val nParts = ExperimentReportHtml.nParts(total, rowsPerFile)
    val chunks = ExperimentReportHtml.photoChunks(total, rowsPerFile)
    val reportStem = "pump_report_$timestamp"
    chunks.forEachIndexed { part0, photoRange ->
        val partIndex1 = part0 + 1
        val partFile = ExperimentReportHtml.partFile(reportDir, reportStem, partIndex1, nParts)
        partFile.writeText(
            pBuildHtmlHeader(
                timestamp, total, BuildConfig.VERSION_NAME, deviceModel, pumpColLabels, pumpMetaHtml,
                partIndex1, nParts, reportStem,
            ),
        )
        val skeleton = buildString {
            for (i in photoRange) {
                val f = photos[i]
                val line = allPhotos.indexOfFirst { it.name == f.name } + 1
                append("<tr data-photo='$line'>")
                append("<td data-col=\"0\"><b>#$line</b><br><small>${f.name}</small>")
                val origId = i * nSlots + 1
                append("<div class=\"orig-details\" id=\"ve-r$line-c0-orig-details\">")
                append(ReportCollapser.htmlBegin(origId))
                append(ReportCollapser.htmlEnd(origId))
                append("</div></td>")
                flowSorted.forEachIndexed { fi, _ ->
                    val colIdx = fi + 1
                    append("<td data-col=\"$colIdx\">")
                    fun emitSlot(slot: String, htmlId: String, cls: String = "") {
                        val sid = i * nSlots + slotNames.indexOf(slot) + 1
                        val clsAttr = if (cls.isEmpty()) "" else " class=\"$cls\""
                        append("<div id=\"$htmlId\"$clsAttr>")
                        append(ReportCollapser.htmlBegin(sid))
                        append(ReportCollapser.htmlEnd(sid))
                        append("</div>")
                    }
                    emitSlot("c$colIdx-pd-red", "ve-r$line-c$colIdx-pd-red")
                    emitSlot("c$colIdx-pd-full", "ve-r$line-c$colIdx-pd-full")
                    emitSlot("c$colIdx-overlay-full", "ve-r$line-c$colIdx-overlay-full", "overlay-full")
                    for (k in 1..nKeepSlots) {
                        emitSlot(
                            "c$colIdx-look-ink-box$k",
                            "ve-r$line-c$colIdx-look-ink-box$k",
                            "look-ink-crops",
                        )
                    }
                    for (k in 1..nKeepSlots) {
                        emitSlot(
                            "c$colIdx-rec-box$k",
                            "ve-r$line-c$colIdx-rec-box$k",
                            "rec-crops",
                        )
                    }
                    emitSlot("c$colIdx-rec-extra", "ve-r$line-c$colIdx-rec-extra", "rec-crops")
                    emitSlot("c$colIdx-dump", "ve-r$line-c$colIdx-dump-details", "dump-details")
                    append("</td>")
                }
                val resId = i * nSlots + slotNames.indexOf("results") + 1
                append("<td class=\"results-col\">")
                append(ReportCollapser.htmlBegin(resId))
                append(ReportCollapser.htmlEnd(resId))
                append("</td></tr>\n")
            }
        }
        partFile.appendText(skeleton)
        partFile.appendText(footer)
    }
    val collapser = ReportCollapser(
        htmlFileForId = { id ->
            val photoIndex0 = (id - 1) / nSlots
            val partIndex1 = ExperimentReportHtml.partIndex1ForPhoto(photoIndex0, rowsPerFile)
            ExperimentReportHtml.partFile(reportDir, reportStem, partIndex1, nParts)
        },
        cellsDir = cellsDir,
        cursorFile = cursorFile,
        nCells = nCells,
        cellOrder = cellOrder,
        onLog = onLog,
    )

    coroutineScope {
    val collapseJob = collapser.start(this)
    try {
    journal.append("RUN_START") {
        put("total", total)
        put("version", BuildConfig.VERSION_NAME)
    }
    photos.forEachIndexed { index, file ->
        val fullRow = allPhotos.indexOfFirst { it.name == file.name } + 1
        try {
            journal.append("PHOTO_START") {
                put("photo", index)
                put("line", fullRow)
                put("file", file.name)
            }
            onLog("Processing ${index + 1}/$total: ${file.name} (line $fullRow)")

            val (probedW, probedH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            if (probedW <= 0 || probedH <= 0) {
                android.util.Log.e("ExperimentPump", "Invalid probe ${probedW}x$probedH for ${file.name}; skipping photo")
                journal.append("PHOTO_END") {
                    put("photo", index)
                    put("line", fullRow)
                    put("file", file.name)
                }
                return@forEachIndexed
            }
            val imgW = probedW
            val imgH = probedH
            masterBuffer.resize(imgW, imgH)
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, masterBuffer.p)

            val root = PumpBranch("Root")
            val (beforeB64, tSnapOrig) = OcrUtils.takeSnapshot(masterBuffer.p, null, PUMP_SMALL_TARGET_W, 0, emptyList(), null, masterBuffer)
            root.images["before"] = beforeB64
            root.images["hist1"] = generateHistogramB64(masterBuffer.p.mat, 0.40f)
            pPublishOrigDetails(
                rowIndex = fullRow,
                photoIndex0 = index,
                imgW = imgW,
                imgH = imgH,
                isDegraded = meta.isDegraded,
                root = root,
                tDeskew = 0L,
                deskewHtml = "",
                diagnostic = meta.diagnostic,
                imgDir = objImgRoot,
                cellsDir = cellsDir,
                slotNames = slotNames,
                nSlots = nSlots,
            )

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

            var photoTilt = 0f
            val tPhotoDeskew0 = System.currentTimeMillis()
            NativePaddleEngine.bufferSetA.p.clear()
            masterBuffer.p.mat.copyTo(NativePaddleEngine.bufferSetA.p.mat)
            if (!masterBuffer.p.uvMat.empty() && !NativePaddleEngine.bufferSetA.p.uvMat.empty()) {
                masterBuffer.p.uvMat.copyTo(NativePaddleEngine.bufferSetA.p.uvMat)
            }
            val deskewOnce = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(
                NativePaddleEngine.bufferSetA.p, longEdgeTarget = 256,
            )
            photoTilt = -deskewOnce.paddleCppAngle
            OdometerOcrUtils.rotate(NativePaddleEngine.bufferSetA, photoTilt)
            root.metadata["t_deskew_once_ms"] = (System.currentTimeMillis() - tPhotoDeskew0).toString()

            // Dynamic Flow Processing
            // Phase 2 dispatch: list-based (flowName, processor) pairs — not index-aligned. Only entries in `flows`
            // are run; the catalog below maps every defined processor by exact flow name.
            flows.forEachIndexed { col, flowName ->
                val branch = root.getBranch(flowName)
                val tFlowStart = System.currentTimeMillis()
                val tSetupStart = System.currentTimeMillis()
                val workspace = if (flowName.contains("rot-", ignoreCase = true)) {
                    masterBuffer
                } else {
                    NativePaddleEngine.bufferSetA
                }

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
                val prodDetScales = listOf(224, 608)
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
                    if (c.recB64.isNotEmpty()) j.put("_htmlRec", c.recB64)
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
                            "box${i + 1}",
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
                                anns.add(SnapshotAnnotation(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt(), Shape.RECTANGLE, AnnYuv.RED, 2))
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
                fun getAnns(list: List<PumpHunk>, color: AnnYuv, width: Int) = list.map { h ->
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
                    inkSweeps: List<ContentExpandUtils.InkSweep?> = emptyList(),
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
                    val sweepArr = JSONArray()
                    inkSweeps.forEach { s ->
                        if (s == null) {
                            sweepArr.put(JSONObject())
                        } else {
                            val vArr = JSONArray(); s.vScores.forEach { vArr.put(it) }
                            val hArr = JSONArray(); s.hScores.forEach { hArr.put(it) }
                            val jo = JSONObject()
                                .put("thr", s.thr.toDouble())
                                .put("sPx", s.sPx.toDouble())
                                .put("minRun", s.minRun)
                                .put("energyRatio", s.energyRatio.toDouble())
                                .put("v0", s.v0)
                                .put("v1", s.v1)
                                .put("h0", s.h0)
                                .put("h1", s.h1)
                                .put("vScores", vArr)
                                .put("hScores", hArr)
                                .put("walkT", s.walkT)
                                .put("walkB", s.walkB)
                                .put("jumpL", s.jumpL)
                                .put("jumpR", s.jumpR)
                            val jpeg = s.threshJpeg
                            if (jpeg != null && jpeg.isNotEmpty()) {
                                jo.put(
                                    "_htmlThresh",
                                    Base64.encodeToString(jpeg, Base64.NO_WRAP),
                                )
                            }
                            sweepArr.put(jo)
                        }
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
                        .put("inkSweep", sweepArr)
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
                        workspace, paddleEngine, experimentRecSet, rects, imgW, imgH,
                    )
                }

                suspend fun doBOrDRedOnlyImage() {
                    // Red-only image for Set B/D (per approved plan): clean view of post-filter reds only (no blue, no orange) so user can inspect redbox merging state without other annotations overlaid. Full image remains exactly "as is happening now". D mirrors B.
                    val redAnnsOnly = getAnns(pdHunksRawTotal, AnnYuv.RED, 2)
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
                    /** Null = product det. */
                    expDetAsset: String? = null,
                    /** If true, width is P4-jump L/R jump-retract (no [horizFactor] / orange). */
                    horizJump: Boolean = false,
                    /** If true, 7-seg stroke expand (seed-ROI `s`, k=1 vert, j=2 horz). No G-list. */
                    seg7Stroke: Boolean = false,
                    detScales: List<Int> = prodDetScales,
                    /** Color expand only. Gray call sites omit this (default false). */
                    chromaExpand: Boolean = false,
                    /** 0 gray, 1 chromaMag, 2 chromaTint2, 3 chromaTint3, 4 color_adaptive. Nonzero wins over [chromaExpand]. */
                    chromaMode: Int = 0,
                    gapFrac: Float = ContentExpandUtils.SEG7_GAP_FRAC,
                    minSeedHsToFreeze: Float = 0f,
                    boundStrategy: Int = 0,
                    inkExpand: ((
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<android.graphics.Rect>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                    ) -> List<ContentExpandUtils.Seg7Expand>)? = null,
                    chromaNote: String? = null,
                    boundNote: String? = null,
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
                    val tilt = photoTilt
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
                    detScales.forEach { scale ->
                    val srcW = workspace.p.width
                    val srcH = workspace.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()
                    val targetLongEdge = max(targetW, targetH)

                    PumpCostVolUtils.prepareScale(workspace, scale)
                    val heatFile = photoHeatDir?.let { File(it, "scale${scale}_heatmap.u8z") }
                    val paddleResults = PumpCostVolUtils.runDiscoveryPaddle(
                        workspace, paddleEngine, targetW, targetH, scale, branch.metadata,
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
                if (CAPTURE_REDBOX_DATA) {
                    captureRedboxData(pdHunksRawTotal, workspace, branch)
                }

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
                val customBlueG: List<PumpHunk>
                val customOrangeG: List<PumpHunk>
                val seg7Strokes: List<ContentExpandUtils.StrokeWidthInSeed>
                val inkWalkSeeds: List<android.graphics.Rect>
                val inkWalkBoxes: List<android.graphics.Rect>
                val inkWalkPads: List<android.graphics.Rect>
                val inkJumpOpts: ContentExpandUtils.ExpandOptions?
                var makeGInkSweeps: List<ContentExpandUtils.InkSweep?> = emptyList()
                val expandMode = if (chromaMode != 0) chromaMode else if (chromaExpand) 1 else 0
                val inkFn = inkExpand
                if (chromaNote != null) {
                    branch.metadata["content_expand_chroma"] = chromaNote
                }
                if (boundNote != null) {
                    branch.metadata["content_expand_bound"] = boundNote
                }
                if (inkFn != null) {
                    val jumpOpts = ContentExpandUtils.ExpandOptions(
                        maxFrac = 0.4f,
                        enableJump = true,
                        jumpFrac = 0.60f,
                        retractClearFrac = 0.30f,
                        energyRatio = 0.65f,
                    )
                    val tExp0 = System.currentTimeMillis()
                    val seeds = pdHunksRawTotal.map { h ->
                        android.graphics.Rect(
                            h.rect.left.toInt(), h.rect.top.toInt(),
                            h.rect.right.toInt(), h.rect.bottom.toInt(),
                        )
                    }
                    branch.metadata.remove("look_ink")
                    masterBuffer.s.clear()
                    NativePaddleEngine.bufferSetB.p.clear()
                    val segs = ArrayList<ContentExpandUtils.Seg7Expand>(seeds.size)
                    var nextInk = 255
                    var nextNon = 1
                    var inkLo = 255
                    val isColor = expandMode != 0
                    seeds.forEachIndexed { si, seed ->

                        val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                        if (poisonBuf.size >= 3) {
                            poisonBuf[0] = nextInk
                            poisonBuf[1] = nextNon
                            poisonBuf[2] = inkLo
                        }
                        val one = inkFn(
                            NativePaddleEngine.bufferSetA.p.mat,
                            if (isColor) NativePaddleEngine.bufferSetA.p.uvMat
                            else NativePaddleEngine.bufferSetB.s.mat,
                            listOf(seed),
                            NativePaddleEngine.bufferSetA.s.mat,
                            masterBuffer.s.mat,
                            NativePaddleEngine.bufferSetB.p.mat,
                            if (isColor) NativePaddleEngine.bufferSetB.s.mat
                            else NativePaddleEngine.bufferSetB.p.uvMat,
                            poisonBuf,
                        )
                        val seg = one.first()
                        segs.add(seg)
                        if (poisonBuf.size >= 3) {
                            nextInk = poisonBuf[poisonBuf.size - 3]
                            nextNon = poisonBuf[poisonBuf.size - 2]
                            inkLo = poisonBuf[poisonBuf.size - 1]
                        }
                        val pd = seg.poison
                        if (pd?.classChange == true) {
                            val dumpSeed = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}.png")
                            dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                            branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                        }
                        if (pd?.bandH == -1) {
                            recordIncompleteLookIds(
                                NativePaddleEngine.bufferSetA.s.mat,
                                objImgRoot, fullRow, col, si, seeds.size, pd, branch, onLog,
                            )
                        }
                        snapshotLookInk(
                            listOf(seed), listOf(seg.rect), imgW, imgH, branch,
                            listOf(seg.poison),
                            listOf(seg.tele),
                            listOf(seg.sweep),
                            listOf(seg.stroke),
                            reportDir, timestamp, fullRow, branch.name,
                            source = NativePaddleEngine.bufferSetB.p,
                            scratchYuv = NativePaddleEngine.bufferSetB,
                        )
                    }
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetB.p,
                        workspace,
                        branch,
                    )
                    val dumpFinal = File(objImgRoot, "r${fullRow}_c${col}_final.png")
                    dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                    branch.metadata["object_dump_final"] = dumpFinal.name
                    val walks = seeds.indices.map { i ->
                        Triple(seeds[i], segs[i].rect, segs[i].stroke)
                    }
                    val jumpedOnce = walks.map { it.second }
                    fun inkBoxesFor(kk: Float): List<android.graphics.Rect> {
                        return walks.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                jumpedOnce[i], walks[i].first, kk,
                                walks[i].third.sPx, imgW, imgH,
                            )
                        }
                    }
                    val official = segs.map { it.rect }
                    makeGInkSweeps = segs.indices.map { i ->
                        segs[i].sweep?.withOfficial(official[i])
                    }
                    customBlueG = official.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    customOrangeG = emptyList()
                    seg7Strokes = walks.map { it.third }
                    inkWalkSeeds = walks.map { it.first }
                    inkWalkBoxes = jumpedOnce
                    inkWalkPads = segs.map { it.rectPad }
                    inkJumpOpts = jumpOpts
                    branch.metadata["s_per_red"] = seg7Strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                } else if (seg7Stroke) {
                    val jumpOpts = ContentExpandUtils.ExpandOptions(
                        maxFrac = 0.4f,
                        enableJump = true,
                        jumpFrac = 0.60f,
                        retractClearFrac = 0.30f,
                        energyRatio = 0.65f,
                    )
                    val tExp0 = System.currentTimeMillis()
                    val seeds = pdHunksRawTotal.map { h ->
                        android.graphics.Rect(
                            h.rect.left.toInt(), h.rect.top.toInt(),
                            h.rect.right.toInt(), h.rect.bottom.toInt(),
                        )
                    }
                    branch.metadata.remove("look_ink")
                    val segs = ArrayList<ContentExpandUtils.Seg7Expand>(seeds.size)
                    seeds.forEach { seed ->
                        val one = listOf(
                            ContentExpandUtils.Seg7Expand(
                                seed,
                                ContentExpandUtils.strokeWidthInSeed(workspace.p.mat, seed),
                                k = 0f,
                            ),
                        )
                        val seg = one.first()
                        segs.add(seg)
                    }
                    val walks = seeds.indices.map { i ->
                        Triple(seeds[i], segs[i].rect, segs[i].stroke)
                    }
                    val walked = walks.map { it.second }
                    val jumpedOnce = walked
                    fun inkBoxesFor(kk: Float): List<android.graphics.Rect> {
                        return walks.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                jumpedOnce[i], walks[i].first, kk,
                                walks[i].third.sPx, imgW, imgH,
                            )
                        }
                    }
                    val official = segs.map { it.rect }
                    makeGInkSweeps = segs.indices.map { i ->
                        segs[i].sweep?.withOfficial(official[i])
                    }
                    customBlueG = official.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    customOrangeG = emptyList()
                    seg7Strokes = walks.map { it.third }
                    inkWalkSeeds = walks.map { it.first }
                    inkWalkBoxes = jumpedOnce
                    inkWalkPads = official.map {
                        ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH)
                    }
                    inkJumpOpts = jumpOpts
                    branch.metadata["s_per_red"] = seg7Strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_vert_cap_frac"] = ContentExpandUtils.SEG7_VERT_CAP_FRAC.toString()
                    branch.metadata["seg7_gap_frac"] = gapFrac.toString()
                    branch.metadata["seg7_freeze_min_hs"] = minSeedHsToFreeze.toString()
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["seg7_retract_clear_frac"] = "0.30"
                    if (expandMode == 4) {
                        branch.metadata["content_expand_chroma"] = "color_adaptive"
                    } else if (expandMode == 3) {
                        branch.metadata["content_expand_chroma"] = "color3"
                    } else if (expandMode == 2) {
                        branch.metadata["content_expand_chroma"] = "color2"
                    } else if (expandMode == 1 || chromaExpand) {
                        branch.metadata["content_expand_chroma"] = "true"
                    }
                    if (boundStrategy != 0) {
                        branch.metadata["content_expand_bound"] =
                            if (boundStrategy == 2) "edge-retract" else "tight"
                    }
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                } else if (horizJump) {
                    val jumpOpts = ContentExpandUtils.ExpandOptions(
                        maxFrac = 0.4f,
                        enableJump = true,
                        jumpFrac = 0.40f,
                        retractClearFrac = 0.30f,
                        energyRatio = 0.65f,
                    )
                    val pads = ArrayList<android.graphics.Rect>(pdHunksRawTotal.size * gVertFactors.size)
                    val seedHs = IntArray(pdHunksRawTotal.size * gVertFactors.size)
                    var seedHsI = 0
                    pdHunksRawTotal.forEach { h ->
                        val r = android.graphics.Rect(
                            h.rect.left.toInt(), h.rect.top.toInt(),
                            h.rect.right.toInt(), h.rect.bottom.toInt(),
                        )
                        val sh = r.height().coerceAtLeast(1)
                        gVertFactors.forEach { v ->
                            pads.add(ContentExpandUtils.calculatedAabb(r, v, horiz = 0f, imgW, imgH))
                            seedHs[seedHsI++] = sh
                        }
                    }
                    val jumped = pads
                    customBlueG = jumped.map { j ->
                        PumpHunk(
                            "",
                            RectF(
                                j.left.toFloat(), j.top.toFloat(),
                                j.right.toFloat(), j.bottom.toFloat(),
                            ),
                        )
                    }
                    customOrangeG = emptyList()
                    seg7Strokes = emptyList()
                    inkWalkSeeds = emptyList()
                    inkWalkBoxes = emptyList()
                    inkWalkPads = emptyList()
                    inkJumpOpts = null
                } else {
                    val pair = createBlueAndOrangeHunksFromReds(
                        pdHunksRawTotal, imgW, imgH, gVertFactors, horizFactor)
                    customBlueG = pair.first
                    customOrangeG = pair.second
                    seg7Strokes = emptyList()
                    inkWalkSeeds = emptyList()
                    inkWalkBoxes = emptyList()
                    inkWalkPads = emptyList()
                    inkJumpOpts = null
                }
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
                if (!branch.metadata.containsKey("t_expand_ms")) {
                    branch.metadata["t_expand_ms"] = "0"
                }
                val gCands = buildRedBoxCandidates(
                    customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs, ocrG.digitsProbs, ocrG.recB64,
                    recWList = ocrG.recW, recHList = ocrG.recH,
                )
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet, paddleEngine, context, imgW, imgH, gCands)
                val redPixelG = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val cvG = PumpCostVolUtils.classifyCostVolFromBoxOcr(gCands)
                val inkVariants = JSONArray()
                var horizPadHunks: List<PumpHunk> = emptyList()
                if ((seg7Stroke || inkFn != null) && inkJumpOpts != null && inkWalkSeeds.isNotEmpty()) {
                    val opts = inkJumpOpts
                    fun inkRectsFor(kk: Float): List<android.graphics.Rect> {
                        return inkWalkSeeds.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                inkWalkBoxes[i], inkWalkSeeds[i], kk,
                                seg7Strokes[i].sPx, imgW, imgH,
                            )
                        }
                    }
                    val quads1 = customBluePixelG.map { ContentExpandUtils.orientedFromAabb(it) }
                    inkVariants.put(
                        ocrScaleVariantJson(
                            0f, customBluePixelG, quads1, gCands, cvG, kind = "ink",
                        ),
                    )
                    var nOcr = customBluePixelG.size
                    val skipExtraK = BooleanArray(gCands.size) { i ->
                        val asis = gCands[i].asis
                        asis.any { it.isLetter() } && asis.none { it.isDigit() }
                    }
                    branch.metadata["seg7_skip_extra_k_letter"] =
                        skipExtraK.count { it }.toString()
                    suspend fun emitHorizPad(
                        s: Float,
                        padRects: List<android.graphics.Rect>,
                        skip: BooleanArray?,
                    ): List<android.graphics.Rect> {
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        padRects.indices.forEach { i ->
                            if (skip == null || i >= skip.size || !skip[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(padRects[i])
                            }
                        }
                        val ocrP = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsP = padRects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                RedBoxOcrCandidate(
                                    "box${i + 1}", "", "",
                                    rect = padRects[i],
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrP.asis.getOrElse(j) { "" },
                                    ocrP.digits.getOrElse(j) { "" },
                                    ocrP.asisProbs.getOrElse(j) { "" },
                                    ocrP.digitsProbs.getOrElse(j) { "" },
                                    padRects[i],
                                    ocrP.recB64.getOrElse(j) { "" },
                                    ocrP.recW.getOrElse(j) { 0 },
                                    ocrP.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                        val quadsP = padRects.map { ContentExpandUtils.orientedFromAabb(it) }
                        inkVariants.put(
                            ocrScaleVariantJson(
                                s, padRects, quadsP, candsP, cvP, kind = "horiz_pad",
                            ),
                        )
                        return padRects
                    }
                    val pad0 = emitHorizPad(0f, inkWalkPads, null)
                    horizPadHunks = pad0.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    for (kk in listOf(1f, 2f, 3f, 4f)) {
                        val rects = inkRectsFor(kk)
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        rects.indices.forEach { i ->
                            if (i >= skipExtraK.size || !skipExtraK[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(rects[i])
                            }
                        }
                        val ocrK = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsK = rects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                val src = gCands.getOrElse(i) {
                                    RedBoxOcrCandidate("box${i + 1}", "", "")
                                }
                                src.copy(
                                    label = "box${i + 1}",
                                    rect = rects[i],
                                    recB64 = "",
                                    recW = 0,
                                    recH = 0,
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrK.asis.getOrElse(j) { "" },
                                    ocrK.digits.getOrElse(j) { "" },
                                    ocrK.asisProbs.getOrElse(j) { "" },
                                    ocrK.digitsProbs.getOrElse(j) { "" },
                                    rects[i],
                                    ocrK.recB64.getOrElse(j) { "" },
                                    ocrK.recW.getOrElse(j) { 0 },
                                    ocrK.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvK = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                        val quadsK = rects.map { ContentExpandUtils.orientedFromAabb(it) }
                        inkVariants.put(
                            ocrScaleVariantJson(
                                kk, rects, quadsK, candsK, cvK, kind = "ink",
                            ),
                        )
                        emitHorizPad(
                            kk,
                            rects.map { ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH) },
                            skipExtraK,
                        )
                    }
                    branch.metadata["n_ocr_energy"] = nOcr.toString()
                    val tOcrAll = (System.currentTimeMillis() - tOcr0).toString()
                    branch.metadata["t_ocr_ms"] = tOcrAll
                    branch.metadata["t_ocr_energy_ms"] = tOcrAll
                }
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelG,
                    ocrSourceRects = customBluePixelG,
                    candidates = gCands,
                    costCand = cvG.costCand,
                    volCand = cvG.volCand,
                    finalCost = cvG.cost,
                    finalVol = cvG.vol,
                    assembly = if (seg7Stroke || inkFn != null) mapOf(
                        "method" to "7seg_stroke",
                        "k" to listOf(0, 1, 2, 3, 4),
                        "kOfficial" to 0,
                        "vertCapFrac" to ContentExpandUtils.SEG7_VERT_CAP_FRAC,
                        "gapFrac" to gapFrac,
                        "minSeedHsToFreeze" to minSeedHsToFreeze,
                        "sPx" to seg7Strokes.map { it.sPx },
                        "vSW" to seg7Strokes.map { it.vSW },
                        "hSW" to seg7Strokes.map { it.hSW },
                        "inkFrac" to seg7Strokes.map { it.inkFrac },
                        "strokeShare" to seg7Strokes.map { it.strokeShare },
                        "maxRunOverW" to seg7Strokes.map { it.maxRunOverW },
                        "vhAgree" to seg7Strokes.map { it.vhAgree },
                        "darkInk" to seg7Strokes.map { it.darkInk },
                        "usedFallback" to seg7Strokes.map { it.usedFallback },
                        "droppedGlare" to seg7Strokes.map { it.droppedGlare },
                        "vertFactors" to emptyList<Float>(),
                        "horiz" to "jump",
                        "jumpFrac" to 0.60f,
                        "retractClearFrac" to 0.30f,
                        "energyRatio" to 0.65f,
                        "maxFrac" to 0.4f,
                        "maxRetractFrac" to 0.50f,
                        "heatmapBoxMode" to if (boxMode == NativeImageUtils.HEATMAP_BOX_AABB) "aabb" else "minAreaRect",
                        "hmThresh" to hmThresh,
                        "hmThreshNote" to (if (hmThresh <= 0f) "u8>=1" else if (kotlin.math.abs(hmThresh - HEAT_THR_U8_GE2) < 1e-6f) "u8>=2" else "custom"),
                        "maskDilatePasses" to maskDilatePasses,
                        "heatmapCellPx" to NativeImageUtils.PADDLE_DET_HEAT_CELL_PX,
                        "note" to assemblyNote,
                        "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                    ) else if (horizJump) mapOf(
                        "method" to "calculated",
                        "vertFactors" to gVertFactors,
                        "heightMultiples" to gVertFactors.map { 1f + 2f * it },
                        "horiz" to "jump",
                        "jumpFrac" to 0.40f,
                        "retractClearFrac" to 0.30f,
                        "energyRatio" to 0.65f,
                        "maxFrac" to 0.4f,
                        "maxRetractFrac" to 0.50f,
                        "heatmapBoxMode" to if (boxMode == NativeImageUtils.HEATMAP_BOX_AABB) "aabb" else "minAreaRect",
                        "hmThresh" to hmThresh,
                        "hmThreshNote" to (if (hmThresh <= 0f) "u8>=1" else if (kotlin.math.abs(hmThresh - HEAT_THR_U8_GE2) < 1e-6f) "u8>=2" else "custom"),
                        "maskDilatePasses" to maskDilatePasses,
                        "heatmapCellPx" to NativeImageUtils.PADDLE_DET_HEAT_CELL_PX,
                        "note" to assemblyNote
                    ) else mapOf(
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
                    oranges = orangePixelG,
                    scaleVariants = inkVariants,
                    inkSweeps = makeGInkSweeps,
                )
                doBOrDRedOnlyImage()
                val aPdG = if (horizJump || seg7Stroke || inkFn != null) {
                    getAnns(pdHunksRawTotal, AnnYuv.RED, 2) + getAnns(customBlueG, AnnYuv.BLUE, 4) +
                        getAnns(horizPadHunks, AnnYuv.BLUE, 4)
                } else {
                    getAnns(pdHunksRawTotal, AnnYuv.RED, 2) + getAnns(customBlueG, AnnYuv.BLUE, 4) + getAnns(customOrangeG, AnnYuv.ORANGE, 2)
                }
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
                fun makeInkAabbProc(
                    assemblyNote: String,
                    expandMany: (
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<android.graphics.Rect>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                    ) -> List<ContentExpandUtils.Seg7Expand>,
                    chromaNote: String? = null,
                    boundNote: String? = null,
                    boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                    dumpHeats: Boolean = false,
                    hmThresh: Float = HEAT_THR_U8_GE1,
                    expDetAsset: String? = null,
                    detScales: List<Int> = prodDetScales,
                ) = makeGProc(
                    emptyList(),
                    assemblyNote,
                    boxMode = boxMode,
                    dumpHeats = dumpHeats,
                    hmThresh = hmThresh,
                    expDetAsset = expDetAsset,
                    detScales = detScales,
                    inkExpand = expandMany,
                    chromaNote = chromaNote,
                    boundNote = boundNote,
                )
                val procGMinusMinus: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val pair = createBlueAndOrangeHunksFromReds(
                        pdHunksRawTotal, imgW, imgH,
                        SET_G_MINUS_MINUS_VERT_FACTORS, SET_G_HORIZ_FACTOR,
                    )
                    val customBlueG = pair.first
                    val customOrangeG = pair.second
                    val customBluePixelG = customBlueG.map { bh ->
                        android.graphics.Rect(
                            bh.rect.left.toInt(), bh.rect.top.toInt(),
                            bh.rect.right.toInt(), bh.rect.bottom.toInt(),
                        )
                    }
                    val orangePixelG = customOrangeG.map { bh ->
                        android.graphics.Rect(
                            bh.rect.left.toInt(), bh.rect.top.toInt(),
                            bh.rect.right.toInt(), bh.rect.bottom.toInt(),
                        )
                    }
                    val tOcr0 = System.currentTimeMillis()
                    val ocrG = ocrPumpRectsAsisAndDigits(customBluePixelG)
                    val tOcr = (System.currentTimeMillis() - tOcr0).toString()
                    branch.metadata["t_ocr_ms"] = tOcr
                    branch.metadata["n_ocr_g"] = customBluePixelG.size.toString()
                    branch.metadata["t_ocr_g_ms"] = tOcr
                    branch.metadata["t_expand_ms"] = "0"
                    val gCands = buildRedBoxCandidates(
                        customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs,
                        ocrG.digitsProbs, ocrG.recB64,
                        recWList = ocrG.recW, recHList = ocrG.recH,
                    )
                    branch.pathResults["Paddle"] = getFinal(
                        customBlueG, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, gCands,
                    )
                    val redPixelG = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
                        )
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
                            "vertFactors" to SET_G_MINUS_MINUS_VERT_FACTORS,
                            "heightMultiples" to SET_G_MINUS_MINUS_VERT_FACTORS.map { 1f + 2f * it },
                            "horizFactor" to SET_G_HORIZ_FACTOR,
                            "orangeSideExt" to 0.1,
                            "heatmapBoxMode" to "aabb",
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "maskDilatePasses" to 0,
                            "heatmapCellPx" to NativeImageUtils.PADDLE_DET_HEAT_CELL_PX,
                            "note" to "G-- AABB det; calculated pads; no 7-seg",
                        ),
                        oranges = orangePixelG,
                    )
                    val aPdG = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(customBlueG, AnnYuv.BLUE, 4) +
                        getAnns(customOrangeG, AnnYuv.ORANGE, 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPdG, null, workspace,
                    ).first
                }
                val procInkEnergyTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = "0"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_mode"] =
                        ContentExpandUtils.Mode.INTERIOR_ENERGY.name
                    branch.metadata["content_expand_bound"] = "tight"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = 0,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val tExpand0 = System.currentTimeMillis()
                    NativeImageUtils.fillEnergyLookU8(
                        NativePaddleEngine.bufferSetA.p.mat,
                        NativePaddleEngine.bufferSetA.s.mat,
                    )
                    val expDiag = ContentExpandUtils.expandEnergyAabbTight(
                        NativePaddleEngine.bufferSetA.p.mat,
                        null,
                        redPixelList,
                    )
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    snapshotLookInk(
                        redPixelList, expDiag.map { it.rect }, imgW, imgH, branch,
                        emptyList(), expDiag.map { it.tele }, expDiag.map { it.sweep },
                        emptyList(),
                        reportDir, timestamp, fullRow, branch.name,
                        source = NativePaddleEngine.bufferSetA.s.mat,
                        scratchYuv = NativePaddleEngine.bufferSetB,
                        energyLook = true,
                    )
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetA.s.mat,
                        NativePaddleEngine.bufferSetB,
                        branch,
                        energyVis = true,
                    )
                    val expandedBase = expDiag.map { it.rect }
                    val hitCaps = expDiag.map { it.hitVertCap }
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedBase.size.toString()
                    storeSeg7Tele(branch, expDiag.map { it.tele })
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
                    val aabbHorizPad = expandedBase.map {
                        ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH)
                    }
                    val aabbPadOcr = ocrPumpRectsAsisAndDigits(aabbHorizPad)
                    val aabbPadCands = buildRedBoxCandidates(
                        aabbHorizPad, aabbPadOcr.asis, aabbPadOcr.digits,
                        aabbPadOcr.asisProbs, aabbPadOcr.digitsProbs, aabbPadOcr.recB64,
                        recWList = aabbPadOcr.recW, recHList = aabbPadOcr.recH,
                    )
                    val aabbPadCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(aabbPadCands)
                    variants.put(
                        ocrScaleVariantJson(
                            0.5f, aabbHorizPad, emptyList(), aabbPadCands, aabbPadCv,
                            kind = "horiz_pad",
                        ),
                    )
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = tOcrE.toString()
                    val blueHunks = expandedBase.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    branch.pathResults["Paddle"] = getFinal(
                        blueHunks, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, energyCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelList,
                        ocrSourceRects = expandedBase,
                        candidates = energyCands,
                        costCand = energyCv.costCand,
                        volCand = energyCv.volCand,
                        finalCost = energyCv.cost,
                        finalVol = energyCv.vol,
                        assembly = mapOf(
                            "method" to "content_expand",
                            "contentExpandMode" to ContentExpandUtils.Mode.INTERIOR_ENERGY.name,
                            "heatmapBoxMode" to "aabb",
                            "heatmapGrowCells" to 0,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "finalKind" to "energy",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to 0.65f,
                            "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                            "maxRetractFrac" to 0.50f,
                            "note" to "ink-energy-tight: AABB grow 0; V then H; rec-pad look-ink",
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        scaleVariants = variants,
                        inkSweeps = expDiag.indices.map { i ->
                            val r = expandedBase.getOrNull(i) ?: return@map expDiag[i].sweep
                            expDiag[i].sweep?.withOfficial(r)
                        },
                    )
                    val padBlueHunks = aabbHorizPad.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(blueHunks, AnnYuv.BLUE, 4) +
                        getAnns(padBlueHunks, AnnYuv.BLUE, 4)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procInkEnergyRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = "1"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_mode"] =
                        ContentExpandUtils.Mode.INTERIOR_ENERGY.name
                    branch.metadata["content_expand_bound"] = "edge-retract"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = 1,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val tExpand0 = System.currentTimeMillis()
                    NativeImageUtils.fillEnergyLookU8(
                        NativePaddleEngine.bufferSetA.p.mat,
                        NativePaddleEngine.bufferSetA.s.mat,
                    )
                    val expDiag = ContentExpandUtils.expandEnergyAabbRetract(
                        NativePaddleEngine.bufferSetA.p.mat,
                        null,
                        redPixelList,
                    )
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    snapshotLookInk(
                        redPixelList, expDiag.map { it.rect }, imgW, imgH, branch,
                        emptyList(), expDiag.map { it.tele }, expDiag.map { it.sweep },
                        emptyList(),
                        reportDir, timestamp, fullRow, branch.name,
                        source = NativePaddleEngine.bufferSetA.s.mat,
                        scratchYuv = NativePaddleEngine.bufferSetB,
                        energyLook = true,
                    )
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetA.s.mat,
                        NativePaddleEngine.bufferSetB,
                        branch,
                        energyVis = true,
                    )
                    val expandedBase = expDiag.map { it.rect }
                    val hitCaps = expDiag.map { it.hitVertCap }
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedBase.size.toString()
                    storeSeg7Tele(branch, expDiag.map { it.tele })
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
                    val aabbHorizPad = expandedBase.map {
                        ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH)
                    }
                    val aabbPadOcr = ocrPumpRectsAsisAndDigits(aabbHorizPad)
                    val aabbPadCands = buildRedBoxCandidates(
                        aabbHorizPad, aabbPadOcr.asis, aabbPadOcr.digits,
                        aabbPadOcr.asisProbs, aabbPadOcr.digitsProbs, aabbPadOcr.recB64,
                        recWList = aabbPadOcr.recW, recHList = aabbPadOcr.recH,
                    )
                    val aabbPadCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(aabbPadCands)
                    variants.put(
                        ocrScaleVariantJson(
                            0.5f, aabbHorizPad, emptyList(), aabbPadCands, aabbPadCv,
                            kind = "horiz_pad",
                        ),
                    )
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = tOcrE.toString()
                    val blueHunks = expandedBase.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    branch.pathResults["Paddle"] = getFinal(
                        blueHunks, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, energyCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelList,
                        ocrSourceRects = expandedBase,
                        candidates = energyCands,
                        costCand = energyCv.costCand,
                        volCand = energyCv.volCand,
                        finalCost = energyCv.cost,
                        finalVol = energyCv.vol,
                        assembly = mapOf(
                            "method" to "content_expand",
                            "contentExpandMode" to ContentExpandUtils.Mode.INTERIOR_ENERGY.name,
                            "heatmapBoxMode" to "aabb",
                            "heatmapGrowCells" to 1,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "finalKind" to "energy",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to 0.65f,
                            "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                            "maxRetractFrac" to 0.50f,
                            "note" to "ink-energy-retract: AABB grow 1; V retract-or-expand then H; rec-pad look-ink",
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        scaleVariants = variants,
                        inkSweeps = expDiag.indices.map { i ->
                            val r = expandedBase.getOrNull(i) ?: return@map expDiag[i].sweep
                            expDiag[i].sweep?.withOfficial(r)
                        },
                    )
                    val padBlueHunks = aabbHorizPad.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(blueHunks, AnnYuv.BLUE, 4) +
                        getAnns(padBlueHunks, AnnYuv.BLUE, 4)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procInkGrayTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = "0"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = "tight"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        ProcessMemProbe.log("gray-tight before_det scale=$scale")
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = 0,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val tExp0 = System.currentTimeMillis()
                    val seeds = redPixelList
                    branch.metadata.remove("look_ink")
                    masterBuffer.s.clear()
                    NativePaddleEngine.bufferSetB.p.clear()
                    val segs = ArrayList<ContentExpandUtils.Seg7Expand>(seeds.size)
                    var nextInk = 255
                    var nextNon = 1
                    var inkLo = 255
                    seeds.forEachIndexed { si, seed ->

                        val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                        if (poisonBuf.size >= 3) {
                            poisonBuf[0] = nextInk
                            poisonBuf[1] = nextNon
                            poisonBuf[2] = inkLo
                        }
                        ProcessMemProbe.log("gray-tight before_expand seed=$si")
                        val one = ContentExpandUtils.expandGrayAabbTight(
                            NativePaddleEngine.bufferSetA.p.mat,
                            NativePaddleEngine.bufferSetB.s.mat,
                            listOf(seed),
                            NativePaddleEngine.bufferSetA.s.mat,
                            masterBuffer.s.mat,
                            NativePaddleEngine.bufferSetB.p.mat,
                            NativePaddleEngine.bufferSetB.p.uvMat,
                            poisonBuf,
                        )
                        val seg = one.first()
                        segs.add(seg)
                        if (poisonBuf.size >= 3) {
                            nextInk = poisonBuf[poisonBuf.size - 3]
                            nextNon = poisonBuf[poisonBuf.size - 2]
                            inkLo = poisonBuf[poisonBuf.size - 1]
                        }
                        val pd = seg.poison
                        if (pd?.classChange == true) {
                            val dumpSeed = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}.png")
                            dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                            branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                        }
                        if (pd?.bandH == -1) {
                            recordIncompleteLookIds(
                                NativePaddleEngine.bufferSetA.s.mat,
                                objImgRoot, fullRow, col, si, seeds.size, pd, branch, onLog,
                            )
                        }
                        snapshotLookInk(
                            listOf(seed), listOf(seg.rect), imgW, imgH, branch,
                            listOf(seg.poison),
                            listOf(seg.tele),
                            listOf(seg.sweep),
                            listOf(seg.stroke),
                            reportDir, timestamp, fullRow, branch.name,
                            source = NativePaddleEngine.bufferSetB.p,
                            scratchYuv = NativePaddleEngine.bufferSetB,
                        )
                    }
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetB.p,
                        workspace,
                        branch,
                    )
                    val dumpFinal = File(objImgRoot, "r${fullRow}_c${col}_final.png")
                    dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                    branch.metadata["object_dump_final"] = dumpFinal.name
                    val walks = segs.indices.map { i ->
                        Triple(seeds[i], segs[i].rect, segs[i].stroke)
                    }
                    val jumpedOnce = walks.map { it.second }
                    fun inkBoxesFor(kk: Float): List<android.graphics.Rect> {
                        return walks.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                jumpedOnce[i], walks[i].first, kk,
                                walks[i].third.sPx, imgW, imgH,
                            )
                        }
                    }
                    val official = segs.map { it.rect }
                    val officialHunks = official.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    val strokes = walks.map { it.third }
                    branch.metadata["s_per_red"] = strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                    val variants = JSONArray()
                    val tOcr0 = System.currentTimeMillis()
                    val ocr0 = ocrPumpRectsAsisAndDigits(official)
                    val cands0 = buildRedBoxCandidates(
                        official, ocr0.asis, ocr0.digits, ocr0.asisProbs, ocr0.digitsProbs,
                        ocr0.recB64, recWList = ocr0.recW, recHList = ocr0.recH,
                    )
                    val cv0 = PumpCostVolUtils.classifyCostVolFromBoxOcr(cands0)
                    variants.put(
                        ocrScaleVariantJson(
                            0f, official, official.map { ContentExpandUtils.orientedFromAabb(it) },
                            cands0, cv0, kind = "ink",
                        ),
                    )
                    var nOcr = official.size
                    val skipExtraK = BooleanArray(cands0.size) { i ->
                        val asis = cands0[i].asis
                        asis.any { it.isLetter() } && asis.none { it.isDigit() }
                    }
                    branch.metadata["seg7_skip_extra_k_letter"] =
                        skipExtraK.count { it }.toString()
                    suspend fun emitHorizPad(
                        s: Float,
                        padRects: List<android.graphics.Rect>,
                        skip: BooleanArray?,
                    ): List<android.graphics.Rect> {
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        padRects.indices.forEach { i ->
                            if (skip == null || i >= skip.size || !skip[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(padRects[i])
                            }
                        }
                        val ocrP = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsP = padRects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                RedBoxOcrCandidate("box${i + 1}", "", "", rect = padRects[i])
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrP.asis.getOrElse(j) { "" },
                                    ocrP.digits.getOrElse(j) { "" },
                                    ocrP.asisProbs.getOrElse(j) { "" },
                                    ocrP.digitsProbs.getOrElse(j) { "" },
                                    padRects[i],
                                    ocrP.recB64.getOrElse(j) { "" },
                                    ocrP.recW.getOrElse(j) { 0 },
                                    ocrP.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                        variants.put(
                            ocrScaleVariantJson(
                                s, padRects,
                                padRects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsP, cvP, kind = "horiz_pad",
                            ),
                        )
                        return padRects
                    }
                    val pad0 = emitHorizPad(0f, segs.map { it.rectPad }, null)
                    val padHunks = pad0.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    for (kk in listOf(1f, 2f, 3f, 4f)) {
                        val rects = inkBoxesFor(kk)
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        rects.indices.forEach { i ->
                            if (i >= skipExtraK.size || !skipExtraK[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(rects[i])
                            }
                        }
                        val ocrK = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsK = rects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                val src = cands0.getOrElse(i) {
                                    RedBoxOcrCandidate("box${i + 1}", "", "")
                                }
                                src.copy(
                                    label = "box${i + 1}",
                                    rect = rects[i],
                                    recB64 = "",
                                    recW = 0,
                                    recH = 0,
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrK.asis.getOrElse(j) { "" },
                                    ocrK.digits.getOrElse(j) { "" },
                                    ocrK.asisProbs.getOrElse(j) { "" },
                                    ocrK.digitsProbs.getOrElse(j) { "" },
                                    rects[i],
                                    ocrK.recB64.getOrElse(j) { "" },
                                    ocrK.recW.getOrElse(j) { 0 },
                                    ocrK.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvK = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                        variants.put(
                            ocrScaleVariantJson(
                                kk, rects, rects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsK, cvK, kind = "ink",
                            ),
                        )
                        emitHorizPad(
                            kk,
                            rects.map { ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH) },
                            skipExtraK,
                        )
                    }
                    branch.metadata["n_ocr"] = nOcr.toString()
                    val tOcrAll = (System.currentTimeMillis() - tOcr0).toString()
                    branch.metadata["t_ocr_ms"] = tOcrAll
                    branch.pathResults["Paddle"] = getFinal(
                        officialHunks, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, cands0,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelList,
                        ocrSourceRects = official,
                        candidates = cands0,
                        costCand = cv0.costCand,
                        volCand = cv0.volCand,
                        finalCost = cv0.cost,
                        finalVol = cv0.vol,
                        assembly = mapOf(
                            "method" to "7seg_stroke",
                            "k" to listOf(0, 1, 2, 3, 4),
                            "kOfficial" to 0,
                            "heatmapBoxMode" to "aabb",
                            "heatmapGrowCells" to 0,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "sPx" to strokes.map { it.sPx },
                            "jumpFrac" to 0.60f,
                            "note" to "ink-gray-tight: AABB grow 0; overlay look-ink rec-pad; k=0..4",
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        scaleVariants = variants,
                        inkSweeps = segs.indices.map { i ->
                            segs[i].sweep?.withOfficial(official.getOrNull(i) ?: segs[i].rect)
                        },
                    )
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(officialHunks, AnnYuv.BLUE, 4) +
                        getAnns(padHunks, AnnYuv.BLUE, 4)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procInkGrayRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = "1"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = "edge-retract"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = 1,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val tExp0 = System.currentTimeMillis()
                    val seeds = redPixelList
                    branch.metadata.remove("look_ink")
                    masterBuffer.s.clear()
                    NativePaddleEngine.bufferSetB.p.clear()
                    val segs = ArrayList<ContentExpandUtils.Seg7Expand>(seeds.size)
                    var nextInk = 255
                    var nextNon = 1
                    var inkLo = 255
                    seeds.forEachIndexed { si, seed ->

                        val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                        if (poisonBuf.size >= 3) {
                            poisonBuf[0] = nextInk
                            poisonBuf[1] = nextNon
                            poisonBuf[2] = inkLo
                        }
                        val one = ContentExpandUtils.expandGrayAabbRetract(
                            NativePaddleEngine.bufferSetA.p.mat,
                            NativePaddleEngine.bufferSetB.s.mat,
                            listOf(seed),
                            NativePaddleEngine.bufferSetA.s.mat,
                            masterBuffer.s.mat,
                            NativePaddleEngine.bufferSetB.p.mat,
                            NativePaddleEngine.bufferSetB.p.uvMat,
                            poisonBuf,
                        )
                        val seg = one.first()
                        segs.add(seg)
                        if (poisonBuf.size >= 3) {
                            nextInk = poisonBuf[poisonBuf.size - 3]
                            nextNon = poisonBuf[poisonBuf.size - 2]
                            inkLo = poisonBuf[poisonBuf.size - 1]
                        }
                        val pd = seg.poison
                        if (pd?.classChange == true) {
                            val dumpSeed = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}.png")
                            dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                            branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                        }
                        if (pd?.bandH == -1) {
                            recordIncompleteLookIds(
                                NativePaddleEngine.bufferSetA.s.mat,
                                objImgRoot, fullRow, col, si, seeds.size, pd, branch, onLog,
                            )
                        }
                        snapshotLookInk(
                            listOf(seed), listOf(seg.rect), imgW, imgH, branch,
                            listOf(seg.poison),
                            listOf(seg.tele),
                            listOf(seg.sweep),
                            listOf(seg.stroke),
                            reportDir, timestamp, fullRow, branch.name,
                            source = NativePaddleEngine.bufferSetB.p,
                            scratchYuv = NativePaddleEngine.bufferSetB,
                        )
                    }
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetB.p,
                        workspace,
                        branch,
                    )
                    val dumpFinal = File(objImgRoot, "r${fullRow}_c${col}_final.png")
                    dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                    branch.metadata["object_dump_final"] = dumpFinal.name
                    val walks = segs.indices.map { i ->
                        Triple(seeds[i], segs[i].rect, segs[i].stroke)
                    }
                    val jumpedOnce = walks.map { it.second }
                    fun inkBoxesFor(kk: Float): List<android.graphics.Rect> {
                        return walks.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                jumpedOnce[i], walks[i].first, kk,
                                walks[i].third.sPx, imgW, imgH,
                            )
                        }
                    }
                    val official = segs.map { it.rect }
                    val officialHunks = official.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    val strokes = walks.map { it.third }
                    branch.metadata["s_per_red"] = strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                    val variants = JSONArray()
                    val tOcr0 = System.currentTimeMillis()
                    val ocr0 = ocrPumpRectsAsisAndDigits(official)
                    val cands0 = buildRedBoxCandidates(
                        official, ocr0.asis, ocr0.digits, ocr0.asisProbs, ocr0.digitsProbs,
                        ocr0.recB64, recWList = ocr0.recW, recHList = ocr0.recH,
                    )
                    val cv0 = PumpCostVolUtils.classifyCostVolFromBoxOcr(cands0)
                    variants.put(
                        ocrScaleVariantJson(
                            0f, official, official.map { ContentExpandUtils.orientedFromAabb(it) },
                            cands0, cv0, kind = "ink",
                        ),
                    )
                    var nOcr = official.size
                    val skipExtraK = BooleanArray(cands0.size) { i ->
                        val asis = cands0[i].asis
                        asis.any { it.isLetter() } && asis.none { it.isDigit() }
                    }
                    branch.metadata["seg7_skip_extra_k_letter"] =
                        skipExtraK.count { it }.toString()
                    suspend fun emitHorizPad(
                        s: Float,
                        padRects: List<android.graphics.Rect>,
                        skip: BooleanArray?,
                    ): List<android.graphics.Rect> {
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        padRects.indices.forEach { i ->
                            if (skip == null || i >= skip.size || !skip[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(padRects[i])
                            }
                        }
                        val ocrP = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsP = padRects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                RedBoxOcrCandidate("box${i + 1}", "", "", rect = padRects[i])
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrP.asis.getOrElse(j) { "" },
                                    ocrP.digits.getOrElse(j) { "" },
                                    ocrP.asisProbs.getOrElse(j) { "" },
                                    ocrP.digitsProbs.getOrElse(j) { "" },
                                    padRects[i],
                                    ocrP.recB64.getOrElse(j) { "" },
                                    ocrP.recW.getOrElse(j) { 0 },
                                    ocrP.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                        variants.put(
                            ocrScaleVariantJson(
                                s, padRects,
                                padRects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsP, cvP, kind = "horiz_pad",
                            ),
                        )
                        return padRects
                    }
                    val pad0 = emitHorizPad(0f, segs.map { it.rectPad }, null)
                    val padHunks = pad0.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    for (kk in listOf(1f, 2f, 3f, 4f)) {
                        val rects = inkBoxesFor(kk)
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        rects.indices.forEach { i ->
                            if (i >= skipExtraK.size || !skipExtraK[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(rects[i])
                            }
                        }
                        val ocrK = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsK = rects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                val src = cands0.getOrElse(i) {
                                    RedBoxOcrCandidate("box${i + 1}", "", "")
                                }
                                src.copy(
                                    label = "box${i + 1}",
                                    rect = rects[i],
                                    recB64 = "",
                                    recW = 0,
                                    recH = 0,
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrK.asis.getOrElse(j) { "" },
                                    ocrK.digits.getOrElse(j) { "" },
                                    ocrK.asisProbs.getOrElse(j) { "" },
                                    ocrK.digitsProbs.getOrElse(j) { "" },
                                    rects[i],
                                    ocrK.recB64.getOrElse(j) { "" },
                                    ocrK.recW.getOrElse(j) { 0 },
                                    ocrK.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvK = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                        variants.put(
                            ocrScaleVariantJson(
                                kk, rects, rects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsK, cvK, kind = "ink",
                            ),
                        )
                        emitHorizPad(
                            kk,
                            rects.map { ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH) },
                            skipExtraK,
                        )
                    }
                    branch.metadata["n_ocr"] = nOcr.toString()
                    val tOcrAll = (System.currentTimeMillis() - tOcr0).toString()
                    branch.metadata["t_ocr_ms"] = tOcrAll
                    branch.pathResults["Paddle"] = getFinal(
                        officialHunks, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, cands0,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelList,
                        ocrSourceRects = official,
                        candidates = cands0,
                        costCand = cv0.costCand,
                        volCand = cv0.volCand,
                        finalCost = cv0.cost,
                        finalVol = cv0.vol,
                        assembly = mapOf(
                            "method" to "7seg_stroke",
                            "k" to listOf(0, 1, 2, 3, 4),
                            "kOfficial" to 0,
                            "heatmapBoxMode" to "aabb",
                            "heatmapGrowCells" to 1,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "sPx" to strokes.map { it.sPx },
                            "jumpFrac" to 0.60f,
                            "note" to "ink-gray-retract: AABB grow 1; V retract-or-expand then H; overlay look-ink rec-pad; k=0..4",
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        scaleVariants = variants,
                        inkSweeps = segs.indices.map { i ->
                            segs[i].sweep?.withOfficial(official.getOrNull(i) ?: segs[i].rect)
                        },
                    )
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(officialHunks, AnnYuv.BLUE, 4) +
                        getAnns(padHunks, AnnYuv.BLUE, 4)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                suspend fun runAabb7segColumn(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    imgW: Int,
                    imgH: Int,
                    growCells: Int,
                    boundNote: String,
                    inkExpandFn: (
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<android.graphics.Rect>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                    ) -> List<ContentExpandUtils.Seg7Expand>,
                    note: String,
                ) {
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = growCells.toString()
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = boundNote
                    branch.metadata["content_expand_chroma"] = "color_adaptive"
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = growCells,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    val tExp0 = System.currentTimeMillis()
                    val seeds = redPixelList
                    branch.metadata.remove("look_ink")
                    masterBuffer.s.clear()
                    NativePaddleEngine.bufferSetB.p.clear()
                    val segs = ArrayList<ContentExpandUtils.Seg7Expand>(seeds.size)
                    var nextInk = 255
                    var nextNon = 1
                    var inkLo = 255
                    seeds.forEachIndexed { si, seed ->

                        val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                        if (poisonBuf.size >= 3) {
                            poisonBuf[0] = nextInk
                            poisonBuf[1] = nextNon
                            poisonBuf[2] = inkLo
                        }
                        val one = inkExpandFn(
                            NativePaddleEngine.bufferSetA.p.mat,
                            NativePaddleEngine.bufferSetA.p.uvMat,
                            listOf(seed),
                            NativePaddleEngine.bufferSetA.s.mat,
                            masterBuffer.s.mat,
                            NativePaddleEngine.bufferSetB.p.mat,
                            NativePaddleEngine.bufferSetB.s.mat,
                            poisonBuf,
                        )
                        val seg = one.first()
                        segs.add(seg)
                        if (poisonBuf.size >= 3) {
                            nextInk = poisonBuf[poisonBuf.size - 3]
                            nextNon = poisonBuf[poisonBuf.size - 2]
                            inkLo = poisonBuf[poisonBuf.size - 1]
                        }
                        val pd = seg.poison
                        if (pd?.classChange == true) {
                            val dumpSeed = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}.png")
                            dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                            branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                        }
                        if (pd?.bandH == -1) {
                            recordIncompleteLookIds(
                                NativePaddleEngine.bufferSetA.s.mat,
                                objImgRoot, fullRow, col, si, seeds.size, pd, branch, onLog,
                            )
                        }
                        snapshotLookInk(
                            listOf(seed), listOf(seg.rect), imgW, imgH, branch,
                            listOf(seg.poison),
                            listOf(seg.tele),
                            listOf(seg.sweep),
                            listOf(seg.stroke),
                            reportDir, timestamp, fullRow, branch.name,
                            source = NativePaddleEngine.bufferSetB.p,
                            scratchYuv = NativePaddleEngine.bufferSetB,
                        )
                    }
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetB.p,
                        workspace,
                        branch,
                    )
                    val dumpFinal = File(objImgRoot, "r${fullRow}_c${col}_final.png")
                    dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                    branch.metadata["object_dump_final"] = dumpFinal.name
                    val walks = segs.indices.map { i ->
                        Triple(seeds[i], segs[i].rect, segs[i].stroke)
                    }
                    val jumpedOnce = walks.map { it.second }
                    fun inkBoxesFor(kk: Float): List<android.graphics.Rect> {
                        return walks.indices.map { i ->
                            ContentExpandUtils.padVertByStrokes(
                                jumpedOnce[i], walks[i].first, kk,
                                walks[i].third.sPx, imgW, imgH,
                            )
                        }
                    }
                    val official = segs.map { it.rect }
                    val officialHunks = official.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    val strokes = walks.map { it.third }
                    branch.metadata["s_per_red"] = strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                    val variants = JSONArray()
                    val tOcr0 = System.currentTimeMillis()
                    val ocr0 = ocrPumpRectsAsisAndDigits(official)
                    val cands0 = buildRedBoxCandidates(
                        official, ocr0.asis, ocr0.digits, ocr0.asisProbs, ocr0.digitsProbs,
                        ocr0.recB64, recWList = ocr0.recW, recHList = ocr0.recH,
                    )
                    val cv0 = PumpCostVolUtils.classifyCostVolFromBoxOcr(cands0)
                    variants.put(
                        ocrScaleVariantJson(
                            0f, official, official.map { ContentExpandUtils.orientedFromAabb(it) },
                            cands0, cv0, kind = "ink",
                        ),
                    )
                    var nOcr = official.size
                    val skipExtraK = BooleanArray(cands0.size) { i ->
                        val asis = cands0[i].asis
                        asis.any { it.isLetter() } && asis.none { it.isDigit() }
                    }
                    branch.metadata["seg7_skip_extra_k_letter"] =
                        skipExtraK.count { it }.toString()
                    suspend fun emitHorizPad(
                        s: Float,
                        padRects: List<android.graphics.Rect>,
                        skip: BooleanArray?,
                    ): List<android.graphics.Rect> {
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        padRects.indices.forEach { i ->
                            if (skip == null || i >= skip.size || !skip[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(padRects[i])
                            }
                        }
                        val ocrP = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsP = padRects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                RedBoxOcrCandidate("box${i + 1}", "", "", rect = padRects[i])
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrP.asis.getOrElse(j) { "" },
                                    ocrP.digits.getOrElse(j) { "" },
                                    ocrP.asisProbs.getOrElse(j) { "" },
                                    ocrP.digitsProbs.getOrElse(j) { "" },
                                    padRects[i],
                                    ocrP.recB64.getOrElse(j) { "" },
                                    ocrP.recW.getOrElse(j) { 0 },
                                    ocrP.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                        variants.put(
                            ocrScaleVariantJson(
                                s, padRects,
                                padRects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsP, cvP, kind = "horiz_pad",
                            ),
                        )
                        return padRects
                    }
                    val pad0 = emitHorizPad(0f, segs.map { it.rectPad }, null)
                    val padHunks = pad0.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    for (kk in listOf(1f, 2f, 3f, 4f)) {
                        val rects = inkBoxesFor(kk)
                        val ocrIdx = ArrayList<Int>()
                        val ocrRects = ArrayList<android.graphics.Rect>()
                        rects.indices.forEach { i ->
                            if (i >= skipExtraK.size || !skipExtraK[i]) {
                                ocrIdx.add(i)
                                ocrRects.add(rects[i])
                            }
                        }
                        val ocrK = if (ocrRects.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpRectsAsisAndDigits(ocrRects)
                        }
                        nOcr += ocrRects.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsK = rects.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                val src = cands0.getOrElse(i) {
                                    RedBoxOcrCandidate("box${i + 1}", "", "")
                                }
                                src.copy(
                                    label = "box${i + 1}",
                                    rect = rects[i],
                                    recB64 = "",
                                    recW = 0,
                                    recH = 0,
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrK.asis.getOrElse(j) { "" },
                                    ocrK.digits.getOrElse(j) { "" },
                                    ocrK.asisProbs.getOrElse(j) { "" },
                                    ocrK.digitsProbs.getOrElse(j) { "" },
                                    rects[i],
                                    ocrK.recB64.getOrElse(j) { "" },
                                    ocrK.recW.getOrElse(j) { 0 },
                                    ocrK.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvK = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                        variants.put(
                            ocrScaleVariantJson(
                                kk, rects, rects.map { ContentExpandUtils.orientedFromAabb(it) },
                                candsK, cvK, kind = "ink",
                            ),
                        )
                        emitHorizPad(
                            kk,
                            rects.map { ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH) },
                            skipExtraK,
                        )
                    }
                    branch.metadata["n_ocr"] = nOcr.toString()
                    val tOcrAll = (System.currentTimeMillis() - tOcr0).toString()
                    branch.metadata["t_ocr_ms"] = tOcrAll
                    branch.pathResults["Paddle"] = getFinal(
                        officialHunks, "Paddle", tilt, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, cands0,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = redPixelList,
                        ocrSourceRects = official,
                        candidates = cands0,
                        costCand = cv0.costCand,
                        volCand = cv0.volCand,
                        finalCost = cv0.cost,
                        finalVol = cv0.vol,
                        assembly = mapOf(
                            "method" to "7seg_stroke",
                            "k" to listOf(0, 1, 2, 3, 4),
                            "kOfficial" to 0,
                            "heatmapBoxMode" to "aabb",
                            "heatmapGrowCells" to growCells,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "sPx" to strokes.map { it.sPx },
                            "jumpFrac" to 0.60f,
                            "chroma" to "color_adaptive",
                            "note" to note,
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        scaleVariants = variants,
                        inkSweeps = segs.indices.map { i ->
                            segs[i].sweep?.withOfficial(official.getOrNull(i) ?: segs[i].rect)
                        },
                    )
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                        getAnns(officialHunks, AnnYuv.BLUE, 4) +
                        getAnns(padHunks, AnnYuv.BLUE, 4)
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procInkColorTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runAabb7segColumn(
                        ws, br, det, w, h, 0, "tight",
                        ContentExpandUtils::expandColorAabbTight,
                        "ink-color-tight: AABB grow 0; tint A.s; look B.s; overlay B.p; k=0..4",
                    )
                }
                val procInkColorRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runAabb7segColumn(
                        ws, br, det, w, h, 1, "edge-retract",
                        ContentExpandUtils::expandColorAabbRetract,
                        "ink-color-retract: AABB grow 1; V retract-or-expand then H; tint A.s; overlay look-ink rec-pad; k=0..4",
                    )
                }


                /** Warp source gray quad → A.s crop, RecBufferFeed that crop into rec 48. */
                suspend fun ocrPumpOrientedFlattenAp(
                    quads: List<ContentExpandUtils.OrientedQuad>,
                    gray: org.opencv.core.Mat,
                ): PumpRectOcrLists {
                    data class OcrOne(
                        val asis: Pair<String, String>,
                        val digits: Pair<String, String>,
                        val snap: String,
                        val recW: Int,
                        val recH: Int,
                    )
                    suspend fun ocrOne(q: ContentExpandUtils.OrientedQuad): OcrOne {
                        if (q.shortAxisBh() < 2f || q.longAxisBw() < 2f) {
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val contentH = RecBufferFeed.DEFAULT_REC_H - 2 * RecBufferFeed.DEFAULT_BORDER_PX
                        val pad = ceil(
                            RecBufferFeed.DEFAULT_BORDER_PX.toDouble() * q.shortAxisBh() / contentH.toDouble(),
                        ).toInt()
                        val qPad = q.padUv(pad)
                        val nativeH = qPad.shortAxisBh().roundToInt().coerceAtLeast(1)
                        val nativeW = qPad.longAxisBw().roundToInt().coerceAtLeast(1)
                            .coerceAtMost(NativePaddleEngine.REC_CANVAS_W)
                        val ap = NativePaddleEngine.bufferSetA
                        val nativeId = ap.s.createCrop(0, 0, nativeW, nativeH)
                        val dest = ap.c[nativeId]
                        dest.clear()
                        val nativeMat = dest.mat
                        val ok = ContentExpandUtils.warpQuadToHorizontalStrip(
                            gray, qPad, nativeMat, targetH = 0,
                        )
                        if (!ok || nativeMat.empty()) {
                            ap.c[nativeId].release()
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val fed = RecBufferFeed.feedSourceBorderHeightStrip(
                            nativeMat, 0, 0, nativeMat.cols(), nativeMat.rows(),
                            experimentRecSet,
                            targetH = RecBufferFeed.DEFAULT_REC_H,
                        )
                        ap.c[nativeId].release()
                        val snap = PumpCostVolUtils.snapRecCrop(
                            experimentRecSet, fed.recCropId, fed.targetW, fed.targetH,
                        )
                        val asisRes = paddleEngine.recognize(experimentRecSet.c[fed.recCropId])
                        val digitsRes = paddleEngine.recognizeNumericDecimal(
                            experimentRecSet.c[fed.recCropId],
                        )
                        experimentRecSet.c[fed.recCropId].release()
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

                fun harvestAabbSeeds(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    growCells: Int,
                    imgW: Int,
                    imgH: Int,
                ): List<android.graphics.Rect> {
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_AABB,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = growCells,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "aabb"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                            val fl = minX.toFloat()
                            val ft = minY.toFloat()
                            val fr = maxX.toFloat()
                            val fb = maxY.toFloat()
                            scaleHunks.add(PumpHunk("", RectF(fl, ft, fr, fb)))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()
                    val redPixelList = pdHunksRawTotal.map { hunk ->
                        android.graphics.Rect(
                            hunk.rect.left.toInt(), hunk.rect.top.toInt(),
                            hunk.rect.right.toInt(), hunk.rect.bottom.toInt(),
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
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    return redPixelList
                }

                fun collectRotOrientedQuads(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    growCells: Int,
                    imgH: Int,
                ): List<ContentExpandUtils.OrientedQuad> {
                    fun hunkFromAabb(r: android.graphics.Rect): PumpHunk =
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    val collected = ArrayList<ContentExpandUtils.OrientedQuad>()
                    prodDetScales.forEach { scale ->
                        val prepared = PumpCostVolUtils.prepareScale(workspace, scale)
                        val contentW = prepared.first
                        val contentH = prepared.second
                        if (contentW < 1 || contentH < 1) return@forEach
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            growCells = growCells,
                            scratchY = NativePaddleEngine.bufferSetA.s.mat,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        branch.metadata["heatmap_post_path_$scale"] =
                            detRes?.metadata?.get("heatmap_post_path") ?: "unknown"
                        branch.metadata["heatmap_box_mode_$scale"] =
                            detRes?.metadata?.get("box_mode") ?: "minAreaRect"
                        val hist = detRes?.heatmapHist ?: IntArray(0)
                        if (hist.isNotEmpty()) {
                            branch.metadata["heatmap_hist_$scale"] =
                                JSONArray(hist.toList()).toString()
                        }
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val oq = ContentExpandUtils.orientedFromPoints8(p)
                            collected.add(oq)
                            scaleHunks.add(hunkFromAabb(oq.toAabb()))
                        }
                        pdHunksRawTotal.addAll(scaleHunks)
                        pdHunksDetectedTotal.addAll(scaleHunks)
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = emptyList()
                    }
                    branch.discoveryDetails = serializeDiscoveryDetails(discoveryDetails)
                    val maxN = PumpOcrSettings.maxRedBoxes(context)
                    val kept = ContentExpandUtils.pruneOrientedQuads(collected, maxN, imgH)
                    pdHunksRawTotal.clear()
                    pdHunksRawTotal.addAll(kept.map { hunkFromAabb(it.toAabb()) })
                    pdHunksDetectedTotal.clear()
                    pdHunksDetectedTotal.addAll(pdHunksRawTotal)
                    branch.metadata["n_reds_after_prune"] = pdHunksRawTotal.size.toString()
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }
                    return kept
                }

                val procRotEnergyTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    branch.metadata["tilt"] = "0"
                    branch.metadata["deskew"] = "skipped"
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "minAreaRect"
                    branch.metadata["heatmap_grow_cells"] = "0"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_mode"] =
                        ContentExpandUtils.Mode.INTERIOR_ENERGY.name
                    branch.metadata["content_expand_bound"] = "tight"
                    val seedQuads = collectRotOrientedQuads(
                        workspace, branch, discoveryDetails, 0, imgH,
                    )
                    val tExpand0 = System.currentTimeMillis()
                    NativeImageUtils.fillEnergyLookU8(
                        workspace.p.mat,
                        NativePaddleEngine.bufferSetA.s.mat,
                    )
                    val expDiag = ContentExpandUtils.expandEnergyOrientTight(
                        workspace.p.mat, seedQuads,
                    )
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    snapshotLookInk(
                        seedQuads.map { it.toAabb() },
                        expDiag.map { it.quad.toAabb() },
                        imgW, imgH, branch,
                        emptyList(), emptyList(), expDiag.map { it.sweep }, emptyList(),
                        reportDir, timestamp, fullRow, branch.name,
                        source = NativePaddleEngine.bufferSetA.s.mat,
                        scratchYuv = NativePaddleEngine.bufferSetB,
                        energyLook = true,
                    )
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetA.s.mat,
                        NativePaddleEngine.bufferSetB,
                        branch,
                        energyVis = true,
                    )
                    val expandedQuads = expDiag.map { it.quad }
                    val hitCaps = expDiag.map { it.hitVertCap }
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedQuads.size.toString()
                    storeSeg7Tele(branch, emptyList())
                    val variants = JSONArray()
                    val tOcrE0 = System.currentTimeMillis()
                    val energyRects = expandedQuads.map { it.toAabb() }
                    val energyOcr = ocrPumpOrientedFlattenAp(expandedQuads, workspace.p.mat)
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
                    val countOcr = ocrPumpOrientedFlattenAp(countQuads, workspace.p.mat)
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
                    val padQuads = expandedQuads.mapIndexed { i, q ->
                        ContentExpandUtils.padOrientedU(q, 0.5f, seedQuads.getOrNull(i))
                    }
                    val padRects = padQuads.map { it.toAabb() }
                    val padOcr = ocrPumpOrientedFlattenAp(padQuads, workspace.p.mat)
                    val padCands = buildRedBoxCandidates(
                        padRects, padOcr.asis, padOcr.digits,
                        padOcr.asisProbs, padOcr.digitsProbs, padOcr.recB64,
                        recWList = padOcr.recW, recHList = padOcr.recH,
                    )
                    val padCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(padCands)
                    variants.put(
                        ocrScaleVariantJson(
                            0.5f, padRects, padQuads, padCands, padCv, kind = "horiz_pad",
                        ),
                    )
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = tOcrE.toString()
                    val blueHunks = energyRects.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    branch.pathResults["Paddle"] = getFinal(
                        blueHunks, "Paddle", 0f, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, energyCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = seedQuads.map { it.toAabb() },
                        ocrSourceRects = energyRects,
                        candidates = energyCands,
                        costCand = energyCv.costCand,
                        volCand = energyCv.volCand,
                        finalCost = energyCv.cost,
                        finalVol = energyCv.vol,
                        assembly = mapOf(
                            "method" to "content_expand",
                            "contentExpandMode" to ContentExpandUtils.Mode.INTERIOR_ENERGY.name,
                            "heatmapBoxMode" to "minAreaRect",
                            "heatmapGrowCells" to 0,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "finalKind" to "energy",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to 0.65f,
                            "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                            "maxRetractFrac" to 0.50f,
                            "note" to "rot-energy-tight: master.p u/v; grow 0; V then H; flatten A.p",
                        ),
                        oranges = emptyList(),
                        ocrQuads = expandedQuads,
                        seedQuads = seedQuads,
                        scaleVariants = variants,
                        inkSweeps = expDiag.indices.map { i ->
                            val off = expandedQuads.getOrNull(i) ?: return@map expDiag[i].sweep
                            expDiag[i].sweep?.withOfficialQuad(seedQuads[i], off)
                        },
                    )
                    val aPd = seedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.RED, 2) } +
                        expandedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) } +
                        padQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) }
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procRotEnergyRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
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
                    branch.metadata["tilt"] = "0"
                    branch.metadata["deskew"] = "skipped"
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "minAreaRect"
                    branch.metadata["heatmap_grow_cells"] = "1"
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_mode"] =
                        ContentExpandUtils.Mode.INTERIOR_ENERGY.name
                    branch.metadata["content_expand_bound"] = "edge-retract"
                    val seedQuads = collectRotOrientedQuads(
                        workspace, branch, discoveryDetails, 1, imgH,
                    )
                    val tExpand0 = System.currentTimeMillis()
                    NativeImageUtils.fillEnergyLookU8(
                        workspace.p.mat,
                        NativePaddleEngine.bufferSetA.s.mat,
                    )
                    val expDiag = ContentExpandUtils.expandEnergyOrientRetract(
                        workspace.p.mat, seedQuads,
                    )
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    snapshotLookInk(
                        seedQuads.map { it.toAabb() },
                        expDiag.map { it.quad.toAabb() },
                        imgW, imgH, branch,
                        emptyList(), emptyList(), expDiag.map { it.sweep }, emptyList(),
                        reportDir, timestamp, fullRow, branch.name,
                        source = NativePaddleEngine.bufferSetA.s.mat,
                        scratchYuv = NativePaddleEngine.bufferSetB,
                        energyLook = true,
                    )
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetA.s.mat,
                        NativePaddleEngine.bufferSetB,
                        branch,
                        energyVis = true,
                    )
                    val expandedQuads = expDiag.map { it.quad }
                    val hitCaps = expDiag.map { it.hitVertCap }
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedQuads.size.toString()
                    storeSeg7Tele(branch, emptyList())
                    val variants = JSONArray()
                    val tOcrE0 = System.currentTimeMillis()
                    val energyRects = expandedQuads.map { it.toAabb() }
                    val energyOcr = ocrPumpOrientedFlattenAp(expandedQuads, workspace.p.mat)
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
                    val countOcr = ocrPumpOrientedFlattenAp(countQuads, workspace.p.mat)
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
                    val padQuads = expandedQuads.mapIndexed { i, q ->
                        ContentExpandUtils.padOrientedU(q, 0.5f, seedQuads.getOrNull(i))
                    }
                    val padRects = padQuads.map { it.toAabb() }
                    val padOcr = ocrPumpOrientedFlattenAp(padQuads, workspace.p.mat)
                    val padCands = buildRedBoxCandidates(
                        padRects, padOcr.asis, padOcr.digits,
                        padOcr.asisProbs, padOcr.digitsProbs, padOcr.recB64,
                        recWList = padOcr.recW, recHList = padOcr.recH,
                    )
                    val padCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(padCands)
                    variants.put(
                        ocrScaleVariantJson(
                            0.5f, padRects, padQuads, padCands, padCv, kind = "horiz_pad",
                        ),
                    )
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = tOcrE.toString()
                    val blueHunks = energyRects.map { r ->
                        PumpHunk(
                            "",
                            RectF(
                                r.left.toFloat(), r.top.toFloat(),
                                r.right.toFloat(), r.bottom.toFloat(),
                            ),
                        )
                    }
                    branch.pathResults["Paddle"] = getFinal(
                        blueHunks, "Paddle", 0f, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, energyCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = seedQuads.map { it.toAabb() },
                        ocrSourceRects = energyRects,
                        candidates = energyCands,
                        costCand = energyCv.costCand,
                        volCand = energyCv.volCand,
                        finalCost = energyCv.cost,
                        finalVol = energyCv.vol,
                        assembly = mapOf(
                            "method" to "content_expand",
                            "contentExpandMode" to ContentExpandUtils.Mode.INTERIOR_ENERGY.name,
                            "heatmapBoxMode" to "minAreaRect",
                            "heatmapGrowCells" to 1,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "finalKind" to "energy",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to 0.65f,
                            "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                            "maxRetractFrac" to 0.50f,
                            "note" to "rot-energy-retract: master.p u/v; grow 1; V then H; flatten A.p",
                        ),
                        oranges = emptyList(),
                        ocrQuads = expandedQuads,
                        seedQuads = seedQuads,
                        scaleVariants = variants,
                        inkSweeps = expDiag.indices.map { i ->
                            val off = expandedQuads.getOrNull(i) ?: return@map expDiag[i].sweep
                            expDiag[i].sweep?.withOfficialQuad(seedQuads[i], off)
                        },
                    )
                    val aPd = seedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.RED, 2) } +
                        expandedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) } +
                        padQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) }
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                suspend fun runRot7segColumn(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    imgW: Int,
                    imgH: Int,
                    growCells: Int,
                    boundNote: String,
                    isColor: Boolean,
                    chromaNote: String?,
                    inkExpandFn: (
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<ContentExpandUtils.OrientedQuad>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                        org.opencv.core.Mat?,
                    ) -> List<ContentExpandUtils.Seg7OrientedExpand>,
                    note: String,
                ) {
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    branch.metadata["tilt"] = "0"
                    branch.metadata["deskew"] = "skipped"
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "minAreaRect"
                    branch.metadata["heatmap_grow_cells"] = growCells.toString()
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = boundNote
                    if (chromaNote != null) {
                        branch.metadata["content_expand_chroma"] = chromaNote
                    }
                    val seedQuads = collectRotOrientedQuads(
                        workspace, branch, discoveryDetails, growCells, imgH,
                    )
                    val tExp0 = System.currentTimeMillis()
                    branch.metadata.remove("look_ink")
                    masterBuffer.s.clear()
                    NativePaddleEngine.bufferSetB.p.clear()
                    val segs = ArrayList<ContentExpandUtils.Seg7OrientedExpand>(seedQuads.size)
                    var nextInk = 255
                    var nextNon = 1
                    var inkLo = 255
                    seedQuads.forEachIndexed { si, q ->

                        val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                        if (poisonBuf.size >= 3) {
                            poisonBuf[0] = nextInk
                            poisonBuf[1] = nextNon
                            poisonBuf[2] = inkLo
                        }
                        val one = inkExpandFn(
                            workspace.p.mat,
                            if (isColor) workspace.p.uvMat else NativePaddleEngine.bufferSetB.s.mat,
                            listOf(q),
                            if (isColor) NativePaddleEngine.bufferSetB.s.mat
                            else NativePaddleEngine.bufferSetA.s.mat,
                            masterBuffer.s.mat,
                            NativePaddleEngine.bufferSetB.p.mat,
                            NativePaddleEngine.bufferSetB.p.uvMat,
                            poisonBuf,
                            if (isColor) NativePaddleEngine.bufferSetA.s.mat else null,
                        )
                        val seg = one.first()
                        segs.add(seg)
                        if (poisonBuf.size >= 3) {
                            nextInk = poisonBuf[poisonBuf.size - 3]
                            nextNon = poisonBuf[poisonBuf.size - 2]
                            inkLo = poisonBuf[poisonBuf.size - 1]
                        }
                        val pd = seg.poison
                        if (pd?.classChange == true) {
                            val dumpSeed = File(objImgRoot, "r${fullRow}_c${col}_box${si + 1}.png")
                            dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                            branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                        }
                        if (pd?.bandH == -1) {
                            recordIncompleteLookIds(
                                if (isColor) NativePaddleEngine.bufferSetB.s.mat
                                else NativePaddleEngine.bufferSetA.s.mat,
                                objImgRoot, fullRow, col, si, seedQuads.size, pd, branch, onLog,
                            )
                        }
                        snapshotLookInkOriented(
                            q, seg.quad, imgW, imgH, branch,
                            seg.poison, seg.tele, seg.sweep, seg.stroke,
                            reportDir, timestamp, fullRow, branch.name,
                            isColor = isColor,
                        )
                    }
                    snapshotOverlayFull(
                        NativePaddleEngine.bufferSetB.p,
                        NativePaddleEngine.bufferSetA,
                        branch,
                    )
                    val dumpFinal = File(objImgRoot, "r${fullRow}_c${col}_final.png")
                    dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                    branch.metadata["object_dump_final"] = dumpFinal.name
                    val jumpedQuads = segs.map { it.quad }
                    fun inkQuadsFor(kk: Float): List<ContentExpandUtils.OrientedQuad> {
                        return segs.indices.map { i ->
                            ContentExpandUtils.padOrientedByStrokes(
                                jumpedQuads[i], seedQuads[i], kk, segs[i].stroke.sPx,
                            )
                        }
                    }
                    val official = segs.map { it.quad }
                    val officialRects = official.map { it.toAabb() }
                    val officialHunks = officialRects.map { e ->
                        PumpHunk(
                            "",
                            RectF(
                                e.left.toFloat(), e.top.toFloat(),
                                e.right.toFloat(), e.bottom.toFloat(),
                            ),
                        )
                    }
                    val strokes = segs.map { it.stroke }
                    branch.metadata["s_per_red"] = strokes.joinToString(",") { it.sPx.toString() }
                    storeSeg7Tele(branch, segs.map { it.tele })
                    branch.metadata["seg7_k"] = "0,1,2,3,4"
                    branch.metadata["seg7_k_official"] = "0"
                    branch.metadata["seg7_jump_frac"] = "0.60"
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExp0).toString()
                    val variants = JSONArray()
                    val tOcr0 = System.currentTimeMillis()
                    val ocr0 = ocrPumpOrientedFlattenAp(official, workspace.p.mat)
                    val cands0 = buildRedBoxCandidates(
                        officialRects, ocr0.asis, ocr0.digits, ocr0.asisProbs, ocr0.digitsProbs,
                        ocr0.recB64, recWList = ocr0.recW, recHList = ocr0.recH,
                    )
                    val cv0 = PumpCostVolUtils.classifyCostVolFromBoxOcr(cands0)
                    variants.put(
                        ocrScaleVariantJson(
                            0f, officialRects, official, cands0, cv0, kind = "ink",
                        ),
                    )
                    var nOcr = official.size
                    val skipExtraK = BooleanArray(cands0.size) { i ->
                        val asis = cands0[i].asis
                        asis.any { it.isLetter() } && asis.none { it.isDigit() }
                    }
                    branch.metadata["seg7_skip_extra_k_letter"] =
                        skipExtraK.count { it }.toString()
                    suspend fun emitHorizPad(
                        s: Float,
                        padQuads: List<ContentExpandUtils.OrientedQuad>,
                        skip: BooleanArray?,
                    ): List<ContentExpandUtils.OrientedQuad> {
                        val padRects = padQuads.map { it.toAabb() }
                        val ocrIdx = ArrayList<Int>()
                        val ocrQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                        padQuads.indices.forEach { i ->
                            if (skip == null || i >= skip.size || !skip[i]) {
                                ocrIdx.add(i)
                                ocrQuads.add(padQuads[i])
                            }
                        }
                        val ocrP = if (ocrQuads.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpOrientedFlattenAp(ocrQuads, workspace.p.mat)
                        }
                        nOcr += ocrQuads.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsP = padQuads.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                RedBoxOcrCandidate("box${i + 1}", "", "", rect = padRects[i])
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrP.asis.getOrElse(j) { "" },
                                    ocrP.digits.getOrElse(j) { "" },
                                    ocrP.asisProbs.getOrElse(j) { "" },
                                    ocrP.digitsProbs.getOrElse(j) { "" },
                                    padRects[i],
                                    ocrP.recB64.getOrElse(j) { "" },
                                    ocrP.recW.getOrElse(j) { 0 },
                                    ocrP.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                        variants.put(
                            ocrScaleVariantJson(
                                s, padRects, padQuads, candsP, cvP, kind = "horiz_pad",
                            ),
                        )
                        return padQuads
                    }
                    val pad0 = emitHorizPad(0f, segs.map { it.quadPad }, null)
                    for (kk in listOf(1f, 2f, 3f, 4f)) {
                        val quads = inkQuadsFor(kk)
                        val rects = quads.map { it.toAabb() }
                        val ocrIdx = ArrayList<Int>()
                        val ocrQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                        quads.indices.forEach { i ->
                            if (i >= skipExtraK.size || !skipExtraK[i]) {
                                ocrIdx.add(i)
                                ocrQuads.add(quads[i])
                            }
                        }
                        val ocrK = if (ocrQuads.isEmpty()) {
                            PumpRectOcrLists(emptyList(), emptyList())
                        } else {
                            ocrPumpOrientedFlattenAp(ocrQuads, workspace.p.mat)
                        }
                        nOcr += ocrQuads.size
                        val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                        ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                        val candsK = quads.indices.map { i ->
                            val j = ocrAt[i]
                            if (j == null) {
                                val src = cands0.getOrElse(i) {
                                    RedBoxOcrCandidate("box${i + 1}", "", "")
                                }
                                src.copy(
                                    label = "box${i + 1}",
                                    rect = rects[i],
                                    recB64 = "",
                                    recW = 0,
                                    recH = 0,
                                )
                            } else {
                                RedBoxOcrCandidate(
                                    "box${i + 1}",
                                    ocrK.asis.getOrElse(j) { "" },
                                    ocrK.digits.getOrElse(j) { "" },
                                    ocrK.asisProbs.getOrElse(j) { "" },
                                    ocrK.digitsProbs.getOrElse(j) { "" },
                                    rects[i],
                                    ocrK.recB64.getOrElse(j) { "" },
                                    ocrK.recW.getOrElse(j) { 0 },
                                    ocrK.recH.getOrElse(j) { 0 },
                                )
                            }
                        }
                        val cvK = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                        variants.put(
                            ocrScaleVariantJson(
                                kk, rects, quads, candsK, cvK, kind = "ink",
                            ),
                        )
                        emitHorizPad(
                            kk,
                            quads.map { ContentExpandUtils.padOrientedU(it, 0.5f) },
                            skipExtraK,
                        )
                    }
                    branch.metadata["n_ocr"] = nOcr.toString()
                    branch.metadata["t_ocr_ms"] =
                        (System.currentTimeMillis() - tOcr0).toString()
                    branch.pathResults["Paddle"] = getFinal(
                        officialHunks, "Paddle", 0f, pdHunksRawTotal, workspace,
                        experimentRecSet, paddleEngine, context, imgW, imgH, cands0,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = seedQuads.map { it.toAabb() },
                        ocrSourceRects = officialRects,
                        candidates = cands0,
                        costCand = cv0.costCand,
                        volCand = cv0.volCand,
                        finalCost = cv0.cost,
                        finalVol = cv0.vol,
                        assembly = mapOf(
                            "method" to "7seg_stroke",
                            "k" to listOf(0, 1, 2, 3, 4),
                            "kOfficial" to 0,
                            "heatmapBoxMode" to "minAreaRect",
                            "heatmapGrowCells" to growCells,
                            "hmThresh" to HEAT_THR_U8_GE1,
                            "hmThreshNote" to "u8>=1",
                            "sPx" to strokes.map { it.sPx },
                            "jumpFrac" to 0.60f,
                            "note" to note,
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ),
                        oranges = emptyList(),
                        ocrQuads = official,
                        seedQuads = seedQuads,
                        scaleVariants = variants,
                        inkSweeps = segs.indices.map { i ->
                            val off = official.getOrNull(i) ?: return@map segs[i].sweep
                            segs[i].sweep?.withOfficialQuad(seedQuads[i], off)
                        },
                    )
                    val aPd = seedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.RED, 2) } +
                        official.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) } +
                        pad0.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) }
                    branch.images["PD"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, workspace,
                    ).first
                }
                val procRotGrayTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 0, "tight", false, null,
                        ContentExpandUtils::expandGrayOrientTight,
                        "rot-gray-tight: master.p u/v; grow 0; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procRotGrayRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 1, "edge-retract", false, null,
                        ContentExpandUtils::expandGrayOrientRetract,
                        "rot-gray-retract: master.p u/v; grow 1; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procRotColorTight: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 0, "tight", true, "color_adaptive",
                        ContentExpandUtils::expandColorOrientTight,
                        "rot-color-tight: master.p u/v; grow 0; tint A.s; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procRotColorRetract: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 1, "edge-retract", true, "color_adaptive",
                        ContentExpandUtils::expandColorOrientRetract,
                        "rot-color-retract: master.p u/v; grow 1; tint A.s; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procInkColorTightVsp: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runAabb7segColumn(
                        ws, br, det, w, h, 0, "tight",
                        ContentExpandUtils::expandColorAabbTightVsp,
                        "ink-color-tight-vsp: AABB grow 0; tint A.s; virtual S&P; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procInkColorRetractVsp: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runAabb7segColumn(
                        ws, br, det, w, h, 1, "edge-retract",
                        ContentExpandUtils::expandColorAabbRetractVsp,
                        "ink-color-retract-vsp: AABB grow 1; tint A.s; virtual S&P; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procRotColorTightVsp: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 0, "tight", true, "color_adaptive",
                        ContentExpandUtils::expandColorOrientTightVsp,
                        "rot-color-tight-vsp: master.p u/v; grow 0; tint A.s; virtual S&P; overlay look-ink rec-pad; k=0..4",
                    )
                }
                val procRotColorRetractVsp: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runRot7segColumn(
                        ws, br, det, w, h, 1, "edge-retract", true, "color_adaptive",
                        ContentExpandUtils::expandColorOrientRetractVsp,
                        "rot-color-retract-vsp: master.p u/v; grow 1; tint A.s; virtual S&P; overlay look-ink rec-pad; k=0..4",
                    )
                }
                suspend fun runSeedInkAabbColumn(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    imgW: Int,
                    imgH: Int,
                    growCells: Int,
                    boundNote: String,
                    color: Boolean = false,
                ) {
                    if (imgW < 1 || imgH < 1) return
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    val tilt = photoTilt
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "aabb"
                    branch.metadata["heatmap_grow_cells"] = growCells.toString()
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = boundNote
                    val seeds = harvestAabbSeeds(
                        workspace, branch, discoveryDetails, growCells, imgW, imgH,
                    )
                    branch.metadata.remove("look_ink")
                    val grayOk = !workspace.p.mat.empty() && workspace.p.mat.type() == CvType.CV_8UC1
                    val keep = SeedInkProbeKeep.keepBoxes(file.name, branch.name)
                    if (grayOk) {
                        val uv = if (color) workspace.p.uvMat else null
                        val whiteY = NativePaddleEngine.bufferSetB.p.mat
                        val strokeY = NativePaddleEngine.bufferSetB.s.mat
                        val deskewP = NativePaddleEngine.deskewBufferSetLarge.p.mat
                        val destSet = NativePaddleEngine.deskewBufferSetLarge
                        seeds.forEachIndexed { i, seed ->
                            val boxN = i + 1
                            if (boxN !in keep) return@forEachIndexed
                            val srcW = seed.width().coerceAtLeast(1)
                            val srcH = seed.height().coerceAtLeast(1)
                            val (destW0, destH0) = seedInkLookDestSize(
                                srcW, srcH, true, destSet.s,
                            )
                            if (destW0 < 2 || destH0 < 2) return@forEachIndexed
                            val cropId = destSet.s.createCrop(0, 0, destW0, destH0)
                            try {
                                val dest = destSet.c[cropId]
                                val probe = ContentExpandUtils.probeSeedInk(
                                    workspace.p.mat, seed, imgW, uv,
                                    workspace.s.mat, whiteY, strokeY, deskewP,
                                    dest.mat, dest.uvMat, destW0, destH0,
                                )
                                for (thr in probe.thrs) {
                                    val okKind = if (color) {
                                        thr.kind == "cband" || thr.kind == "cunion" ||
                                            thr.kind == "cpick" || thr.kind == "cflood"
                                    } else {
                                        thr.kind == "gt" || thr.kind == "band" ||
                                            thr.kind == "union" ||
                                            thr.kind == "pick" || thr.kind == "flood"
                                    }
                                    if (!okKind) continue
                                    snapshotSeedInkProbe(
                                        thr, boxN, branch,
                                        reportDir, timestamp, fullRow, branch.name,
                                    )
                                }
                            } finally {
                                destSet.c[cropId].release()
                            }
                        }
                    }
                    val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2)
                    val pd = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, NativePaddleEngine.bufferSetB,
                    ).first
                    branch.images["PD"] = pd
                    branch.images["overlay"] = pd
                    branch.pathResults["Paddle"] = PathResult("N/A", "N/A", "", "")
                }
                suspend fun runSeedInkRotColumn(
                    workspace: BufferSet,
                    branch: PumpBranch,
                    discoveryDetails: MutableMap<String, MutableMap<Int, List<PumpHunk>>>,
                    imgW: Int,
                    imgH: Int,
                    growCells: Int,
                    boundNote: String,
                    color: Boolean = false,
                ) {
                    if (imgW < 1 || imgH < 1) return
                    pdHunksDetectedTotal.clear()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    val tDeskewStart = System.currentTimeMillis()
                    branch.metadata["tilt"] = "0"
                    branch.metadata["deskew"] = "skipped"
                    branch.metadata["t_deskew_ms"] =
                        (System.currentTimeMillis() - tDeskewStart).toString()
                    branch.metadata["heatmap_box_mode"] = "minAreaRect"
                    branch.metadata["heatmap_grow_cells"] = growCells.toString()
                    branch.metadata["hm_thresh"] = HEAT_THR_U8_GE1.toString()
                    branch.metadata["hm_thresh_note"] = "u8>=1"
                    branch.metadata["mask_dilate_passes"] = "0"
                    branch.metadata["heatmap_cell_px"] =
                        NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
                    branch.metadata["product_path"] = NativePaddleEngine.activeProductPathId
                    branch.metadata["product_dir"] = NativePaddleEngine.activeProductDir
                    branch.metadata["det_model"] = "product_det"
                    branch.metadata["content_expand_bound"] = boundNote
                    val seedQuads = collectRotOrientedQuads(
                        workspace, branch, discoveryDetails, growCells, imgH,
                    )
                    branch.metadata.remove("look_ink")
                    val grayOk = !workspace.p.mat.empty() && workspace.p.mat.type() == CvType.CV_8UC1
                    val keep = SeedInkProbeKeep.keepBoxes(file.name, branch.name)
                    if (grayOk) {
                        val uv = if (color) workspace.p.uvMat else null
                        val whiteY = NativePaddleEngine.bufferSetB.p.mat
                        val strokeY = NativePaddleEngine.bufferSetB.s.mat
                        val deskewP = NativePaddleEngine.deskewBufferSetLarge.p.mat
                        val destSet = NativePaddleEngine.deskewBufferSetLarge
                        seedQuads.forEachIndexed { i, q ->
                            val boxN = i + 1
                            if (boxN !in keep) return@forEachIndexed
                            val (srcW, srcH) = q.uvRasterSize()
                            val (destW0, destH0) = seedInkLookDestSize(
                                srcW, srcH, false, destSet.s,
                            )
                            if (destW0 < 2 || destH0 < 2) return@forEachIndexed
                            val cropId = destSet.s.createCrop(0, 0, destW0, destH0)
                            try {
                                val dest = destSet.c[cropId]
                                val probe = ContentExpandUtils.probeSeedInkUv(
                                    workspace.p.mat, uv, q, imgW,
                                    workspace.s.mat, whiteY, strokeY, deskewP,
                                    dest.mat, dest.uvMat, destW0, destH0,
                                )
                                for (thr in probe.thrs) {
                                    val okKind = if (color) {
                                        thr.kind == "cband" || thr.kind == "cunion" ||
                                            thr.kind == "cpick" || thr.kind == "cflood"
                                    } else {
                                        thr.kind == "gt" || thr.kind == "band" ||
                                            thr.kind == "union" ||
                                            thr.kind == "pick" || thr.kind == "flood"
                                    }
                                    if (!okKind) continue
                                    snapshotSeedInkProbe(
                                        thr, boxN, branch,
                                        reportDir, timestamp, fullRow, branch.name,
                                    )
                                }
                            } finally {
                                destSet.c[cropId].release()
                            }
                        }
                    }
                    val aPd = seedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.RED, 2) }
                    val pd = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        aPd, null, NativePaddleEngine.bufferSetB,
                    ).first
                    branch.images["PD"] = pd
                    branch.images["overlay"] = pd
                    branch.pathResults["Paddle"] = PathResult("N/A", "N/A", "", "")
                }
                val procAabbTightSeedInk: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkAabbColumn(ws, br, det, w, h, 0, "tight")
                }
                val procAabbLargeSeedInk: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkAabbColumn(ws, br, det, w, h, 1, "large")
                }
                val procRotTightSeedInk: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkRotColumn(ws, br, det, w, h, 0, "tight")
                }
                val procRotLargeSeedInk: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkRotColumn(ws, br, det, w, h, 1, "large")
                }
                val procAabbTightSeedInkColor: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkAabbColumn(ws, br, det, w, h, 0, "tight", color = true)
                }
                val procAabbLargeSeedInkColor: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkAabbColumn(ws, br, det, w, h, 1, "large", color = true)
                }
                val procRotTightSeedInkColor: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkRotColumn(ws, br, det, w, h, 0, "tight", color = true)
                }
                val procRotLargeSeedInkColor: suspend (
                    BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int,
                ) -> Unit = { ws, br, det, w, h ->
                    runSeedInkRotColumn(ws, br, det, w, h, 1, "large", color = true)
                }
                val procProdInk = makeInkAabbProc(
                    "ink-prod: product det + seed-ROI s; walk once; OCR k=0..4; official k=0; gap/peek 0.5s; cap 2.5×seedH safety; jump-retract (no G-list)",
                    ContentExpandUtils::expandGrayAabbExpand,
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
                    // Pad oriented quads along u/v in source so warp margin is real pixels, then
                    // place strip at (0,0) without black 4px createCrop inset (same as odo Raw).
                    data class OcrOne(
                        val asis: Pair<String, String>,
                        val digits: Pair<String, String>,
                        val snap: String,
                        val recW: Int,
                        val recH: Int,
                    )
                    suspend fun ocrOne(q: ContentExpandUtils.OrientedQuad): OcrOne {
                        if (q.shortAxisBh() < 2f || q.longAxisBw() < 2f) {
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val rSc = 48f / q.shortAxisBh()
                        val pad = kotlin.math.ceil(4.0 / rSc.toDouble()).toInt().coerceAtLeast(1)
                        val qPad = q.padUv(pad)
                        experimentRecSet.p.clear()
                        val nativeH = qPad.shortAxisBh().roundToInt().coerceAtLeast(1)
                        val nativeW = qPad.longAxisBw().roundToInt().coerceAtLeast(1)
                        val work = NativePaddleEngine.bufferSetB
                        val nativeId = work.s.createCrop(0, 0, nativeW, nativeH)
                        val nativeMat = work.c[nativeId].mat
                        val ok = ContentExpandUtils.warpQuadToHorizontalStrip(
                            gray, qPad, nativeMat, targetH = 0,
                        )
                        if (!ok || nativeMat.empty()) {
                            work.c[nativeId].release()
                            return OcrOne("?" to "", "?" to "", "", 0, 0)
                        }
                        val recH = RecBufferFeed.DEFAULT_REC_H.coerceAtMost(nativeH)
                        val recW = ((nativeW.toFloat() * recH / nativeH).roundToInt()).coerceAtLeast(1)
                        val recId = experimentRecSet.p.createCrop(0, 0, recW, recH)
                        ContentExpandUtils.downscaleStripArea(
                            nativeMat, experimentRecSet.c[recId].mat, recW, recH,
                        )
                        work.c[nativeId].release()
                        val fed = RecBufferFeed.Result(1f, 0, recId, recW, recH)
                        val snap = PumpCostVolUtils.snapRecCrop(
                            experimentRecSet, fed.recCropId, fed.targetW, fed.targetH,
                        )
                        val asisRes = paddleEngine.recognize(experimentRecSet.c[fed.recCropId])
                        val digitsRes = paddleEngine.recognizeNumericDecimal(
                            experimentRecSet.c[fed.recCropId],
                        )
                        experimentRecSet.c[fed.recCropId].release()
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
                    seg7Stroke: Boolean = false,
                    detScales: List<Int> = prodDetScales,
                    chromaExpand: Boolean = false,
                    chromaMode: Int = 0,
                    boundStrategy: Int = 0,
                    energyOrientNative: ((org.opencv.core.Mat, FloatArray, ShortArray?, org.opencv.core.Mat?) -> FloatArray?)? = null,
                    orientInk: ((
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<ContentExpandUtils.OrientedQuad>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                        org.opencv.core.Mat?,
                    ) -> List<ContentExpandUtils.Seg7OrientedExpand>)? = null,
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
                    detScales.forEach { scale ->
                        val srcW = workspace.p.width
                        val srcH = workspace.p.height
                        val currentLongEdge = max(srcW, srcH)
                        val scaleFactor =
                            if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                        val targetW = (srcW * scaleFactor).toInt().coerceAtLeast(2)
                        val targetH = (srcH * scaleFactor).toInt().coerceAtLeast(2)
                        PumpCostVolUtils.prepareScale(workspace, scale)
                        val dest = NativePaddleEngine.deskewSetFor(scale)
                        val S = dest.width
                        val fullW = workspace.p.width
                        val fullH = workspace.p.height
                        val heatToPhoto =
                            max(fullW, fullH).toFloat() / max(targetW, targetH).coerceAtLeast(1).toFloat()
                        Log.i(
                            TAG,
                            "pump_rot_detect scale=$scale content=${targetW}x$targetH packed=${S}x$S",
                        )
                        val detRes = paddleEngine.detect(
                            dest,
                            targetW = S,
                            targetH = S,
                            copyHeatmap = false,
                            boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                            hmThresh = HEAT_THR_U8_GE1,
                            maskDilatePasses = 0,
                            heatToPhoto = heatToPhoto,
                            photoW = fullW,
                            photoH = fullH,
                        )
                        branch.metadata["t_pd_inference_$scale"] =
                            detRes?.metadata?.get("t_inference_ms") ?: "0"
                        branch.metadata["t_pd_native_post_$scale"] =
                            detRes?.metadata?.get("t_native_post_ms") ?: "0"
                        val scaleHunks = mutableListOf<PumpHunk>()
                        detRes?.nativeBoxes?.forEach { box ->
                            val p = box.points
                            if (p.size < 8) return@forEach
                            val oq = ContentExpandUtils.orientedFromPoints8(p)
                            collected.add(oq)
                            scaleHunks.add(hunkFromAabb(oq.toAabb()))
                        }
                        discoveryDetails["Paddle Raw"]!![scale] = scaleHunks
                        discoveryDetails["Paddle Expanded"]!![scale] = emptyList()
                        discoveryDetails["Paddle Max Extent"]!![scale] = emptyList()
                        discoveryDetails["Paddle Native"]!![scale] = scaleHunks
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
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(pdHunksRawTotal, workspace, branch)
                    }

                    val seedQuads = kept.toList()
                    val tExpand0 = System.currentTimeMillis()
                    val expDiag: List<ContentExpandUtils.OrientedExpand>
                    val expandedQuads: List<ContentExpandUtils.OrientedQuad>
                    val hitCaps: List<Boolean>
                    val inkStrokes: List<ContentExpandUtils.StrokeWidthInSeed>
                    val inkWalkSeeds: List<ContentExpandUtils.OrientedQuad>
                    val inkWalkBoxes: List<ContentExpandUtils.OrientedQuad>
                    val inkWalkPadQuads: List<ContentExpandUtils.OrientedQuad>
                    val inkJumpOptsRot: ContentExpandUtils.ExpandOptions?
                    var rotInkSweeps: List<ContentExpandUtils.InkSweep?> = emptyList()
                    val expandMode = if (chromaMode != 0) chromaMode else if (chromaExpand) 1 else 0
                    if (orientInk != null || seg7Stroke) {
                        val jumpOpts = ContentExpandUtils.ExpandOptions(
                            maxFrac = 0.4f,
                            enableJump = true,
                            jumpFrac = 0.60f,
                            retractClearFrac = 0.30f,
                            energyRatio = 0.65f,
                        )
                        branch.metadata.remove("look_ink")
                        val colIdx = flows.indexOf(branch.name).let { if (it < 0) 0 else it }
                        masterBuffer.s.clear()
                        val segs = ArrayList<ContentExpandUtils.Seg7OrientedExpand>(seedQuads.size)
                        var nextInk = 255
                        var nextNon = 1
                        var inkLo = 255
                        val isColor = expandMode != 0
                        seedQuads.forEachIndexed { si, q ->
                            val poisonBuf = ContentExpandUtils.poisonStatsBuf(1)
                            if (poisonBuf.size >= 3) {
                                poisonBuf[0] = nextInk
                                poisonBuf[1] = nextNon
                                poisonBuf[2] = inkLo
                            }
                            val one = if (orientInk != null) {
                                orientInk(
                                    masterBuffer.p.mat,
                                    if (isColor) masterBuffer.p.uvMat else NativePaddleEngine.bufferSetB.s.mat,
                                    listOf(q),
                                    if (isColor) NativePaddleEngine.bufferSetB.s.mat
                                    else NativePaddleEngine.bufferSetA.s.mat,
                                    masterBuffer.s.mat,
                                    NativePaddleEngine.bufferSetB.p.mat,
                                    NativePaddleEngine.bufferSetB.p.uvMat,
                                    poisonBuf,
                                    if (isColor) NativePaddleEngine.bufferSetA.s.mat else null,
                                )
                            } else {
                                listOf(
                                    ContentExpandUtils.Seg7OrientedExpand(
                                        q,
                                        ContentExpandUtils.strokeWidthInSeed(
                                            masterBuffer.p.mat, q.toAabb(),
                                        ),
                                        quadPad = ContentExpandUtils.padOrientedU(q, 0.5f),
                                    ),
                                )
                            }
                            val seg = one.first()
                            segs.add(seg)
                            if (poisonBuf.size >= 3) {
                                nextInk = poisonBuf[poisonBuf.size - 3]
                                nextNon = poisonBuf[poisonBuf.size - 2]
                                inkLo = poisonBuf[poisonBuf.size - 1]
                            }
                            val pd = seg.poison
                            if (pd?.classChange == true) {
                                val dumpSeed = File(objImgRoot, "r${fullRow}_c${colIdx}_box${si + 1}.png")
                                dumpObjectPlanePng(masterBuffer.s.mat, dumpSeed)
                                branch.metadata["object_dump_box${si + 1}"] = dumpSeed.name
                            }
                            if (pd?.bandH == -1) {
                                recordIncompleteLookIds(
                                    if (isColor) NativePaddleEngine.bufferSetB.s.mat
                                    else NativePaddleEngine.bufferSetA.s.mat,
                                    objImgRoot, fullRow, colIdx, si, seedQuads.size, pd, branch, onLog,
                                )
                            }
                            snapshotLookInkOriented(
                                q, seg.quad, imgW, imgH, branch,
                                seg.poison, seg.tele, seg.sweep, seg.stroke,
                                reportDir, timestamp, fullRow, branch.name,
                                isColor = isColor,
                            )
                        }
                        val dumpFinal = File(objImgRoot, "r${fullRow}_c${colIdx}_final.png")
                        dumpObjectPlanePng(masterBuffer.s.mat, dumpFinal)
                        branch.metadata["object_dump_final"] = dumpFinal.name
                        val jumpedQuads = segs.map { it.quad }
                        fun inkQuadsFor(kk: Float): List<ContentExpandUtils.OrientedQuad> {
                            return segs.indices.map { i ->
                                ContentExpandUtils.padOrientedByStrokes(
                                    jumpedQuads[i], seedQuads[i], kk, segs[i].stroke.sPx,
                                )
                            }
                        }
                        expandedQuads = segs.map { it.quad }
                        hitCaps = expandedQuads.map { false }
                        inkStrokes = segs.map { it.stroke }
                        expDiag = emptyList()
                        inkWalkSeeds = seedQuads
                        inkWalkBoxes = jumpedQuads
                        inkWalkPadQuads = segs.map { it.quadPad }
                        inkJumpOptsRot = jumpOpts
                        rotInkSweeps = segs.indices.map { i ->
                            val off = expandedQuads.getOrNull(i) ?: return@map segs[i].sweep
                            segs[i].sweep?.withOfficialQuad(seedQuads[i], off)
                        }
                        branch.metadata["s_per_red"] =
                            inkStrokes.joinToString(",") { it.sPx.toString() }
                        storeSeg7Tele(branch, segs.map { it.tele })
                        branch.metadata["seg7_k"] = "0,1,2,3,4"
                        branch.metadata["seg7_k_official"] = "0"
                        branch.metadata["seg7_vert_cap_frac"] =
                            ContentExpandUtils.SEG7_VERT_CAP_FRAC.toString()
                        branch.metadata["seg7_gap_frac"] =
                            ContentExpandUtils.SEG7_GAP_FRAC.toString()
                        branch.metadata["seg7_jump_frac"] = "0.60"
                        branch.metadata["seg7_retract_clear_frac"] = "0.30"
                        branch.metadata["content_expand_oriented_7seg"] = "true"
                        if (expandMode == 4) {
                            branch.metadata["content_expand_chroma"] = "color_adaptive"
                        } else if (expandMode == 1 || chromaExpand) {
                            branch.metadata["content_expand_chroma"] = "true"
                        }
                        if (boundStrategy != 0) {
                            branch.metadata["content_expand_bound"] =
                                if (boundStrategy == 2) "edge-retract" else "tight"
                        }
                    } else {
                        val expandOpts = ContentExpandUtils.ExpandOptions(
                            maxFrac = maxFrac,
                            enableJump = enableJump,
                            jumpFrac = jumpFrac,
                            energyRatio = energyRatio,
                            freezeHorzDuringVert = freezeHorzDuringVert,
                            vertPadFrac = vertPadFrac,
                            recordVertEnergy = energyTraceOut != null,
                            boundStrategy = boundStrategy,
                        )
                        expDiag = seedQuads.map { seed ->
                            val d = ContentExpandUtils.expandOrientedDiagnose(
                                masterBuffer.p.mat, seed, expandOpts, energyOrientNative,
                            )
                            val jpeg = OcrUtils.takeSnapshotJpeg(
                                workspace.s, seed.toAabb(), PUMP_CROP_TARGET_W, PUMP_CROP_TARGET_H,
                                emptyList(), null, NativePaddleEngine.bufferSetB,
                            ).first
                            if (jpeg.isEmpty()) d else d.copy(sweep = d.sweep?.copy(threshJpeg = jpeg))
                        }
                        snapshotLookInk(
                            seedQuads.map { it.toAabb() },
                            expDiag.map { it.quad.toAabb() },
                            imgW, imgH, branch,
                            emptyList(), emptyList(), expDiag.map { it.sweep }, emptyList(),
                            reportDir, timestamp, fullRow, branch.name,
                            source = workspace.s.mat,
                            scratchYuv = NativePaddleEngine.bufferSetB,
                            energyLook = true,
                        )
                        expandedQuads = expDiag.map { it.quad }
                        hitCaps = expDiag.map { it.hitVertCap }
                        inkStrokes = emptyList()
                        inkWalkSeeds = emptyList()
                        inkWalkBoxes = emptyList()
                        inkWalkPadQuads = emptyList()
                        inkJumpOptsRot = null
                        rotInkSweeps = expDiag.indices.map { i ->
                            val off = expandedQuads.getOrNull(i) ?: return@map expDiag[i].sweep
                            expDiag[i].sweep?.withOfficialQuad(seedQuads[i], off)
                        }
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
                    }
                    branch.metadata["t_expand_ms"] =
                        (System.currentTimeMillis() - tExpand0).toString()
                    branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                    branch.metadata["n_ocr_energy"] = expandedQuads.size.toString()
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
                    var pdPadQuads: List<ContentExpandUtils.OrientedQuad> = emptyList()
                    val energyRects: List<android.graphics.Rect>
                    val energyCands: List<RedBoxOcrCandidate>
                    val energyCv: CostVolClassifyResult
                    val tOcrE0 = System.currentTimeMillis()
                    if ((seg7Stroke || orientInk != null) && inkJumpOptsRot != null) {
                        val opts = inkJumpOptsRot
                        fun inkQuadsForK(kk: Float): List<ContentExpandUtils.OrientedQuad> {
                            return inkWalkSeeds.indices.map { i ->
                                ContentExpandUtils.padOrientedByStrokes(
                                    inkWalkBoxes[i], inkWalkSeeds[i], kk,
                                    inkStrokes[i].sPx,
                                )
                            }
                        }
                        var nOcr = 0
                        var officialCands: List<RedBoxOcrCandidate> = emptyList()
                        var officialCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(emptyList())
                        var skipExtraK = BooleanArray(0)
                        suspend fun emitRotHorizPad(
                            s: Float,
                            padQuads: List<ContentExpandUtils.OrientedQuad>,
                            skip: BooleanArray?,
                        ): List<ContentExpandUtils.OrientedQuad> {
                            val padRects = padQuads.map { it.toAabb() }
                            val ocrIdx = ArrayList<Int>()
                            val ocrQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                            padQuads.indices.forEach { i ->
                                if (skip == null || i >= skip.size || !skip[i]) {
                                    ocrIdx.add(i)
                                    ocrQuads.add(padQuads[i])
                                }
                            }
                            val ocrP = if (ocrQuads.isEmpty()) {
                                PumpRectOcrLists(emptyList(), emptyList())
                            } else {
                                ocrPumpOrientedQuads(ocrQuads, masterBuffer.p.mat, imgW, imgH)
                            }
                            nOcr += ocrQuads.size
                            val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                            ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                            val candsP = padQuads.indices.map { i ->
                                val j = ocrAt[i]
                                if (j == null) {
                                    RedBoxOcrCandidate(
                                        "box${i + 1}", "", "",
                                        rect = padRects[i],
                                    )
                                } else {
                                    RedBoxOcrCandidate(
                                        "box${i + 1}",
                                        ocrP.asis.getOrElse(j) { "" },
                                        ocrP.digits.getOrElse(j) { "" },
                                        ocrP.asisProbs.getOrElse(j) { "" },
                                        ocrP.digitsProbs.getOrElse(j) { "" },
                                        padRects[i],
                                        ocrP.recB64.getOrElse(j) { "" },
                                        ocrP.recW.getOrElse(j) { 0 },
                                        ocrP.recH.getOrElse(j) { 0 },
                                    )
                                }
                            }
                            val cvP = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsP)
                            variants.put(
                                ocrScaleVariantJson(
                                    s, padRects, padQuads, candsP, cvP, kind = "horiz_pad",
                                ),
                            )
                            return padQuads
                        }
                        for (kk in listOf(0f, 1f, 2f, 3f, 4f)) {
                            val quads = if (kk == 0f) expandedQuads else inkQuadsForK(kk)
                            val rects = quads.map { it.toAabb() }
                            val candsK: List<RedBoxOcrCandidate>
                            if (kk == 0f) {
                                val ocrK = ocrPumpOrientedQuads(quads, masterBuffer.p.mat, imgW, imgH)
                                nOcr += quads.size
                                candsK = buildRedBoxCandidates(
                                    rects, ocrK.asis, ocrK.digits,
                                    ocrK.asisProbs, ocrK.digitsProbs, ocrK.recB64,
                                    recWList = ocrK.recW, recHList = ocrK.recH,
                                )
                                officialCands = candsK
                                officialCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                                skipExtraK = BooleanArray(candsK.size) { i ->
                                    val asis = candsK[i].asis
                                    asis.any { it.isLetter() } && asis.none { it.isDigit() }
                                }
                                branch.metadata["seg7_skip_extra_k_letter"] =
                                    skipExtraK.count { it }.toString()
                            } else {
                                val ocrIdx = ArrayList<Int>()
                                val ocrQuads = ArrayList<ContentExpandUtils.OrientedQuad>()
                                quads.indices.forEach { i ->
                                    if (i >= skipExtraK.size || !skipExtraK[i]) {
                                        ocrIdx.add(i)
                                        ocrQuads.add(quads[i])
                                    }
                                }
                                val ocrK = if (ocrQuads.isEmpty()) {
                                    PumpRectOcrLists(emptyList(), emptyList())
                                } else {
                                    ocrPumpOrientedQuads(ocrQuads, masterBuffer.p.mat, imgW, imgH)
                                }
                                nOcr += ocrQuads.size
                                val ocrAt = HashMap<Int, Int>(ocrIdx.size)
                                ocrIdx.forEachIndexed { j, i -> ocrAt[i] = j }
                                candsK = quads.indices.map { i ->
                                    val j = ocrAt[i]
                                    if (j == null) {
                                        val src = officialCands.getOrElse(i) {
                                            RedBoxOcrCandidate("box${i + 1}", "", "")
                                        }
                                        src.copy(
                                            label = "box${i + 1}",
                                            rect = rects[i],
                                            recB64 = "",
                                            recW = 0,
                                            recH = 0,
                                        )
                                    } else {
                                        RedBoxOcrCandidate(
                                            "box${i + 1}",
                                            ocrK.asis.getOrElse(j) { "" },
                                            ocrK.digits.getOrElse(j) { "" },
                                            ocrK.asisProbs.getOrElse(j) { "" },
                                            ocrK.digitsProbs.getOrElse(j) { "" },
                                            rects[i],
                                            ocrK.recB64.getOrElse(j) { "" },
                                            ocrK.recW.getOrElse(j) { 0 },
                                            ocrK.recH.getOrElse(j) { 0 },
                                        )
                                    }
                                }
                            }
                            val cvK = if (kk == 0f) officialCv else
                                PumpCostVolUtils.classifyCostVolFromBoxOcr(candsK)
                            variants.put(
                                ocrScaleVariantJson(
                                    kk, rects, quads, candsK, cvK, kind = "ink",
                                ),
                            )
                            val pads = emitRotHorizPad(
                                kk,
                                if (kk == 0f) inkWalkPadQuads
                                else quads.map { ContentExpandUtils.padOrientedU(it, 0.5f) },
                                if (kk == 0f) null else skipExtraK,
                            )
                            if (kk == 0f) pdPadQuads = pads
                        }
                        energyRects = expandedQuads.map { it.toAabb() }
                        energyCands = officialCands
                        energyCv = officialCv
                        branch.metadata["n_ocr_energy"] = nOcr.toString()
                        branch.metadata["t_ocr_count_ms"] = "0"
                        branch.metadata["n_ocr_count"] = "0"
                        branch.metadata["n_count_pull"] = "0"
                    } else {
                        energyRects = expandedQuads.map { it.toAabb() }
                        val energyOcr = ocrPumpOrientedQuads(expandedQuads, masterBuffer.p.mat, imgW, imgH)
                        energyCands = buildRedBoxCandidates(
                            energyRects, energyOcr.asis, energyOcr.digits,
                            energyOcr.asisProbs, energyOcr.digitsProbs, energyOcr.recB64,
                            recWList = energyOcr.recW, recHList = energyOcr.recH,
                        )
                        energyCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(energyCands)
                        variants.put(
                            ocrScaleVariantJson(
                                1.0f, energyRects, expandedQuads, energyCands, energyCv,
                                kind = "energy", hitCaps = hitCaps,
                            ),
                        )
                        val countQuads = expDiag.map { it.countQuad }
                        val countRects = countQuads.map { it.toAabb() }
                        val tOcrC0 = System.currentTimeMillis()
                        val countOcr = ocrPumpOrientedQuads(countQuads, masterBuffer.p.mat, imgW, imgH)
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
                    }
                    branch.metadata["t_ocr_energy_ms"] =
                        (System.currentTimeMillis() - tOcrE0).toString()

                    branch.metadata["t_ocr_g_ms"] = "0"
                    branch.metadata["n_ocr_g"] = "0"
                    val hybridQuads = ArrayList(expandedQuads)
                    val hybridCands = energyCands
                    val hybridCv = energyCv
                    val hybridRects = hybridQuads.map { it.toAabb() }
                    if (!seg7Stroke) {
                        pdPadQuads = expandedQuads.mapIndexed { i, q ->
                            ContentExpandUtils.padOrientedU(q, 0.5f, seedQuads.getOrNull(i))
                        }
                        val horizPadRects = pdPadQuads.map { it.toAabb() }
                        val horizPadOcr = ocrPumpOrientedQuads(pdPadQuads, masterBuffer.p.mat, imgW, imgH)
                        val horizPadCands = buildRedBoxCandidates(
                            horizPadRects, horizPadOcr.asis, horizPadOcr.digits,
                            horizPadOcr.asisProbs, horizPadOcr.digitsProbs, horizPadOcr.recB64,
                            recWList = horizPadOcr.recW, recHList = horizPadOcr.recH,
                        )
                        val horizPadCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(horizPadCands)
                        variants.put(
                            ocrScaleVariantJson(
                                0.5f, horizPadRects, pdPadQuads, horizPadCands, horizPadCv,
                                kind = "horiz_pad",
                            ),
                        )
                    }
                    val tOcrE = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                    val tOcrG = branch.metadata["t_ocr_g_ms"]?.toLongOrNull() ?: 0L
                    branch.metadata["t_ocr_ms"] = (tOcrE + tOcrG).toString()

                    // Calculated-vert sweep on every seed (G-style), for combo cover.
                    for (vv in vertSweep) {
                        val qV = seedQuads.map {
                            ContentExpandUtils.calculatedOriented(it, vv, SET_G_HORIZ_FACTOR)
                        }
                        val rV = qV.map { it.toAabb() }
                        val oV = ocrPumpOrientedQuads(qV, masterBuffer.p.mat, imgW, imgH)
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
                        experimentRecSet, paddleEngine, context, imgW, imgH, primaryCands,
                    )
                    branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                        reds = keptAabb,
                        ocrSourceRects = primaryRects,
                        candidates = primaryCands,
                        costCand = primaryCv.costCand,
                        volCand = primaryCv.volCand,
                        finalCost = primaryCv.cost,
                        finalVol = primaryCv.vol,
                        assembly = if (seg7Stroke) mapOf(
                            "method" to "oriented_independent",
                            "contentExpandMode" to "7seg_stroke",
                            "finalKind" to "ink",
                            "maxFrac" to 0.4f,
                            "k" to listOf(0, 1, 2, 3, 4),
                            "kOfficial" to 0,
                            "vertCapFrac" to ContentExpandUtils.SEG7_VERT_CAP_FRAC,
                            "gapFrac" to ContentExpandUtils.SEG7_GAP_FRAC,
                            "sPx" to inkStrokes.map { it.sPx },
                            "enableJump" to true,
                            "jumpFrac" to 0.60f,
                            "retractClearFrac" to 0.30f,
                            "energyRatio" to 0.65f,
                            "maxRetractFrac" to 0.50f,
                            "ocrScales" to scalesToOcr,
                            "finalOcrScale" to 1.0f,
                            "doDeskew" to false,
                            "useOriented" to true,
                            "orientedMerge" to "normal_sides",
                            "detModel" to (expDetAsset ?: "product_det"),
                            "vertFactors" to emptyList<Float>(),
                            "vertSweep" to emptyList<Float>(),
                            "hitVertCap" to hitCaps,
                            "note" to assemblyNote,
                            "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                        ) else mapOf(
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
                            "vertFactors" to emptyList<Float>(),
                            "vertSweep" to vertSweep,
                            "finalKind" to "energy",
                            "hitVertCap" to hitCaps,
                            "energyRatio" to energyRatio,
                            "freezeHorzDuringVert" to freezeHorzDuringVert,
                            "vertPadFrac" to vertPadFrac,
                            "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                            "maxRetractFrac" to 0.50f,
                            "note" to assemblyNote,
                        ),
                        oranges = emptyList(),
                        ocrQuads = primaryQuads,
                        seedQuads = seedQuads,
                        scaleVariants = variants,
                        inkSweeps = rotInkSweeps,
                    )
                    val redOnlyAnns = seedQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.RED, 2) }
                    branch.images["PD_red_only"] = OcrUtils.takeSnapshot(
                        workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
                        redOnlyAnns, null, workspace,
                    ).first
                    val aPd = redOnlyAnns +
                        primaryQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) } +
                        pdPadQuads.flatMap { pumpQuadEdgeAnns(it, AnnYuv.BLUE, 4) }
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
                    chi2K: Float = 3.5f,
                    vertPadFrac: Float = 0.0f,
                    seg7Stroke: Boolean = false,
                    detScales: List<Int> = prodDetScales,
                    chromaExpand: Boolean = false,
                    chromaMode: Int = 0,
                    boundStrategy: Int = 0,
                    aabbEnergy: ((org.opencv.core.Mat, org.opencv.core.Mat?, List<android.graphics.Rect>) -> List<ContentExpandUtils.AabbExpand>)? = null,
                    energyOrientNative: ((org.opencv.core.Mat, FloatArray, ShortArray?, org.opencv.core.Mat?) -> FloatArray?)? = null,
                    orientInk: ((
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<ContentExpandUtils.OrientedQuad>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                        org.opencv.core.Mat?,
                    ) -> List<ContentExpandUtils.Seg7OrientedExpand>)? = null,
                    boundNote: String? = null,
                    chromaNote: String? = null,
                ): suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit =
                    { ws, br, det, w, h ->
                        val aabbFn = aabbEnergy ?: if (!useOriented) {
                            ContentExpandUtils::expandEnergyAabbExpand
                        } else {
                            null
                        }
                        val orientNativeFn = energyOrientNative ?: if (useOriented && orientInk == null) {
                            NativeImageUtils::energyOrientExpandNative
                        } else {
                            energyOrientNative
                        }
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
                            tilt = photoTilt
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
                        if (boundNote != null) {
                            branch.metadata["content_expand_bound"] = boundNote
                        }
                        if (chromaNote != null) {
                            branch.metadata["content_expand_chroma"] = chromaNote
                        }
                        if (chromaMode == 4) {
                            branch.metadata["content_expand_chroma"] = "color_adaptive"
                        } else if (chromaExpand) {
                            branch.metadata["content_expand_chroma"] = "true"
                        }
                        if (boundStrategy != 0) {
                            branch.metadata["content_expand_bound"] =
                                if (boundStrategy == 2) "edge-retract" else "tight"
                        }
                        if (vertEnergy == ContentExpandUtils.VertEnergyKind.CHI2) {
                            branch.metadata["content_expand_chi2_k"] = chi2K.toString()
                        }
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
                                    seg7Stroke = seg7Stroke,
                                    detScales = detScales,
                                    chromaExpand = chromaExpand,
                                    chromaMode = chromaMode,
                                    boundStrategy = boundStrategy,
                                    energyOrientNative = orientNativeFn,
                                    orientInk = orientInk,
                                )
                            } else {
                            detScales.forEach { scale ->
                                val srcW = workspace.p.width
                                val srcH = workspace.p.height
                                val currentLongEdge = max(srcW, srcH)
                                val scaleFactor =
                                    if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                                val targetW = (srcW * scaleFactor).toInt()
                                val targetH = (srcH * scaleFactor).toInt()
                                PumpCostVolUtils.prepareScale(workspace, scale)
                                val paddleResults = PumpCostVolUtils.runDiscoveryPaddle(
                                    workspace, paddleEngine, targetW, targetH,
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
                            if (CAPTURE_REDBOX_DATA) {
                                captureRedboxData(pdHunksRawTotal, workspace, branch)
                            }

                            val expandOpts = ContentExpandUtils.ExpandOptions(
                                maxFrac = maxFrac,
                                enableJump = enableJump,
                                jumpFrac = jumpFrac,
                                energyRatio = energyRatio,
                                freezeHorzDuringVert = freezeHorzDuringVert,
                                vertEnergy = vertEnergy,
                                chi2K = chi2K,
                                vertPadFrac = vertPadFrac,
                                recordVertEnergy = energyTraceOut != null,
                                boundStrategy = boundStrategy,
                            )
                            val tExpand0 = System.currentTimeMillis()
                            val energyFn = aabbFn
                            val expDiag = if (energyFn != null) {
                                val expanded = energyFn(
                                    NativePaddleEngine.bufferSetA.p.mat,
                                    NativePaddleEngine.bufferSetA.p.uvMat,
                                    redPixelList,
                                )
                                val withJpeg = expanded.mapIndexed { i, d ->
                                    val seed = redPixelList.getOrNull(i) ?: return@mapIndexed d
                                    val jpeg = OcrUtils.takeSnapshotJpeg(
                                        workspace.s, seed, PUMP_CROP_TARGET_W, PUMP_CROP_TARGET_H,
                                        emptyList(), null, NativePaddleEngine.bufferSetB,
                                    ).first
                                    if (jpeg.isEmpty()) d else d.copy(sweep = d.sweep?.copy(threshJpeg = jpeg))
                                }
                                snapshotLookInk(
                                    redPixelList, withJpeg.map { it.rect }, imgW, imgH, branch,
                                    emptyList(), withJpeg.map { it.tele }, withJpeg.map { it.sweep },
                                    emptyList(),
                                    reportDir, timestamp, fullRow, branch.name,
                                    source = workspace.s.mat,
                                    scratchYuv = NativePaddleEngine.bufferSetB,
                                    energyLook = true,
                                )
                                withJpeg
                            } else {
                                ContentExpandUtils.expandDiagnoseMany(
                                    NativePaddleEngine.bufferSetA.p.mat,
                                    if (chromaExpand) NativePaddleEngine.bufferSetA.p.uvMat else null,
                                    redPixelList,
                                    mode,
                                    expandOpts,
                                ) ?: redPixelList.map { seed ->
                                    if (chromaExpand) {
                                        ContentExpandUtils.expandDiagnoseChroma(
                                            NativePaddleEngine.bufferSetA.p.mat,
                                            NativePaddleEngine.bufferSetA.p.uvMat,
                                            seed, mode, expandOpts,
                                        )
                                    } else {
                                        ContentExpandUtils.expandDiagnose(
                                            NativePaddleEngine.bufferSetA.p.mat,
                                            seed, mode, expandOpts,
                                        )
                                    }
                                }
                            }
                            branch.metadata["t_expand_ms"] =
                                (System.currentTimeMillis() - tExpand0).toString()
                            val expandedBase = expDiag.map { it.rect }
                            val hitCaps = expDiag.map { it.hitVertCap }
                            branch.metadata["n_hit_cap"] = hitCaps.count { it }.toString()
                            branch.metadata["n_ocr_energy"] = expandedBase.size.toString()
                            storeSeg7Tele(branch, expDiag.map { it.tele })
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
                            val hybridRects = ArrayList(expandedBase)
                            branch.metadata["t_ocr_g_ms"] = "0"
                            branch.metadata["n_ocr_g"] = "0"
                            val aabbHorizPad = expandedBase.map {
                                ContentExpandUtils.calculatedAabb(it, 0f, 0.6f, imgW, imgH)
                            }
                            val aabbPadOcr = ocrPumpRectsAsisAndDigits(aabbHorizPad)
                            val aabbPadCands = buildRedBoxCandidates(
                                aabbHorizPad, aabbPadOcr.asis, aabbPadOcr.digits,
                                aabbPadOcr.asisProbs, aabbPadOcr.digitsProbs, aabbPadOcr.recB64,
                                recWList = aabbPadOcr.recW, recHList = aabbPadOcr.recH,
                            )
                            val aabbPadCv = PumpCostVolUtils.classifyCostVolFromBoxOcr(aabbPadCands)
                            variants.put(
                                ocrScaleVariantJson(
                                    0.5f, aabbHorizPad, emptyList(), aabbPadCands, aabbPadCv,
                                    kind = "horiz_pad",
                                ),
                            )
                            val hybridPair = energyCands to energyCv
                            val tOcrEa = branch.metadata["t_ocr_energy_ms"]?.toLongOrNull() ?: 0L
                            val tOcrGa = branch.metadata["t_ocr_g_ms"]?.toLongOrNull() ?: 0L
                            branch.metadata["t_ocr_ms"] = (tOcrEa + tOcrGa).toString()
                            val primaryRects = hybridRects
                            val primaryCands = hybridPair.first
                            val primaryCv = hybridPair.second
                            val finalKind = "energy"
                            val scalesToOcr = ocrScales.ifEmpty { listOf(1.0f) }
                            val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)
                            branch.pathResults["Paddle"] = getFinal(
                                pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace,
                                experimentRecSet, paddleEngine, context, imgW, imgH, primaryCands,
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
                                    "vertFactors" to emptyList<Float>(),
                                    "finalKind" to finalKind,
                                    "hitVertCap" to hitCaps,
                                    "energyRatio" to energyRatio,
                                    "freezeHorzDuringVert" to freezeHorzDuringVert,
                                    "vertEnergy" to vertEnergy.name,
                                    "vertPadFrac" to vertPadFrac,
                                    "countPull" to "gx-run-count valley + one-dir pad; scaleVariants kind=energy_count",
                                    "maxRetractFrac" to 0.50f,
                                    "note" to assemblyNote,
                                    "inkTelemetry" to JSONArray(branch.metadata["seg7_tele"] ?: "[]"),
                                ) + if (vertEnergy == ContentExpandUtils.VertEnergyKind.CHI2) {
                                    mapOf("chi2K" to chi2K)
                                } else {
                                    emptyMap()
                                },
                                oranges = emptyList(),
                                scaleVariants = variants,
                                inkSweeps = expDiag.indices.map { i ->
                                    val r = primaryRects.getOrNull(i) ?: return@map expDiag[i].sweep
                                    expDiag[i].sweep?.withOfficial(r)
                                },
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
                            val padBlueHunks = aabbHorizPad.map { r ->
                                PumpHunk(
                                    "",
                                    RectF(
                                        r.left.toFloat(), r.top.toFloat(),
                                        r.right.toFloat(), r.bottom.toFloat(),
                                    ),
                                )
                            }
                            val aPd = getAnns(pdHunksRawTotal, AnnYuv.RED, 2) +
                                getAnns(blueHunks, AnnYuv.BLUE, 4) +
                                getAnns(padBlueHunks, AnnYuv.BLUE, 4)
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
                val procPRot = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "P-rot: one minAreaRect detect, no deskew, expandOriented, warp rec",
                    expDetAsset = null,
                    enableJump = false,
                    doDeskew = false,
                    useOriented = true,
                )
                val procProdJump = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "jump-prod: energy maxFrac=0.4; final = energy crop (cap is leash only)",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    energyRatio = 0.65f,
                )
                val procProdM65 = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "Prod-m65: product det + frozen-width mean |∇| 0.65 + 0.08 pad + L/R jump; maxFrac=2.5 so a short seed can grow; final = energy crop",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = 2.5f,
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertEnergy = ContentExpandUtils.VertEnergyKind.MAGNITUDE,
                    vertPadFrac = 0.08f,
                )
                val procProdRotInk = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "rot-ink-prod: product oriented det + AABB ink walk once; OCR k=0..4; official k=0; jump (no G-list)",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    vertSweep = emptyList(),
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.0f,
                    orientInk = ContentExpandUtils::expandGrayOrientExpand,
                )
                val procInkProdColor = makeInkAabbProc(
                    "ink-prod-color: product det + chromaMag 7seg (median<8 Y fallback); OCR k=0..4; official k=0",
                    ContentExpandUtils::expandColorAabbExpand,
                    chromaNote = "true",
                )
                val procInkProdColor2 = makeInkAabbProc(
                    "ink-prod-color2: product det + chroma tintMask 7seg (u_p·u_ink / Y polarity); OCR k=0..4; official k=0",
                    ContentExpandUtils::expandColorAabbExpand,
                    chromaNote = "color2",
                )
                val procInkProdWalk2 = makeInkAabbProc(
                    "ink-prod-walk2: product det + 7seg walk gap/peek 2.0s; freeze empty peek only if seedH>=4s; OCR k=0..4; official k=0",
                    ContentExpandUtils::expandGrayAabbExpand,
                )
                val procJumpProdColor = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "jump-prod-color: product fused |∇Y|+|∇C| maxFrac=0.4; L/R jump on fused hypot; final = expand crop",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    energyRatio = 0.65f,
                    aabbEnergy = ContentExpandUtils::expandEnergyAabbExpand,
                )
                val procRotInkProdColor = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "rot-ink-prod-color: product oriented det + chroma AABB ink walk; OCR k=0..4; official k=0",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    vertSweep = emptyList(),
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.0f,
                    orientInk = ContentExpandUtils::expandColorOrientExpand,
                    chromaNote = "true",
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
                    prodDetScales.forEach { scale ->
                        val srcW = workspace.p.width
                        val srcH = workspace.p.height
                        val currentLongEdge = max(srcW, srcH)
                        val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                        val targetW = (srcW * scaleFactor).toInt()
                        val targetH = (srcH * scaleFactor).toInt()
                        val targetLongEdge = max(targetW, targetH)
                        PumpCostVolUtils.prepareScale(workspace, scale)
                        val paddleResults = PumpCostVolUtils.runDiscoveryPaddle(workspace, paddleEngine, targetW, targetH, scale, branch.metadata)
                        pdHunksDetectedTotal.addAll(paddleResults[0])
                        pdHunksRawTotal.addAll(paddleResults[1])
                        pdHunksExpTotal.addAll(paddleResults[2])
                        pdHunksMaxTotal.addAll(paddleResults[3])
                        pdHunksNativeTotal.addAll(paddleResults[4])
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
                    val tilt = photoTilt
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
                    if (CAPTURE_REDBOX_DATA) {
                        captureRedboxData(lastReds, workspace, branch)
                    }
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
                    branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, lastReds, workspace, experimentRecSet, paddleEngine, context, imgW, imgH, allCands)
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
                    val aPdI = getAnns(lastReds, AnnYuv.RED, 2) + getAnns(lastBlueHunks, AnnYuv.BLUE, 4) + getAnns(lastOrangeHunks, AnnYuv.ORANGE, 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdI, null, workspace).first
                }
                fun inkEnergyAabb(
                    name: String,
                    aabb: (org.opencv.core.Mat, org.opencv.core.Mat?, List<android.graphics.Rect>) -> List<ContentExpandUtils.AabbExpand>,
                    boundNote: String?,
                ) = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "$name: product det + interior-energy AABB; jump; maxFrac=0.4",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = true,
                    useOriented = false,
                    ocrScales = pJumpOcrScales,
                    maxFrac = alignedExpandMaxFrac,
                    energyRatio = 0.65f,
                    aabbEnergy = aabb,
                    boundNote = boundNote,
                )
                fun rotEnergyOrient(
                    name: String,
                    native: (org.opencv.core.Mat, FloatArray, ShortArray?, org.opencv.core.Mat?) -> FloatArray?,
                    boundNote: String?,
                ) = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    "$name: product oriented det + interior-energy; jump ±u",
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    vertSweep = emptyList(),
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.0f,
                    energyOrientNative = native,
                    boundNote = boundNote,
                )
                fun rotInkOrient(
                    name: String,
                    expand: (
                        org.opencv.core.Mat,
                        org.opencv.core.Mat?,
                        List<ContentExpandUtils.OrientedQuad>,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        org.opencv.core.Mat?,
                        IntArray?,
                        org.opencv.core.Mat?,
                    ) -> List<ContentExpandUtils.Seg7OrientedExpand>,
                    chromaNote: String?,
                    boundNote: String?,
                ) = makeContentExpandProc(
                    ContentExpandUtils.Mode.INTERIOR_ENERGY,
                    name,
                    expDetAsset = null,
                    enableJump = true,
                    doDeskew = false,
                    useOriented = true,
                    ocrScales = pJumpOcrScales,
                    maxFrac = rotExpandMaxFrac,
                    vertSweep = emptyList(),
                    energyRatio = 0.65f,
                    freezeHorzDuringVert = true,
                    vertPadFrac = 0.0f,
                    orientInk = expand,
                    chromaNote = chromaNote,
                    boundNote = boundNote,
                )
                val procInkEnergyBase = inkEnergyAabb("ink-energy-base", ContentExpandUtils::expandEnergyAabbExpand, null)
                val procInkGrayBase = makeInkAabbProc(
                    "ink-gray-base: product det + greyscale Otsu 7seg; OCR k=0..4; official k=0",
                    ContentExpandUtils::expandGrayAabbExpand,
                )
                val procInkColorBase = makeInkAabbProc(
                    "ink-color-base: product det + color_adaptive 7seg; OCR k=0..4; official k=0",
                    ContentExpandUtils::expandColorAabbExpand,
                    chromaNote = "color_adaptive",
                )
                val procRotEnergyBase = rotEnergyOrient(
                    "rot-energy-base",
                    NativeImageUtils::energyOrientExpandNative,
                    null,
                )
                val procRotGrayBase = rotInkOrient(
                    "rot-gray-base: product oriented det + greyscale Otsu 7seg",
                    ContentExpandUtils::expandGrayOrientExpand,
                    null,
                    null,
                )
                val procRotColorBase = rotInkOrient(
                    "rot-color-base: product oriented det + color_adaptive 7seg",
                    ContentExpandUtils::expandColorOrientExpand,
                    "color_adaptive",
                    null,
                )
                val flowProcessors = buildList {
                    add("Set G-- (4 pass, none, calculated)" to procGMinusMinus)
                    add("Set ink-energy-tight" to procInkEnergyTight)
                    add("Set ink-energy-retract" to procInkEnergyRetract)
                    add("Set ink-color-tight" to procInkColorTight)
                    add("Set ink-color-retract" to procInkColorRetract)
                    add("Set rot-energy-tight" to procRotEnergyTight)
                    add("Set rot-energy-retract" to procRotEnergyRetract)
                    add("Set rot-color-tight" to procRotColorTight)
                    add("Set rot-color-retract" to procRotColorRetract)
                    add("Set ink-color-tight-vsp" to procInkColorTightVsp)
                    add("Set ink-color-retract-vsp" to procInkColorRetractVsp)
                    add("Set rot-color-tight-vsp" to procRotColorTightVsp)
                    add("Set rot-color-retract-vsp" to procRotColorRetractVsp)
                    add("Set aabb-tight" to procAabbTightSeedInk)
                    add("Set aabb-large" to procAabbLargeSeedInk)
                    add("Set rot-tight" to procRotTightSeedInk)
                    add("Set rot-large" to procRotLargeSeedInk)
                    add("Set aabb-tight-color" to procAabbTightSeedInkColor)
                    add("Set aabb-large-color" to procAabbLargeSeedInkColor)
                    add("Set rot-tight-color" to procRotTightSeedInkColor)
                    add("Set rot-large-color" to procRotLargeSeedInkColor)
                }
                // Parked (compiled, not scheduled): gray 7seg, prior ink-prod/color/walk2/jump, P*, L/M, G-dense/K, *-base.
                @Suppress("UNUSED_VARIABLE")
                val parked = listOf(
                    procInkGrayTight, procInkGrayRetract, procRotGrayTight, procRotGrayRetract,
                    procGDense, procK, procP, procPJump,
                    procPRot, procProdM65, procProdInk, procInkProdColor,
                    procInkProdColor2, procInkProdWalk2, procProdJump,
                    procJumpProdColor, procProdRotInk, procRotInkProdColor,
                    procInkEnergyBase, procInkGrayBase, procInkColorBase,
                    procRotEnergyBase, procRotGrayBase, procRotColorBase,
                ) +
                    procHorizByFactor.values + listOf(procL, procM)
                val processor = flowProcessors.firstOrNull { it.first == flowName }?.second
                    ?: error("No processor registered for flow: $flowName")

                tDiscoveryWrapperStart = System.currentTimeMillis()
                journal.append("COLUMN_START") {
                    put("col", col)
                    put("flow", flowName)
                    put("photo", index)
                    put("line", fullRow)
                    put("file", file.name)
                }
                val tCol0 = System.currentTimeMillis()
                processor(workspace, branch, discoveryDetails, imgW, imgH)
                journal.append("COLUMN_END") {
                    put("col", col)
                    put("flow", flowName)
                    put("elapsed_ms", System.currentTimeMillis() - tCol0)
                    put("photo", index)
                    put("line", fullRow)
                }
                val sortedCol = flowSorted.indexOf(flowName) + 1
                if (sortedCol > 0) {
                    pPublishFlowColumn(
                        rowIndex = fullRow,
                        photoIndex0 = index,
                        name = flowName,
                        br = branch,
                        colIdx = sortedCol,
                        maxRedBoxes = nKeepSlots,
                        imgDir = objImgRoot,
                        imgRel = imgRel,
                        cellsDir = cellsDir,
                        slotNames = slotNames,
                        nSlots = nSlots,
                        imgW = imgW,
                    )
                }
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

            pPublishOrigDetails(
                rowIndex = fullRow,
                photoIndex0 = index,
                imgW = imgW,
                imgH = imgH,
                isDegraded = meta.isDegraded,
                root = root,
                tDeskew = 0L,
                deskewHtml = deskewHtml,
                diagnostic = meta.diagnostic,
                imgDir = objImgRoot,
                cellsDir = cellsDir,
                slotNames = slotNames,
                nSlots = nSlots,
            )
            pPublishResultsSlots(
                rowIndex = fullRow,
                photoIndex0 = index,
                root = root,
                imgDir = objImgRoot,
                cellsDir = cellsDir,
                slotNames = slotNames,
                nSlots = nSlots,
            )

            val photoJson = pSerializePhotoResultToJson(
                fullRow, imgW, imgH, imgW, imgH, meta.isDegraded, meta.diagnostic, deskewResA, tSnapOrig, 0L, file.name, root, originalHistogram
            )

            logHeapState(context, "before-photo-json-serialize")
            Log.i("PUMP_FRAG", "row=$fullRow photoJson keys=${photoJson.length()}, writing frag...")
            val fragFile = getPhotoFragmentFile(reportDir, timestamp, fullRow)
            fragFile.bufferedWriter().use { writer ->
                appendJsonObject(writer, photoJson, 2, 0)
            }
            val fragSize = fragFile.length()
            Log.i("PUMP_FRAG", "row=$fullRow frag size=$fragSize bytes")

            val photoSb = StringBuilder()
            if (!firstPhoto) photoSb.append(",\n") else firstPhoto = false
            appendJsonObject(photoSb, photoJson, 2, 0)
            jsonSyncStr(photoSb.toString())
            journal.append("PHOTO_END") {
                put("photo", index)
                put("line", fullRow)
                put("file", file.name)
            }

            fragFile.delete()
            Log.i("PUMP_FRAG", "streamed row $fullRow to main JSON, deleted frag (size was $fragSize)")
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
            Log.e(TAG, "FATAL: Experiment failed for row $fullRow (${file.name}):\n" + Log.getStackTraceString(e))
            Log.w("PUMP_FRAG", "partial run - JSON may be incomplete (no final footer) at row $fullRow")
        }
    }
        journal.append("RUN_END")
    } finally {
        collapser.requestFinal("pump final collapse")
        try {
            collapseJob.join()
        } catch (_: CancellationException) {
        }
        collapser.drainAll()
        journal.close()
    }
    }

    jsonSyncStr(jsonFooter)
    try {
        jsonFos.fd.sync()
    } catch (_: Exception) {
    }
    jsonFos.close()
    logHeapState(context, "after-json-close")
    Log.i("PUMP_JSON", "wrote JSON footer and closed main JSON file")

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


/** u-axis angle of an oriented quad (flatter seed pick), normalized to [-90, 90] degrees. */
private fun pumpQuadLongAngleDeg(q: ContentExpandUtils.OrientedQuad): Float {
    return q.uAngleDeg()
}

/** Four LINE annotations along the quad edges (photo pixels). */
private fun pumpQuadEdgeAnns(
    q: ContentExpandUtils.OrientedQuad,
    color: AnnYuv,
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
private const val PUMP_LOOKINK_MAX_W = 500
private const val PUMP_CROP_TARGET_W = 150
private const val PUMP_CROP_TARGET_H = 75
private const val PUMP_C_VISUAL_TARGET_W = 340
private const val PUMP_SMALL_TARGET_W = 180
private const val PUMP_PER_RED_TARGET_W = 120
private const val PER_PHOTO_FRAGMENT_BUFFER_BYTES = 4 * 1024 * 1024

private fun lookInkStripRect(
    seed: android.graphics.Rect,
    walk: android.graphics.Rect,
    sPx: Int = 0,
    imgW: Int = 0,
    imgH: Int = 0,
    k4Horiz: Boolean = false,
): android.graphics.Rect {
    if (!k4Horiz || imgW < 1 || imgH < 1) {
        return android.graphics.Rect(
            seed.left,
            min(seed.top, walk.top),
            seed.right,
            max(seed.bottom, walk.bottom),
        )
    }
    val vPad = 4 * max(1, sPx)
    val t = min(seed.top - vPad, walk.top).coerceAtLeast(0)
    val b = max(seed.bottom + vPad, walk.bottom).coerceAtMost(imgH).coerceAtLeast(t + 1)
    val plusH = max(1, b - t)
    val padX = plusH
    val l = (seed.left - padX).coerceAtLeast(0)
    val r = (seed.right + padX).coerceAtMost(imgW).coerceAtLeast(l + 1)
    return android.graphics.Rect(l, t, r, b)
}

/** Dest-crop JPEG on scratch.s. visGain (energy ×8) applies to dest Y only, not source. */
private fun pumpEncodeSnapshot(
    source: Any,
    sourceRect: android.graphics.Rect?,
    destW: Int,
    destH: Int,
    anns: List<SnapshotAnnotation>,
    scratchYuv: BufferSet,
    visGain: Int = 1,
): ByteArray {
    var fw = destW.coerceAtLeast(2)
    var fh = destH.coerceAtLeast(2)
    fw = (fw + 1) / 2 * 2
    fh = (fh + 1) / 2 * 2
    val maxW = scratchYuv.s.width
    val maxH = scratchYuv.s.height
    if (fw > maxW || fh > maxH) {
        val fit = min(maxW.toDouble() / fw, maxH.toDouble() / fh)
        fw = ((fw * fit).toInt() / 2) * 2
        fh = ((fh * fit).toInt() / 2) * 2
    }
    fw = fw.coerceAtLeast(2)
    fh = fh.coerceAtLeast(2)
    val cropId = scratchYuv.s.createCrop(0, 0, fw, fh)
    try {
        val dest = scratchYuv.c[cropId]
        val srcY: Mat
        val srcUv: Mat?
        val srcW: Int
        val srcH: Int
        when (source) {
            is BufferSet.Slice -> {
                srcY = source.mat
                srcUv = source.uvMat
                srcW = source.width
                srcH = source.height
            }
            is Mat -> {
                if (source.type() != CvType.CV_8UC1) return ByteArray(0)
                srcY = source
                srcUv = null
                srcW = source.cols()
                srcH = source.rows()
            }
            else -> return ByteArray(0)
        }
        val roi = sourceRect ?: android.graphics.Rect(0, 0, srcW, srcH)
        if (roi.width() < 1 || roi.height() < 1) return ByteArray(0)
        val ok = NativeImageUtils.scaleYuvRoi(srcY, srcUv, roi, dest.mat, dest.uvMat)
        if (!ok) return ByteArray(0)
        if (visGain != 1) {
            dest.mat.convertTo(dest.mat, CvType.CV_8UC1, visGain.toDouble())
        }
        if (anns.isNotEmpty()) {
            val s = min(fw.toFloat() / roi.width(), fh.toFloat() / roi.height())
            val scaled = anns.map { ann ->
                ann.copy(
                    x1 = ((ann.x1 - roi.left) * s).toInt(),
                    y1 = ((ann.y1 - roi.top) * s).toInt(),
                    x2 = ((ann.x2 - roi.left) * s).toInt(),
                    y2 = ((ann.y2 - roi.top) * s).toInt(),
                )
            }
            NativeImageUtils.drawYuvAnnotations(dest.mat, dest.uvMat, scaled)
        }
        return NativeImageUtils.encodeYuvMatJpeg(dest.mat, dest.uvMat, 80)
    } finally {
        scratchYuv.c[cropId].release()
    }
}

/** Full-frame overlay JPEG (no box anns). Dest is scratch.s origin — call before PD dest. */
private suspend fun snapshotOverlayFull(
    source: Any,
    scratchYuv: BufferSet,
    branch: PumpBranch,
    energyVis: Boolean = false,
) {
    if (energyVis) {
        val jpeg = pumpEncodeSnapshot(
            source, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
            emptyList(), scratchYuv, visGain = 8,
        )
        branch.images["overlay"] = if (jpeg.isEmpty()) {
            ""
        } else {
            Base64.encodeToString(jpeg, Base64.NO_WRAP)
        }
    } else {
        branch.images["overlay"] = OcrUtils.takeSnapshot(
            source, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H,
            emptyList(), null, scratchYuv,
        ).first
    }
}

private fun seedInkLookDestSize(
    srcW: Int,
    srcH: Int,
    scaleByHeight: Boolean,
    destSlice: BufferSet.Slice,
): Pair<Int, Int> {
    if (srcW < 1 || srcH < 1) return 0 to 0
    fun even2(v: Int) = ((v + 1) / 2 * 2).coerceAtLeast(2)
    val axis = if (scaleByHeight) srcH else min(srcW, srcH)
    val scale = minOf(
        96.0 / axis.coerceAtLeast(1),
        PUMP_LOOKINK_MAX_W.toDouble() / srcW,
        destSlice.height.toDouble() / srcH,
        destSlice.width.toDouble() / srcW,
    )
    var destH0 = even2(ceil(srcH * scale).toInt())
    var destW0 = even2(ceil(srcW * scale).toInt())
    val maxW = destSlice.width
    val maxH = destSlice.height
    if (destW0 > maxW || destH0 > maxH) {
        val fit = min(maxW.toDouble() / destW0, maxH.toDouble() / destH0)
        destW0 = even2((destW0 * fit).toInt())
        destH0 = even2((destH0 * fit).toInt())
    }
    return destW0 to destH0
}

/** Per-threshold probe JPEG already encoded in native; height/short-axis 96. */
private fun snapshotSeedInkProbe(
    thr: ContentExpandUtils.SeedInkThr,
    boxN: Int,
    branch: PumpBranch,
    reportDir: File,
    timestamp: String,
    fullRow: Int,
    flowName: String,
) {
    val jpeg = thr.jpeg
    if (jpeg.isEmpty()) return
    val arr = try {
        org.json.JSONArray(branch.metadata["look_ink"] ?: "[]")
    } catch (_: Exception) {
        org.json.JSONArray()
    }
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    val recW = opts.outWidth.coerceAtLeast(0)
    val recH = opts.outHeight.coerceAtLeast(0)
    val imgDir = File(reportDir, "pump_imgs_$timestamp").also { it.mkdirs() }
    val flowSlug = flowName.filter { it.isLetterOrDigit() || it == '-' }.take(24)
    val fname = "r${fullRow}_${flowSlug}_look_box${boxN}_${thr.kind}_t${thr.t}_lo${thr.tLo}_hi${thr.tHi}.jpg"
    File(imgDir, fname).writeBytes(jpeg)
    arr.put(
        org.json.JSONObject()
            .put("label", "box$boxN")
            .put("lookInkFile", fname)
            .put("lookInkMime", "image/jpeg")
            .put("lookKind", "seed-probe")
            .put("row", fullRow)
            .put("flow", flowName)
            .put("recW", recW)
            .put("recH", recH)
            .put("t", thr.t)
            .put("tLo", thr.tLo)
            .put("tHi", thr.tHi)
            .put("sPx", thr.sPx)
            .put("kind", thr.kind)
            .put("probeMode", thr.kind)
            .put("seedW", thr.seedW)
            .put("seedH", thr.seedH)
            .put("seed", "${thr.seedW}x${thr.seedH}")
            .put("skipTint", thr.skipTint)
            .put("nBand", thr.nBand),
    )
    branch.metadata["look_ink"] = arr.toString()
}

/** Per-seed look-ink JPEG; spliced into HTML as data URI (no look_ink/ folder). */
private suspend fun snapshotLookInk(
    seeds: List<android.graphics.Rect>,
    walked: List<android.graphics.Rect>,
    imgW: Int,
    imgH: Int,
    branch: PumpBranch,
    poisons: List<ContentExpandUtils.PoisonDump?> = emptyList(),
    teles: List<ContentExpandUtils.Seg7Telemetry?> = emptyList(),
    sweeps: List<ContentExpandUtils.InkSweep?> = emptyList(),
    strokes: List<ContentExpandUtils.StrokeWidthInSeed?> = emptyList(),
    reportDir: File,
    timestamp: String,
    fullRow: Int,
    flowName: String,
    source: Any = NativePaddleEngine.bufferSetB.p,
    scratchYuv: BufferSet = NativePaddleEngine.bufferSetB,
    energyLook: Boolean = false,
) {
    val arr = try {
        org.json.JSONArray(branch.metadata["look_ink"] ?: "[]")
    } catch (_: Exception) {
        org.json.JSONArray()
    }
    val boxBase = arr.length()
    seeds.forEachIndexed { i, seed ->
        val walk = walked.getOrNull(i) ?: seed
        val stroke = strokes.getOrNull(i)
        val sweep = sweeps.getOrNull(i)
        val sPx = stroke?.sPx ?: sweep?.sPx?.toInt() ?: 0
        val official = ContentExpandUtils.padVertByStrokes(walk, seed, 0f, sPx, imgW, imgH)
        val k4 = ContentExpandUtils.padVertByStrokes(walk, seed, 4f, sPx, imgW, imgH)
        val k4H = max(1, k4.height())
        val halfW = (0.5f * k4H).roundToInt()
        val uL = (walk.left - halfW).coerceAtLeast(0)
        val uR = (walk.right + halfW).coerceAtMost(imgW).coerceAtLeast(uL + 1)
        val uT = minOf(seed.top, official.top, walk.top, k4.top)
        val uB = maxOf(seed.bottom, official.bottom, walk.bottom, k4.bottom)
        val cropH = max(1, uB - uT)
        val pad = ceil(
            RecBufferFeed.DEFAULT_BORDER_PX.toDouble() * cropH / RecBufferFeed.DEFAULT_REC_H,
        ).toInt().coerceAtLeast(1)
        val strip = android.graphics.Rect(
            uL,
            (uT - pad).coerceAtLeast(0),
            uR,
            (uB + pad).coerceAtMost(imgH).coerceAtLeast(1),
        )
        if (strip.width() < 1 || strip.height() < 1) return@forEachIndexed
        val tele = teles.getOrNull(i)
        val farL = (tele?.farL?.roundToInt() ?: walk.left).coerceAtLeast(0)
        val farR = (tele?.farR?.roundToInt() ?: walk.right).coerceAtMost(imgW).coerceAtLeast(farL + 1)
        val yellow = android.graphics.Rect(
            farL,
            minOf(seed.top, walk.top).coerceAtLeast(0),
            farR,
            maxOf(seed.bottom, walk.bottom).coerceAtMost(imgH).coerceAtLeast(1),
        )
        val anns = listOf(
            SnapshotAnnotation(
                yellow.left, yellow.top, yellow.right, yellow.bottom,
                Shape.RECTANGLE, AnnYuv.YELLOW, 2,
            ),
            SnapshotAnnotation(
                seed.left, seed.top, seed.right, seed.bottom,
                Shape.RECTANGLE, AnnYuv.RED, 2,
            ),
            SnapshotAnnotation(
                official.left, official.top, official.right, official.bottom,
                Shape.RECTANGLE, AnnYuv.BLUE, 4,
            ),
        )
        val seedH = max(1, seed.height())
        val stripH = max(1, strip.height())
        val stripW = max(1, strip.width())
        fun even2(v: Int) = ((v + 1) / 2 * 2).coerceAtLeast(2)
        val scale = minOf(
            96.0 / seedH,
            PUMP_LOOKINK_MAX_W.toDouble() / stripW,
            scratchYuv.s.height.toDouble() / stripH,
        )
        val destH0 = even2(ceil(stripH * scale).toInt())
        val destW0 = even2(ceil(stripW * scale).toInt())
        val jpeg = pumpEncodeSnapshot(
            source, strip, destW0, destH0, anns, scratchYuv,
            visGain = if (energyLook) 8 else 1,
        )
        if (jpeg.isEmpty()) return@forEachIndexed
        val boxN = boxBase + i + 1
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        val recW = opts.outWidth.coerceAtLeast(0)
        val recH = opts.outHeight.coerceAtLeast(0)
        val minRun = sweep?.minRun ?: 0
        val glareW = 5 * max(sPx, 4)
        val imgDir = File(reportDir, "pump_imgs_$timestamp").also { it.mkdirs() }
        val fname = "r${fullRow}_${flowName.filter { it.isLetterOrDigit() || it == '-' }.take(24)}_look_box$boxN.jpg"
        File(imgDir, fname).writeBytes(jpeg)
        val j = org.json.JSONObject()
            .put("label", "box$boxN")
            .put("lookInkFile", fname)
            .put("lookInkMime", "image/jpeg")
            .put("lookKind", if (energyLook) "energy" else "7seg")
            .put("row", fullRow)
            .put("flow", flowName)
            .put("recW", recW)
            .put("recH", recH)
            .put("minRun", minRun)
            .put("sPx", sPx)
            .put("glareW", glareW)
        if (tele != null) {
            j.put("gapJumpTop", tele.gapJumpTop)
            j.put("gapJumpBot", tele.gapJumpBot)
            if (tele.dispKind.isNotEmpty()) {
                j.put("dispKind", tele.dispKind)
                j.put("poisonHMul", tele.poisonHMul.toDouble())
                j.put("poisonVMul", tele.poisonVMul.toDouble())
                j.put("gapDriftP90S", tele.gapDriftP90S.toDouble())
            }
        }
        val inkSeed: Int
        val inkBlue: Int
        val inkYellow: Int
        if (energyLook) {
            inkSeed = countU8Gt0(source, seed)
            inkBlue = countU8Gt0(source, official)
            inkYellow = countU8Gt0(source, yellow)
        } else {
            inkSeed = tele?.nInkSeed?.roundToInt() ?: 0
            inkBlue = tele?.nInkBlue?.roundToInt() ?: 0
            inkYellow = tele?.nInkYellow?.roundToInt() ?: 0
        }
        j.put("inkSeed", inkSeed).put("inkBlue", inkBlue).put("inkYellow", inkYellow)
        if (!energyLook && tele != null) {
            j.put("nLookBinSeed", tele.nLookBinSeed.roundToInt())
            j.put("nRecoveredSeed", tele.nRecoveredSeed.roundToInt())
            j.put("fill", tele.fill)
            j.put("nRetry", tele.nRetry.roundToInt())
            j.put("retryWhy", tele.retryWhy.roundToInt())
            j.put("nValley", tele.nValley.roundToInt())
            putLookInkFillAttempts(j, tele)
        }
        val pd = poisons.getOrNull(i)
        if (pd != null) {
            j.put("bandTop", pd.bandTop)
            j.put("bandBot", pd.bandBot)
            j.put("bandH", pd.bandH)
            val ccArr = org.json.JSONArray()
            pd.ccs.forEach { cc ->
                if (cc.x < 0) return@forEach
                val hRun = cc.thr
                val vRun = cc.nInk
                val hMul = if (!energyLook && tele != null && tele.dispKind.isNotEmpty()) {
                    tele.poisonHMul.roundToInt().coerceAtLeast(1)
                } else {
                    6
                }
                val vMul = if (!energyLook && tele != null && tele.dispKind.isNotEmpty()) {
                    tele.poisonVMul.roundToInt().coerceAtLeast(1)
                } else {
                    16
                }
                val why = when {
                    hRun > hMul * sPx && vRun > vMul * sPx -> "wide+tall"
                    hRun > hMul * sPx -> "wide"
                    vRun > vMul * sPx -> "tall"
                    else -> ""
                }
                ccArr.put(
                    org.json.JSONObject()
                        .put("x", cc.x).put("y", cc.y).put("w", cc.w).put("h", cc.h)
                        .put("noPeak", cc.noPeak).put("thr", cc.thr).put("nInk", cc.nInk)
                        .put("hRun", hRun).put("vRun", vRun).put("why", why),
                )
            }
            j.put("ccs", ccArr)
            if (pd.vspSkip.isNotEmpty()) {
                val skip = org.json.JSONObject()
                pd.vspSkip.forEach { (k, v) -> skip.put(k, v) }
                j.put("vspSkip", skip)
            }
        }
        arr.put(j)
    }
    if (arr.length() > 0) branch.metadata["look_ink"] = arr.toString()
}

/** Plus-ROI in the seed warp frame: 4×sPx on ±v, union walk v, padU = plusH. */
private fun plusOrientedCrop(
    seed: ContentExpandUtils.OrientedQuad,
    walked: ContentExpandUtils.OrientedQuad,
    sPx: Int,
): Triple<ContentExpandUtils.OrientedQuad, Float, Float> {
    val so = ContentExpandUtils.orderQuadForWarp(seed)
        ?: return Triple(walked, 1f, 1f)
    val wp = walked.pts
    val tlx = so[0]
    val tly = so[1]
    val ux = so[2] - so[0]
    val uy = so[3] - so[1]
    val vx = so[6] - so[0]
    val vy = so[7] - so[1]
    val uLen = hypot(ux.toDouble(), uy.toDouble()).toFloat().coerceAtLeast(1f)
    val vLen = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1f)
    val unx = ux / uLen
    val uny = uy / uLen
    val vnx = vx / vLen
    val vny = vy / vLen
    var wV0 = Float.POSITIVE_INFINITY
    var wV1 = Float.NEGATIVE_INFINITY
    val n = min(4, wp.size / 2)
    for (i in 0 until n) {
        val v = (wp[i * 2] - tlx) * vnx + (wp[i * 2 + 1] - tly) * vny
        if (v < wV0) wV0 = v
        if (v > wV1) wV1 = v
    }
    val vPad = 4f * max(1, sPx)
    val v0 = min(-vPad, wV0)
    val v1 = max(vLen + vPad, wV1)
    val plusH = (v1 - v0).coerceAtLeast(1f)
    val u0 = -plusH
    val u1 = uLen + plusH
    fun c(u: Float, v: Float) = floatArrayOf(
        tlx + u * unx + v * vnx,
        tly + u * uny + v * vny,
    )
    val a = c(u0, v0)
    val b = c(u1, v0)
    val d = c(u1, v1)
    val e = c(u0, v1)
    val crop = ContentExpandUtils.OrientedQuad(
        floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]),
    )
    return Triple(crop, vLen, plusH)
}

private fun orientedLookUnionCrop(
    seed: ContentExpandUtils.OrientedQuad,
    official: ContentExpandUtils.OrientedQuad,
    walked: ContentExpandUtils.OrientedQuad,
    k4: ContentExpandUtils.OrientedQuad,
): Pair<ContentExpandUtils.OrientedQuad, Int>? {
    val so = ContentExpandUtils.orderQuadForWarp(seed) ?: return null
    val tlx = so[0]
    val tly = so[1]
    val ux = so[2] - so[0]
    val uy = so[3] - so[1]
    val vx = so[6] - so[0]
    val vy = so[7] - so[1]
    val uLen = hypot(ux.toDouble(), uy.toDouble()).toFloat().coerceAtLeast(1f)
    val vLen = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1f)
    val unx = ux / uLen
    val uny = uy / uLen
    val vnx = vx / vLen
    val vny = vy / vLen
    var u0 = Float.POSITIVE_INFINITY
    var u1 = Float.NEGATIVE_INFINITY
    var v0 = Float.POSITIVE_INFINITY
    var v1 = Float.NEGATIVE_INFINITY
    fun accum(q: ContentExpandUtils.OrientedQuad) {
        val p = q.pts
        val n = min(4, p.size / 2)
        for (i in 0 until n) {
            val dx = p[i * 2] - tlx
            val dy = p[i * 2 + 1] - tly
            val u = dx * unx + dy * uny
            val v = dx * vnx + dy * vny
            if (u < u0) u0 = u
            if (u > u1) u1 = u
            if (v < v0) v0 = v
            if (v > v1) v1 = v
        }
    }
    accum(seed)
    accum(official)
    accum(walked)
    accum(k4)
    var k4V0 = Float.POSITIVE_INFINITY
    var k4V1 = Float.NEGATIVE_INFINITY
    var wU0 = Float.POSITIVE_INFINITY
    var wU1 = Float.NEGATIVE_INFINITY
    fun span(q: ContentExpandUtils.OrientedQuad, onU: Boolean, onV: Boolean) {
        val p = q.pts
        val n = min(4, p.size / 2)
        for (i in 0 until n) {
            val dx = p[i * 2] - tlx
            val dy = p[i * 2 + 1] - tly
            val u = dx * unx + dy * uny
            val v = dx * vnx + dy * vny
            if (onU) {
                if (u < wU0) wU0 = u
                if (u > wU1) wU1 = u
            }
            if (onV) {
                if (v < k4V0) k4V0 = v
                if (v > k4V1) k4V1 = v
            }
        }
    }
    span(k4, onU = false, onV = true)
    span(walked, onU = true, onV = false)
    val k4V = (k4V1 - k4V0).coerceAtLeast(1f)
    u0 = wU0 - 0.5f * k4V
    u1 = wU1 + 0.5f * k4V
    val cropH = (v1 - v0).coerceAtLeast(1f)
    val pad = ceil(
        RecBufferFeed.DEFAULT_BORDER_PX.toDouble() * cropH / RecBufferFeed.DEFAULT_REC_H,
    ).toInt().coerceAtLeast(1)
    v0 -= pad
    v1 += pad
    fun c(u: Float, v: Float) = floatArrayOf(
        tlx + u * unx + v * vnx,
        tly + u * uny + v * vny,
    )
    val a = c(u0, v0)
    val b = c(u1, v0)
    val d = c(u1, v1)
    val e = c(u0, v1)
    val crop = ContentExpandUtils.OrientedQuad(
        floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]),
    )
    return crop to pad
}

private fun growOrientedByPad(
    seed: ContentExpandUtils.OrientedQuad,
    q: ContentExpandUtils.OrientedQuad,
    pad: Int,
): ContentExpandUtils.OrientedQuad {
    val so = ContentExpandUtils.orderQuadForWarp(seed) ?: return q
    val tlx = so[0]
    val tly = so[1]
    val ux = so[2] - so[0]
    val uy = so[3] - so[1]
    val vx = so[6] - so[0]
    val vy = so[7] - so[1]
    val uLen = hypot(ux.toDouble(), uy.toDouble()).toFloat().coerceAtLeast(1f)
    val vLen = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1f)
    val unx = ux / uLen
    val uny = uy / uLen
    val vnx = vx / vLen
    val vny = vy / vLen
    var u0 = Float.POSITIVE_INFINITY
    var u1 = Float.NEGATIVE_INFINITY
    var v0 = Float.POSITIVE_INFINITY
    var v1 = Float.NEGATIVE_INFINITY
    val p = q.pts
    val n = min(4, p.size / 2)
    for (i in 0 until n) {
        val dx = p[i * 2] - tlx
        val dy = p[i * 2 + 1] - tly
        val u = dx * unx + dy * uny
        val v = dx * vnx + dy * vny
        if (u < u0) u0 = u
        if (u > u1) u1 = u
        if (v < v0) v0 = v
        if (v > v1) v1 = v
    }
    val padF = pad.toFloat()
    u0 -= padF
    u1 += padF
    v0 -= padF
    v1 += padF
    fun c(u: Float, v: Float) = floatArrayOf(
        tlx + u * unx + v * vnx,
        tly + u * uny + v * vny,
    )
    val a = c(u0, v0)
    val b = c(u1, v0)
    val d = c(u1, v1)
    val e = c(u0, v1)
    return ContentExpandUtils.OrientedQuad(
        floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]),
    )
}

private fun destAabbOfQuad(
    q: ContentExpandUtils.OrientedQuad,
    crop: ContentExpandUtils.OrientedQuad,
    destW: Int,
    destH: Int,
): android.graphics.Rect {
    val co = ContentExpandUtils.orderQuadForWarp(crop)
        ?: return android.graphics.Rect(0, 0, destW, destH)
    val src = MatOfPoint2f(
        Point(co[0].toDouble(), co[1].toDouble()),
        Point(co[2].toDouble(), co[3].toDouble()),
        Point(co[4].toDouble(), co[5].toDouble()),
        Point(co[6].toDouble(), co[7].toDouble()),
    )
    val dst = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((destW - 1).toDouble().coerceAtLeast(0.0), 0.0),
        Point((destW - 1).toDouble().coerceAtLeast(0.0), (destH - 1).toDouble().coerceAtLeast(0.0)),
        Point(0.0, (destH - 1).toDouble().coerceAtLeast(0.0)),
    )
    val m = Imgproc.getPerspectiveTransform(src, dst)
    val pts = q.pts
    val n = min(4, pts.size / 2)
    val inPts = MatOfPoint2f(
        *Array(n) { i -> Point(pts[i * 2].toDouble(), pts[i * 2 + 1].toDouble()) },
    )
    val out = MatOfPoint2f()
    Core.perspectiveTransform(inPts, out, m)
    val arr = out.toArray()
    var minX = destW
    var minY = destH
    var maxX = 0
    var maxY = 0
    for (pt in arr) {
        val x = pt.x.roundToInt()
        val y = pt.y.roundToInt()
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    m.release()
    src.release()
    dst.release()
    inPts.release()
    out.release()
    minX = minX.coerceIn(0, (destW - 1).coerceAtLeast(0))
    minY = minY.coerceIn(0, (destH - 1).coerceAtLeast(0))
    maxX = maxX.coerceAtLeast(minX + 1).coerceAtMost(destW)
    maxY = maxY.coerceAtLeast(minY + 1).coerceAtMost(destH)
    return android.graphics.Rect(minX, minY, maxX, maxY)
}

/** Rot look-ink: warp B.p of union crop; dest not look-bin; seed → 96 px; dest-space rects. */
private suspend fun snapshotLookInkOriented(
    seed: ContentExpandUtils.OrientedQuad,
    walked: ContentExpandUtils.OrientedQuad,
    imgW: Int,
    imgH: Int,
    branch: PumpBranch,
    poison: ContentExpandUtils.PoisonDump?,
    tele: ContentExpandUtils.Seg7Telemetry?,
    sweep: ContentExpandUtils.InkSweep?,
    stroke: ContentExpandUtils.StrokeWidthInSeed?,
    reportDir: File,
    timestamp: String,
    fullRow: Int,
    flowName: String,
    isColor: Boolean = false,
) {
    val sPx = stroke?.sPx ?: sweep?.sPx?.toInt() ?: 0
    val official = ContentExpandUtils.padOrientedByStrokes(walked, seed, 0f, sPx)
    val k4 = ContentExpandUtils.padOrientedByStrokes(walked, seed, 4f, sPx)
    val union = orientedLookUnionCrop(seed, official, walked, k4) ?: return
    val cropQ = union.first
    val seedOrder = ContentExpandUtils.orderQuadForWarp(seed) ?: return
    val cropOrder = ContentExpandUtils.orderQuadForWarp(cropQ) ?: return
    val wSeed = hypot(
        (seedOrder[2] - seedOrder[0]).toDouble(),
        (seedOrder[3] - seedOrder[1]).toDouble(),
    ).coerceAtLeast(1.0)
    val hSeed = hypot(
        (seedOrder[6] - seedOrder[0]).toDouble(),
        (seedOrder[7] - seedOrder[1]).toDouble(),
    ).coerceAtLeast(1.0)
    val wUnion = hypot(
        (cropOrder[2] - cropOrder[0]).toDouble(),
        (cropOrder[3] - cropOrder[1]).toDouble(),
    ).coerceAtLeast(1.0)
    val hUnion = hypot(
        (cropOrder[6] - cropOrder[0]).toDouble(),
        (cropOrder[7] - cropOrder[1]).toDouble(),
    ).coerceAtLeast(1.0)
    fun even2(v: Int) = ((v + 1) / 2 * 2).coerceAtLeast(2)
    val destSlice = NativePaddleEngine.bufferSetB.s
    val scale = minOf(
        96.0 / hSeed,
        PUMP_LOOKINK_MAX_W.toDouble() / wUnion,
        destSlice.height.toDouble() / hUnion,
    )
    var destH = even2(ceil(hUnion * scale).toInt())
    var destW = even2(ceil(wUnion * scale).toInt())
    var cropId = destSlice.createCrop(0, 0, destW, destH)
    var dest = NativePaddleEngine.bufferSetB.c[cropId]
    if (dest.mat.cols() < destW || dest.width < destW) {
        destW = dest.mat.cols().coerceAtLeast(2)
        destH = even2((destW * hUnion / wUnion).roundToInt())
    }
    if (dest.mat.rows() < destH || dest.height < destH) {
        destH = dest.mat.rows().coerceAtLeast(2)
        destW = even2((destH * wUnion / hUnion).roundToInt())
    }
    if (dest.mat.cols() != destW || dest.width != destW ||
        dest.mat.rows() != destH || dest.height != destH
    ) {
        NativePaddleEngine.bufferSetB.c[cropId].release()
        cropId = destSlice.createCrop(0, 0, destW, destH)
        dest = NativePaddleEngine.bufferSetB.c[cropId]
    }
    dest.clear()
    val ok = try {
        ContentExpandUtils.warpQuadToHorizontalStrip(
            NativePaddleEngine.bufferSetB.p.mat, cropQ, dest.mat, targetH = 0,
        )
    } catch (_: Throwable) {
        false
    }
    if (!ok || dest.mat.empty() || dest.mat.cols() < 1 || dest.mat.rows() < 1) {
        NativePaddleEngine.bufferSetB.c[cropId].release()
        return
    }
    destW = dest.mat.cols()
    destH = dest.mat.rows()
    val srcUv = NativePaddleEngine.bufferSetB.p.uvMat
    val dstUv = dest.uvMat
    if (!srcUv.empty() && !dstUv.empty() && dstUv.cols() >= 1 && dstUv.rows() >= 1) {
        val order = ContentExpandUtils.orderQuadForWarp(cropQ)
        if (order != null) {
            val src = MatOfPoint2f(
                Point(order[0] / 2.0, order[1] / 2.0),
                Point(order[2] / 2.0, order[3] / 2.0),
                Point(order[4] / 2.0, order[5] / 2.0),
                Point(order[6] / 2.0, order[7] / 2.0),
            )
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((dstUv.cols() - 1).toDouble().coerceAtLeast(0.0), 0.0),
                Point(
                    (dstUv.cols() - 1).toDouble().coerceAtLeast(0.0),
                    (dstUv.rows() - 1).toDouble().coerceAtLeast(0.0),
                ),
                Point(0.0, (dstUv.rows() - 1).toDouble().coerceAtLeast(0.0)),
            )
            val hm = Imgproc.getPerspectiveTransform(src, dstPts)
            try {
                Imgproc.warpPerspective(
                    srcUv, dstUv, hm, dstUv.size(),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(128.0, 128.0),
                )
            } finally {
                hm.release()
                src.release()
                dstPts.release()
            }
        }
    }
    val redR = destAabbOfQuad(seed, cropQ, destW, destH)
    val blueR = destAabbOfQuad(official, cropQ, destW, destH)
    val yellowQ = if (tele != null) {
        ContentExpandUtils.lookInkJumpFarQuad(seed, walked, tele.farL, tele.farR)
    } else {
        walked
    }
    val yellowR = destAabbOfQuad(yellowQ, cropQ, destW, destH)
    val anns = listOf(
        SnapshotAnnotation(
            yellowR.left, yellowR.top, yellowR.right, yellowR.bottom,
            Shape.RECTANGLE, AnnYuv.YELLOW, 2,
        ),
        SnapshotAnnotation(
            redR.left, redR.top, redR.right, redR.bottom,
            Shape.RECTANGLE, AnnYuv.RED, 2,
        ),
        SnapshotAnnotation(
            blueR.left, blueR.top, blueR.right, blueR.bottom,
            Shape.RECTANGLE, AnnYuv.BLUE, 4,
        ),
    )
    NativeImageUtils.drawYuvAnnotations(dest.mat, dest.uvMat, anns)
    val jpeg = try {
        NativeImageUtils.encodeYuvMatJpeg(dest.mat, dest.uvMat, 80)
    } finally {
        NativePaddleEngine.bufferSetB.c[cropId].release()
    }
    if (jpeg.isEmpty()) return
    val arr = try {
        org.json.JSONArray(branch.metadata["look_ink"] ?: "[]")
    } catch (_: Exception) {
        org.json.JSONArray()
    }
    val boxN = arr.length() + 1
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    val recW = opts.outWidth.coerceAtLeast(0)
    val recH = opts.outHeight.coerceAtLeast(0)
    val minRun = sweep?.minRun ?: 0
    val glareW = 5 * max(sPx, 4)
    val imgDir = File(reportDir, "pump_imgs_$timestamp").also { it.mkdirs() }
    val fname = "r${fullRow}_${flowName.filter { it.isLetterOrDigit() || it == '-' }.take(24)}_look_box$boxN.jpg"
    File(imgDir, fname).writeBytes(jpeg)
    val j = org.json.JSONObject()
        .put("label", "box$boxN")
        .put("lookInkFile", fname)
        .put("lookInkMime", "image/jpeg")
        .put("lookKind", "7seg")
        .put("row", fullRow)
        .put("flow", flowName)
        .put("recW", recW)
        .put("recH", recH)
        .put("minRun", minRun)
        .put("sPx", sPx)
        .put("glareW", glareW)
    if (tele != null) {
        j.put("gapJumpTop", tele.gapJumpTop)
        j.put("gapJumpBot", tele.gapJumpBot)
        if (tele.dispKind.isNotEmpty()) {
            j.put("dispKind", tele.dispKind)
            j.put("poisonHMul", tele.poisonHMul.toDouble())
            j.put("poisonVMul", tele.poisonVMul.toDouble())
            j.put("gapDriftP90S", tele.gapDriftP90S.toDouble())
        }
    }
    j.put("inkSeed", tele?.nInkSeed?.roundToInt() ?: 0)
        .put("inkBlue", tele?.nInkBlue?.roundToInt() ?: 0)
        .put("inkYellow", tele?.nInkYellow?.roundToInt() ?: 0)
    if (tele != null) {
        j.put("nLookBinSeed", tele.nLookBinSeed.roundToInt())
        j.put("nRecoveredSeed", tele.nRecoveredSeed.roundToInt())
        j.put("fill", tele.fill)
        j.put("nRetry", tele.nRetry.roundToInt())
        j.put("retryWhy", tele.retryWhy.roundToInt())
        j.put("nValley", tele.nValley.roundToInt())
        putLookInkFillAttempts(j, tele)
    }
    if (poison != null) {
        j.put("bandTop", poison.bandTop)
        j.put("bandBot", poison.bandBot)
        j.put("bandH", poison.bandH)
        val ccArr = org.json.JSONArray()
        poison.ccs.forEach { cc ->
            if (cc.x < 0) return@forEach
            val hRun = cc.thr
            val vRun = cc.nInk
            val hMul = if (tele != null && tele.dispKind.isNotEmpty()) {
                tele.poisonHMul.roundToInt().coerceAtLeast(1)
            } else {
                6
            }
            val vMul = if (tele != null && tele.dispKind.isNotEmpty()) {
                tele.poisonVMul.roundToInt().coerceAtLeast(1)
            } else {
                16
            }
            val why = when {
                hRun > hMul * sPx && vRun > vMul * sPx -> "wide+tall"
                hRun > hMul * sPx -> "wide"
                vRun > vMul * sPx -> "tall"
                else -> ""
            }
            ccArr.put(
                org.json.JSONObject()
                    .put("x", cc.x).put("y", cc.y).put("w", cc.w).put("h", cc.h)
                    .put("noPeak", cc.noPeak).put("thr", cc.thr).put("nInk", cc.nInk)
                    .put("hRun", hRun).put("vRun", vRun).put("why", why),
            )
        }
        j.put("ccs", ccArr)
        if (poison.vspSkip.isNotEmpty()) {
            val skip = org.json.JSONObject()
            poison.vspSkip.forEach { (k, v) -> skip.put(k, v) }
            j.put("vspSkip", skip)
        }
    }
    arr.put(j)
    branch.metadata["look_ink"] = arr.toString()
}

private fun seedVRowsInWarp(
    seed: ContentExpandUtils.OrientedQuad,
    crop: ContentExpandUtils.OrientedQuad,
    destW: Int,
    destH: Int,
): Pair<Int, Int> {
    val co = ContentExpandUtils.orderQuadForWarp(crop) ?: return 0 to destH - 1
    val so = ContentExpandUtils.orderQuadForWarp(seed) ?: return 0 to destH - 1
    val src = MatOfPoint2f(
        Point(co[0].toDouble(), co[1].toDouble()),
        Point(co[2].toDouble(), co[3].toDouble()),
        Point(co[4].toDouble(), co[5].toDouble()),
        Point(co[6].toDouble(), co[7].toDouble()),
    )
    val dst = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((destW - 1).toDouble(), 0.0),
        Point((destW - 1).toDouble(), (destH - 1).toDouble()),
        Point(0.0, (destH - 1).toDouble()),
    )
    val m = Imgproc.getPerspectiveTransform(src, dst)
    val seedPts = MatOfPoint2f(
        Point(so[0].toDouble(), so[1].toDouble()),
        Point(so[6].toDouble(), so[7].toDouble()),
    )
    val out = MatOfPoint2f()
    Core.perspectiveTransform(seedPts, out, m)
    val a = out.toArray()
    val yT = a[0].y.roundToInt().coerceIn(0, destH - 1)
    val yB = a[1].y.roundToInt().coerceIn(0, destH - 1)
    m.release()
    src.release()
    dst.release()
    seedPts.release()
    out.release()
    return yT to yB
}

private fun putLookInkFillAttempts(j: org.json.JSONObject, tele: ContentExpandUtils.Seg7Telemetry) {
    j.put("nKeep", tele.nKeep.roundToInt())
    j.put("nPoison", tele.nPoison.roundToInt())
    j.put("firstThr", tele.firstThr.roundToInt())
    val arr = org.json.JSONArray()
    tele.attempts.forEachIndexed { i, a ->
        val histRaw = org.json.JSONArray()
        a.histRaw.forEach { histRaw.put(it) }
        val valleys = org.json.JSONArray()
        a.valleys.forEach { valleys.put(it) }
        val skipped = org.json.JSONArray()
        a.skipped.forEach { skipped.put(it) }
        arr.put(
            org.json.JSONObject()
                .put("i", i)
                .put("kind", a.kind.roundToInt())
                .put("firstThr", a.firstThr.roundToInt())
                .put("thr", a.thr.roundToInt())
                .put("dark", a.dark.roundToInt())
                .put("flip", a.flip.roundToInt())
                .put("nLookBin", a.nLookBin.roundToInt())
                .put("nRecovered", a.nRecovered.roundToInt())
                .put("fill", a.fill)
                .put("nKeep", a.nKeep.roundToInt())
                .put("nValley", a.nValley.roundToInt())
                .put("nPoison", a.nPoison.roundToInt())
                .put("sPx", a.sPx.roundToInt())
                .put("acceptedSpx", a.acceptedSpx.roundToInt())
                .put("inBand", a.inBand.roundToInt())
                .put("kept", a.kept.roundToInt())
                .put("inkFracDark", a.inkFracDark.toDouble())
                .put("inverted", a.inverted.roundToInt())
                .put("cleanDark", a.cleanDark.roundToInt())
                .put("cleanInkFrac", a.cleanInkFrac.toDouble())
                .put("vSW", a.vSW.roundToInt())
                .put("strokeShare", a.strokeShare.toDouble())
                .put("maxRunOverW", a.maxRunOverW.toDouble())
                .put("needVsw", a.needVsw.roundToInt())
                .put("needInkFrac", a.needInkFrac.roundToInt())
                .put("needShare", a.needShare.roundToInt())
                .put("needMaxRun", a.needMaxRun.roundToInt())
                .put("hist_raw", histRaw)
                .put("hist_raw_tail", a.histRawTail.roundToInt())
                .put("valleys", valleys)
                .put("skipped", skipped)
                .put("stop", when (a.stop.roundToInt()) {
                    1 -> "take"
                    2 -> "noCand"
                    3 -> "maxAttempts"
                    else -> "none"
                }),
        )
    }
    j.put("attempts", arr)
}

private fun lookInkCountCap(c: org.json.JSONObject): String {
    if (c.optString("lookKind") == "seed-probe") {
        val kind = c.optString("kind", c.optString("probeMode", "gt"))
        val sPx = c.optInt("sPx", 0)
        val seed = "seed=${c.optInt("seedW", 0)}x${c.optInt("seedH", 0)}"
        val skip = if (c.optBoolean("skipTint", false)) " skipTint" else ""
        return when (kind) {
            "gt" -> "gt t=${c.optInt("t", 0)} sPx=$sPx $seed"
            "band" -> "band ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed"
            "union" -> "union sPx=$sPx nBand=${c.optInt("nBand", 0)} $seed"
            "cband" -> "cband ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed$skip"
            "cunion" -> "cunion sPx=$sPx nBand=${c.optInt("nBand", 0)} $seed$skip"
            "pick" -> if (c.optInt("nBand", 0) == 1) {
                "pick Band ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed"
            } else {
                "pick Light t=${c.optInt("t", 0)} sPx=$sPx $seed"
            }
            "flood" -> if (c.optInt("nBand", 0) == 1) {
                "flood Band ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed"
            } else {
                "flood Light t=${c.optInt("t", 0)} sPx=$sPx $seed"
            }
            "cpick" -> "cpick Band ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed$skip"
            "cflood" -> "cflood Band ${c.optInt("tLo", 0)}-${c.optInt("tHi", 0)} sPx=$sPx $seed$skip"
            else -> "$kind t=${c.optInt("t", 0)} sPx=$sPx $seed"
        }
    }
    val base = "inkSeed=${c.optInt("inkSeed", 0)} inkBlue=${c.optInt("inkBlue", 0)} inkYellow=${c.optInt("inkYellow", 0)}"
    val att = c.optJSONArray("attempts") ?: return base
    if (att.length() < 1) return base
    val parts = ArrayList<String>(att.length())
    for (i in 0 until att.length()) {
        val a = att.optJSONObject(i) ?: continue
        val fill = a.optDouble("fill", 0.0)
        parts.add(
            "$i:${a.optInt("kind")},${a.optInt("thr")},${String.format(java.util.Locale.US, "%.3f", fill)}," +
                "inv=${a.optInt("inverted")},needVsw=${a.optInt("needVsw")}," +
                "needInk=${a.optInt("needInkFrac")},needShare=${a.optInt("needShare")}," +
                "needMaxRun=${a.optInt("needMaxRun")},vSW=${a.optInt("vSW")}," +
                "stop=${a.optString("stop")}",
        )
    }
    return if (parts.isEmpty()) base else "$base ${parts.joinToString(" ")}"
}

private fun histRawBarsHtml(att: org.json.JSONArray?): String {
    if (att == null || att.length() < 1) return ""
    val cell = "padding:1px 3px;border:1px solid #ddd;"
    val sb = StringBuilder()
    for (i in 0 until att.length()) {
        val a = att.optJSONObject(i) ?: continue
        val hr = a.optJSONArray("hist_raw") ?: continue
        val show = ArrayList<Int>()
        for (k in 0 until hr.length()) {
            if (hr.optInt(k) > 0) show.add(k)
        }
        if (show.isEmpty() && a.optInt("hist_raw_tail", 0) <= 0) continue
        sb.append("<table style='border-collapse:collapse;font-size:8px;margin:2px 0;text-align:center;'>")
        sb.append("<tr><th style='$cell'>att$i k</th>")
        for (k in show) sb.append("<th style='$cell'>${k + 1}</th>")
        if (a.optInt("hist_raw_tail", 0) > 0) sb.append("<th style='$cell'>257+</th>")
        sb.append("</tr><tr><th style='$cell'>n</th>")
        for (k in show) sb.append("<td style='$cell'>${hr.optInt(k)}</td>")
        if (a.optInt("hist_raw_tail", 0) > 0) {
            sb.append("<td style='$cell'>${a.optInt("hist_raw_tail")}</td>")
        }
        sb.append("</tr></table>")
    }
    return sb.toString()
}

private fun countU8Gt0(source: Any, rect: android.graphics.Rect): Int {
    val mat = when (source) {
        is BufferSet.Slice -> source.mat
        is Mat -> source
        else -> return 0
    }
    if (mat.empty() || mat.type() != CvType.CV_8UC1) return 0
    val l = rect.left.coerceAtLeast(0)
    val t = rect.top.coerceAtLeast(0)
    val r = rect.right.coerceAtMost(mat.cols())
    val b = rect.bottom.coerceAtMost(mat.rows())
    if (r <= l || b <= t) return 0
    val roi = mat.submat(t, b, l, r)
    return try {
        Core.countNonZero(roi)
    } finally {
        roi.release()
    }
}

private fun pLookInkArr(br: PumpBranch): org.json.JSONArray {
    val raw = br.metadata["look_ink"] ?: return org.json.JSONArray()
    return try {
        org.json.JSONArray(raw)
    } catch (_: Exception) {
        org.json.JSONArray()
    }
}

private fun pLookInkBoxHtml(br: PumpBranch, k: Int, imgRel: String): String {
    val arr = pLookInkArr(br)
    val sb = StringBuilder()
    for (j in 0 until arr.length()) {
        val c = arr.optJSONObject(j) ?: continue
        val lab = c.optString("label")
        if (lab != "box$k" && lab != "box${k}") continue
        val file = c.optString("lookInkFile")
        if (file.isNullOrEmpty()) continue
        val cap = "$lab ${lookInkCountCap(c)}"
        sb.append(pumpImgTag("$imgRel/$file", "height:auto;", cap))
    }
    if (sb.isEmpty()) return ""
    return "<div style='display:flex;flex-wrap:wrap;gap:3px;'>$sb</div>"
}

private fun pOfficialRecBoxHtml(
    br: PumpBranch, k: Int, imgDir: File, rowIndex: Int, colIdx: Int,
): String {
    val data = try {
        org.json.JSONObject(br.metadata["costVolDecisionData_Paddle"] ?: return "")
    } catch (_: Exception) {
        return ""
    }
    val want = "box$k"
    fun fromCands(cands: org.json.JSONArray?): String {
        if (cands == null) return ""
        for (j in 0 until cands.length()) {
            val c = cands.optJSONObject(j) ?: continue
            if (c.optString("label") != want) continue
            val b64 = c.optString("_htmlRec")
            if (b64.isNullOrEmpty()) return ""
            val asis = c.optString("asis")
            val dig = c.optString("digits")
            val src = pumpPersistJpeg(imgDir, "r${rowIndex}_c${colIdx}_rec_box$k.jpg", b64)
            val cap = "$want <span style='font-size:12px;'>asis=$asis dig=$dig</span>"
            return pumpImgTag(src, "height:48px;width:auto;", cap)
        }
        return ""
    }
    val variants = data.optJSONArray("scaleVariants")
    if (variants != null) {
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            val kind = v.optString("kind")
            val s = v.optDouble("s", Double.NaN)
            val officialInk = kind == "ink" && (s.isNaN() || s == 0.0)
            if (!officialInk && kind != "energy") continue
            val hit = fromCands(v.optJSONArray("candidates"))
            if (hit.isNotEmpty()) return hit
        }
    }
    return fromCands(data.optJSONArray("candidates"))
}

private fun pRecExtraHtml(br: PumpBranch, imgDir: File, rowIndex: Int, colIdx: Int): String {
    val raw = br.metadata["costVolDecisionData_Paddle"] ?: return ""
    val data = try {
        org.json.JSONObject(raw)
    } catch (_: Exception) {
        return ""
    }
    val sb = StringBuilder()
    fun emitCands(cands: org.json.JSONArray, heading: String, kind: String, s: Double) {
        var any = false
        val chunk = StringBuilder()
        chunk.append("<div><small>$heading</small></div>")
        chunk.append("<div style='display:flex;flex-wrap:wrap;gap:3px;'>")
        val sTok = if (s.isNaN()) "na" else s.toString()
        for (j in 0 until cands.length()) {
            val c = cands.optJSONObject(j) ?: continue
            val b64 = c.optString("_htmlRec")
            if (b64.isNullOrEmpty()) continue
            any = true
            val lab = c.optString("label")
            val asis = c.optString("asis")
            val dig = c.optString("digits")
            val src = pumpPersistJpeg(
                imgDir,
                "r${rowIndex}_c${colIdx}_recextra_${kind}_s${sTok}_${lab}.jpg",
                b64,
            )
            val cap = "$lab <span style='font-size:12px;'>asis=$asis dig=$dig</span>"
            chunk.append(
                "<div style='flex:0 0 auto;font-size:9px;'>" +
                    pumpImgTag(src, "height:48px;width:auto;", cap) +
                    "</div>",
            )
        }
        chunk.append("</div>")
        if (any) sb.append(chunk)
    }
    val variants = data.optJSONArray("scaleVariants")
    if (variants != null && variants.length() > 0) {
        var header = false
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            val kind = v.optString("kind")
            if (kind == "energy_or_g") continue
            val s = v.optDouble("s", Double.NaN)
            val officialInk = kind == "ink" && (s.isNaN() || s == 0.0)
            if (officialInk || kind == "energy") continue
            val cands = v.optJSONArray("candidates") ?: continue
            if (!header) {
                sb.append("<div class='rec-crops' style='margin-top:6px;text-align:left;'><b>Rec extra</b>")
                header = true
            }
            emitCands(cands, recVariantHeading(kind, v), kind, s)
        }
        if (header) sb.append("</div>")
    }
    return sb.toString()
}

private fun pChosenSeedLabels(br: PumpBranch): Pair<String, String> {
    val data = try {
        org.json.JSONObject(br.metadata["costVolDecisionData_Paddle"] ?: return "" to "")
    } catch (_: Exception) {
        return "" to ""
    }
    val chosen = data.optJSONObject("chosen") ?: return "" to ""
    fun lab(key: String): String {
        val o = chosen.optJSONObject(key) ?: return ""
        val s = o.optString("label")
        return s.removePrefix("box")
    }
    return lab("cost") to lab("vol")
}

private fun pLookInkHtml(br: PumpBranch): String {
    val raw = br.metadata["look_ink"] ?: return ""
    val arr = try {
        org.json.JSONArray(raw)
    } catch (_: Exception) {
        return ""
    }
    if (arr.length() == 0) return ""
    val sb = StringBuilder()
    sb.append("<div class='look-ink-crops' style='margin-top:6px;text-align:left;'><b>Look ink</b>")
    sb.append("<div style='display:flex;flex-wrap:wrap;gap:3px;'>")
    var any = false
    for (j in 0 until arr.length()) {
        val c = arr.optJSONObject(j) ?: continue
        val b64 = c.optString("lookInkB64")
        if (b64.isNullOrEmpty()) continue
        any = true
        val lab = c.optString("label")
        val recW = c.optInt("recW", 0)
        val recH = c.optInt("recH", 0)
        val wCss = if (recW > 0) "width:${recW}px;" else "width:auto;"
        val hCss = if (recH > 0) "height:${recH}px;" else "height:auto;"
        val energy = c.optString("lookKind") == "energy"
        val seedProbe = c.optString("lookKind") == "seed-probe"
        val meta = if (seedProbe) {
            lookInkCountCap(c)
        } else if (energy) {
            "${lookInkCountCap(c)} energy U8"
        } else {
            val bandTop = c.optBoolean("bandTop", false)
            val bandBot = c.optBoolean("bandBot", false)
            val ccs = c.optJSONArray("ccs")
            val ccBits = StringBuilder()
            if (ccs != null) {
                for (k in 0 until ccs.length()) {
                    val cc = ccs.optJSONObject(k) ?: continue
                    if (ccBits.isNotEmpty()) ccBits.append("; ")
                    ccBits.append("cc${k + 1} noPeak=${cc.optBoolean("noPeak")} nInk=${cc.optInt("nInk")}")
                }
            }
            val gjt = c.optBoolean("gapJumpTop", false)
            val gjb = c.optBoolean("gapJumpBot", false)
            val gapCap = buildString {
                if (gjt) append(" gapJumpTop")
                if (gjb) append(" gapJumpBot")
            }
            val minRun = c.optInt("minRun", 0)
            val sPx = c.optInt("sPx", 0)
            val glareW = c.optInt("glareW", 0)
            "${lookInkCountCap(c)} minRun=$minRun sPx=$sPx glareW=$glareW bandTop=$bandTop bandBot=$bandBot$gapCap" +
                if (ccBits.isNotEmpty()) " $ccBits" else ""
        }
        sb.append(
            "<div style='flex:0 0 auto;font-size:9px;'>" +
                "<img src='data:image/jpeg;base64,$b64' " +
                "style='$hCss$wCss max-width:none;image-rendering:pixelated;'>" +
                "<br>$lab $meta${histRawBarsHtml(c.optJSONArray("attempts"))}</div>",
        )
    }
    sb.append("</div></div>")
    return if (any) sb.toString() else ""
}

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
            val b64 = c.optString("_htmlRec")
            if (b64.isNullOrEmpty()) continue
            any = true
            val lab = c.optString("label")
            val asis = c.optString("asis")
            val dig = c.optString("digits")
            val recW = c.optInt("recW", 0)
            val wCss = if (recW > 0) "width:${recW}px;" else "width:auto;"
            chunk.append(
                "<div style='flex:0 0 auto;font-size:9px;'>" +
                    "<img src='data:image/jpeg;base64,$b64' " +
                    "style='height:48px;$wCss max-width:none;image-rendering:pixelated;'>" +
                    "<br>$lab <span style='font-size:12px;'>asis=$asis dig=$dig</span></div>",
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
            val kind = v.optString("kind")
            if (kind == "energy_or_g") continue
            val cands = v.optJSONArray("candidates") ?: continue
            emitCands(cands, recVariantHeading(kind, v))
        }
    } else {
        val cands = data.optJSONArray("candidates")
        if (cands != null && cands.length() > 0) {
            sb.append("<div style='margin-top:6px;text-align:left;'><b>Rec buffers</b></div>")
            emitCands(cands, "candidates")
        }
    }
    if (sb.isEmpty()) return ""
    return "<div class='rec-crops'>$sb</div>"
}

private fun pColumnTitle(name: String, br: PumpBranch): String {
    val pr = br.pathResults["Paddle"] ?: br.pathResults.values.firstOrNull()
    val cv = if (pr != null) " ${pr.cost} / ${pr.vol}" else ""
    return "<b>$name Paddle:</b>$cv"
}

private fun recVariantHeading(kind: String, v: JSONObject): String {
    val s = v.optDouble("s", Double.NaN)
    return when (kind) {
        "energy" -> "energy"
        "energy_count" -> "energy_count"
        "horiz_pad" -> if (!s.isNaN() && s > 0.0 && s < 1.0) {
            "horiz_pad ${"%.1f".format(s)}×H"
        } else {
            "horiz_pad k=${if (s.isNaN()) "?" else s.toInt()}"
        }
        "ink" -> "ink k=${if (s.isNaN()) "?" else s.toInt()}"
        else -> if (s.isNaN()) kind.ifBlank { "S=?" } else "S=${"%.2f".format(s)}"
    }
}

private val jpegDupeKeys = setOf(
    "recB64", "threshB64", "_htmlRec", "_htmlThresh", "lookInkB64",
    "costB64", "volB64", "images",
)

private fun stripJpegDupes(value: Any?): Any? {
    when (value) {
        is JSONObject -> {
            val out = JSONObject()
            val keys = value.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in jpegDupeKeys) continue
                out.put(k, stripJpegDupes(value.opt(k)))
            }
            return out
        }
        is JSONArray -> {
            val out = JSONArray()
            for (i in 0 until value.length()) out.put(stripJpegDupes(value.opt(i)))
            return out
        }
        is String -> {
            val t = value.trim()
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    val parsed: Any = if (t.startsWith("{")) JSONObject(t) else JSONArray(t)
                    return stripJpegDupes(parsed).toString()
                } catch (_: Exception) {
                }
            }
            return value
        }
        else -> return value
    }
}

private fun pumpSlotNames(nKeep: Int, nFlows: Int): List<String> {
    val out = mutableListOf("orig-details")
    for (c in 1..nFlows) {
        out.add("c$c-pd-red")
        out.add("c$c-pd-full")
        out.add("c$c-overlay-full")
        for (k in 1..nKeep) out.add("c$c-look-ink-box$k")
        for (k in 1..nKeep) out.add("c$c-rec-box$k")
        out.add("c$c-rec-extra")
        out.add("c$c-dump")
    }
    out.add("results")
    return out
}

private fun pumpPersistJpeg(dir: File, name: String, b64: String): String {
    if (b64.isEmpty()) return ""
    return try {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        if (bytes.isEmpty()) return ""
        File(dir, name).writeBytes(bytes)
        "${dir.name}/$name"
    } catch (_: Throwable) {
        ""
    }
}

private fun pumpImgTag(src: String, style: String, cap: String = ""): String {
    if (src.isEmpty()) return ""
    val capHtml = if (cap.isEmpty()) "" else "<br>$cap"
    return "<img src='$src' style='$style max-width:none;image-rendering:pixelated;'>$capHtml"
}

private fun pumpColumnLabels(flows: List<String>): List<String> {
    val sorted = flows.toSortedSet()
    val labels = mutableListOf("# &amp; Original")
    sorted.forEach { flow ->
        labels.add("$flow Paddle")
    }
    labels.add("Results")
    return labels
}

private fun seg7TeleJson(t: ContentExpandUtils.Seg7Telemetry): JSONObject {
    val hh = JSONArray(); t.histH.forEach { hh.put(it) }
    val hv = JSONArray(); t.histV.forEach { hv.put(it) }
    val hgh = JSONArray(); t.histGapH.forEach { hgh.put(it) }
    val hgv = JSONArray(); t.histGapV.forEach { hgv.put(it) }
    return JSONObject()
        .put("method", t.method)
        .put("y_ink", t.yInk.toDouble())
        .put("y_bg", t.yBg.toDouble())
        .put("d_ink", t.dInk.toDouble())
        .put("mean_chroma", t.meanChroma.toDouble())
        .put("u_ink_x", t.uInkX.toDouble())
        .put("u_ink_y", t.uInkY.toDouble())
        .put("otsu_thresh", t.otsuThr.toDouble())
        .put("s_px", t.sPx.toDouble())
        .put("delta_top", t.deltaTop.toDouble())
        .put("delta_bot", t.deltaBot.toDouble())
        .put("delta_left", t.deltaLeft.toDouble())
        .put("delta_right", t.deltaRight.toDouble())
        .put("flag_top", t.flagTop)
        .put("flag_bot", t.flagBot)
        .put("flag_left", t.flagLeft)
        .put("flag_right", t.flagRight)
        .put("gap_jump_top", if (t.gapJumpTop) 1 else 0)
        .put("gap_jump_bot", if (t.gapJumpBot) 1 else 0)
        .put("delta_top_1", t.deltaTop1.toDouble())
        .put("delta_bot_1", t.deltaBot1.toDouble())
        .put("flag_top_1", t.flagTop1)
        .put("flag_bot_1", t.flagBot1)
        .put("gap_jump_bot_1", if (t.gapJumpBot1) 1 else 0)
        .put("n_drop_tall", t.nDropTall.toDouble())
        .put("max_cc_h", t.maxCcH.toDouble())
        .put("dispKind", t.dispKind)
        .put("poisonHMul", t.poisonHMul.toDouble())
        .put("poisonVMul", t.poisonVMul.toDouble())
        .put("gapDriftP90S", t.gapDriftP90S.toDouble())
        .put("gap_land_top", t.landTop.toDouble())
        .put("gap_land_bot", t.landBot.toDouble())
        .put("hist_h", hh)
        .put("hist_v", hv)
        .put("hist_gap_h", hgh)
        .put("hist_gap_v", hgv)
}

private fun storeSeg7Tele(branch: PumpBranch, teles: List<ContentExpandUtils.Seg7Telemetry?>) {
    val arr = JSONArray()
    teles.forEach { t -> if (t != null) arr.put(seg7TeleJson(t)) }
    if (arr.length() > 0) branch.metadata["seg7_tele"] = arr.toString()
}

private fun histBinLabel(b: Int, method: String = "", imgW: Int = 0): String {
    if (method == "gray" || method == "color_adaptive") {
        val step = if (imgW < 2000) 2 else 4
        val half = if (imgW < 2000) 2 else 3
        val c0 = if (imgW < 2000) 6 else 16
        val lo = c0 - half + step * b
        val hi = c0 + half + step * b
        return if (b >= NativeImageUtils.SEG7_HIST_BINS - 1) "$lo+" else "$lo-$hi"
    }
    if (b <= 0) return "1-2"
    var hi = 2
    repeat(b) { hi *= 2 }
    val lo = hi / 2 + 1
    return if (b >= 31) "$lo+" else "$lo-$hi"
}

private fun pSparkSvg(
    scores: JSONArray?,
    thr: Double,
    seed0: Int,
    seed1: Int,
    stop0: Int,
    stop1: Int,
    w: Int = 240,
    h: Int = 52,
): String {
    val arr = scores ?: return ""
    val n = arr.length()
    if (n < 2) return ""
    var lo = 0.0
    var hi = thr
    for (i in 0 until n) {
        val v = arr.optDouble(i)
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    if (hi <= lo) hi = lo + 1.0
    val plotH = h - 12
    fun x(i: Int): Double = 1.0 + i.toDouble() / (n - 1).toDouble() * (w - 2)
    fun y(v: Double): Double = (plotH - 2) - (v - lo) / (hi - lo) * (plotH - 4)
    val pts = StringBuilder()
    for (i in 0 until n) {
        if (i > 0) pts.append(' ')
        pts.append("%.1f,%.1f".format(x(i), y(arr.optDouble(i))))
    }
    fun overlay(idx: Int, color: String): String {
        if (idx < 0) return ""
        val xi = "%.1f".format(x(idx.coerceIn(0, n - 1)))
        return "<line x1='$xi' y1='0' x2='$xi' y2='$plotH' stroke='$color' stroke-width='1'/>"
    }
    val xIdx = linkedSetOf<Int>()
    xIdx.add(0)
    if (seed0 >= 0) xIdx.add(seed0.coerceIn(0, n - 1))
    if (seed1 >= 0) xIdx.add(seed1.coerceIn(0, n - 1))
    xIdx.add(n - 1)
    val xTicks = StringBuilder()
    for (idx in xIdx) {
        val xf = x(idx)
        val xi = "%.1f".format(xf)
        val anchor = when {
            xf < 12.0 -> "start"
            xf > w - 12.0 -> "end"
            else -> "middle"
        }
        xTicks.append(
            "<line x1='$xi' y1='$plotH' x2='$xi' y2='${plotH + 3}' stroke='#666' stroke-width='1'/>" +
                "<text x='$xi' y='$h' font-size='8' fill='#555' text-anchor='$anchor'>$idx</text>",
        )
    }
    val yt = "%.1f".format(y(thr))
    return "<svg width='$w' height='$h' viewBox='0 0 $w $h' " +
        "style='display:block;background:#fafafa;border:1px solid #ccc;margin:2px 0;'>" +
        "<line x1='0' y1='$yt' x2='$w' y2='$yt' stroke='#c44' stroke-width='1' stroke-dasharray='3,2'/>" +
        "<polyline fill='none' stroke='#258' stroke-width='1' points='$pts'/>" +
        overlay(seed0, "#888") + overlay(seed1, "#888") +
        overlay(stop0, "#2a2") + overlay(stop1, "#2a2") +
        xTicks.toString() +
        "</svg>"
}

private fun pInkSweepHtml(br: PumpBranch): String {
    val raw = br.metadata["costVolDecisionData_Paddle"] ?: return ""
    val sweeps = try {
        JSONObject(raw).optJSONArray("inkSweep")
    } catch (_: Exception) {
        return ""
    } ?: return ""
    if (sweeps.length() == 0) return ""
    val sb = StringBuilder()
    for (i in 0 until sweeps.length()) {
        val o = sweeps.optJSONObject(i) ?: continue
        val vScores = o.optJSONArray("vScores")
        val hScores = o.optJSONArray("hScores")
        if ((vScores?.length() ?: 0) < 2 && (hScores?.length() ?: 0) < 2) continue
        val thr = o.optDouble("thr")
        val minRun = o.optInt("minRun")
        val sPx = o.optDouble("sPx")
        val vLabel = if (sPx > 0.0) {
            val halfS = max(1, kotlin.math.round(0.5 * sPx).toInt())
            "box${i + 1} ink V (thr=$minRun (0.5s=$halfS))"
        } else {
            "box${i + 1} ink V (thr=${"%.1f".format(thr)})"
        }
        sb.append("<div style='margin:4px 0;'>")
        sb.append("<div style='font-size:8px;color:#555;'>$vLabel</div>")
        sb.append(
            pSparkSvg(
                vScores, thr,
                o.optInt("v0", -1), o.optInt("v1", -1),
                o.optInt("walkT", -1), o.optInt("walkB", -1),
            ),
        )
        sb.append("<div style='font-size:8px;color:#555;'>box${i + 1} ink H</div>")
        sb.append(
            pSparkSvg(
                hScores, thr,
                o.optInt("h0", -1), o.optInt("h1", -1),
                o.optInt("jumpL", -1), o.optInt("jumpR", -1),
            ),
        )
        val threshB64 = o.optString("_htmlThresh")
        if (!threshB64.isNullOrEmpty()) {
            sb.append("<div style='font-size:8px;color:#555;'>box${i + 1} thresh</div>")
            sb.append(
                "<img src='data:image/jpeg;base64,$threshB64' " +
                    "style='max-width:400px;height:auto;image-rendering:pixelated;" +
                    "border:1px solid #eee;display:block;margin:2px 0;'>",
            )
        }
        sb.append(
            "<div style='font-size:8px;color:#555;'>gray=seed  green=walk  dashed red=thr  x=scan index (seed ± 2.5H)</div>",
        )
        sb.append("</div>")
    }
    return sb.toString()
}

private fun pSeg7TeleHtml(br: PumpBranch, imgW: Int): String {
    val raw = br.metadata["seg7_tele"]
    val arr = if (raw.isNullOrBlank()) JSONArray() else try {
        JSONArray(raw)
    } catch (_: Exception) {
        JSONArray()
    }
    val sweepHtml = pInkSweepHtml(br)
    if (arr.length() == 0 && sweepHtml.isEmpty()) return ""
    fun f1(o: JSONObject, k: String) = "%.1f".format(o.optDouble(k))
    fun f0(o: JSONObject, k: String) = "%.0f".format(o.optDouble(k))
    val cell = "padding:1px 3px;border:1px solid #ddd;"
    val th = "text-align:left;$cell"
    val sb = StringBuilder()
    sb.append("<div class='dump-details' style='font-size:9px;text-align:left;margin-top:4px;'>")
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        sb.append("<table style='border-collapse:collapse;font-size:9px;margin:4px 0;width:100%;text-align:left;'>")
        sb.append("<tr><th colspan='4' style='background:#eee;$th'>box${i + 1} ${o.optString("method")}</th></tr>")
        fun row2(k1: String, v1: String, k2: String, v2: String) {
            sb.append("<tr><th style='$th'>$k1</th><td style='$cell'>$v1</td>")
            sb.append("<th style='$th'>$k2</th><td style='$cell'>$v2</td></tr>")
        }
        row2("y_ink", f1(o, "y_ink"), "y_bg", f1(o, "y_bg"))
        row2("d_ink", f1(o, "d_ink"), "mean_chroma", f1(o, "mean_chroma"))
        row2("u_ink_x", f1(o, "u_ink_x"), "u_ink_y", f1(o, "u_ink_y"))
        row2("s_px", f0(o, "s_px"), "otsu", f0(o, "otsu_thresh"))
        row2("Δt1", f0(o, "delta_top_1"), "Δb1", f0(o, "delta_bot_1"))
        row2("Δt", f0(o, "delta_top"), "Δb", f0(o, "delta_bot"))
        row2("Δl", f0(o, "delta_left"), "Δr", f0(o, "delta_right"))
        row2(
            "flag T/B 1",
            "${o.optString("flag_top_1")}/${o.optString("flag_bot_1")}",
            "gap_bot_1",
            o.optInt("gap_jump_bot_1").toString(),
        )
        row2(
            "flag T/B",
            "${o.optString("flag_top")}/${o.optString("flag_bot")}",
            "flag L/R",
            "${o.optString("flag_left")}/${o.optString("flag_right")}",
        )
        row2("n_drop_tall", f0(o, "n_drop_tall"), "max_cc_h", f0(o, "max_cc_h"))
        row2("dispKind", o.optString("dispKind"), "gapDriftP90S", f1(o, "gapDriftP90S"))
        row2("poisonHMul", f0(o, "poisonHMul"), "poisonVMul", f0(o, "poisonVMul"))
        sb.append("</table>")
        val hh = o.optJSONArray("hist_h")
        val hv = o.optJSONArray("hist_v")
        val hgh = o.optJSONArray("hist_gap_h")
        val hgv = o.optJSONArray("hist_gap_v")
        val n = max(
            max(hh?.length() ?: 0, hv?.length() ?: 0),
            max(hgh?.length() ?: 0, hgv?.length() ?: 0),
        )
        if (n > 0) {
            val show = (0 until n).filter { b ->
                (hh?.optInt(b) ?: 0) > 0 || (hv?.optInt(b) ?: 0) > 0 ||
                    (hgh?.optInt(b) ?: 0) > 0 || (hgv?.optInt(b) ?: 0) > 0
            }
            if (show.isNotEmpty()) {
                sb.append("<table style='border-collapse:collapse;font-size:8px;margin:2px 0 6px;text-align:center;'>")
                sb.append("<tr><th style='$th'>bin</th>")
                for (b in show) sb.append("<th style='$cell'>${histBinLabel(b, o.optString("method"), imgW)}</th>")
                sb.append("</tr><tr><th style='$th'>H</th>")
                for (b in show) sb.append("<td style='$cell'>${hh?.optInt(b) ?: 0}</td>")
                sb.append("</tr><tr><th style='$th'>V</th>")
                for (b in show) sb.append("<td style='$cell'>${hv?.optInt(b) ?: 0}</td>")
                if ((hgh?.length() ?: 0) > 0 || (hgv?.length() ?: 0) > 0) {
                    sb.append("</tr><tr><th style='$th'>gap H</th>")
                    for (b in show) sb.append("<td style='$cell'>${hgh?.optInt(b) ?: 0}</td>")
                    sb.append("</tr><tr><th style='$th'>gap V</th>")
                    for (b in show) sb.append("<td style='$cell'>${hgv?.optInt(b) ?: 0}</td>")
                }
                sb.append("</tr></table>")
            }
        }
    }
    sb.append(sweepHtml)
    sb.append("</div>")
    return sb.toString()
}

private fun pBuildHtmlHeader(
    time: String,
    total: Int,
    version: String,
    device: String,
    colLabels: List<String>,
    metaHtml: String,
    partIndex1: Int,
    nParts: Int,
    reportStem: String,
): String = buildString {
    append(ExperimentReportHtml.documentHead("Pump Experiment - $time", ExperimentReportHtml.Kind.PUMP))
    appendLine("<h1>Pump Extraction Experiment</h1>")
    appendLine("<p>$metaHtml</p>")
    append(
        ExperimentReportHtml.toolbar(
            ExperimentReportHtml.Kind.PUMP, colLabels, metaHtml,
            partIndex1 = partIndex1, nParts = nParts, reportStem = reportStem,
        ),
    )
    append(ExperimentReportHtml.tableOpen(colLabels, resultsLast = true))
    appendLine("<!-- total=$total device=$device version=$version -->")
}

private fun pPublishSlot(
    slotNames: List<String>,
    photoIndex0: Int,
    nSlots: Int,
    cellsDir: File,
    slot: String,
    html: String,
) {
    val si = slotNames.indexOf(slot)
    if (si < 0) return
    val id = photoIndex0 * nSlots + si + 1
    ReportCollapser.publishCell(cellsDir, id, html, "{}")
}

private fun pPublishOrigDetails(
    rowIndex: Int,
    photoIndex0: Int,
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    root: PumpBranch,
    tDeskew: Long,
    deskewHtml: String,
    diagnostic: String = "",
    imgDir: File,
    cellsDir: File,
    slotNames: List<String>,
    nSlots: Int,
) {
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
    val origSb = StringBuilder()
    origSb.append("<br><small>$rowHtml</small>$diagHtml")
    origSb.append("<br><span style=\"font-size:6px\"><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt per set:</b> $perSetTilts")
    origSb.append("<table style='width:100%; border:none;'><tr style='border:none;'>")
    val before = pumpPersistJpeg(imgDir, "r${rowIndex}_orig_before.jpg", img["before"] ?: "")
    val hist1 = pumpPersistJpeg(imgDir, "r${rowIndex}_orig_hist1.jpg", img["hist1"] ?: "")
    val after = pumpPersistJpeg(imgDir, "r${rowIndex}_orig_after.jpg", img["after"] ?: "")
    val hist2 = pumpPersistJpeg(imgDir, "r${rowIndex}_orig_hist2.jpg", img["hist2"] ?: "")
    origSb.append("<td style='border:none; padding:1px;'>${pumpImgTag(before, "", "Orig")}</td>")
    origSb.append("<td style='border:none; padding:1px;'>${pumpImgTag(hist1, "", "Hist 1")}</td></tr>")
    origSb.append("<tr style='border:none;'><td style='border:none; padding:1px;'>${pumpImgTag(after, "", "Stretch")}</td>")
    origSb.append("<td style='border:none; padding:1px;'>${pumpImgTag(hist2, "", "Hist 2")}</td></tr>")
    origSb.append("<tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:6px;'><small>$deskewHtml</small></td></tr></table></span>")
    pPublishSlot(slotNames, photoIndex0, nSlots, cellsDir, "orig-details", origSb.toString())
}

private fun pPublishFlowColumn(
    rowIndex: Int,
    photoIndex0: Int,
    name: String,
    br: PumpBranch,
    colIdx: Int,
    maxRedBoxes: Int,
    imgDir: File,
    imgRel: String,
    cellsDir: File,
    slotNames: List<String>,
    nSlots: Int,
    imgW: Int,
) {
    val nKeep = maxRedBoxes.coerceIn(PumpOcrSettings.MIN_MAX_RED_BOXES, PumpOcrSettings.MAX_MAX_RED_BOXES)
    val skipLook = name.contains("G--")
    val abortHtml = br.metadata["object_abort_html"].orEmpty()
    val sPerRed = br.metadata["s_per_red"]
    val sHtml = if (!sPerRed.isNullOrBlank() && sPerRed.length <= 100) {
        "<br><small>s=$sPerRed</small>"
    } else {
        ""
    }
    val teleHtml = pSeg7TeleHtml(br, imgW)
    val dumpFinal = br.metadata["object_dump_final"].orEmpty()
    val redOnly = pumpPersistJpeg(
        imgDir, "r${rowIndex}_c${colIdx}_pd_red.jpg", br.images["PD_red_only"] ?: "",
    )
    val full = pumpPersistJpeg(
        imgDir, "r${rowIndex}_c${colIdx}_pd_full.jpg", br.images["PD"] ?: "",
    )
    val overlay = pumpPersistJpeg(
        imgDir, "r${rowIndex}_c${colIdx}_overlay.jpg", br.images["overlay"] ?: "",
    )
    pPublishSlot(
        slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-pd-red",
        pColumnTitle(name, br) + pumpImgTag(redOnly, "max-width:100%;"),
    )
    pPublishSlot(
        slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-pd-full",
        pumpImgTag(full, "max-width:100%;"),
    )
    pPublishSlot(
        slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-overlay-full",
        if (overlay.isEmpty()) "" else pumpImgTag(overlay, "max-width:100%;"),
    )
    for (k in 1..nKeep) {
        val look = if (skipLook) {
            ""
        } else {
            val body = pLookInkBoxHtml(br, k, imgRel)
            if (body.isNotEmpty()) body else abortHtml
        }
        pPublishSlot(slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-look-ink-box$k", look)
    }
    for (k in 1..nKeep) {
        val rec = pOfficialRecBoxHtml(br, k, imgDir, rowIndex, colIdx)
        val recBody = if (rec.isNotEmpty()) rec else abortHtml
        pPublishSlot(slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-rec-box$k", recBody)
    }
    pPublishSlot(
        slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-rec-extra",
        pRecExtraHtml(br, imgDir, rowIndex, colIdx),
    )
    val dumpBits = StringBuilder()
    dumpBits.append(sHtml).append(teleHtml)
    if (dumpFinal.isNotEmpty() && !skipLook) {
        dumpBits.append(pumpImgTag("$imgRel/$dumpFinal", "max-width:100%;", "U8 final"))
    }
    if (abortHtml.isNotEmpty()) dumpBits.append(abortHtml)
    pPublishSlot(slotNames, photoIndex0, nSlots, cellsDir, "c$colIdx-dump", dumpBits.toString())
}

private fun pPublishResultsSlots(
    rowIndex: Int,
    photoIndex0: Int,
    root: PumpBranch,
    imgDir: File,
    cellsDir: File,
    slotNames: List<String>,
    nSlots: Int,
) {
    val resSb = StringBuilder()
    resSb.append("<table class='res-table'><tr><th>Path</th><th>Cost</th><th>Volume</th></tr>")
    var resCol = 1
    root.subBranches.toSortedMap().forEach { (name, br) ->
        val ks = pChosenSeedLabels(br)
        br.pathResults.forEach { (eng, res) ->
            resSb.append("<tr data-col=\"$resCol\"><td>$name:$eng</td>")
            val costK = ks.first
            val volK = ks.second
            resSb.append("<td><b>${res.cost}</b>")
            if (costK.isNotEmpty()) resSb.append(" <small>k=$costK</small>")
            if (res.costB64.isNotEmpty()) {
                val src = pumpPersistJpeg(imgDir, "r${rowIndex}_c${resCol}_${eng}_cost.jpg", res.costB64)
                resSb.append("<br>").append(pumpImgTag(src, "width:150px;"))
            }
            resSb.append("</td><td><b>${res.vol}</b>")
            if (volK.isNotEmpty()) resSb.append(" <small>k=$volK</small>")
            if (res.volB64.isNotEmpty()) {
                val src = pumpPersistJpeg(imgDir, "r${rowIndex}_c${resCol}_${eng}_vol.jpg", res.volB64)
                resSb.append("<br>").append(pumpImgTag(src, "width:150px;"))
            }
            resSb.append("</td></tr>")
        }
        resCol++
    }
    resSb.append("</table>")
    pPublishSlot(slotNames, photoIndex0, nSlots, cellsDir, "results", resSb.toString())
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

        val sl = l.toInt().coerceIn(0, (masterW - 1).coerceAtLeast(0))
        val st = t.toInt().coerceIn(0, (masterH - 1).coerceAtLeast(0))
        val sr = r.toInt().coerceIn(sl + 1, masterW)
        val sb = b.toInt().coerceIn(st + 1, masterH)
        if ((sr - sl) < 2 || (sb - st) < 2) return@map hunk

        val fed = RecBufferFeed.feedSourceBorderHeightStrip(
            buffer.p.mat,
            sl, st, sr, sb,
            recBuffer,
            targetH = 48,
            borderPx = RecBufferFeed.DEFAULT_BORDER_PX,
        )
        if (fed.targetW <= 0 || fed.targetH <= 0) {
            recBuffer.c[fed.recCropId].release()
            return@map hunk
        }

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
            paddleEngine.recognize(recBuffer.c[fed.recCropId])
        }

        recBuffer.c[fed.recCropId].release()
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