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
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
private const val AMAZON_PHOTOS_LINK = "https://www.amazon.com/photos/shared/81xh078qSgydiVwUH9VWBw.EcItxhL_TTM9KNvR0akUC0"
private const val TAG = "ExperimentAlignment"
private const val PLACEHOLDER_BASE64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/4gHYSUNDX1BST0ZJTEUAAQEAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAEAAAAAmZmAADypwAADVkAABPQAAAKWwAAAAAAAAAAbWx1YwAAAAAAAAABAAAADGVuVVMAAAAgAAAAHABHAG8AbwBnAGwAZQAgAEkAbgBjAC4AIAAyADAAMQA2/9sAQwAQCwwODAoQDg0OEhEQExgoGhgWFhgxIyUdKDozPTw5Mzg3QEhcTkBEV0U3OFBtUVdfYmdoZz5NcXlwZHhcZWdj/8AACwgACgAOAQERAP/EABUAAQEAAAAAAAAAAAAAAAAAAAIG/8QAGREBAQEBAQAAAAAAAAAAAAAAACERAQH/4gAgTVBGAE1NACoAAAAIAAGwAAAHAAAABDAxMDAAAAAA/9oACAEBAAA/AMLx6QmsoA8bqyd82tjpPLNjX4MlFUA9FKiv/9k="

