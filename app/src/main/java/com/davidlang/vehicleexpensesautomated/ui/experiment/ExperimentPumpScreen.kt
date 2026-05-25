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

data class PumpReferenceCache(
    val vehicle: Vehicle, 
    val referenceBase64: String, 
    val curatedLandmarks: List<TextBlock>, 
    val bmp: Bitmap,
    val width: Int,
    val height: Int
)

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
    val paddleEngine = NativePaddleEngine(context, variant = "V3")

    val jsonFile = File(reportDir, "pump_results_$timestamp.json")
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"total_photos\": $total,\n  \"results\": [\n")
    
    var partCount = 1
    val maxSizeBytes = 2 * 1024 * 1024 // 2MB parts
    var currentSize = 0
    val footer = "</table></body></html>"
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentDetSet512x128 = BufferSet(512, 128)
    val harnessEngineNames = mutableListOf(
        "Set A ML", "Set A Paddle", 
        "Set B ML", "Set B Paddle"
    )

    fun pStartNewFile(): File {
        val f = File(reportDir, "pump_report_${timestamp}_part${partCount++}.html")
        f.writeText(pBuildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME))
        return f
    }

    var currentFile = pStartNewFile()
    
    photos.forEachIndexed { index, file ->
        // Phase 116 Emergency Fix: Initialize photoResult early with "No Match" state
        // to prevent serializePhotoResultToJson crashes on failed identification.
        var photoResult: ProcessedPhotoResult? = ProcessedPhotoResult(file.name, emptyMap(), emptyMap(), emptyMap())
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"

            try {
            withContext(Dispatchers.Main) { onLog("Processing ${index + 1}/$total: ${file.name}") }
            
            
            
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, NativePaddleEngine.bufferSetA.p)
            NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
            NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)

            // Capture BEFORE and generate Histogram
            val (beforeB64, tSnapOrig) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetA.p, null, 225, 0, emptyList(), null, NativePaddleEngine.bufferSetA)
            val histB64 = generateHistogramB64(NativePaddleEngine.bufferSetA.p.mat, 0.40f)
            val cdfB64 = generateCdfB64(NativePaddleEngine.bufferSetA.p.mat, 0.40f)

            // Step 1.5: 40% Contrast Stretch
            OdometerOcrUtils.applyContrastStretch(NativePaddleEngine.bufferSetA.p.mat, 0.40f)
            OdometerOcrUtils.applyContrastStretch(NativePaddleEngine.bufferSetB.p.mat, 0.40f)
            
            // Capture AFTER
            val (afterB64, _) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetA.p, null, 225, 0, emptyList(), null, NativePaddleEngine.bufferSetA)

            try {
                val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(NativePaddleEngine.bufferSetA.p)
                val tilt = deskewResA.angle
                val deskewHtml = deskewResA.engines.map { (k, v) -> "$k: ${v.angle}&deg; (${v.timesMs.sum()}ms)" }.joinToString("<br>")

                suspend fun pRotate(set: BufferSet, angle: Float): Long = withContext(Dispatchers.IO) {
                    val tRot0 = System.currentTimeMillis()
                    val src = set.p.mat; val dst = set.s.mat
                    val matrixLocal = android.graphics.Matrix(); matrixLocal.postRotate(-angle, src.cols() / 2f, src.rows() / 2f)
                    val values = FloatArray(9); matrixLocal.getValues(values)
                    val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
                    rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble()); rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())
                    org.opencv.imgproc.Imgproc.warpAffine(src, dst, rotMat, src.size(), org.opencv.imgproc.Imgproc.INTER_CUBIC, org.opencv.core.Core.BORDER_CONSTANT, org.opencv.core.Scalar(0.0))
                    set.flip(); rotMat.release(); System.currentTimeMillis() - tRot0
                }

                val angleA = deskewResA.engines["ML Kit"]?.angle ?: 0f; val tRotateA = pRotate(NativePaddleEngine.bufferSetA, angleA)
                val angleB = deskewResA.engines["Paddle V3"]?.angle ?: 0f; val tRotateB = pRotate(NativePaddleEngine.bufferSetB, angleB)

                // 1. Quad Discovery (Multi-Scale A/B x ML/PD)
                val scales = listOf(200, 600, 1000, 2500)
                
                // Track details for JSON
                val detailsA = mutableMapOf<String, MutableMap<Int, List<PumpHunk>>>("ML" to mutableMapOf(), "PD" to mutableMapOf())
                val detailsB = mutableMapOf<String, MutableMap<Int, List<PumpHunk>>>("ML" to mutableMapOf(), "PD" to mutableMapOf())

                // Discovery Path A
                val mlBlocksRawA = mutableListOf<PumpHunk>(); val pdBlocksRawA = mutableListOf<PumpHunk>()
                val t0A = System.currentTimeMillis()
                scales.forEach { scale ->
                    prepareScale(NativePaddleEngine.bufferSetA, scale)
                    val ml = runDiscoveryML(NativePaddleEngine.bufferSetA, context); mlBlocksRawA.addAll(ml); detailsA["ML"]!![scale] = ml
                    val pd = runDiscoveryPaddle(NativePaddleEngine.bufferSetA, paddleEngine); pdBlocksRawA.addAll(pd); detailsA["PD"]!![scale] = pd
                }
                val tDiscoveryA = System.currentTimeMillis() - t0A
                val mlHunksRawA = mergeGeometryIntoHunks(mlBlocksRawA); val pdHunksRawA = mergeGeometryIntoHunks(pdBlocksRawA)
                
                // Discovery Path B
                val mlBlocksRawB = mutableListOf<PumpHunk>(); val pdBlocksRawB = mutableListOf<PumpHunk>()
                val t0B = System.currentTimeMillis()
                scales.forEach { scale ->
                    prepareScale(NativePaddleEngine.bufferSetB, scale)
                    val ml = runDiscoveryML(NativePaddleEngine.bufferSetB, context); mlBlocksRawB.addAll(ml); detailsB["ML"]!![scale] = ml
                    val pd = runDiscoveryPaddle(NativePaddleEngine.bufferSetB, paddleEngine); pdBlocksRawB.addAll(pd); detailsB["PD"]!![scale] = pd
                }
                val tDiscoveryB = System.currentTimeMillis() - t0B
                val mlHunksRawB = mergeGeometryIntoHunks(mlBlocksRawB); val pdHunksRawB = mergeGeometryIntoHunks(pdBlocksRawB)

                // 2. Selection & Extraction for ALL 4 PATHS
                val minEdge = Math.min(imgW, imgH).toFloat()
                val maxX = imgW / (2f * minEdge); val maxY = imgH / (2f * minEdge)

                suspend fun getFinal(hunks: List<PumpHunk>, buf: BufferSet, engine: String, ang: Float): PathResult {
                    val stitched = stitchHunksHorizontally(hunks)
                    val (top, bottom) = groupLanesByVerticalGap(stitched)
                    val pair = findBestLanePair(top, bottom) ?: return PathResult("N/A", "N/A", "", "")
                    val expT = expandHunkContext(pair.first, maxX, maxY); val expB = expandHunkContext(pair.second, maxX, maxY)
                    val res = performHunkRecognition(listOf(expT, expB), buf, experimentRecSet320x48, engine, paddleEngine, context, ang)
                    
                    suspend fun takeCrop(exp: PumpHunk, orig: PumpHunk): String {
                        val p1 = IcrsMath.icrsToPixel(exp.icrs.left, exp.icrs.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(exp.icrs.right, exp.icrs.bottom, imgW, imgH)
                        val rect = android.graphics.Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                        val anns = mutableListOf<SnapshotAnnotation>()
                        if (engine == "Paddle") {
                            val o1 = IcrsMath.icrsToPixel(orig.icrs.left, orig.icrs.top, imgW, imgH)
                            val o2 = IcrsMath.icrsToPixel(orig.icrs.right, orig.icrs.bottom, imgW, imgH)
                            anns.add(SnapshotAnnotation(o1.x.toInt(), o1.y.toInt(), o2.x.toInt(), o2.y.toInt(), Shape.RECTANGLE, 0xFFFFFFFF.toInt(), 4))
                        }
                        return OcrUtils.takeSnapshot(buf.p, rect, 300, 100, anns, null, buf).first
                    }
                    return PathResult(applyRecognitionHeuristics(res[0].text), applyRecognitionHeuristics(res[1].text), takeCrop(expT, pair.first), takeCrop(expB, pair.second))
                }

                val resAMl = getFinal(mlHunksRawA, NativePaddleEngine.bufferSetA, "ML Kit", angleA)
                val resAPd = getFinal(pdHunksRawA, NativePaddleEngine.bufferSetA, "Paddle", angleA)
                val resBMl = getFinal(mlHunksRawB, NativePaddleEngine.bufferSetB, "ML Kit", angleB)
                val resBPd = getFinal(pdHunksRawB, NativePaddleEngine.bufferSetB, "Paddle", angleB)

                // 3. Visualization
                fun getAnns(list: List<PumpHunk>, color: Int, width: Int) = list.map { h -> 
                    val p1 = IcrsMath.icrsToPixel(h.icrs.left, h.icrs.top, imgW, imgH); val p2 = IcrsMath.icrsToPixel(h.icrs.right, h.icrs.bottom, imgW, imgH)
                    SnapshotAnnotation(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), Shape.RECTANGLE, color, width)
                }
                
                val aAMl = getAnns(mlBlocksRawA, 0xFFFFA500.toInt(), 2) + getAnns(mlHunksRawA, 0xFFFF0000.toInt(), 4)
                val (hunksAMl64, _) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetA.p, null, 600, 450, aAMl, null, NativePaddleEngine.bufferSetA)
                val aAPd = getAnns(pdBlocksRawA, 0xFFFFA500.toInt(), 2) + getAnns(pdHunksRawA, 0xFF00FF00.toInt(), 4)
                val (hunksAPd64, _) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetA.p, null, 600, 450, aAPd, null, NativePaddleEngine.bufferSetA)
                val aBMl = getAnns(mlBlocksRawB, 0xFFFFA500.toInt(), 2) + getAnns(mlHunksRawB, 0xFFFF0000.toInt(), 4)
                val (hunksBMl64, _) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetB.p, null, 600, 450, aBMl, null, NativePaddleEngine.bufferSetB)
                val aBPd = getAnns(pdBlocksRawB, 0xFFFFA500.toInt(), 2) + getAnns(pdHunksRawB, 0xFF00FF00.toInt(), 4)
                val (hunksBPd64, _) = OcrUtils.takeSnapshot(NativePaddleEngine.bufferSetB.p, null, 600, 450, aBPd, null, NativePaddleEngine.bufferSetB)

                
                val rowHtml = pBuildHtmlRowDynamic(
                    rowIndex = index + 1,
                    fileName = file.name,
                    imgW = imgW,
                    imgH = imgH,
                    isDegraded = meta.isDegraded,
                    beforeB64 = beforeB64,
                    histB64 = histB64,
                    cdfB64 = cdfB64,
                    afterB64 = afterB64,
                    deskewHtml = deskewHtml,
                    hunksAMl64 = hunksAMl64,
                    hunksAPd64 = hunksAPd64,
                    hunksBMl64 = hunksBMl64,
                    hunksBPd64 = hunksBPd64,
                    resAMl = resAMl,
                    resAPd = resAPd,
                    resBMl = resBMl,
                    resBPd = resBPd,
                    tDeskew = (tRotateA + tRotateB),
                    tilt = tilt,
                    angleA = angleA,
                    angleB = angleB,
                    diagnostic = meta.diagnostic
                )

                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = pStartNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length

                val discDetails = JSONObject().apply { 
                    put("path_a", serializeDiscoveryDetails(detailsA))
                    put("path_b", serializeDiscoveryDetails(detailsB))
                }

                val photoJson = pSerializePhotoResultToJson(
                    index + 1, imgW, imgH, imgW, imgH, meta.isDegraded, meta.diagnostic, deskewResA, tSnapOrig, 0L, file.name,
                    tDiscoveryA, tDiscoveryB, resAMl, resAPd, resBMl, resBPd, mlHunksRawA.size, pdHunksRawA.size, discDetails
                )
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma" + "\\n")
                val resultSummary = PumpPhotoResultSummary(file.name, "ML: ${mlHunksRawA.size} | PD: ${pdHunksRawA.size}", 1.0f, "${resAMl.cost} / ${resAMl.vol}")
                withContext(Dispatchers.Main) { onProgress(resultSummary, (index + 1).toFloat() / total) }
                delay(150)



            } finally {
                // BufferSets are cleaned up globally or at end of session
            }
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Experiment failed for row $index (${file.name}):\n" + Log.getStackTraceString(e))
        }
    }
    currentFile.appendText(footer)
    jsonFile.appendText("\n  ]\n}")
    
    experimentRecSet320x48.release()
    experimentDetSet512x128.release()
}

