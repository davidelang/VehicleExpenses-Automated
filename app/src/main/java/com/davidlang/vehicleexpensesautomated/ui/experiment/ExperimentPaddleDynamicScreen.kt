package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.graphics.Bitmap
import android.graphics.RectF
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
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.imgproc.Imgproc
import kotlin.math.max

@Composable
fun ExperimentPaddleDynamicScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<DynamicResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                isRunning = true
                scope.launch {
                    results = runDynamicValidation(context)
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Running..." else "Run Dynamic Detect Test (Dash Row 1)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { res ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scale: ${res.scale}", style = MaterialTheme.typography.titleMedium)
                        Text("Tensor Size: ${res.alignedW}x${res.alignedH}")
                        Text("Inference: ${res.inferenceTimeMs}ms")
                        Text("Detections: ${res.detectionCount}")
                    }
                }
            }
        }
    }
}

data class DynamicResult(
    val scale: Int,
    val alignedW: Int,
    val alignedH: Int,
    val inferenceTimeMs: Long,
    val detectionCount: Int
)

private suspend fun runDynamicValidation(context: android.content.Context): List<DynamicResult> = withContext(Dispatchers.IO) {
    val output = mutableListOf<DynamicResult>()
    val testFile = "PXL_20220701_020625793.dng"
    
    // 1. Isolation: Local BufferSet
    val buffer = BufferSet(4000, 3072)
    val paddleEngine = VehicleExpensesApplication.anchoredEngineV3 ?: return@withContext emptyList<DynamicResult>()
    
    try {
        // 2. Load Image into Primary
        val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, testFile)
        buffer.resize(imgW, imgH)
        ImageIngestionProvider.ingestFromFile(context, testFile, buffer.p)
        
        val scales = listOf(224, 608, 1024, 2496)
        
        scales.forEach { scaleLongEdge ->
            // 3. Variable-based Alignment Logic
            val currentLongEdge = max(imgW, imgH)
            val s = if (currentLongEdge <= scaleLongEdge) 1.0f else scaleLongEdge.toFloat() / currentLongEdge
            val targetW = (imgW * s).toInt()
            val targetH = (imgH * s).toInt()
            
            // Round UP to 32
            val alignedW = ((targetW + 31) / 32) * 32
            val alignedH = ((targetH + 31) / 32) * 32
            
            // 4. Manual Letterboxing in Scratch
            val outerId = buffer.s.createCrop(0, 0, alignedW, alignedH)
            val outerSlice = buffer.c[outerId]
            outerSlice.clear() // Zero padding
            
            val innerId = outerSlice.createCrop(0, 0, targetW, targetH)
            val innerSlice = buffer.c[innerId]
            
            // Resize Primary into Inner
            Imgproc.resize(buffer.p.mat, innerSlice.mat, innerSlice.mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)
            
            // 5. Detect on Outer
            val res = paddleEngine.detectMat(outerSlice.mat)
            
            if (res != null) {
                val tInf = res.metadata["t_inference_ms"]?.toDouble()?.toLong() ?: 0L
                val blocks = OdometerOcrUtils.processPaddleHeatmap(res.heatmap, res.width, res.height, 1.0f, outerSlice)
                output.add(DynamicResult(scaleLongEdge, alignedW, alignedH, tInf, blocks.size))
            }
            
            // 6. Explicit Release
            innerSlice.release()
            outerSlice.release()
        }
    } catch (e: Exception) {
        Log.e("DynamicTest", "Test failed", e)
    } finally {
        buffer.release()
    }
    
    output
}
