package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentPumpScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val logs = remember { mutableStateListOf<String>("Ready to start Pump Experiment.") }
    
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
                        logs.clear()
                        runPumpExperiment(context, { progress = it }, { logs.add(it) })
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Running..." else "Run Pump Experiment")
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                itemsIndexed(logs.toList().asReversed()) { _, msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.contains("Error", ignoreCase = true)) ComposeColor.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private suspend fun runPumpExperiment(
    context: Context,
    onProgress: (Float) -> Unit,
    onLog: (String) -> Unit
) = withContext(Dispatchers.IO) {
    onLog("Initializing Pump Experiment...")
    Log.i("PumpExperiment", "Starting Gas Pump Field Extraction Experiment")
    
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val rootDir = context.getExternalFilesDir(null) ?: return@withContext
    val photosDir = File(rootDir, "pump_photos")
    val zipFile = File(rootDir, "pump_photos.zip")
    
    // Unified report directory
    val reportDir = File(context.filesDir, "experiment_reports")
    if (!reportDir.exists()) reportDir.mkdirs()
    
    val reportFile = File(reportDir, "pump_experiment_$timestamp.html")
    val jsonFile = File(reportDir, "pump_results_$timestamp.json")

    if (!photosDir.exists() && zipFile.exists()) {
        onLog("Extracting pump_photos.zip...")
        // Helper to extract zip (Assume exists in OcrUtils or similar)
        // For now, assume directory exists or was extracted manually
    }

    val files = photosDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "dng") }
        ?.sortedBy { it.name } ?: emptyList()

    if (files.isEmpty()) {
        onLog("No pump photos found in ${photosDir.absolutePath}")
        Log.e("PumpExperiment", "Photo directory empty: ${photosDir.absolutePath}")
        return@withContext
    }

    Log.i("PumpExperiment", "Found ${files.size} photos. Initializing reports.")

    // Initialize HTML
    val htmlHeader = """
        <html><head><style>
        body { font-family: sans-serif; background: #121212; color: #e0e0e0; padding: 20px; }
        .row { border-bottom: 1px solid #444; padding: 20px 0; display: flex; gap: 20px; }
        .img-container { flex: 0 0 400px; }
        img { max-width: 100%; border: 1px solid #666; }
        .results { flex: 1; }
        b { color: #bb86fc; }
        </style></head><body>
        <h1>Pump Field Extraction Experiment</h1>
        <p><b>Time:</b> $timestamp | <b>Version:</b> ${BuildConfig.VERSION_NAME} | <b>Total:</b> ${files.size}</p>
    """.trimIndent()
    reportFile.writeText(htmlHeader)

    // Initialize JSON
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"total_photos\": ${files.size},\n  \"results\": [\n")

    val masterBufferSet = NativePaddleEngine.fullBufferSet
    val paddleEngine = NativePaddleEngine(context, variant = "V3", useMono = true)
    
    files.forEachIndexed { index, file ->
        val p = (index + 1).toFloat() / files.size
        onProgress(p)
        onLog("Processing ${index + 1}/${files.size}: ${file.name}")
        Log.i("PumpExperiment", "[${index + 1}/${files.size}] Processing: ${file.name}")

        try {
            Log.d("PumpExperiment", "Probing dimensions...")
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            Log.d("PumpExperiment", "Resizing masterBufferSet to ${imgW}x${imgH}...")
            masterBufferSet.resize(imgW, imgH)
            
            val masterBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            val scratchBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            
            Log.d("PumpExperiment", "Ingesting file...")
            ImageIngestionProvider.ingestFromFile(
                context, 
                file.absolutePath, 
                masterBufferSet, 
                masterBufferSet, // Self-sync A=B
                scratchBmp, 
                masterBmp
            )
            
            Log.d("PumpExperiment", "Starting discovery algorithm...")
            val result = PumpOcrUtils.discoverPumpFields(context, paddleEngine) { logMsg ->
                // Log discovery sub-steps
                Log.v("PumpExperiment", "  Discovery: $logMsg")
            }
            Log.d("PumpExperiment", "Discovery complete. Cost: ${result.cost}, Vol: ${result.gallons}")

            // Capture Snapshot
            Log.d("PumpExperiment", "Capturing snapshot...")
            val snapB64 = OcrUtils.takeSnapshot(
                source = masterBmp,
                targetW = 600,
                targetH = 450,
                scratchArgb = scratchBmp,
                scratchYuv = masterBufferSet
            ).first

            // Incremental HTML Row
            Log.d("PumpExperiment", "Appending to HTML...")
            val rowHtml = StringBuilder()
            rowHtml.append("<div class='row'>")
            rowHtml.append("<div class='img-container'><img src='data:image/jpeg;base64,$snapB64'></div>")
            rowHtml.append("<div class='results'>")
            rowHtml.append("<b>File:</b> ${file.name}<br>")
            rowHtml.append("<b>Execution Time:</b> ${result.executionTimeMs}ms<br>")
            rowHtml.append("<b>Cost:</b> ${result.cost ?: "NOT FOUND"}<br>")
            rowHtml.append("<b>Volume:</b> ${result.gallons ?: "NOT FOUND"}<br>")
            rowHtml.append("<div class='meta'>")
            result.metadata.forEach { (key, value) ->
                rowHtml.append("$key: $value<br>")
            }
            rowHtml.append("</div>")
            rowHtml.append("<br><small>${result.debugText}</small>")
            rowHtml.append("</div></div>")
            reportFile.appendText(rowHtml.toString())

            // Incremental JSON Row
            Log.d("PumpExperiment", "Appending to JSON...")
            val photoJson = serializePumpResultToJson(file, result, imgW, imgH)
            val comma = if (index < files.size - 1) "," else ""
            jsonFile.appendText(photoJson.toString(2) + "$comma\n")

            onLog("  DONE. Reports: ${reportFile.length() / 1024}KB / ${jsonFile.length() / 1024}KB")
            Log.i("PumpExperiment", "  Successfully processed ${file.name}. JSON size: ${jsonFile.length()}")

            masterBmp.recycle()
            scratchBmp.recycle()

        } catch (t: Throwable) {
            onLog("Error processing ${file.name}: ${t.message ?: t.toString()}")
            Log.e("PumpExperiment", "Fatal error on ${file.name}", t)
        }
    }

    reportFile.appendText("</body></html>")
    jsonFile.appendText("\n  ]\n}")
    onLog("Experiment Complete. Reports saved to: ${reportDir.absolutePath}")
    Log.i("PumpExperiment", "Experiment Complete. Final JSON size: ${jsonFile.length()}")
}

private fun serializePumpResultToJson(
    file: File,
    result: OcrResult,
    imgW: Int,
    imgH: Int
): JSONObject {
    val root = JSONObject()
    root.put("file", file.name)
    root.put("execution_time_ms", result.executionTimeMs)
    root.put("cost", result.cost ?: "")
    root.put("volume", result.gallons ?: "")
    root.put("imageWidth", imgW)
    root.put("imageHeight", imgH)
    
    val meta = JSONObject()
    result.metadata.forEach { (k, v) -> meta.put(k, v) }
    root.put("metadata", meta)
    
    root.put("debugText", result.debugText)
    return root
}
