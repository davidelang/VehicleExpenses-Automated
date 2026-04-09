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
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OcrStepResult
import com.davidlang.vehicleexpensesautomated.ui.util.AlignmentResult
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

data class VehicleMatchResult(
    val vehicleName: String,
    val score: Float,
    val message: String,
    val referenceBase64: String,
    val fullAlignedBase64: String,
    val hubAlignedBase64: String = "",
    val fullOcrSteps: List<OcrStepResult> = emptyList(),
    val hubOcrSteps: List<OcrStepResult> = emptyList(),
    val anchorOcrSteps: List<OcrStepResult> = emptyList(),
    val methodScores: Map<String, Float>,
    val wordVeto: Boolean = false,
    val queryTesseractFullOcr: String = "",
    val queryMlKitFullOcr: String = "",
    val allMethodResults: Map<String, AlignmentResult> = emptyMap(),
    val tieredTierReached: Int = 0
)

data class PhotoResult(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val originalThumbBase64: String,
    val allVehicleResults: List<VehicleMatchResult>,
    val methodWinners: Map<String, String>,
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
    val resultsList = remember { mutableStateListOf<PhotoResult>() }

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
                status = if (success) "ZIP extracted! Found $photoCount photos." else "Failed to extract ZIP."
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
                        progress = 0f
                        runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { log -> detailLog = log }) { res, p ->
                            resultsList.add(res)
                            progress = p
                            currentPhotoName = res.photoName
                            status = "Processing ${res.photoName} (${(p * 100).toInt()}%)"
                        }
                        isRunning = false
                        updatePhotoCount()
                        detailLog = ""
                        status = "Complete! Reports saved. ($photoCount processed)"
                    }
                },
                enabled = !isRunning && photoCount > 0,
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

