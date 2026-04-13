package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    Scaffold(topBar = { TopAppBar(title = { Text("Alignment Experiment") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
}

data class ReferenceCache(val vehicle: Vehicle, val referenceBase64: String, val curatedLandmarks: List<TextBlock>, val ocrResult: OcrResult, val bmp: Bitmap)

data class SingleVehicleResult(
    val vehicleName: String,
    val confidence: Float,
    val vetoReason: String,
    val matchTimeMs: Long,
    val orbBase64: String,
    val anchorBase64: String,
    val traceData: Map<String, List<OcrStepResult>>,
    val methodScores: Map<String, Float>,
    val methodTimes: Map<String, Long>,
    val tierReached: Int,
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val anchorStrategy: String,
    val anchorsUsed: List<String>,
    val anchorTimeMs: Long
)

private suspend fun processSingleVehicleMatch(ref: ReferenceCache, originalBitmap: Bitmap, queryOcrMl: OcrResult, queryLandmarks: List<TextBlock>, veto: VetoResult, forceAlignment: Boolean, context: Context): SingleVehicleResult {
    val tStart = System.currentTimeMillis()
    val odoCropF = ref.vehicle.odometerCropLeft?.let { android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
    val otherCropF = ref.vehicle.otherTextCropLeft?.let { android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }
    
    // Optimization: Skip alignment if vetoed AND not forced
    val skipAlignment = veto.isVetoed && !forceAlignment
    
    val matchResults = ImageAlignmentUtils.matchWithAllMethods(ref.bmp, originalBitmap, ref.ocrResult, queryOcrMl, odoCropF, otherCropF, skipExpensiveORB = skipAlignment, veto = veto)
    val tiered = matchResults["tiered"]!!
    val orbRes = matchResults["feature"]!!
    val tMatch = System.currentTimeMillis() - tStart

    // Execute Anchor Alignment-triangle
    val anchorRes = if (!skipAlignment) {
        ImageAlignmentUtils.anchorAlign(ref.bmp, originalBitmap, ref.curatedLandmarks, queryLandmarks)
    } else AnchorResult(false)

    val traceData = mutableMapOf<String, List<OcrStepResult>>()
    if (orbRes.success && orbRes.alignedImage != null) { val crop = manualCropOdometer(orbRes.alignedImage, ref.vehicle); if (crop != null) { traceData["ORB"] = OdometerOcrUtils.runMultiStepOcr(crop, context); crop.recycle() } }
    if (anchorRes.success && anchorRes.alignedImage != null) { val crop = manualCropOdometer(anchorRes.alignedImage, ref.vehicle); if (crop != null) { traceData["Anchor-Tri"] = OdometerOcrUtils.runMultiStepOcr(crop, context); crop.recycle() } }

    val orbBase64 = if (orbRes.alignedImage != null) createScaledBase64(orbRes.alignedImage, 400, 70) else ""
    val anchorBase64 = if (anchorRes.alignedImage != null) createScaledBase64(anchorRes.alignedImage, 400, 70) else ""
    orbRes.alignedImage?.recycle(); anchorRes.alignedImage?.recycle()

    return SingleVehicleResult(
        vehicleName = ref.vehicle.name, confidence = tiered.confidence, vetoReason = tiered.vetoReason ?: "", matchTimeMs = tMatch, 
        orbBase64 = orbBase64, anchorBase64 = anchorBase64, traceData = traceData, 
        methodScores = matchResults.mapValues { it.value.confidence }, methodTimes = matchResults.mapValues { it.value.timeMs }, 
        tierReached = tiered.tierReached, vetoQueryWords = veto.queryWords, vetoMyManifest = veto.myManifest, vetoPool = veto.vetoPool,
        anchorStrategy = anchorRes.strategy, anchorsUsed = anchorRes.anchorsUsed, anchorTimeMs = anchorRes.timeMs
    )
}

private fun loadGroundTruth(context: Context): Map<String, String> {
    val file = File(context.filesDir, "ground_truth.json")
    if (!file.exists()) return emptyMap()
    return try {
        val json = JSONObject(file.readText())
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Store as lowercase key for case-insensitive lookup
            map[key.lowercase()] = json.getString(key)
        }
        map
    } catch (e: Exception) { Log.e(TAG, "Failed to load ground_truth.json", e); emptyMap() }
}

private fun getFullLandmarksFromJson(json: String?): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<TextBlock>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text")
            val cx = obj.getInt("cx"); val cy = obj.getInt("cy"); val h = obj.getInt("h"); val w = obj.getInt("w")
            val left = cx - (w / 2); val top = cy - (h / 2)
            list.add(TextBlock(text, android.graphics.Rect(left, top, left + w, top + h)))
        }
    } catch (e: Exception) { Log.e(TAG, "Landmark parse failed", e) }
    return list
}

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size; val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    
    val groundTruth = loadGroundTruth(context)
    if (groundTruth.isEmpty()) { withContext(Dispatchers.Main) { onLog("Warning: No ground_truth.json found in files/") } }
    
    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curatedBlocks = getFullLandmarksFromJson(v.landmarkTextBlocksJson)
        val refOcr = OcrResult(engineName = "Curated DB", textBlocks = curatedBlocks, imageWidth = bmp.width, imageHeight = bmp.height, debugText = curatedBlocks.joinToString(" ") { it.text })
        val annotatedBmp = drawCropBoxesOnReference(bmp, v); val refBase64 = createScaledBase64(annotatedBmp, 400, 70); annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curatedBlocks, refOcr, bmp)
    }
    val landmarkManifest = JSONObject()
    cachedRefs.forEach { ref -> landmarkManifest.put(ref.vehicle.name, JSONArray(ref.curatedLandmarks.map { it.text }.sorted())) }
    File(reportDir, "alignment_landmarks_${timestamp}.json").writeText(landmarkManifest.toString(2))
    
    val jsonArray = JSONArray(); var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    fun startNewFile(allVehicles: List<Vehicle>) = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { writeText(buildHtmlHeader(timestamp, total, allVehicles)) }
    var currentFile = startNewFile(vehicles); val footer = "</table></body></html>"
    
    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: return@forEachIndexed
            var originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            val tilt = OdometerOcrUtils.calculateAverageTextAngle(originalBitmap)
            if (Math.abs(tilt) > 0.2f) { val leveled = OdometerOcrUtils.rotateBitmap(originalBitmap, -tilt); if (leveled != originalBitmap) { originalBitmap.recycle(); originalBitmap = leveled } }
            val grayBitmap = OdometerOcrUtils.applyGrayscale(originalBitmap); val bileBitmap = OdometerOcrUtils.applyBilateral(originalBitmap)
            val globalOcrResultsMap = mutableMapOf<String, Map<String, Pair<String, Long>>>()
            mapOf("Original" to originalBitmap, "Grayscale" to grayBitmap, "Bilateral" to bileBitmap).forEach { (ver, bmp) -> val res = OcrHarness.runAll(bmp, context); globalOcrResultsMap[ver] = res.mapValues { it.value.debugText to it.value.executionTimeMs } }
            val queryOcrMl = OcrHarness.runAll(originalBitmap, context)["ML Kit"]!!; val queryLandmarks = OdometerOcrUtils.discoverLandmarksFromBitmap(originalBitmap)
            val qLandmarkDisplays = queryLandmarks.map { "${it.text} (${"%.1f".format(it.angle)}°)" }.sorted()
            
            val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, cachedRefs.map { it.vehicle })
            
            val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
            val strategyTraces = mutableMapOf<String, List<OcrStepResult>>()
            
            // DECISION OVERRIDE: Check Ground Truth first (Case-insensitive)
            val hardcodedWinner = groundTruth[file.name.lowercase()]
            
            cachedRefs.forEach { ref ->
                val veto = vetoResults[ref.vehicle.id] ?: VetoResult(false)
                // Force alignment if this is the hardcoded winner
                val isHardcodedWinner = (hardcodedWinner != null && hardcodedWinner == ref.vehicle.name)
                
                val vRes = processSingleVehicleMatch(ref, originalBitmap, queryOcrMl, queryLandmarks, veto, forceAlignment = isHardcodedWinner, context)
                vehicleResultsMap[ref.vehicle.id] = vRes
                strategyTraces["${ref.vehicle.name}_ORB"] = vRes.traceData["ORB"] ?: emptyList()
                strategyTraces["${ref.vehicle.name}_Anchor-Tri"] = vRes.traceData["Anchor-Tri"] ?: emptyList()
            }

            var winnerName = "No match"
            var bestConf = 0f
            var pickedOdometer = "FAILED"
            var decisionReason = "Standard Tiered Logic"

            if (hardcodedWinner != null) {
                winnerName = hardcodedWinner
                bestConf = 1.0f
                decisionReason = "HARDCODED OVERRIDE"
                // Pick odo from the forced winner
                val winnerRef = cachedRefs.find { it.vehicle.name == hardcodedWinner }
                winnerRef?.let {
                    val vRes = vehicleResultsMap[it.vehicle.id]!!
                    pickedOdometer = OdometerOcrUtils.pickBestOdometer((vRes.traceData["ORB"] ?: emptyList()) + (vRes.traceData["Anchor-Tri"] ?: emptyList())) ?: "FAILED"
                }
            } else {
                // MISSING from map -> Strictly No Match as requested
                winnerName = "No match"
                bestConf = 0f
                decisionReason = "NOT IN GROUND TRUTH MAP"
            }

            val candidates = cachedRefs.filter { it.vehicle.name == winnerName }
            val rowHtml = buildHtmlRowFoundation(file.name, mapOf("Original" to createScaledBase64(originalBitmap, 150, 50), "Grayscale" to createScaledBase64(grayBitmap, 150, 50), "Bilateral" to createScaledBase64(bileBitmap, 150, 50)), globalOcrResultsMap, qLandmarkDisplays, vehicleResultsMap, candidates, winnerName, bestConf, pickedOdometer, strategyTraces, decisionReason)
            
            if (currentSize + rowHtml.length > maxSizeBytes) { currentSize = 0; currentFile.appendText(footer); currentFile = startNewFile(vehicles) }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length
            
            jsonArray.put(JSONObject().apply {
                put("file", file.name); put("winner", winnerName); put("confidence", bestConf.toDouble()); put("odometer", pickedOdometer); put("decision_reason", decisionReason)
                val vArray = JSONArray()
                vehicleResultsMap.values.forEach { vRes ->
                    vArray.put(JSONObject().apply {
                        put("name", vRes.vehicleName); put("score", vRes.confidence.toDouble()); put("match_time_ms", vRes.matchTimeMs); put("veto_word", vRes.vetoReason)
                        put("tier_reached", vRes.tierReached); put("veto_query_words", JSONArray(vRes.vetoQueryWords)); put("veto_my_manifest", JSONArray(vRes.vetoMyManifest)); put("veto_pool", JSONArray(vRes.vetoPool))
                        put("method_scores", JSONObject().apply { vRes.methodScores.forEach { (k, v) -> put(k, v.toDouble()) } }); put("method_times", JSONObject().apply { vRes.methodTimes.forEach { (k, v) -> put(k, v) } })
                        put("anchor_strategy", vRes.anchorStrategy); put("anchors_used", JSONArray(vRes.anchorsUsed)); put("anchor_time_ms", vRes.anchorTimeMs)
                    })
                }
                put("vehicles", vArray)
            })
            withContext(Dispatchers.Main) { onProgress(PhotoResultSummary(file.name, winnerName, bestConf, pickedOdometer), (index + 1).toFloat() / total) }
            grayBitmap.recycle(); bileBitmap.recycle(); originalBitmap.recycle(); strategyTraces.values.flatten().forEach { it.bitmap.recycle() }
        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }
    currentFile.appendText(footer); File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2)); cachedRefs.forEach { it.bmp.recycle() }
}

