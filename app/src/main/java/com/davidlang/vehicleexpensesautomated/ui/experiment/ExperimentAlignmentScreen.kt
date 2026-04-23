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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.*
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(currentPhotoName, style = MaterialTheme.typography.labelSmall); Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_PHOTOS_LINK)); context.startActivity(intent) }, modifier = Modifier.fillMaxWidth()) { Text("Open Amazon Photos Album") }
        Button(onClick = { zipLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Extract Downloaded ZIP") }
        Button(onClick = { if (vehicles.isEmpty()) { status = "Error: No vehicles in DB."; return@Button }; scope.launch { isRunning = true; resultsList.clear(); runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }) { res, p -> resultsList.add(res); progress = p; currentPhotoName = res.photoName }; isRunning = false; status = "Complete! Reports saved." } }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
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
    val width: Int,
    val height: Int
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

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    Log.i(TAG, "Starting experiment with ${photos.size} photos")
    val total = photos.size; val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val groundTruth = loadGroundTruth(context)
    val paddleEngine = NativePaddleEngine(context, isConstrained = true)

    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curated = getFullLandmarksFromJson(v.landmarkTextBlocksJson, "ML Kit", bmp.width, bmp.height)
        val annotatedBmp = drawCropBoxesOnReference(bmp, v)
        val refBase64 = createScaledBase64(annotatedBmp, 400, 70)
        val w = bmp.width; val h = bmp.height
        annotatedBmp.recycle(); bmp.recycle()
        ReferenceCache(v, refBase64, curated, w, h)
    }
    
    val jsonResults = JSONArray(); var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    val footer = "</table></body></html>"
    
    // Phase 58 Strategies: 4 Crops x 4 Processing x 2 Engines = 32 Variants
    val cropTypes = listOf("Exact", "Padded", "48px", "48px-Padded")
    val procTypes = listOf("Original", "CLAHE", "Otsu", "CLAHE+Otsu")
    val strategies = mutableListOf<String>()
    cropTypes.forEach { c -> procTypes.forEach { p -> strategies.add("$c ($p)") } }

    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { 
        writeText(buildHtmlHeader(timestamp, total, strategies)) 
    }
    var currentFile = startNewFile()
    
    photos.forEachIndexed { index, file ->
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: throw Exception("Bitmap decode failed")
            var originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            if (originalBitmap != rawBitmap) rawBitmap.recycle()
            
            val deskewedBase64 = createScaledBase64(originalBitmap, 150, 50)
            val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(originalBitmap)
            val tilt = deskewRes.angle; val tDeskewTotal = deskewRes.timeMs
            if (Math.abs(tilt) > 0.2f) { 
                val leveled = OdometerOcrUtils.rotateBitmap(originalBitmap, -tilt)
                if (leveled != originalBitmap) { originalBitmap.recycle(); originalBitmap = leveled }
            }
            val tDiscoveryStart = System.currentTimeMillis()
            val queryOcrDiscovery = OcrHarness.runDiscovery(originalBitmap, context)
            val tDiscoveryTotal = System.currentTimeMillis() - tDiscoveryStart
            val queryLandmarksPrimary = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight)
            val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarksPrimary, cachedRefs.map { it.vehicle }, "ML Kit")
            val hardcodedWinner = groundTruth[file.name.lowercase()]
            val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()

            cachedRefs.forEach { ref ->
                val veto = primaryVetoResults[ref.vehicle.id] ?: VetoResult(false)
                val tMatchStart = System.currentTimeMillis()
                val isWinner = finalWinnerName == "No match" && !veto.isVetoed
                val tMatchTotal = System.currentTimeMillis() - tMatchStart
                var alignmentTrace: AlignmentTraceResult? = null
                val refinementTraces = mutableMapOf<String, RefinementTrace>()
                
                if (isWinner) {
                    val t0 = System.currentTimeMillis()
                    val alignRes = ImageAlignmentUtils.anchorAlign(ref.width, ref.height, originalBitmap, ref.curatedLandmarks, queryLandmarksPrimary, ref.vehicle)
                    val elapsedAlign = System.currentTimeMillis() - t0

                    if (alignRes.success && alignRes.alignedImage != null) {
                        finalWinnerName = ref.vehicle.name // Only confirm winner if alignment succeeded
                        alignmentTrace = AlignmentTraceResult(true, elapsedAlign, createScaledBase64(alignRes.alignedImage, 400, 70), alignRes.metadata)

                        // Phase 58: Refinement Matrix (4 Crops x 4 Processing x 2 Engines)
                        val exactCrop = manualCropOdometer(alignRes.alignedImage, ref.vehicle)
                        if (exactCrop != null && !exactCrop.isRecycled) {
                            val cropVariants = listOf("Exact", "Padded", "48px", "48px-Padded")
                            val processingVariants = listOf("Original", "CLAHE", "Otsu", "CLAHE+Otsu")

                            cropVariants.forEach { cVar ->
                                val baseCrop = when (cVar) {
                                    "Exact" -> exactCrop.copy(Bitmap.Config.ARGB_8888, true)
                                    "Padded" -> OdometerOcrUtils.addPadding(exactCrop, 10, Color.BLACK)
                                    "48px" -> {
                                        val scale = 48f / exactCrop.height.toFloat()
                                        Bitmap.createScaledBitmap(exactCrop, (exactCrop.width * scale).toInt(), 48, true)
                                    }
                                    "48px-Padded" -> {
                                        val scale = 48f / exactCrop.height.toFloat()
                                        val scaled = Bitmap.createScaledBitmap(exactCrop, (exactCrop.width * scale).toInt(), 48, true)
                                        val padded = OdometerOcrUtils.addPadding(scaled, 10, Color.BLACK)
                                        scaled.recycle(); padded
                                    }
                                    else -> null
                                }

                                if (baseCrop != null && !baseCrop.isRecycled) {
                                    processingVariants.forEach { pVar ->
                                        val tRef0 = System.currentTimeMillis()
                                        val procCrop = when (pVar) {
                                            "Original" -> baseCrop.copy(Bitmap.Config.ARGB_8888, true)
                                            "CLAHE" -> OdometerOcrUtils.applyClahe(baseCrop)
                                            "Otsu" -> OdometerOcrUtils.applyOtsu(baseCrop)
                                            "CLAHE+Otsu" -> OdometerOcrUtils.applyClaheOtsu(baseCrop)
                                            else -> null
                                        }

                                        if (procCrop != null && !procCrop.isRecycled) {
                                            val stratName = "$cVar ($pVar)"
                                            val results = mutableListOf<OcrStepResult>()

                                            // Run both engines for each processed crop
                                            listOf("ML Kit", "Paddle-Lite").forEach { engineName ->
                                                try {
                                                    val text = if (engineName == "ML Kit") {
                                                        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                                                        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(procCrop, 0)
                                                        val visionText = recognizer.process(image).await()
                                                        visionText.text.filter { c -> c.isDigit() }
                                                    } else {
                                                        NativePaddleEngine.runConstrainedStatic(procCrop, procCrop.height, paddleEngine.getDictionary())
                                                    }
                                                    results.add(OcrStepResult(engineName, procCrop.copy(Bitmap.Config.ARGB_8888, true), text))
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "OCR Engine $engineName failed for $stratName", e)
                                                }
                                            }
                                            refinementTraces[stratName] = RefinementTrace(stratName, System.currentTimeMillis() - tRef0, results)
                                            procCrop.recycle()
                                        }
                                    }
                                    baseCrop.recycle()
                                }
                            }
                            exactCrop.recycle()
                        }

                        // Final Consensus: Only if alignment was a success
                        val allReadings = refinementTraces.values.flatMap { it.steps }.mapNotNull { it.text }.filter { it.isNotBlank() }
                        if (allReadings.isNotEmpty()) {
                            bestOdometer = allReadings.groupBy { it }.maxBy { it.value.size }.key
                        }
                        alignRes.alignedImage.recycle()
                    } else {
                        alignmentTrace = AlignmentTraceResult(false, elapsedAlign, "", alignRes.metadata)
                        finalWinnerName = "No match" // Explicitly reset if alignment failed
                        alignRes.alignedImage?.recycle()
                    }
                }
                vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(ref.vehicle.name, veto.reasonWord, tMatchTotal, alignmentTrace, refinementTraces, veto.queryWords, veto.myManifest.toList(), veto.vetoPool.toList(), isWinner)
            }

            val rowHtml = buildHtmlRowDynamic(index + 1, file.name, deskewedBase64, queryOcrDiscovery.debugText, vehicleResultsMap, cachedRefs, finalWinnerName, strategies, tDeskewTotal, tDiscoveryTotal)
            val photoJson = serializePhotoResultToJson(index + 1, file.name, finalWinnerName, bestOdometer, tDeskewTotal, tDiscoveryTotal, queryOcrDiscovery, primaryVetoResults, vehicleResultsMap, vehicles, hardcodedWinner, strategies)
            jsonResults.put(photoJson)

            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length
            
            // Clean up refinement Bitmaps immediately after HTML generation
            vehicleResultsMap.values.forEach { vr -> 
                vr.refinementTraces.values.forEach { tr -> 
                    tr.steps.forEach { step -> step.bitmap.recycle() } 
                } 
            }
            originalBitmap.recycle()
        } catch (e: Exception) { 
            Log.e(TAG, "FATAL CRASH processing ${file.name}", e)
        }
        withContext(Dispatchers.Main) { onProgress(PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer), (index + 1).toFloat() / total) }
    }
    currentFile.appendText(footer)
    val finalReport = JSONObject().apply {
        put("timestamp", timestamp); put("total_photos", total)
        val refManifest = JSONObject(); vehicles.forEach { v -> refManifest.put(v.name, if (v.landmarkTextBlocksJson.isNullOrEmpty()) JSONObject() else JSONObject(v.landmarkTextBlocksJson)) }
        put("reference_vehicles", refManifest); put("results", jsonResults)
    }
    File(reportDir, "alignment_results_${timestamp}.json").writeText(finalReport.toString(2))
}

