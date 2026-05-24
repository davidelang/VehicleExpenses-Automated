package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentPumpScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var detailLog by remember { mutableStateOf("Ready to start Pump Experiment.") }
    
    // Placeholder for the experiment directory
    val experimentDir = File(context.getExternalFilesDir(null), "pump_experiment")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Gas Pump Extraction Experiment") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        runPumpExperiment(context, { progress = it }, { detailLog = it })
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Running..." else "Run Pump Experiment")
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )

            Text(
                text = detailLog,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private suspend fun runPumpExperiment(
    context: Context,
    onProgress: (Float) -> Unit,
    onLog: (String) -> Unit
) = withContext(Dispatchers.IO) {
    onLog("Initializing Pump Experiment...")
    
    val rootDir = context.getExternalFilesDir(null) ?: return@withContext
    val photosDir = File(rootDir, "pump_photos")
    val zipFile = File(rootDir, "pump_photos.zip")
    val reportFile = File(rootDir, "reports/pump_experiment.html")
    reportFile.parentFile?.mkdirs()

    if (!photosDir.exists() && zipFile.exists()) {
        onLog("Extracting pump_photos.zip...")
        // Helper to extract zip (Assume exists in OcrUtils or similar)
        // For now, assume directory exists or was extracted manually
    }

    val files = photosDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "dng") }
        ?.sortedBy { it.name } ?: emptyList()

    if (files.isEmpty()) {
        onLog("No pump photos found in ${photosDir.absolutePath}")
        return@withContext
    }

    val htmlOutput = StringBuilder()
    htmlOutput.append("<html><head><style>")
    htmlOutput.append("body { font-family: sans-serif; background: #121212; color: #e0e0e0; padding: 20px; }")
    htmlOutput.append(".row { border-bottom: 1px solid #444; padding: 20px 0; display: flex; gap: 20px; }")
    htmlOutput.append(".img-container { flex: 0 0 400px; }")
    htmlOutput.append("img { max-width: 100%; border: 1px solid #666; }")
    htmlOutput.append(".results { flex: 1; }")
    htmlOutput.append("b { color: #bb86fc; }")
    htmlOutput.append("</style></head><body><h1>Pump Field Extraction Experiment</h1>")

    val masterBufferSet = NativePaddleEngine.fullBufferSet
    val paddleEngine = NativePaddleEngine(context, variant = "V3", useMono = true)
    
    files.forEachIndexed { index, file ->
        val p = (index + 1).toFloat() / files.size
        onProgress(p)
        onLog("Processing ${index + 1}/${files.size}: ${file.name}")

        try {
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            masterBufferSet.resize(imgW, imgH)
            
            val masterBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            val scratchBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            
            ImageIngestionProvider.ingestFromFile(context, file.absolutePath, masterBufferSet, scratchBmp, masterBmp)
            
            val result = PumpOcrUtils.discoverPumpFields(context, paddleEngine) {
                // Nested logs if needed
            }

            // Capture Snapshot
            val snapB64 = OcrUtils.takeSnapshot(
                source = masterBmp,
                targetW = 600,
                targetH = 450,
                scratchArgb = scratchBmp,
                scratchYuv = masterBufferSet
            ).first

            htmlOutput.append("<div class='row'>")
            htmlOutput.append("<div class='img-container'><img src='data:image/jpeg;base64,$snapB64'></div>")
            htmlOutput.append("<div class='results'>")
            htmlOutput.append("<b>File:</b> ${file.name}<br>")
            htmlOutput.append("<b>Execution Time:</b> ${result.executionTimeMs}ms<br>")
            htmlOutput.append("<b>Cost:</b> ${result.cost ?: "NOT FOUND"}<br>")
            htmlOutput.append("<b>Volume:</b> ${result.gallons ?: "NOT FOUND"}<br>")
            htmlOutput.append("<div class='meta'>")
            result.metadata.forEach { (key, value) ->
                htmlOutput.append("$key: $value<br>")
            }
            htmlOutput.append("</div>")
            htmlOutput.append("<br><small>${result.debugText}</small>")
            htmlOutput.append("</div></div>")

            masterBmp.recycle()
            scratchBmp.recycle()

        } catch (e: Exception) {
            onLog("Error processing ${file.name}: ${e.message}")
        }
    }

    htmlOutput.append("</body></html>")
    reportFile.writeText(htmlOutput.toString())
    onLog("Experiment Complete. Report saved to: ${reportFile.absolutePath}")
}
