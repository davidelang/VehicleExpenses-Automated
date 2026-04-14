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
    val confidence: Float,
    val vetoReason: String,
    val matchTimeMs: Long,
    val alignmentTraces: Map<String, AlignmentTraceResult>,
    val tierReached: Int,
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>
)

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size; val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    
    val groundTruth = loadGroundTruth(context)
    
    // One-time Cleanup: Delete all orphaned vehicle_ref files
    val allRefPaths = vehicles.mapNotNull { it.referenceDashPhotoUrl }.toSet()
    context.filesDir.listFiles { f -> f.name.startsWith("vehicle_ref_") }?.forEach { f ->
        if (!allRefPaths.contains(f.absolutePath)) {
            Log.i(TAG, "Deleting orphaned ref: ${f.name}")
            f.delete()
        }
    }

    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curatedBlocks = getFullLandmarksFromJson(v.landmarkTextBlocksJson)
        val refOcr = OcrResult(engineName = "Curated DB", textBlocks = curatedBlocks, imageWidth = bmp.width, imageHeight = bmp.height, debugText = curatedBlocks.joinToString(" ") { it.text })
        val annotatedBmp = drawCropBoxesOnReference(bmp, v); val refBase64 = createScaledBase64(annotatedBmp, 400, 70); annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curatedBlocks, refOcr, bmp)
    }
    
    val jsonArray = JSONArray(); var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    val activeAlignments = listOf("ORB", "Anchor-Tri")
    
    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { 
        writeText(buildHtmlHeader(timestamp, total, cachedRefs.map { it.vehicle }, activeAlignments)) 
    }
    var currentFile = startNewFile(); val footer = "</table></body></html>"
    
    photos.forEachIndexed { index, file ->
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${file.name}...") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: return@forEachIndexed
            var originalBitmap = OdometerOcrUtils.rotateImageIfRequired(rawBitmap, file.absolutePath)
            
            // 1. IDENTITY STAGE (Deskew then Discovery)
            val tIdentityStart = System.currentTimeMillis()
            
            // Pass 1 & 2: Calculate skew and Deskew immediately to get a 0-degree baseline
            val tilt = OdometerOcrUtils.calculateAverageTextAngle(originalBitmap)
            if (Math.abs(tilt) > 0.2f) { 
                val leveled = OdometerOcrUtils.rotateBitmap(originalBitmap, -tilt)
                if (leveled != originalBitmap) { 
                    originalBitmap.recycle() 
                    originalBitmap = leveled 
                }
            }
            
            // Pass 3: Identification & Landmark Discovery on the DESKEWED image
            val queryOcrDiscovery = OcrHarness.runDiscovery(originalBitmap, context)["ML Kit"]!!
            val queryLandmarks = OdometerOcrUtils.discoverLandmarksFromBitmap(originalBitmap)
            
            val tIdentityTotal = System.currentTimeMillis() - tIdentityStart
            
            val deskewedBase64 = createScaledBase64(originalBitmap, 150, 50)
            val discoveryText = queryOcrDiscovery.debugText
            
            val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, cachedRefs.map { it.vehicle })
            val hardcodedWinner = groundTruth[file.name.lowercase()]
            
            val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
            var finalWinnerName = "No match"
            var bestOdometer = "FAILED"
            
            cachedRefs.forEach { ref ->
                val veto = vetoResults[ref.vehicle.id] ?: VetoResult(false)
                val isWinner = (hardcodedWinner != null && hardcodedWinner == ref.vehicle.name)
                
                val tMatchStart = System.currentTimeMillis()
                val matchResults = ImageAlignmentUtils.matchWithAllMethods(
                    ref.bmp, originalBitmap, ref.ocrResult, queryOcrDiscovery,
                    ref.vehicle.odometerCropLeft?.let { l -> android.graphics.RectF(l, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) },
                    ref.vehicle.otherTextCropLeft?.let { l -> android.graphics.RectF(l, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) },
                    skipExpensiveORB = !isWinner,
                    veto = veto,
                    vehicle = ref.vehicle
                )
                val tiered = matchResults["tiered"]!!
                val tMatchTotal = System.currentTimeMillis() - tMatchStart
                
                val alignmentTraces = mutableMapOf<String, AlignmentTraceResult>()
                
                // 2. ALIGNMENT & EXTRACTION STAGES (Only for winners/forced)
                if (isWinner) {
                    finalWinnerName = ref.vehicle.name
                    // Run ORB
                    val orbRes = matchResults["feature"]!!
                    if (orbRes.success && orbRes.alignedImage != null) {
                        val crop = manualCropOdometer(orbRes.alignedImage, ref.vehicle)
                        if (crop != null) {
                            val steps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                            alignmentTraces["ORB"] = AlignmentTraceResult("ORB", true, orbRes.timeMs, createScaledBase64(orbRes.alignedImage, 400, 70), steps, orbRes.metadata)
                            crop.recycle()
                        }
                        orbRes.alignedImage.recycle()
                    }
                    
                    // Run Anchor-Tri
                    val anchorRes = ImageAlignmentUtils.anchorAlign(ref.bmp, originalBitmap, ref.curatedLandmarks, queryLandmarks, ref.vehicle)
                    if (anchorRes.success && anchorRes.alignedImage != null) {
                        val crop = manualCropOdometer(anchorRes.alignedImage, ref.vehicle)
                        if (crop != null) {
                            val steps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                            alignmentTraces["Anchor-Tri"] = AlignmentTraceResult("Anchor-Tri", true, anchorRes.timeMs, createScaledBase64(anchorRes.alignedImage, 400, 70), steps, anchorRes.metadata)
                            crop.recycle()
                        }
                        anchorRes.alignedImage.recycle()
                    }
                    
                    bestOdometer = OdometerOcrUtils.pickBestOdometer(alignmentTraces.values.flatMap { it.ocrTraces }) ?: "FAILED"
                }
                
                vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(
                    ref.vehicle.name, tiered.confidence, tiered.vetoReason ?: "", tMatchTotal, alignmentTraces, tiered.tierReached, veto.queryWords, veto.myManifest, veto.vetoPool
                )
            }

            val rowHtml = buildHtmlRowDynamic(index + 1, file.name, deskewedBase64, discoveryText, vehicleResultsMap, cachedRefs, finalWinnerName, bestOdometer, activeAlignments)
            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length
            
            // DYNAMIC FIX: Populate jsonArray
            jsonArray.put(JSONObject().apply {
                put("index", index + 1)
                put("file", file.name)
                put("winner", finalWinnerName)
                put("odometer", bestOdometer)
                val vResults = JSONArray()
                vehicleResultsMap.values.forEach { vr ->
                    vResults.put(JSONObject().apply {
                        put("vehicle", vr.vehicleName)
                        put("confidence", vr.confidence.toDouble())
                        put("tier", vr.tierReached)
                        val traces = JSONObject()
                        vr.alignmentTraces.forEach { (name, trace) ->
                            traces.put(name, JSONObject().apply {
                                put("success", trace.success)
                                put("time_ms", trace.timeMs)
                                trace.metadata.forEach { (mk, mv) -> put(mk.lowercase(), mv) }
                            })
                        }
                        put("traces", traces)
                    })
                }
                put("vehicles", vResults)
            })

            withContext(Dispatchers.Main) { onProgress(PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer), (index + 1).toFloat() / total) }
            originalBitmap.recycle()
        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }
    currentFile.appendText(footer)
    File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2))
    cachedRefs.forEach { it.bmp.recycle() }
}

