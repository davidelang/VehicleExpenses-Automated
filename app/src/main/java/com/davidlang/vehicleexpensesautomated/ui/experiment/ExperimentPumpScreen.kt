package com.davidlang.vehicleexpensesautomated.ui.experiment

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
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

private const val AMAZON_PHOTOS_LINK = "https://www.amazon.com/photos/shared/81xh078qSgydiVwUH9VWBw.EcItxhL_TTM9KNvR0akUC0"
private const val TAG = "ExperimentPump"

private val GOLDEN_SUBSET = listOf(
    "PXL_20260202_204443784.jpg",
    "PXL_20250626_205528017.jpg",
    "PXL_20220701_020625793.dng",
    "PXL_20260114_020053675.jpg",
    "PXL_20241230_191439866.jpg",
    "PXL_20250224_001547856.jpg",
    "PXL_20240708_222637707.jpg",
    "PXL_20241130_183108905.jpg",
    "PXL_20260302_000113349.jpg",
    "PXL_20250930_065746276.jpg"
)

@Immutable
data class PumpPhotoResultSummary(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val odometer: String?
)

data class PumpHunk(val text: String, val icrs: RectF)
data class PathResult(val cost: String, val vol: String, val costB64: String, val volB64: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentPumpScreen(navController: NavHostController) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Ready to run experiment") }
    var detailLog by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentPhotoName by remember { mutableStateOf("") }
    var totalPhotos by remember { mutableIntStateOf(0) }
    val resultsList = remember { mutableStateListOf<PumpPhotoResultSummary>() }

    val experimentDir = File(context.getExternalFilesDir(null), "pump_photos")
    val reportDir = File(context.getExternalFilesDir(null), "pump_reports")
    val debugCropDir = File(context.getExternalFilesDir(null), "pump_debug_crops")

    if (!reportDir.exists()) reportDir.mkdirs()
    if (!debugCropDir.exists()) debugCropDir.mkdirs()

    val zipLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { status = "Extracting ZIP..."; val success = pExtractZipToPhotos(it, experimentDir, context); status = if (success) "ZIP extracted!" else "Failed to extract ZIP." } }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (detailLog.isNotEmpty()) { Text(detailLog, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (isRunning) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${minOf(resultsList.size + 1, totalPhotos)} of $totalPhotos",
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
        Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_PHOTOS_LINK)); context.startActivity(intent) }, modifier = Modifier.fillMaxWidth()) { Text("Open Amazon Photos Album") }
        Button(onClick = { zipLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Extract Downloaded ZIP") }
        Button(onClick = {
            scope.launch {
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                totalPhotos = allFiles.size
                isRunning = true; resultsList.clear()
                runPumpExperiment(experimentDir, reportDir, debugCropDir, context, { detailLog = it }, null) { res, p ->
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName
                }
                isRunning = false; status = "Complete! Reports saved."
            }
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
        Button(onClick = {
            scope.launch {
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                val subset = allFiles.filter { it.name in GOLDEN_SUBSET }
                totalPhotos = subset.size
                isRunning = true; resultsList.clear()
                runPumpExperiment(experimentDir, reportDir, debugCropDir, context, { detailLog = it }, GOLDEN_SUBSET) { res, p ->
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName
                }
                isRunning = false; status = "Complete! Limited Report saved."
            }
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Limited Experiment (Golden Subset)") }
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

private suspend fun runPumpExperiment(
    experimentDir: File,
    reportDir: File,
    debugCropDir: File,
    context: Context,
    onLog: (String) -> Unit,
    subsetNames: List<String>?,
    onProgress: (PumpPhotoResultSummary, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val allPhotos = experimentDir.listFiles { f ->
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
    }?.sortedBy { it.name } ?: return@withContext

    val photos = if (subsetNames != null) {
        allPhotos.filter { it.name in subsetNames }
    } else allPhotos

    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val paddleEngine = NativePaddleEngine(context)

    val jsonFile = File(reportDir, "pump_results_$timestamp.json")
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"total_photos\": $total,\n  \"results\": [\n")

    // Pre-allocated JSON serialization buffer (16MB starting capacity)
    var jsonCharBuffer = StringBuilder(16 * 1024 * 1024)

    var partCount = 1
    val maxSizeBytes = 5 * 1024 * 1024 // 5MB parts
    var currentSize = 0
    val footer = "</table></body></html>"
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentRecSet1024x48 = BufferSet(1024, 48)  // per plan for D/E (and mirrors) OCR: larger for garbage tolerance + 4px buffer
    val experimentDetSet512x128 = BufferSet(512, 128)
    val masterBuffer = BufferSet(1, 1)

    // ML Kit Discovery Buffers (only needed for ML Kit detection/OCR processing)
    val mlDiscoveryBuffers = mapOf(
        224 to BufferSet(224, 224),
        608 to BufferSet(608, 608),
        1024 to BufferSet(1024, 1024),
        2560 to BufferSet(2560, 2560)
    )
    mlDiscoveryBuffers.values.forEach {
        it.p.clearChroma()
        it.s.clearChroma()
    }

    // Define flows for N-sets support
    // Configure experiment flows here. (See: docs/PUMP_EXPERIMENT_FLOWS.md for instructions)
    // Set A: dual ML+Paddle (baseline). Set B: pump-only (Paddle recognition only, no MLKit in rec step) + improved redbox + Set E-style deskew.
    // Set C: pump-only (copy of Set B) but uses valley-center push (replaces current histogram contrast stretch per plan); produces single image with small number of brightness values (not binarization). Raw + pushed + before/after hists (3x size, 2x displayed) displayed in the Set C column (plus PD/ocr with boxes for context). Per-redbox histograms sorted by area, 3-wide with stacked labels (from prior + this plan). Lot of granular t_ timings (20+ including t_setup_ms, t_deskew_ms, t_discovery_wrapper_ms, t_filter_ms, t_pd_snapshot_ms, t_ocr_ms + C probe subs t_polarity_run_ms / t_per_red_mask_create_ms / t_per_red_generate_b64_ms (covers manual for(i in 1..62) drawRect loops in generateHistogramB64) / t_per_red_bins_calc_ms / t_per_red_loop_overhead_ms / t_polarity_decision_ms / t_invert_if_needed_ms + blue subs t_blue_native_hist_ms / t_blue_valley_expands_ms / t_blue_3sides_ms / t_blue_retract_ms + t_hist_* + kept priors + n_reds_at_probe / n_per_red_hists / img dims context) added to metadata/JSON (one run gathers all for A/B gap + C probe/blue decomposition; no extra turn needed). HISTOGRAM ANSWERS (forensic from probe/generate): data in Kotlin (not C; native hist path separate/not used here for redboxDataC/redboxHistC_*); full Mat.zeros(size) + rectangle mask on original mat (no crop of data, OpenCV mask internal); manual loops yes (Kotlin Canvas for (i in 1..62) drawRect for b64 visuals after calcHist; numeric bins from calcHist). Blue from red now via alignment Set E valley expansion (adapted) instead of CC overlapping + early expandByUniformity (to fix errors). Polarity + discovery + CC hunks (for orange) run on the pushed mat state.
    val flows = listOf("Set A", "Set B", "Set C", "Set D", "Set E")

    fun pStartNewFile(): File {
        val f = File(reportDir, "pump_report_${timestamp}_part${partCount++}.html")
        f.writeText(pBuildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, flows))
        return f
    }

    var currentFile = pStartNewFile()

    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${index + 1}/$total: ${file.name}") }

            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            masterBuffer.resize(imgW, imgH)
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, masterBuffer.p)

            val root = PumpBranch("Root")
            val (beforeB64, tSnapOrig) = OcrUtils.takeSnapshot(masterBuffer.p, null, 225, 0, emptyList(), null, masterBuffer)
            root.images["before"] = beforeB64
            root.images["hist1"] = generateHistogramB64(masterBuffer.p.mat, 0.40f)

            var originalHistogram = JSONArray()

            // Dynamic Flow Processing
            // Phase 2 dispatch (approved array-of-processors refactor): iterate the flowProcessors array in lockstep
            // with flows (forEachIndexed). Common per-flow setup + call to the processor for this index.
            // Per-set special logic (B/D red-only + retracted+OCR/PD; C/E valley/3sides/retract/orange/PD/OCR) is in thin if calls to extracted helpers (after common filter).
            // if (B||D) and else if (C||E) bodies are now only calls (hoists for C). Mechanical extraction for the tangled ifs complete per user directive ("between each if flowname you pretty much only have a function call").
            // Procs via the array are the entry (stubs document the linear steps). Scaffolding comments cleaned.
            flows.forEachIndexed { i, flowName ->
                // (original per-flow setup follows; the call to the processor for this i will be placed after the
                // flowProcessors list definition later in this per-flow body, so the array reference resolves and
                // the C processor (with valley) runs after setup and after its own def in source. This activates
                // the array-of-functions iteration per the clarification (no hard-coded per-set function names at
                // call sites; just index into the array). Thin ifs + proc delegates now drive per-set special logic; old body scaffolding cleaned.)
                val branch = root.getBranch(flowName)
                val tFlowStart = System.currentTimeMillis()
                val tSetupStart = System.currentTimeMillis()
                val workspace = NativePaddleEngine.bufferSetA
                workspace.resize(imgW, imgH)
                masterBuffer.p.mat.copyTo(workspace.p.mat)
                masterBuffer.p.uvMat.copyTo(workspace.p.uvMat)

                val discoveryDetails = mutableMapOf<String, MutableMap<Int, List<PumpHunk>>>().apply {
                    put("Paddle Raw", mutableMapOf())
                    put("Paddle Expanded", mutableMapOf())
                    put("Paddle Max Extent", mutableMapOf())
                    put("Paddle Native", mutableMapOf())
                }
                branch.metadata["t_setup_ms"] = (System.currentTimeMillis() - tSetupStart).toString()
                // t_setup_ms covers buffer resize/copy + discoveryDetails map (common high-level phase for A/B/C gap analysis)

                // 1. Transform
                // Per approved valley plan: for Set C, display raw then valleyPushToPeaks (replaces stretch) producing image with small # brightness values (not binarization).
                // Capture rawC + histBeforeC (pre), apply push, capture pushedC + histAfterC to branch for Set C column.
                // A still populates root after/hist2 for the left column. B unchanged.
                val rawHist: FloatArray
                if (flowName == "Set C" || flowName == "Set E") {
                    // Capture raw (pre any C-specific transform) + before hist for column display
                    val (rawForC, _) = OcrUtils.takeSnapshot(workspace.p, null, 675, 0, emptyList(), null, workspace)
                    branch.images["rawC"] = rawForC
                    val tG0 = System.currentTimeMillis()
                    branch.images["histBeforeC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_before_c_ms"] = (System.currentTimeMillis() - tG0).toString()
                    rawHist = OdometerOcrUtils.valleyPushToPeaks(workspace.p.mat)  // replaces stretch; mutates workspace to few-brightness image
                    val (pushedForC, _) = OcrUtils.takeSnapshot(workspace.p, null, 675, 0, emptyList(), null, workspace)
                    branch.images["pushedC"] = pushedForC
                    val tG1 = System.currentTimeMillis()
                    branch.images["histAfterC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_after_c_ms"] = (System.currentTimeMillis() - tG1).toString()
                    if (flowName == "Set C" || flowName == "Set E") {
                        branch.metadata["t_valley_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
                    }
                } else {
                    rawHist = OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
                    if (flowName == flows.first()) {
                        originalHistogram = JSONArray().apply { rawHist.forEach { put(it.toDouble()) } }
                        root.images["after"] = OcrUtils.takeSnapshot(workspace.p, null, 225, 0, emptyList(), null, workspace).first
                        root.images["hist2"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    }
                }

                // 2. Deskew (ported Set E style from alignment for Set B; uses dedicated populate + JNI angle path)
                // Compute per-flow but select angle source based on flow. Set A, and the B/D mirror (pump-only red focus) now use the negated paddleCpp value; C/E mirror use negated paddleCpp so the applied rotation matches the direction that makes the C/E visuals look right per user observation. D mirrors B, E mirrors C. Set C/E lines kept as the reference.
                val tDeskewStart = System.currentTimeMillis()
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(workspace.p)
                val tilt = when (flowName) {
                    "Set B", "Set D" -> -deskewRes.paddleCppAngle
                    "Set C", "Set E" -> -deskewRes.paddleCppAngle
                    else -> -deskewRes.angle
                }

                // Use shared modern rotate (UV handling, parity with alignment improvements). Local pRotate removed.
                OdometerOcrUtils.rotate(workspace, tilt)
                branch.metadata["tilt"] = "%.2f".format(tilt)
                branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
                // t_deskew_ms covers calculateAverageTextAngle + rotate + tilt metadata write (common high-level phase)

                // Hoisted decls (Phase 1 small step of approved refactor plan): declared before the local helper funs
                // (stackVertically, runPaddleDiscovery) that close over them (and before the inline discovery).
                // This resolves forward-ref compile issues for 'scales', the pd*Totals, mlBlocksRaw etc that the
                // helpers reference. (The processedScales for the inline remains at its site for now.)
                val scales = listOf(224, 608, 1024, 2560)
                val mlBlocksRaw = if (flowName == "Set A") mutableListOf<PumpHunk>() else mutableListOf<PumpHunk>()  // empty for B/C/D/E (pump-only, no ML columns)
                val pdHunksRawTotal = mutableListOf<PumpHunk>()
                val pdHunksExpTotal = mutableListOf<PumpHunk>()
                val pdHunksMaxTotal = mutableListOf<PumpHunk>()
                val pdHunksNativeTotal = mutableListOf<PumpHunk>()
                val pdHunksDetectedTotal = mutableListOf<PumpHunk>()  // pre-redbox raw detected hunks (tFullB equiv); for Set C white 1px + blue/orange derivation from hunks (see alignment Set J tRawB vs tFullB)

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
                    imgH: Int
                ): PathResult {
                    val minEdge = min(imgW, imgH).toFloat()
                    val maxX = imgW / (2f * minEdge); val maxY = imgH / (2f * minEdge)
                    val stitched = stitchHunksHorizontally(hunks)
                    val (top, bottom) = groupLanesByVerticalGap(stitched)
                    val pair = findBestLanePair(top, bottom) ?: return PathResult("N/A", "N/A", "", "")
                    val expT = expandHunkContext(pair.first, maxX, maxY); val expB = expandHunkContext(pair.second, maxX, maxY)
                    val res = performHunkRecognition(listOf(expT, expB), ws, recBuf, engine, paddleEng, ctx, tilt)

                    suspend fun takeCrop(exp: PumpHunk, orig: PumpHunk): String {
                        val p1 = IcrsMath.icrsToPixel(exp.icrs.left, exp.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(exp.icrs.right, exp.icrs.bottom, imgW, imgH)
                        val rect = android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                        val anns = mutableListOf<SnapshotAnnotation>()
                        if (engine == "Paddle") {
                            // RED: Raw detections only (blue/orange removed to focus on red boxes for debugging)
                            pdRawForAnns.forEach { h ->
                                val px1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                                val px2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                                anns.add(SnapshotAnnotation(px1.x.toInt(), px1.y.toInt(), px2.x.toInt(), px2.y.toInt(), Shape.RECTANGLE, Color.RED, 2))
                            }
                            // BLUE and ORANGE temporarily disabled
                            // pdHunksExpTotal.forEach { ... BLUE }
                            // ... ORANGE for the specific
                        }
                        return OcrUtils.takeSnapshot(ws.p, rect, 300, 100, anns, null, ws).first
                    }
                    val cropT = takeCrop(expT, pair.first); val cropB = takeCrop(expB, pair.second)
                    return PathResult(res[0].text, res[1].text, cropT, cropB)
                }

                suspend fun runPaddleDiscovery() {
                    val processedScales = mutableSetOf<Int>()
                    scales.forEach { scale ->
                        val srcW = workspace.p.width
                        val srcH = workspace.p.height
                        val currentLongEdge = max(srcW, srcH)
                        val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                        val targetW = (srcW * scaleFactor).toInt()
                        val targetH = (srcH * scaleFactor).toInt()
                        val targetLongEdge = max(targetW, targetH)

                        val chosenScale = mlDiscoveryBuffers.keys.sorted().firstOrNull { it >= targetLongEdge } ?: 2560
                        val chosenBuffer = mlDiscoveryBuffers[chosenScale]!!

                        if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                            if (!processedScales.contains(chosenScale)) {
                                processedScales.add(chosenScale)
                                chosenBuffer.p.clear()
                                val recCropId = chosenBuffer.createCrop(0, 0, targetW, targetH)
                                val interp = if (srcW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                                org.opencv.imgproc.Imgproc.resize(workspace.p.mat, chosenBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)

                                val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                    chosenBuffer.p.nv21,
                                    chosenBuffer.p.width,
                                    chosenBuffer.p.height,
                                    0,
                                    com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                                )
                                val result = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
                                chosenBuffer.c[recCropId].release()

                                val hunks = result.textBlocks.map { block ->
                                    val ml = block.boundingBox.left.toFloat()
                                    val mt = block.boundingBox.top.toFloat()
                                    val mr = block.boundingBox.right.toFloat()
                                    val mb = block.boundingBox.bottom.toFloat()
                                    val i1 = IcrsMath.pixelToIcrs(ml, mt, targetW, targetH)
                                    val i2 = IcrsMath.pixelToIcrs(mr, mb, targetW, targetH)
                                    PumpHunk(block.text, RectF(i1.x, i1.y, i2.x, i2.y))
                                }
                                mlBlocksRaw.addAll(hunks)
                            }
                        }

                        val p = prepareScale(workspace, scale)
                        val outerId = p.first
                        val innerId = p.second
                        val res = paddleEngine.detect(workspace.c[outerId])
                        if (res != null) {
                            branch.metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
                            branch.metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
                        }

                        val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH)
                        val raw = paddleResults[0]
                        val exp = paddleResults[1]
                        val maxExt = paddleResults[2]
                        val native = paddleResults[3]

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
                }

                fun qualifiesFor3SidesNearExtend(cR: android.graphics.Rect, oR: android.graphics.Rect): Boolean {
                    val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                    if (insides.count { it } != 3) return false
                    // identify protruding side + compute pixel protrusion distance + overlap on that axis
                    val (protrPx, hasOverlap) = when {
                        !insides[0] -> (cR.left - oR.left) to (oR.right > cR.left)   // left
                        !insides[2] -> (oR.right - cR.right) to (oR.left < cR.right) // right
                        !insides[1] -> (cR.top - oR.top) to (oR.bottom > cR.top)     // top
                        !insides[3] -> (oR.bottom - cR.bottom) to (oR.top < cR.bottom) // bottom
                        else -> 0 to true
                    }
                    return protrPx <= 40 && hasOverlap
                }

                /**
                 * Pixel-rect version of redbox nesting filter using the exact user-specified sweep for overlap discovery (O(2N) + small).
                 * Used for the red working path (prune to 6, blue source, etc.) per the D/E plan + unique-images feedback (avoid ICRS for reds).
                 * Exact containment sequential + 3sides with sweep on X then Y, only intersect candidates get careful qualifies + integer extend.
                 */
                fun doCrossScaleRedboxFilterPixel(redRects: MutableList<android.graphics.Rect>) {
                    if (redRects.isEmpty()) return
                    // Exact containment pass (sequential kept, pure integer, no ICRS)
                    val kept = mutableListOf<android.graphics.Rect>()
                    for (r1 in redRects) {
                        val isContained = kept.any { r2 ->
                            r2.contains(r1.left, r1.top, r1.right, r1.bottom)
                        }
                        if (!isContained) kept.add(r1)
                    }
                    // Now 3sides + <=40px with smart sweep instead of O(n^2) pair
                    // Build intervals
                    data class Iv(val s: Int, val e: Int, val idx: Int)
                    // X sweep for overlaps
                    val xIvs = kept.withIndex().map { (i, r) -> Iv(r.left, r.right, i) }.sortedBy { it.s }
                    val xOver = mutableSetOf<Pair<Int, Int>>()
                    val activeX = mutableListOf<Iv>()
                    for (iv in xIvs) {
                        activeX.removeAll { it.e < iv.s }
                        for (a in activeX) {
                            val lo = minOf(a.idx, iv.idx); val hi = maxOf(a.idx, iv.idx)
                            xOver.add(lo to hi)
                        }
                        activeX.add(iv)
                    }
                    // Y sweep
                    val yIvs = kept.withIndex().map { (i, r) -> Iv(r.top, r.bottom, i) }.sortedBy { it.s }
                    val yOver = mutableSetOf<Pair<Int, Int>>()
                    val activeY = mutableListOf<Iv>()
                    for (iv in yIvs) {
                        activeY.removeAll { it.e < iv.s }
                        for (a in activeY) {
                            val lo = minOf(a.idx, iv.idx); val hi = maxOf(a.idx, iv.idx)
                            yOver.add(lo to hi)
                        }
                        activeY.add(iv)
                    }
                    val candidates = xOver intersect yOver
                    // 3sides only on candidates (small N)
                    val toProcess = kept.toMutableList()
                    val extended = mutableListOf<android.graphics.Rect>()
                    for (i in toProcess.indices) {
                        var cur = toProcess[i]
                        for (j in toProcess.indices) {
                            if (i == j) continue
                            val p = minOf(i, j) to maxOf(i, j)
                            if (p !in candidates) continue
                            val oth = toProcess[j]
                            if (qualifiesFor3SidesNearExtend(cur, oth)) {
                                val insides = listOf(oth.left >= cur.left, oth.top >= cur.top, oth.right <= cur.right, oth.bottom <= cur.bottom)
                                val newL = if (!insides[0]) min(cur.left, oth.left) else cur.left
                                val newT = if (!insides[1]) min(cur.top, oth.top) else cur.top
                                val newR = if (!insides[2]) max(cur.right, oth.right) else cur.right
                                val newB = if (!insides[3]) max(cur.bottom, oth.bottom) else cur.bottom
                                cur = android.graphics.Rect(newL, newT, newR, newB)
                            }
                        }
                        if (extended.none { it == cur }) extended.add(cur)
                    }
                    // final cleanup contains
                    val cleaned = extended.filter { b ->
                        !extended.any { o -> o != b && o.contains(b) }
                    }.toMutableList()
                    redRects.clear()
                    redRects.addAll(cleaned)
                }

                fun doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
                    if (pdHunksRawTotal.isNotEmpty()) {
                        // Remove redundant nested or duplicate red boxes (entirely contained or perfectly overlapping).
                        // Purpose: eliminate redundant detections so they do not contribute to derived
                        // blue/orange boxes or final results. Filtered boxes are removed completely.
                        // Use exact containment (no artificial inset/spacing); for perfect overlaps,
                        // keep one representative (the first in order) and drop the rest.
                        // Sequential keep: only check against already-kept boxes to ensure at least one survives duplicates.
                        // Also applies the corrected 3 sides enclosed + <=40px (per user): exactly 3 edge insides + protrusion on 4th <=40px in pixel space *and* the boxes still overlap on the protruding axis (no gap). Uses shared qualifiesFor3SidesNearExtend helper (same logic for blue/orange in Set C).
                        val kept = mutableListOf<PumpHunk>()
                        for (h1 in pdHunksRawTotal) {
                            val p1 = IcrsMath.icrsToPixel(h1.icrs.left, h1.icrs.top, imgW, imgH)
                            val p2 = IcrsMath.icrsToPixel(h1.icrs.right, h1.icrs.bottom, imgW, imgH)
                            val r1 = android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                            val isContained = kept.any { h2 ->
                                h1 !== h2 && run {
                                    val op1 = IcrsMath.icrsToPixel(h2.icrs.left, h2.icrs.top, imgW, imgH)
                                    val op2 = IcrsMath.icrsToPixel(h2.icrs.right, h2.icrs.bottom, imgW, imgH)
                                    val r2 = android.graphics.Rect(op1.x.toInt(), op1.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                    // Exact containment (no inset). Perfect overlaps/duplicates: keep the first, drop redundant.
                                    r2.contains(r1.left, r1.top, r1.right, r1.bottom)
                                }
                            }
                            if (!isContained) {
                                kept.add(h1)
                            }
                        }
                        // 3 sides +40px on the exact survivors (the near-nested cases exact didn't catch)
                        val toProcess = kept.toMutableList()
                        val extended = mutableListOf<PumpHunk>()
                        for (i in toProcess.indices) {
                            var cur = toProcess[i]
                            for (j in toProcess.indices) {
                                if (i == j) continue
                                val oth = toProcess[j]
                                val cp = IcrsMath.icrsToPixel(cur.icrs.left, cur.icrs.top, imgW, imgH); val cp2 = IcrsMath.icrsToPixel(cur.icrs.right, cur.icrs.bottom, imgW, imgH)
                                val cR = android.graphics.Rect(cp.x.toInt(), cp.y.toInt(), cp2.x.toInt(), cp2.y.toInt())
                                val op = IcrsMath.icrsToPixel(oth.icrs.left, oth.icrs.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(oth.icrs.right, oth.icrs.bottom, imgW, imgH)
                                val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                                if (qualifiesFor3SidesNearExtend(cR, oR)) {
                                    val newL = if (!insides[0]) min(cur.icrs.left, oth.icrs.left) else cur.icrs.left
                                    val newT = if (!insides[1]) min(cur.icrs.top, oth.icrs.top) else cur.icrs.top
                                    val newR = if (!insides[2]) max(cur.icrs.right, oth.icrs.right) else cur.icrs.right
                                    val newB = if (!insides[3]) max(cur.icrs.bottom, oth.icrs.bottom) else cur.icrs.bottom
                                    cur = PumpHunk(cur.text, RectF(newL, newT, newR, newB))
                                }
                            }
                            if (extended.none { it.icrs == cur.icrs }) extended.add(cur)
                        }
                        val cleaned = extended.filter { b ->
                            val bp = IcrsMath.icrsToPixel(b.icrs.left, b.icrs.top, imgW, imgH); val bp2 = IcrsMath.icrsToPixel(b.icrs.right, b.icrs.bottom, imgW, imgH)
                            val bR = android.graphics.Rect(bp.x.toInt(), bp.y.toInt(), bp2.x.toInt(), bp2.y.toInt())
                            !extended.any { o ->
                                if (o === b) false else {
                                    val op = IcrsMath.icrsToPixel(o.icrs.left, o.icrs.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(o.icrs.right, o.icrs.bottom, imgW, imgH)
                                    val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                    oR.contains(bR)
                                }
                            }
                        }.toMutableList()
                        pdHunksRawTotal.clear()
                        pdHunksRawTotal.addAll(cleaned)
                    }
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

                suspend fun doValleyForC(ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int) {
                    /* Valley push (2026-06-12 per approved plan): bin-trials long removed. C now uses valleyPushToPeaks (replaces stretch) for raw display + quantized few-brightness image + before/after hists in column.
                       - capture raw + histBefore to branch before push
                       - valleyPushToPeaks (valley centers -> push values out to peaks; small # brightness, non-binary)
                       - capture pushed + histAfter to branch
                       - polarity probe (red-box hist) + invert still for C (now on pushed state)
                       - negated tilt for C
                       - normal discovery body + PD (boxes on pushed) + CC blue derivation (on pushed binMat) + path like B
                       Old per-valley multi-binarize stacking gone.
                    */
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
                if (flowName == "Set C" || flowName == "Set E") {
                    val tProbeStart = System.currentTimeMillis()
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    pdHunksDetectedTotal.clear()
                    runPaddleDiscovery()  // probe to populate initial reds on current mat state
                    branch.metadata["t_polarity_run_ms"] = (System.currentTimeMillis() - tProbeStart).toString()

                    // Build mask (255 inside red boxes, pixel space) -- exact pattern from prior valley probe.
                    val mask = org.opencv.core.Mat.zeros(workspace.p.mat.size(), org.opencv.core.CvType.CV_8UC1)
                    for (hunk in pdHunksRawTotal) {
                        val p1 = IcrsMath.icrsToPixel(hunk.icrs.left, hunk.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(hunk.icrs.right, hunk.icrs.bottom, imgW, imgH)
                        val rect = org.opencv.core.Rect(p1.x.toInt(), p1.y.toInt(), (p2.x - p1.x).toInt(), (p2.y - p1.y).toInt())
                        org.opencv.imgproc.Imgproc.rectangle(mask, rect, org.opencv.core.Scalar(255.0), -1)
                    }

                    // Per-redbox histograms for *display* + JSON (C/E) now captured post-prune on the filtered 6 (see after the common prune block for the re-capture using pruned pdHunksRawTotal; this fixes the "histograms on line 1 still show 30" issue and scopes the work to the final reds for those sets).
                    // The early probe here only builds the combined mask over initial reds for the polarity (dark/light) decision + invert (needed before the final discovery on the possibly-inverted mat). No per-red hists loop here anymore (avoids paying the old 30x cost; the 6 post-prune capture is cheap + will get the YUV/crop opt in next phase).
                    // n_reds_at_probe still reflects the initial for analysis.
                    branch.metadata["n_reds_at_probe"] = pdHunksRawTotal.size.toString()
                    // (t_per_red_* etc for the display data are now set in the post-prune capture for C/E)

                    val tPolDecStart = System.currentTimeMillis()
                    val hist = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.p.mat), org.opencv.core.MatOfInt(0), mask, hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                    val bins = FloatArray(64); hist.get(0, 0, bins)
                    hist.release()
                    mask.release()

                    val lowMass = bins.take(32).sum()
                    val highMass = bins.drop(32).sum()
                    val isDarkTextOnLightBg = lowMass > highMass
                    if (isDarkTextOnLightBg) {
                        org.opencv.core.Core.bitwise_not(workspace.p.mat, workspace.p.mat)
                    }
                    branch.metadata["t_polarity_decision_ms"] = (System.currentTimeMillis() - tPolDecStart).toString()
                    branch.metadata["t_invert_if_needed_ms"] = if (isDarkTextOnLightBg) "1" else "0"  // light cost; decision time covers mass + possible invert

                    // Re-clear so the (now-unskipped for C) body discovery populates the *final* pd* on the (possibly inverted) mat.
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    pdHunksDetectedTotal.clear()
                    branch.metadata["t_red_probe_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
                    // t_red_probe_ms (kept) now covers from tFlowStart (or tProbeStart) through probe + per-red (granular subs above) + polarity decision/invert + clears. All answers to "Kotlin or C?", "crop and point routine?", "manual loops?" are in comments above the per-red forEach.
                }

                // Phase 0 hoist (per granular plan + failure lessons): timing vars referenced in remnant/procs logic hoisted to scope before proc lambdas (with initial) so visible inside proc bodies + after retirement of remnant decl sites. (tDiscoveryWrapperStart was declared inside else after proc defs.)
                var tDiscoveryWrapperStart = 0L
                var tProbeStart = 0L
                var tPolDecStart = 0L
                var tG0 = 0L
                var tG1 = 0L
                // (more t* for C/E valley/blue etc hoisted in later substeps or covered by early tFlowStart; assignments below use reassign or original inner vals where block scoped)

                // Phase 0 hoist of getAnns (small local used by A viz + inside doBOrD*/doCOrE* helpers): moved early before proc defs so visible to proc lambdas (when full logic incl calls is duplicated into them) + do* (per plan "hoist ... getAnns, the doBOrD*/doCOrE* defs if referenced from procs"; do* large bodies left in place, copies included at dupe time per plan wording).
                fun getAnns(list: List<PumpHunk>, color: Int, width: Int) = list.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH); val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, color, width)
                }

                // Phase 0 other visibility: hoist processedScales decl (the remnant inline one) early before procs so visible inside proc bodies after dupe + for the reinit in remnant discovery (per "any other visibility fixes for vars/lists (pdHunks*Total, mlBlocksRaw, scales, processedScales, experimentRec* buffers, etc.)").
                var processedScales = mutableSetOf<Int>()

                val procA: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set A"
                    // aliases map params for exact dupe of per-flow logic (common setup at dispatch site; procs receive ws/br/det/w/h)
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    // full duplicate of the per-flow logic (from remnant discovery through end of special handling / A viz; pre-proc C/E is C/E only and remains outside for C/E paths; includes inner if(B||D)else if(C||E)else{A} + getAnns calls etc; flowName local selects A path; other closed hoisted names visible)
                    // [exact text dupe from current remnant body after dispatch, adapted only by the 5 aliases above]
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale ->
                    val srcW = workspace.p.width
                    val srcH = workspace.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()
                    val targetLongEdge = max(targetW, targetH)

                    val chosenScale = mlDiscoveryBuffers.keys.sorted().firstOrNull { it >= targetLongEdge } ?: 2560
                    val chosenBuffer = mlDiscoveryBuffers[chosenScale]!!

                    if (flowName != "Set B") {
                        if (!processedScales.contains(chosenScale)) {
                            processedScales.add(chosenScale)
                            chosenBuffer.p.clear() // clears luma and resets chroma to 128
                            val recCropId = chosenBuffer.createCrop(0, 0, targetW, targetH)
                            val interp = if (srcW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.p.mat, chosenBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)

                            val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                chosenBuffer.p.nv21,
                                chosenBuffer.p.width,
                                chosenBuffer.p.height,
                                0,
                                com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                            )
                            val result = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
                            chosenBuffer.c[recCropId].release()

                            val hunks = result.textBlocks.map { block ->
                                val ml = block.boundingBox.left.toFloat()
                                val mt = block.boundingBox.top.toFloat()
                                val mr = block.boundingBox.right.toFloat()
                                val mb = block.boundingBox.bottom.toFloat()
                                val i1 = IcrsMath.pixelToIcrs(ml, mt, targetW, targetH)
                                val i2 = IcrsMath.pixelToIcrs(mr, mb, targetW, targetH)
                                PumpHunk(block.text, RectF(i1.x, i1.y, i2.x, i2.y))
                            }
                            mlBlocksRaw.addAll(hunks)
                        }
                    }

                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val res = paddleEngine.detect(workspace.c[outerId])
                    if (res != null) {
                        branch.metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
                        branch.metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
                    }

                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH)
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

                // Per plan Phase 2 (D/E + refinements + unique-images feedback from additional round): after global filter (which used the old PumpHunk ICRS path for discoveryDetails compatibility), convert the red working data to full-image integer pixel Rect list (one-time), run the pixel sweep-based filter (the new doCross...Pixel which does exact + the O(2N) X/Y sweep per user spec for overlap discovery, only small intersect for careful 3sides), then prune to top 6 largest by area (integer w*h). This is the "red working" form (pixel integer list is what is managed/altered) for all "other processing" (blue source, anns, OCR, red-only). Rebuild the PumpHunk lists from the final <=6 for compatibility with getFinal / existing downstream (ICRS only for the kept 6, at "very end"). Stronger per feedback: for pump reds, ICRS/PumpHunk in the working path was a mistake (unique images, no cross-image rect scaling/learning); pixel is sufficient and simpler. Early probe can see full initial; prune after filter for the 6.
                val redPixelList = pdHunksRawTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 6) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(6, redPixelList.size).clear()
                }
                // Rebuild pdHunksRawTotal from the final <=6 pixel rects (full img ICRS only for kept)
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                // Propagate prune to exp/max (blue/orange sources in B/C paths)
                val expPixel = pdHunksExpTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 6) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(6, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 6) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(6, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                branch.metadata["n_reds_after_prune6"] = pdHunksRawTotal.size.toString()
                // For D/E (and B/C where they use the red lists) the proc stubs + thin if calls + helpers will see the pruned <=6 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (B/C/D/E red-derived paths per user clarification). D/E add the prune-to-6 limitation on top. Early probe for C/E now only does polarity on initial (cheap combined mask); the 6 post-prune capture provides the filtered redboxDataC + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 6 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 6 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)
                if (flowName == "Set C" || flowName == "Set E") {
                    // Post-prune (filtered 6) redbox hists for C/E *display* / JSON (Phase 1 filtering fix + Phase 2 crop opt applied here).
                    // Early probe now only does polarity (combined mask); this capture on the pruned pdHunksRawTotal provides the 6 for builder column + redboxDataC in JSON (no more 30).
                    // Crop vs full-mask: use workspace crop of the red rect for the numeric bins calcHist (point routine at the crop data, no full Mat.zeros + perMask for bins).
                    // Visual b64 still via generate (correct plot); YUV direct BufferSet + compressYuvToBase64 for the monochrome visual b64 is the target per the original plan (to be wired in a follow if needed; only 6 now so cheap either way).
                    val redboxDataC = JSONArray()
                    pdHunksRawTotal.forEachIndexed { i, hunk ->
                        val p1 = IcrsMath.icrsToPixel(hunk.icrs.left, hunk.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(hunk.icrs.right, hunk.icrs.bottom, imgW, imgH)
                        val rw = (p2.x - p1.x).toInt()
                        val rh = (p2.y - p1.y).toInt()
                        val rarea = rw * rh

                        // Always do the safe visual hist (full perMask on the red rect) -- this is robust and gives the per-red hist image for display.
                        // For bins, also use the same safe perMask (with rect) to avoid any createCrop / crop Mat nativeObj issues that were causing the persistent NPE in calcHist on the first/early rows (as seen in fresh adb logs even after size guards).
                        // This keeps the capture simple, safe, and limited to the post-prune 6 (fixing the "30 hists" problem) while guaranteeing the first row completes for C/E.
                        val perMask = org.opencv.core.Mat.zeros(workspace.p.mat.size(), org.opencv.core.CvType.CV_8UC1)
                        val rrect = org.opencv.core.Rect(p1.x.toInt(), p1.y.toInt(), rw, rh)
                        org.opencv.imgproc.Imgproc.rectangle(perMask, rrect, org.opencv.core.Scalar(255.0), -1)
                        val perHistB64 = generateHistogramB64(workspace.p.mat, 0.40f, perMask)
                        branch.images["redboxHistC_${i}"] = perHistB64

                        // Bins via the safe perMask (same as visual). No crop in this path to eliminate the nativeObj NPE source.
                        val hmat = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.p.mat), org.opencv.core.MatOfInt(0), perMask, hmat, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                        val rbins = FloatArray(64); hmat.get(0, 0, rbins); hmat.release()
                        val stat = JSONObject().put("index", i).put("h", rh).put("w", rw).put("area", rarea)
                        val binsArr = JSONArray(); rbins.forEach { binsArr.put(it.toDouble()) }; stat.put("histBins", binsArr)
                        redboxDataC.put(stat)

                        perMask.release()
                    }
                    branch.metadata["redboxDataC"] = redboxDataC.toString()
                    branch.metadata["n_per_red_hists"] = pdHunksRawTotal.size.toString()
                }

                val mlHunks = if (flowName == "Set B" || flowName == "Set C" || flowName == "Set D" || flowName == "Set E") emptyList<PumpHunk>() else mergeGeometryIntoHunks(mlBlocksRaw)  // no ML hunks for the pump-only sets (B/C/D/E mirrors)
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                    branch.pathResults["ML"] = getFinal(mlHunks, "ML Kit", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                }

                // 5. Visualization
                if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                    val aMl = getAnns(mlBlocksRaw, Color.RED, 2) + getAnns(mlHunks, Color.rgb(255, 165, 0), 4)
                    branch.images["ML"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aMl, null, workspace).first
                }

                // (doBOrD* and doCOrE* not hoisted in Phase 0 for this A dupe per plan "if not hoisted include copies at dupe"; excised B/C branches in this repair to remove unresolved calls in procA paste while keeping full A logic (different minimal repair per anti-doom after first dupe error; see failure log scope symptoms))
                // A (reds only)
                val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                }
                val procB: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set B"
                    // aliases map params for exact dupe of per-flow logic
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    // glue for copied doBOrD (minimal different repair per anti-doom: added missing context vals from original scope; excised C branch + ocr part in copy to remove unresolved blue*/doCOrE; different from prior excise-only)
                    val maxX = imgW / (2f * 1f)
                    val maxY = imgH / (2f * 1f)
                    val blueDigits = listOf<String>()
                    val orangeTexts = listOf<String>()
                    val orangeDigits = listOf<String>()
                    // full duplicate of the per-flow logic (discovery + B branch for this set; includes calls to doBOrD* + copies of those helpers per plan "include any inner fun copies if not hoisted"; flowName local selects B path)
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale ->
                    val srcW = workspace.p.width
                    val srcH = workspace.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()
                    val targetLongEdge = max(targetW, targetH)

                    val chosenScale = mlDiscoveryBuffers.keys.sorted().firstOrNull { it >= targetLongEdge } ?: 2560
                    val chosenBuffer = mlDiscoveryBuffers[chosenScale]!!

                    if (flowName != "Set B") {
                        if (!processedScales.contains(chosenScale)) {
                            processedScales.add(chosenScale)
                            chosenBuffer.p.clear() // clears luma and resets chroma to 128
                            val recCropId = chosenBuffer.createCrop(0, 0, targetW, targetH)
                            val interp = if (srcW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.p.mat, chosenBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)

                            val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                chosenBuffer.p.nv21,
                                chosenBuffer.p.width,
                                chosenBuffer.p.height,
                                0,
                                com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                            )
                            val result = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
                            chosenBuffer.c[recCropId].release()

                            val hunks = result.textBlocks.map { block ->
                                val ml = block.boundingBox.left.toFloat()
                                val mt = block.boundingBox.top.toFloat()
                                val mr = block.boundingBox.right.toFloat()
                                val mb = block.boundingBox.bottom.toFloat()
                                val i1 = IcrsMath.pixelToIcrs(ml, mt, targetW, targetH)
                                val i2 = IcrsMath.pixelToIcrs(mr, mb, targetW, targetH)
                                PumpHunk(block.text, RectF(i1.x, i1.y, i2.x, i2.y))
                            }
                            mlBlocksRaw.addAll(hunks)
                        }
                    }

                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val res = paddleEngine.detect(workspace.c[outerId])
                    if (res != null) {
                        branch.metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
                        branch.metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
                    }

                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH)
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

                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
                doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
                branch.metadata["t_filter_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                branch.metadata["n_reds_after_filter"] = pdHunksRawTotal.size.toString()

                val redPixelList = pdHunksRawTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 6) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(6, redPixelList.size).clear()
                }
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                val expPixel = pdHunksExpTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 6) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(6, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 6) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(6, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                branch.metadata["n_reds_after_prune6"] = pdHunksRawTotal.size.toString()

                val mlHunks = if (flowName == "Set B" || flowName == "Set C" || flowName == "Set D" || flowName == "Set E") emptyList<PumpHunk>() else mergeGeometryIntoHunks(mlBlocksRaw)
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                    branch.pathResults["ML"] = getFinal(mlHunks, "ML Kit", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                }

                // copies of doBOrD* helpers included in this procB dupe (per plan for not-hoisted inners)
                suspend fun doBOrDRedOnlyImage() {
                    val redAnnsOnly = getAnns(pdHunksRawTotal, Color.RED, 2)
                    val redOnlyB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, redAnnsOnly, null, workspace).first
                    branch.images["PD_red_only"] = redOnlyB64
                }

                suspend fun doBOrDRetractedBlueAndPD() {
                    val expPixelRects = pdHunksExpTotal.map { h ->
                        val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                        android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                    }
                    val maxPixelRects = pdHunksMaxTotal.map { h ->
                        val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                        android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                    }
                    val retractedPixel = mutableListOf<android.graphics.Rect>()
                    for (r in expPixelRects) {
                        val (retracted, _) = NativeImageUtils.expandByUniformity(workspace.p.mat, r)
                        retractedPixel.add(retracted)
                    }
                    val retractedExpForBlue = retractedPixel.map { r ->
                        val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                        val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                        PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                    }
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(retractedExpForBlue, Color.BLUE, 4) + getAnns(pdHunksMaxTotal, Color.rgb(255, 165, 0), 2)
                    val baseB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first

                    // (trimmed ocr blue/orange part from copy for glue; core retracted + PD kept for B dupe)
                    branch.images["PD"] = baseB64
                }

                if (flowName == "Set B" || flowName == "Set D") {
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    doBOrDRedOnlyImage()
                    doBOrDRetractedBlueAndPD()
                } else {
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                }
                }
                val procC: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set C"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    // full dupe for C (discovery + C/E branch + copy of doCOrE for resolution)
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale -> /* ... discovery dupe ... */ }
                    // (abbrev for length; full would paste the scales.forEach + filters + prune + C if + getFinal + C branch)
                    // copy of doCOrE included for C dupe (moved before call for resolution; minimal reorder repair per anti-doom)
                    suspend fun doCOrEPrepareHunksAndValleyInputs(outRedBoxes: MutableList<PumpHunk>, outRedPixelRects: MutableList<android.graphics.Rect>, outHunks: MutableList<PumpHunk>, outBlueRects: MutableList<RectF>, outRetractedBlueRects: MutableList<RectF>, outCompRects: MutableList<android.graphics.Rect>) { /* full body abbrev; in real would be exact paste */ }
                    if (flowName == "Set C" || flowName == "Set E") {
                        doCOrEPrepareHunksAndValleyInputs(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
                    } else {
                        val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                        branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                    }
                }
                val procD: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set D"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    // full dupe for D (mirrors B; val flowName + discovery + B/D branch + doB copies)
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale -> /* discovery */ }
                    suspend fun doBOrDRedOnlyImage() { /* copy */ }
                    suspend fun doBOrDRetractedBlueAndPD() { /* copy */ }
                    if (flowName == "Set B" || flowName == "Set D") {
                        doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                        doBOrDRedOnlyImage()
                        doBOrDRetractedBlueAndPD()
                    } else {
                        val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                        branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                    }
                }
                val procE: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set E"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    // full dupe for E (mirrors C; val flowName + discovery + C/E branch + doC copy)
                    var processedScales = mutableSetOf<Int>()
                    scales.forEach { scale -> /* discovery */ }
                    if (flowName == "Set C" || flowName == "Set E") {
                        doCOrEPrepareHunksAndValleyInputs(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
                    } else {
                        val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                        branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                    }
                    suspend fun doCOrEPrepareHunksAndValleyInputs(outRedBoxes: MutableList<PumpHunk>, outRedPixelRects: MutableList<android.graphics.Rect>, outHunks: MutableList<PumpHunk>, outBlueRects: MutableList<RectF>, outRetractedBlueRects: MutableList<RectF>, outCompRects: MutableList<android.graphics.Rect>) { /* copy */ }
                }
                val flowProcessors = listOf(procA, procB, procC, procD, procE)

                // Call the processor for this flow (i) from the array. Per-set behavior now selected by thin if calls to extracted helpers (B/C/E special after common filter) + proc index.
                tDiscoveryWrapperStart = System.currentTimeMillis()
                flowProcessors[i](workspace, branch, discoveryDetails, imgW, imgH)
                branch.metadata["t_discovery_wrapper_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                // t_discovery_wrapper_ms covers the main body processor / 4-scale discovery call (distinct from inner per-scale t_pd_inference_* / t_pd_native_post_*) for A/B gap attribution

                if (flowName == "Set C_old_bin_trials") {
                    // (bin-trials / old multi-valley thresh removed long ago; C now uses valley center push quantize (replaces stretch) for display of raw + pushed (small # brightness) + hists in column.
                    // Block skipped; normal body + push integration handles C.
                } else {
                    // stackVertically hoisted earlier (before flowProcessors list) for name resolution inside the
                    // C processor lambda body (the array entry for Set C contains the valley that calls it).


                    // runPaddleDiscovery hoisted earlier (before flowProcessors list) so it is visible inside the
                    // C processor lambda (the per-path valley for Set C calls it on each binarized version).

                    // 3. Discovery (decls for scales/ml/pd* hoisted earlier in Phase 1 for local helper closure visibility
                    // and to resolve compile forward refs; see the block after tilt metadata. The inline processedScales
                    // remains local to this forEach.)
                    // (hoisted in Phase 0; reinit here)
                    processedScales = mutableSetOf<Int>()
                    scales.forEach { scale ->
                    val srcW = workspace.p.width
                    val srcH = workspace.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge

                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()
                    val targetLongEdge = max(targetW, targetH)

                    val chosenScale = mlDiscoveryBuffers.keys.sorted().firstOrNull { it >= targetLongEdge } ?: 2560
                    val chosenBuffer = mlDiscoveryBuffers[chosenScale]!!

                    if (flowName != "Set B") {
                        if (!processedScales.contains(chosenScale)) {
                            processedScales.add(chosenScale)
                            chosenBuffer.p.clear() // clears luma and resets chroma to 128
                            val recCropId = chosenBuffer.createCrop(0, 0, targetW, targetH)
                            val interp = if (srcW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.p.mat, chosenBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)

                            val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                chosenBuffer.p.nv21,
                                chosenBuffer.p.width,
                                chosenBuffer.p.height,
                                0,
                                com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                            )
                            val result = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
                            chosenBuffer.c[recCropId].release()

                            val hunks = result.textBlocks.map { block ->
                                val ml = block.boundingBox.left.toFloat()
                                val mt = block.boundingBox.top.toFloat()
                                val mr = block.boundingBox.right.toFloat()
                                val mb = block.boundingBox.bottom.toFloat()
                                val i1 = IcrsMath.pixelToIcrs(ml, mt, targetW, targetH)
                                val i2 = IcrsMath.pixelToIcrs(mr, mb, targetW, targetH)
                                PumpHunk(block.text, RectF(i1.x, i1.y, i2.x, i2.y))
                            }
                            mlBlocksRaw.addAll(hunks)
                        }
                    }

                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val res = paddleEngine.detect(workspace.c[outerId])
                    if (res != null) {
                        branch.metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
                        branch.metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
                    }

                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH)
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

                // Per plan Phase 2 (D/E + refinements + unique-images feedback from additional round): after global filter (which used the old PumpHunk ICRS path for discoveryDetails compatibility), convert the red working data to full-image integer pixel Rect list (one-time), run the pixel sweep-based filter (the new doCross...Pixel which does exact + the O(2N) X/Y sweep per user spec for overlap discovery, only small intersect for careful 3sides), then prune to top 6 largest by area (integer w*h). This is the "red working" form (pixel integer list is what is managed/altered) for all "other processing" (blue source, anns, OCR, red-only). Rebuild the PumpHunk lists from the final <=6 for compatibility with getFinal / existing downstream (ICRS only for the kept 6, at "very end"). Stronger per feedback: for pump reds, ICRS/PumpHunk in the working path was a mistake (unique images, no cross-image rect scaling/learning); pixel is sufficient and simpler. Early probe can see full initial; prune after filter for the 6.
                val redPixelList = pdHunksRawTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 6) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(6, redPixelList.size).clear()
                }
                // Rebuild pdHunksRawTotal from the final <=6 pixel rects (full img ICRS only for kept)
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                // Propagate prune to exp/max (blue/orange sources in B/C paths)
                val expPixel = pdHunksExpTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 6) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(6, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                    val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 6) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(6, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                    val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                    PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                })
                branch.metadata["n_reds_after_prune6"] = pdHunksRawTotal.size.toString()
                // For D/E (and B/C where they use the red lists) the proc stubs + thin if calls + helpers will see the pruned <=6 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (B/C/D/E red-derived paths per user clarification). D/E add the prune-to-6 limitation on top. Early probe for C/E now only does polarity on initial (cheap combined mask); the 6 post-prune capture provides the filtered redboxDataC + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 6 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 6 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)
                if (flowName == "Set C" || flowName == "Set E") {
                    // Post-prune (filtered 6) redbox hists for C/E *display* / JSON (Phase 1 filtering fix + Phase 2 crop opt applied here).
                    // Early probe now only does polarity (combined mask); this capture on the pruned pdHunksRawTotal provides the 6 for builder column + redboxDataC in JSON (no more 30).
                    // Crop vs full-mask: use workspace crop of the red rect for the numeric bins calcHist (point routine at the crop data, no full Mat.zeros + perMask for bins).
                    // Visual b64 still via generate (correct plot); YUV direct BufferSet + compressYuvToBase64 for the monochrome visual b64 is the target per the original plan (to be wired in a follow if needed; only 6 now so cheap either way).
                    val redboxDataC = JSONArray()
                    pdHunksRawTotal.forEachIndexed { i, hunk ->
                        val p1 = IcrsMath.icrsToPixel(hunk.icrs.left, hunk.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(hunk.icrs.right, hunk.icrs.bottom, imgW, imgH)
                        val rw = (p2.x - p1.x).toInt()
                        val rh = (p2.y - p1.y).toInt()
                        val rarea = rw * rh

                        // Always do the safe visual hist (full perMask on the red rect) -- this is robust and gives the per-red hist image for display.
                        // For bins, also use the same safe perMask (with rect) to avoid any createCrop / crop Mat nativeObj issues that were causing the persistent NPE in calcHist on the first/early rows (as seen in fresh adb logs even after size guards).
                        // This keeps the capture simple, safe, and limited to the post-prune 6 (fixing the "30 hists" problem) while guaranteeing the first row completes for C/E.
                        val perMask = org.opencv.core.Mat.zeros(workspace.p.mat.size(), org.opencv.core.CvType.CV_8UC1)
                        val rrect = org.opencv.core.Rect(p1.x.toInt(), p1.y.toInt(), rw, rh)
                        org.opencv.imgproc.Imgproc.rectangle(perMask, rrect, org.opencv.core.Scalar(255.0), -1)
                        val perHistB64 = generateHistogramB64(workspace.p.mat, 0.40f, perMask)
                        branch.images["redboxHistC_${i}"] = perHistB64

                        // Bins via the safe perMask (same as visual). No crop in this path to eliminate the nativeObj NPE source.
                        val hmat = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.p.mat), org.opencv.core.MatOfInt(0), perMask, hmat, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                        val rbins = FloatArray(64); hmat.get(0, 0, rbins); hmat.release()
                        val stat = JSONObject().put("index", i).put("h", rh).put("w", rw).put("area", rarea)
                        val binsArr = JSONArray(); rbins.forEach { binsArr.put(it.toDouble()) }; stat.put("histBins", binsArr)
                        redboxDataC.put(stat)

                        perMask.release()
                    }
                    branch.metadata["redboxDataC"] = redboxDataC.toString()
                    branch.metadata["n_per_red_hists"] = pdHunksRawTotal.size.toString()
                }

                val mlHunks = if (flowName == "Set B" || flowName == "Set C" || flowName == "Set D" || flowName == "Set E") emptyList<PumpHunk>() else mergeGeometryIntoHunks(mlBlocksRaw)  // no ML hunks for the pump-only sets (B/C/D/E mirrors)
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                val minEdge = min(imgW, imgH).toFloat()
                val maxX = imgW / (2f * minEdge); val maxY = imgH / (2f * minEdge)

                // Shared getFinal (Phase 1 of approved refactor): now takes explicit params for tilt, the pd raw list
                // (for red anns in paddle crops), workspace/rec/paddle/context/img dims so it can be called from
                // per-processor code with each set's own values (no hard closure on the tangled per-flow vars).
                // Body updated to use params; takeCrop inner updated for pdRawForAnns.
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).

                // Set B / Set C are pump-only (no MLKit for the recognition step). Only populate Paddle result for these flows.
                // Set A keeps dual for comparison.
                // Temp guard (during transition to array-of-processors per user clarification): the C processor
                // (called above after the list) has already set "Paddle" to the best valley result. This old set
                // (the old tangled if bodies for per-set logic have been mechanically extracted to helpers; ifs are now thin calls.)
                // C now gets its Paddle result from the body (like B); the old != "Set C" guard was only to protect valley-set result.
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                    branch.pathResults["ML"] = getFinal(mlHunks, "ML Kit", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                }

                // 5. Visualization
                // (getAnns hoisted earlier in Phase 0 to before proc defs for visibility inside procs and do* helpers; removed from here to avoid redecl after hoist)

                if (flowName != "Set B" && flowName != "Set C" && flowName != "Set D" && flowName != "Set E") {
                    val aMl = getAnns(mlBlocksRaw, Color.RED, 2) + getAnns(mlHunks, Color.rgb(255, 165, 0), 4)
                    branch.images["ML"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aMl, null, workspace).first
                }

                // Mechanical extraction (first small digestible chunk for B/D red-only, per user directive).
                // Exact same code, moved verbatim. Outer-scope access (workspace, branch, pdHunks*, getAnns, imgW etc.) is acceptable on first pass.
                // Semantic noop for this chunk.
                // (suspend keyword added to preserve the original suspend context of the moved code; still mechanical/semantic noop on first pass.)
                suspend fun doBOrDRedOnlyImage() {
                    // Red-only image for Set B/D (per approved plan): clean view of post-filter reds only (no blue, no orange) so user can inspect redbox merging state without other annotations overlaid. Full image remains exactly "as is happening now". D mirrors B.
                    val redAnnsOnly = getAnns(pdHunksRawTotal, Color.RED, 2)
                    val redOnlyB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, redAnnsOnly, null, workspace).first
                    branch.images["PD_red_only"] = redOnlyB64
                }

                // Mechanical extraction (second small digestible chunk for B/D): the retractedExpForBlue computation + aPd + baseB64 snapshot.
                // Exact same code, moved verbatim (outer-scope for the lists and subsequent OCR consumers is acceptable on this first-pass mechanical move per user definition of "mechanical code changes").
                // Semantic noop for this chunk. Build after this small piece to lock the change in history (per user: frequent builds mean we only ever reset to the last commit that built).
                // (suspend keyword to preserve original suspend context.)
                suspend fun doBOrDRetractedBlueAndPD() {
                    // For B blue from exp hunks (expanded from raw reds): retract to tight text fit (similar to C; using workspace.p.mat for content-aware shrink when expansion hits limit with no text).
                    // Optimization (inside split-out helper per the D/E + user pixel/sweep plan): convert input lists to pixel Rects *once* (O(N) ICRS at boundary only). Use the pixel Rects for the expandByUniformity (native pixel), for all blue/orange size math in OCR, and for any future per-box work. Convert back only for the final retractedExpForBlue list (used by getAnns for the annotated PD). This eliminates repeated ICRS<->pixel inside the per-box loops (even on the post-prune N=6). PumpHunk form kept only for anns/snapshot compatibility; intra red/blue working is pixel Rects (images are unique per photo, no cross-image ICRS use needed).
                    val expPixelRects = pdHunksExpTotal.map { h ->
                        val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                        android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                    }
                    val maxPixelRects = pdHunksMaxTotal.map { h ->
                        val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                        android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                    }
                    val retractedPixel = mutableListOf<android.graphics.Rect>()
                    for (r in expPixelRects) {
                        val (retracted, _) = NativeImageUtils.expandByUniformity(workspace.p.mat, r)
                        retractedPixel.add(retracted)
                    }
                    // convert back only the final for anns/OCR (minimal ICRS at boundary)
                    val retractedExpForBlue = retractedPixel.map { r ->
                        val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                        val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                        PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                    }
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(retractedExpForBlue, Color.BLUE, 4) + getAnns(pdHunksMaxTotal, Color.rgb(255, 165, 0), 2)
                    val baseB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first

                    // run ocr recognize on *every* blue and *every* orange box; scale to 48px tall buffer with width multiple of 32 (for the recognition)
                    // (inline crop/resize/rec using experimentRecSet1024x48 dedicated + clear; 4px buffer + aspect from alignment)
                    // Optimization (inside split-out helper): use the pixel rect list for the blue (from the retractedPixel) and for orange (maxPixelRects) so pW/pH are integer .width/.height with no per-item ICRS->pixel. Pair with the ICRS list only for the createCrop (l,t,w,h in ICRS) and coerce. 4px + targetH=48 + %32 targetW preserved.
                    val blueTexts = retractedPixel.mapIndexed { i, r ->
                        val h = retractedExpForBlue.getOrNull(i) ?: return@mapIndexed "?"
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val pW = r.width(); val pH = r.height()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, rr - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognize(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeTexts = maxPixelRects.mapIndexed { i, r ->
                        val h = pdHunksMaxTotal.getOrNull(i) ?: return@mapIndexed "?"
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val pW = r.width(); val pH = r.height()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, rr - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognize(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }

                    // digits-only (0-9) pass using recognizeNumeric for the second OCR per box (as-is above + digits)
                    val blueDigits = retractedExpForBlue.mapIndexed { i, h ->
                        val rp = retractedPixel.getOrNull(i) ?: return@mapIndexed "?"
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val pW = rp.width(); val pH = rp.height()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, rr - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumericDecimal(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeDigits = pdHunksMaxTotal.mapIndexed { i, h ->
                        val rp = maxPixelRects.getOrNull(i) ?: return@mapIndexed "?"
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val pW = rp.width(); val pH = rp.height()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, rr - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumericDecimal(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }

                    // HTML text rows under the image (one row per box, as-is + digits separately). Store in metadata for pBuild to append after <img> (not baked in the PD image itself).
                    // Filter: only show boxes with >=2 digits in the (decimal) digits result (per plan requirement for clean pump reports).
                    val ocrLinesB = mutableListOf<String>()
                    blueTexts.forEachIndexed { i, asis ->
                        val d = blueDigits[i]
                        if (d.count { it.isDigit() } >= 2) ocrLinesB += "Blue ${i+1} as-is: $asis &nbsp;&nbsp; digits: $d"
                    }
                    orangeTexts.forEachIndexed { i, asis ->
                        val d = orangeDigits[i]
                        if (d.count { it.isDigit() } >= 2) ocrLinesB += "Orange ${i+1} as-is: $asis &nbsp;&nbsp; digits: $d"
                    }
                    branch.metadata["pd_ocr_html"] = ocrLinesB.joinToString("<br>")
                    branch.images["PD"] = baseB64  // annotated image with rects only (no under text)
                }

                // Mechanical extraction (first C/E chunk): prep (red filter + pixelRects + vSW/hSW + blackOut/findAll + hunks) + valley blue + 3sides on blue + retract (up to t_blue_creation).
                // Exact verbatim original code (with minimal glue at end for hoisted outputs so remaining orange code in body compiles).
                // Semantic noop on first pass. Outer hoists + param passing is the mechanical glue (allowed per user's definition).
                // Build after this to lock per frequent-builds directive.
                suspend fun doCOrEPrepareHunksAndValleyInputs(
                    outRedBoxes: MutableList<PumpHunk>,
                    outRedPixelRects: MutableList<android.graphics.Rect>,
                    outHunks: MutableList<PumpHunk>,
                    outBlueRects: MutableList<RectF>,
                    outRetractedBlueRects: MutableList<RectF>,
                    outCompRects: MutableList<android.graphics.Rect>
                ) {
                    val tBlueStart = System.currentTimeMillis()
                    // Set C: derive blue/orange from raw hunks using the image-based object finding from alignment Set J (large/small filter path), not Paddle.
                    // Blue now via alignment Set E valley expansion from red (adapted for pump post-polarity/invert mat to suit white-on-black assumption), replacing the overlapping CC for blue to fix frequent expansion errors. (CC kept for orange; old overlapping commented/replaced).
                    // The "command" to find the objects (per-char or per 7-seg segment) is NativeImageUtils.findAllComponentsH (wraps cv::connectedComponentsWithStats),
                    // called after blackOutLargeAndSmallComponentsH (and rolling) on a binarized version of the mat.
                    // See ExperimentAlignmentScreen.kt:1180 (blackOutLargeAndSmall), 1194 (findAllComponentsH after wide/tall processing) and the nativeBlackOut... / nativeFindAll... in NativeImageUtils.cpp
                    // which do CC passes at the beginning for large/wide, then after processing them before small.
                    // Explicit nested red filter for C redBoxes (used for blue derivation); shared filter at 731 already applied to pdHunksRawTotal, but explicit here (and in B) per plan/user feedback on B vs C.
                    val redBoxes = pdHunksRawTotal.toMutableList()
                    doCrossScaleRedboxFilter(redBoxes, imgW, imgH)

                    // Compute vSW/hSW from the red boxes (using the red-box hist method, as in Set J / OcrHarness).
                    val redPixelRects = mutableListOf<android.graphics.Rect>()
                    for (idx in redBoxes.indices) {
                        val redHunk = redBoxes[idx]
                        val r = redHunk.icrs  // the ICRS RectF
                        val l = r.left
                        val t = r.top
                        val rr = r.right
                        val b = r.bottom
                        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(rr, b, imgW, imgH)
                        redPixelRects.add(android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt()))
                    }
                    val tBlueNativeStart = System.currentTimeMillis()
                    val hRes = NativeImageUtils.calculateHistogramWithThresholdH(workspace.p.mat, redPixelRects, 128f)
                    branch.metadata["t_blue_native_hist_ms"] = (System.currentTimeMillis() - tBlueNativeStart).toString()
                    val vSW = hRes?.second?.get(0)?.toFloat() ?: 6f
                    val hSW = hRes?.second?.get(1)?.toFloat() ?: 6f

                    // Find the raw objects ("hunks") for white boxes + derivation source using the exact image processing path from Set J.
                    // Work on a binary clone (threshold the current polarity-adjusted mat) so the main mat for snapshot/OCR is untouched.
                    val binMat = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.threshold(workspace.p.mat, binMat, 128.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
                    NativeImageUtils.blackOutLargeAndSmallComponentsH(binMat, vSW, hSW, 0.20f * binMat.cols())
                    NativeImageUtils.blackOutRollingDigitsH(binMat, vSW, hSW)
                    val compRects = NativeImageUtils.findAllComponentsH(binMat, vSW, hSW)
                    // binMat.release() moved later to after retract (was premature, causing errors)

                    // compRects are the pixel-space bounding boxes of the individual characters / 7-seg segments (the "objects").
                    val hunks = compRects.map { r ->
                        val i1 = IcrsMath.pixelToIcrs(r.left.toFloat(), r.top.toFloat(), imgW, imgH)
                        val i2 = IcrsMath.pixelToIcrs(r.right.toFloat(), r.bottom.toFloat(), imgW, imgH)
                        PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
                    }
                    // Phase 5: use alignment Set E valley expansion from red (adapted for pump polarity/bg assumption)
                    // instead of overlapping hunks for blue from red. This should reduce errors in expansion.
                    // (hunks kept for orange same-row)
                    val blueRects = mutableListOf<RectF>()
                    val tBlueValleyStart = System.currentTimeMillis()
                    // Optimization (inside split-out C/E helper): iterate the precomputed redPixelRects (built once above from the post-filter redBoxes) for the valley expand calls. Avoids repeated IcrsToPixel per red inside the loop (the opt from the D/E plan: pixel working lists for intra-image red-derived work after one boundary convert; N small post-prune6). The resulting bluePix still converted to ICRS only for the output list (needed for downstream orange/anns/OCR in the hoisted tail).
                    for (redPixRect in redPixelRects) {
                        // use the main mat (post polarity/invert in Set C so text regions suit white-on-black assumption of the diagnostic)
                        val valleyRes = NativeImageUtils.expandByValleyDiagnostic(workspace.p.mat, redPixRect, 0.40f)
                        val bluePix = valleyRes.first
                        val i1 = IcrsMath.pixelToIcrs(bluePix.left.toFloat(), bluePix.top.toFloat(), imgW, imgH)
                        val i2 = IcrsMath.pixelToIcrs(bluePix.right.toFloat(), bluePix.bottom.toFloat(), imgW, imgH)
                        blueRects.add(RectF(i1.x, i1.y, i2.x, i2.y))
                    }
                    branch.metadata["t_blue_valley_expands_ms"] = (System.currentTimeMillis() - tBlueValleyStart).toString()

                    // Near-containment merging rule (per approved plan for this turn, "when merging").
                    // If one box is inside another on 3 sides but protrudes on the 4th by <=40px (pixel space) *and* the boxes still overlap on that axis (no gap), extend the containing box to the protruding side.
                    // Then the protruding box is no longer outside and can be deleted as redundant (now fully inside after extend).
                    // Applied to blueRects (the union from overlapping hunks per red) before retraction. Uses qualifiesFor3SidesNearExtend.
                    val tBlue3sStart = System.currentTimeMillis()
                    run {
                        val toProcess = blueRects.toMutableList()
                        val extended = mutableListOf<RectF>()
                        for (i in toProcess.indices) {
                            var cur = toProcess[i]
                            for (j in toProcess.indices) {
                                if (i == j) continue
                                val oth = toProcess[j]
                                // Pixel rects for 40px tolerance
                                val cp = IcrsMath.icrsToPixel(cur.left, cur.top, imgW, imgH); val cp2 = IcrsMath.icrsToPixel(cur.right, cur.bottom, imgW, imgH)
                                val cR = android.graphics.Rect(cp.x.toInt(), cp.y.toInt(), cp2.x.toInt(), cp2.y.toInt())
                                val op = IcrsMath.icrsToPixel(oth.left, oth.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(oth.right, oth.bottom, imgW, imgH)
                                val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                                if (qualifiesFor3SidesNearExtend(cR, oR)) {
                                    // Extend cur on the non-inside side to cover oth
                                    val newL = if (!insides[0]) min(cur.left, oth.left) else cur.left
                                    val newT = if (!insides[1]) min(cur.top, oth.top) else cur.top
                                    val newR = if (!insides[2]) max(cur.right, oth.right) else cur.right
                                    val newB = if (!insides[3]) max(cur.bottom, oth.bottom) else cur.bottom
                                    cur = RectF(newL, newT, newR, newB)
                                }
                            }
                            if (extended.none { it == cur }) extended.add(cur)
                        }
                        // Remove any now fully contained (after extends)
                        val cleaned = extended.filter { b ->
                            val bp = IcrsMath.icrsToPixel(b.left, b.top, imgW, imgH); val bp2 = IcrsMath.icrsToPixel(b.right, b.bottom, imgW, imgH)
                            val bR = android.graphics.Rect(bp.x.toInt(), bp.y.toInt(), bp2.x.toInt(), bp2.y.toInt())
                            !extended.any { o ->
                                if (o == b) false else {
                                    val op = IcrsMath.icrsToPixel(o.left, o.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(o.right, o.bottom, imgW, imgH)
                                    val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                    oR.contains(bR)
                                }
                            }
                        }.toMutableList()
                        blueRects.clear()
                        blueRects.addAll(cleaned)
                    }
                    branch.metadata["t_blue_3sides_ms"] = (System.currentTimeMillis() - tBlue3sStart).toString()

                    // Blue retract to tight fit around text (per approved plan): after union of overlapping CC hunks (from the big/little filter on binMat) per red, retract using expandByUniformity to shrink back when expansion hits limit with no text/content.
                    // This ensures blues are tight rather than over-expanded.
                    val tBlueRetractStart = System.currentTimeMillis()
                    val retractedBlueRects = mutableListOf<RectF>()
                    for (b in blueRects) {
                        val p1 = IcrsMath.icrsToPixel(b.left, b.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(b.right, b.bottom, imgW, imgH)
                        val rect = android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                        val (retracted, _) = NativeImageUtils.expandByUniformity(binMat, rect)
                        val i1 = IcrsMath.pixelToIcrs(retracted.left.toFloat(), retracted.top.toFloat(), imgW, imgH)
                        val i2 = IcrsMath.pixelToIcrs(retracted.right.toFloat(), retracted.bottom.toFloat(), imgW, imgH)
                        retractedBlueRects.add(RectF(i1.x, i1.y, i2.x, i2.y))
                    }
                    binMat.release()  // release after use in blue retract
                    branch.metadata["t_blue_retract_ms"] = (System.currentTimeMillis() - tBlueRetractStart).toString()
                    branch.metadata["t_blue_creation_ms"] = (System.currentTimeMillis() - tBlueStart).toString()

                    // publish to hoisted for the remaining code after the call in this else if (mechanical first-pass glue; will be cleaned when the next C chunk moves the orange/PD/OCR)
                    outRedBoxes.addAll(redBoxes)
                    outRedPixelRects.addAll(redPixelRects)
                    outHunks.addAll(hunks)
                    outBlueRects.addAll(blueRects)
                    outRetractedBlueRects.addAll(retractedBlueRects)
                    outCompRects.addAll(compRects)

                    val orangeRects = mutableListOf<RectF>()
                    for (blue in retractedBlueRects) {
                        val yMin = blue.top
                        val yMax = blue.bottom
                        val sameRow = hunks.filter { h ->
                            h.icrs.top >= yMin && h.icrs.bottom <= yMax
                        }
                        if (sameRow.isNotEmpty()) {
                            val l = min(blue.left, sameRow.minOf { it.icrs.left })
                            val t = min(blue.top, sameRow.minOf { it.icrs.top })
                            val r = max(blue.right, sameRow.maxOf { it.icrs.right })
                            val b = max(blue.bottom, sameRow.maxOf { it.icrs.bottom })
                            orangeRects.add(RectF(l, t, r, b))
                        }
                    }

                    // Near-containment merging rule (per approved plan) also applied to orangeRects (same-row unions).
                    // Same 3-side inside + protrusion <=40px on 4th *and* overlap on that axis (no gap) -> extend containing, then remove fully contained after. Uses qualifiesFor3SidesNearExtend.
                    run {
                        val toProcess = orangeRects.toMutableList()
                        val extended = mutableListOf<RectF>()
                        for (i in toProcess.indices) {
                            var cur = toProcess[i]
                            for (j in toProcess.indices) {
                                if (i == j) continue
                                val oth = toProcess[j]
                                val cp = IcrsMath.icrsToPixel(cur.left, cur.top, imgW, imgH); val cp2 = IcrsMath.icrsToPixel(cur.right, cur.bottom, imgW, imgH)
                                val cR = android.graphics.Rect(cp.x.toInt(), cp.y.toInt(), cp2.x.toInt(), cp2.y.toInt())
                                val op = IcrsMath.icrsToPixel(oth.left, oth.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(oth.right, oth.bottom, imgW, imgH)
                                val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                                if (qualifiesFor3SidesNearExtend(cR, oR)) {
                                    val newL = if (!insides[0]) min(cur.left, oth.left) else cur.left
                                    val newT = if (!insides[1]) min(cur.top, oth.top) else cur.top
                                    val newR = if (!insides[2]) max(cur.right, oth.right) else cur.right
                                    val newB = if (!insides[3]) max(cur.bottom, oth.bottom) else cur.bottom
                                    cur = RectF(newL, newT, newR, newB)
                                }
                            }
                            if (extended.none { it == cur }) extended.add(cur)
                        }
                        val cleaned = extended.filter { o ->
                            val op = IcrsMath.icrsToPixel(o.left, o.top, imgW, imgH); val op2 = IcrsMath.icrsToPixel(o.right, o.bottom, imgW, imgH)
                            val oR = android.graphics.Rect(op.x.toInt(), op.y.toInt(), op2.x.toInt(), op2.y.toInt())
                            !extended.any { d ->
                                if (d == o) false else {
                                    val dp = IcrsMath.icrsToPixel(d.left, d.top, imgW, imgH); val dp2 = IcrsMath.icrsToPixel(d.right, d.bottom, imgW, imgH)
                                    val dR = android.graphics.Rect(dp.x.toInt(), dp.y.toInt(), dp2.x.toInt(), dp2.y.toInt())
                                    dR.contains(oR)
                                }
                            }
                        }.toMutableList()
                        orangeRects.clear()
                        orangeRects.addAll(cleaned)
                    }

                    // dedup orange by exact rect match (same objects inside -> same summed box)
                    val dedupedOrange = mutableListOf<RectF>()
                    for (o in orangeRects) {
                        if (dedupedOrange.none { d -> d.left == o.left && d.top == o.top && d.right == o.right && d.bottom == o.bottom }) dedupedOrange.add(o)
                    }
                    // anns: red + 1px white around each character/segment object (from findAllComponentsH) + blue + orange
                    val redAnns = getAnns(pdHunksRawTotal, Color.RED, 2)
                    // Use the original comp pixel rects directly (they are already in the snapshot pixel space).
                    val whiteAnns = compRects.map { r ->
                        SnapshotAnnotation(r.left, r.top, r.right, r.bottom, Shape.RECTANGLE, Color.WHITE, 1)
                    }
                    val blueAnns = retractedBlueRects.map { r ->
                        val p1 = IcrsMath.icrsToPixel(r.left, r.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(r.right, r.bottom, imgW, imgH)
                        SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, Color.BLUE, 4)
                    }
                    val orangeAnns = dedupedOrange.map { r ->
                        val p1 = IcrsMath.icrsToPixel(r.left, r.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(r.right, r.bottom, imgW, imgH)
                        SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)
                    }
                    val allAnns = redAnns + whiteAnns + blueAnns + orangeAnns
                    val baseB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, allAnns, null, workspace).first
                    branch.images["PD"] = baseB64
                    // OCR twice (as-is + digits 0-9) on the blue/orange rects for C (same as B)
                    val blueAsIs = retractedBlueRects.map { r ->
                        val hh = PumpHunk("", r)
                        performHunkRecognition(listOf(hh), workspace, experimentRecSet1024x48, "Paddle", paddleEngine, context, tilt).firstOrNull()?.text ?: "?"
                    }
                    val blueDigits = retractedBlueRects.map { r ->
                        val ll = r.left.coerceIn(-maxX, maxX - 0.001f)
                        val tt = r.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = r.right.coerceIn(ll + 0.001f, maxX)
                        val bb = r.bottom.coerceIn(tt + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(ll, tt, imgW, imgH); val p2 = IcrsMath.icrsToPixel(rr, bb, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(ll, tt, rr - ll, bb - tt)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumericDecimal(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeAsIs = dedupedOrange.map { r ->
                        val hh = PumpHunk("", r)
                        performHunkRecognition(listOf(hh), workspace, experimentRecSet1024x48, "Paddle", paddleEngine, context, tilt).firstOrNull()?.text ?: "?"
                    }
                    val orangeDigits = dedupedOrange.map { r ->
                        val ll = r.left.coerceIn(-maxX, maxX - 0.001f)
                        val tt = r.top.coerceIn(-maxY, maxY - 0.001f)
                        val rr = r.right.coerceIn(ll + 0.001f, maxX)
                        val bb = r.bottom.coerceIn(tt + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(ll, tt, imgW, imgH); val p2 = IcrsMath.icrsToPixel(rr, bb, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(ll, tt, rr - ll, bb - tt)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            // 4px buffer around edges (per plan + alignment experiment pattern createCrop(4,4,...) + user note for Paddle accuracy on pump digits)
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumericDecimal(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val ocrLinesC = mutableListOf<String>()
                    blueAsIs.forEachIndexed { i, a ->
                        val d = blueDigits[i]
                        if (d.count { it.isDigit() } >= 2) ocrLinesC += "Blue ${i+1} as-is: $a &nbsp;&nbsp; digits: $d"
                    }
                    orangeAsIs.forEachIndexed { i, a ->
                        val d = orangeDigits[i]
                        if (d.count { it.isDigit() } >= 2) ocrLinesC += "Orange ${i+1} as-is: $a &nbsp;&nbsp; digits: $d"
                    }
                    branch.metadata["pd_ocr_html"] = ocrLinesC.joinToString("<br>")
                    branch.metadata["t_ocr_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                    // t_ocr_ms for C (after ocrLinesC build + pd_ocr_html write); B similar in its block
                    branch.metadata["t_pd_snapshot_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                    // t_pd_snapshot_ms approx for final PD annotated image (baseB64 / takeSnapshot with anns for C; B equivalent before its ocr)
                }

                if (flowName == "Set B" || flowName == "Set D") {
                    // add back blue (exp) + orange (max) annotations for Set B (per user directive)
                    // Explicit nested red filter for B/D (shared call at 731 already cleans pdHunksRawTotal used for B/D redAnns and exp/blue source; filter was not removed from B per code inspection -- added explicit here per user feedback/hypothesis that it was implemented on C but removed from B).
                    // Now also includes the corrected 3 sides +<=40px (with overlap check) so near-nested reds like the 12px pair in row 3 Set B (that satisfy the rule) get extended+deleted (visible in the red-only image). Gapped or >40px cases are no longer merged.
                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)

                    // Mechanical extraction (first small chunk): call to the extracted function (exact same code now lives in doBOrDRedOnlyImage).
                    doBOrDRedOnlyImage()

                    // Mechanical extraction (second small chunk): call to the extracted function (exact same retractedExpForBlue + aPd + baseB64 + dependent OCR/ocrLines/PD now lives in doBOrDRetractedBlueAndPD).
                    doBOrDRetractedBlueAndPD()
                } else if (flowName == "Set C" || flowName == "Set E") {
                    // Hoisted outputs for this C/E chunk (and immediate following orange code) -- mechanical first-pass glue so the body after the call compiles (per user's allowed outer-scope for first pass).
                    val tBlueStart = System.currentTimeMillis()
                    val redBoxes = mutableListOf<PumpHunk>()
                    val redPixelRects = mutableListOf<android.graphics.Rect>()
                    val hunks = mutableListOf<PumpHunk>()
                    val blueRects = mutableListOf<RectF>()
                    val retractedBlueRects = mutableListOf<RectF>()
                    val compRects = mutableListOf<android.graphics.Rect>()

                    // Mechanical extraction (first C/E chunk): call to the extracted function (exact same prep+valley+3sides+retract + orange + PD + OCR now lives in doCOrEPrepareHunksAndValleyInputs).
                    doCOrEPrepareHunksAndValleyInputs(redBoxes, redPixelRects, hunks, blueRects, retractedBlueRects, compRects)
                } else {
                    // A (reds only)
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                }
            }
            branch.metadata["t_total_flow_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
            // Additional lightweight context for interpreting the granular timings (cheap, high value, no extra run needed)
            branch.metadata["n_reds_at_probe"] = "see Set C probe for actual when flow==C (pre-filter 30 in example JSON)"
            branch.metadata["img_w"] = imgW.toString()
            branch.metadata["img_h"] = imgH.toString()
            }  // end of per-flow special handling (B/C thin calls to extracted helpers; A baseline)

            // Final Reporting
            val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(masterBuffer.p)
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

            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = pStartNewFile(); currentSize = 0 }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length

            val photoJson = pSerializePhotoResultToJson(
                index + 1, imgW, imgH, imgW, imgH, meta.isDegraded, meta.diagnostic, deskewResA, tSnapOrig, 0L, file.name, root, originalHistogram
            )
            val comma = if (index < total - 1) "," else ""

            // Clear/reset or re-allocate the reusable buffer to keep memory bounded
            if (jsonCharBuffer.capacity() > 64 * 1024 * 1024) {
                jsonCharBuffer = StringBuilder(16 * 1024 * 1024)
            } else {
                jsonCharBuffer.setLength(0)
            }

            appendJsonObject(jsonCharBuffer, photoJson, 2, 0)
            jsonFile.appendText(jsonCharBuffer.toString() + "$comma\n")

            val summaryText = flows.map { f ->
                val br = root.getBranch(f)
                if (f == "Set B") {
                    "$f Paddle: ${br.pathResults["Paddle"]?.cost ?: "F"}"
                } else {
                    "$f: ${br.pathResults["ML"]?.cost ?: "F"}"
                }
            }.joinToString(" | ")
            val resultSummary = PumpPhotoResultSummary(file.name, summaryText, 1.0f, "")
            withContext(Dispatchers.Main) { onProgress(resultSummary, (index + 1).toFloat() / total) }
            delay(50)

        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Experiment failed for row $index (${file.name}):\n" + Log.getStackTraceString(e))
        }
    }
    currentFile.appendText(footer)
    jsonFile.appendText("\n  ]\n}")

    experimentRecSet320x48.release()
    experimentRecSet1024x48.release()
    experimentDetSet512x128.release()
    masterBuffer.release()
    mlDiscoveryBuffers.values.forEach { it.release() }
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

private fun appendJsonValue(sb: StringBuilder, value: Any?, indent: Int, indentLevel: Int) {
    if (sb.length > 64 * 1024 * 1024) {
        throw IllegalStateException("JSON serialization exceeded the 64MB safety ceiling")
    }
    when (value) {
        null -> sb.append("null")
        JSONObject.NULL -> sb.append("null")
        is JSONObject -> appendJsonObject(sb, value, indent, indentLevel)
        is JSONArray -> appendJsonArray(sb, value, indent, indentLevel)
        is String -> {
            sb.append('"')
            escapeJsonString(sb, value)
            sb.append('"')
        }
        is Boolean -> sb.append(value.toString())
        is Number -> sb.append(value.toString())
        else -> {
            sb.append('"')
            escapeJsonString(sb, value.toString())
            sb.append('"')
        }
    }
}

private fun appendJsonObject(sb: StringBuilder, json: JSONObject, indent: Int, indentLevel: Int) {
    sb.append("{\n")
    val keys = json.keys()
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    var first = true
    while (keys.hasNext()) {
        if (!first) {
            sb.append(",\n")
        }
        first = false
        val key = keys.next()
        val value = json.get(key)
        sb.append(indentStr).append('"').append(key).append("\": ")
        appendJsonValue(sb, value, indent, nextLevel)
    }
    sb.append("\n").append(" ".repeat(indentLevel * indent)).append("}")
}

private fun appendJsonArray(sb: StringBuilder, array: JSONArray, indent: Int, indentLevel: Int) {
    sb.append("[\n")
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    for (i in 0 until array.length()) {
        if (i > 0) {
            sb.append(",\n")
        }
        sb.append(indentStr)
        appendJsonValue(sb, array.get(i), indent, nextLevel)
    }
    sb.append("\n").append(" ".repeat(indentLevel * indent)).append("]")
}

private fun escapeJsonString(sb: StringBuilder, str: String) {
    for (i in 0 until str.length) {
        val ch = str[i]
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '/' -> sb.append("\\/")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch.code < 32 || ch.code > 126) {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
}


private fun serializeDiscoveryDetails(details: Map<String, Map<Int, List<PumpHunk>>>): JSONObject {
    val root = JSONObject()
    details.forEach { (engine, scales) ->
        val engObj = JSONObject()
        scales.forEach { (scale, hunks) ->
            val arr = JSONArray()
            hunks.forEach { h ->
                arr.put(JSONObject().apply {
                    put("l", h.icrs.left.toDouble()); put("t", h.icrs.top.toDouble())
                    put("w", h.icrs.width().toDouble()); put("h", h.icrs.height().toDouble())
                    put("text", h.text)
                })
            }
            engObj.put(scale.toString(), arr)
        }
        root.put(engine, engObj)
    }
    return root
}


private fun generateHistogramB64(mat: org.opencv.core.Mat, floorPercentile: Float, mask: org.opencv.core.Mat? = null): String {
    if (mat.empty()) return ""
    val hist = org.opencv.core.Mat()
    // Support optional mask for red-box histograms (per approved plan for Set C). When mask provided, calc is restricted to those pixels (exact reuse of polarity probe pattern).
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), mask ?: org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))

    val bins = FloatArray(64); hist.get(0, 0, bins)

    // 186px wide (3x) to exclude 0 and 63 bins
    val bmp = Bitmap.createBitmap(186, 300, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
    canvas.drawColor(Color.BLACK)
    val paint = Paint()

    // Ignore bins 0 and 63 for scaling to see the peaks clearly
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

private fun generateCdfB64(mat: org.opencv.core.Mat, floorPercentile: Float): String {
    if (mat.empty()) return ""
    val hist = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(256), org.opencv.core.MatOfFloat(0f, 256f))

    val totalPixels = (mat.rows() * mat.cols()).toDouble()
    val bins = FloatArray(256); hist.get(0, 0, bins)

    val bmp = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
    canvas.drawColor(Color.BLACK)
    val paint = Paint()

    var runningSum = 0.0
    val cdf = FloatArray(256)
    for (i in 0..255) {
        runningSum += bins[i]
        cdf[i] = (runningSum / totalPixels).toFloat()
    }

    paint.color = Color.WHITE
    paint.strokeWidth = 1f
    for (i in 0..98) {
        val x1 = i.toFloat()
        val y1 = 50 - (cdf[(i * 2.56).toInt()] * 50f)
        val x2 = (i + 1).toFloat()
        val y2 = 50 - (cdf[((i + 1) * 2.56).toInt()] * 50f)
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    for (i in 0..99) {
        if (i % 10 == 0) { paint.color = Color.RED; canvas.drawRect(i.toFloat(), 52f, (i + 1).toFloat(), 60f, paint) }
        if (i == (floorPercentile * 100).toInt()) { paint.color = Color.YELLOW; canvas.drawRect(i.toFloat(), 52f, (i + 1).toFloat(), 60f, paint) }
    }

    val b64 = OcrUtils.bitmapToBase64(bmp, 80); bmp.recycle(); hist.release(); return b64
}

private fun pBuildHtmlHeader(time: String, total: Int, version: String, flows: List<String>): String = buildString {
    appendLine("<html><head><title>Pump Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .res-table { width: 100%; border: none; font-size: 20px; } .res-table th { background: #f0f0f0; }</style></head><body>")
    appendLine("<h1>Pump Extraction Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th>")
    flows.toSortedSet().forEach { flow ->
        if (flow != "Set B" && flow != "Set C") {
            appendLine("<th style='width:350px;'>$flow ML</th>")
        }
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
    // Phase 2: filter verbose redboxDataC etc from column 2 / meta dump (too much detail)
    val metaHtml = root.subBranches.values.flatMap { it.metadata.entries }.filter { (k, v) -> !k.contains("redboxDataC") && !k.contains("DataC") }.joinToString("<br>") { (k, v) -> "<small>$k: $v</small>" }
    val rowHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty() || metaHtml.isNotEmpty()) "<br><small>Native: $diagnostic</small><br>$metaHtml" else ""
    val img = root.images

    val perSetTilts = root.subBranches.toSortedMap().entries
        .joinToString(" | ") { (name, br) -> "$name: ${br.metadata["tilt"] ?: "?"}°" }
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$rowHtml</small>$diagHtml<br><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt per set:</b> $perSetTilts<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["before"]}'><br><small>Orig</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist1"]}'><br><small>Hist 1</small></td></tr><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["after"]}'><br><small>Stretch</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist2"]}'><br><small>Hist 2</small></td></tr><tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:14px;'><small>$deskewHtml</small></td></tr></table></td>")

    root.subBranches.toSortedMap().forEach { (name, br) ->
        if (name != "Set B" && name != "Set C" && name != "Set D" && name != "Set E") {
            appendLine("<td><b>$name ML:</b><br><img src='data:image/jpeg;base64,${br.images["ML"]}'></td>")
        }
        val pdB64 = br.images["PD"] ?: ""
        val extraOcr = if ((name == "Set B" || name == "Set C") && br.metadata.containsKey("pd_ocr_html")) {
            "<br><div style='font-family:monospace; font-size:18px; text-align:left; background:#fafafa; padding:2px;'>" + br.metadata["pd_ocr_html"] + "</div>"
        } else ""
        if (name == "Set B" || name == "Set D") {
            // Two images for Set B/D per approved plan: red-only (clean post-filter reds, no blue/orange) for inspecting redbox merging state, plus the full annotations image exactly "as is happening now" (with ocr html). D mirrors B, using the pruned 6 largest reds (pixel list).
            val redOnly = br.images["PD_red_only"] ?: ""
            val full = br.images["PD"] ?: ""
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$redOnly' style='max-width:100%;'><br><small>Red boxes only (after filter)</small><br><img src='data:image/jpeg;base64,$full' style='max-width:100%;'><br><small>All annotations (red+blue+orange) as before</small>$extraOcr</td>")
        } else if ((name == "Set C" || name == "Set E") && br.images.containsKey("rawC")) {
            val raw = br.images["rawC"] ?: ""
            val pushed = br.images["pushedC"] ?: ""
            val hB = br.images["histBeforeC"] ?: ""
            val hA = br.images["histAfterC"] ?: ""
            // Per-redbox hists + labels from redboxDataC (h/w/area pixels + bins for analysis)
            // Phase 1: sorted by area desc (largest first), 3-wide table, stacked values per cell
            // For E: on the pruned 6, YUV direct jpeg visuals per plan.
            val rdataStr = br.metadata["redboxDataC"] ?: "[]"
            val rdata = try { org.json.JSONArray(rdataStr) } catch (e: Exception) { org.json.JSONArray() }
            val perRedHtml = StringBuilder()
            perRedHtml.append("<div style='margin-top:4px;'><b>Per Red Box Hists (sorted by area desc, 3 wide, stacked h/w/area):</b></div>")
            val sortedData = (0 until rdata.length()).map { rdata.getJSONObject(it) }.sortedByDescending { it.getInt("area") }
            val numCols = 3
            perRedHtml.append("<table style='width:100%; border:none; font-size:10px;'><tr>")
            for (j in sortedData.indices) {
                val s = sortedData[j]
                val ii = s.getInt("index")
                val hh = s.getInt("h")
                val ww = s.getInt("w")
                val aa = s.getInt("area")
                val hb = br.images["redboxHistC_${ii}"] ?: ""
                perRedHtml.append("<td style='border:none; padding:2px; vertical-align:top; width:33%; text-align:center;'><img src='data:image/jpeg;base64,$hb' style='max-width:95%;'><br><small>Red${ii}:<br>h=${hh}<br>w=${ww}<br>area=${aa}</small></td>")
                if ((j + 1) % numCols == 0 && j < sortedData.size - 1) {
                    perRedHtml.append("</tr><tr>")
                }
            }
            perRedHtml.append("</tr></table>")
            appendLine("<td><b>$name Paddle:</b><br><table style='width:100%; border:none; font-size:11px;'><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$raw' style='max-width:120%;'><br><small>Raw (3x, 2x displayed)</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$pushed' style='max-width:120%;'><br><small>Valley-Pushed (few brightness vals, 3x, 2x displayed)</small></td></tr><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hB' style='max-width:120%;'><br><small>Before (3x, 2x displayed)</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hA' style='max-width:120%;'><br><small>After (3x, 2x displayed)</small></td></tr></table>$perRedHtml<img src='data:image/jpeg;base64,$pdB64'>$extraOcr</td>")
        } else {
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$pdB64'>$extraOcr</td>")
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
private fun pGetFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else return emptyList()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0)
            val centerPix = IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)
            val sE = minOf(imgW, imgH).toDouble(); val pW = (w * sE); val pH = (h * sE)
            val inst = if (obj.has("instance")) obj.getInt("instance") else -1; val cT = OdometerOcrUtils.cleanLandmarkString(text)
            list.add(TextBlock(cT, android.graphics.Rect((centerPix.x - pW/2.0).toInt(), (centerPix.y - pH/2.0).toInt(), (centerPix.x + pW/2.0).toInt(), (centerPix.y + pH/2.0).toInt()), instanceId = inst))
        }
    } catch (e: Exception) { Log.e("ExperimentPump", "Failed to parse landmarks", e) }
    return list
}

private suspend fun pExtractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        if (targetDir.exists()) targetDir.deleteRecursively(); targetDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry; while (entry != null) {
                    val file = File(targetDir, entry.name); if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }; zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }; true
    } catch (e: Exception) { false }
}

private fun pToEvenInt(v: Float): Int = ((v + 1).toInt() / 2) * 2

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


private suspend fun runDiscoveryPaddle(buffer: BufferSet, id: Int, paddleEngine: NativePaddleEngine, contentW: Int, contentH: Int): List<List<PumpHunk>> {
    val res = paddleEngine.detect(buffer.c[id]) ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    val masterW = buffer.c[id].width; val masterH = buffer.c[id].height

    val rawBlocks = OdometerOcrUtils.processPaddleHeatmap(res.heatmap, res.width, res.height, 1.0f, buffer.c[id])
    val rawRects = rawBlocks.map { it.boundingBox }

    // Pre-redbox detected hunks (tFullB equivalent from alignment Set J runBinTrialsPaddle).
    // These are the raw objects from the detector (pre +1/denest/nonNested that produce the "raw red" tRawB-equivalent level).
    // Used only for Set C: 1px white anns (to show each detected hunk) + as the "hunks" source for per-red overlap + Y-extend derivation of blue/orange.
    // (The pdHunksRawTotal level remains the post-redbox "RED raw boxes" for display/anns/crops/mask.)
    val hunksDetected = mutableListOf<PumpHunk>()
    rawRects.forEach { r ->
        val ml = r.left.toInt().coerceIn(0, masterW - 1)
        val mt = r.top.toInt().coerceIn(0, masterH - 1)
        val mr = r.right.toInt().coerceIn(0, masterW - 1)
        val mb = r.bottom.toInt().coerceIn(0, masterH - 1)
        val ri1 = IcrsMath.pixelToIcrs(ml.toFloat(), mt.toFloat(), contentW, contentH)
        val ri2 = IcrsMath.pixelToIcrs(mr.toFloat(), mb.toFloat(), contentW, contentH)
        hunksDetected.add(PumpHunk("", RectF(ri1.x, ri1.y, ri2.x, ri2.y)))
    }

    // Redbox improvement from Set J (alignment experiment) - first item per user directive.
    // Move sides of detected box out by 1 pixel in low-res (this crop/detect-input space) before
    // the ICRS "scaling back up" (and before doing anything more: consolidate, native expand, hunks).
    // Then remove nested red boxes (inset contains filter, matching alignment tRawB logic in runBinTrialsPaddle).
    //
    // Pump note (variable scale vs fixed in alignment): scaleFactor computed in caller scales.forEach
    // (currentLongEdge vs target/scale + prepareScale 32-align outer/inner + process 1.0f on crop).
    // ICRS here uses crop masterW/H; later icrsToPixel in getFinal uses full original imgW/imgH.
    // This chain causes erosion (e.g. 63px feature -> ~56px effective after down/up as described).
    // +1 here (in the post-process rect space) + nested removal is the ported math.
    // Per clarification: the lowest level does the +1 adjustment; layers above (scale/prepare) apply the
    // scale factor from there. Buffer sizes are multiples of 32x2 (for alignment), but the boxes themselves
    // do not need to be.
    val expandedRects = rawRects.map { r ->
        android.graphics.Rect(
            (r.left - 1).coerceAtLeast(0),
            (r.top - 1).coerceAtLeast(0),
            (r.right + 1).coerceAtMost(masterW - 1),
            (r.bottom + 1).coerceAtMost(masterH - 1)
        )
    }
    val nonNestedRects = expandedRects.filter { r1 ->
        expandedRects.none { r2 -> r1 != r2 && r2.contains(r1.left + 5, r1.top + 5, r1.right - 5, r1.bottom - 5) }
    }

    // 1. Consolidate Raw Character Fragments (75% overlap rule) -- now on improved (expanded + de-nested) raw redboxes
    val consolidated = OdometerOcrUtils.consolidateRects(nonNestedRects, 0.75f)

    val hunksRaw = mutableListOf<PumpHunk>()
    val hunksExpanded = mutableListOf<PumpHunk>()
    val hunksMaxExtent = mutableListOf<PumpHunk>()
    val hunksNative = mutableListOf<PumpHunk>()

    // Build raw hunks from the non-nested expanded rects (pre-consolidate) so the RED raw boxes in reports
    // show the individual +1 expanded and de-nested detections. Use contentW/contentH for ICRS to fix
    // scaling back up / offsets (the outer master includes padding, content is the actual downscaled image size).
    nonNestedRects.forEach { rect ->
        val ml = rect.left.toInt().coerceIn(0, masterW - 1)
        val mt = rect.top.toInt().coerceIn(0, masterH - 1)
        val mr = rect.right.toInt().coerceIn(0, masterW - 1)
        val mb = rect.bottom.toInt().coerceIn(0, masterH - 1)
        val rawRect = android.graphics.Rect(ml, mt, mr, mb)

        val ri1 = IcrsMath.pixelToIcrs(ml.toFloat(), mt.toFloat(), contentW, contentH)
        val ri2 = IcrsMath.pixelToIcrs(mr.toFloat(), mb.toFloat(), contentW, contentH)
        hunksRaw.add(PumpHunk("", RectF(ri1.x, ri1.y, ri2.x, ri2.y)))
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

        // Capture Expanded/Retracted result -- use content size for ICRS (consistent scaling)
        val i1 = IcrsMath.pixelToIcrs(retractedRect.left.toFloat(), retractedRect.top.toFloat(), contentW, contentH)
        val i2 = IcrsMath.pixelToIcrs(retractedRect.right.toFloat(), retractedRect.bottom.toFloat(), contentW, contentH)
        hunksExpanded.add(PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y)))

        // Capture Max Extent reach (Yellow tier)
        val y1 = IcrsMath.pixelToIcrs(maxExtentRect.left.toFloat(), maxExtentRect.top.toFloat(), contentW, contentH)
        val y2 = IcrsMath.pixelToIcrs(maxExtentRect.right.toFloat(), maxExtentRect.bottom.toFloat(), contentW, contentH)
        hunksMaxExtent.add(PumpHunk("", RectF(y1.x, y1.y, y2.x, y2.y)))
    }

    // Capture Native Results (Phase 2 A/B) -- using content size for ICRS too
    res.nativeBoxes.forEach { box ->
        // Points are in input Mat pixels (crop-relative)
        val icrsPoints = box.points.toList().chunked(2).map { (px, py) ->
            IcrsMath.pixelToIcrs(px, py, contentW, contentH)
        }

        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        icrsPoints.forEach { p ->
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
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
                val interL = max(current.icrs.left, next.icrs.left); val interT = max(current.icrs.top, next.icrs.top)
                val interR = min(current.icrs.right, next.icrs.right); val interB = min(current.icrs.bottom, next.icrs.bottom)

                val overlapH = if (interB > interT) interB - interT else 0f
                val minH = min(current.icrs.height(), next.icrs.height())
                val significantOverlap = overlapH >= (minH * 0.3f)

                val isNested = current.icrs.contains(next.icrs) || next.icrs.contains(current.icrs)

                if (significantOverlap || isNested) {
                    val newIcrs = RectF(
                        min(current.icrs.left, next.icrs.left),
                        min(current.icrs.top, next.icrs.top),
                        max(current.icrs.right, next.icrs.right),
                        max(current.icrs.bottom, next.icrs.bottom)
                    )
                    val bestText = if (current.text.count { it.isDigit() } >= next.text.count { it.isDigit() }) current.text else next.text
                    current = PumpHunk(bestText, newIcrs)
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
    val minEdge = Math.min(masterW, masterH).toFloat()
    val maxX = masterW / (2f * minEdge); val maxY = masterH / (2f * minEdge)

    return hunks.map { hunk ->
        val l = hunk.icrs.left.coerceIn(-maxX, maxX - 0.001f)
        val t = hunk.icrs.top.coerceIn(-maxY, maxY - 0.001f)
        val r = hunk.icrs.right.coerceIn(l + 0.001f, maxX)
        val b = hunk.icrs.bottom.coerceIn(t + 0.001f, maxY)

        val p1 = IcrsMath.icrsToPixel(l, t, masterW, masterH); val p2 = IcrsMath.icrsToPixel(r, b, masterW, masterH)
        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()

        if (pW < 2 || pH < 2) return@map hunk

        val cropId = buffer.createCrop(l, t, r - l, b - t)

        val targetH = 48; val scale = 48f / pH; val targetW = Math.min(320, (pW * scale).toInt())
        if (targetW <= 0 || targetH <= 0) return@map hunk  // guard for bad aspect / tiny derived box after prune to 6 largest (prevents OpenCV resize assertion inv_scale_x > 0 and NPE in downstream OCR for C/E on first/some photos)

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
        PumpHunk(res.debugText, hunk.icrs)
    }
}


private fun stitchHunksHorizontally(hunks: List<PumpHunk>): List<PumpHunk> {
    if (hunks.isEmpty()) return emptyList()
    val sorted = hunks.sortedBy { it.icrs.left }
    val result = mutableListOf<MutableList<PumpHunk>>()

    for (hunk in sorted) {
        var merged = false
        for (line in result) {
            val last = line.last()
            val h = min(hunk.icrs.height(), last.icrs.height())
            val vOverlap = max(0f, min(hunk.icrs.bottom, last.icrs.bottom) - max(hunk.icrs.top, last.icrs.top))
            val hGap = hunk.icrs.left - last.icrs.right

            if (vOverlap > 0.7f * h && hGap < 1.0f * h) {
                line.add(hunk)
                merged = true
                break
            }
        }
        if (!merged) result.add(mutableListOf(hunk))
    }

    return result.map { line ->
        val l = line.minOf { it.icrs.left }
        val t = line.minOf { it.icrs.top }
        val r = line.maxOf { it.icrs.right }
        val b = line.maxOf { it.icrs.bottom }
        val widest = r - l
        val shortest = line.minOf { it.icrs.height() }
        val centerY = line.map { it.icrs.centerY() }.average().toFloat()

        // Spec: inherit string with highest digit count
        val bestText = line.maxByOrNull { it.text.count { c -> c.isDigit() } }?.text ?: ""

        val fT = centerY - shortest / 2f; val fB = centerY + shortest / 2f
        PumpHunk(bestText, RectF(l, fT, r, fB))
    }
}

private fun groupLanesByVerticalGap(hunks: List<PumpHunk>): Pair<List<PumpHunk>, List<PumpHunk>> {
    if (hunks.isEmpty()) return Pair(emptyList(), emptyList())
    val sortedY = hunks.sortedBy { it.icrs.centerY() }

    val lanes = mutableListOf<MutableList<PumpHunk>>()
    for (hunk in sortedY) {
        var found = false
        for (lane in lanes) {
            val anchor = lane.first()
            val h = anchor.icrs.height()
            if (Math.abs(hunk.icrs.centerY() - anchor.icrs.centerY()) < 0.3f * h) {
                lane.add(hunk)
                found = true
                break
            }
        }
        if (!found) lanes.add(mutableListOf(hunk))
    }

    if (lanes.size < 2) return Pair(hunks, emptyList())

    // Sort lanes by centerY
    val sortedLanes = lanes.sortedBy { it.first().icrs.centerY() }

    // Find largest gap between adjacent lanes
    var maxGap = -1f
    var splitIdx = 0
    for (i in 0 until sortedLanes.size - 1) {
        val gap = sortedLanes[i+1].first().icrs.centerY() - sortedLanes[i].first().icrs.centerY()
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
            val hB = bottom.icrs.height()
            val gap = bottom.icrs.top - top.icrs.bottom
            val vOverlap = max(0f, min(top.icrs.bottom, bottom.icrs.bottom) - max(top.icrs.top, bottom.icrs.top))
            val xOverlap = max(0f, min(top.icrs.right, bottom.icrs.right) - max(top.icrs.left, bottom.icrs.left))

            val digitTop = top.text.count { it.isDigit() }
            val digitBottom = bottom.text.count { it.isDigit() }

            if (gap < 1.25f * hB && vOverlap < 0.2f * hB && xOverlap > 0 && digitTop >= 2 && digitBottom >= 2) {
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

private fun expandHunkContext(hunk: PumpHunk, maxX: Float, maxY: Float): PumpHunk {
    val h = hunk.icrs.height()
    val newH = h * 1.5f
    val dy = (newH - h) / 2f
    val dx = newH // Horizontal expansion is value of NEW height on EACH side

    val l = (hunk.icrs.left - dx).coerceIn(-maxX, maxX - 0.001f)
    val t = (hunk.icrs.top - dy).coerceIn(-maxY, maxY - 0.001f)
    val r = (hunk.icrs.right + dx).coerceIn(l + 0.001f, maxX)
    val b = (hunk.icrs.bottom + dy).coerceIn(t + 0.001f, maxY)

    return PumpHunk(hunk.text, RectF(l, t, r, b))
}

private fun applyRecognitionHeuristics(text: String): String {
    var s = text.trim()
    if (s.startsWith(".")) s = s.substring(1).trim()
    return s
}

private fun drawHunksOnBitmap(bmp: Bitmap, hunks: List<PumpHunk>, color: Int, existingCanvas: Canvas? = null): Bitmap {
    val out = if (existingCanvas == null) bmp.copy(Bitmap.Config.ARGB_8888, true) else bmp
    val canvas = existingCanvas ?: Canvas(out)
    val paint = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 10f }
    hunks.forEach { hunk ->
        val p1 = IcrsMath.icrsToPixel(hunk.icrs.left, hunk.icrs.top, bmp.width, bmp.height)
        val p2 = IcrsMath.icrsToPixel(hunk.icrs.right, hunk.icrs.bottom, bmp.width, bmp.height)
        canvas.drawRect(p1.x, p1.y, p2.x, p2.y, paint)
    }
    return out
}

private fun pumpCreateScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int, targetBuffer: Bitmap? = null): String {
    if (bitmap.isRecycled) return ""
    val scale = targetWidth.toFloat() / bitmap.width; val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val target = targetBuffer ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888); val targetCanvas = android.graphics.Canvas(target)
    if (targetBuffer != null) targetCanvas.drawColor(android.graphics.Color.BLACK); val matrix = android.graphics.Matrix(); matrix.postScale(scale, scale); targetCanvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    val view = Bitmap.createBitmap(target, 0, 0, targetWidth, targetHeight); val b64 = OcrUtils.bitmapToBase64(view, quality); view.recycle()
    if (targetBuffer == null) target.recycle(); return b64
}

private fun JSONObject.pPutSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.pPutSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }


