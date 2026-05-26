package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.max

@Composable
fun ExperimentPaddleDynamicScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<MultiPredictorResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        
        Button(
            onClick = {
                isRunning = true
                results = emptyList()
                scope.launch {
                    results = runMultiPredictorTest(context) { statusMessage = it }
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Running..." else "Run Multi-Predictor Test")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { res ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scale: ${res.scale}", style = MaterialTheme.typography.titleMedium)
                        Text("Init Time: ${res.initTimeMs}ms")
                        Text("Inference Time: ${res.inferenceTimeMs}ms")
                        Text("Detections: ${res.detectionCount}")
                    }
                }
            }
        }
    }
}

data class MultiPredictorResult(
    val scale: Int,
    val initTimeMs: Long,
    val inferenceTimeMs: Long,
    val detectionCount: Int
)

private suspend fun runMultiPredictorTest(
    context: android.content.Context, 
    onStatus: (String) -> Unit
): List<MultiPredictorResult> = withContext(Dispatchers.IO) {
    val output = mutableListOf<MultiPredictorResult>()
    
    // 1. Path Resolution
    val arch = android.os.Build.SUPPORTED_ABIS[0]
    val modelPath = File(context.cacheDir, "paddle/det_v4_4000_mono_$arch.nb").absolutePath
    
    if (!File(modelPath).exists()) {
        withContext(Dispatchers.Main) { onStatus("Error: Model not found at $modelPath") }
        return@withContext emptyList()
    }
    
    val dashFile = File(context.filesDir, "experiment_photos/PXL_20220701_020707365.dng")
    if (!dashFile.exists()) {
        withContext(Dispatchers.Main) { onStatus("Error: Test image not found") }
        return@withContext emptyList()
    }

    // 2. Initialization Phase
    val predictors = mutableMapOf<Int, PaddlePredictor>()
    val initTimes = mutableMapOf<Int, Long>()
    val scales = listOf(224, 608, 1024, 2560)
    
    try {
        scales.forEach { scale ->
            withContext(Dispatchers.Main) { onStatus("Initializing predictor for $scale...") }
            val t0 = System.currentTimeMillis()
            
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(com.baidu.paddle.lite.PowerMode.LITE_POWER_HIGH)
            config.setModelFromFile(modelPath)
            
            val predictor = PaddlePredictor.createPaddlePredictor(config)
            predictor.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
            
            initTimes[scale] = System.currentTimeMillis() - t0
            predictors[scale] = predictor
        }

        // 3. Inference Phase
        val buffer = BufferSet(4000, 3072)
        try {
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, dashFile.absolutePath)
            buffer.resize(imgW, imgH)
            ImageIngestionProvider.ingestFromFile(context, dashFile.absolutePath, buffer.p)
            
            scales.forEach { scale ->
                withContext(Dispatchers.Main) { onStatus("Running inference for $scale...") }
                val predictor = predictors[scale] ?: return@forEach
                
                // Scale calculations (preserve aspect ratio)
                val currentLongEdge = max(imgW, imgH)
                val s = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                val targetW = (imgW * s).toInt()
                val targetH = (imgH * s).toInt()
                
                // Setup fixed-size outer container (e.g. 224x224)
                val outerId = buffer.s.createCrop(0, 0, scale, scale)
                val outerSlice = buffer.c[outerId]
                outerSlice.clear() // Zero pad
                
                // Resize into inner target crop
                val innerId = buffer.s.createCrop(0, 0, targetW, targetH)
                val innerSlice = buffer.c[innerId]
                Imgproc.resize(buffer.p.mat, innerSlice.mat, innerSlice.mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)
                
                // Populate Tensor
                val w = outerSlice.width
                val h = outerSlice.height
                val floatData = FloatArray(w * h)
                NativeImageUtils.populateMonoTensor(outerSlice.mat, floatData, w, h, 0.485f, 0.229f)
                
                val tJniIn0 = System.nanoTime()
                predictor.getInput(0).setData(floatData)
                val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

                val tInfer0 = System.nanoTime()
                predictor.run()
                val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0
                
                val outputTensor = predictor.getOutput(0)
                val heatmap = outputTensor.floatData
                val dims = outputTensor.shape()
                
                val blocks = OdometerOcrUtils.processPaddleHeatmap(heatmap, dims[3].toInt(), dims[2].toInt(), 1.0f, outerSlice)
                
                output.add(
                    MultiPredictorResult(
                        scale = scale,
                        initTimeMs = initTimes[scale] ?: 0L,
                        inferenceTimeMs = tInfer.toLong(),
                        detectionCount = blocks.size
                    )
                )
                
                innerSlice.release()
                outerSlice.release()
            }
        } finally {
            buffer.release()
        }
        withContext(Dispatchers.Main) { onStatus("Complete") }
    } catch (e: Exception) {
        Log.e("MultiPredictorTest", "Test failed", e)
        withContext(Dispatchers.Main) { onStatus("Failed: ${e.message}") }
    }
    
    output
}
