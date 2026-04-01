package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Intent
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
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.zip.ZipInputStream

private const val AMAZON_PHOTOS_LINK = "https://www.amazon.com/photos/shared/81xh078qSgydiVwUH9VWBw.EcItxhL_TTM9KNvR0akUC0"
private const val TAG = "ExperimentAlignment"

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
                status = if (success) "✅ ZIP extracted successfully!" else "❌ Failed to extract ZIP"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!experimentDir.exists()) experimentDir.mkdirs()
        val count = experimentDir.listFiles()?.size ?: 0
        status = if (count == 0) "⚠️ Folder is empty.\nUse the buttons below." else "✅ $count photos ready."
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
                    status = "🚀 Starting alignment test..."

                    scope.launch {
                        try {
                            val result = runFullExperiment(vehicles, experimentDir, context) { p, name ->
                                progress = p
                                currentPhoto = name
                            }
                            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                            val htmlFile = File(reportDir, "alignment_report_$timestamp.html")
                            htmlFile.writeText(result.htmlReport)
                            reportPath = htmlFile.absolutePath
                            status = "✅ Test complete!\n${result.summary}"
                            Log.i(TAG, "Report written: ${htmlFile.absolutePath} (${htmlFile.length() / 1024} KB)")
                        } catch (e: Exception) {
                            status = "❌ Report generation failed: ${e.message}"
                            Log.e(TAG, "Report generation failed", e)
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = !isRunning && experimentDir.listFiles()?.isNotEmpty() == true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Running..." else "🚀 Run Alignment Experiment Now")
            }

            if (reportPath != null) {
                Button(onClick = { Toast.makeText(context, "Report: $reportPath", Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) {
                    Text("📄 Open Latest Report")
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
        Log.e("ExperimentAlignment", "ZIP extraction failed", e)
        false
    }
}

private suspend fun runFullExperiment(
    vehicles: List<Vehicle>,
    experimentDir: File,
    context: android.content.Context,
    onProgress: (Float, String) -> Unit
): ExperimentResult {
    // Only process likely dash photos (ignore pump/receipt shots as requested)
    val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") && !it.name.contains("pump", true) && !it.name.contains("receipt", true) } ?: emptyList()
    val total = photos.size
    if (total == 0) return ExperimentResult("No photos found", "<h1>No photos</h1>")

    val results = mutableListOf<PhotoResult>()
    var success = 0

    photos.forEachIndexed { index, file ->
        onProgress((index.toFloat() / total), "Processing ${file.name} (${index+1}/$total)")
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed

        var bestScore = 0f
        var bestVehicleName = "No match"
        var alignedBitmap: Bitmap? = null
        var odometerCropBitmap: Bitmap? = null
        var extractedOdometer: String? = null

        vehicles.filter { it.referenceDashPhotoUrl != null && (it.name.contains("honda", true) || it.name.contains("ford", true)) }.forEach { vehicle ->
            val refFile = File(vehicle.referenceDashPhotoUrl!!)
            if (!refFile.exists()) return@forEach
            val refBmp = BitmapFactory.decodeFile(refFile.absolutePath) ?: return@forEach

            // Lowered minInliers to 12 as requested
            val alignment = ImageAlignmentUtils.alignImages(refBmp, originalBitmap, minInliers = 12)
            if (alignment.success && alignment.confidence > bestScore) {
                bestScore = alignment.confidence
                bestVehicleName = vehicle.name
                alignedBitmap = alignment.alignedImage
            }
        }

        if (alignedBitmap != null) {
            // OCR now runs on the ALIGNED image (fixes broken crop)
            val tempAlignedFile = File(context.cacheDir, "aligned_${file.name}")
            val out = java.io.FileOutputStream(tempAlignedFile)
            alignedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            val ocrResult = OdometerOcrUtils.extractFromPhoto(tempAlignedFile.absolutePath)
            extractedOdometer = ocrResult.odometer
            odometerCropBitmap = ocrResult.croppedBitmap
            tempAlignedFile.delete()
            if (ocrResult.odometer != null) success++
        }

        results.add(PhotoResult(
            photoName = file.name,
            vehicle = bestVehicleName,
            confidence = bestScore,
            originalThumbBase64 = bitmapToBase64(originalBitmap, 120),
            alignedBase64 = bitmapToBase64(alignedBitmap, 120),
            odometerCropBase64 = bitmapToBase64(odometerCropBitmap, 120),
            referenceBase64 = bitmapToBase64(null, 120), // reference + crops kept but optional
            odometer = extractedOdometer
        ))
    }

    onProgress(1f, "Generating visual report...")
    val html = buildRichHtmlReport(results, total)
    val summary = "Processed $total photos — $success successful alignments"
    return ExperimentResult(summary, html)
}

private data class PhotoResult(
    val photoName: String,
    val vehicle: String,
    val confidence: Float,
    val originalThumbBase64: String,
    val alignedBase64: String,
    val odometerCropBase64: String,
    val referenceBase64: String,
    val odometer: String?
)

private data class ExperimentResult(val summary: String, val htmlReport: String)

private fun buildRichHtmlReport(results: List<PhotoResult>, total: Int): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("<html><head><title>Alignment Experiment - $time</title>")
        appendLine("<style>table { border-collapse: collapse; width: 100%; } th, td { border: 1px solid #ccc; padding: 8px; text-align: center; vertical-align: top; } img { max-width: 140px; height: auto; }</style></head><body>")
        appendLine("<h1>Alignment Experiment Report</h1>")
        appendLine("<p><b>Run:</b> $time | <b>Total photos:</b> $total | <b>Images optimized (&lt;300 KB total)</b></p>")
        appendLine("<table>")
        appendLine("<tr><th>Original</th><th>Aligned (Munged)</th><th>Odometer Crop</th><th>Vehicle Reference + Crops</th><th>Matched Vehicle</th><th>Extracted Odometer</th><th>Confidence</th></tr>")

        results.forEach { r ->
            appendLine("<tr>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.originalThumbBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.alignedBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.odometerCropBase64}'></td>")
            appendLine("<td><img src='data:image/jpeg;base64,${r.referenceBase64}'></td>")
            appendLine("<td>${r.vehicle}</td>")
            appendLine("<td>${r.odometer ?: "—"}</td>")
            appendLine("<td>${"%.1f".format(r.confidence * 100)}%</td>")
            appendLine("</tr>")
        }
        appendLine("</table></body></html>")
    }
}

private fun bitmapToBase64(bitmap: Bitmap?, maxWidth: Int): String {
    if (bitmap == null) return ""
    val scaled = if (bitmap.width > maxWidth) {
        val scale = maxWidth.toFloat() / bitmap.width
        Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).toInt(), true)
    } else bitmap

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
    val bytes = out.toByteArray()
    return Base64.encodeToString(bytes, Base64.DEFAULT)
}