private fun pSerializePhotoResultToJson(
    lineNumber: Int, probedW: Int, probedH: Int, decodedW: Int, decodedH: Int, 
    isDegraded: Boolean, nativeProbe: String, deskewResA: OdometerOcrUtils.DeskewResult? = null,
    tSnapOrig: Long = 0, tSnapDeskew: Long = 0, fileName: String = "",
    tDiscoveryA: Long = 0, tDiscoveryB: Long = 0,
    resAMl: PathResult = PathResult("N/A", "N/A", "", ""),
    resAPd: PathResult = PathResult("N/A", "N/A", "", ""),
    resBMl: PathResult = PathResult("N/A", "N/A", "", ""),
    resBPd: PathResult = PathResult("N/A", "N/A", "", ""),
    mlHunkCountA: Int = 0, pdHunkCountA: Int = 0,
    discoveryDetails: JSONObject? = null
): JSONObject {
    val root = JSONObject()
    root.apply {
        put("line_number", lineNumber); put("file", fileName)
        put("probedWidth", probedW); put("probedHeight", probedH)
        put("imageWidth", decodedW); put("imageHeight", decodedH)
        put("isDegraded", isDegraded); put("nativeProbe", nativeProbe)
        put("t_thumb_orig_ms", tSnapOrig); put("t_snap_deskew_ms", tSnapDeskew)
        put("t_discovery_a_ms", tDiscoveryA); put("t_discovery_b_ms", tDiscoveryB)
        put("ml_hunk_count_a", mlHunkCountA); put("pd_hunk_count_a", pdHunkCountA)
        if (discoveryDetails != null) put("discovery_details", discoveryDetails)
        val r = JSONObject()
        r.put("aml_cost", resAMl.cost); r.put("aml_vol", resAMl.vol)
        r.put("apd_cost", resAPd.cost); r.put("apd_vol", resAPd.vol)
        r.put("bml_cost", resBMl.cost); r.put("bml_vol", resBMl.vol)
        r.put("bpd_cost", resBPd.cost); r.put("bpd_vol", resBPd.vol)
        put("quad_results", r)
        val d = JSONObject()
        d.pPutSafe("angle_a", (deskewResA?.angle ?: 0f).toDouble())
        
        // Add A/B Parity Checksums (Phase 117 Patch 3)
        deskewResA?.engines?.get("Paddle V3")?.metadata?.forEach { (k, v) -> 
            if (k.contains("chk") || k.contains("count")) d.put(k, v)
        }
        put("deskew", d)
    }
    return root
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
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(256), org.opencv.core.MatOfFloat(0f, 256f))
    
    val bins = FloatArray(256); hist.get(0, 0, bins)
    
    val bmp = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
    canvas.drawColor(Color.BLACK)
    val paint = Paint(); val maxVal = (0..255).maxOf { bins[it] }.toDouble().coerceAtLeast(1.0)
    
    for (i in 0..99) {
        val bStart = (i * 2.56).toInt(); val bEnd = ((i + 1) * 2.56).toInt()
        val avg = (bStart until bEnd.coerceAtMost(256)).map { bins[it] }.average()
        val h = (avg / maxVal * 50.0).toInt()
        paint.color = Color.WHITE; canvas.drawRect(i.toFloat(), (50 - h).toFloat(), (i + 1).toFloat(), 50f, paint)
        
        if (i % 10 == 0) { paint.color = Color.RED; canvas.drawRect(i.toFloat(), 52f, (i + 1).toFloat(), 60f, paint) }
        if (i == (floorPercentile * 100).toInt()) { paint.color = Color.YELLOW; canvas.drawRect(i.toFloat(), 52f, (i + 1).toFloat(), 60f, paint) }
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

private fun pBuildHtmlHeader(time: String, total: Int, version: String): String = buildString {
    appendLine("<html><head><title>Pump Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .res-table { width: 100%; border: none; font-size: 20px; } .res-table th { background: #f0f0f0; }</style></head><body>")
    appendLine("<h1>Pump Extraction Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th><th style='width:350px;'>Set A ML Kit</th><th style='width:350px;'>Set A Paddle</th><th style='width:350px;'>Set B ML Kit</th><th style='width:350px;'>Set B Paddle</th><th style='width:600px;'>Final Comparison</th></tr>")
}

private fun pBuildHtmlRowDynamic(
    rowIndex: Int, 
    fileName: String, 
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    beforeB64: String, 
    histB64: String, 
    cdfB64: String,
    afterB64: String, 
    deskewHtml: String,
    hunksAMl64: String,
    hunksAPd64: String,
    hunksBMl64: String,
    hunksBPd64: String,
    resAMl: PathResult,
    resAPd: PathResult,
    resBMl: PathResult,
    resBPd: PathResult,
    tDeskew: Long, 
    tilt: Float,
    angleA: Float,
    angleB: Float,
    diagnostic: String = ""
): String = buildString {
    val resHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty()) "<br><small>Native: $diagnostic</small>" else ""
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$resHtml</small>$diagHtml<br><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt:</b> $tilt<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$beforeB64'><br><small>Orig</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$histB64'><br><small>Hist</small></td></tr><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$afterB64'><br><small>Stretch</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,$cdfB64'><br><small>CDF</small></td></tr><tr style='border:none;'><td colspan='2' style='border:none; padding:1px; text-align:left; font-size:14px;'><small>$deskewHtml</small></td></tr></table></td>")
    
    appendLine("<td><b>ML Kit (A):</b><br><img src='data:image/jpeg;base64,$hunksAMl64'></td>")
    appendLine("<td><b>Paddle (A):</b><br><img src='data:image/jpeg;base64,$hunksAPd64'></td>")
    appendLine("<td><b>ML Kit (B):</b><br><img src='data:image/jpeg;base64,$hunksBMl64'></td>")
    appendLine("<td><b>Paddle (B):</b><br><img src='data:image/jpeg;base64,$hunksBPd64'></td>")

    appendLine("<td><table class='res-table'><tr><th>Path</th><th>Cost</th><th>Volume</th></tr>")
    val pArr = listOf("A:ML" to resAMl, "A:PD" to resAPd, "B:ML" to resBMl, "B:PD" to resBPd)
    pArr.forEach { (name, res) ->
        appendLine("<tr><td>$name</td>")
        appendLine("<td><b>${res.cost}</b><br>" + (if(res.costB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${res.costB64}' style='width:150px;'>" else ""))
        appendLine("<td><b>${res.vol}</b><br>" + (if(res.volB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${res.volB64}' style='width:150px;'>" else ""))
        appendLine("</td></tr>")
    }
    appendLine("</table></td></tr>")
}

private fun pGetFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else return emptyList()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0); val isIcrs = obj.optBoolean("is_icrs", false)
            val centerPix = if (isIcrs) IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH) else android.graphics.PointF((cx * imgW).toFloat(), (cy * imgH).toFloat())
            val sE = minOf(imgW, imgH).toDouble(); val pW = if (isIcrs) (w * sE) else (w * imgW); val pH = if (isIcrs) (h * sE) else (h * imgH)
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

private fun prepareScale(buffer: BufferSet, targetLongEdge: Int) {
    val srcW = buffer.p.width
    val srcH = buffer.p.height
    val currentLongEdge = max(srcW, srcH)
    
    val targetW: Int
    val targetH: Int
    
    if (currentLongEdge <= targetLongEdge) {
        targetW = srcW
        targetH = srcH
    } else {
        val scale = targetLongEdge.toFloat() / currentLongEdge
        targetW = (srcW * scale).toInt()
        targetH = (srcH * scale).toInt()
    }
    
    Log.d(TAG, "prepareScale: target=$targetLongEdge -> ${targetW}x$targetH (src=${srcW}x$srcH)")
    
    val i1 = IcrsMath.pixelToIcrs(0f, 0f, buffer.s.width, buffer.s.height)
    val i2 = IcrsMath.pixelToIcrs(targetW.toFloat(), targetH.toFloat(), buffer.s.width, buffer.s.height)
    
    val cropId = buffer.s.createCrop(i1.x, i1.y, i2.x - i1.x, i2.y - i1.y, id = 999)
    val dstMat = buffer.c[cropId].mat
    org.opencv.imgproc.Imgproc.resize(buffer.p.mat, dstMat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()))
}

private fun flattenToNv21(slice: BufferSet.Slice): java.nio.ByteBuffer {
    val w = slice.width; val h = slice.height
    val ySize = w * h; val uvSize = (w / 2) * (h / 2) * 2
    val nv21 = ByteArray(ySize + uvSize)
    
    // Copy Y plane
    val yData = ByteArray(ySize)
    slice.mat.get(0, 0, yData)
    System.arraycopy(yData, 0, nv21, 0, ySize)
    
    // Copy UV plane (interleaved)
    val uvData = ByteArray(uvSize)
    slice.uvMat.get(0, 0, uvData)
    System.arraycopy(uvData, 0, nv21, ySize, uvSize)
    
    return java.nio.ByteBuffer.wrap(nv21)
}

private suspend fun runDiscoveryML(buffer: BufferSet, context: Context): List<PumpHunk> {
    val crop = buffer.c[999] ?: return emptyList()
    Log.d(TAG, "runDiscoveryML: crop=${crop.width}x${crop.height}")
    val nv21 = flattenToNv21(crop)
    val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(nv21, crop.width, crop.height, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
    val result = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
    
    val scaleW = result.imageWidth.toFloat()
    val scaleH = result.imageHeight.toFloat()
    val masterW = buffer.p.width; val masterH = buffer.p.height
    
    return result.textBlocks.map { block ->
        val ml = block.boundingBox.left * masterW / scaleW; val mt = block.boundingBox.top * masterH / scaleH
        val mr = block.boundingBox.right * masterW / scaleW; val mb = block.boundingBox.bottom * masterH / scaleH
        val i1 = IcrsMath.pixelToIcrs(ml, mt, masterW, masterH)
        val i2 = IcrsMath.pixelToIcrs(mr, mb, masterW, masterH)
        PumpHunk(block.text, RectF(i1.x, i1.y, i2.x, i2.y))
    }
}

private suspend fun runDiscoveryPaddle(buffer: BufferSet, paddleEngine: NativePaddleEngine): List<PumpHunk> {
    val crop = buffer.c[999] ?: return emptyList()
    Log.d(TAG, "runDiscoveryPaddle: crop=${crop.width}x${crop.height}")
    val res = paddleEngine.detect(crop) ?: return emptyList()
    
    // Calculate actual occupancy in the heatmap based on detector tensor size
    val isLarge = crop.width > 512 || crop.height > 128
    val tensorW = if (isLarge) 2048f else 512f
    val tensorH = if (isLarge) 2048f else 128f
    
    val occW = (crop.width / tensorW) * res.width
    val occH = (crop.height / tensorH) * res.height
    
    val masterW = buffer.p.width; val masterH = buffer.p.height
    
    val (blocks, _) = OdometerOcrUtils.processPaddleHeatmap(res.heatmap, res.width, res.height, 1.0f, crop)
    return blocks.map { block ->
        // Normalize coordinates relative to the occupied portion of the heatmap
        val nl = block.boundingBox.left / occW; val nt = block.boundingBox.top / occH
        val nr = block.boundingBox.right / occW; val nb = block.boundingBox.bottom / occH
        
        // Map to master pixels
        val ml = nl * masterW; val mt = nt * masterH
        val mr = nr * masterW; val mb = nb * masterH
        
        val i1 = IcrsMath.pixelToIcrs(ml, mt, masterW, masterH)
        val i2 = IcrsMath.pixelToIcrs(mr, mb, masterW, masterH)
        PumpHunk("", RectF(i1.x, i1.y, i2.x, i2.y))
    }
}

private fun mergeGeometryIntoHunks(allBlocks: List<PumpHunk>): List<PumpHunk> {
    if (allBlocks.isEmpty()) return emptyList()
    
    val clusters = mutableListOf<MutableList<PumpHunk>>()
    for (box in allBlocks) {
        var found = false
        for (cluster in clusters) {
            if (cluster.any { c -> 
                val interL = max(box.icrs.left, c.icrs.left); val interT = max(box.icrs.top, c.icrs.top)
                val interR = min(box.icrs.right, c.icrs.right); val interB = min(box.icrs.bottom, c.icrs.bottom)
                if (interR > interL && interB > interT) {
                    val interArea = (interR - interL) * (interB - interT)
                    val unionArea = (box.icrs.width() * box.icrs.height()) + (c.icrs.width() * c.icrs.height()) - interArea
                    (interArea / unionArea) > 0.4f
                } else false
            }) {
                cluster.add(box)
                found = true
                break
            }
        }
        if (!found) clusters.add(mutableListOf(box))
    }

    return clusters.map { cluster ->
        val widestL = cluster.minOf { it.icrs.left }; val widestR = cluster.maxOf { it.icrs.right }
        val shortestH = cluster.minOf { it.icrs.height() }
        val centerY = cluster.map { it.icrs.centerY() }.average().toFloat()
        
        val bestText = cluster.maxByOrNull { it.text.count { c -> c.isDigit() } }?.text ?: ""
        
        val fT = centerY - shortestH / 2f; val fB = centerY + shortestH / 2f
        PumpHunk(bestText, RectF(widestL, fT, widestR, fB))
    }
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

         val cropId = buffer.createCrop(l, t, r - l, b - t, id = 888)
         val crop = buffer.c[cropId]!!
         
         val targetH = 48; val scale = 48f / pH; val targetW = Math.min(320, (pW * scale).toInt())
         recBuffer.p.clear()
         val recCropId = recBuffer.createCrop(0, 0, targetW, targetH, id = 777)
         val recCrop = recBuffer.c[recCropId]!!
         org.opencv.imgproc.Imgproc.resize(crop.mat, recCrop.mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
         
         val res = if (engine == "ML Kit") {
             val nv21 = flattenToNv21(recCrop)
             val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(nv21, targetW, targetH, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
             val ocrRes = OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
             // ML Kit 7-Segment Cleanup + Upside Down detection
             val cleaned = OdometerOcrUtils.clean7SegmentDigits(ocrRes.debugText, Math.abs(angle) > 135f)
             ocrRes.copy(debugText = cleaned)
         } else {
             paddleEngine.recognize(recCrop)
         }
         
         recCrop.release(); crop.release()
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

private suspend fun pPerformLandmarkDiscovery(input: Any, context: Context): Pair<OcrResult, List<TextBlock>> {
    val queryOcrDiscovery = OcrHarness.runDiscovery(input, context)
    val landmarks = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight)
    return Pair(queryOcrDiscovery, landmarks)
}

private fun JSONObject.pPutSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.pPutSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentPump", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }

private fun pClusterRects(fragments: List<android.graphics.Rect>): List<android.graphics.Rect> {
    val clusters = mutableListOf<MutableList<android.graphics.Rect>>()
    for (frag in fragments) {
        val matchingClusters = mutableListOf<Int>()
        for ((idx, cluster) in clusters.withIndex()) {
            if (cluster.any { c ->
                val overlapTop = kotlin.math.max(frag.top, c.top); val overlapBottom = kotlin.math.min(frag.bottom, c.bottom)
                val overlapHeight = overlapBottom - overlapTop
                overlapHeight > 0 && overlapHeight >= kotlin.math.min(frag.height(), c.height()) * 0.20
            }) matchingClusters.add(idx)
        }
        if (matchingClusters.isEmpty()) clusters.add(mutableListOf(frag))
        else {
            val firstIdx = matchingClusters[0]; clusters[firstIdx].add(frag)
            for (k in matchingClusters.size - 1 downTo 1) { clusters[firstIdx].addAll(clusters[matchingClusters[k]]); clusters.removeAt(matchingClusters[k]) }
        }
    }
    return clusters.map { cluster -> 
        android.graphics.Rect(cluster.minOf { it.left }, cluster.minOf { it.top }, cluster.maxOf { it.right }, cluster.maxOf { it.bottom }) 
    }
}

private suspend fun pRunPaddleValleyIterative(
    displayName: String, 
    masterBuffer: Any, 
    mWidth: Int, 
    mHeight: Int, 
    winnerRef: PumpReferenceCache,
    vehicleBufferSets: Map<Int, BufferSet>,
    experimentDetSet512x128: BufferSet,
    experimentRecSet320x48: BufferSet,
    paddleEngine: NativePaddleEngine,
    report: MutableMap<String, OcrHarnessResult>, 
    targetRefMap: MutableMap<String, RefinementTrace>
) {
    val tH0 = System.currentTimeMillis()
    val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: return
    val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
    val jsonStages = com.google.gson.JsonObject()
    val allOdo = mutableListOf<String>()
    
    val l = winnerRef.vehicle.odometerCropLeft ?: 0f
    val t = winnerRef.vehicle.odometerCropTop ?: 0f
    val r = winnerRef.vehicle.odometerCropRight ?: 1f
    val b = winnerRef.vehicle.odometerCropBottom ?: 1f
    
    val roiW = ((r - l) * mWidth).toInt().coerceAtMost(mWidth)
    val roiH = ((b - t) * mHeight).toInt().coerceAtMost(mHeight)
    val startX = (l * mWidth).toInt().coerceIn(0, mWidth - 1)
    val startY = (t * mHeight).toInt().coerceIn(0, mHeight - 1)
    
    val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stages.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.75f) 
        else if (stage.contains("78%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.78f)
        
        val detSc = minOf(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
        val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
        val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
        
        val detCropId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[detCropId].mat, experimentDetSet512x128.c[detCropId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes = paddleEngine.detect(experimentDetSet512x128.p)
        val (rawB, _) = if (detRes != null) OdometerOcrUtils.processPaddleHeatmap(detRes.heatmap, detRes.width, detRes.height, detSc, odoBuffer.p.mat, "Paddle") else Pair(emptyList<TextBlock>(), emptyMap<String, String>())
        experimentDetSet512x128.c[detCropId].release()
        
        val frags = rawB.map { NativeImageUtils.expandByValley(odoBuffer.p.mat, it.boundingBox) }
        val cons = pClusterRects(frags).sortedBy { it.left }
        val odoB = StringBuilder()
        val fBoxes = mutableListOf<android.graphics.Rect>()
        val jMeta = com.google.gson.JsonObject()
        
        cons.forEach { box ->
            val sL = box.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
            val sT = box.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
            val sR = box.right.coerceIn(sL + 1, odoBuffer.p.mat.cols())
            val sB = box.bottom.coerceIn(sT + 1, odoBuffer.p.mat.rows())
            val rSrcId = odoBuffer.createCrop(sL, sT, sR - sL, sB - sT)
            experimentRecSet320x48.p.clear()
            val rSc = minOf(312f / (sR - sL), 40f / (sB - sT))
            val ew = (( (sR - sL) * rSc + 1).toInt() / 2) * 2
            val eh = (( (sB - sT) * rSc + 1).toInt() / 2) * 2
            val rCrId = experimentRecSet320x48.createCrop(4, 4, ew, eh)
            org.opencv.imgproc.Imgproc.resize(odoBuffer.c[rSrcId].mat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
            odoBuffer.c[rSrcId].release()
            experimentRecSet320x48.c[rCrId].release()
            
            val ocrR = paddleEngine.runConstrainedStatic(experimentRecSet320x48.p, paddleEngine.getDictionary())
            if (ocrR.text.isNotBlank()) { odoB.append(ocrR.text).append(" "); fBoxes.add(box) }
            ocrR.metadata.forEach { (k, v) -> jMeta.addProperty(k, v) }
        }
        
        val odoStr = odoB.toString().trim()
        allOdo.add(odoStr)
        val tL = System.currentTimeMillis() - tS0
        steps.add(OcrStepResult(stage, "", null, odoStr, emptyList(), emptyList(), null, null, jMeta.asMap().mapValues { it.value.asString }))
        
        val anns = mutableListOf<SnapshotAnnotation>()
        rawB.forEach { b -> anns.add(SnapshotAnnotation(b.boundingBox.left, b.boundingBox.top, b.boundingBox.right, b.boundingBox.bottom, Shape.RECTANGLE, Color.RED, 2)) }
        fBoxes.forEach { b -> anns.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) }
        
        val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
        lastThumb = sB64
        tSnTotal += ts
        htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br><img src='data:image/jpeg;base64,$lastThumb'><br>$odoStr</div>")
        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", odoStr)
        sObj.addProperty("time", tL)
        jMeta.entrySet().forEach { e -> sObj.add(e.key, e.value) }
        jsonStages.add(stage, sObj)
    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, allOdo.firstOrNull { it.isNotBlank() }, lastThumb, System.currentTimeMillis() - tH0, tSnTotal)
    report[displayName] = result
    targetRefMap[displayName] = RefinementTrace(displayName, System.currentTimeMillis() - tH0, steps)
}

private suspend fun pRunMLKitIterative(
    displayName: String, 
    masterBuffer: Any, 
    mWidth: Int, 
    mHeight: Int, 
    winnerRef: PumpReferenceCache,
    vehicleBufferSets: Map<Int, BufferSet>,
    experimentRecSet320x48: BufferSet,
    report: MutableMap<String, OcrHarnessResult>, 
    targetRefMap: MutableMap<String, RefinementTrace>
) {
    val tH0 = System.currentTimeMillis()
    val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: return
    val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
    val jsonStages = com.google.gson.JsonObject()
    val allOdo = mutableListOf<String>()
    
    val l = winnerRef.vehicle.odometerCropLeft ?: 0f
    val t = winnerRef.vehicle.odometerCropTop ?: 0f
    val r = winnerRef.vehicle.odometerCropRight ?: 1f
    val b = winnerRef.vehicle.odometerCropBottom ?: 1f
    
    val roiW = ((r - l) * mWidth).toInt().coerceAtMost(mWidth)
    val roiH = ((b - t) * mHeight).toInt().coerceAtMost(mHeight)
    val sX = (l * mWidth).toInt()
    val sY = (t * mHeight).toInt()
    
    val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stages.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.75f) 
        else if (stage.contains("78%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.78f)
        
        experimentRecSet320x48.p.clear()
        val rSc = minOf(320f / odoBuffer.p.mat.cols(), 48f / odoBuffer.p.mat.rows())
        val ew = ((odoBuffer.p.mat.cols() * rSc + 1).toInt() / 2) * 2
        val eh = ((odoBuffer.p.mat.rows() * rSc + 1).toInt() / 2) * 2
        val rCrId = experimentRecSet320x48.createCrop(0, 0, ew, eh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        
        val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(experimentRecSet320x48.p.nv21, 320, 48, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
        val vText = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await()
        experimentRecSet320x48.c[rCrId].release()
        
        val odoB = StringBuilder()
        vText.textBlocks.forEach { blk -> 
            blk.lines.forEach { line -> 
                val cleaned = OdometerOcrUtils.clean7SegmentDigits(line.text, Math.abs(line.angle) > 135f).filter { it.isDigit() }
                if (cleaned.isNotBlank()) odoB.append(cleaned) 
            } 
        }
        
        val odoStr = odoB.toString()
        allOdo.add(odoStr)
        val tL = System.currentTimeMillis() - tS0
        steps.add(OcrStepResult(stage, "", null, odoStr, emptyList(), emptyList(), null, null, emptyMap()))
        
        val anns = mutableListOf<SnapshotAnnotation>()
        val snX = odoBuffer.p.mat.cols().toFloat() / ew.toFloat()
        val snY = odoBuffer.p.mat.rows().toFloat() / eh.toFloat()
        vText.textBlocks.forEach { b -> 
            b.boundingBox?.let { anns.add(SnapshotAnnotation((it.left * snX).toInt(), (it.top * snY).toInt(), (it.right * snX).toInt(), (it.bottom * snY).toInt(), Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) } 
        }
        
        val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
        lastThumb = sB64
        tSnTotal += ts
        htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br><img src='data:image/jpeg;base64,$lastThumb'><br>$odoStr</div>")
        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", odoStr)
        sObj.addProperty("time", tL)
        jsonStages.add(stage, sObj)
    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, allOdo.firstOrNull { it.isNotBlank() }, lastThumb, System.currentTimeMillis() - tH0, tSnTotal)
    report[displayName] = result
    targetRefMap[displayName] = RefinementTrace(displayName, System.currentTimeMillis() - tH0, steps)
}