private fun headerLength(f: File): Int = f.readText().length

private fun buildHtmlHeader(time: String, total: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<html><head><title>Alignment Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 10px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 150px; height: auto; border: 1px solid #eee; } .score-box { text-align: left; font-size: 9px; background: #f9f9f9; padding: 4px; border-radius: 4px; overflow-wrap: break-word; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px; } .word-list { text-align: left; font-size: 8px; color: #666; background: #fff; border: 1px solid #eee; padding: 2px; height: 60px; overflow-y: scroll; }</style></head><body>")
    appendLine("<h1>Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p>")
    appendLine("<h3>Reference Landmark Manifest (Curated DB)</h3><ul>")
    allVehicles.forEach { v -> val landmarks = ImageAlignmentUtils.getLandmarksFromJson(v.landmarkTextBlocksJson).sorted(); appendLine("<li><b>${v.name}:</b> ${landmarks.joinToString(", ")}</li>") }
    appendLine("</ul><table><tr><th style='width:80px;'># & Photo</th><th style='width:160px;'>Original / Discovery</th><th style='width:300px;'>Candidate Match Results (Tier 1-3 Only)</th><th style='width:120px;'>Final Result</th></tr>")
}

private fun buildHtmlRowFoundation(photoName: String, globalBase64s: Map<String, String>, globalOcr: Map<String, Map<String, Pair<String, Long>>>, queryLandmarks: List<String>, vehicleResultsMap: Map<Int, SingleVehicleResult>, candidates: List<ReferenceCache>, winnerName: String, bestConf: Float, pickedOdo: String, traces: Map<String, List<OcrStepResult>>, decisionReason: String): String = buildString {
    appendLine("<tr><td><small>$photoName</small></td><td>")
    globalBase64s.forEach { (ver, b64) -> appendLine("<b>$ver:</b><br><img src='data:image/jpeg;base64,$b64'><br>"); globalOcr[ver]?.forEach { (eng, res) -> appendLine("<small><b>$eng:</b> ${res.first} (${res.second}ms)</small><br>") } }
    appendLine("<hr><b>Tier 1 Pass Landmarks:</b><br><small>${queryLandmarks.joinToString(", ")}</small></td>")
    appendLine("<td>")
    if (candidates.isEmpty()) {
        appendLine("<i>No candidates passed. Reason: $decisionReason</i><br>")
        vehicleResultsMap.values.forEach { res ->
            appendLine("<div style='border:1px solid #fee; margin-bottom:4px; padding:2px;'>")
            appendLine("<b>${res.vehicleName}:</b> Vetoed by: <span style='color:red;'>${res.vetoReason}</span><br>")
            appendLine("<b>Veto Manifest:</b> <div class='word-list'>${res.vetoMyManifest.joinToString(", ")}</div>")
            appendLine("<b>Veto Pool (Others - Me):</b> <div class='word-list'>${res.vetoPool.joinToString(", ")}</div>")
            appendLine("</div>")
        }
    } else {
        candidates.forEach { ref ->
            val vRes = vehicleResultsMap[ref.vehicle.id]!!; val isWinner = winnerName == ref.vehicle.name
            appendLine("<div class='${if (isWinner) "winner" else ""}' style='border:1px solid #ddd; margin-bottom:10px; padding:4px;'>")
            appendLine("<div class='score-box'><b>${ref.vehicle.name}</b> (Decision: $decisionReason)<br>Score: ${"%.3f".format(vRes.confidence)} | Tier: ${vRes.tierReached}</div>")
            appendLine("<b>Ref:</b><br><img src='data:image/jpeg;base64,${ref.referenceBase64}'><br>")
            appendLine("<b>Anchor Alignment (${vRes.anchorTimeMs}ms):</b><br><small>Strategy: ${vRes.anchorStrategy}</small><br><small>Words: ${vRes.anchorsUsed.joinToString(", ")}</small><br><img src='data:image/jpeg;base64,${vRes.anchorBase64}'><br>")
            appendLine("<b>ORB Alignment:</b><br><img src='data:image/jpeg;base64,${vRes.orbBase64}'><br>")
            appendLine("<hr><b>Anchor OCR:</b><br>${buildOcrStepHtml(traces["${ref.vehicle.name}_Anchor-Tri"] ?: emptyList())}")
            appendLine("<hr><b>ORB OCR:</b><br>${buildOcrStepHtml(traces["${ref.vehicle.name}_ORB"] ?: emptyList())}")
            appendLine("</div>")
        }
    }
    appendLine("</td><td><b>Match:</b> $winnerName<br><b>Conf:</b> ${"%.2f".format(bestConf)}<br><b>Odo:</b> $pickedOdo</td></tr>")
}

