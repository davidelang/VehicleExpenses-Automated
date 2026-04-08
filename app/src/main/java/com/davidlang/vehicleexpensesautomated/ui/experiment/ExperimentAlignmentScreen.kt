package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OcrStepResult
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
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

data class CachedRef(val vehicle: Vehicle, val bmp: Bitmap, val ocr: OcrResult)

data class VehicleMatchResult(
    val vehicleName: String,
    val score: Float,
    val message: String,
    val referenceBase64: String,
    val fullAlignedBase64: String,
    val fullOcrSteps: List<OcrStepResult> = emptyList(),
    val anchorOcrSteps: List<OcrStepResult> = emptyList(),
    val methodScores: Map<String, Float>
)

data class PhotoResult(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val originalThumbBase64: String,
    val allVehicleResults: List<VehicleMatchResult>,
    val methodWinners: Map<String, String> = emptyMap(),
    val odometer: String? = null
)

@Composable
fun ExperimentAlignmentScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val experimentDir = File(context.filesDir, "experiment_photos")
    val reportDir = File(context.filesDir, "experiment_reports").apply { mkdirs() }
    val debugCropDir = File(context.filesDir, "experiment_debug_crops").apply { mkdirs() }
    var status by remember { mutableStateOf("Checking experiment folder...") }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentPhoto by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                status = "Extracting ZIP..."
                val success = extractZipToPhotos(uri, experimentDir, context)
                status = if (success) "ZIP extracted successfully!" else "Failed to extract ZIP"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!experimentDir.exists()) experimentDir.mkdirs()
        val count = experimentDir.listFiles()?.size ?: 0
        status = if (count == 0) "Folder is empty.\nUse the buttons below." else "$count photos ready."
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Alignment Experiment") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = status, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (isRunning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(text = currentPhoto.ifEmpty { "Processing..." }, style = MaterialTheme.typography.bodyMedium)
            }
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
                    if (isRunning) return@Button
                    isRunning = true
                    progress = 0f
                    currentPhoto = ""
                    status = "Starting experiment..."
                    scope.launch {
                        try {
                            val summary = runFullExperiment(vehicles, experimentDir, reportDir, debugCropDir, context) { p, name ->
                                progress = p
                                currentPhoto = name
                            }
                            status = "Test complete!\n$summary"
                        } catch (e: Exception) {
                            status = "Error: ${e.message}"
                            Log.e(TAG, "Experiment failed", e)
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = !isRunning && experimentDir.listFiles()?.isNotEmpty() == true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Running..." else "Run Alignment Experiment Now")
            }
            Button(onClick = { navController?.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Quick Fill-up")
            }
        }
    }
}

