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

/**
 * Deep Trace Report (Phase 1): Foundation
 * Implements the new multi-column layout with streaming HTML and bitmap recycling.
 */

data class PhotoResultSummary(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val odometer: String?
)

data class SingleVehicleResult(
    val vehicleName: String,
    val confidence: Float,
    val vetoReason: String,
    val matchTimeMs: Long,
    val orbBase64: String,
    val hubBase64: String,
    val traceData: Map<String, List<OcrStepResult>>,
    val methodScores: Map<String, Float>,
    val methodTimes: Map<String, Long>,
    val tierReached: Int
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
    var photoCount by remember { mutableStateOf(0) }
    val resultsList = remember { mutableStateListOf<PhotoResultSummary>() }

    val experimentDir = File(context.filesDir, "experiment_photos")
    val reportDir = File(context.filesDir, "experiment_reports")
    val debugCropDir = File(context.filesDir, "experiment_debug_crops")

    if (!reportDir.exists()) reportDir.mkdirs()
    if (!debugCropDir.exists()) debugCropDir.mkdirs()

    fun updatePhotoCount() {
        photoCount = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png") }?.size ?: 0
        if (!isRunning) {
            status = if (photoCount > 0) "Ready: $photoCount photos found." else "Folder is empty. Please extract a ZIP."
        }
    }

    LaunchedEffect(Unit) {
        updatePhotoCount()
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                status = "Extracting ZIP..."
                val success = extractZipToPhotos(it, experimentDir, context)
                updatePhotoCount()
                status = if (success) "ZIP extracted!" else "Failed to extract ZIP."
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alignment Experiment") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (detailLog.isNotEmpty()) {
                Text(detailLog, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            
            if (isRunning) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(currentPhotoName, style = MaterialTheme.typography.labelSmall)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_PHOTOS_LINK))
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Amazon Photos Album")
            }

            Button(onClick = { zipLauncher.launch("application/zip") }, modifier = Modifier.fillMaxWidth()) {
                Text("Extract Downloaded ZIP")
            }

            Button(
                onClick = {
                    if (vehicles.isEmpty()) {
                        status = "Error: No vehicles in DB."
                        return@Button
                    }
                    scope.launch {
                        isRunning = true
                        resultsList.clear()
                        runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }) { res, p ->
                            resultsList.add(res)
                            progress = p
                            currentPhotoName = res.photoName
                        }
                        isRunning = false
                        updatePhotoCount()
                        detailLog = ""
                        status = "Complete! Reports saved."
                    }
                },
                enabled = !isRunning && experimentDir.exists(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Test")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(resultsList) { index, res ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(res.photoName, style = MaterialTheme.typography.labelSmall)
                                Text("Match: ${res.matchedVehicle}", color = MaterialTheme.colorScheme.primary)
                                Text("Odo: ${res.odometer ?: "FAILED"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pre-processed and cached reference data to avoid redundant work */
data class ReferenceCache(
    val vehicle: Vehicle,
    val referenceBase64: String,
    val fullOcrText: Map<String, String>, // Engine -> Text
    val ocrResult: OcrResult, // To reuse text blocks for matching
    val bmp: Bitmap
)

private suspend fun processSingleVehicleMatch(
    ref: ReferenceCache,
    originalBitmap: Bitmap,
    queryOcrMl: OcrResult,
    globalWordCounts: Map<String, Int>,
    dynamicAnchors: Map<String, String>,
    allOtherRefs: List<OcrResult>,
    context: Context
): SingleVehicleResult {
    val odometerCropF = ref.vehicle.odometerCropLeft?.let { android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
    val otherTextCropF = ref.vehicle.otherTextCropLeft?.let { android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }
    
    // Matching
    val tMatch0 = System.currentTimeMillis()
    val matchResults = ImageAlignmentUtils.matchWithAllMethods(
        ref.bmp, originalBitmap, ref.ocrResult, queryOcrMl, odometerCropF, otherTextCropF, 
        skipExpensiveORB = false, globalWordCounts = globalWordCounts, 
        allOtherRefs = allOtherRefs, dynamicAnchors = dynamicAnchors, currentVehicleName = ref.vehicle.name
    )
    val tMatch = System.currentTimeMillis() - tMatch0
    val consensus = matchResults["consensus"]!!
    
    // Alignment Previews
    val orbRes = matchResults["feature"]!!
    val hubRes = matchResults["hub"]!!
    val orbBase64 = if (orbRes.success && orbRes.alignedImage != null) createScaledBase64(orbRes.alignedImage, 400, 70) else ""
    val hubBase64 = if (hubRes.success && hubRes.alignedImage != null) createScaledBase64(hubRes.alignedImage, 400, 70) else ""
    
    // OCR Strategy Trace (Phase 1: Raw only)
    val traceData = mutableMapOf<String, List<OcrStepResult>>()
    
    // Aligned Odo Trace
    if (orbRes.success && orbRes.alignedImage != null) {
        val crop = manualCropOdometer(orbRes.alignedImage, ref.vehicle)
        if (crop != null) {
            traceData["Aligned"] = OdometerOcrUtils.runMultiStepOcr(crop, context)
            crop.recycle()
        }
    }
    
    // Hub Odo Trace
    if (hubRes.success && hubRes.alignedImage != null) {
        val crop = manualCropOdometer(hubRes.alignedImage, ref.vehicle)
        if (crop != null) {
            traceData["Hub"] = OdometerOcrUtils.runMultiStepOcr(crop, context)
            crop.recycle()
        }
    }

    orbRes.alignedImage?.recycle()
    hubRes.alignedImage?.recycle()

    return SingleVehicleResult(
        vehicleName = ref.vehicle.name,
        confidence = consensus.confidence,
        vetoReason = consensus.vetoReason ?: "",
        matchTimeMs = tMatch,
        orbBase64 = orbBase64,
        hubBase64 = hubBase64,
        traceData = traceData,
        methodScores = matchResults.mapValues { it.value.confidence },
        methodTimes = matchResults.mapValues { it.value.timeMs },
        tierReached = matchResults["tiered"]?.tierReached ?: 0
    )
}

private suspend fun runExperiment(
    experimentDir: File,
    reportDir: File,
    debugCropDir: File,
    vehicles: List<Vehicle>,
    context: Context,
    onLog: (String) -> Unit,
    onProgress: (PhotoResultSummary, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    
    val ocrEngines = listOf("Tesseract", "ML Kit")
    
    // 1. CACHE REFERENCE DATA
    withContext(Dispatchers.Main) { onLog("Processing reference images...") }
    val cachedRefs = vehicles.map { v ->
        val bmp = BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val rawTessOcr = OdometerOcrUtils.extractFullImageOcr(v.referenceDashPhotoUrl!!)
        val rawMlKitOcr = OdometerOcrUtils.extractFromPhoto(v.referenceDashPhotoUrl!!)
        
        val odoCropF = v.odometerCropLeft?.let { android.graphics.RectF(it, v.odometerCropTop ?: 0f, v.odometerCropRight ?: 1f, v.odometerCropBottom ?: 1f) }
        val otherCropF = v.otherTextCropLeft?.let { android.graphics.RectF(it, v.otherTextCropTop ?: 0f, v.otherTextCropRight ?: 1f, v.otherTextCropBottom ?: 1f) }
        
        val tessOcr = rawTessOcr.filterByCrops(odoCropF, otherCropF)
        val mlKitOcr = rawMlKitOcr.filterByCrops(odoCropF, otherCropF)

        val annotatedBmp = drawCropBoxesOnReference(bmp, v)
        val refBase64 = createScaledBase64(annotatedBmp, 400, 70)
        annotatedBmp.recycle()

        ReferenceCache(
            vehicle = v,
            referenceBase64 = refBase64,
            fullOcrText = mapOf("Tesseract" to (tessOcr.debugText), "ML Kit" to (mlKitOcr.debugText)),
            ocrResult = mlKitOcr, // Use ML Kit for anchor matching
            bmp = bmp
        )
    }

    // Dynamic Anchor Pass
    val globalWordCounts = mutableMapOf<String, Int>()
    val dynamicAnchors = mutableMapOf<String, String>()
    cachedRefs.forEach { ref ->
        ref.ocrResult.textBlocks.map { it.text.lowercase().trim() }.distinct().forEach { w ->
            if (w.length >= 3) globalWordCounts[w] = (globalWordCounts[w] ?: 0) + 1
        }
    }
    cachedRefs.forEach { ref ->
        ref.ocrResult.textBlocks.map { it.text.lowercase().trim() }.distinct().forEach { w ->
            if (globalWordCounts[w] == 1) dynamicAnchors[w] = ref.vehicle.name
        }
    }

    // 2. INITIALIZE REPORTS
    val jsonArray = JSONArray()
    var partCount = 1
    val maxSizeBytes = 2 * 1024 * 1024 // 2MB per part
    var currentSize = 0
    fun startNewFile(): File {
        val header = buildHtmlHeader(timestamp, total, vehicles)
        currentSize = header.length
        return File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply {
            writeText(header)
        }
    }
    var currentFile = startNewFile()
    val footer = "</table></body></html>"

    // 3. MAIN PROCESSING LOOP
    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            val originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            
            // Phase 2b: dashboard images
            val grayBitmap = OdometerOcrUtils.applyGrayscale(originalBitmap)
            val bileBitmap = OdometerOcrUtils.applyBilateral(originalBitmap)
            
            // Global Discovery OCR
            val globalOcrResultsMap = mutableMapOf<String, Map<String, Pair<String, Long>>>()
            val globalVersions = mapOf("Original" to originalBitmap, "Grayscale" to grayBitmap, "Bilateral" to bileBitmap)
            
            globalVersions.forEach { (verName, bmp) ->
                val engineResults = OcrHarness.runAll(bmp)
                globalOcrResultsMap[verName] = engineResults.mapValues { it.value.debugText to it.value.executionTimeMs }
            }
            
            val queryOcrMl = OcrHarness.runAll(originalBitmap)["ML Kit"] ?: OcrResult(debugText = "")
            val globalOcrResults = globalOcrResultsMap["Original"]!!

            val vehicleResults = mutableListOf<JSONObject>()
            var winnerName = "No match"
            var bestConf = 0f
            var pickedOdometer = "FAILED"
            val strategyTraces = mutableMapOf<String, List<OcrStepResult>>()

            // Process Each Vehicle
            cachedRefs.forEach { ref ->
                val vRes = processSingleVehicleMatch(
                    ref, originalBitmap, queryOcrMl!!, globalWordCounts, dynamicAnchors, 
                    cachedRefs.map { it.ocrResult }, context
                )
                
                strategyTraces["${ref.vehicle.name}_Aligned"] = vRes.traceData["Aligned"] ?: emptyList()
                strategyTraces["${ref.vehicle.name}_Hub"] = vRes.traceData["Hub"] ?: emptyList()
                
                if (vRes.confidence > bestConf) {
                    bestConf = vRes.confidence
                    winnerName = vRes.vehicleName
                    // Pick odo from traces (best candidate)
                    val candidates = (vRes.traceData["Aligned"] ?: emptyList()) + (vRes.traceData["Hub"] ?: emptyList())
                    pickedOdometer = pickBestOdometer(candidates) ?: "FAILED"
                }

                // Store Per-Vehicle Data for JSON
                val vJson = JSONObject().apply {
                    put("name", vRes.vehicleName)
                    put("score", vRes.confidence.toDouble())
                    put("match_time_ms", vRes.matchTimeMs)
                    put("veto_word", vRes.vetoReason)
                    put("orb_base64", vRes.orbBase64)
                    put("hub_base64", vRes.hubBase64)
                    val scoresObj = JSONObject()
                    vRes.methodScores.forEach { (k, v) -> scoresObj.put(k, v.toDouble()) }
                    put("method_scores", scoresObj)
                    val timesObj = JSONObject()
                    vRes.methodTimes.forEach { (k, v) -> timesObj.put(k, v) }
                    put("method_times", timesObj)
                    put("tier_reached", vRes.tierReached)
                }
                vehicleResults.add(vJson)
            }

            // HTML Streaming
            val globalVersionBase64s = globalVersions.mapValues { createScaledBase64(it.value, 150, 50) }
            val rowHtml = buildHtmlRowFoundation(file.name, globalVersionBase64s, globalOcrResultsMap, vehicleResults, vehicles, cachedRefs, winnerName, bestConf, pickedOdometer, strategyTraces)
            
            if (currentSize + rowHtml.length > maxSizeBytes) {
                currentFile.appendText(footer)
                currentFile = startNewFile()
            }
            currentFile.appendText(rowHtml)
            currentSize += rowHtml.length

            // JSON Persistence
            val globalJson = JSONObject()
            globalOcrResultsMap.forEach { (version, engineMap) ->
                val verObj = JSONObject()
                engineMap.forEach { (eng, data) -> verObj.put(eng, data.first) }
                globalJson.put(version, verObj)
            }

            val photoJson = JSONObject().apply {
                put("file", file.name)
                put("winner", winnerName)
                put("confidence", bestConf.toDouble())
                put("odometer", pickedOdometer)
                put("global_discovery", globalJson)
                put("vehicles", JSONArray(vehicleResults))
            }
            jsonArray.put(photoJson)

            withContext(Dispatchers.Main) {
                onProgress(PhotoResultSummary(file.name, winnerName, bestConf, pickedOdometer), (index + 1).toFloat() / total)
            }
            
            // Phase 2b recycling
            grayBitmap.recycle()
            bileBitmap.recycle()
            originalBitmap.recycle()
            
            // Phase 2c Deep Trace recycling
            strategyTraces.values.flatten().forEach { it.bitmap.recycle() }

        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }

    currentFile.appendText(footer)
    File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2))
    cachedRefs.forEach { it.bmp.recycle() }
}

private fun buildHtmlHeader(time: String, total: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<html><head><title>Deep Trace Foundation - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: monospace; font-size: 9px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: left; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 140px; height: auto; border: 1px solid #eee; } .discovery-trace { color: #444; background: #f0f0f0; padding: 2px; } .vehicle-header { background: #333; color: #fff; text-align: center; } .match-box { background: #eef; } .trace-column { background: #ffe; }</style></head><body>")
    appendLine("<h1>Deep Trace Report (Phase 1)</h1><p><b>Run:</b> $time | <b>Images:</b> $total</p><table>")
    
    // Header Row 1: Groups
    appendLine("<tr><th style='width:150px;'>1. Global Discovery</th>")
    allVehicles.forEach { v ->
        appendLine("<th colspan='4' class='vehicle-header'>Vehicle: ${v.name}</th>")
    }
    appendLine("<th style='width:100px;'>Summary</th></tr>")
    
    // Header Row 2: Specific Columns
    appendLine("<tr><th>Photo & OCR Discovery</th>")
    allVehicles.forEach { _ ->
        appendLine("<th style='width:160px;'>Match & Align</th>")
        appendLine("<th style='width:140px;'>Aligned Trace</th>")
        appendLine("<th style='width:140px;'>Hub Trace</th>")
        appendLine("<th style='width:140px;'>Anchor Trace</th>")
    }
    appendLine("<th>Final Result</th></tr>")
}

private fun buildHtmlRowFoundation(
    photoName: String, 
    globalVersionBase64s: Map<String, String>, 
    globalOcr: Map<String, Map<String, Pair<String, Long>>>,
    vehicleResults: List<JSONObject>,
    allVehicles: List<Vehicle>,
    cachedRefs: List<ReferenceCache>,
    winnerName: String,
    bestConf: Float,
    pickedOdo: String,
    strategyTraces: Map<String, List<OcrStepResult>>
): String = buildString {
    appendLine("<tr>")
    
    // Column 1: Global Discovery Trace
    appendLine("<td><small>$photoName</small><br>")
    globalOcr.forEach { (version, engineMap) ->
        appendLine("<div class='discovery-trace'><b>Version: $version</b><br>")
        val b64 = globalVersionBase64s[version]
        if (b64 != null) appendLine("<img src='data:image/jpeg;base64,$b64'><br>")
        engineMap.forEach { (engine, data) ->
            appendLine("<i>$engine (${data.second}ms):</i><br>${data.first}<br>")
        }
        appendLine("</div>")
    }
    appendLine("</td>")
    
    // Columns per Vehicle
    allVehicles.forEachIndexed { i, v ->
        val vRes = if (i < vehicleResults.size) vehicleResults[i] else null
        val cache = cachedRefs.find { it.vehicle.id == v.id }
        
        // Column 2: Match & Alignment
        appendLine("<td class='match-box'>")
        if (cache != null) {
            appendLine("<b>Ref:</b><br><img src='data:image/jpeg;base64,${cache.referenceBase64}'><br>")
            appendLine("<small>Ref OCR: ${cache.fullOcrText["ML Kit"]?.take(50)}...</small><hr>")
        }
        if (vRes != null) {
            appendLine("<b>Score:</b> ${"%.3f".format(vRes.optDouble("score"))}<br>")
            val tier = vRes.optInt("tier_reached", -1)
            if (tier != -1) appendLine("<b>Tier:</b> $tier<br>")
            val veto = vRes.optString("veto_word")
            if (veto.isNotEmpty()) appendLine("<b style='color:red;'>VETO: $veto</b><br>")
            
            val scoresObj = vRes.optJSONObject("method_scores")
            val timesObj = vRes.optJSONObject("method_times")
            if (scoresObj != null && timesObj != null) {
                appendLine("<small>")
                scoresObj.keys().forEach { k ->
                    val s = "%.2f".format(scoresObj.getDouble(k))
                    val t = timesObj.optLong(k, 0)
                    appendLine("$k: $s (${t}ms)<br>")
                }
                appendLine("</small>")
            }
            appendLine("<small>Total Match Time: ${vRes.optLong("match_time_ms")}ms</small><br>")
            
            val orb64 = vRes.optString("orb_base64")
            if (orb64.isNotEmpty()) appendLine("<b>ORB:</b><br><img src='data:image/jpeg;base64,$orb64'><br>")
            
            val hub64 = vRes.optString("hub_base64")
            if (hub64.isNotEmpty()) appendLine("<b>HUB:</b><br><img src='data:image/jpeg;base64,$hub64'><br>")
        }
        appendLine("</td>")
        
        // Columns 3, 4, 5: OCR Traces
        listOf("Aligned", "Hub", "Anchor").forEach { strat ->
            appendLine("<td class='trace-column'>")
            val steps = strategyTraces["${v.name}_$strat"]
            if (steps != null && steps.isNotEmpty()) {
                steps.forEach { step ->
                    appendLine("<img src='data:image/jpeg;base64,${createScaledBase64(step.bitmap, 120, 60)}'><br>")
                    appendLine("<b>${step.stageName}:</b><br>${step.text ?: "-"}<hr>")
                }
            } else {
                appendLine("<i>(No crop)</i>")
            }
            appendLine("</td>")
        }
    }
    
    // Column 6: Summary
    appendLine("<td><b>Winner:</b> $winnerName<br><b>Conf:</b> ${"%.2f".format(bestConf)}<br><b>Odometer:</b> $pickedOdo</td>")
    appendLine("</tr>")
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int): String {
    val q = quality.coerceIn(0, 100)
    val width = targetWidth.coerceAtMost(bitmap.width)
    val height = (width.toFloat() / bitmap.width * bitmap.height).toInt()
    if (width <= 0 || height <= 0) return ""
    
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val outputStream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, q, outputStream)
    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    scaled.recycle()
    return base64
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
    val q = quality.coerceIn(0, 100)
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, q, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(annotated)
    val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 12f }
    
    // Odometer Box (Red)
    vehicle.odometerCropLeft?.let { l ->
        paint.color = android.graphics.Color.RED
        val top = (vehicle.odometerCropTop ?: 0f) * bmp.height
        val right = (vehicle.odometerCropRight ?: 1f) * bmp.width
        val bottom = (vehicle.odometerCropBottom ?: 1f) * bmp.height
        canvas.drawRect(l * bmp.width, top, right, bottom, paint)
    }
    
    // Other Text Box (Blue)
    vehicle.otherTextCropLeft?.let { l ->
        paint.color = android.graphics.Color.BLUE
        val top = (vehicle.otherTextCropTop ?: 0f) * bmp.height
        val right = (vehicle.otherTextCropRight ?: 1f) * bmp.width
        val bottom = (vehicle.otherTextCropBottom ?: 1f) * bmp.height
        canvas.drawRect(l * bmp.width, top, right, bottom, paint)
    }
    return annotated
}