@Composable
fun ExperimentAlignmentScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val experimentDir = File(context.filesDir, "experiment_photos")
    val reportDir = File(context.filesDir, "experiment_reports").apply { mkdirs() }
    var status by remember { mutableStateOf("Checking experiment folder...") }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentPhoto by remember { mutableStateOf("") }
    var reportPath by remember { mutableStateOf<String?>(null) }
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
    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var cleanedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            isCleaning = true
            try {
                val bmp = BitmapFactory.decodeFile(url) ?: return@let
                val (cleanedBmp, _) = ImageAlignmentUtils.createCleanedReference(bmp)
                if (cleanedBmp != null) {
                    val tempFile = File(context.cacheDir, "temp_cleaned_${System.currentTimeMillis()}.jpg")
                    val out = java.io.FileOutputStream(tempFile)
                    cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    cleanedPhotoUrl = tempFile.absolutePath
                    cleanedBmp.recycle()
                    Log.i("Experiment", "Cleaned image ready for alignment test")
                }
            } catch (e: Exception) {
                Log.e("Experiment", "Cleaning failed", e)
            } finally {
                isCleaning = false
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
                .padding(16.dp),
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
                Toast.makeText(context, "Opened Amazon Photos — tap 'Download all'", Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Amazon Photos Album (100+ images)")
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
                    status = "Starting alignment test..."
                    scope.launch {
                        try {
                            val result = runFullExperiment(vehicles, experimentDir, viewModel, context) { p, name ->
                                progress = p
                                currentPhoto = name
                            }
                            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                            val htmlFiles = writeSizeSplitHtmlReports(result.htmlReport, reportDir, timestamp, maxSizeKB = 300)
                            reportPath = htmlFiles.firstOrNull()?.absolutePath
                            status = "Test complete!\n${result.summary}\n${htmlFiles.size} report files written"
                            Log.i(TAG, "Reports written: ${htmlFiles.size} files")
                        } catch (e: Exception) {
                            status = "Report generation failed: ${e.message}"
                            Log.e(TAG, "Report generation failed", e)
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
            if (reportPath != null) {
                Button(onClick = { Toast.makeText(context, "Reports written to: $reportPath (and siblings)", Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Latest Reports")
                }
            }
            Button(onClick = { navController?.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Quick Fill-up")
            }
        }
    }
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: android.content.Context): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().matches(Regex(".*\\.(jpg|jpeg|png)$"))) {
                        val outFile = File(targetDir, entry.name.substringAfterLast('/'))
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "ZIP extraction failed", e)
        false
    }
}

private suspend fun runFullExperiment(
    vehicles: List<Vehicle>,
    experimentDir: File,
    viewModel: VehicleViewModel,
    context: android.content.Context,
    onProgress: (Float, String) -> Unit
): ExperimentResult {
    val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") && !it.name.contains("pump", true) && !it.name.contains("receipt", true) } ?: emptyList()
    val total = photos.size
    if (total == 0) return ExperimentResult("No photos found", "<h1>No photos</h1>")
    val results = mutableListOf<PhotoResult>()
    var success = 0
    photos.forEachIndexed { index, file ->
        onProgress((index.toFloat() / total), "Processing ${file.name} (${index+1}/$total)")
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
        val (cleanedBmp, dashTextBlocks) = ImageAlignmentUtils.createCleanedReference(originalBitmap)
        val vehicleAlignments = mutableListOf<VehicleAlignment>()
        vehicles.forEach { vehicle ->
            val refUrl = viewModel.ensureCleanedReference(vehicle) ?: vehicle.referenceDashPhotoUrl
            if (refUrl == null) return@forEach
            val refFile = File(refUrl)
            if (!refFile.exists()) return@forEach
            val refBmp = BitmapFactory.decodeFile(refFile.absolutePath) ?: return@forEach
            val odometerCrop = vehicle.odometerCropLeft?.let {
                android.graphics.RectF(it, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f)
            }
            val otherTextCrop = vehicle.otherTextCropLeft?.let {
                android.graphics.RectF(it, vehicle.otherTextCropTop ?: 0f, vehicle.otherTextCropRight ?: 1f, vehicle.otherTextCropBottom ?: 1f)
            }
            val alignment = ImageAlignmentUtils.alignImages(refBmp, originalBitmap, minInliers = 12, odometerCrop, otherTextCrop, useAffineFallback = true)
            if (alignment.success) {
                val inliersMatch = Regex("with (\\d+) inliers").find(alignment.message)
                val inliers = inliersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val textSim = computeTextSimilarity(dashTextBlocks ?: "", vehicle.referenceTextBlocks ?: "")
                val maxSpeedPenalty = if (speedRangeMismatch(dashTextBlocks ?: "", vehicle.referenceTextBlocks ?: "")) 0.0f else 1.0f
                val combinedScore = (0.65f * alignment.confidence + 0.35f * textSim) * maxSpeedPenalty
                vehicleAlignments.add(VehicleAlignment(vehicle.name, alignment.alignedImage, combinedScore, inliers, textSim))
            }
        }
        val sortedAlignments = vehicleAlignments.sortedByDescending { it.combinedScore }
        val top2 = sortedAlignments.take(2)
        val best = sortedAlignments.firstOrNull() ?: VehicleAlignment("No match", null, 0f, 0, 0f)
        var bestScore = best.combinedScore
        var bestVehicleName = best.vehicleName
        var inliersCount = best.inliers
        var alignmentMessage = "Aligned with $inliersCount inliers (${String.format("%.1f", bestScore * 100)}%)"
        var alignedBitmap = best.alignedBitmap
        var odometerCropBitmap: Bitmap? = null
        var extractedOdometer: String? = null
        var fullImageOcr: String? = null
        if (alignedBitmap != null) {
            val matchedVehicle = vehicles.find { it.name == bestVehicleName }
            if (matchedVehicle != null && bestVehicleName != "No match") {
                odometerCropBitmap = manualCropOdometer(alignedBitmap, matchedVehicle)
            }
            val tempAlignedFile = File(context.cacheDir, "aligned_${file.name}")
            val out = java.io.FileOutputStream(tempAlignedFile)
            alignedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            val ocrResult = OdometerOcrUtils.extractFromPhoto(tempAlignedFile.absolutePath)
            extractedOdometer = ocrResult.odometer
            if (odometerCropBitmap == null) odometerCropBitmap = ocrResult.croppedBitmap
            val fullOcrResult = OdometerOcrUtils.extractFullImageOcr(tempAlignedFile.absolutePath, ocrResult.textBlocks)
            fullImageOcr = fullOcrResult.odometer
            tempAlignedFile.delete()
            if (ocrResult.odometer != null) success++
        }
        val referenceWithCrop = if (bestVehicleName != "No match") {
            vehicles.find { it.name == bestVehicleName }?.let { v ->
                val refUrl = viewModel.ensureCleanedReference(v) ?: v.referenceDashPhotoUrl
                if (refUrl != null) {
                    val refFile = File(refUrl)
                    if (refFile.exists()) {
                        val refBmp = BitmapFactory.decodeFile(refFile.absolutePath)
                        drawCropBoxesOnReference(refBmp, v)
                    } else null
                } else null
            }
        } else null
        val mungedFord = sortedAlignments.firstOrNull { it.vehicleName.contains("Ford", ignoreCase = true) }?.alignedBitmap
        val mungedHonda = sortedAlignments.firstOrNull { it.vehicleName.contains("Honda", ignoreCase = true) }?.alignedBitmap
        results.add(PhotoResult(
            photoName = file.name,
            vehicle = bestVehicleName,
            confidence = bestScore,
            inliersCount = inliersCount,
            alignmentMessage = alignmentMessage,
            topMatches = top2.joinToString(", ") { "${it.vehicleName}: ${String.format("%.1f", it.combinedScore * 100)}%" },
            originalThumbBase64 = bitmapToBase64(originalBitmap, 240),
            alignedBase64 = bitmapToBase64(alignedBitmap, 240),
            cleanedDashBase64 = bitmapToBase64(cleanedBmp, 240),
            odometerCropBase64 = bitmapToBase64(odometerCropBitmap, 240),
            referenceBase64 = bitmapToBase64(referenceWithCrop, 240),
            mungedFordBase64 = bitmapToBase64(mungedFord, 240),
            mungedHondaBase64 = bitmapToBase64(mungedHonda, 240),
            odometer = extractedOdometer,
            fullImageOcr = fullImageOcr,
            referenceTextBlocks = vehicles.find { it.name == bestVehicleName }?.referenceTextBlocks ?: "",
            dashTextBlocks = dashTextBlocks ?: "",
            textScore = best.textScore
        ))
    }
    onProgress(1f, "Generating visual report...")
    val html = buildRichHtmlReport(results, total)
    val summary = "Processed $total photos — $success successful alignments"
    return ExperimentResult(summary, html)
}

private fun computeTextSimilarity(dashText: String, refText: String): Float {
    val dashDigits = dashText.split(Regex("[^0-9]")).filter { it.isNotEmpty() }.toSet()
    val refDigits = refText.split(Regex("[^0-9]")).filter { it.isNotEmpty() }.toSet()
    if (dashDigits.isEmpty() || refDigits.isEmpty()) return 0f
    val intersection = dashDigits.intersect(refDigits).size
    val union = dashDigits.union(refDigits).size
    return intersection.toFloat() / union.toFloat()
}

private fun speedRangeMismatch(dashText: String, refText: String): Boolean {
    val dashMax = dashText.split(Regex("[^0-9]")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
    val refMax = refText.split(Regex("[^0-9]")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
    return dashMax > refMax + 20 // penalty if dash shows higher speed than reference
}

private data class VehicleAlignment(
    val vehicleName: String,
    val alignedBitmap: Bitmap?,
    val combinedScore: Float,
    val inliers: Int,
    val textScore: Float
)

private data class PhotoResult(
    val photoName: String,
    val vehicle: String,
    val confidence: Float,
    val inliersCount: Int,
    val alignmentMessage: String,
    val topMatches: String,
    val originalThumbBase64: String,
    val alignedBase64: String,
    val cleanedDashBase64: String,
    val odometerCropBase64: String,
    val referenceBase64: String,
    val mungedFordBase64: String,
    val mungedHondaBase64: String,
    val odometer: String?,
    val fullImageOcr: String?,
    val referenceTextBlocks: String,
    val dashTextBlocks: String,
    val textScore: Float
)

private data class ExperimentResult(val summary: String, val htmlReport: String)

private fun buildRichHtmlReport(results: List<PhotoResult>, total: Int): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("<html><head><title>Alignment Experiment - $time</title>")
        appendLine("<style>table { border-collapse: collapse; width: 100%; } th, td { border: 1px solid #ccc; padding: 8px; text-align: center; vertical-align: top; } img { max-width: 240px; height: auto; }</style></head><body>")
        appendLine("<h1>Alignment Experiment Report</h1>")
        appendLine("<p><b>Run:</b> $time | <b>Total photos:</b> $total | <b>Images optimized (&lt;300 KB total)</b></p>")
        appendLine("<table>")
        appendLine("<tr><th>#</th><th>Original</th><th>Cleaned Dash</th><th>Vehicle Reference + Crops</th><th>Aligned (Munged)</th><th>Munged Ford</th><th>Munged Honda</th><th>Odometer Crop</th><th>Matched Vehicle</th><th>Inliers</th><th>Alignment Info</th><th>Top 3 Matches</th><th>Extracted Odometer</th><th>Full Image OCR</th><th>Text Score</th><th>Confidence</th><th>Reference Text Blocks</th><th>Dash Text Blocks</th></tr>")
        results.forEachIndexed { index, r ->
            appendLine("<tr>")
            appendLine("<td>${index + 1}</td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.originalThumbBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.cleanedDashBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.referenceBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.alignedBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.mungedFordBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.mungedHondaBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.odometerCropBase64}'></td>")
            appendLine("<td>${r.vehicle}</td>")
            appendLine("<td>${r.inliersCount}</td>")
            appendLine("<td>${r.alignmentMessage}</td>")
            appendLine("<td>${r.topMatches}</td>")
            appendLine("<td>${r.odometer ?: "—"}</td>")
            appendLine("<td>${r.fullImageOcr ?: "—"}</td>")
            appendLine("<td>${"%.1f".format(r.textScore * 100)}%</td>")
            appendLine("<td>${"%.1f".format(r.confidence * 100)}%</td>")
            appendLine("<td>${r.referenceTextBlocks.replace("\n", "<br>").replace("|", "<br>")}</td>")
            appendLine("<td>${r.dashTextBlocks.replace("\n", "<br>").replace("|", "<br>")}</td>")
            appendLine("</tr>")
        }
        appendLine("</table></body></html>")
    }
}

private fun writeSizeSplitHtmlReports(fullHtml: String, reportDir: File, timestamp: String, maxSizeKB: Int = 300): List<File> {
    val lines = fullHtml.lines()
    val headerEndIndex = lines.indexOfFirst { it.trim().startsWith("<tr>") } + 1
    val header = lines.take(headerEndIndex).joinToString("\n")
    val footer = lines.drop(headerEndIndex).dropWhile { it.trim().startsWith("<tr>") }.joinToString("\n")
    val dataRows = lines.drop(headerEndIndex).takeWhile { it.trim().startsWith("<tr>") }
    val files = mutableListOf<File>()
    var currentChunk = mutableListOf<String>()
    var currentSize = 0L
    for (row in dataRows) {
        val rowSize = row.length + 2L
        if (currentSize + rowSize > maxSizeKB * 1024L && currentChunk.isNotEmpty()) {
            val pageNum = files.size + 1
            val pageHtml = buildString {
                appendLine(header)
                currentChunk.forEach { appendLine(it) }
                appendLine(footer)
            }
            val file = File(reportDir, "alignment_report_${timestamp}_part${pageNum}.html")
            file.writeText(pageHtml)
            files.add(file)
            currentChunk = mutableListOf()
            currentSize = 0L
        }
        currentChunk.add(row)
        currentSize += rowSize
    }
    if (currentChunk.isNotEmpty()) {
        val pageNum = files.size + 1
        val pageHtml = buildString {
            appendLine(header)
            currentChunk.forEach { appendLine(it) }
            appendLine(footer)
        }
        val file = File(reportDir, "alignment_report_${timestamp}_part${pageNum}.html")
        file.writeText(pageHtml)
        files.add(file)
    }
    return files
}

private fun bitmapToBase64(bitmap: Bitmap?, maxWidth: Int): String {
    val bmp = bitmap ?: createPlaceholderBitmap()
    val scaled = if (bmp.width > maxWidth) {
        val scale = maxWidth.toFloat() / bmp.width
        Bitmap.createScaledBitmap(bmp, maxWidth, (bmp.height * scale).toInt(), true)
    } else bmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
    val bytes = out.toByteArray()
    return Base64.encodeToString(bytes, Base64.DEFAULT)
}

private fun createPlaceholderBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.GRAY)
    return bmp
}
