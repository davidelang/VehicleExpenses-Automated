package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

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

data class ReferenceCache(val vehicle: Vehicle, val referenceBase64: String, val curatedLandmarks: List<TextBlock>, val ocrResult: OcrResult, val bmp: Bitmap)

data class AlignmentTraceResult(
    val strategyName: String,
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
    val ocrTraces: List<OcrStepResult>,
    val metadata: Map<String, String> = emptyMap()
)

data class SingleVehicleResult(
    val vehicleName: String,
    val vetoReason: String,
    val matchTimeMs: Long,
    val alignmentTraces: Map<String, AlignmentTraceResult>,
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val identityResults: Map<String, AlignmentResult>
)

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size; val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    
    val groundTruth = loadGroundTruth(context)
    val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
    val primaryIdentityEngine = prefs.getString("primary_identity_pref", "hardcoded") ?: "hardcoded"
    val anchorSourceEngine = prefs.getString("anchor_source_pref", "ML Kit") ?: "ML Kit"

    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curatedBlocks = getFullLandmarksFromJson(v.landmarkTextBlocksJson)
        val refOcr = OcrResult(engineName = "Curated DB", textBlocks = curatedBlocks, imageWidth = bmp.width, imageHeight = bmp.height, debugText = curatedBlocks.joinToString(" ") { it.text })
        val annotatedBmp = drawCropBoxesOnReference(bmp, v); val refBase64 = createScaledBase64(annotatedBmp, 400, 70); annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curatedBlocks, refOcr, bmp)
    }
    
    val jsonArray = JSONArray(); var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    val activeAlignments = AlignmentRegistry.getActiveEngines().map { it.name }
    
    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { 
        writeText(buildHtmlHeader(timestamp, total, cachedRefs.map { it.vehicle }, activeAlignments)) 
    }
    var currentFile = startNewFile(); val footer = "</table></body></html>"
    
    photos.forEachIndexed { index, file ->
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: throw Exception("Bitmap decode failed")
            var originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            
            // CRITICAL FIX 1: Capture main thumbnail BEFORE any deskewing logic can recycle the bitmap
            val deskewedBase64 = createScaledBase64(originalBitmap, 150, 50)
            
            // 1. IDENTITY STAGE
            val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(originalBitmap)
            val tilt = deskewRes.angle
            val tDeskewTotal = deskewRes.timeMs

            if (Math.abs(tilt) > 0.2f) { 
                val leveled = OdometerOcrUtils.rotateBitmap(originalBitmap, -tilt)
                if (leveled != originalBitmap) { originalBitmap.recycle(); originalBitmap = leveled }
            }
            
            val tDiscoveryStart = System.currentTimeMillis()
            val discoveryResults = OcrHarness.runDiscovery(originalBitmap, context)
            val tDiscoveryTotal = System.currentTimeMillis() - tDiscoveryStart

            val queryOcrDiscovery = discoveryResults[anchorSourceEngine] ?: discoveryResults["ML Kit"]!!
            val queryLandmarks = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight, stripPunctuation = (anchorSourceEngine == "ML Kit"))
            
            // MULTI-SOURCE VETO SWEEP
            val vetoSweep = discoveryResults.mapValues { (name, ocrRes) ->
                // Apply punctuation stripping to ALL engines for now (e.g., -20 -> 20)
                val engineLandmarks = OdometerOcrUtils.processRawLandmarks(ocrRes.textBlocks, null, null, ocrRes.imageWidth, ocrRes.imageHeight, stripPunctuation = true)
                ImageAlignmentUtils.performTier1Veto(engineLandmarks, cachedRefs.map { it.vehicle })
            }
            
            val primaryVetoResults = vetoSweep[anchorSourceEngine] ?: vetoSweep["ML Kit"]!!
            val hardcodedWinner = groundTruth[file.name.lowercase()]
            val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
            val identityEngines = IdentityRegistry.getActiveEngines()

            cachedRefs.forEach { ref ->
                val veto = primaryVetoResults[ref.vehicle.id] ?: VetoResult(false)
                val forceWinner = (hardcodedWinner != null && hardcodedWinner == ref.vehicle.name)
                
                val tMatchStart = System.currentTimeMillis()
                val matchResults = mutableMapOf<String, AlignmentResult>()
                
                val odoCrop = ref.vehicle.odometerCropLeft?.let { l -> RectF(l, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
                val otherCrop = ref.vehicle.otherTextCropLeft?.let { l -> RectF(l, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }
                
                for (engine in identityEngines) {
                    val t0 = System.currentTimeMillis()
                    val result = engine.identify(ref.bmp, originalBitmap, ref.ocrResult, queryOcrDiscovery, odoCrop, otherCrop, ref.vehicle, veto, forceWinner)
                    matchResults[engine.name] = result.copy(timeMs = System.currentTimeMillis() - t0)
                }
                
                val primaryResult = matchResults[primaryIdentityEngine] ?: AlignmentResult(false, null, -1f, "Primary Engine Missing")
                val isWinner = finalWinnerName == "No match" && primaryResult.confidence > 0.5f
                val tMatchTotal = System.currentTimeMillis() - tMatchStart
                val alignmentTraces = mutableMapOf<String, AlignmentTraceResult>()
                
                if (isWinner) {
                    finalWinnerName = ref.vehicle.name
                    AlignmentRegistry.getActiveEngines().forEach { engine ->
                        val t0 = System.currentTimeMillis()
                        val alignRes = engine.align(ref.bmp, originalBitmap, ref.curatedLandmarks, queryLandmarks, ref.vehicle)
                        val elapsed = System.currentTimeMillis() - t0
                        if (alignRes.success && alignRes.alignedImage != null) {
                            val crop = manualCropOdometer(alignRes.alignedImage, ref.vehicle)
                            if (crop != null) {
                                val steps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                                alignmentTraces[engine.name] = AlignmentTraceResult(engine.name, true, elapsed, createScaledBase64(alignRes.alignedImage, 400, 70), steps, alignRes.metadata)
                                crop.recycle()
                            }
                            alignRes.alignedImage.recycle()
                        } else {
                            alignmentTraces[engine.name] = AlignmentTraceResult(engine.name, false, elapsed, "", emptyList(), alignRes.metadata)
                        }
                    }
                    bestOdometer = OdometerOcrUtils.pickBestOdometer(alignmentTraces.values.flatMap { it.ocrTraces }) ?: "FAILED"
                }
                vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(ref.vehicle.name, veto.reasonWord, tMatchTotal, alignmentTraces, veto.queryWords, veto.myManifest.toList(), veto.vetoPool.toList(), matchResults)
            }

            val rowHtml = buildHtmlRowDynamic(index + 1, file.name, deskewedBase64, queryOcrDiscovery.debugText, vehicleResultsMap, cachedRefs, finalWinnerName, bestOdometer, activeAlignments, tDeskewTotal, tDiscoveryTotal)
            
            val photoJson = serializePhotoResultToJson(
                index + 1, file.name, finalWinnerName, bestOdometer, tDeskewTotal, tDiscoveryTotal,
                discoveryResults, vetoSweep, vehicleResultsMap, vehicles
            )
            jsonArray.put(photoJson)

            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length

            // CRITICAL FIX 2: Cleanup intermediate bitmaps AFTER HTML generation
            vehicleResultsMap.values.forEach { vr -> 
                vr.alignmentTraces.values.forEach { trace -> 
                    trace.ocrTraces.forEach { step -> step.bitmap.recycle() } 
                } 
            }
            
            // FINAL CLEANUP
            originalBitmap.recycle()
            
        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
        withContext(Dispatchers.Main) { onProgress(PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer), (index + 1).toFloat() / total) }
    }
    currentFile.appendText(footer); File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2)); cachedRefs.forEach { it.bmp.recycle() }
}

