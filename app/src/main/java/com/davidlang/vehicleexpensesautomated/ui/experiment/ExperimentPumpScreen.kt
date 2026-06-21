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

data class PumpHunk(val text: String, val rect: RectF) // pixel coordinates in full workspace/photo space for this image

private data class PumpRectOcrLists(
    val asis: List<String>,
    val digits: List<String>,
    val asisProbs: List<String> = emptyList(),
    val digitsProbs: List<String> = emptyList()
)

private data class RedBoxOcrCandidate(
    val label: String,
    val asis: String,
    val digits: String,
    val asisProbs: String = "",
    val digitsProbs: String = "",
    val rect: android.graphics.Rect? = null
)
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

    if (!reportDir.exists()) reportDir.mkdirs()

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
                runPumpExperiment(experimentDir, reportDir, context, { detailLog = it }, null) { res, p ->
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
                runPumpExperiment(experimentDir, reportDir, context, { detailLog = it }, GOLDEN_SUBSET) { res, p ->
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

    // Pre-allocated JSON serialization buffer (256MB upfront for many P4 base64 strings per photo)
    var jsonCharBuffer = StringBuilder(PUMP_JSON_BUFFER_INITIAL_BYTES)

    var partCount = 1
    val maxSizeBytes = 50 * 1024 * 1024 // 50MB HTML parts (JPEG previews only; P4 base64 volume lives in 256M JSON buffer)
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

    // Long-lived histogram BufferSet for per-red C/E report (dual visuals: rect snapshot from workspace crop + hist plot snapshot); initialized once before photo loop, sized for per-red display (plot 186x300), held for experiment lifetime, passed as scratchYuv to takeSnapshot, never released here. Internal histPlotCrop for render from bins (replaces per-red custom generate + temp workspace plot). (C/E visuals only; redboxData hists now for all 7 sets via captureRedboxData after top-4 prune.)
    val longLivedHistogramBuffer = BufferSet(186, 300)
    longLivedHistogramBuffer.p.clear()
    longLivedHistogramBuffer.s.clear()
    val histPlotCrop = longLivedHistogramBuffer.createCrop(0, 0, 186, 300)

    // Define flows for N-sets support
    // Configure experiment flows here. (See: docs/PUMP_EXPERIMENT_FLOWS.md for instructions)
    // Set A: dual ML+Paddle (baseline). Set B: pump-only (Paddle recognition only, no MLKit in rec step) + improved redbox + Set E-style deskew.
    // Set C: pump-only (copy of Set B) but uses valley-center push (replaces current histogram contrast stretch per plan); produces single image with small number of brightness values (not binarization). Raw + pushed + before/after hists (display-matched 1:1 size) displayed in the Set C column (plus PD/ocr with boxes for context). Per-redbox histograms sorted by area, 3-wide with stacked labels (from prior + this plan; now via createCrop + direct calcHist + dual takeSnapshot from long-lived hist BufferSet at pump start). Lot of granular t_ timings (20+ including t_setup_ms, t_deskew_ms, t_discovery_wrapper_ms, t_filter_ms, t_pd_snapshot_ms, t_ocr_ms + C probe subs t_polarity_run_ms / t_per_red_bins_calc_ms / t_per_red_loop_overhead_ms / t_polarity_decision_ms / t_invert_if_needed_ms + blue subs t_blue_native_hist_ms / t_blue_valley_expands_ms / t_blue_3sides_ms / t_blue_retract_ms + t_hist_* + kept priors + n_reds_at_probe / n_per_red_hists / img dims context) added to metadata/JSON (one run gathers all for A/B gap + C probe/blue decomposition; no extra turn needed). HISTOGRAM ANSWERS (forensic): per-red C/E now createCrop (pixel rect from hunk) + direct calcHist on crop.mat (no mask) + OcrUtils.takeSnapshot (dual rect snapshot + plot from longLived histPlotCrop); long-lived BufferSet init once at pump start for scratch + plot crop (no per-red full Mat alloc/zeros/draw/generate custom); old perMask/rectangle/generate retired for this path. Blue from red now via alignment Set E valley expansion (adapted) instead of CC overlapping + early expandByUniformity (to fix errors). Polarity + discovery + CC hunks (for orange) run on the pushed mat state. Set F: raw clone of B (no automaticContrastStretch; deskew/rotate/discovery/retracted-blue/red-only/OCR/PD on raw master copy). Set G: raw clone of D (no stretch; keeps custom 10% blue/orange + red-only + OCR on raw).
    // Label convention (added this plan): Set A exactly unchanged (baseline). B-G use "Set X (stretch-type, blue-method)" so columns are self-describing in all report output (th, tilt line, <b>$name ...); stretch=clip edges (automaticContrastStretch), valley push (valleyPushToPeaks), none (raw F/G clones); blue=expanded (doBOrDRetractedBlueAndPD + expandByUniformity+retract: B/C/F) or calculated (vert expansions +10%..+80% step 10%: D/E/G). Exact: B (clip edges, expanded), C (valley push, expanded), D (clip edges, calculated), E (valley push, calculated), F (none, expanded), G (none, calculated). B/C/F (expanded columns only) also emit per-peak binarized debug JPEGs (nativeBinarizeRange into workspace.s/b, takeSnapshot on b.mat) in HTML/JSON as binPeak_*; peaks from combinedRedboxHistBins; for C (valley expanded) uses exact 1-bin peaks from quantized combined hist (no range/smoothing, d=0); calculated columns D/E/G skip binPeak work entirely (results identical to expanded, saves processing + report size).
    val flows = listOf("Set A", "Set B (clip edges, expanded)", "Set C (valley push, expanded)", "Set D (clip edges, calculated)", "Set E (valley push, calculated)", "Set F (none, expanded)", "Set G (none, calculated)")

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
                        val r1 = android.graphics.Rect(h1.rect.left.toInt(), h1.rect.top.toInt(), h1.rect.right.toInt(), h1.rect.bottom.toInt())
                        val isContained = kept.any { h2 ->
                            h1 !== h2 && run {
                                val r2 = android.graphics.Rect(h2.rect.left.toInt(), h2.rect.top.toInt(), h2.rect.right.toInt(), h2.rect.bottom.toInt())
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
                            val cR = android.graphics.Rect(cur.rect.left.toInt(), cur.rect.top.toInt(), cur.rect.right.toInt(), cur.rect.bottom.toInt())
                            val oR = android.graphics.Rect(oth.rect.left.toInt(), oth.rect.top.toInt(), oth.rect.right.toInt(), oth.rect.bottom.toInt())
                            val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                            if (qualifiesFor3SidesNearExtend(cR, oR)) {
                                val newL = if (!insides[0]) min(cur.rect.left, oth.rect.left) else cur.rect.left
                                val newT = if (!insides[1]) min(cur.rect.top, oth.rect.top) else cur.rect.top
                                val newR = if (!insides[2]) max(cur.rect.right, oth.rect.right) else cur.rect.right
                                val newB = if (!insides[3]) max(cur.rect.bottom, oth.rect.bottom) else cur.rect.bottom
                                cur = PumpHunk(cur.text, RectF(newL, newT, newR, newB))
                            }
                        }
                        if (extended.none { it.rect == cur.rect }) extended.add(cur)
                    }
                    val cleaned = extended.filter { b ->
                        val bR = android.graphics.Rect(b.rect.left.toInt(), b.rect.top.toInt(), b.rect.right.toInt(), b.rect.bottom.toInt())
                        !extended.any { o ->
                            if (o === b) false else {
                                val oR = android.graphics.Rect(o.rect.left.toInt(), o.rect.top.toInt(), o.rect.right.toInt(), o.rect.bottom.toInt())
                                oR.contains(bR)
                            }
                        }
                    }.toMutableList()
                    pdHunksRawTotal.clear()
                    pdHunksRawTotal.addAll(cleaned)
                }
            }


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
                    digitsProbsList: List<String> = emptyList()
                ): List<RedBoxOcrCandidate> {
                    val n = minOf(boxRects.size, asisList.size, digitsList.size)
                    return (0 until n).map { i ->
                        RedBoxOcrCandidate(
                            "Red${i+1}",
                            asisList[i],
                            digitsList[i],
                            asisProbsList.getOrElse(i) { "" },
                            digitsProbsList.getOrElse(i) { "" },
                            boxRects[i]
                        )
                    }
                }

                
                data class CostVolClassifyResult(val cost: String, val vol: String, val costCand: RedBoxOcrCandidate, val volCand: RedBoxOcrCandidate)

                // fix-classifier-numeric-only-values-asis-golden-yband-20260619-plan + fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: digits-only; overlap clustering; probs for correctness; role-conditional repair
                fun classifyCostVolFromBoxOcr(candidates: List<RedBoxOcrCandidate>): CostVolClassifyResult {
                    val na = CostVolClassifyResult("N/A", "N/A", RedBoxOcrCandidate("", "", ""), RedBoxOcrCandidate("", "", ""))
                    if (candidates.isEmpty()) return na
                    fun digitCount(s: String) = s.count { it.isDigit() }
                    fun parse(s: String): Pair<Float, Int> {
                        val d = s.filter { it.isDigit() || it == '.' }
                        val f = d.toFloatOrNull() ?: 0f
                        val dp = if ("." in d) d.substringAfter(".").length else 0
                        return f to dp
                    }
                    data class Enriched(
                        val cand: RedBoxOcrCandidate,
                        val cleanDigits: String,
                        val value: Float,
                        val dp: Int,
                        val probScore: Float,
                        val costScore: Int,
                        val volScore: Int
                    )
                    fun correctnessScore(e: Enriched): Int {
                        var s = (e.probScore * 100).toInt()
                        if ("." in e.cleanDigits) s += 50
                        return s
                    }
                    fun clusterOf(pool: List<Enriched>, seed: Enriched): List<Enriched> {
                        val pref = seed.cand.rect ?: return listOf(seed)
                        return pool.filter { e ->
                            e.cand.rect?.let { significantYOverlap(pref, it) } == true
                        }.ifEmpty { listOf(seed) }
                    }
                    fun pickClusterBest(pool: List<Enriched>, seed: Enriched): Enriched =
                        clusterOf(pool, seed).maxByOrNull { correctnessScore(it) + maxOf(it.costScore, it.volScore) } ?: seed
                    val goldenYs = candidates.filter { c ->
                        val a = c.asis.lowercase()
                        a.contains("$") || a.contains("/gal") || a.contains("gal")
                    }.mapNotNull { it.rect?.top }
                    val enriched = candidates.mapNotNull { c ->
                        if (hasBadInternalDecimals(c.digits)) return@mapNotNull null
                        val cleanDigits = cleanDecimal(c.digits)
                        if (digitCount(cleanDigits) < 2) return@mapNotNull null
                        val (v, dp) = parse(cleanDigits)
                        var cs = 0; var vs = 0
                        if (dp == 2) cs += 12
                        if (dp == 3) vs += 12
                        if (v > 20) cs += 8
                        if (v < 60 && v > 0) vs += 6
                        if (v in 3.0..30.0) vs += 1
                        if (dp > 0) { cs += 2; vs += 2 }
                        if ("." in cleanDigits) { cs += 5; vs += 5 }
                        if (goldenYs.isNotEmpty() && c.rect != null) {
                            val minDist = goldenYs.minOf { kotlin.math.abs(it - c.rect.top) }
                            cs += 20 - minOf(minDist / 10, 20)
                        }
                        val prob = probCorrectness(c.digitsProbs)
                        cs += (prob * 20).toInt()
                        vs += (prob * 20).toInt()
                        Enriched(c, cleanDigits, v, dp, prob, cs, vs)
                    }
                    if (enriched.isEmpty()) return na
                    if (enriched.none { it.cand.rect != null }) {
                        val costE = enriched.maxByOrNull { it.costScore }!!
                        val volE = enriched.filter { it.cand != costE.cand }.maxByOrNull { it.volScore }
                            ?: enriched.maxByOrNull { it.volScore }!!
                        var cstFb = repairDecimalForRole(costE.cleanDigits, "cost")
                        var vlmFb = repairDecimalForRole(volE.cleanDigits, "vol")
                        if (cstFb == vlmFb && enriched.size >= 2) vlmFb = "N/A"
                        if (digitCount(cstFb) < 2) cstFb = "N/A"
                        if (digitCount(vlmFb) < 2) vlmFb = "N/A"
                        return CostVolClassifyResult(cstFb, vlmFb, costE.cand, volE.cand)
                    }
                    val seed1 = enriched.maxByOrNull { maxOf(it.costScore, it.volScore) }!!
                    val firstBest = pickClusterBest(enriched, seed1)
                    val firstCluster = clusterOf(enriched, firstBest)
                    val firstClusterIds = firstCluster.map { it.cand }.toSet()
                    val costLikelihood = firstCluster.sumOf { it.costScore.toDouble() }.toFloat() + firstBest.probScore * 30f
                    val volLikelihood = firstCluster.sumOf { it.volScore.toDouble() }.toFloat() + firstBest.probScore * 30f
                    val firstIsCost = costLikelihood >= volLikelihood
                    val prefRect1 = firstBest.cand.rect
                    val secondPool = enriched.filter { e ->
                        e.cand !in firstClusterIds && (prefRect1 == null || e.cand.rect == null || !significantYOverlap(prefRect1, e.cand.rect))
                    }
                    var costCand: RedBoxOcrCandidate
                    var volCand: RedBoxOcrCandidate
                    var cst: String
                    var vlm: String
                    if (firstIsCost) {
                        costCand = firstBest.cand
                        cst = firstBest.cleanDigits
                        if (secondPool.isNotEmpty()) {
                            val seed2 = secondPool.maxByOrNull { maxOf(it.costScore, it.volScore) }!!
                            val secondBest = pickClusterBest(secondPool, seed2)
                            volCand = secondBest.cand
                            vlm = secondBest.cleanDigits
                        } else {
                            val alt = enriched.filter { it.cand != firstBest.cand }.maxByOrNull { it.volScore }
                            if (alt != null) { volCand = alt.cand; vlm = alt.cleanDigits }
                            else { volCand = firstBest.cand; vlm = "N/A" }
                        }
                    } else {
                        volCand = firstBest.cand
                        vlm = firstBest.cleanDigits
                        if (secondPool.isNotEmpty()) {
                            val seed2 = secondPool.maxByOrNull { maxOf(it.costScore, it.volScore) }!!
                            val secondBest = pickClusterBest(secondPool, seed2)
                            costCand = secondBest.cand
                            cst = secondBest.cleanDigits
                        } else {
                            val alt = enriched.filter { it.cand != firstBest.cand }.maxByOrNull { it.costScore }
                            if (alt != null) { costCand = alt.cand; cst = alt.cleanDigits }
                            else { costCand = firstBest.cand; cst = "N/A" }
                        }
                    }
                    cst = repairDecimalForRole(cst, "cost")
                    vlm = repairDecimalForRole(vlm, "vol")
                    if (cst == vlm && enriched.size >= 2) {
                        if (firstIsCost) {
                            val alt = secondPool.filter { it.cand != volCand }.maxByOrNull { it.volScore }
                                ?: enriched.filter { it.cand != costCand && it.cand != volCand }.maxByOrNull { it.volScore }
                            if (alt != null) {
                                volCand = alt.cand
                                vlm = repairDecimalForRole(alt.cleanDigits, "vol")
                            } else vlm = "N/A"
                        } else {
                            val alt = secondPool.filter { it.cand != costCand }.maxByOrNull { it.costScore }
                                ?: enriched.filter { it.cand != costCand && it.cand != volCand }.maxByOrNull { it.costScore }
                            if (alt != null) {
                                costCand = alt.cand
                                cst = repairDecimalForRole(alt.cleanDigits, "cost")
                            } else cst = "N/A"
                        }
                    }
                    if (digitCount(cst) < 2) cst = "N/A"
                    if (digitCount(vlm) < 2) vlm = "N/A"
                    return CostVolClassifyResult(cst, vlm, costCand, volCand)
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
                        val cv = classifyCostVolFromBoxOcr(candidates)
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
                // Called after the (now top-4) prune in every proc. Reuses the existing createCrop + direct calcHist + stat pattern from C/E visuals (no visuals/longLived here; data only for JSON/metadata "redboxData" + "n_per_red_hists").
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
                    oranges: List<android.graphics.Rect> = emptyList()
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
                    return JSONObject()
                        .put("reds", redsArr)
                        .put("ocrSourceRects", ocrArr)
                        .put("candidates", candsArr)
                        .put("chosen", chosen)
                        .put("final", finalObj)
                        .put("assembly", assemblyObj)
                        .put("oranges", orangesArr)
                        .toString()
                }

                // Phase 0 other visibility: hoist processedScales decl (the remnant inline one) early before procs so visible inside proc bodies after dupe + for the reinit in remnant discovery (per "any other visibility fixes for vars/lists (pdHunks*Total, mlBlocksRaw, scales, processedScales, experimentRec* buffers, etc.)").
                var processedScales = mutableSetOf<Int>()

                // Per-column top-4 box OCR (PUMP_COST_VOLUME_CLASSIFIER_SPEC.md): as-is (golden Y-band only) + digits on pixel rects; fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan: clean text only; probs returned separately
                suspend fun ocrPumpRectsAsisAndDigits(rects: List<android.graphics.Rect>): PumpRectOcrLists {
                    val asisPairs = rects.map { r ->
                        val pW = r.width(); val pH = r.height()
                        if (pW < 2 || pH < 2) "?" to "" else {
                            val l = r.left.coerceIn(0, imgW - 1)
                            val t = r.top.coerceIn(0, imgH - 1)
                            val rr = r.right.coerceIn(l + 1, imgW)
                            val bb = r.bottom.coerceIn(t + 1, imgH)
                            val cropId = workspace.createCrop(l, t, rr - l, bb - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognize(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            pumpOcrCleanAndProbs(res.debugText, res.perCharProbs)
                        }
                    }
                    val digitsPairs = rects.map { rp ->
                        val pW = rp.width(); val pH = rp.height()
                        if (pW < 2 || pH < 2) "?" to "" else {
                            val l = rp.left.coerceIn(0, imgW - 1)
                            val t = rp.top.coerceIn(0, imgH - 1)
                            val rr = rp.right.coerceIn(l + 1, imgW)
                            val bb = rp.bottom.coerceIn(t + 1, imgH)
                            val cropId = workspace.createCrop(l, t, rr - l, bb - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet1024x48.p.clear()
                            val recCropId = experimentRecSet1024x48.createCrop(4, 4, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet1024x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumericDecimal(experimentRecSet1024x48.c[recCropId])
                            experimentRecSet1024x48.c[recCropId].release(); workspace.c[cropId].release()
                            pumpOcrCleanAndProbs(res.debugText, res.perCharProbs)
                        }
                    }
                    return PumpRectOcrLists(
                        asis = asisPairs.map { it.first },
                        digits = digitsPairs.map { it.first },
                        asisProbs = asisPairs.map { it.second },
                        digitsProbs = digitsPairs.map { it.second }
                    )
                }

                suspend fun computeRetractedBluePixelRects(): List<android.graphics.Rect> {
                    val expPixelRects = pdHunksExpTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }
                    val retractedPixel = mutableListOf<android.graphics.Rect>()
                    for (r in expPixelRects) {
                        val (retracted, _) = NativeImageUtils.expandByUniformity(workspace.p.mat, r)
                        retractedPixel.add(retracted)
                    }
                    return retractedPixel
                }

                suspend fun doBOrDRedOnlyImage() {
                    // Red-only image for Set B/D (per approved plan): clean view of post-filter reds only (no blue, no orange) so user can inspect redbox merging state without other annotations overlaid. Full image remains exactly "as is happening now". D mirrors B.
                    val redAnnsOnly = getAnns(pdHunksRawTotal, Color.RED, 2)
                    val redOnlyB64 = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, redAnnsOnly, null, workspace).first
                    branch.images["PD_red_only"] = redOnlyB64
                }

                suspend fun doBOrDRetractedBlueAndPD() {
                    // For B blue from exp hunks (expanded from raw reds): retract to tight text fit (similar to C; using workspace.p.mat for content-aware shrink when expansion hits limit with no text).
                    // Optimization (inside split-out helper per the D/E + user pixel/sweep plan): convert input lists to pixel Rects *once* (O(N) ICRS at boundary only). Use the pixel Rects for the expandByUniformity (native pixel) and for any future per-box work. Convert back only for the final retractedExpForBlue list (used by getAnns for the annotated PD). This eliminates repeated ICRS<->pixel inside the per-box loops (even on the post-prune N=6). PumpHunk form kept only for anns/snapshot compatibility; intra red/blue working is pixel Rects (images are unique per photo, no cross-image ICRS use needed). (This pixel-vs-ICRS / ICRS-at-boundary is pump red box (and associated blue) only and must not affect alignment or other experiments' ICRS sourceRect usage on full buffers for diagnostic crops.)
                    pdHunksExpTotal.forEach { h ->
                        if (h.rect.left > 2f || h.rect.top > 2f || h.rect.right > 2f || h.rect.bottom > 2f) {
                            Log.w(TAG, "ICRS-like PumpHunk rect sanity: L=${h.rect.left} T=${h.rect.top} R=${h.rect.right} B=${h.rect.bottom}")
                        }
                    }
                    val expPixelRects = pdHunksExpTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }
                    val rectMin = expPixelRects.minOfOrNull { it.left } ?: -1
                    val rectMax = expPixelRects.maxOfOrNull { it.right } ?: -1
                    Log.d(TAG, "BCF expand path still active: n=${expPixelRects.size} rectMin=$rectMin rectMax=$rectMax")
                    val maxPixelRects = pdHunksMaxTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }
                    val retractedPixel = mutableListOf<android.graphics.Rect>()
                    for (r in expPixelRects) {
                        val (retracted, _) = NativeImageUtils.expandByUniformity(workspace.p.mat, r)
                        retractedPixel.add(retracted)
                    }
                    // direct pixel wrap (no ICRS at boundary; retractedPixel already full photo pixel space)
                    val retractedExpForBlue = retractedPixel.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    }
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(retractedExpForBlue, Color.BLUE, 4) + getAnns(pdHunksMaxTotal, Color.rgb(255, 165, 0), 2)
                    val baseB64 = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPd, null, workspace).first
                    branch.images["PD"] = baseB64
                }



                val procA: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set A"
                    // aliases map params for exact dupe of per-flow logic (common setup at dispatch site; procs receive ws/br/det/w/h)
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    val rawHist = OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
                    originalHistogram = JSONArray().apply { rawHist.forEach { put(it.toDouble()) } }
                    root.images["after"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_SMALL_TARGET_W, 0, emptyList(), null, workspace).first
                    root.images["hist2"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAngleMlOnly(workspace.p)
                    val tilt = -deskewRes.angle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()

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
                                // Set A ML only: direct integer pixel mapping from ML block (at target buffer size) to full imgW/imgH.
                                // No IcrsMath, no pixelToIcrs, no ICRS roundtrip, integer arithmetic (equiv within rounding to prior).
                                // Produces integer-valued rects (via .0 in RectF for PumpHunk compat; PumpHunk kept as-is per plan).
                                val ml = block.boundingBox.left
                                val mt = block.boundingBox.top
                                val mr = block.boundingBox.right
                                val mb = block.boundingBox.bottom
                                val l = ml * imgW / targetW
                                val t = mt * imgH / targetH
                                val r = mr * imgW / targetW
                                val b = mb * imgH / targetH
                                PumpHunk(block.text, RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()))
                            }
                            mlBlocksRaw.addAll(hunks)
                        }

                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
                    val detected = paddleResults[0]
                    val raw = paddleResults[1]
                    val exp = paddleResults[2]
                    val maxExt = paddleResults[3]
                    val native = paddleResults[4]

                    // Set A red path only: snap rects to integer pixels on collection (re-derive integer from run results; eliminate float *fullW.toFloat()/contentW effect from shared runDiscoveryPaddle for A's pd* lists only; other sets untouched)
                    pdHunksDetectedTotal.addAll(detected.map { h -> val rr = h.rect; PumpHunk(h.text, RectF(rr.left.toInt().toFloat(), rr.top.toInt().toFloat(), rr.right.toInt().toFloat(), rr.bottom.toInt().toFloat())) })
                    pdHunksRawTotal.addAll(raw.map { h -> val rr = h.rect; PumpHunk(h.text, RectF(rr.left.toInt().toFloat(), rr.top.toInt().toFloat(), rr.right.toInt().toFloat(), rr.bottom.toInt().toFloat())) })
                    pdHunksExpTotal.addAll(exp.map { h -> val rr = h.rect; PumpHunk(h.text, RectF(rr.left.toInt().toFloat(), rr.top.toInt().toFloat(), rr.right.toInt().toFloat(), rr.bottom.toInt().toFloat())) })
                    pdHunksMaxTotal.addAll(maxExt.map { h -> val rr = h.rect; PumpHunk(h.text, RectF(rr.left.toInt().toFloat(), rr.top.toInt().toFloat(), rr.right.toInt().toFloat(), rr.bottom.toInt().toFloat())) })
                    pdHunksNativeTotal.addAll(native.map { h -> val rr = h.rect; PumpHunk(h.text, RectF(rr.left.toInt().toFloat(), rr.top.toInt().toFloat(), rr.right.toInt().toFloat(), rr.bottom.toInt().toFloat())) })

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

                // A red prune only: stay in integer Rect throughout (build pixel lists, run filterPixel, use pruned integer rects for A data). Removed map-to-RectF rebuilds for A's red lists (no .map {r->PumpHunk RectF} in this A block; pd* fed from integer Rects for getFinal/expand/takeCrop in A path).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                val expPixel = pdHunksExpTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                // Sync A pd* lists from pruned integer Rects (ctor RectF(int) only for compat with PumpHunk/getFinal; A red path now integer end-to-end for this proc's data)
                pdHunksRawTotal.clear()
                redPixelList.forEach { r -> pdHunksRawTotal.add(PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))) }
                pdHunksExpTotal.clear()
                expPixel.forEach { r -> pdHunksExpTotal.add(PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))) }
                pdHunksMaxTotal.clear()
                maxPixel.forEach { r -> pdHunksMaxTotal.add(PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))) }
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // data for all sets (redboxData + n_per_red_hists); C/E visuals/redboxDataC follow below
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 4 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)

                val mlHunks = mergeGeometryIntoHunks(mlBlocksRaw)
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                // Updated for A: now receives integer pixel data (red snapped+pruned int Rect lists, ML direct int, expand/takeCrop int) -> direct int Rect to takeSnapshot crops for A final PathResult.
                // fix-remaining-report-issues-20260619-plan: Set A — buildRedBoxCandidates uses aRedPixel ocr rects
                val aRedPixel = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val ocrA = ocrPumpRectsAsisAndDigits(aRedPixel)
                val aCands = buildRedBoxCandidates(aRedPixel, ocrA.asis, ocrA.digits, ocrA.asisProbs, ocrA.digitsProbs)
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, aCands)
                val cvAPaddle = classifyCostVolFromBoxOcr(aCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = aRedPixel,
                    ocrSourceRects = aRedPixel,
                    candidates = aCands,
                    costCand = cvAPaddle.costCand,
                    volCand = cvAPaddle.volCand,
                    finalCost = cvAPaddle.cost,
                    finalVol = cvAPaddle.vol,
                    assembly = mapOf("method" to "raw", "note" to "raw reds as ocr rects (no blue expansion)")
                )
                    branch.pathResults["ML"] = getFinal(mlHunks, "ML Kit", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, aCands)
                val cvAML = classifyCostVolFromBoxOcr(aCands)
                branch.metadata["costVolDecisionData_ML"] = buildCostVolDecisionDataJson(
                    reds = aRedPixel,
                    ocrSourceRects = aRedPixel,
                    candidates = aCands,
                    costCand = cvAML.costCand,
                    volCand = cvAML.volCand,
                    finalCost = cvAML.cost,
                    finalVol = cvAML.vol,
                    assembly = mapOf("method" to "raw", "note" to "raw reds as ocr rects (no blue expansion)")
                )

                // 5. Visualization
                    val aMl = getAnns(mlBlocksRaw, Color.RED, 2) + getAnns(mlHunks, Color.rgb(255, 165, 0), 4)
                    branch.images["ML"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aMl, null, workspace).first

                // (doBOrD* and doCOrE* not hoisted in Phase 0 for this A dupe per plan "if not hoisted include copies at dupe"; excised B/C branches in this repair to remove unresolved calls in procA paste while keeping full A logic (different minimal repair per anti-doom after first dupe error; see failure log scope symptoms))
                // A (reds only)
                val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPd, null, workspace).first
                }
                val procB: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set B"
                    // aliases map params for exact dupe of per-flow logic
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    val rawHist = OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel (already in .rect); no roundtrip.
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val expPixel = pdHunksExpTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for F (redboxData + n_per_red_hists)
                val redPixelBForBinPeak = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val binPeakCandidatesB = mutableListOf<RedBoxOcrCandidate>()
                captureBinPeakSnapshotsFromRedbox(branch, workspace, redPixelBForBinPeak, paddleEngine, experimentRecSet1024x48, imgW, imgH, binPeakCandidatesB)
                branch.metadata["binPeakCandidateCount"] = binPeakCandidatesB.size.toString()

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // Set B — object-based blue from binPeak binaries (pre/post-clean per peak); replaces expandByUniformity path
                val bCands = binPeakCandidatesB
                val bOcrSourceRects = bCands.mapNotNull { it.rect }
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, bCands)
                val redPixelB = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val cvB = classifyCostVolFromBoxOcr(bCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelB,
                    ocrSourceRects = bOcrSourceRects,
                    candidates = bCands,
                    costCand = cvB.costCand,
                    volCand = cvB.volCand,
                    finalCost = cvB.cost,
                    finalVol = cvB.vol,
                    assembly = mapOf("method" to "binPeak-object", "note" to "per-peak binarized object-based blue (uncleaned+cleaned)")
                )

                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    doBOrDRedOnlyImage()
                    // BCF expand/retract bypassed; using object-based binPeak blue only
                    // doBOrDRetractedBlueAndPD()
                }
                val procC: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set C"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h

                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()

                    val (rawForC, _) = OcrUtils.takeSnapshot(workspace.p, null, PUMP_C_VISUAL_TARGET_W, 0, emptyList(), null, workspace)
                    branch.images["rawC"] = rawForC
                    val tG0 = System.currentTimeMillis()
                    branch.images["histBeforeC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_before_c_ms"] = (System.currentTimeMillis() - tG0).toString()
                    val rawHist = OdometerOcrUtils.valleyPushToPeaks(workspace.p.mat)
                    val (pushedForC, _) = OcrUtils.takeSnapshot(workspace.p, null, PUMP_C_VISUAL_TARGET_W, 0, emptyList(), null, workspace)
                    branch.images["pushedC"] = pushedForC
                    val tG1 = System.currentTimeMillis()
                    branch.images["histAfterC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_after_c_ms"] = (System.currentTimeMillis() - tG1).toString()
                    branch.metadata["t_valley_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel (already in .rect); no roundtrip.
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
                // Rebuild pdHunksRawTotal from the final <=4 (direct pixel RectF, no IcrsMath)
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                // Propagate prune to exp/max (blue/orange sources in B/C paths)
                val expPixel = pdHunksExpTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for A (redboxData + n_per_red_hists)

                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 4 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic updated per per-red C/E histogram cleanup plan: createCrop + direct calcHist + dual takeSnapshot from longLived.)
                    // Post-prune (filtered 4) redbox hists for C/E *display* / JSON (updated to createCrop + dual takeSnapshot + long-lived buffer per plan).
                    // Early probe now only does polarity (combined mask); this capture on the pruned pdHunksRawTotal provides the 4 for builder column + redboxDataC in JSON (no more 30).
                    // h/w/area kept from rect; collection to redboxDataC / redboxHistC_* / metadata unchanged.
                    captureRedboxData(pdHunksRawTotal, workspace, branch)  // common redboxData for JSON (all sets); C visuals/redboxDataC + n_per_red_hists below
                    val redPixelCForBinPeak = pdHunksRawTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }
                    val binPeakCandidatesC = mutableListOf<RedBoxOcrCandidate>()
                    captureBinPeakSnapshotsFromRedbox(branch, workspace, redPixelCForBinPeak, paddleEngine, experimentRecSet1024x48, imgW, imgH, binPeakCandidatesC)
                    branch.metadata["binPeakCandidateCount"] = binPeakCandidatesC.size.toString()
                    val redboxDataC = JSONArray()
                    pdHunksRawTotal.forEachIndexed { i, hunk ->
                        val rw = (hunk.rect.right - hunk.rect.left).toInt()
                        val rh = (hunk.rect.bottom - hunk.rect.top).toInt()
                        val rarea = rw * rh

                        // Use createCrop on red rect pixel coords from hunk (for red rect image snapshot).
                        // Bins: direct calcHist on crop's .mat (no mask).
                        // Red rect snapshot + hist snapshot via OcrUtils.takeSnapshot (dual); scratchYuv = longLivedHistogramBuffer (init at pump start, held lifetime).
                        // Plot render into longLived's histPlotCrop (no temp on workspace; plotCrop never released).
                        // Only the per-red rect crop released promptly.
                        // Removed all perMask / Mat.zeros / rectangle(perMask) / generateHistogramB64 for this per-red path.
                        // (Comments cleaned of old "safe perMask", "Crop vs full-mask", "to avoid nativeObj", "manual drawRect loops"; documents crop + dual takeSnapshot + long-lived at pump start.)
                        // Red box crop must be done by caller only (createCrop on the red rect pixel coords after pump optimization), then pass crop[id] (Slice) as source to takeSnapshot. This pattern is required. The pixel-vs-ICRS / ICRS-at-boundary optimization is pump red box only and must not affect alignment or other experiments' ICRS sourceRect usage on full buffers for diagnostic crops. takeSnapshot's internal output crop creation (snapCropId) is unchanged from alignment-tested behavior.
                        val cropId = workspace.createCrop(hunk.rect.left.toInt(), hunk.rect.top.toInt(), (hunk.rect.right - hunk.rect.left).toInt(), (hunk.rect.bottom - hunk.rect.top).toInt())
                        val (rectB64, _) = OcrUtils.takeSnapshot(source = workspace.c[cropId], targetW = PUMP_PER_RED_TARGET_W, scratchYuv = longLivedHistogramBuffer)
                        branch.images["redboxRectC_${i}"] = rectB64

                        val hmat = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.c[cropId].mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hmat, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                        val rbins = FloatArray(64); hmat.get(0, 0, rbins); hmat.release()
                        val stat = JSONObject().put("index", i).put("h", rh).put("w", rw).put("area", rarea)
                        val binsArr = JSONArray(); rbins.forEach { binsArr.put(it.toDouble()) }; stat.put("histBins", binsArr)
                        redboxDataC.put(stat)

                        // Histogram visual via plot render into longLived histPlotCrop + takeSnapshot (no generate, no workspace temp)
                        val plotMat = longLivedHistogramBuffer.c[histPlotCrop].mat
                        plotMat.setTo(org.opencv.core.Scalar(0.0))
                        val maxVal = (1..62).maxOf { rbins[it] }.toDouble().coerceAtLeast(1.0)
                        for (k in 1..62) {
                            val hh = (rbins[k] / maxVal * 240.0).toInt().coerceAtMost(240)
                            val xx = (k - 1) * 3
                            org.opencv.imgproc.Imgproc.rectangle(plotMat, org.opencv.core.Rect(xx, 240 - hh, 3, hh), org.opencv.core.Scalar(255.0), -1)
                        }
                        val (histB64, _) = OcrUtils.takeSnapshot(source = longLivedHistogramBuffer.c[histPlotCrop], targetW = PUMP_PER_RED_TARGET_W, scratchYuv = longLivedHistogramBuffer)
                        branch.images["redboxHistC_${i}"] = histB64

                        workspace.c[cropId].release()  // only rect crop; longLived + histPlotCrop held lifetime, never released in per-red path
                    }
                    branch.metadata["redboxDataC"] = redboxDataC.toString()
                    branch.metadata["n_per_red_hists"] = pdHunksRawTotal.size.toString()

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                // Set C — object-based blue from binPeak binaries (pre/post-clean per peak); replaces expandByUniformity path
                val cCands = binPeakCandidatesC
                val cOcrSourceRects = cCands.mapNotNull { it.rect }
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, cCands)
                val redPixelC = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val cvC = classifyCostVolFromBoxOcr(cCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelC,
                    ocrSourceRects = cOcrSourceRects,
                    candidates = cCands,
                    costCand = cvC.costCand,
                    volCand = cvC.volCand,
                    finalCost = cvC.cost,
                    finalVol = cvC.vol,
                    assembly = mapOf("method" to "binPeak-object", "note" to "per-peak binarized object-based blue (uncleaned+cleaned)")
                )
                val redAnns = getAnns(pdHunksRawTotal, Color.RED, 2)
                branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, redAnns, null, workspace).first
                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                doBOrDRedOnlyImage()
                // BCF expand/retract bypassed; using object-based binPeak blue only
                // doBOrDRetractedBlueAndPD()
            }
                val procD: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set D"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    val rawHist = OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel from ingest (runDiscoveryPaddle explicit upscale); no roundtrip. Pixel filter + prune4 on rects; direct rebuild. Early probe sees full; post-prune 4 for all sets (C/E display only).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
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
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for D (redboxData + n_per_red_hists)

                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 4 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                
                // fix-remaining-report-issues-20260619-plan: Set D — buildRedBoxCandidates uses customBluePixelD ocr rects
                val (customBlueDPre, _) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val customBluePixelD = customBlueDPre.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val ocrD = ocrPumpRectsAsisAndDigits(customBluePixelD)
                val dCands = buildRedBoxCandidates(customBluePixelD, ocrD.asis, ocrD.digits, ocrD.asisProbs, ocrD.digitsProbs)
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, dCands)
                val redPixelD = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val dVertFactors = (1..8).map { it / 10f }
                val cvD = classifyCostVolFromBoxOcr(dCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelD,
                    ocrSourceRects = customBluePixelD,
                    candidates = dCands,
                    costCand = cvD.costCand,
                    volCand = cvD.volCand,
                    finalCost = cvD.cost,
                    finalVol = cvD.vol,
                    assembly = mapOf(
                        "method" to "calculated",
                        "vertFactors" to dVertFactors,
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (Pre blues for OCR cands)"
                    )
                )
                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                doBOrDRedOnlyImage()
                // D custom blue/orange (no valley) via createBlueAndOrangeHunksFromReds (calculated blue expansions +10% to +80% vert step 10%, horiz 50%)
                val (customBlueD, customOrangeD) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val orangePixelD = customOrangeD.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelD,
                    ocrSourceRects = customBluePixelD,
                    candidates = dCands,
                    costCand = cvD.costCand,
                    volCand = cvD.volCand,
                    finalCost = cvD.cost,
                    finalVol = cvD.vol,
                    assembly = mapOf(
                        "method" to "calculated",
                        "vertFactors" to dVertFactors,
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (viz second create includes oranges)"
                    ),
                    oranges = orangePixelD
                )
                val aPdD = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(customBlueD, Color.BLUE, 4) + getAnns(customOrangeD, Color.rgb(255, 165, 0), 2)
                val baseB64D = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdD, null, workspace).first
                branch.images["PD"] = baseB64D
            }
                val procE: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set E"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h

                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()

                    val (rawForC, _) = OcrUtils.takeSnapshot(workspace.p, null, PUMP_C_VISUAL_TARGET_W, 0, emptyList(), null, workspace)
                    branch.images["rawC"] = rawForC
                    val tG0 = System.currentTimeMillis()
                    branch.images["histBeforeC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_before_c_ms"] = (System.currentTimeMillis() - tG0).toString()
                    val rawHist = OdometerOcrUtils.valleyPushToPeaks(workspace.p.mat)
                    val (pushedForC, _) = OcrUtils.takeSnapshot(workspace.p, null, PUMP_C_VISUAL_TARGET_W, 0, emptyList(), null, workspace)
                    branch.images["pushedC"] = pushedForC
                    val tG1 = System.currentTimeMillis()
                    branch.images["histAfterC"] = generateHistogramB64(workspace.p.mat, 0.40f)
                    branch.metadata["t_hist_after_c_ms"] = (System.currentTimeMillis() - tG1).toString()
                    branch.metadata["t_valley_ms"] = (System.currentTimeMillis() - tFlowStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel from ingest (runDiscoveryPaddle explicit upscale); no roundtrip. Pixel filter + prune4 on rects; direct rebuild. Early probe sees full; post-prune 4 for all sets (C/E display only).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
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
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 4 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic updated per per-red C/E histogram cleanup plan: createCrop + direct calcHist + dual takeSnapshot from longLived.)
                    // Post-prune (filtered 4) redbox hists for C/E *display* / JSON (updated to createCrop + dual takeSnapshot + long-lived buffer per plan).
                    // Early probe now only does polarity (combined mask); this capture on the pruned pdHunksRawTotal provides the 4 for builder column + redboxDataC in JSON (no more 30).
                    // h/w/area kept from rect; collection to redboxDataC / redboxHistC_* / metadata unchanged.
                    captureRedboxData(pdHunksRawTotal, workspace, branch)  // common redboxData (all sets); E visuals/redboxDataC follow
                    val redboxDataC = JSONArray()
                    pdHunksRawTotal.forEachIndexed { i, hunk ->
                        val rw = (hunk.rect.right - hunk.rect.left).toInt()
                        val rh = (hunk.rect.bottom - hunk.rect.top).toInt()
                        val rarea = rw * rh

                        // Use createCrop on red rect pixel coords from hunk (for red rect image snapshot).
                        // Bins: direct calcHist on crop's .mat (no mask).
                        // Red rect snapshot + hist snapshot via OcrUtils.takeSnapshot (dual); scratchYuv = longLivedHistogramBuffer (init at pump start, held lifetime).
                        // Plot render into longLived's histPlotCrop (no temp on workspace; plotCrop never released).
                        // Only the per-red rect crop released promptly.
                        // Removed all perMask / Mat.zeros / rectangle(perMask) / generateHistogramB64 for this per-red path.
                        // (Comments cleaned of old "safe perMask", "Crop vs full-mask", "to avoid nativeObj", "manual drawRect loops"; documents crop + dual takeSnapshot + long-lived at pump start.)
                        // Red box crop must be done by caller only (createCrop on the red rect pixel coords after pump optimization), then pass crop[id] (Slice) as source to takeSnapshot. This pattern is required. The pixel-vs-ICRS / ICRS-at-boundary optimization is pump red box only and must not affect alignment or other experiments' ICRS sourceRect usage on full buffers for diagnostic crops. takeSnapshot's internal output crop creation (snapCropId) is unchanged from alignment-tested behavior.
                        val cropId = workspace.createCrop(hunk.rect.left.toInt(), hunk.rect.top.toInt(), (hunk.rect.right - hunk.rect.left).toInt(), (hunk.rect.bottom - hunk.rect.top).toInt())
                        val (rectB64, _) = OcrUtils.takeSnapshot(source = workspace.c[cropId], targetW = PUMP_PER_RED_TARGET_W, scratchYuv = longLivedHistogramBuffer)
                        branch.images["redboxRectC_${i}"] = rectB64

                        val hmat = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(workspace.c[cropId].mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hmat, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
                        val rbins = FloatArray(64); hmat.get(0, 0, rbins); hmat.release()
                        val stat = JSONObject().put("index", i).put("h", rh).put("w", rw).put("area", rarea)
                        val binsArr = JSONArray(); rbins.forEach { binsArr.put(it.toDouble()) }; stat.put("histBins", binsArr)
                        redboxDataC.put(stat)

                        // Histogram visual via plot render into longLived histPlotCrop + takeSnapshot (no generate, no workspace temp)
                        val plotMat = longLivedHistogramBuffer.c[histPlotCrop].mat
                        plotMat.setTo(org.opencv.core.Scalar(0.0))
                        val maxVal = (1..62).maxOf { rbins[it] }.toDouble().coerceAtLeast(1.0)
                        for (k in 1..62) {
                            val hh = (rbins[k] / maxVal * 240.0).toInt().coerceAtMost(240)
                            val xx = (k - 1) * 3
                            org.opencv.imgproc.Imgproc.rectangle(plotMat, org.opencv.core.Rect(xx, 240 - hh, 3, hh), org.opencv.core.Scalar(255.0), -1)
                        }
                        val (histB64, _) = OcrUtils.takeSnapshot(source = longLivedHistogramBuffer.c[histPlotCrop], targetW = PUMP_PER_RED_TARGET_W, scratchYuv = longLivedHistogramBuffer)
                        branch.images["redboxHistC_${i}"] = histB64

                        workspace.c[cropId].release()  // only rect crop; longLived + histPlotCrop held lifetime, never released in per-red path
                    }
                    branch.metadata["redboxDataC"] = redboxDataC.toString()
                    branch.metadata["n_per_red_hists"] = pdHunksRawTotal.size.toString()

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                // fix-remaining-report-issues-20260619-plan: Set E — buildRedBoxCandidates uses customBluePixelE ocr rects
                val (customBlueEPre, _) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val customBluePixelE = customBlueEPre.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val ocrE = ocrPumpRectsAsisAndDigits(customBluePixelE)
                val eCands = buildRedBoxCandidates(customBluePixelE, ocrE.asis, ocrE.digits, ocrE.asisProbs, ocrE.digitsProbs)
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, eCands)
                val redPixelE = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val eVertFactors = (1..8).map { it / 10f }
                val cvE = classifyCostVolFromBoxOcr(eCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelE,
                    ocrSourceRects = customBluePixelE,
                    candidates = eCands,
                    costCand = cvE.costCand,
                    volCand = cvE.volCand,
                    finalCost = cvE.cost,
                    finalVol = cvE.vol,
                    assembly = mapOf(
                        "method" to "calculated",
                        "vertFactors" to eVertFactors,
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (Pre blues for OCR cands)"
                    )
                )
                val redAnns = getAnns(pdHunksRawTotal, Color.RED, 2)
                branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, redAnns, null, workspace).first
                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                doBOrDRedOnlyImage()
                // E custom blue/orange (matching D, no valley) via createBlueAndOrangeHunksFromReds (calculated blue expansions +10% to +80% vert step 10%, horiz 50%)
                val (customBlueE, customOrangeE) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val orangePixelE = customOrangeE.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelE,
                    ocrSourceRects = customBluePixelE,
                    candidates = eCands,
                    costCand = cvE.costCand,
                    volCand = cvE.volCand,
                    finalCost = cvE.cost,
                    finalVol = cvE.vol,
                    assembly = mapOf(
                        "method" to "calculated",
                        "vertFactors" to eVertFactors,
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (viz second create includes oranges)"
                    ),
                    oranges = orangePixelE
                )
                val aPdE = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(customBlueE, Color.BLUE, 4) + getAnns(customOrangeE, Color.rgb(255, 165, 0), 2)
                val baseB64E = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdE, null, workspace).first
                branch.images["PD"] = baseB64E
            }
                val procF: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set F"
                    // aliases map params for exact dupe of per-flow logic
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel (already in .rect); no roundtrip.
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
                pdHunksRawTotal.clear()
                pdHunksRawTotal.addAll(redPixelList.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val expPixel = pdHunksExpTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(expPixel)
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for F (redboxData + n_per_red_hists)
                val redPixelFForBinPeak = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val binPeakCandidatesF = mutableListOf<RedBoxOcrCandidate>()
                captureBinPeakSnapshotsFromRedbox(branch, workspace, redPixelFForBinPeak, paddleEngine, experimentRecSet1024x48, imgW, imgH, binPeakCandidatesF)
                branch.metadata["binPeakCandidateCount"] = binPeakCandidatesF.size.toString()

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // Set F — object-based blue from binPeak binaries (pre/post-clean per peak); replaces expandByUniformity path
                val fCands = binPeakCandidatesF
                val fOcrSourceRects = fCands.mapNotNull { it.rect }
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, fCands)
                val redPixelF = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val cvF = classifyCostVolFromBoxOcr(fCands)
                branch.metadata["costVolDecisionData_Paddle"] = buildCostVolDecisionDataJson(
                    reds = redPixelF,
                    ocrSourceRects = fOcrSourceRects,
                    candidates = fCands,
                    costCand = cvF.costCand,
                    volCand = cvF.volCand,
                    finalCost = cvF.cost,
                    finalVol = cvF.vol,
                    assembly = mapOf("method" to "binPeak-object", "note" to "per-peak binarized object-based blue (uncleaned+cleaned)")
                )

                    doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                    doBOrDRedOnlyImage()
                    // BCF expand/retract bypassed; using object-based binPeak blue only
                    // doBOrDRetractedBlueAndPD()
                }
                val procG: suspend (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    val flowName = "Set G"
                    val workspace = ws
                    val branch = br
                    val discoveryDetails = det
                    val imgW = w
                    val imgH = h
                    val tDeskewStart = System.currentTimeMillis()
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(workspace, tilt)
                    branch.metadata["tilt"] = "%.2f".format(tilt)
                    branch.metadata["t_deskew_ms"] = (System.currentTimeMillis() - tDeskewStart).toString()
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


                    val (outerId, innerId) = prepareScale(workspace, scale)
                    val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale, branch.metadata)
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

                // Direct pixel from ingest (runDiscoveryPaddle explicit upscale); no roundtrip. Pixel filter + prune4 on rects; direct rebuild. Early probe sees full; post-prune 4 for all sets (C/E display only).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                if (redPixelList.size > 4) {
                    redPixelList.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    redPixelList.subList(4, redPixelList.size).clear()
                }
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
                if (expPixel.size > 4) {
                    expPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    expPixel.subList(4, expPixel.size).clear()
                }
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                if (maxPixel.size > 4) {
                    maxPixel.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
                    maxPixel.subList(4, maxPixel.size).clear()
                }
                pdHunksMaxTotal.clear()
                pdHunksMaxTotal.addAll(maxPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                branch.metadata["n_reds_after_prune4"] = pdHunksRawTotal.size.toString()
                // For all sets (prune now applies in every proc A/B/C/D/E/F/G) the proc stubs + thin if calls + helpers will see the pruned <=4 in the lists for "other processing" (blue, anns, OCR, red-only, and the post-prune display hists for C/E).
                captureRedboxData(pdHunksRawTotal, workspace, branch)  // common for G (redboxData + n_per_red_hists)

                // The optimizations (pixel Rects for red working lists, 4px/1024x48 aspect OCR in helpers, crop for hists in the C/E display capture here) apply to *any of the paddle sets that they could apply to* (all red-derived paths per user clarification). Prune-to-4 limitation applies in all procs now. Early probe for C/E now only does polarity on initial (cheap combined mask); the 4 post-prune capture provides the filtered redboxData + redboxHistC_* for display/JSON (fixing the 30 histograms issue).

                // Phase 1 fix (per approved plan for user's clarification "the current code doesn't properly filter the red boxes (histograms on line 1 still show 30 for C and E)"):
                // After the common prune (which thins pdHunks* to the 4 largest), for C/E re-capture the *display* redboxDataC + redboxHistC_* images using only the now-pruned list.
                // This overwrites the pre-prune data set in the early probe (~708), so the builder for C/E columns (and JSON redboxDataC for those sets) only sees the filtered 4 (sorted by area desc, 3-wide stacked in the HTML).
                // Early probe still does polarity on initial reds + n_reds_at_probe for analysis (per plan language "early probe can see full initial").
                // (The capture logic is duplicated here for this small mechanical fix chunk; will factor + optimize with YUV/crop in Phase 2.)

                val mlHunks = emptyList<PumpHunk>()
                val pdHunksMerged = mergeGeometryIntoHunks(pdHunksExpTotal)

                // 4. Extraction
                // getFinal (the shared param'd version from Phase 1) hoisted earlier (before flowProcessors list)
                // for name resolution inside the C processor lambda body (the array entry for Set C calls it
                // for the best path result using the valley versions).
                // fix-remaining-report-issues-20260619-plan: Set G — buildRedBoxCandidates uses customBluePixelG ocr rects
                val (customBlueGPre, _) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val customBluePixelG = customBlueGPre.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val ocrG = ocrPumpRectsAsisAndDigits(customBluePixelG)
                val gCands = buildRedBoxCandidates(customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs, ocrG.digitsProbs)
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH, gCands)
                val redPixelG = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }
                val gVertFactors = (1..8).map { it / 10f }
                val cvG = classifyCostVolFromBoxOcr(gCands)
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
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (Pre blues for OCR cands)"
                    )
                )
                doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
                doBOrDRedOnlyImage()
                // G custom blue/orange (raw clone of D, no stretch) via createBlueAndOrangeHunksFromReds (calculated blue expansions +10% to +80% vert step 10%, horiz 50%)
                val (customBlueG, customOrangeG) = createBlueAndOrangeHunksFromReds(
                    pdHunksRawTotal, imgW, imgH, (1..8).map { it / 10f }, 0.5f)
                val orangePixelG = customOrangeG.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
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
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to "createBlueAndOrangeHunksFromReds (viz second create includes oranges)"
                    ),
                    oranges = orangePixelG
                )
                val aPdG = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(customBlueG, Color.BLUE, 4) + getAnns(customOrangeG, Color.rgb(255, 165, 0), 2)
                val baseB64G = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdG, null, workspace).first
                branch.images["PD"] = baseB64G
            }
                val flowProcessors = listOf(procA, procB, procC, procD, procE, procF, procG)

                // Call the processor for this flow (i) from the array. Per-set behavior now selected by thin if calls to extracted helpers (B/C/E special after common filter) + proc index.
                // Phase 3 post-dupe prep: all 5 procs have full dupe + local flowName; additional vis hoists done in Phase 0; remnant still present but names resolvable; ready for granular retirement (dispatch sole after).
                tDiscoveryWrapperStart = System.currentTimeMillis()
                flowProcessors[i](workspace, branch, discoveryDetails, imgW, imgH)
                branch.metadata["t_discovery_wrapper_ms"] = (System.currentTimeMillis() - tDiscoveryWrapperStart).toString()
                // t_discovery_wrapper_ms covers the main body processor / 4-scale discovery call (distinct from inner per-scale t_pd_inference_* / t_pd_native_post_*) for A/B gap attribution

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

            // Clear/reset or re-allocate the reusable buffer to keep memory bounded; large initial for P4 base64 strings per photo
            if (jsonCharBuffer.capacity() > PUMP_JSON_BUFFER_RESET_CAPACITY) {
                jsonCharBuffer = StringBuilder(PUMP_JSON_BUFFER_INITIAL_BYTES)
            } else {
                jsonCharBuffer.setLength(0)
            }

            appendJsonObject(jsonCharBuffer, photoJson, 2, 0)
            jsonFile.appendText(jsonCharBuffer.toString() + "$comma\n")

            val summaryText = flows.map { f ->
                val br = root.getBranch(f)
                if (br.pathResults.containsKey("ML")) {
                    "$f: ${br.pathResults["ML"]?.cost ?: "F"}"
                } else {
                    "$f Paddle: ${br.pathResults["Paddle"]?.cost ?: "F"}"
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


private fun binPeakRectsIntersect(a: android.graphics.Rect, b: android.graphics.Rect): Boolean =
    a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

private fun binPeakYOverlapHeight(a: android.graphics.Rect, b: android.graphics.Rect): Int {
    val interTop = maxOf(a.top, b.top)
    val interBottom = minOf(a.bottom, b.bottom)
    return maxOf(0, interBottom - interTop)
}

/** Per-red object-based blue: components intersecting red, then union of all comps with Y-overlap to those seeds. */
private fun binPeakComputeBlueRectsPerRed(
    redRects: List<android.graphics.Rect>,
    compRects: List<android.graphics.Rect>
): List<android.graphics.Rect> {
    return redRects.mapNotNull { red ->
        val intersecting = compRects.filter { binPeakRectsIntersect(it, red) }
        if (intersecting.isEmpty()) return@mapNotNull null
        val associated = compRects.filter { comp ->
            intersecting.any { seed -> binPeakYOverlapHeight(comp, seed) > 0 }
        }
        if (associated.isEmpty()) return@mapNotNull null
        android.graphics.Rect(
            associated.minOf { it.left },
            associated.minOf { it.top },
            associated.maxOf { it.right },
            associated.maxOf { it.bottom }
        )
    }
}

/** vSW/hSW from run-length histogram on red areas of binarized image (native calculateHistogramWithThresholdH). */
private fun binPeakComputeStrokeWidths(
    binMat: org.opencv.core.Mat,
    redRects: List<android.graphics.Rect>
): Pair<Float, Float> {
    if (redRects.isEmpty()) return -1f to -1f
    val hRes = NativeImageUtils.calculateHistogramWithThresholdH(binMat, redRects, 128f) ?: return -1f to -1f
    val vSW = hRes.second.getOrNull(0)?.toFloat() ?: -1f
    val hSW = hRes.second.getOrNull(1)?.toFloat() ?: -1f
    return vSW to hSW
}

/** Shrink full blue union rect to 40px tall (centered) for OCR crop; 4px offset applied in rec buffer. */
private fun shrinkBlueRectForOcr(fullBlue: android.graphics.Rect, imgW: Int, imgH: Int): android.graphics.Rect {
    val targetH = 40
    val cy = (fullBlue.top + fullBlue.bottom) / 2
    var nt = (cy - targetH / 2).coerceIn(0, imgH - 1)
    var nb = (nt + targetH).coerceIn(nt + 1, imgH)
    if (nb - nt < targetH) nt = (nb - targetH).coerceAtLeast(0)
    val nl = fullBlue.left.coerceIn(0, imgW - 1)
    val nr = fullBlue.right.coerceIn(nl + 1, imgW)
    return android.graphics.Rect(nl, nt.coerceIn(0, imgH - 1), nr, nb.coerceIn(nt + 1, imgH))
}

private suspend fun ocrBinPeakRectsAsisAndDigits(
    workspace: BufferSet,
    binSlice: BufferSet.Slice,
    paddleEngine: NativePaddleEngine,
    recBuffer: BufferSet,
    rects: List<android.graphics.Rect>
): PumpRectOcrLists {
    val imgW = binSlice.width; val imgH = binSlice.height
    val asisPairs = rects.map { r ->
        val pW = r.width(); val pH = r.height()
        if (pW < 2 || pH < 2) "?" to "" else {
            val l = r.left.coerceIn(0, imgW - 1)
            val t = r.top.coerceIn(0, imgH - 1)
            val rr = r.right.coerceIn(l + 1, imgW)
            val bb = r.bottom.coerceIn(t + 1, imgH)
            val cropId = binSlice.createCrop(l, t, rr - l, bb - t)
            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
            recBuffer.p.clear()
            val recCropId = recBuffer.createCrop(4, 4, targetW, targetH)
            val interp = if (pW > targetW) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            Imgproc.resize(workspace.c[cropId].mat, recBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
            val res = paddleEngine.recognize(recBuffer.c[recCropId])
            recBuffer.c[recCropId].release(); workspace.c[cropId].release()
            res.debugText to (if (res.perCharProbs.isNotEmpty()) res.perCharProbs else "")
        }
    }
    val digitsPairs = rects.map { rp ->
        val pW = rp.width(); val pH = rp.height()
        if (pW < 2 || pH < 2) "?" to "" else {
            val l = rp.left.coerceIn(0, imgW - 1)
            val t = rp.top.coerceIn(0, imgH - 1)
            val rr = rp.right.coerceIn(l + 1, imgW)
            val bb = rp.bottom.coerceIn(t + 1, imgH)
            val cropId = binSlice.createCrop(l, t, rr - l, bb - t)
            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
            recBuffer.p.clear()
            val recCropId = recBuffer.createCrop(4, 4, targetW, targetH)
            val interp = if (pW > targetW) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            Imgproc.resize(workspace.c[cropId].mat, recBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
            val res = paddleEngine.recognizeNumericDecimal(recBuffer.c[recCropId])
            recBuffer.c[recCropId].release(); workspace.c[cropId].release()
            res.debugText to (if (res.perCharProbs.isNotEmpty()) res.perCharProbs else "")
        }
    }
    return PumpRectOcrLists(
        asis = asisPairs.map { it.first },
        digits = digitsPairs.map { it.first },
        asisProbs = asisPairs.map { it.second },
        digitsProbs = digitsPairs.map { it.second }
    )
}

/** P4 (1-bit packed PBM) base64 for binPeak plain/cleaned binary debug in JSON (alignment pattern). */
private fun matToPbmP4Base64(mat: org.opencv.core.Mat): String {
    val cols = mat.cols()
    val rows = mat.rows()
    val totalPixels = cols * rows
    val data = ByteArray(totalPixels)
    mat.get(0, 0, data)

    val packedSize = (totalPixels + 7) / 8
    val packed = ByteArray(packedSize)

    var byteIdx = 0
    var bitIdx = 0
    var currentByte = 0

    for (i in 0 until totalPixels) {
        val pixelVal = data[i].toInt() and 0xFF
        val bit = if (pixelVal <= 127) 1 else 0
        currentByte = (currentByte shl 1) or bit
        bitIdx++
        if (bitIdx == 8) {
            packed[byteIdx++] = currentByte.toByte()
            currentByte = 0
            bitIdx = 0
        }
    }
    if (bitIdx > 0) {
        currentByte = currentByte shl (8 - bitIdx)
        packed[byteIdx++] = currentByte.toByte()
    }

    val header = "P4\n$cols $rows\n".toByteArray(Charsets.US_ASCII)
    val fullData = ByteArray(header.size + packed.size)
    System.arraycopy(header, 0, fullData, 0, header.size)
    System.arraycopy(packed, 0, fullData, header.size, packed.size)

    val result = Base64.encodeToString(fullData, Base64.NO_WRAP)
    // P4 internal buffers released after base64 extraction — source mat data no longer needed for this peak; workspace.s reused by next iteration in caller
    data.fill(0)
    packed.fill(0)
    fullData.fill(0)
    return result
}

private fun componentStatsToJson(stats: List<NativeImageUtils.ComponentStats>): String {
    val arr = JSONArray()
    stats.forEach { s ->
        val obj = JSONObject()
        obj.put("index", s.index)
        obj.put("left", s.left)
        obj.put("top", s.top)
        obj.put("width", s.width)
        obj.put("height", s.height)
        obj.put("area", s.area)
        arr.put(obj)
    }
    return arr.toString()
}

/** Annotated binPeak snapshot: binarized mat + red rects + full blue union rects overlaid (JPEG base64). */
private suspend fun takeBinPeakAnnotatedSnapshot(
    binMat: org.opencv.core.Mat,
    redRects: List<android.graphics.Rect>,
    blueRects: List<android.graphics.Rect>
): String {
    val anns = mutableListOf<SnapshotAnnotation>()
    redRects.forEach { r ->
        anns.add(SnapshotAnnotation(r.left, r.top, r.right, r.bottom, Shape.RECTANGLE, Color.RED, 2))
    }
    blueRects.forEach { r ->
        anns.add(SnapshotAnnotation(r.left, r.top, r.right, r.bottom, Shape.RECTANGLE, Color.BLUE, 4))
    }
    return OcrUtils.takeSnapshot(binMat, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, anns, null, null).first
}

/** Extract brightness peaks (0-255) + union bar heights from combinedRedboxHistBins for binPeak debug images (expanded B/C/F only; calculated D/E/G skip capture entirely). Valley-push quantized hists (<=10 positive bins): return all exact 1-bin positive peaks, no smoothing. Raw/others: findPeakBinsFromHistogram + positive-count filter. */
private fun findPeaksFromHistBins(combinedBinsJson: String): List<Pair<Int, Int>> {
    val binsArr = JSONArray(combinedBinsJson)
    val bins = FloatArray(64) { j -> binsArr.getDouble(j).toFloat() }
    val numNz = (0..63).count { bins[it] > 0f }
    if (numNz <= 10) {
        return (0..63).filter { bins[it] > 0f }
            .map { j -> (j * 4 + 2).coerceIn(0, 255) to bins[j].toInt() }
            .sortedByDescending { it.second }
    }
    val peakBins = OdometerOcrUtils.findPeakBinsFromHistogram(bins)
    return peakBins
        .map { j -> (j * 4 + 2).coerceIn(0, 255) to bins[j].toInt() }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
}

private suspend fun captureBinPeakSnapshotsFromRedbox(
    branch: PumpBranch,
    workspace: BufferSet,
    redRects: List<android.graphics.Rect>,
    paddleEngine: NativePaddleEngine,
    recBuffer: BufferSet,
    imgW: Int,
    imgH: Int,
    candidatesOut: MutableList<RedBoxOcrCandidate>,
    delta: Int = BIN_PEAK_BINARIZE_DELTA
) {
    val combinedBinsJson = branch.metadata["combinedRedboxHistBins"] ?: return
    val binsArr = JSONArray(combinedBinsJson)
    val bins = FloatArray(64) { j -> binsArr.getDouble(j).toFloat() }
    val numNz = (0..63).count { bins[it] > 0f }
    val peaks = findPeaksFromHistBins(combinedBinsJson)
    val d = if (numNz <= 10) 0 else delta
    for ((peak, height) in peaks) {
        val b = workspace.s
        b.mat.setTo(org.opencv.core.Scalar(0.0))
        // Valley (numNz<=10): LUT stretching places mass anywhere in the bin; binarize full 1-bin width, not center±d.
        val (low, high) = if (numNz <= 10) {
            val j = (peak / 4).coerceIn(0, 63)
            (j * 4) to ((j + 1) * 4 - 1)
        } else {
            (peak - d) to (peak + d)
        }
        NativeImageUtils.binarizeRange(workspace.p.mat, b.mat, low, high)
        branch.metadata["binPeak_${peak}_plain_objects"] = componentStatsToJson(NativeImageUtils.getComponentStats(b.mat))
        // Direct Mat snapshot (not Slice/YUV path); null scratchYuv so we do not clear the b we just binarized into
        val binB64 = OcrUtils.takeSnapshot(b.mat, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, emptyList(), null, null).first
        branch.images["binPeak_$peak"] = binB64
        branch.metadata["binPeak_${peak}_count"] = height.toString()
        // Plain binary debug: P4 + full CC object stats (JSON inspection only; base64 string retained for output; internal P4 buffer released immediately for reuse on next peak per this plan)
        Log.d(TAG, "P4 snapshot would have stored here: plain peak=$peak")
        // P4 snapshots commented per plan; all other binpeak debug + object-based logic left active
        // branch.images["binPeak_${peak}_plain_p4"] = matToPbmP4Base64(b.mat)
        // P4 buffer (b.mat) cleared after base64 + objects stored in JSON path; reusable for cleaned path or next peak
        b.mat.setTo(org.opencv.core.Scalar(0.0))
        NativeImageUtils.binarizeRange(workspace.p.mat, b.mat, low, high)

        val (vSW, hSW) = binPeakComputeStrokeWidths(b.mat, redRects)
        if (vSW > 0f && hSW > 0f && redRects.isNotEmpty()) {
            val compRectsUncleaned = NativeImageUtils.findAllComponentsH(b.mat, vSW, hSW)
            val blueRectsUncleaned = binPeakComputeBlueRectsPerRed(redRects, compRectsUncleaned)
            val ocrRectsUncleaned = blueRectsUncleaned.map { shrinkBlueRectForOcr(it, imgW, imgH) }
            val ocrUncleaned = ocrBinPeakRectsAsisAndDigits(workspace, b, paddleEngine, recBuffer, ocrRectsUncleaned)
            blueRectsUncleaned.forEachIndexed { redK, fullBlue ->
                val label = "Peak${peak}-uncleaned-Red${redK + 1}"
                candidatesOut.add(RedBoxOcrCandidate(
                    label,
                    ocrUncleaned.asis.getOrElse(redK) { "?" },
                    ocrUncleaned.digits.getOrElse(redK) { "?" },
                    ocrUncleaned.asisProbs.getOrElse(redK) { "" },
                    ocrUncleaned.digitsProbs.getOrElse(redK) { "" },
                    fullBlue
                ))
            }

            // Buffer-width percentage (0.20 of the binarized image width) for pump binPeak image-wide cleaning.
            val useMaxW = 0.20f * b.mat.cols()
            val editedIndices = NativeImageUtils.blackOutLargeAndSmallComponentsHWithEditedIndices(b.mat, vSW, hSW, useMaxW)
            branch.metadata["binPeak_${peak}_edited_object_indices"] = JSONArray(editedIndices.toList()).toString()
            Log.d(TAG, "P4 snapshot would have stored here: cleaned peak=$peak")
            // branch.images["binPeak_${peak}_cleaned_p4"] = matToPbmP4Base64(b.mat)
            branch.metadata["binPeak_${peak}_cleaned_objects"] = componentStatsToJson(NativeImageUtils.getComponentStats(b.mat))
            val compRectsCleaned = NativeImageUtils.findAllComponentsH(b.mat, vSW, hSW)
            val blueRectsCleaned = binPeakComputeBlueRectsPerRed(redRects, compRectsCleaned)
            val ocrRectsCleaned = blueRectsCleaned.map { shrinkBlueRectForOcr(it, imgW, imgH) }
            val ocrCleaned = ocrBinPeakRectsAsisAndDigits(workspace, b, paddleEngine, recBuffer, ocrRectsCleaned)
            blueRectsCleaned.forEachIndexed { redK, fullBlue ->
                val label = "Peak${peak}-cleaned-Red${redK + 1}"
                candidatesOut.add(RedBoxOcrCandidate(
                    label,
                    ocrCleaned.asis.getOrElse(redK) { "?" },
                    ocrCleaned.digits.getOrElse(redK) { "?" },
                    ocrCleaned.asisProbs.getOrElse(redK) { "" },
                    ocrCleaned.digitsProbs.getOrElse(redK) { "" },
                    fullBlue
                ))
            }
            // P4 buffer (b.mat) cleared after cleaned base64 stored in JSON path; reusable for next peak
            b.mat.setTo(org.opencv.core.Scalar(0.0))
        }
    }
}

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

    // Display-only: ignore bins 0 and 63 for scaling/readability (binPeak selection scans all 64 combined bins via findPeaksFromHistBins but filters to positive-count bins).
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

private fun parseBinPeakKeyToPeakNum(key: String): Int? {
    if (!key.startsWith("binPeak_")) return null
    val rest = key.removePrefix("binPeak_")
    rest.toIntOrNull()?.let { return it }
    if (rest.endsWith("_uncleaned")) return rest.removeSuffix("_uncleaned").toIntOrNull()
    if (rest.endsWith("_cleaned")) return rest.removeSuffix("_cleaned").toIntOrNull()
    return null
}

private fun buildBinPeakHtmlForBranch(flowName: String, br: PumpBranch): String {
    val blueMethodPrefixes = listOf("Set B", "Set C", "Set D", "Set E", "Set F", "Set G")
    if (blueMethodPrefixes.none { flowName.startsWith(it) }) return ""
    val peakNums = br.images.keys.mapNotNull { parseBinPeakKeyToPeakNum(it) }.distinct()
    if (peakNums.isEmpty()) return ""
    val sorted = peakNums.map { peak ->
        peak to (br.metadata["binPeak_${peak}_count"]?.toIntOrNull() ?: 0)
    }.sortedByDescending { it.second }
    return buildString {
        append("<br><div style='margin-top:4px;'><b>Binarized by redbox peaks (range ±$BIN_PEAK_BINARIZE_DELTA, highest peak height to lowest):</b></div>")
        sorted.forEach { (peak, height) ->
            br.images["binPeak_$peak"]?.let { b64 ->
                append("<img src='data:image/jpeg;base64,$b64' style='max-width:100%;'>")
                append("<br><small>peak=$peak plain (union bar height: $height px)</small><br>")
            }
        }
    }
}

private const val PUMP_PD_TARGET_W = 340
private const val PUMP_PD_TARGET_H = 255
private const val PUMP_CROP_TARGET_W = 150
private const val PUMP_CROP_TARGET_H = 75
private const val PUMP_C_VISUAL_TARGET_W = 340
private const val PUMP_SMALL_TARGET_W = 180
private const val PUMP_PER_RED_TARGET_W = 120
private const val BIN_PEAK_BINARIZE_DELTA = 8
private const val PUMP_JSON_BUFFER_INITIAL_BYTES = 256 * 1024 * 1024
private const val PUMP_JSON_BUFFER_RESET_CAPACITY = 512 * 1024 * 1024

private fun pBuildHtmlHeader(time: String, total: Int, version: String, flows: List<String>): String = buildString {
    appendLine("<html><head><title>Pump Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .res-table { width: 100%; border: none; font-size: 20px; } .res-table th { background: #f0f0f0; }</style></head><body>")
    appendLine("<h1>Pump Extraction Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th>")
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
        .joinToString(" | ") { (name, br) -> "$name: ${br.metadata["tilt"] ?: "?"}°" }
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$rowHtml</small>$diagHtml<br><span style=\"font-size:6px\"><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt per set:</b> $perSetTilts<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["before"]}'><br><small>Orig</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist1"]}'><br><small>Hist 1</small></td></tr><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["after"]}'><br><small>Stretch</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist2"]}'><br><small>Hist 2</small></td></tr><tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:6px;'><small>$deskewHtml</small></td></tr></table></span></td>")

    val hasML = root.subBranches.filter { (_, br) -> br.images.containsKey("ML") && br.images["ML"]?.isNotEmpty() == true }.keys.toSet()  // data-driven from subBranches presence, no name if
    root.subBranches.toSortedMap().forEach { (name, br) ->
        if (name in hasML) {
            appendLine("<td><b>$name ML:</b><br><img src='data:image/jpeg;base64,${br.images["ML"]}'></td>")
        }
        val pdB64 = br.images["PD"] ?: ""
        val binPeakHtml = buildBinPeakHtmlForBranch(name, br)
        if (br.images.containsKey("PD_red_only")) {
            // red-only + full PD pair (when branch populates the key from explicit helper call)
            val redOnly = br.images["PD_red_only"] ?: ""
            val full = br.images["PD"] ?: ""
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$redOnly' style='max-width:100%;'><br><small>Red boxes only (after filter)</small><br><img src='data:image/jpeg;base64,$full' style='max-width:100%;'><br><small>All annotations (red+blue+orange) as before</small>$binPeakHtml</td>")
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
            appendLine("<td><b>$name Paddle:</b><br><table style='width:100%; border:none; font-size:11px;'><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$raw' style='max-width:100%;'><br><small>Raw</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$pushed' style='max-width:100%;'><br><small>Valley-Pushed (few brightness vals)</small></td></tr><tr><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hB' style='max-width:100%;'><br><small>Before</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$hA' style='max-width:100%;'><br><small>After</small></td></tr></table>$perRedHtml<img src='data:image/jpeg;base64,$pdB64'>$binPeakHtml</td>")
        } else {
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$pdB64'>$binPeakHtml</td>")
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


private suspend fun runDiscoveryPaddle(
    buffer: BufferSet,
    id: Int,
    paddleEngine: NativePaddleEngine,
    contentW: Int,
    contentH: Int,
    scale: Int,
    metadata: MutableMap<String, String>? = null
): List<List<PumpHunk>> {
    val res = paddleEngine.detect(buffer.c[id]) ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    if (metadata != null) {
        metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
        metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
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

    // Redbox improvement from Set J (alignment experiment) - first item per user directive.
    // Move sides of detected box out by 1 pixel in low-res (this crop/detect-input space) before
    // the explicit upscale to full (and before doing anything more: consolidate, native expand, hunks).
    // Then remove nested red boxes (inset contains filter, matching alignment tRawB logic in runBinTrialsPaddle).
    //
    // Pump note (variable scale vs fixed in alignment): scaleFactor computed in caller scales.forEach
    // (currentLongEdge vs target/scale + prepareScale 32-align outer/inner + process 1.0f on crop).
    // Explicit upscale (full/content ratio) applied once here to produce full photo pixel rects for PumpHunk.rect.
    // No ICRS roundtrip or erosion chain in the pd path.
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
    // show the individual +1 expanded and de-nested detections. Explicit upscale (full/content) once for full photo pixels.
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
        canvas.drawRect(hunk.rect.left, hunk.rect.top, hunk.rect.right, hunk.rect.bottom, paint)
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


                    // h/w/area kept from rect; collection to redboxDataC / redboxHistC_* / metadata unchanged.
                    val redboxDataC = JSONArray()