private fun buildHtmlHeader(time: String, total: Int, vehicles: List<Vehicle>, alignNames: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 9px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 4px; text-align: left; } .word-list { font-size: 7px; color: #666; height: 40px; overflow-y: scroll; }</style></head><body>")
    appendLine("<h1>Deep Trace Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th style='width:100px;'># & Original</th>")
    alignNames.forEach { appendLine("<th style='width:250px;'>$it Alignment</th>") }
    appendLine("<th style='width:120px;'>Final Result</th></tr>")
}

private fun buildHtmlRowDynamic(rowIndex: Int, fileName: String, deskewedBase64: String, discovery: String, vehicleResults: Map<Int, SingleVehicleResult>, cachedRefs: List<ReferenceCache>, winnerName: String, bestOdo: String, alignNames: List<String>): String = buildString {
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><b>Deskewed:</b><br><img src='data:image/jpeg;base64,$deskewedBase64'><br><small>$discovery</small></td>")
    
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }
    val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    alignNames.forEach { alignName ->
        appendLine("<td>")
        if (vRes != null) {
            val trace = vRes.alignmentTraces[alignName]
            if (trace != null && trace.success) {
                appendLine("<b>Time:</b> ${trace.timeMs}ms<br>")
                if (trace.metadata.isNotEmpty()) appendLine("<small>${trace.metadata}</small><br>")
                appendLine("<img src='data:image/jpeg;base64,${trace.alignedImageBase64}'><br><hr>")
                trace.ocrTraces.forEach { step ->
                    appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br><img src='data:image/jpeg;base64,${createScaledBase64(step.bitmap, 300, 60)}'><br>${step.text}</div>")
                }
            } else appendLine("<i>Alignment failed or skipped</i>")
        } else appendLine("<i>No match found</i>")
        appendLine("</td>")
    }
    
    appendLine("<td><b>Winner:</b> $winnerName<br><b>Odometer:</b> $bestOdo</td></tr>")
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
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
    return try {
        val json = JSONObject(file.readText()); val map = mutableMapOf<String, String>()
        val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); map[key.lowercase()] = json.getString(key) }
        map
    } catch (e: Exception) { emptyMap() }
}

private fun getFullLandmarksFromJson(json: String?): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<TextBlock>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text")
            val cx = obj.getInt("cx"); val cy = obj.getInt("cy"); val h = obj.getInt("h"); val w = obj.getInt("w")
            list.add(TextBlock(text, android.graphics.Rect(cx - w/2, cy - h/2, cx + w/2, cy + h/2)))
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