private fun serializePhotoResultToJson(
    lineNumber: Int, fileName: String, winner: String, odo: String, tDeskew: Long, tDiscovery: Long,
    discovery: OcrResult, vetoSweep: Map<Int, VetoResult>, vResults: Map<Int, SingleVehicleResult>,
    vehicles: List<Vehicle>, groundTruth: String?, strategies: List<String>
): JSONObject {
    return JSONObject().apply {
        put("line_number", lineNumber); put("file", fileName); put("winner", winner); put("ground_truth", groundTruth ?: "unmapped"); put("odometer", odo); put("deskew_time_ms", tDeskew); put("discovery_time_ms", tDiscovery)
        val fullImageOcrTimings = JSONObject(); fullImageOcrTimings.put("ML Kit", tDeskew + discovery.executionTimeMs)
        put("has_heatmap", discovery.rawHeatmap != null); put("full_image_ocr_timings", fullImageOcrTimings)
        val vSweepJson = JSONObject(); val safeVehicles = vetoSweep.filter { !it.value.isVetoed }.map { vRes -> vehicles.find { it.id == vRes.key }?.name ?: "Unknown" }
        vSweepJson.put("ML Kit", JSONObject().apply { put("safe_count", safeVehicles.size); put("safe_vehicles", JSONArray(safeVehicles)) }); put("veto_accuracy_sweep", vSweepJson)
        val dResults = JSONObject(); val landmarksArray = JSONArray(); discovery.textBlocks.forEach { block -> 
            val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text); if (cleanedText.length > 1) {
                landmarksArray.put(JSONObject().apply { 
                    put("text", cleanedText); put("cx", block.boundingBox.centerX().toDouble() / discovery.imageWidth.toDouble()); put("cy", block.boundingBox.centerY().toDouble() / discovery.imageHeight.toDouble())
                    put("w", block.boundingBox.width().toDouble() / discovery.imageWidth.toDouble()); put("h", block.boundingBox.height().toDouble() / discovery.imageHeight.toDouble())
                })
            }
        }; dResults.put("ML Kit", landmarksArray); put("discovery_landmarks", dResults)
        
        val vehicleResults = JSONArray(); vResults.values.forEach { vr -> 
            vehicleResults.put(JSONObject().apply { 
                put("vehicle", vr.vehicleName); put("veto_reason", vr.vetoReason)
                val refDetails = JSONObject()
                vr.refinementTraces.forEach { (strat, trace) -> 
                    val stratObj = JSONObject(); stratObj.put("time_ms", trace.timeMs)
                    val stepsArr = JSONArray(); trace.steps.forEach { step -> 
                        val stepObj = JSONObject(); stepObj.put("engine", step.stageName); stepObj.put("text", step.text); stepsArr.put(stepObj) 
                    }
                    stratObj.put("variants", stepsArr); refDetails.put(strat, stratObj)
                }
                put("refinement_matrix", refDetails)
            }) 
        }; put("vehicles", vehicleResults)
    }
}