private fun manualCropOdometer(bmp: Bitmap, vehicle: Vehicle): Bitmap? {
    val l = vehicle.odometerCropLeft ?: return null
    val t = vehicle.odometerCropTop ?: 0f
    val r = vehicle.odometerCropRight ?: 1f
    val b = vehicle.odometerCropBottom ?: 1f
    val left = (l * bmp.width).toInt().coerceAtLeast(0)
    val top = (t * bmp.height).toInt().coerceAtLeast(0)
    val width = ((r - l) * bmp.width).toInt().coerceAtMost(bmp.width - left)
    val height = ((b - t) * bmp.height).toInt().coerceAtMost(bmp.height - top)
    if (width <= 0 || height <= 0) return null
    return Bitmap.createBitmap(bmp, left, top, width, height)
}

private fun manualCropFromRectF(bmp: Bitmap, rect: android.graphics.RectF): Bitmap? {
    val left = (rect.left * bmp.width).toInt().coerceAtLeast(0)
    val top = (rect.top * bmp.height).toInt().coerceAtLeast(0)
    val width = ((rect.right - rect.left) * bmp.width).toInt().coerceAtMost(bmp.width - left)
    val height = ((rect.bottom - rect.top) * bmp.height).toInt().coerceAtMost(bmp.height - top)
    if (width <= 0 || height <= 0) return null
    return Bitmap.createBitmap(bmp, left, top, width, height)
}

private fun pickBestOdometer(allSteps: List<OcrStepResult>): String? {
    val candidates = allSteps.mapNotNull { it.text }.flatMap { text -> Regex("\\b\\d{4,7}\\b").findAll(text).map { it.value } }
    return candidates.groupBy { it }.maxByOrNull { it.value.size }?.key ?: candidates.maxByOrNull { it.length }
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { Log.e(TAG, "Zip error", e); false }
}