private suspend fun runExperiment(
    experimentDir: File,
    reportDir: File,
    debugCropDir: File,
    vehicles: List<Vehicle>,
    context: Context,
    onLog: (String) -> Unit,
    onProgress: (PhotoResult, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    
    suspend fun updateLog(msg: String) = withContext(Dispatchers.Main) { onLog(msg) }

    updateLog("Caching vehicle references...")
    data class CachedRef(val vehicle: Vehicle, val bmp: Bitmap, val ocr: OcrResult)
    val cachedRefs = vehicles.map { v ->
        val bmp = BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val ocr = OdometerOcrUtils.extractFullImageOcr(v.referenceDashPhotoUrl!!)
        CachedRef(v, bmp, ocr)
    }

    updateLog("Building word significance map...")
    val globalWordCounts = mutableMapOf<String, Int>()
    val dynamicAnchors = mutableMapOf<String, String>()
    cachedRefs.forEach { ref ->
        ref.ocr.textBlocks.map { it.text.lowercase().trim() }.distinct().forEach { w ->
            if (w.length >= 3) globalWordCounts[w] = (globalWordCounts[w] ?: 0) + 1
        }
    }
    cachedRefs.forEach { ref ->
        ref.ocr.textBlocks.map { it.text.lowercase().trim() }.distinct().forEach { w ->
            if (globalWordCounts[w] == 1) dynamicAnchors[w] = ref.vehicle.name
        }
    }

    val jsonArray = JSONArray()
    var partCount = 1
    val maxSizeBytes = 1 * 1024 * 1024
    var currentSize = 0
    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply {
        writeText(buildHtmlHeader(timestamp, total, vehicles))
    }
    var currentFile = startNewFile()
    val footer = "</table></body></html>"

    photos.forEachIndexed { index, file ->
        try {
            updateLog("Decoding ${file.name}...")
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            
            updateLog("Running discovery OCR (ML Kit + Tess)...")
            val queryOcrTess = OdometerOcrUtils.extractFullImageOcr(file.absolutePath)
            val queryOcrMl = OdometerOcrUtils.extractFromPhoto(file.absolutePath)
            
            val vehicleMatchResults = mutableListOf<VehicleMatchResult>()
            val methodWinners = mutableMapOf<String, String>()
            val methodTopScores = mutableMapOf<String, Float>()
            
            cachedRefs.forEach { ref ->
                updateLog("Matching vs ${ref.vehicle.name}...")
                val odometerCropF = ref.vehicle.odometerCropLeft?.let { android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f) }
                val otherTextCropF = ref.vehicle.otherTextCropLeft?.let { android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f) }
                
                val allOtherRefs = cachedRefs.map { it.ocr }
                val allResults = ImageAlignmentUtils.matchWithAllMethods(
                    ref.bmp, originalBitmap, ref.ocr, queryOcrMl, odometerCropF, otherTextCropF, 
                    skipExpensiveORB = false, globalWordCounts = globalWordCounts, 
                    allOtherRefs = allOtherRefs, dynamicAnchors = dynamicAnchors, currentVehicleName = ref.vehicle.name
                )
                
                allResults.forEach { (m, res) ->
                    if (res.confidence > (methodTopScores[m] ?: -1f)) {
                        methodTopScores[m] = res.confidence; methodWinners[m] = ref.vehicle.name
                    }
                }

                val alignRes = allResults["feature"]!!
                val hubRes = allResults["hub"]!!
                var fullSteps = emptyList<OcrStepResult>()
                var alignRescued = false
                
                if (alignRes.success && alignRes.alignedImage != null) {
                    val crop = manualCropOdometer(alignRes.alignedImage, ref.vehicle, debugCropDir, file.name)
                    if (crop != null) {
                        updateLog("OCR on aligned crop (ORB)...")
                        fullSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                    }
                } else if (hubRes.success && hubRes.alignedImage != null) {
                    alignRescued = true
                    val crop = manualCropOdometer(hubRes.alignedImage, ref.vehicle, debugCropDir, "rescued_" + file.name)
                    if (crop != null) {
                        updateLog("OCR on rescued crop (Hub)...")
                        fullSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                    }
                }
                
                var hubSteps = emptyList<OcrStepResult>()
                if (hubRes.success && hubRes.alignedImage != null) {
                    val crop = manualCropOdometer(hubRes.alignedImage, ref.vehicle, debugCropDir, "hub_" + file.name)
                    if (crop != null) hubSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                }

                var anchorSteps = emptyList<OcrStepResult>()
                if (odometerCropF != null) {
                    val proj = ImageAlignmentUtils.projectCropViaAnchor(ref.ocr.textBlocks, queryOcrMl.textBlocks, odometerCropF, ref.bmp.width, ref.bmp.height, originalBitmap.width, originalBitmap.height)
                    if (proj != null) {
                        val crop = manualCropFromRectF(originalBitmap, proj, ref.vehicle, debugCropDir, "anc_" + file.name)
                        if (crop != null) anchorSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                    }
                }
                
                val finalAlignBase64 = if (alignRes.alignedImage != null) bitmapToBase64(alignRes.alignedImage, 70) else if (alignRescued && hubRes.alignedImage != null) bitmapToBase64(hubRes.alignedImage, 70) else ""

                vehicleMatchResults.add(VehicleMatchResult(
                    vehicleName = ref.vehicle.name,
                    score = allResults["consensus"]?.confidence ?: 0f,
                    message = "${alignRes.message} | ${hubRes.message} | DashText: [${queryOcrMl.textBlocks.joinToString(",") { it.text }}]" + (if (alignRescued) " | ORB Rescued by Hub" else ""),
                    referenceBase64 = bitmapToBase64(drawCropBoxesOnReference(ref.bmp, ref.vehicle), 70),
                    fullAlignedBase64 = finalAlignBase64,
                    hubAlignedBase64 = if (hubRes.alignedImage != null) bitmapToBase64(hubRes.alignedImage, 70) else "",
                    fullOcrSteps = fullSteps,
                    hubOcrSteps = hubSteps,
                    anchorOcrSteps = anchorSteps,
                    methodScores = allResults.mapValues { it.value.confidence },
                    wordVeto = allResults["consensus"]?.wordVeto ?: false,
                    queryTesseractFullOcr = queryOcrTess.textBlocks.joinToString(",") { it.text },
                    queryMlKitFullOcr = queryOcrMl.textBlocks.joinToString(",") { it.text },
                    allMethodResults = allResults,
                    tieredTierReached = allResults["tiered"]?.tierReached ?: 0
                ))
            }
            
            val winner = vehicleMatchResults.maxByOrNull { it.allMethodResults["tiered"]?.confidence ?: -1f }
            var extractedOdometer: String? = null
            if (winner != null && winner.vehicleName != "No match") {
                extractedOdometer = pickBestOdometer(winner.fullOcrSteps, winner.hubOcrSteps, winner.anchorOcrSteps)
            }

            val photoResult = PhotoResult(
                photoName = file.name,
                matchedVehicle = winner?.vehicleName ?: "No match",
                finalConfidence = winner?.allMethodResults?.get("tiered")?.confidence ?: 0f,
                originalThumbBase64 = bitmapToBase64(originalBitmap, 80),
                allVehicleResults = vehicleMatchResults,
                methodWinners = methodWinners,
                odometer = extractedOdometer
            )
            
            val rowHtml = buildHtmlRow(photoResult, index, vehicles)
            if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length

            val jsonRow = JSONObject().apply {
                put("file", file.name)
                put("winner", photoResult.matchedVehicle)
                put("confidence", photoResult.finalConfidence.toDouble())
                put("odometer", extractedOdometer ?: "FAILED")
                put("tiered_tier", winner?.tieredTierReached ?: 4)
                val metrics = JSONObject()
                winner?.allMethodResults?.forEach { (m, res) ->
                    val mObj = JSONObject().apply {
                        put("score", res.confidence.toDouble())
                        put("time_ms", res.timeMs)
                        put("status", if (res.success) "Success" else "Failed")
                        put("tier", res.tierReached)
                    }
                    metrics.put(m, mObj)
                }
                put("algorithm_metrics", metrics)
                val mWins = JSONObject(); methodWinners.forEach { (m, w) -> mWins.put(m, w) }; put("method_winners", mWins)
            }
            jsonArray.put(jsonRow)

            withContext(Dispatchers.Main) { onProgress(photoResult, (index + 1).toFloat() / total) }
            originalBitmap.recycle()
        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }

    currentFile.appendText(footer)
    File(reportDir, "alignment_results_${timestamp}.json").writeText(jsonArray.toString(2))
    cachedRefs.forEach { it.bmp.recycle() }
}