private fun buildOcrStepHtml(steps: List<OcrStepResult>): String = buildString { if (steps.isEmpty()) { appendLine("<i>(No crop)</i>"); return@buildString }; steps.forEach { step -> appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br>${step.text}</div>") } }

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String { val q = quality.coerceIn(0, 100); val outputStream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, q, outputStream); return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP) }

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int): String { val scale = targetWidth.toFloat() / bitmap.width; val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true); val b64 = bitmapToBase64(scaled, quality); scaled.recycle(); return b64 }

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true); val canvas = android.graphics.Canvas(annotated); val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f }
    vehicle.odometerCropLeft?.let { l -> paint.color = android.graphics.Color.RED; canvas.drawRect(l * bmp.width, (vehicle.odometerCropTop ?: 0f) * bmp.height, (vehicle.odometerCropRight ?: 1f) * bmp.width, (vehicle.odometerCropBottom ?: 1f) * bmp.height, paint) }
    vehicle.otherTextCropLeft?.let { l -> paint.color = android.graphics.Color.BLUE; canvas.drawRect(l * bmp.width, (vehicle.otherTextCropTop ?: 0f) * bmp.height, (vehicle.otherTextCropRight ?: 1f) * bmp.width, (vehicle.otherTextCropBottom ?: 1f) * bmp.height, paint) }
    return annotated
}

