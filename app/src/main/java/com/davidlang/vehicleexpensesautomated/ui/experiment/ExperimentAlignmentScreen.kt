package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.*
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val TAG = "ExperimentAlignment"

@Immutable
data class PhotoResultSummary(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val odometer: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentAlignmentScreen(navController: NavHostController) {
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
    val resultsList = remember { mutableStateListOf<PhotoResultSummary>() }

    val experimentDir = File(context.filesDir, "experiment_photos")
    val reportDir = File(context.filesDir, "experiment_reports")
    val debugCropDir = File(context.filesDir, "experiment_debug_crops")

    if (!reportDir.exists()) reportDir.mkdirs()
    if (!debugCropDir.exists()) debugCropDir.mkdirs()

    val zipLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { status = "Extracting ZIP..."; val success = extractZipToPhotos(it, experimentDir, context); status = if (success) "ZIP extracted!" else "Failed to extract ZIP." } }
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
            if (vehicles.isEmpty()) { status = "Error: No vehicles in DB."; return@Button }
            scope.launch { 
                totalPhotos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.size ?: 0
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Reports saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
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

data class ReferenceCache(
    val vehicle: Vehicle, 
    val referenceBase64: String, 
    val curatedLandmarks: List<TextBlock>, 
    val bmp: Bitmap
)

data class RefinementTrace(
    val strategyName: String,
    val timeMs: Long,
    val steps: List<OcrStepResult>
)

data class SingleVehicleResult(
    val vehicleName: String,
    val vetoReason: String,
    val matchTimeMs: Long,
    val alignmentTrace: AlignmentTraceResult?,
    val refinementTraces: Map<String, RefinementTrace>,
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val isWinner: Boolean
)

data class AlignmentTraceResult(
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ProcessedPhotoResult(
    val winnerName: String,
    val bestOdometer: String,
    val bestOdometerDisplay: String,
    val tDeskewTotal: Long,
    val tDiscoveryTotal: Long,
    val deskewedBase64: String,
    val discoveryDebugText: String,
    val discoveryResult: OcrResult,
    val primaryVetoResults: Map<Int, VetoResult>,
    val vehicleResultsMap: Map<Int, SingleVehicleResult>,
    val hardcodedWinner: String?,
    val annotatedCrops: Map<String, String>
)

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val paddleEngineV2 = NativePaddleEngine(context, variant = "V2")
    val paddleEngineV3 = NativePaddleEngine(context, variant = "V3")

    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curated = getFullLandmarksFromJson(v.landmarkTextBlocksJson, "ML Kit", bmp.width, bmp.height)
        val annotatedBmp = drawCropBoxesOnReference(bmp, v); val refBase64 = createScaledBase64(annotatedBmp, 400, 70); annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curated, bmp)
    }
    
    val jsonFile = File(reportDir, "alignment_results_$timestamp.json")
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"total_photos\": $total,\n  \"results\": [\n")
    
    var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    val footer = "</table></body></html>"
    
    // Phase 58 Strategies
    val strategies = listOf(
        "ML Kit Native (Exact)", "ML Kit 48px (Exact)", "ML Kit 32px (Exact)",
        "Paddle V2 Greedy", "Paddle V3 Greedy",
        "Paddle V3 Disc (Unclip)", "Paddle V3 Disc (Valley)"
    )

    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { 
        writeText(buildHtmlHeader(timestamp, total, strategies)) 
    }
    var currentFile = startNewFile()
    
    photos.forEachIndexed { index, file ->
        var currentResult: ProcessedPhotoResult? = null
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"
        try {
            withContext(Dispatchers.Main) { onLog("") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: throw Exception("Bitmap decode failed")
            var originalBitmap: Bitmap? = null
            try {
                originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
                val deskewedBase64 = createScaledBase64(originalBitmap!!, 150, 50)
                
                // Phase 63 Increment 1: Pre-Deskew Forensic Scan
                val paddleDeskewDiscovery = if (paddleEngineV3.isAvailable) paddleEngineV3.runDetectionOnly(originalBitmap!!) else null
                
                // Pass paddleEngineV3 for optimized deskew calculation
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(originalBitmap!!, paddleEngineV3)
                val tilt = deskewRes.angle; val tDeskewTotal = deskewRes.timeMs
                if (Math.abs(tilt) > 0.2f) { 
                    val leveled = OdometerOcrUtils.rotateBitmap(originalBitmap!!, -tilt)
                    if (leveled != originalBitmap) { originalBitmap!!.recycle(); originalBitmap = leveled }
                }
                val tDiscoveryStart = System.currentTimeMillis()
                val queryOcrDiscovery = OcrHarness.runDiscovery(originalBitmap!!, context)
                val tDiscoveryTotal = System.currentTimeMillis() - tDiscoveryStart
                val queryLandmarksPrimary = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight)
                val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarksPrimary, cachedRefs.map { it.vehicle }, "ML Kit")
                val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()

                cachedRefs.forEach { ref ->
                    val veto = primaryVetoResults[ref.vehicle.id] ?: VetoResult(false)
                    val tMatchStart = System.currentTimeMillis()
                    val isWinner = finalWinnerName == "No match" && !veto.isVetoed
                    
                    var alignmentTrace: AlignmentTraceResult? = null
                    val refinementTraces = mutableMapOf<String, RefinementTrace>()
                    
                    if (isWinner) {
                        finalWinnerName = ref.vehicle.name
                        val t0 = System.currentTimeMillis()
                        val alignRes = ImageAlignmentUtils.anchorAlign(ref.bmp, originalBitmap!!, ref.curatedLandmarks, queryLandmarksPrimary, ref.vehicle)
                        val elapsedAlign = System.currentTimeMillis() - t0
                        if (alignRes.success && alignRes.alignedImage != null) {
                            alignmentTrace = AlignmentTraceResult(true, elapsedAlign, createScaledBase64(alignRes.alignedImage, 400, 70), alignRes.metadata)
                            
                            // Phase 58: Refinement Loop
                            val exactCrop = manualCropOdometer(alignRes.alignedImage, ref.vehicle)
                            if (exactCrop != null) {
                                // Reconstruct Ground Truth: Save raw crop for host-side labeling
                                val cropFile = File(debugCropDir, "crop_${file.name.replace(".dng", ".jpg")}")
                                try {
                                    cropFile.outputStream().use { out ->
                                        exactCrop.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    }
                                } catch (e: Exception) { Log.e(TAG, "Failed to save crop", e) }

                                for (strat in strategies) {
                                    val tRef0 = System.currentTimeMillis()
                                    val engine = when {
                                        strat.contains("ML Kit") -> "ML Kit"
                                        strat.contains("V2") -> if (strat.contains("Disc")) "Paddle V2 Disc (Padded)" else "Paddle V2 Greedy"
                                        else -> if (strat.contains("Disc")) "Paddle V3 Disc (Padded)" else "Paddle V3 Greedy"
                                    }
                                    val h = when {
                                        strat.contains("48px") -> 48
                                        strat.contains("32px") -> 32
                                        else -> null
                                    }
                                    val activePaddle = if (strat.contains("V3")) paddleEngineV3 else paddleEngineV2
                                    val expansionMode = if (strat.contains("Valley")) DiscoveryExpansion.VALLEY else DiscoveryExpansion.UNCLIP
                                    
                                    val steps = if (strat.contains("Disc")) {
                                        DiscoveryOcrUtils.runDiscoveryMultiStepOcr(exactCrop, context, engine, h, activePaddle, expansionMode)
                                    } else {
                                        OdometerOcrUtils.runMultiStepOcr(exactCrop, context, engine, h, activePaddle)
                                    }
                                    refinementTraces[strat] = RefinementTrace(strat, System.currentTimeMillis() - tRef0, steps)
                                }
                                exactCrop.recycle()
                            }
                            
                            val allResults = refinementTraces.values.flatMap { it.steps }.mapNotNull { it.text }.filter { it.isNotBlank() }
                            if (allResults.isNotEmpty()) {
                                bestOdometer = allResults.groupBy { it }.mapValues { it.value.size }.maxByOrNull { it.value }?.key ?: "FAILED"
                            }
                            
                            alignRes.alignedImage.recycle()
                        } else { alignmentTrace = AlignmentTraceResult(false, elapsedAlign, "", alignRes.metadata) }
                    }
                    vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(ref.vehicle.name, veto.reasonWord, System.currentTimeMillis() - tMatchStart, alignmentTrace, refinementTraces, veto.queryWords, veto.myManifest.toList(), veto.vetoPool.toList(), isWinner)
                }

                val rowHtml = buildHtmlRowDynamic(index + 1, file.name, deskewedBase64, queryOcrDiscovery.debugText, vehicleResultsMap, cachedRefs, finalWinnerName, strategies, tDeskewTotal, tDiscoveryTotal)
                val photoJson = serializePhotoResultToJson(index + 1, file.name, finalWinnerName, bestOdometer, tDeskewTotal, tilt, tDiscoveryTotal, queryOcrDiscovery, primaryVetoResults, vehicleResultsMap, vehicles, strategies, deskewRes.rawBlocks, paddleDeskewDiscovery)
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma\n")
                
                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length
                
                val resultSummary = PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer)
                currentResult = ProcessedPhotoResult(finalWinnerName, bestOdometer, bestOdometer, tDeskewTotal, tDiscoveryTotal, deskewedBase64, queryOcrDiscovery.debugText, queryOcrDiscovery, primaryVetoResults, vehicleResultsMap, null, emptyMap())

                // Ensure UI update is dispatched BEFORE we move to cleanup
                withContext(Dispatchers.Main) { 
                    onProgress(resultSummary, (index + 1).toFloat() / total) 
                }
                
                val runtime = Runtime.getRuntime(); val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.i("MEMORY_CHECK", "[Image ${index + 1}/${total}] Used Heap: ${usedMem}MB / ${runtime.maxMemory() / 1024 / 1024}MB")
                
                delay(150)
            } finally {
                if (originalBitmap != rawBitmap) rawBitmap.recycle()
                originalBitmap?.recycle()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Failed ${file.name}", e) 
        } finally {
            // DEFENSIVE CLEANUP: Recycle all bitmaps generated for this photo
            currentResult?.let { res ->
                res.discoveryResult.croppedBitmap?.let { if (!it.isRecycled) it.recycle() }
                res.discoveryResult.openCvProcessedBitmap?.let { if (!it.isRecycled) it.recycle() }
                res.vehicleResultsMap.values.forEach { vr ->
                    vr.refinementTraces.values.forEach { tr ->
                        tr.steps.forEach { step ->
                            if (!step.bitmap.isRecycled) step.bitmap.recycle()
                        }
                    }
                }
            }
            currentResult = null // Explicitly nullify to prevent re-access
            System.gc()
        }
    }
    currentFile.appendText(footer)
    jsonFile.appendText("\n  ]\n}")
    cachedRefs.forEach { it.bmp.recycle() }
}

