package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory

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

    LaunchedEffect(Unit) {
        if (!experimentDir.exists()) {
            experimentDir.mkdirs()
            status = "✅ Created experiment_photos folder.\n\nAdd test photos from Amazon Photos."
        } else if (experimentDir.listFiles()?.isEmpty() == true) {
            status = "⚠️ Folder is empty.\n\nAdd test photos from Amazon Photos."
        } else {
            status = "✅ ${experimentDir.listFiles()?.size ?: 0} photos ready."
        }
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
            Text(text = status, style = MaterialTheme.typography.bodyLarge)

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = currentPhoto.ifEmpty { "Processing..." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    if (isRunning) return@Button
                    isRunning = true
                    progress = 0f
                    currentPhoto = ""
                    status = "🚀 Starting alignment test..."

                    scope.launch {
                        val result = runFullExperiment(vehicles, experimentDir, context) { p, name ->
                            progress = p
                            currentPhoto = name
                        }
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                        val htmlFile = File(reportDir, "alignment_report_$timestamp.html")
                        htmlFile.writeText(result.htmlReport)

                        reportPath = htmlFile.absolutePath
                        status = "✅ Test complete!\n${result.summary}"
                        isRunning = false
                        Toast.makeText(context, "Report saved: ${htmlFile.name}", Toast.LENGTH_LONG).show()
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

private suspend fun runFullExperiment(
    vehicles: List<Vehicle>,
    experimentDir: File,
    context: android.content.Context,
    onProgress: (Float, String) -> Unit
): ExperimentResult {
    val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") } ?: emptyList()
    val total = photos.size
    if (total == 0) return ExperimentResult("No photos found", "<h1>No photos</h1>")

    val results = mutableListOf<PhotoResult>()
    var success = 0

    photos.forEachIndexed { index, file ->
        onProgress((index.toFloat() / total), "Processing ${file.name} (${index+1}/$total)")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed

        var bestScore = 0f
        var bestVehicleName = "No match"
        var alignedOk = false

        vehicles.filter { it.referenceDashPhotoUrl != null }.forEach { vehicle ->
            val refFile = File(vehicle.referenceDashPhotoUrl!!)
            if (!refFile.exists()) return@forEach
            val refBmp = BitmapFactory.decodeFile(refFile.absolutePath) ?: return@forEach

            val alignment = ImageAlignmentUtils.alignImages(refBmp, bitmap)
            if (alignment.success && alignment.confidence > bestScore) {
                bestScore = alignment.confidence
                bestVehicleName = vehicle.name
                alignedOk = true
            }
        }

        if (alignedOk) success++

        results.add(PhotoResult(file.name, bestVehicleName, bestScore, alignedOk))
    }

    onProgress(1f, "Generating report...")
    val html = buildHtmlReport(results, total, success)
    val summary = "Aligned $success/$total photos (${"%.1f".format(success * 100f / total)}%)"

    return ExperimentResult(summary, html)
}

private data class PhotoResult(val photo: String, val vehicle: String, val confidence: Float, val success: Boolean)
private data class ExperimentResult(val summary: String, val htmlReport: String)

private fun buildHtmlReport(results: List<PhotoResult>, total: Int, success: Int): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("<html><head><title>Alignment Experiment - $time</title></head><body>")
        appendLine("<h1>Alignment Experiment Report</h1>")
        appendLine("<p><b>Run:</b> $time | <b>Photos:</b> $total | <b>Successful:</b> $success (${"%.1f".format(success * 100f / total)}%)</p>")
        appendLine("<table border='1' cellpadding='6'><tr><th>Photo</th><th>Best Vehicle</th><th>Confidence</th><th>Aligned?</th></tr>")
        results.forEach {
            appendLine("<tr><td>${it.photo}</td><td>${it.vehicle}</td><td>${"%.1f".format(it.confidence*100)}%</td><td>${if(it.success)"✅ YES" else "❌ NO"}</td></tr>")
        }
        appendLine("</table></body></html>")
    }
}