private fun manualCropOdometer(bmp: Bitmap, vehicle: Vehicle): Bitmap? {
    val l = vehicle.odometerCropLeft ?: return null; val t = vehicle.odometerCropTop ?: 0f; val r = vehicle.odometerCropRight ?: 1f; val b = vehicle.odometerCropBottom ?: 1f
    val left = (l * bmp.width).toInt().coerceAtLeast(0); val top = (t * bmp.height).toInt().coerceAtLeast(0); val width = ((r - l) * bmp.width).toInt().coerceAtMost(bmp.width - left); val height = ((b - t) * bmp.height).toInt().coerceAtMost(bmp.height - top)
    if (width <= 0 || height <= 0) return null; return Bitmap.createBitmap(bmp, left, top, width, height)
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try { if (targetDir.exists()) targetDir.deleteRecursively(); targetDir.mkdirs(); context.contentResolver.openInputStream(uri)?.use { inputStream -> ZipInputStream(inputStream).use { zis -> var entry = zis.nextEntry; while (entry != null) { val file = File(targetDir, entry.name); if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }; zis.closeEntry(); entry = zis.nextEntry } } }; true } catch (e: Exception) { Log.e(TAG, "Zip error", e); false }
}

private fun serializeLandmarks(landmarks: List<TextBlock>): String {
    val array = JSONArray(); landmarks.forEach { block -> val obj = JSONObject(); obj.put("text", block.text); val box = block.boundingBox; val boxObj = JSONObject(); boxObj.put("left", box.left); boxObj.put("top", box.top); boxObj.put("right", box.right); boxObj.put("bottom", box.bottom); obj.put("boundingBox", boxObj); array.put(obj) }; return array.toString()
}
