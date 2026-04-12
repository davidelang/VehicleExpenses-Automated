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

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                status = "Extracting ZIP..."
                val success = extractZipToPhotos(it, experimentDir, context)
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

data class ReferenceCache(
    val vehicle: Vehicle,
    val referenceBase64: String,
    val curatedLandmarks: List<TextBlock>,
    val ocrResult: OcrResult, // Reconstructed from DB landmarks
    val bmp: Bitmap
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

private suspend fun processSingleVehicleMatch(
    ref: ReferenceCache,
    originalBitmap: Bitmap,
    queryOcrMl: OcrResult,
    globalWordCounts: Map<String, Int>,
    dynamicAnchors: Map<String, String>,
    allOtherRefs: List<OcrResult>,
    veto: VetoResult,
    context: Context
): SingleVehicleResult {
    val tStart = System.currentTimeMillis()
    val odoCropF = ref.vehicle.odometerCropLeft?.let { android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
    val otherCropF = ref.vehicle.otherTextCropLeft?.let { android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }

    // Exhaustive matching: Run every algorithm for EVERY candidate to gather report data.
    val matchResults = ImageAlignmentUtils.matchWithAllMethods(
        ref.bmp, originalBitmap, ref.ocrResult, queryOcrMl, odoCropF, otherCropF,
        skipExpensiveORB = false, globalWordCounts = globalWordCounts,
        allOtherRefs = allOtherRefs, dynamicAnchors = dynamicAnchors,
        currentVehicleName = ref.vehicle.name, veto = veto
    )

    val tiered = matchResults["tiered"]!!
    val orbRes = matchResults["feature"]!!
    val hubRes = matchResults["hub"]!!
    val tMatch = System.currentTimeMillis() - tStart

    val traceData = mutableMapOf<String, List<OcrStepResult>>()
    if (orbRes.success && orbRes.alignedImage != null) {
        val crop = manualCropOdometer(orbRes.alignedImage, ref.vehicle)
        if (crop != null) { traceData["Aligned"] = OdometerOcrUtils.runMultiStepOcr(crop, context); crop.recycle() }
    }
    if (hubRes.success && hubRes.alignedImage != null) {
        val crop = manualCropOdometer(hubRes.alignedImage, ref.vehicle)
        if (crop != null) { traceData["Hub"] = OdometerOcrUtils.runMultiStepOcr(crop, context); crop.recycle() }
    }

    val orbBase64 = if (orbRes.alignedImage != null) createScaledBase64(orbRes.alignedImage, 400, 70) else ""
    val hubBase64 = if (hubRes.alignedImage != null) createScaledBase64(hubRes.alignedImage, 400, 70) else ""
    orbRes.alignedImage?.recycle(); hubRes.alignedImage?.recycle()

    return SingleVehicleResult(
        vehicleName = ref.vehicle.name, confidence = tiered.confidence, vetoReason = tiered.vetoReason ?: "",
        matchTimeMs = tMatch, orbBase64 = orbBase64, hubBase64 = hubBase64, traceData = traceData,
        methodScores = matchResults.mapValues { it.value.confidence },
        methodTimes = matchResults.mapValues { it.value.timeMs },
        tierReached = tiered.tierReached
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
    
    val cachedRefs = vehicles.map { v ->
        val bmp = BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curatedBlocks = getFullLandmarksFromJson(v.landmarkTextBlocksJson)
        val refOcr = OcrResult(engineName = "Curated DB", textBlocks = curatedBlocks, imageWidth = bmp.width, imageHeight = bmp.height, debugText = curatedBlocks.joinToString(" ") { it.text })
        val annotatedBmp = drawCropBoxesOnReference(bmp, v)
        val refBase64 = createScaledBase64(annotatedBmp, 400, 70)
        annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curatedBlocks, refOcr, bmp)
    }

    val globalWordCounts = mutableMapOf<String, Int>()
    val dynamicAnchors = mutableMapOf<String, String>()
    cachedRefs.forEach { ref ->
        ref.curatedLandmarks.map { it.text }.forEach { w ->
            if (w.length >= 3) globalWordCounts[w] = (globalWordCounts[w] ?: 0) + 1
        }
    }
    cachedRefs.forEach { ref ->
        ref.curatedLandmarks.map { it.text }.forEach { w ->
            if (globalWordCounts[w] == 1) dynamicAnchors[w] = ref.vehicle.name
        }
    }

    val landmarkManifest = JSONObject()
    cachedRefs.forEach { ref -> landmarkManifest.put(ref.vehicle.name, JSONArray(ref.curatedLandmarks.map { it.text }.sorted())) }
    File(reportDir, "alignment_landmarks_${timestamp}.json").writeText(landmarkManifest.toString(2))

    val jsonArray = JSONArray()
    var partCount = 1
    val maxSizeBytes = 2 * 1024 * 1024
    var currentSize = 0
    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { writeText(buildHtmlHeader(timestamp, total, cachedRefs.map { it.vehicle })) }
    var currentFile = startNewFile()
    val footer = "</table></body></html>"

    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            val originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            val grayBitmap = OdometerOcrUtils.applyGrayscale(originalBitmap)
            val bileBitmap = OdometerOcrUtils.applyBilateral(originalBitmap)
            
            val globalOcrResultsMap = mutableMapOf<String, Map<String, Pair<String, Long>>>()
            mapOf("Original" to originalBitmap, "Grayscale" to grayBitmap, "Bilateral" to bileBitmap).forEach { (ver, bmp) ->
                val res = OcrHarness.runAll(bmp, context)
                globalOcrResultsMap[ver] = res.mapValues { it.value.debugText to it.value.executionTimeMs }
            }

            val queryOcrMl = OcrHarness.runAll(originalBitmap, context)["ML Kit"]!!
            val queryLandmarks = OdometerOcrUtils.discoverLandmarksFromBitmap(originalBitmap)
            
            val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, cachedRefs.map { it.vehicle })
            val nonVetoedRefs = cachedRefs.filter { !vetoResults[it.vehicle.id]!!.isVetoed }
            
            val vehicleResults = mutableListOf<JSONObject>()
            var winnerName = "No match"
            var bestConf = 0f
            var pickedOdometer = "FAILED"
            val strategyTraces = mutableMapOf<String, List<OcrStepResult>>()

            // IN EXPERIMENT: We run everything for everyone, but the winner logic still uses Tiers.
            cachedRefs.forEach { ref ->
                val veto = vetoResults[ref.vehicle.id] ?: VetoResult(false)
                
                // NO GATING: We call processSingleVehicleMatch for every single vehicle.
                val vRes = processSingleVehicleMatch(ref, originalBitmap, queryOcrMl, globalWordCounts, dynamicAnchors, cachedRefs.map { it.ocrResult }, veto, context)
                strategyTraces["${ref.vehicle.name}_Aligned"] = vRes.traceData["Aligned"] ?: emptyList()
                strategyTraces["${ref.vehicle.name}_Hub"] = vRes.traceData["Hub"] ?: emptyList()
                
                // Winner logic follows project mandates: If only one survives Veto, it wins Tier 1.
                val isStrictWinner = nonVetoedRefs.size == 1 && ref.vehicle.name == nonVetoedRefs[0].vehicle.name
                val finalConf = if (isStrictWinner) 1.0f else vRes.confidence
                val finalTier = if (isStrictWinner) 1 else vRes.tierReached

                if (finalTier in 1..3 && finalConf >= 0.25f && finalConf > bestConf) {
                    bestConf = finalConf; winnerName = vRes.vehicleName
                    pickedOdometer = OdometerOcrUtils.pickBestOdometer((vRes.traceData["Aligned"] ?: emptyList()) + (vRes.traceData["Hub"] ?: emptyList())) ?: "FAILED"
                }
                vehicleResults.add(JSONObject().apply {
                    put("name", vRes.vehicleName); put("score", finalConf.toDouble()); put("match_time_ms", vRes.matchTimeMs); put("veto_word", vRes.vetoReason)
                    put("orb_base64", vRes.orbBase64); put("hub_base64", vRes.hubBase64); put("tier_reached", finalTier)
                    put("method_scores", JSONObject().apply { vRes.methodScores.forEach { (k, v) -> put(k, v.toDouble()) } })
                    put("method_times", JSONObject().apply { vRes.methodTimes.forEach { (k, v) -> put(k, v) } })
                })
            }

            val qLandmarkDisplays = queryLandmarks.map { "${it.text} (${"%.1f".format(it.angle)}°)" }.sorted()
            val rowHtml = buildHtmlRowFoundation(file.name, mapOf("Original" to createScaledBase64(originalBitmap, 150, 50), "Grayscale" to createScaledBase64(grayBitmap, 150, 50), "Bilateral" to createScaledBase64(bileBitmap, 150, 50)), globalOcrResultsMap, qLandmarkDisplays, vehicleResults, cachedRefs.map { it.vehicle }, cachedRefs, winnerName, bestConf, pickedOdometer, strategyTraces)
            
            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = headerLength(currentFile) }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length

            jsonArray.put(JSONObject().apply {
                put("file", file.name); put("winner", winnerName); put("confidence", bestConf.toDouble()); put("odometer", pickedOdometer)
                put("query_landmarks", JSONArray(queryLandmarks.map { l -> JSONObject().apply { put("text", l.text); put("angle", l.angle.toDouble()) } }))
                put("vehicles", JSONArray(vehicleResults))
                put("strict_veto_winner", nonVetoedRefs.size == 1)
                put("conflict_candidates", JSONArray(nonVetoedRefs.map { it.vehicle.name }))
            })

            withContext(Dispatchers.Main) { onProgress(PhotoResultSummary(file.name, winnerName, bestConf, pickedOdometer), (index + 1).toFloat() / total) }
            grayBitmap.recycle(); bileBitmap.recycle(); originalBitmap.recycle()
            strategyTraces.values.flatten().forEach { it.bitmap.recycle() }
        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }
    currentFile.appendText(footer)
    File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2))
    cachedRefs.forEach { it.bmp.recycle() }
}

