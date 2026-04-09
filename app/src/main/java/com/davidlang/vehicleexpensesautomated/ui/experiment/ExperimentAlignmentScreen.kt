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
        val tessOcr = OdometerOcrUtils.extractFullImageOcr(v.referenceDashPhotoUrl!!)
        val mlKitOcr = OdometerOcrUtils.extractFromPhoto(v.referenceDashPhotoUrl!!)
        
        ReferenceCache(
            vehicle = v,
            referenceBase64 = bitmapToBase64(drawCropBoxesOnReference(bmp, v), 80),
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
    val maxSizeBytes = 1024 * 1024 // 1MB per part
    var currentSize = 0
    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply {
        writeText(buildHtmlHeader(timestamp, total, vehicles))
    }
    var currentFile = startNewFile()
    val footer = "</table></body></html>"

    // 3. MAIN PROCESSING LOOP
    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            
            // Global Discovery OCR
            val t0 = System.currentTimeMillis()
            val queryOcrTess = OdometerOcrUtils.extractFullImageOcr(file.absolutePath)
            val tTess = System.currentTimeMillis() - t0
            
            val t1 = System.currentTimeMillis()
            val queryOcrMl = OdometerOcrUtils.extractFromPhoto(file.absolutePath)
            val tMl = System.currentTimeMillis() - t1
            
            val globalOcrResults = mapOf(
                "Tesseract" to (queryOcrTess.debugText to tTess),
                "ML Kit" to (queryOcrMl.debugText to tMl)
            )

            val vehicleResults = mutableListOf<JSONObject>()
            var winnerName = "No match"
            var bestConf = 0f
            var pickedOdometer = "FAILED"

            // Process Each Vehicle
            cachedRefs.forEach { ref ->
                val odometerCropF = ref.vehicle.odometerCropLeft?.let { android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
                val otherTextCropF = ref.vehicle.otherTextCropLeft?.let { android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }
                
                // Matching
                val tMatch0 = System.currentTimeMillis()
                val matchResults = ImageAlignmentUtils.matchWithAllMethods(
                    ref.bmp, originalBitmap, ref.ocrResult, queryOcrMl, odometerCropF, otherTextCropF, 
                    skipExpensiveORB = false, globalWordCounts = globalWordCounts, 
                    allOtherRefs = cachedRefs.map { it.ocrResult }, dynamicAnchors = dynamicAnchors, currentVehicleName = ref.vehicle.name
                )
                val tMatch = System.currentTimeMillis() - tMatch0
                val consensus = matchResults["consensus"]!!
                
                // Alignment Previews
                val orbRes = matchResults["feature"]!!
                val hubRes = matchResults["hub"]!!
                val orbBase64 = if (orbRes.success && orbRes.alignedImage != null) bitmapToBase64(orbRes.alignedImage, 70) else ""
                val hubBase64 = if (hubRes.success && hubRes.alignedImage != null) bitmapToBase64(hubRes.alignedImage, 70) else ""
                
                // OCR Strategy Trace (Phase 1: Raw only)
                // We'll simulate the Trace Columns here
                val traceData = mutableMapOf<String, List<OcrStepResult>>() // Strategy -> OCR Steps
                
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

                // Final Logic Check for Winner
                if (consensus.confidence > bestConf) {
                    bestConf = consensus.confidence
                    winnerName = ref.vehicle.name
                    // Pick odo from traces (best candidate)
                    pickedOdometer = pickBestOdometer(traceData.values.flatten()) ?: "FAILED"
                }

                // Store Per-Vehicle Data for JSON
                val vJson = JSONObject().apply {
                    put("name", ref.vehicle.name)
                    put("score", consensus.confidence.toDouble())
                    put("match_time_ms", tMatch)
                    put("veto_word", consensus.vetoReason)
                    // (More fields for Deep Trace in Phase 2)
                }
                vehicleResults.add(vJson)

                // Clean up per-vehicle aligned bitmaps
                orbRes.alignedImage?.recycle()
                hubRes.alignedImage?.recycle()
            }

            // HTML Streaming
            val thumbBase64 = bitmapToBase64(originalBitmap, 50)
            val rowHtml = buildHtmlRowFoundation(file.name, thumbBase64, globalOcrResults, vehicleResults, vehicles, cachedRefs, winnerName, bestConf, pickedOdometer)
            
            if (currentSize + rowHtml.length > maxSizeBytes) {
                currentFile.appendText(footer)
                currentFile = startNewFile()
                currentSize = 0
            }
            currentFile.appendText(rowHtml)
            currentSize += rowHtml.length

            // JSON Persistence
            val photoJson = JSONObject().apply {
                put("file", file.name)
                put("winner", winnerName)
                put("confidence", bestConf.toDouble())
                put("odometer", pickedOdometer)
                put("global_discovery", JSONObject(globalOcrResults.mapValues { it.value.first }))
                put("vehicles", JSONArray(vehicleResults))
            }
            jsonArray.put(photoJson)

            withContext(Dispatchers.Main) {
                onProgress(PhotoResultSummary(file.name, winnerName, bestConf, pickedOdometer), (index + 1).toFloat() / total)
            }
            originalBitmap.recycle()

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
    thumbBase64: String, 
    globalOcr: Map<String, Pair<String, Long>>,
    vehicleResults: List<JSONObject>,
    allVehicles: List<Vehicle>,
    cachedRefs: List<ReferenceCache>,
    winnerName: String,
    bestConf: Float,
    pickedOdo: String
): String = buildString {
    appendLine("<tr>")
    
    // Column 1: Global Discovery
    appendLine("<td><small>$photoName</small><br><img src='data:image/jpeg;base64,$thumbBase64'><br>")
    globalOcr.forEach { (engine, data) ->
        appendLine("<div class='discovery-trace'><b>$engine (${data.second}ms):</b><br>${data.first}</div>")
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
            val veto = vRes.optString("veto_word")
            if (veto.isNotEmpty()) appendLine("<b style='color:red;'>VETO: $veto</b><br>")
            appendLine("<small>Match Time: ${vRes.optLong("match_time_ms")}ms</small>")
        }
        appendLine("</td>")
        
        // Columns 3, 4, 5: OCR Traces (Phase 1: Placeholders or basic data)
        appendLine("<td class='trace-column'><i>(Phase 2)</i></td>")
        appendLine("<td class='trace-column'><i>(Phase 2)</i></td>")
        appendLine("<td class='trace-column'><i>(Phase 2)</i></td>")
    }
    
    // Column 6: Summary
    appendLine("<td><b>Winner:</b> $winnerName<br><b>Conf:</b> ${"%.2f".format(bestConf)}<br><b>Odometer:</b> $pickedOdo</td>")
    appendLine("</tr>")
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
    val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f }
    vehicle.odometerCropLeft?.let { l -> paint.color = android.graphics.Color.RED; canvas.drawRect(l * bmp.width, (vehicle.odometerCropTop ?: 0f) * bmp.height, (vehicle.odometerCropRight ?: 1f) * bmp.width, (vehicle.odometerCropBottom ?: 1f) * bmp.height, paint) }
    vehicle.otherTextCropLeft?.let { l -> paint.color = android.graphics.Color.BLUE; canvas.drawRect(l * bmp.width, (vehicle.otherTextCropTop ?: 0f) * bmp.height, (vehicle.otherTextCropRight ?: 1f) * bmp.width, (vehicle.otherTextCropBottom ?: 1f) * bmp.height, paint) }
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
    val candidates = allSteps.mapNotNull { it.text }.flatMap { text -> Regex("\\d{4,7}").findAll(text).map { it.value } }
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
                    if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { Log.e(TAG, "Zip error", e); false }
}