private fun serializePhotoResultToJson(
    lineNumber: Int, fileName: String, winner: String, odo: String, tDeskew: Long, deskewAngle: Float, tDiscovery: Long,
    discovery: OcrResult, vetoSweep: Map<Int, VetoResult>, vResults: Map<Int, SingleVehicleResult>,
    vehicles: List<Vehicle>, strategies: List<String>, deskewBlocks: List<TextBlock>, paddleDiscovery: OcrResult? = null
): JSONObject {
    return JSONObject().apply {
        put("line_number", lineNumber); put("file", fileName); put("winner", winner); put("ground_truth", "unmapped"); put("odometer", odo); put("deskew_time_ms", tDeskew); put("deskew_angle", deskewAngle); put("discovery_time_ms", tDiscovery)
        
        val deskewArray = JSONArray()
        deskewBlocks.forEach { block ->
            deskewArray.put(JSONObject().apply {
                put("text", block.text)
                put("cx", block.boundingBox.centerX().toDouble() / 1500.0)
                // Normalize height using the same 1500px aspect ratio scaling logic
                val aspect = discovery.imageHeight.toDouble() / discovery.imageWidth.toDouble()
                put("cy", block.boundingBox.centerY().toDouble() / (1500.0 * aspect))
                put("w", block.boundingBox.width().toDouble() / 1500.0)
                put("h", block.boundingBox.height().toDouble() / (1500.0 * aspect))
                put("angle", block.angle)
            })
        }
        put("deskew_data", deskewArray)

        paddleDiscovery?.let { p ->
            val pArray = JSONArray()
            p.textBlocks.forEach { block ->
                pArray.put(JSONObject().apply {
                    put("text", block.text)
                    put("cx", block.boundingBox.centerX().toDouble() / p.imageWidth.toDouble())
                    put("cy", block.boundingBox.centerY().toDouble() / p.imageHeight.toDouble())
                    put("w", block.boundingBox.width().toDouble() / p.imageWidth.toDouble())
                    put("h", block.boundingBox.height().toDouble() / p.imageHeight.toDouble())
                    put("angle", block.angle)
                })
            }
            put("paddle_deskew_data", pArray)
        }

        val fullImageOcrTimings = JSONObject(); fullImageOcrTimings.put("ML Kit", tDeskew + discovery.executionTimeMs)
        put("has_heatmap", discovery.rawHeatmap != null); put("full_image_ocr_timings", fullImageOcrTimings)
        val vSweepJson = JSONObject(); val safeVehicles = vetoSweep.filter { !it.value.isVetoed }.map { vRes -> vehicles.find { it.id == vRes.key }?.name ?: "Unknown" }
        vSweepJson.put("ML Kit", JSONObject().apply { put("safe_count", safeVehicles.size); put("safe_vehicles", JSONArray(safeVehicles)) }); put("veto_accuracy_sweep", vSweepJson)
        val dResults = JSONObject(); val landmarksArray = JSONArray(); discovery.textBlocks.forEach { block -> 
            val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text); if (cleanedText.length > 1) {
                landmarksArray.put(JSONObject().apply { 
                    put("text", cleanedText); put("cx", block.boundingBox.centerX().toDouble() / discovery.imageWidth.toDouble()); put("cy", block.boundingBox.centerY().toDouble() / discovery.imageHeight.toDouble())
                    put("w", block.boundingBox.width().toDouble() / discovery.imageWidth.toDouble()); put("h", block.boundingBox.height().toDouble() / discovery.imageHeight.toDouble())
                    put("angle", block.angle)
                })
            }
        }; dResults.put("ML Kit", landmarksArray); put("discovery_landmarks", dResults)
        
        val vehicleResults = JSONArray(); vResults.values.forEach { vr -> 
            vehicleResults.put(JSONObject().apply { 
                put("vehicle", vr.vehicleName); put("veto_reason", vr.vetoReason)
                val refDetails = JSONObject()
                vr.refinementTraces.forEach { (strat, trace) -> 
                    val stratObj = JSONObject(); stratObj.put("time_ms", trace.timeMs)
                    val stepsArr = JSONArray(); trace.steps.forEach { step -> val stepObj = JSONObject(); stepObj.put("stage", step.stageName); stepObj.put("text", step.text); stepsArr.put(stepObj) }
                    stratObj.put("steps", stepsArr); refDetails.put(strat, stratObj)
                }
                put("refinement_details", refDetails)
            }) 
        }; put("vehicles", vehicleResults)
    }
}