private fun buildHtmlHeader(time: String, total: Int, strategies: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 16px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 14px; text-align: left; } .consensus { background: #f0f8ff; font-weight: bold; }</style></head><body>")
    appendLine("<h1>OCR Refinement Experiment (Phase 58)</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th style='width:120px;'># & Original</th>")
    strategies.forEach { appendLine("<th style='width:180px;'>$it</th>") }
    appendLine("<th style='width:200px;'>Refinement Consensus</th></tr>")
}

private fun buildHtmlRowDynamic(rowIndex: Int, fileName: String, deskewedBase64: String, discovery: String, vehicleResults: Map<Int, SingleVehicleResult>, cachedRefs: List<ReferenceCache>, winnerName: String, strategies: List<String>, tDeskew: Long, tDiscoveryTotal: Long): String = buildString {
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscoveryTotal}ms<br><img src='data:image/jpeg;base64,$deskewedBase64'></td>")
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    val allReadings = mutableListOf<String>()
    strategies.forEach { strat ->
        appendLine("<td>")
        if (vRes != null && vRes.refinementTraces.containsKey(strat)) {
            val trace = vRes.refinementTraces[strat]
            if (trace != null) {
                appendLine("<b>Time:</b> ${trace.timeMs}ms<br>")
                trace.steps.forEach { step -> 
                    if (step.text?.isNotBlank() == true) allReadings.add(step.text)
                    // Scale all images to same height (60px) for column comparison
                    val b64 = if (!step.bitmap.isRecycled) createScaledBase64ByHeight(step.bitmap, 60, 70) else ""
                    appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br><img src='data:image/jpeg;base64,$b64'><br><b>${step.text ?: "---"}</b></div>") 
                }
            } else appendLine("<i>No refinement data</i>")
        } else appendLine("<i>No match</i>")
        appendLine("</td>")
    }
    
    appendLine("<td class='consensus'><b>Winner:</b> $winnerName<br><br><b>Consensus:</b><br>")
    val freq = allReadings.groupBy { it }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
    freq.forEach { (text, count) -> appendLine("<b>$text</b> ($count/32)<br>") }
    appendLine("</td></tr>")
}

private fun createScaledBase64ByHeight(bitmap: Bitmap, targetHeight: Int, quality: Int): String {
    if (bitmap.isRecycled) return ""; val scale = targetHeight.toFloat() / bitmap.height; val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), targetHeight, true)
    val b64 = bitmapToBase64(scaled, quality); scaled.recycle(); return b64
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

private fun loadGroundTruth(context: Context): Map<String, String> {
    val file = File(context.filesDir, "ground_truth.json"); if (!file.exists()) return emptyMap()
    return try { val json = JSONObject(file.readText()); val map = mutableMapOf<String, String>(); val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); map[key.lowercase()] = json.getString(key) }; map } catch (e: Exception) { emptyMap() }
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