private suspend fun runFullExperiment(
    vehicles: List<Vehicle>,
    experimentDir: File,
    reportDir: File,
    debugCropDir: File,
    context: android.content.Context,
    onProgress: (Float, String) -> Unit
): String = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") && !it.name.contains("pump", true) && !it.name.contains("receipt", true) } ?: emptyList()
    val total = photos.size
    if (total == 0) return@withContext "No photos found"
    
    var successCount = 0
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val jsonArray = JSONArray()
    
    // Cache references
    val cachedRefs = vehicles.mapNotNull { vehicle ->
        val url = vehicle.referenceDashPhotoUrl ?: return@mapNotNull null
        val file = File(url)
        if (!file.exists()) return@mapNotNull null
        val bmp = BitmapFactory.decodeFile(url) ?: return@mapNotNull null
        val ocr = OdometerOcrUtils.extractFromPhoto(url)
        CachedRef(vehicle, bmp, ocr)
    }

    var currentPage = 1
    var currentSize = 0L
    val maxSizeKB = 2000
    val maxSizeBytes = maxSizeKB * 1024L
    val reportFiles = mutableListOf<File>()
    
    fun startNewFile(): File {
        val f = File(reportDir, "alignment_report_${timestamp}_part${currentPage}.html")
        f.writeText(buildHtmlHeader(timestamp, total, vehicles))
        reportFiles.add(f); currentPage++; currentSize = 0L
        return f
    }
    var currentFile = startNewFile()
    val footer = "</table></body></html>"

    photos.forEachIndexed { index, file ->
        onProgress((index.toFloat() / total), "Processing ${file.name} (${index+1}/$total)")
        try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            val queryOcr = OdometerOcrUtils.extractFromPhoto(file.absolutePath)
            
            val vehicleMatchResults = mutableListOf<VehicleMatchResult>()
            val methodWinners = mutableMapOf<String, String>()
            val methodTopScores = mutableMapOf<String, Float>()
            
            cachedRefs.forEach { ref ->
                val odometerCropF = ref.vehicle.odometerCropLeft?.let {
                    android.graphics.RectF(it, ref.vehicle.odometerCropTop ?: 0f, ref.vehicle.odometerCropRight ?: 1f, ref.vehicle.odometerCropBottom ?: 1f)
                }
                val otherTextCropF = ref.vehicle.otherTextCropLeft?.let {
                    android.graphics.RectF(it, ref.vehicle.otherTextCropTop ?: 0f, ref.vehicle.otherTextCropRight ?: 1f, ref.vehicle.otherTextCropBottom ?: 1f)
                }
                
                // Matching
                val allResults = ImageAlignmentUtils.matchWithAllMethods(ref.bmp, originalBitmap, ref.ocr, queryOcr, odometerCropF, otherTextCropF, skipExpensiveORB = true)
                val consensusRes = allResults["consensus"]!!
                
                allResults.forEach { (m, res) ->
                    if (res.confidence > (methodTopScores[m] ?: -1f)) {
                        methodTopScores[m] = res.confidence; methodWinners[m] = ref.vehicle.name
                    }
                }

                // Dual Alignment & OCR
                val alignRes = ImageAlignmentUtils.alignImages(ref.bmp, originalBitmap, 10, odometerCropF, otherTextCropF)
                var fullSteps = emptyList<OcrStepResult>()
                if (alignRes.success && alignRes.alignedImage != null) {
                    val crop = manualCropOdometer(alignRes.alignedImage, ref.vehicle, debugCropDir, file.name)
                    if (crop != null) fullSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                }
                
                var anchorSteps = emptyList<OcrStepResult>()
                if (odometerCropF != null) {
                    val proj = ImageAlignmentUtils.projectCropViaAnchor(
                        ref.ocr.textBlocks, queryOcr.textBlocks, odometerCropF,
                        ref.bmp.width, ref.bmp.height, originalBitmap.width, originalBitmap.height
                    )
                    if (proj != null) {
                        val crop = manualCropFromRectF(originalBitmap, proj, ref.vehicle, debugCropDir, "anc_" + file.name)
                        if (crop != null) anchorSteps = OdometerOcrUtils.runMultiStepOcr(crop, context)
                    }
                }
                
                vehicleMatchResults.add(VehicleMatchResult(
                    vehicleName = ref.vehicle.name,
                    score = consensusRes.confidence,
                    message = alignRes.message,
                    referenceBase64 = bitmapToBase64(drawCropBoxesOnReference(ref.bmp, ref.vehicle), 180),
                    fullAlignedBase64 = if (alignRes.alignedImage != null) bitmapToBase64(alignRes.alignedImage, 180) else "",
                    fullOcrSteps = fullSteps,
                    anchorOcrSteps = anchorSteps,
                    methodScores = allResults.mapValues { it.value.confidence }
                ))
            }
            
            val winner = vehicleMatchResults.maxByOrNull { it.score }
            var extractedOdometer: String? = null
            if (winner != null && winner.vehicleName != "No match") {
                extractedOdometer = pickBestOdometer(winner.fullOcrSteps, winner.anchorOcrSteps)
                if (extractedOdometer != null) successCount++
            }

            val photoResult = PhotoResult(
                photoName = file.name,
                matchedVehicle = winner?.vehicleName ?: "No match",
                finalConfidence = winner?.score ?: 0f,
                originalThumbBase64 = bitmapToBase64(originalBitmap, 180),
                allVehicleResults = vehicleMatchResults,
                methodWinners = methodWinners,
                odometer = extractedOdometer
            )
            
            val rowHtml = buildHtmlRow(photoResult, index, vehicles)
            if (currentSize + rowHtml.length > maxSizeBytes) {
                currentFile.appendText(footer); currentFile = startNewFile()
            }
            currentFile.appendText(rowHtml); currentSize += rowHtml.length

            val jsonRow = JSONObject().apply {
                put("file", file.name); put("winner", photoResult.matchedVehicle); put("confidence", photoResult.finalConfidence.toDouble()); put("odometer", extractedOdometer ?: "FAILED")
                val mWins = JSONObject(); methodWinners.forEach { (m, w) -> mWins.put(m, w) }; put("method_winners", mWins)
            }
            jsonArray.put(jsonRow)

        } catch (e: Exception) { Log.e(TAG, "Failed ${file.name}", e) }
    }
    
    currentFile.appendText(footer)
    try {
        val jsonFile = File(reportDir, "alignment_results_${timestamp}.json")
        jsonFile.writeText(jsonArray.toString(2))
    } catch (e: Exception) { Log.e(TAG, "Failed JSON write", e) }

    cachedRefs.forEach { it.bmp.recycle() }
    "Processed $total. ${reportFiles.size} parts + JSON."
}