private fun serializePhotoResultToJson(
    index: Int, fileName: String, winner: String, odo: String, tDeskew: Long, tDiscovery: Long,
    discovery: Map<String, OcrResult>, vetoSweep: Map<String, Map<Int, VetoResult>>,
    vResults: Map<Int, SingleVehicleResult>, vehicles: List<Vehicle>
): JSONObject {
    return JSONObject().apply {
        put("index", index); put("file", fileName); put("winner", winner); put("odometer", odo); put("deskew_time_ms", tDeskew); put("discovery_time_ms", tDiscovery)
        
        val fullImageOcrTimings = JSONObject()
        discovery.forEach { (name, res) -> fullImageOcrTimings.put(name, if (name == "ML Kit") tDeskew + res.executionTimeMs else res.executionTimeMs) }
        put("has_heatmap", discovery.values.any { it.rawHeatmap != null })
        put("full_image_ocr_timings", fullImageOcrTimings)
        
        val vSweepJson = JSONObject()
        vetoSweep.forEach { (engine, results) ->
            val safeVehicles = results.filter { vRes -> !vRes.value.isVetoed }.map { vRes -> vehicles.find { it.id == vRes.key }?.name ?: "Unknown" }
            vSweepJson.put(engine, JSONObject().apply { put("safe_count", safeVehicles.size); put("safe_vehicles", JSONArray(safeVehicles)) })
        }
        put("veto_accuracy_sweep", vSweepJson)

        val dResults = JSONObject()
        discovery.forEach { (name, res) ->
            val landmarksArray = JSONArray()
            res.textBlocks.forEach { block -> 
                landmarksArray.put(JSONObject().apply { 
                    put("text", block.text); put("cx", block.boundingBox.centerX()); put("cy", block.boundingBox.centerY()); put("w", block.boundingBox.width()); put("h", block.boundingBox.height())
                    val metaJson = JSONObject(); block.metadata.forEach { (k, v) -> metaJson.put(k, v) }; put("metadata", metaJson) 
                }) 
            }
            dResults.put(name, landmarksArray)
        }
        put("discovery_landmarks", dResults)

        val vehicleResults = JSONArray()
        vResults.values.forEach { vr -> 
            vehicleResults.put(JSONObject().apply { 
                put("vehicle", vr.vehicleName); put("veto_reason", vr.vetoReason); put("veto_my_manifest", JSONArray(vr.vetoMyManifest)); put("veto_pool", JSONArray(vr.vetoPool))
                val identityMethods = JSONObject()
                vr.identityResults.forEach { (name, res) -> 
                    identityMethods.put(name, JSONObject().apply { 
                        put("success", res.success); put("confidence", res.confidence.toDouble()); put("time_ms", res.timeMs)
                        val metaJson = JSONObject(); res.metadata.forEach { (k,v) -> metaJson.put(k,v) }; put("metadata", metaJson) 
                    }) 
                }
                put("identity_methods", identityMethods)
                val traces = JSONObject()
                vr.alignmentTraces.forEach { (name, trace) -> 
                    traces.put(name, JSONObject().apply { 
                        put("success", trace.success); put("time_ms", trace.timeMs)
                        trace.metadata.forEach { (mk, mv) -> put(mk.lowercase().replace(" ", "_"), mv) } 
                    }) 
                }
                put("traces", traces) 
            }) 
        }
        put("vehicles", vehicleResults)
    }
}