private fun headerLength(f: File): Int = f.readText().length

private fun buildHtmlHeader(time: String, total: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<html><head><title>Alignment Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 10px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 150px; height: auto; border: 1px solid #eee; } .score-box { text-align: left; font-size: 9px; background: #f9f9f9; padding: 4px; border-radius: 4px; overflow-wrap: break-word; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px; }</style></head><body>")
    appendLine("<h1>Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p>")
    appendLine("<h3>Reference Landmark Manifest (Curated DB)</h3><ul>")
    allVehicles.forEach { v ->
        val landmarks = ImageAlignmentUtils.getLandmarksFromJson(v.landmarkTextBlocksJson).sorted()
        appendLine("<li><b>${v.name}:</b> ${landmarks.joinToString(", ")}</li>")
    }
    appendLine("</ul><table><tr><th style='width:80px;'># & Photo</th><th style='width:160px;'>Original / Discovery</th>")
    allVehicles.forEach { v -> appendLine("<th style='width:160px;'>${v.name} Match</th><th style='width:160px;'>${v.name} Aligned OCR</th><th style='width:160px;'>${v.name} Hub OCR</th>") }
    appendLine("<th style='width:120px;'>Final Result</th></tr>")
}

private fun buildHtmlRowFoundation(photoName: String, globalBase64s: Map<String, String>, globalOcr: Map<String, Map<String, Pair<String, Long>>>, queryLandmarks: List<String>, vehicleResults: List<JSONObject>, allVehicles: List<Vehicle>, cachedRefs: List<ReferenceCache>, winnerName: String, bestConf: Float, pickedOdo: String, traces: Map<String, List<OcrStepResult>>): String = buildString {
    appendLine("<tr><td><small>$photoName</small></td><td>")
    globalBase64s.forEach { (ver, b64) ->
        appendLine("<b>$ver:</b><br><img src='data:image/jpeg;base64,$b64'><br>")
        globalOcr[ver]?.forEach { (eng, res) -> appendLine("<small><b>$eng:</b> ${res.first} (${res.second}ms)</small><br>") }
    }
    appendLine("<hr><b>Query Landmarks Found:</b><br><small>${queryLandmarks.joinToString(", ")}</small>")
    appendLine("</td>")
    allVehicles.forEachIndexed { i, v ->
        val vJson = vehicleResults[i]
        val isWinner = winnerName == v.name
        val ref = cachedRefs[i]
        appendLine("<td class='${if (isWinner) "winner" else ""}'>")
        appendLine("<div class='score-box'><b>Score:</b> ${"%.3f".format(vJson.getDouble("score"))}<br><b>Tier:</b> ${vJson.getInt("tier_reached")}<br><b>Veto:</b> ${vJson.getString("veto_word")}<br></div>")
        appendLine("<b>Ref:</b><br><img src='data:image/jpeg;base64,${ref.referenceBase64}'><br>")
        appendLine("<small><b>Curated DB:</b> ${ref.curatedLandmarks.joinToString(", ") { it.text }}</small>")
        appendLine("<hr><b>ORB:</b><br><img src='data:image/jpeg;base64,${vJson.getString("orb_base64")}'><br>")
        appendLine("<b>Hub:</b><br><img src='data:image/jpeg;base64,${vJson.getString("hub_base64")}'>")
        appendLine("</td>")
        appendLine("<td>${buildOcrStepHtml(traces["${v.name}_Aligned"] ?: emptyList())}</td>")
        appendLine("<td>${buildOcrStepHtml(traces["${v.name}_Hub"] ?: emptyList())}</td>")
    }
    appendLine("<td><b>Match:</b> $winnerName<br><b>Conf:</b> ${"%.2f".format(bestConf)}<br><b>Odo:</b> $pickedOdo</td></tr>")
}