private fun buildHtmlHeader(time: String, total: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<html><head><title>Alignment Experiment - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 10px; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; } img { max-width: 150px; height: auto; border: 1px solid #eee; } .score-box { text-align: left; font-size: 9px; background: #f9f9f9; padding: 4px; border-radius: 4px; } .winner { background-color: #e6ffed; border: 2px solid #28a745; } .ocr-step { margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px; }</style></head><body>")
    appendLine("<h1>Alignment Experiment</h1><p><b>Run:</b> $time | <b>Total:</b> $total</p><table><tr><th># & Photo</th><th>Original</th>")
    allVehicles.forEach { v ->
        appendLine("<th>${v.name} Match</th><th>${v.name} Full OCR</th><th>${v.name} Anchor OCR</th>")
    }
    appendLine("<th>Final Result</th></tr>")
}

private fun buildHtmlRow(r: PhotoResult, index: Int, allVehicles: List<Vehicle>): String = buildString {
    appendLine("<tr><td>${index + 1}<br><b>${r.photoName}</b></td><td><img src='data:image/jpeg;base64,${r.originalThumbBase64}'></td>")
    allVehicles.forEach { vehicle ->
        val vRes = r.allVehicleResults.find { it.vehicleName == vehicle.name }
        val winnerClass = if (r.matchedVehicle == vehicle.name) "winner" else ""
        appendLine("<td class='$winnerClass'>")
        if (vRes != null) {
            appendLine("<img src='data:image/jpeg;base64,${vRes.referenceBase64}'><br>")
            appendLine("<div style='margin-bottom:5px;'>")
            r.methodWinners.forEach { (m, winnerName) ->
                if (winnerName == vehicle.name) {
                    val color = when(m) {
                        "feature" -> "#90EE90"; "arg" -> "#87CEEB"; "histogram" -> "#FFA500"
                        "embedding" -> "#BA55D3"; "anchor" -> "#FFB6C1"; "consensus" -> "#FFD700"
                        else -> "#CCCCCC"
                    }
                    appendLine("<span style='background-color:$color; padding:2px 4px; border-radius:3px; margin-right:2px; font-weight:bold; border:1px solid #666;'>${m.uppercase().take(1)}</span>")
                }
            }
            appendLine("</div><div class='score-box'>")
            vRes.methodScores.forEach { (m, s) -> appendLine("<b>$m:</b> ${"%.3f".format(s)}<br>") }
            appendLine("</div>")
        } else appendLine("N/A")
        appendLine("</td>")
        appendLine("<td class='$winnerClass'>")
        vRes?.fullOcrSteps?.forEach { step ->
            val b64 = bitmapToBase64(step.bitmap, 120)
            val ocrText = step.text ?: ""
            appendLine("<div class='ocr-step'><small>${step.stageName}</small><br><img src='data:image/jpeg;base64,$b64'><br><b>OCR: $ocrText</b></div>")
        }
        appendLine("</td>")
        appendLine("<td class='$winnerClass'>")
        vRes?.anchorOcrSteps?.forEach { step ->
            val b64 = bitmapToBase64(step.bitmap, 120)
            val ocrText = step.text ?: ""
            appendLine("<div class='ocr-step'><small>${step.stageName}</small><br><img src='data:image/jpeg;base64,$b64'><br><b>OCR: $ocrText</b></div>")
        }
        appendLine("</td>")
    }
    appendLine("<td><b>Winner:</b> ${r.matchedVehicle}<br><b>OCR:</b> ${r.odometer ?: "FAIL"}</td></tr>")
}