private fun buildHtmlHeader(time: String, total: Int, vehicles: List<Vehicle>, alignNames: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 36px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 4px; text-align: left; } .word-list { font-size: 28px; color: #666; height: 160px; overflow-y: scroll; }</style></head><body>")
    appendLine("<h1>Deep Trace Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th style='width:300px;'># & Original</th>")
    alignNames.forEach { appendLine("<th style='width:600px;'>$it Alignment</th>") }
    appendLine("<th style='width:400px;'>Final Result</th></tr>")
}

private fun buildHtmlRowDynamic(rowIndex: Int, fileName: String, deskewedBase64: String, discovery: String, vehicleResults: Map<Int, SingleVehicleResult>, cachedRefs: List<ReferenceCache>, winnerName: String, bestOdo: String, alignNames: List<String>, tDeskew: Long, tDiscovery: Long): String = buildString {
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms<br><img src='data:image/jpeg;base64,$deskewedBase64'><br><small>$discovery</small></td>")
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    alignNames.forEach { alignName ->
        appendLine("<td>")
        if (vRes != null) {
            val trace = vRes.alignmentTraces[alignName]
            if (trace != null && trace.success) {
                appendLine("<b>Time:</b> ${trace.timeMs}ms<br>")
                if (trace.metadata.isNotEmpty()) { appendLine("<small>"); trace.metadata.forEach { (k, v) -> appendLine("<b>$k:</b> $v<br>") }; appendLine("</small><br>") }
                appendLine("<img src='data:image/jpeg;base64,${trace.alignedImageBase64}'><br><hr>")
                trace.ocrTraces.forEach { step -> appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br><img src='data:image/jpeg;base64,${createScaledBase64(step.bitmap, 200, 60)}'><br>${step.text}</div>") }
            } else appendLine("<i>Alignment failed or skipped</i>")
        } else appendLine("<i>No match found</i>")
        appendLine("</td>")
    }
    appendLine("<td><b>Winner:</b> $winnerName<br><b>Odo:</b> $bestOdo<br>")
    if (vRes != null) {
        appendLine("<br><b>Identity Scores:</b><br><small>")
        vRes.identityResults.forEach { (name, res) ->
            val color = if (res.confidence > 0.5f) "green" else "gray"
            appendLine("<span style='color:$color'>$name: %.2f</span>".format(res.confidence))
            if (res.metadata.isNotEmpty()) { appendLine("<br><i style='font-size:24px;'>${res.metadata}</i>") }
            appendLine("<br>")
        }
        appendLine("</small>")
    }
    appendLine("</td></tr>")
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int): String {
    if (bitmap.isRecycled) return ""
    val scale = targetWidth.toFloat() / bitmap.width; val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true)
    val b64 = bitmapToBase64(scaled, quality); scaled.recycle(); return b64
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true); val canvas = android.graphics.Canvas(annotated)
    val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f; color = android.graphics.Color.RED }
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
    val file = File(context.filesDir, "ground_truth.json")
    if (!file.exists()) return emptyMap()
    return try { val json = JSONObject(file.readText()); val map = mutableMapOf<String, String>(); val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); map[key.lowercase()] = json.getString(key) }; map } catch (e: Exception) { emptyMap() }
}

private fun getFullLandmarksFromJson(json: String?): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<TextBlock>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text")
            val cx = obj.getInt("cx"); val cy = obj.getInt("cy"); val h = obj.getInt("h"); val w = obj.getInt("w")
            val cleanText = OdometerOcrUtils.cleanLandmarkString(text, stripPunctuation = true)
            list.add(TextBlock(cleanText, android.graphics.Rect(cx - w/2, cy - h/2, cx + w/2, cy + h/2)))
        }
    } catch (e: Exception) { }
    return list
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        if (targetDir.exists()) targetDir.deleteRecursively(); targetDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { false }
}