private fun buildHtmlHeader(time: String, total: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<html><head><title>Alignment Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 10px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 150px; height: auto; border: 1px solid #eee; } .score-box { text-align: left; font-size: 9px; background: #f9f9f9; padding: 4px; border-radius: 4px; overflow-wrap: break-word; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px; }</style></head><body>")
    appendLine("<h1>Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th style='width:80px;'># & Photo</th><th style='width:160px;'>Original</th>")
    allVehicles.forEach { v -> appendLine("<th style='width:160px;'>${v.name} Match</th><th style='width:160px;'>${v.name} Full OCR</th><th style='width:160px;'>${v.name} Hub OCR</th><th style='width:160px;'>${v.name} Anchor OCR</th>") }
    appendLine("<th style='width:120px;'>Final Result</th></tr>")
}

private fun buildHtmlRow(res: PhotoResult, index: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<tr><td>${index + 1}<br><small>${res.photoName}</small></td>")
    appendLine("<td><img src='data:image/jpeg;base64,${res.originalThumbBase64}'></td>")
    allVehicles.forEach { vehicle ->
        val vMatch = res.allVehicleResults.find { it.vehicleName == vehicle.name }
        if (vMatch != null) {
            val isWinner = res.matchedVehicle == vehicle.name
            appendLine("<td class='${if (isWinner) "winner" else ""}'>")
            appendLine("<div class='score-box'>")
            appendLine("<b>Score:</b> ${"%.3f".format(vMatch.score)}<br>")
            appendLine("<b>Tier:</b> ${vMatch.tieredTierReached}<br>")
            appendLine("<b>Veto:</b> ${if (vMatch.wordVeto) "YES" else "no"}<br>")
            appendLine("<b>Msg:</b> ${vMatch.message}<br>")
            appendLine("</div>")
            appendLine("<div style='margin-top:5px; background:#eee; padding:2px;'><small>CORRECTED DASH:</small><br>")
            if (vMatch.fullAlignedBase64.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,${vMatch.fullAlignedBase64}' style='max-width:140px;'><br><small>ORB/Rescued</small><br>")
            if (vMatch.hubAlignedBase64.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,${vMatch.hubAlignedBase64}' style='max-width:140px;'><br><small>Hub Mech</small>")
            appendLine("</div></td>")
            appendLine("<td>${buildOcrStepHtml(vMatch.fullOcrSteps)}</td>")
            appendLine("<td>${buildOcrStepHtml(vMatch.hubOcrSteps)}</td>")
            appendLine("<td>${buildOcrStepHtml(vMatch.anchorOcrSteps)}</td>")
        } else { appendLine("<td colspan='4'>No Result</td>") }
    }
    appendLine("<td><b>Match:</b> ${res.matchedVehicle}<br><b>Tier:</b> ${res.allVehicleResults.find { it.vehicleName == res.matchedVehicle }?.tieredTierReached ?: "N/A"}<br><b>Odo:</b> ${res.odometer ?: "FAILED"}</td></tr>")
}

private fun buildOcrStepHtml(steps: List<OcrStepResult>): String = buildString {
    if (steps.isEmpty()) { appendLine("<i>(No crop)</i>"); return@buildString }
    steps.forEach { step -> appendLine("<div class='ocr-step'><b>${step.stageName}:</b> ${step.text ?: "-"}<br></div>") }
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
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

private fun manualCropOdometer(bmp: Bitmap, vehicle: Vehicle, debugDir: File, fileName: String): Bitmap? {
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

private fun manualCropFromRectF(bmp: Bitmap, rect: android.graphics.RectF, vehicle: Vehicle, debugDir: File, fileName: String): Bitmap? {
    val left = (rect.left * bmp.width).toInt().coerceAtLeast(0)
    val top = (rect.top * bmp.height).toInt().coerceAtLeast(0)
    val width = ((rect.right - rect.left) * bmp.width).toInt().coerceAtMost(bmp.width - left)
    val height = ((rect.bottom - rect.top) * bmp.height).toInt().coerceAtMost(bmp.height - top)
    if (width <= 0 || height <= 0) return null
    return Bitmap.createBitmap(bmp, left, top, width, height)
}

private fun pickBestOdometer(full: List<OcrStepResult>, hub: List<OcrStepResult>, anchor: List<OcrStepResult>): String? {
    val allSteps = full + hub + anchor
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
    } catch (e: Exception) {
        Log.e(TAG, "Zip error", e)
        false
    }
}
