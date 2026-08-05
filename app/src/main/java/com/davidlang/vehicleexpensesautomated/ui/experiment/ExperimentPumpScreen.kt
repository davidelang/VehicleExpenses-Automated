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

private fun pruneRedPixelsTopN(rects: MutableList<Rect>, context: Context) {
    PumpCostVolUtils.pruneRectsToTopN(rects, PumpOcrSettings.maxRedBoxes(context))
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
            "availMem=${mi.availMem} threshold=${mi.threshold} lowMemory=${mi.lowMemory}"
    )
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
) {
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
    var autoStarted by remember { mutableStateOf(false) }

    val experimentDir = File(context.getExternalFilesDir(null), "pump_photos")
    experimentDir.mkdirs()
    val reportDir = File(context.getExternalFilesDir(null), "pump_reports")

    if (!reportDir.exists()) reportDir.mkdirs()

    val runFirst10: () -> Unit = {
        scope.launch {
            val allFiles = experimentDir.listFiles { f ->
                f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
            } ?: emptyArray()
            val first10Names = allFiles.sortedBy { it.name }.take(10).map { it.name }
            Log.d(TAG, "First 10 listFiles: dir=${experimentDir.absolutePath} count=${first10Names.size}")
            totalPhotos = first10Names.size
            isRunning = true
            resultsList.clear()
            runPumpExperiment(experimentDir, reportDir, context, { detailLog = it }, first10Names) { res, p ->
                resultsList.add(res); progress = p; currentPhotoName = res.photoName
            }
            isRunning = false
            status = "Complete! First 10 report saved."
        }
    }

    // Deep link: vehicleexpenses://experiment/pump?auto=first10
    LaunchedEffect(autoFirst10) {
        if (!autoFirst10 || autoStarted || isRunning) return@LaunchedEffect
        autoStarted = true
        Log.i(TAG, "autoFirst10 starting pump First 10")
        runFirst10()
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
        Button(onClick = { zipLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Extract Downloaded ZIP") }
        Button(onClick = {
            scope.launch {
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                Log.d(TAG, "Run Test listFiles: dir=${experimentDir.absolutePath} count=${allFiles.size}")
                totalPhotos = allFiles.size
                isRunning = true; resultsList.clear()
                runPumpExperiment(experimentDir, reportDir, context, { detailLog = it }, null) { res, p ->
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName
                }
                isRunning = false; status = "Complete! Reports saved."
            }
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
        Button(
            onClick = runFirst10,
            enabled = !isRunning && experimentDir.exists(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("First 10") }
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
    logHeapState(context, "runPumpExperiment:start")
    val allPhotos = experimentDir.listFiles { f ->
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
    }?.sortedBy { it.name } ?: return@withContext
    Log.d(TAG, "runPumpExperiment listFiles: dir=${experimentDir.absolutePath} count=${allPhotos.size}")

    val photos = if (subsetNames != null) {
        allPhotos.filter { it.name in subsetNames }
    } else allPhotos

    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val paddleEngine = NativePaddleEngine(context)

    val jsonFile = File(reportDir, "pump_results_$timestamp.json")
    val deviceModel = Build.MODEL
    val jsonHeader = "{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"device\": \"$deviceModel\",\n  \"total_photos\": $total,\n  \"results\": [\n"
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

    // Define flows for N-sets support
    // Configure experiment flows here. (See: docs/PUMP_EXPERIMENT_FLOWS.md for instructions)
    // Active sets: G--, I only — each column gets a fresh master copy. Processors bit-identical to multi-set era.
    // Retired (docs/obsolete/EXPERIMENT_PUMP_SETS.md + tag obsolete-experiment-pump-multi-sets):
    // A (ML+Paddle), B/C/F (binPeak), D, E, G, G-, H.
    // G--: SET_G_MINUS_MINUS_VERT_FACTORS k=4 [0.1,0.3,0.4,1.1]; Quick Fill production path.
    // Set I (D+E+G hybrid, calculated): deskew once, G (iGVert), clip + adjust p/v grays, D (iDVert), valley push, E (iEVert); one combined classify.
    // I Both stage lists: iGVert=[0.1,0.2,0.3,0.4,0.6,1.1,1.5] iDVert=[0.1,0.2] iEVert=[0.3,0.7] (cover 268+261).
    // Label convention: active sets use "Set X (stretch-type, blue-method)" self-describing columns.
    val flows = listOf(
        "Set G-- (4 pass, none, calculated)",
        "Set I (D+E+G hybrid, calculated)"
    )

    fun pStartNewFile(): File {
        val f = File(reportDir, "pump_report_${timestamp}_part${partCount++}.html")
        f.writeText(pBuildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, deviceModel, flows))
        return f
    }

    var currentFile = pStartNewFile()

    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${index + 1}/$total: ${file.name}") }

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
                            var nl = newL; var nr = newR; var nt = newT; var nb = newB
                            if (nl > nr) { val t = nl; nl = nr; nr = t }
                            if (nt > nb) { val t = nt; nt = nb; nb = t }
                            cur = android.graphics.Rect(nl, nt, nr, nb)
                        }
                    }
                    if (extended.none { it == cur }) extended.add(cur)
                }
                // final cleanup contains
                val cleaned = extended.filter { b ->
                    !extended.any { o -> o != b && o.contains(b) }
                }.filter { it.width() > 0 && it.height() > 0 }.toMutableList()
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
                                var nl = newL; var nr = newR; var nt = newT; var nb = newB
                                if (nl > nr) { val t = nl; nl = nr; nr = t }
                                if (nt > nb) { val t = nt; nt = nb; nb = t }
                                cur = PumpHunk(cur.text, RectF(nl, nt, nr, nb))
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
                    assemblyNote: String
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

                // Direct pixel from ingest (runDiscoveryPaddle explicit upscale); no roundtrip. Pixel filter + prune6 on rects; direct rebuild. Early probe sees full; post-prune 6 for all sets (C/E display only).
                val redPixelList = pdHunksRawTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(redPixelList)
                pruneRedPixelsTopN(redPixelList, context)
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
                pruneRedPixelsTopN(expPixel, context)
                pdHunksExpTotal.clear()
                pdHunksExpTotal.addAll(expPixel.map { r ->
                    PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                })
                val maxPixel = pdHunksMaxTotal.map { h ->
                    android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                }.toMutableList()
                doCrossScaleRedboxFilterPixel(maxPixel)
                pruneRedPixelsTopN(maxPixel, context)
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
                    pdHunksRawTotal, imgW, imgH, gVertFactors, 0.5f)
                val customBluePixelG = customBlueG.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val orangePixelG = customOrangeG.map { bh ->
                    android.graphics.Rect(bh.rect.left.toInt(), bh.rect.top.toInt(), bh.rect.right.toInt(), bh.rect.bottom.toInt())
                }
                val ocrG = ocrPumpRectsAsisAndDigits(customBluePixelG)
                val gCands = buildRedBoxCandidates(customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs, ocrG.digitsProbs)
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
                        "horizFactor" to 0.5f,
                        "orangeSideExt" to 0.1,
                        "note" to assemblyNote
                    ),
                    oranges = orangePixelG
                )
                doBOrDRedOnlyImage()
                val aPdG = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(customBlueG, Color.BLUE, 4) + getAnns(customOrangeG, Color.rgb(255, 165, 0), 2)
                val baseB64G = OcrUtils.takeSnapshot(workspace.p, null, PUMP_PD_TARGET_W, PUMP_PD_TARGET_H, aPdG, null, workspace).first
                branch.images["PD"] = baseB64G
            }
                val procGMinusMinus = makeGProc(SET_G_MINUS_MINUS_VERT_FACTORS, "G-- shared k=4 [0.1,0.3,0.4,1.1]; Quick Fill; phone loss 4 / emu 5")
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
                    pruneRedPixelsTopN(redPixelList, context)
                    pdHunksRawTotal.clear()
                    pdHunksRawTotal.addAll(redPixelList.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    })
                    val expPixel = pdHunksExpTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }.toMutableList()
                    doCrossScaleRedboxFilterPixel(expPixel)
                    pruneRedPixelsTopN(expPixel, context)
                    pdHunksExpTotal.clear()
                    pdHunksExpTotal.addAll(expPixel.map { r ->
                        PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    })
                    val maxPixel = pdHunksMaxTotal.map { h ->
                        android.graphics.Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
                    }.toMutableList()
                    doCrossScaleRedboxFilterPixel(maxPixel)
                    pruneRedPixelsTopN(maxPixel, context)
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
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
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
                val flowProcessors = listOf(
                    "Set G-- (4 pass, none, calculated)" to procGMinusMinus,
                    "Set I (D+E+G hybrid, calculated)" to procI,
                )
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
            withContext(Dispatchers.Main) { onProgress(resultSummary, (index + 1).toFloat() / total) }
            delay(50)

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
        .joinToString(" | ") { (name, br) -> "$name: ${br.metadata["tilt"] ?: "?"}°" }
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
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$redOnly' style='max-width:100%;'><br><small>Red boxes only (after filter)</small><br><img src='data:image/jpeg;base64,$full' style='max-width:100%;'><br><small>All annotations (red+blue+orange) as before</small></td>")
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
            appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$pdB64'></td>")
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

private fun JSONObject.pPutSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.pPutSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }


                    // h/w/area kept from rect; collection to redboxDataC / redboxHistC_* / metadata unchanged.
                    val redboxDataC = JSONArray()