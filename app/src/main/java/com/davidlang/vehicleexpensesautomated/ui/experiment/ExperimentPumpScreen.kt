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
            
            // Sequential A/B Ingestion
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)
            
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, NativePaddleEngine.bufferSetA.p)
            
            // Manual Distribution (A to B)
            NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
            NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)
            
            val (originalBase64, tSnapOrig) = OcrUtils.takeSnapshot(
                source = NativePaddleEngine.bufferSetA.p,
                sourceRect = null,
                targetW = 225,
                targetH = 0, // Aspect-aware
                annotations = emptyList(),
                scratchArgb = null,
                scratchYuv = NativePaddleEngine.bufferSetA
            )

            try {
                // Step 2 (Deskew): Calculate tilt independently for both pipelines
                val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(NativePaddleEngine.bufferSetA.p)

                val tilt = deskewResA.angle
                val tMl = deskewResA.mlTimeMs
                val tPd = deskewResA.paddleTimeMs

                // Phase 116: Independent High-Quality Rotation (Cubic)
                suspend fun pRotate(set: BufferSet, angle: Float): Long = withContext(Dispatchers.IO) {
                    val tRot0 = System.currentTimeMillis()
                    val src = set.p.mat
                    val dst = set.s.mat
                    
                    val matrixLocal = android.graphics.Matrix()
                    matrixLocal.postRotate(-angle, src.cols() / 2f, src.rows() / 2f)
                    val values = FloatArray(9)
                    matrixLocal.getValues(values)

                    val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
                    rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
                    rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())

                    org.opencv.imgproc.Imgproc.warpAffine(src, dst, rotMat, src.size(), org.opencv.imgproc.Imgproc.INTER_CUBIC, org.opencv.core.Core.BORDER_CONSTANT, org.opencv.core.Scalar(0.0))
                    set.flip()
                    rotMat.release()
                    System.currentTimeMillis() - tRot0
                }

                // Path A: ML Kit Deskew
                val angleA = deskewResA.engines["ML Kit"]?.angle ?: 0f
                val tRotateA = pRotate(NativePaddleEngine.bufferSetA, angleA)
                
                // Path B: Paddle Deskew
                val angleB = deskewResA.engines["Paddle V3"]?.angle ?: 0f
                val tRotateB = pRotate(NativePaddleEngine.bufferSetB, angleB)
                // Capture Deskewed Thumbnail A (ML Kit)
                val (deskewedA64, tSnapA) = OcrUtils.takeSnapshot(
                    source = NativePaddleEngine.bufferSetA.p, 
                    sourceRect = null, 
                    targetW = 600, 
                    targetH = 450, 
                    annotations = emptyList(),
                    scratchArgb = null,
                    scratchYuv = NativePaddleEngine.bufferSetA
                ) 
                
                // Capture Deskewed Thumbnail B (Paddle)
                val (deskewedB64, tSnapB) = OcrUtils.takeSnapshot(
                    source = NativePaddleEngine.bufferSetB.p, 
                    sourceRect = null, 
                    targetW = 600, 
                    targetH = 450, 
                    annotations = emptyList(),
                    scratchArgb = null,
                    scratchYuv = NativePaddleEngine.bufferSetB
                ) 
                
                val tSnapDeskew = tSnapA + tSnapB

                // PUMP_PIPELINE_TARGET
                val scales = listOf(200, 600, 1000, 2500)
                
                // Discovery ML Kit (Path A)
                val t0ML = System.currentTimeMillis()
                val mlBlocksRaw = mutableListOf<PumpHunk>()
                scales.forEach { scale ->
                    prepareScale(NativePaddleEngine.bufferSetA, scale)
                    mlBlocksRaw.addAll(runDiscoveryML(NativePaddleEngine.bufferSetA, context))
                }
                val mlHunksRaw = mergeGeometryIntoHunks(mlBlocksRaw)
                val mlStitched = stitchHunksHorizontally(mlHunksRaw)
                val (mlTop, mlBottom) = groupLanesByVerticalGap(mlStitched)
                val mlHunks = performHunkRecognition(mlTop + mlBottom, NativePaddleEngine.bufferSetA, "ML Kit", paddleEngine, context)
                val tDiscoveryML = System.currentTimeMillis() - t0ML
                
                // Discovery Paddle (Path B)
                val t0PD = System.currentTimeMillis()
                val pdBlocksRaw = mutableListOf<PumpHunk>()
                scales.forEach { scale ->
                    prepareScale(NativePaddleEngine.bufferSetB, scale)
                    pdBlocksRaw.addAll(runDiscoveryPaddle(NativePaddleEngine.bufferSetB, paddleEngine))
                }
                val pdHunksRaw = mergeGeometryIntoHunks(pdBlocksRaw)
                val pdStitched = stitchHunksHorizontally(pdHunksRaw)
                val (pdTop, pdBottom) = groupLanesByVerticalGap(pdStitched)
                val pdHunks = performHunkRecognition(pdTop + pdBottom, NativePaddleEngine.bufferSetB, "Paddle", paddleEngine, context)
                val tDiscoveryPD = System.currentTimeMillis() - t0PD

                // Create master BMP for overlays
                val masterBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(NativePaddleEngine.bufferSetA.p.mat, masterBmp)

                // Capture Hunk Overlays
                val mlOutBmp = masterBmp.copy(Bitmap.Config.ARGB_8888, true); val mlCanvas = Canvas(mlOutBmp)
                drawHunksOnBitmap(mlOutBmp, mlHunksRaw, Color.RED, mlCanvas) // Raw Hunks
                drawHunksOnBitmap(mlOutBmp, mlStitched, Color.BLUE, mlCanvas) // Stitched Lanes
                val hunksA64 = pumpCreateScaledBase64(mlOutBmp, 600, 70)
                mlOutBmp.recycle()
                
                val pdOutBmp = masterBmp.copy(Bitmap.Config.ARGB_8888, true); val pdCanvas = Canvas(pdOutBmp)
                drawHunksOnBitmap(pdOutBmp, pdHunksRaw, Color.GREEN, pdCanvas) // Raw Hunks
                drawHunksOnBitmap(pdOutBmp, pdStitched, Color.BLUE, pdCanvas) // Stitched Lanes
                val hunksB64 = pumpCreateScaledBase64(pdOutBmp, 600, 70)
                pdOutBmp.recycle()
                masterBmp.recycle()

                val rowHtml = pBuildHtmlRowDynamic(
                    index + 1, file.name, imgW, imgH, meta.isDegraded, originalBase64, 
                    deskewedA64, deskewedB64, hunksA64, hunksB64, (tRotateA + tRotateB), tDiscoveryML, tDiscoveryPD, tilt, angleA, angleB, meta.diagnostic
                )

                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = pStartNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length

                val photoJson = pSerializePhotoResultToJson(
                    index + 1, imgW, imgH, imgW, imgH, meta.isDegraded, 
                    meta.diagnostic, deskewResA, tSnapOrig, tSnapDeskew, file.name
                )
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma\n")
                
                finalWinnerName = "N/A"
                bestOdometer = "N/A"
                val resultSummary = PumpPhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer)

                // Ensure UI update is dispatched BEFORE we move to cleanup
                withContext(Dispatchers.Main) { 
                    onProgress(resultSummary, (index + 1).toFloat() / total) 
                }
                
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.i("MEMORY_CHECK", "[Image ${index + 1}/${total}] Used Heap: ${usedMem}MB / ${runtime.maxMemory() / 1024 / 1024}MB")
                
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
    lineNumber: Int, probedW: Int, probedH: Int, decodedW: Int, decodedH: Int, isDegraded: Boolean, 
    nativeProbe: String, deskewResA: OdometerOcrUtils.DeskewResult? = null,
    tSnapOrig: Long = 0, tSnapDeskew: Long = 0, fileName: String = ""
): JSONObject {
    val root = JSONObject()
    root.apply {
        put("line_number", lineNumber)
        put("file", fileName)
        put("probedWidth", probedW)
        put("probedHeight", probedH)
        put("imageWidth", decodedW)
        put("imageHeight", decodedH)
        put("isDegraded", isDegraded)
        put("nativeProbe", nativeProbe)
        put("t_thumb_orig_ms", tSnapOrig)
        put("t_snap_deskew_ms", tSnapDeskew)

        // Deskew Data (Source from Path A)
        val deskewObj = JSONObject()
        deskewObj.pPutSafe("angle_a", (deskewResA?.angle ?: 0f).toDouble())
        put("deskew", deskewObj)
    }
    return root
}

