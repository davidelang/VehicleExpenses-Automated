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
    // Set C: pump-only (copy of Set B) but uses alignment Set J bin-test (histogram valleys for multiple binarizations instead of stretch); each version gets full detect/redbox (incl. nesting filter); versions shown stacked in Set C column.
    val flows = listOf("Set A", "Set B", "Set C")

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
            // The processor bodies (linear "what to do for this path", no internal flowName ifs) will be filled
            // and the old tangled body below will be removed in subsequent phases. Temp: old body still runs
            // so behavior is unchanged during the transition builds.
            flows.forEachIndexed { i, flowName ->
                // (original per-flow setup follows; the call to the processor for this i will be placed after the
                // flowProcessors list definition later in this per-flow body, so the array reference resolves and
                // the C processor (with valley) runs after setup and after its own def in source. This activates
                // the array-of-functions iteration per the clarification (no hard-coded per-set function names at
                // call sites; just index into the array). Old body remains temp during transition.)
                val branch = root.getBranch(flowName)
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

                // 1. Transform
                // C now uses the same histogram/automaticContrastStretch as B (per "go back to the same histogram that B uses"; removed special-case skip for C).
                val rawHist = OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
                if (flowName == flows.first() && flowName != "Set C") {
                    originalHistogram = JSONArray().apply { rawHist.forEach { put(it.toDouble()) } }
                    root.images["after"] = OcrUtils.takeSnapshot(workspace.p, null, 225, 0, emptyList(), null, workspace).first
                    root.images["hist2"] = generateHistogramB64(workspace.p.mat, 0.40f)
                }

                // 2. Deskew (ported Set E style from alignment for Set B; uses dedicated populate + JNI angle path)
                // Compute per-flow but select angle source based on flow. Set B (pump-only) uses paddleCppAngle (from the optimized/JNI path inside calculateAverageTextAngle).
                // Set C uses negated paddleCppAngle (user: "deskew rotation of set C seems to be rotating the wrong direction").
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(workspace.p)
                val tilt = when (flowName) {
                    "Set B" -> deskewRes.paddleCppAngle
                    "Set C" -> -deskewRes.paddleCppAngle
                    else -> deskewRes.angle
                }

                // Use shared modern rotate (UV handling, parity with alignment improvements). Local pRotate removed.
                OdometerOcrUtils.rotate(workspace, tilt)
                branch.metadata["tilt"] = "%.2f".format(tilt)

                // Hoisted decls (Phase 1 small step of approved refactor plan): declared before the local helper funs
                // (stackVertically, runPaddleDiscovery) that close over them (and before the inline discovery).
                // This resolves forward-ref compile issues for 'scales', the pd*Totals, mlBlocksRaw etc that the
                // helpers reference. (The processedScales for the inline remains at its site for now.)
                val scales = listOf(224, 608, 1024, 2560)
                val mlBlocksRaw = if (flowName == "Set B") mutableListOf<PumpHunk>() else mutableListOf<PumpHunk>()
                val pdHunksRawTotal = mutableListOf<PumpHunk>()
                val pdHunksExpTotal = mutableListOf<PumpHunk>()
                val pdHunksMaxTotal = mutableListOf<PumpHunk>()
                val pdHunksNativeTotal = mutableListOf<PumpHunk>()

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

                        if (flowName != "Set B" && flowName != "Set C") {
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

                fun doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
                    if (pdHunksRawTotal.isNotEmpty()) {
                        val kept = pdHunksRawTotal.filter { h1 ->
                            val p1 = IcrsMath.icrsToPixel(h1.icrs.left, h1.icrs.top, imgW, imgH)
                            val p2 = IcrsMath.icrsToPixel(h1.icrs.right, h1.icrs.bottom, imgW, imgH)
                            val r1 = android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                            pdHunksRawTotal.none { h2 ->
                                h1 !== h2 && run {
                                    val op1 = IcrsMath.icrsToPixel(h2.icrs.left, h2.icrs.top, imgW, imgH)
                                    val op2 = IcrsMath.icrsToPixel(h2.icrs.right, h2.icrs.bottom, imgW, imgH)
                                    val r2 = android.graphics.Rect(op1.x.toInt(), op1.y.toInt(), op2.x.toInt(), op2.y.toInt())
                                    // Small inset tolerance in final pixel space (cross-scale nesting can appear after mapping).
                                    // Remove only boxes that are entirely contained.
                                    r2.contains(r1.left + 2, r1.top + 2, r1.right - 2, r1.bottom - 2)
                                }
                            }
                        }
                        pdHunksRawTotal.clear()
                        pdHunksRawTotal.addAll(kept)
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
                    /* bin-trials removed 2026-06-11 per user directive ("remove the bin-trials from set C and go back to the same histogram that B uses").
                       C now:
                       - uses same automaticContrastStretch histogram as B (rawHist change)
                       - gets red-box-hist polarity probe + invert (if dark text on light) inserted in common per-flow scope before discovery (forces light text on dark)
                       - deskew tilt is negated for C (common)
                       - uses the normal discovery body (after guard change below) + PD/path like B
                       The old valley (gray + per-midpoint binarize + per-version stack + best) is gone; no more multiple thresh images for C.
                    */
                }

                // Phase 2 of approved refactor plan: the array of processor functions (one per flow, in same order as
                // the flows list) that we iterate over (forEachIndexed or zip). Each is a self-contained lambda whose
                // body is the linear list of steps for that path (no if(flowName) inside). Common setup (ws copy,
                // discoveryDetails map) happens at the dispatch site; processors receive ws/br/det/w/h and populate
                // only their branch (images, pathResults, metadata["tilt"]). Old tangled forEach body remains
                // temporarily (will be removed as logic is moved into the processors in subsequent phases).
                // Set C valley (bin-test) will be fully implemented in its processor (Phase 3).
                // Red-box-hist polarity fix for Set C only (after tilt/rotate, before processors/body discovery; uses runPaddleDiscovery probe which is now defined).
                // Looks at 64-bin hist *only inside the initial red boxes* (text regions) on the (deskewed, same-hist-as-B stretched) mat to decide dark text on light bg vs light on dark.
                // If dark text, inverts the mat (bitwise_not) so subsequent detection/rec + PD snapshot for C always see light text on dark bg.
                if (flowName == "Set C") {
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                    runPaddleDiscovery()  // probe to populate initial reds on current mat state

                    // Build mask (255 inside red boxes, pixel space) -- exact pattern from prior valley probe.
                    val mask = org.opencv.core.Mat.zeros(workspace.p.mat.size(), org.opencv.core.CvType.CV_8UC1)
                    for (hunk in pdHunksRawTotal) {
                        val p1 = IcrsMath.icrsToPixel(hunk.icrs.left, hunk.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(hunk.icrs.right, hunk.icrs.bottom, imgW, imgH)
                        val rect = org.opencv.core.Rect(p1.x.toInt(), p1.y.toInt(), (p2.x - p1.x).toInt(), (p2.y - p1.y).toInt())
                        org.opencv.imgproc.Imgproc.rectangle(mask, rect, org.opencv.core.Scalar(255.0), -1)
                    }

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

                    // Re-clear so the (now-unskipped for C) body discovery populates the *final* pd* on the (possibly inverted) mat.
                    pdHunksRawTotal.clear()
                    pdHunksExpTotal.clear()
                    pdHunksMaxTotal.clear()
                    pdHunksNativeTotal.clear()
                }

                val procA: (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    // linear steps (no conditionals on set):
                    // - automaticContrastStretch + (A is first) root after/hist2/originalHistogram snaps
                    // - standard tilt = deskewRes.angle; rotate; br.metadata["tilt"] = ...
                    // - its mlBlocksRaw + pd* totals
                    // - ml + pd discovery (via runPaddleDiscovery / inline scales for now)
                    // - global filter (via doCross...)
                    // - mlHunks + pdMerged; getFinal (shared) for both -> pathResults ML + Paddle
                    // - viz: ML image + PD raw reds snapshot
                }
                val procB: (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    // linear for B:
                    // - no stretch
                    // - paddleCpp tilt (from deskewRes); rotate; tilt meta
                    // - pd totals only (mlBlocksRaw empty or guarded)
                    // - pd discovery (runPaddleDiscovery)
                    // - filter
                    // - only pdMerged + getFinal for "Paddle" path
                    // - only PD viz (raw reds)
                }
                val procC: (BufferSet, PumpBranch, MutableMap<String, MutableMap<Int, List<PumpHunk>>>, Int, Int) -> Unit = { ws: BufferSet, br: PumpBranch, det: MutableMap<String, MutableMap<Int, List<PumpHunk>>>, w: Int, h: Int ->
                    // C no longer uses dedicated valley (bin-trials removed). Polarity invert probe + common deskew/hist already applied; body discovery (now unskipped for C) + PD/path do the rest. Empty to keep array dispatch happy.
                }
                val flowProcessors = listOf(procA, procB, procC)

                // Call the processor for this flow (i) from the array. Placed after the flowProcessors list val
                // in source (name resolves) and after per-flow setup in execution. For Set C (i==2) this invokes
                // the dedicated processor lambda containing the full valley bin-test logic (no hard-coded "Set C"
                // checks inside the per-path code itself -- the array + index is how we select/iterate the
                // function per the clarification, avoiding ugly name hard-coding at call sites or inside paths).
                // The C processor sets br.images["PD"] to the stacked composite (multiple binarized versions +
                // their red raw boxes post +1/nest filters) and br.pathResults["Paddle"] to the best version's.
                // Old body continues (normal discovery runs for C on restored mat; viz if-C skips PD overwrite;
                // path set guarded below to protect processor result). Temp during transition; old body to be
                // removed when array fully replaces the tangle.
                flowProcessors[i](workspace, branch, discoveryDetails, imgW, imgH)

                if (flowName == "Set C_old_bin_trials") {
                    // (bin-trials removed per 2026-06-11 directive; C now executes normal body (probe invert + negated tilt + B-like hist already applied upstream)
                    // old long comment left for reference but block now skipped for C

                    // per-valley binarized images, each with "thresh = XX" label + reds from detection on that version).
                    // Skip the following old duplicated // 3. Discovery (the inline scales.forEach etc.) and later
                    // unconditional path/viz blocks for the normal pdHunks so they do not execute for C. This lets
                    // the processor's composite "win" and prevents interference with the mat state or pdHunks the
                    // processor used for best tracking. The old code for C is now dead for this turn (to be removed
                    // in a later phase when the tangle is fully replaced by the array of processors).
                } else {
                    // stackVertically hoisted earlier (before flowProcessors list) for name resolution inside the
                    // C processor lambda body (the array entry for Set C contains the valley that calls it).


                    // runPaddleDiscovery hoisted earlier (before flowProcessors list) so it is visible inside the
                    // C processor lambda (the per-path valley for Set C calls it on each binarized version).

                    // 3. Discovery (decls for scales/ml/pd* hoisted earlier in Phase 1 for local helper closure visibility
                    // and to resolve compile forward refs; see the block after tilt metadata. The inline processedScales
                    // remains local to this forEach.)
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

                val mlHunks = if (flowName == "Set B" || flowName == "Set C") emptyList<PumpHunk>() else mergeGeometryIntoHunks(mlBlocksRaw)
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
                // (and the name check) will be removed when the old tangled body is deleted and the array fully
                // drives the flows (avoiding hard-coded names in the main logic).
                // C now gets its Paddle result from the body (like B); the old != "Set C" guard was only to protect valley-set result.
                branch.pathResults["Paddle"] = getFinal(pdHunksMerged, "Paddle", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                if (flowName != "Set B" && flowName != "Set C") {
                    branch.pathResults["ML"] = getFinal(mlHunks, "ML Kit", tilt, pdHunksRawTotal, workspace, experimentRecSet320x48, paddleEngine, context, imgW, imgH)
                }

                // 5. Visualization
                fun getAnns(list: List<PumpHunk>, color: Int, width: Int) = list.map { h ->
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH); val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, color, width)
                }

                if (flowName != "Set B" && flowName != "Set C") {
                    val aMl = getAnns(mlBlocksRaw, Color.RED, 2) + getAnns(mlHunks, Color.rgb(255, 165, 0), 4)
                    branch.images["ML"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aMl, null, workspace).first
                }
                if (flowName == "Set B") {
                    // add back blue (exp) + orange (max) annotations for Set B (per user directive)
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2) + getAnns(pdHunksExpTotal, Color.BLUE, 4) + getAnns(pdHunksMaxTotal, Color.rgb(255, 165, 0), 2)
                    val baseB64 = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first

                    // run ocr recognize on *every* blue and *every* orange box; scale to 48px tall buffer with width multiple of 32 (for the recognition)
                    // (inline crop/resize/rec using experimentRecSet320x48 and direct paddle rec, modeled on performHunkRecognition but forcing %32 width)
                    val blueTexts = pdHunksExpTotal.map { h ->
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val r = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH); val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, r - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognize(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeTexts = pdHunksMaxTotal.map { h ->
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val r = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH); val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, r - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognize(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }

                    // digits-only (0-9) pass using recognizeNumeric for the second OCR per box (as-is above + digits)
                    val blueDigits = pdHunksExpTotal.map { h ->
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val r = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH); val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, r - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumeric(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeDigits = pdHunksMaxTotal.map { h ->
                        val l = h.icrs.left.coerceIn(-maxX, maxX - 0.001f)
                        val t = h.icrs.top.coerceIn(-maxY, maxY - 0.001f)
                        val r = h.icrs.right.coerceIn(l + 0.001f, maxX)
                        val b = h.icrs.bottom.coerceIn(t + 0.001f, maxY)
                        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH); val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
                        val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
                        if (pW < 2 || pH < 2) "?" else {
                            val cropId = workspace.createCrop(l, t, r - l, b - t)
                            val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                            val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumeric(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }

                    // HTML text rows under the image (one row per box, as-is + digits separately). Store in metadata for pBuild to append after <img> (not baked in the PD image itself).
                    val ocrLinesB = mutableListOf<String>()
                    blueTexts.forEachIndexed { i, asis -> ocrLinesB += "Blue ${i+1} as-is: $asis &nbsp;&nbsp; digits: ${blueDigits[i]}" }
                    orangeTexts.forEachIndexed { i, asis -> ocrLinesB += "Orange ${i+1} as-is: $asis &nbsp;&nbsp; digits: ${orangeDigits[i]}" }
                    branch.metadata["pd_ocr_html"] = ocrLinesB.joinToString("<br>")
                    branch.images["PD"] = baseB64  // annotated image with rects only (no under text)
                } else if (flowName == "Set C") {
                    // Set C: derive blue/orange from raw hunks using per-red overlap + Y-range horizontal extend + exact dedup (per user clarifications; no overlap/nesting tests, only exact box matches from same objects).
                    val hunks = pdHunksRawTotal.toList()  // the "objects"/hunks (raw after cross filter)
                    val blueRects = mutableListOf<RectF>()
                    for (red in hunks) {
                        val overlapping = hunks.filter { other ->
                            !(other.icrs.right < red.icrs.left || other.icrs.left > red.icrs.right || other.icrs.bottom < red.icrs.top || other.icrs.top > red.icrs.bottom)
                        }
                        if (overlapping.isNotEmpty()) {
                            val l = overlapping.minOf { it.icrs.left }
                            val t = overlapping.minOf { it.icrs.top }
                            val r = overlapping.maxOf { it.icrs.right }
                            val b = overlapping.maxOf { it.icrs.bottom }
                            blueRects.add(RectF(l, t, r, b))
                        }
                    }
                    val orangeRects = mutableListOf<RectF>()
                    for (blue in blueRects) {
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
                    // dedup orange by exact rect match (same objects inside -> same summed box)
                    val dedupedOrange = mutableListOf<RectF>()
                    for (o in orangeRects) {
                        if (dedupedOrange.none { d -> d.left == o.left && d.top == o.top && d.right == o.right && d.bottom == o.bottom }) dedupedOrange.add(o)
                    }
                    // anns: red + 1px white around each hunk (to show hunk bounds) + blue + orange
                    val redAnns = getAnns(pdHunksRawTotal, Color.RED, 2)
                    val whiteAnns = hunks.map { h ->
                        val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                        SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, Color.WHITE, 1)
                    }
                    val blueAnns = blueRects.map { r ->
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
                    val blueAsIs = blueRects.map { r ->
                        val hh = PumpHunk("", r)
                        performHunkRecognition(listOf(hh), workspace, experimentRecSet320x48, "Paddle", paddleEngine, context, tilt).firstOrNull()?.text ?: "?"
                    }
                    val blueDigits = blueRects.map { r ->
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
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumeric(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val orangeAsIs = dedupedOrange.map { r ->
                        val hh = PumpHunk("", r)
                        performHunkRecognition(listOf(hh), workspace, experimentRecSet320x48, "Paddle", paddleEngine, context, tilt).firstOrNull()?.text ?: "?"
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
                            experimentRecSet320x48.p.clear()
                            val recCropId = experimentRecSet320x48.createCrop(0, 0, targetW, targetH)
                            val interp = if (pW > targetW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                            org.opencv.imgproc.Imgproc.resize(workspace.c[cropId].mat, experimentRecSet320x48.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                            val res = paddleEngine.recognizeNumeric(experimentRecSet320x48.c[recCropId])
                            experimentRecSet320x48.c[recCropId].release(); workspace.c[cropId].release()
                            res.debugText
                        }
                    }
                    val ocrLinesC = mutableListOf<String>()
                    blueAsIs.forEachIndexed { i, a -> ocrLinesC += "Blue ${i+1} as-is: $a &nbsp;&nbsp; digits: ${blueDigits[i]}" }
                    orangeAsIs.forEachIndexed { i, a -> ocrLinesC += "Orange ${i+1} as-is: $a &nbsp;&nbsp; digits: ${orangeDigits[i]}" }
                    branch.metadata["pd_ocr_html"] = ocrLinesC.joinToString("<br>")
                } else {
                    // A (reds only)
                    val aPd = getAnns(pdHunksRawTotal, Color.RED, 2)
                    branch.images["PD"] = OcrUtils.takeSnapshot(workspace.p, null, 600, 450, aPd, null, workspace).first
                }
            }
            }  // close the else for old body (skipped for Set C so processor composite wins)

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


private fun generateHistogramB64(mat: org.opencv.core.Mat, floorPercentile: Float): String {
    if (mat.empty()) return ""
    val hist = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))

    val bins = FloatArray(64); hist.get(0, 0, bins)

    // 62px wide to exclude 0 and 63 bins
    val bmp = Bitmap.createBitmap(62, 100, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
    canvas.drawColor(Color.BLACK)
    val paint = Paint()

    // Ignore bins 0 and 63 for scaling to see the peaks clearly
    val maxVal = (1..62).maxOf { bins[it] }.toDouble().coerceAtLeast(1.0)

    for (i in 1..62) {
        val h = (bins[i] / maxVal * 80.0).toInt().coerceAtMost(80)
        val x = (i - 1).toFloat()
        paint.color = Color.WHITE; canvas.drawRect(x, (80 - h).toFloat(), x + 1f, 80f, paint)

        if (i % 8 == 0) { paint.color = Color.RED; canvas.drawRect(x, 82f, x + 1f, 90f, paint) }
        if (i == (floorPercentile * 63).toInt()) { paint.color = Color.YELLOW; canvas.drawRect(x, 82f, x + 1f, 90f, paint) }
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
    val metaHtml = root.subBranches.values.flatMap { it.metadata.entries }.joinToString("<br>") { (k, v) -> "<small>$k: $v</small>" }
    val rowHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty() || metaHtml.isNotEmpty()) "<br><small>Native: $diagnostic</small><br>$metaHtml" else ""
    val img = root.images

    val perSetTilts = root.subBranches.toSortedMap().entries
        .joinToString(" | ") { (name, br) -> "$name: ${br.metadata["tilt"] ?: "?"}°" }
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$rowHtml</small>$diagHtml<br><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt per set:</b> $perSetTilts<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["before"]}'><br><small>Orig</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist1"]}'><br><small>Hist 1</small></td></tr><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["after"]}'><br><small>Stretch</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${img["hist2"]}'><br><small>Hist 2</small></td></tr><tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:14px;'><small>$deskewHtml</small></td></tr></table></td>")

    root.subBranches.toSortedMap().forEach { (name, br) ->
        if (name != "Set B" && name != "Set C") {
            appendLine("<td><b>$name ML:</b><br><img src='data:image/jpeg;base64,${br.images["ML"]}'></td>")
        }
        val pdB64 = br.images["PD"] ?: ""
        val extraOcr = if ((name == "Set B" || name == "Set C") && br.metadata.containsKey("pd_ocr_html")) {
            "<br><div style='font-family:monospace; font-size:18px; text-align:left; background:#fafafa; padding:2px;'>" + br.metadata["pd_ocr_html"] + "</div>"
        } else ""
        appendLine("<td><b>$name Paddle:</b><br><img src='data:image/jpeg;base64,$pdB64'>$extraOcr</td>")
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
    val res = paddleEngine.detect(buffer.c[id]) ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList())

    val masterW = buffer.c[id].width; val masterH = buffer.c[id].height

    val rawBlocks = OdometerOcrUtils.processPaddleHeatmap(res.heatmap, res.width, res.height, 1.0f, buffer.c[id])
    val rawRects = rawBlocks.map { it.boundingBox }

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

    return listOf(hunksRaw, hunksExpanded, hunksMaxExtent, hunksNative)
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