private fun buildOcrStepHtml(steps: List<OcrStepResult>): String = buildString {
    if (steps.isEmpty()) { appendLine("<i>(No crop)</i>"); return@buildString }
    steps.forEach { step -> appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br>${step.text}</div>") }
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val q = quality.coerceIn(0, 100)
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, q, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int): String {
    val scale = targetWidth.toFloat() / bitmap.width
    val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true)
    val b64 = bitmapToBase64(scaled, quality)
    scaled.recycle()
    return b64
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(annotated)
    val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f }
    vehicle.odometerCropLeft?.let { l -> paint.color = android.graphics.Color.RED; canvas.drawRect(l * bmp.width, (vehicle.odometerCropTop ?: 0f) * bmp.height, (vehicle.odometerCropRight ?: 1f) * bmp.width, (vehicle.odometerCropBottom ?: 1f) * bmp.height, paint) }
    vehicle.otherTextCropLeft?.let { l -> paint.color = android.graphics.Color.BLUE; canvas.drawRect(l * bmp.width, (vehicle.otherTextCropTop ?: 0f) * bmp.height, (vehicle.otherTextCropRight ?: 1f) * bmp.width, (vehicle.otherTextCropBottom ?: 1f) * bmp.height, paint) }
    return annotated
}

private fun manualCropOdometer(bmp: Bitmap, vehicle: Vehicle): Bitmap? {
    val cropRect = vehicle.odometerCropLeft?.let { l -> android.graphics.RectF(l, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f) }
    return cropRect?.let { OdometerOcrUtils.manualCropFromRectF(bmp, it) }
}

private fun serializeLandmarks(landmarks: List<TextBlock>): String {
    val array = JSONArray()
    landmarks.forEach { block ->
        val obj = JSONObject()
        obj.put("text", block.text)
        val box = block.boundingBox
        val boxObj = JSONObject()
        boxObj.put("left", box.left); boxObj.put("top", box.top); boxObj.put("right", box.right); boxObj.put("bottom", box.bottom)
        obj.put("boundingBox", boxObj)
        array.put(obj)
    }
    return array.toString()
}

private fun getFullLandmarksFromJson(json: String?): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<TextBlock>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val boxObj = obj.optJSONObject("boundingBox")
            val box = if (boxObj != null) {
                android.graphics.Rect(boxObj.getInt("left"), boxObj.getInt("top"), boxObj.getInt("right"), boxObj.getInt("bottom"))
            } else {
                android.graphics.Rect(0, 0, 0, 0)
            }
            list.add(TextBlock(obj.getString("text"), box, (obj.optDouble("angle", 0.0)).toFloat()))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse curated landmarks", e)
    }
    return list
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
                    if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { Log.e(TAG, "Zip error", e); false }
}