private fun buildHtmlHeader(time: String, total: Int, strategies: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }</style></head><body>")
    appendLine("<h1>OCR Refinement Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th style='width:200px;'># & Original</th>")
    strategies.forEach { appendLine("<th style='width:300px;'>$it</th>") }
    appendLine("<th style='width:300px;'>Refinement Consensus</th></tr>")
}

private fun buildHtmlRowDynamic(rowIndex: Int, fileName: String, deskewedBase64: String, discovery: String, vehicleResults: Map<Int, SingleVehicleResult>, cachedRefs: List<ReferenceCache>, winnerName: String, strategies: List<String>, tDeskew: Long, tDiscovery: Long): String = buildString {
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms<br><img src='data:image/jpeg;base64,$deskewedBase64'></td>")
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    val allReadings = mutableListOf<String>()
    strategies.forEach { strat ->
        appendLine("<td>")
        if (vRes != null) {
            val trace = vRes.refinementTraces[strat]
            if (trace != null) {
                appendLine("<b>Time:</b> ${trace.timeMs}ms<br>")
                trace.steps.forEach { step -> 
                    if (step.text?.isNotBlank() == true) allReadings.add(step.text)
                    // Scale to 48px high for HTML output
                    val scale = 48f / step.bitmap.height
                    val sw = (step.bitmap.width * scale).toInt()
                    val scaled = Bitmap.createScaledBitmap(step.bitmap, sw, 48, true)
                    appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br><img src='data:image/jpeg;base64,${bitmapToBase64(scaled, 60)}'><br>${step.text ?: "---"}</div>") 
                    scaled.recycle()
                }
            } else appendLine("<i>No refinement data</i>")
        } else appendLine("<i>No match</i>")
        appendLine("</td>")
    }
    
    appendLine("<td><b>Winner:</b> $winnerName<br><br><b>Consensus:</b><br>")
    val freq = allReadings.groupBy { it }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
    freq.forEach { (text, count) -> appendLine("<b>$text</b> ($count/48)<br>") }
    appendLine("</td></tr>")
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val outputStream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream); return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int): String {
    if (bitmap.isRecycled) return ""; val scale = targetWidth.toFloat() / bitmap.width; val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true)
    val b64 = bitmapToBase64(scaled, quality); scaled.recycle(); return b64
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true); val canvas = android.graphics.Canvas(annotated); val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f; color = android.graphics.Color.RED }
    vehicle.odometerCropLeft?.let { l -> canvas.drawRect(l * bmp.width, (vehicle.odometerCropTop ?: 0f) * bmp.height, (vehicle.odometerCropRight ?: 1f) * bmp.width, (vehicle.odometerCropBottom ?: 1f) * bmp.height, paint) }
    return annotated
}