private fun pBuildHtmlHeader(time: String, total: Int, version: String): String = buildString {
    appendLine("<html><head><title>Pump Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; }</style></head><body>")
    appendLine("<h1>Pump Extraction Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th><th style='width:650px;'>ML Kit Hunks</th><th style='width:650px;'>Paddle Hunks</th></tr>")
}

private fun pBuildHtmlRowDynamic(
    rowIndex: Int, 
    fileName: String, 
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    originalBase64: String, 
    deskewedA64: String,
    deskewedB64: String,
    hunksA64: String,
    hunksB64: String,
    tDeskew: Long, 
    tDiscoveryML: Long,
    tDiscoveryPD: Long,
    tilt: Float,
    angleA: Float,
    angleB: Float,
    diagnostic: String = ""
): String = buildString {
    val resHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty()) "<br><small>Native: $diagnostic</small>" else ""
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$resHtml</small>$diagHtml<br><b>Deskew Time:</b> ${tDeskew}ms<br><b>Tilt:</b> $tilt<br><img src='data:image/jpeg;base64,$originalBase64'></td>")
    
    // ML Kit Column
    appendLine("<td>")
    appendLine("<b>Deskewed:</b><br><img src='data:image/jpeg;base64,$deskewedA64'><br><small>Angle: %.2f&deg;</small><br>".format(angleA))
    appendLine("<b>Ideal Hunks (ML Kit):</b><br><img src='data:image/jpeg;base64,$hunksA64'><br><small>Time: ${tDiscoveryML}ms</small>")
    appendLine("</td>")

    // Paddle Column
    appendLine("<td>")
    appendLine("<b>Deskewed:</b><br><img src='data:image/jpeg;base64,$deskewedB64'><br><small>Angle: %.2f&deg;</small><br>".format(angleB))
    appendLine("<b>Ideal Hunks (Paddle):</b><br><img src='data:image/jpeg;base64,$hunksB64'><br><small>Time: ${tDiscoveryPD}ms</small>")
    appendLine("</td></tr>")
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
    val crop = buffer.c[999]!!
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
    val res = paddleEngine.detect(buffer.c[999]!!) ?: return emptyList()
    val scaleW = res.width.toFloat(); val scaleH = res.height.toFloat()
    val masterW = buffer.p.width; val masterH = buffer.p.height
    
    val blocks = OdometerOcrUtils.processPaddleHeatmap(res.heatmap, res.width, res.height, 1.0f, buffer.c[999]!!)
    return blocks.map { block ->
        val ml = block.boundingBox.left * masterW / scaleW; val mt = block.boundingBox.top * masterH / scaleH
        val mr = block.boundingBox.right * masterW / scaleW; val mb = block.boundingBox.bottom * masterH / scaleH
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

private suspend fun performHunkRecognition(hunks: List<PumpHunk>, buffer: BufferSet, engine: String, paddleEngine: NativePaddleEngine, context: Context): List<PumpHunk> {
     val masterW = buffer.p.width; val masterH = buffer.p.height
     val minEdge = min(masterW, masterH).toFloat()
     val maxX = masterW / (2f * minEdge); val maxY = masterH / (2f * minEdge)
     
     return hunks.map { hunk ->
         val l = hunk.icrs.left.coerceIn(-maxX, maxX - 0.001f)
         val t = hunk.icrs.top.coerceIn(-maxY, maxY - 0.001f)
         val r = hunk.icrs.right.coerceIn(l + 0.001f, maxX)
         val b = hunk.icrs.bottom.coerceIn(t + 0.001f, maxY)
         
         val p1 = IcrsMath.icrsToPixel(l, t, masterW, masterH)
         val p2 = IcrsMath.icrsToPixel(r, b, masterW, masterH)
         val pW = (p2.x - p1.x).toInt(); val pH = (p2.y - p1.y).toInt()
         
         if (pW < 32 || pH < 32) {
             Log.i("PUMP_ICRS", "Skipping tiny hunk: x at ICRS [, , , ]")
             return@map hunk
         }
         
         Log.i("PUMP_ICRS", "Hunk ICRS: [, , , ] | Master: x | Pixels:  x ")
         
         val cropId = buffer.createCrop(l, t, r - l, b - t, id = 888)
         val crop = buffer.c[cropId]!!
         val res = if (engine == "ML Kit") {
             val nv21 = flattenToNv21(crop)
             val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(nv21, crop.width, crop.height, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
             OdometerOcrUtils.extractFromPhotoBitmapRaw(img)
         } else {
             paddleEngine.recognize(crop)
         }
         crop.release()
         
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
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f) 
        else if (stage.contains("78%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.78f)
        
        val detSc = minOf(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
        val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
        val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
        
        val detCropId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[detCropId].mat, experimentDetSet512x128.c[detCropId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes = paddleEngine.detect(experimentDetSet512x128.p)
        val rawB = if (detRes != null) OdometerOcrUtils.processPaddleHeatmap(detRes.heatmap, detRes.width, detRes.height, detSc, odoBuffer.p.mat, "Paddle") else emptyList()
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
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f) 
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