private fun manualCropOdometer(aligned: Bitmap?, vehicle: Vehicle, debugDir: File, photoName: String): Bitmap? {
    if (aligned == null) return null
    val leftF = vehicle.odometerCropLeft ?: return null
    val topF = vehicle.odometerCropTop ?: 0f
    val rightF = vehicle.odometerCropRight ?: 1f
    val bottomF = vehicle.odometerCropBottom ?: 1f
    val w = aligned.width; val h = aligned.height
    val left = (leftF * w).toInt().coerceAtLeast(0); val top = (topF * h).toInt().coerceAtLeast(0)
    val right = (rightF * w).toInt().coerceAtMost(w); val bottom = (bottomF * h).toInt().coerceAtMost(h)
    val cropW = right - left; val cropH = bottom - top
    if (cropW < 1 || cropH < 1) return null
    return try {
        val cropped = Bitmap.createBitmap(aligned, left, top, cropW, cropH)
        val debugFile = File(debugDir, "crop_${vehicle.name}_${photoName}.jpg")
        val out = java.io.FileOutputStream(debugFile); cropped.compress(Bitmap.CompressFormat.JPEG, 90, out); out.close()
        cropped
    } catch (e: Exception) { null }
}

private fun manualCropFromRectF(bmp: Bitmap, rect: android.graphics.RectF, vehicle: Vehicle, debugDir: File, name: String): Bitmap? {
    val w = bmp.width; val h = bmp.height
    val left = (rect.left * w).toInt().coerceAtLeast(0); val top = (rect.top * h).toInt().coerceAtLeast(0)
    val right = (rect.right * w).toInt().coerceAtMost(w); val bottom = (rect.bottom * h).toInt().coerceAtMost(h)
    val cropW = right - left; val cropH = bottom - top
    if (cropW < 1 || cropH < 1) return null
    return try {
        val cropped = Bitmap.createBitmap(bmp, left, top, cropW, cropH)
        val debugFile = File(debugDir, "crop_${vehicle.name}_${name}.jpg")
        val out = java.io.FileOutputStream(debugFile); cropped.compress(Bitmap.CompressFormat.JPEG, 90, out); out.close()
        cropped
    } catch (e: Exception) { null }
}

private fun drawCropBoxesOnReference(refBmp: Bitmap?, vehicle: Vehicle): Bitmap? {
    if (refBmp == null) return null
    val bitmap = refBmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.RED }
    vehicle.odometerCropLeft?.let { left ->
        val l = left * bitmap.width; val t = (vehicle.odometerCropTop ?: 0f) * bitmap.height
        val r = (vehicle.odometerCropRight ?: 1f) * bitmap.width; val b = (vehicle.odometerCropBottom ?: 1f) * bitmap.height
        canvas.drawRect(l, t, r, b, paint)
    }
    return bitmap
}

private fun bitmapToBase64(bitmap: Bitmap?, maxWidth: Int): String {
    if (bitmap == null) return ""
    val scaled = if (bitmap.width > maxWidth) {
        val scale = maxWidth.toFloat() / bitmap.width
        Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).toInt(), true)
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
    return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: android.content.Context): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().matches(Regex(".*\\\\.(jpg|jpeg|png)$"))) {
                        val outFile = File(targetDir, entry.name.substringAfterLast('/'))
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { Log.e(TAG, "ZIP extraction failed", e); false }
}

private fun pickBestOdometer(fullSteps: List<OcrStepResult>, anchorSteps: List<OcrStepResult>): String? {
    val allSteps = fullSteps + anchorSteps
    if (allSteps.isEmpty()) return null

    val errorStrings = listOf("(no text)", "(Tesseract init failed)", "FAILED")
    
    // 1. Normalize and score candidates
    // We give higher weight to stages we know are better (Grayscale, Bilateral)
    val scoredCandidates = mutableMapOf<String, Float>()
    
    allSteps.forEach { step ->
        val raw = step.text ?: return@forEach
        if (raw in errorStrings || raw.isBlank()) return@forEach
        
        val clean = raw.replace(" ", "")
                       .replace("I", "1").replace("l", "1")
                       .replace("O", "0").replace("o", "0")
                       .replace("S", "5").replace("s", "5")
                       .replace("B", "8")
        
        val match = Regex("""\d{4,7}""").find(clean)
        val digits = match?.value ?: return@forEach
        
        val weight = when(step.stageName) {
            "Grayscale" -> 1.5f
            "Bilateral" -> 1.5f
            "Raw" -> 1.0f
            "CLAHE" -> 0.5f
            "Threshold" -> 0.5f
            else -> 1.0f
        }
        
        scoredCandidates[digits] = (scoredCandidates[digits] ?: 0f) + weight
    }

    if (scoredCandidates.isEmpty()) return null

    // 2. Pick the candidate with the highest total weight
    return scoredCandidates.maxByOrNull { it.value }?.key
}