private fun manualCropOdometer(bmp: Bitmap, vehicle: Vehicle): Bitmap? {
    val l = vehicle.odometerCropLeft ?: return null
    val left = (l * bmp.width).toInt().coerceAtLeast(0); val top = ((vehicle.odometerCropTop ?: 0f) * bmp.height).toInt().coerceAtLeast(0)
    val width = (((vehicle.odometerCropRight ?: 1f) - l) * bmp.width).toInt(); val height = (((vehicle.odometerCropBottom ?: 1f) - (vehicle.odometerCropTop ?: 0f)) * bmp.height).toInt()
    if (width <= 0 || height <= 0) return null
    return Bitmap.createBitmap(bmp, left, top, width.coerceAtMost(bmp.width - left), height.coerceAtMost(bmp.height - top))
}

private fun getFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else { val keys = root.keys(); if (keys.hasNext()) root.getJSONArray(keys.next()) else null } ?: return emptyList()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0)
            val cleanText = OdometerOcrUtils.cleanLandmarkString(text); val left = ((cx - w/2.0) * imgW).toInt(); val top = ((cy - h/2.0) * imgH).toInt(); val right = ((cx + w/2.0) * imgW).toInt(); val bottom = ((cy + h/2.0) * imgH).toInt()
            list.add(TextBlock(cleanText, android.graphics.Rect(left, top, right, bottom)))
        }
    } catch (e: Exception) { Log.e("ExperimentAlignment", "Failed to parse landmarks", e) }
    return list
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        if (targetDir.exists()) targetDir.deleteRecursively(); targetDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry; while (entry != null) {
                    val file = File(targetDir, entry.name); if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }; true
    } catch (e: Exception) { false }
}