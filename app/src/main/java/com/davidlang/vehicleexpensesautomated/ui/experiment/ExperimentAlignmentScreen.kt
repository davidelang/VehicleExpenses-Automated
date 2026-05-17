package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

import android.graphics.RectF
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.*
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

private const val AMAZON_PHOTOS_LINK = "https://www.amazon.com/photos/shared/81xh078qSgydiVwUH9VWBw.EcItxhL_TTM9KNvR0akUC0"
private const val TAG = "ExperimentAlignment"

@Immutable
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
    var totalPhotos by remember { mutableIntStateOf(0) }
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${minOf(resultsList.size + 1, totalPhotos)} of $totalPhotos",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                    Text(currentPhotoName, style = MaterialTheme.typography.labelSmall)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall) 
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_PHOTOS_LINK)); context.startActivity(intent) }, modifier = Modifier.fillMaxWidth()) { Text("Open Amazon Photos Album") }
        Button(onClick = { zipLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Extract Downloaded ZIP") }
        Button(onClick = { 
            if (vehicles.isEmpty()) { status = "Error: No vehicles in DB."; return@Button }
            scope.launch { 
                totalPhotos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.size ?: 0
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Reports saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
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

data class ReferenceCache(
    val vehicle: Vehicle, 
    val referenceBase64: String, 
    val curatedLandmarks: List<TextBlock>, 
    val bmp: Bitmap,
    val width: Int,
    val height: Int
)

data class RefinementTrace(
    val strategyName: String,
    val timeMs: Long,
    val steps: List<OcrStepResult>
)

data class SingleVehicleResult(
    val vehicleName: String,
    val vetoReason: String,
    val tMatchMs: Long,
    val discoveryTimeMonoMs: Long = 0,
    val alignmentTrace: AlignmentTraceResult?,
    val alignmentTraceMono: AlignmentTraceResult?,
    val refinementTraces: Map<String, RefinementTrace>,
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val isWinner: Boolean,
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap()
)

data class AlignmentTraceResult(
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ProcessedPhotoResult(
    val winnerName: String,
    val bestOdometer: String,
    val bestOdometerDisplay: String,
    val tDeskewTotal: Long,
    val tDiscoveryTotal: Long,
    val deskewedBase64: String,
    val discoveryDebugText: String,
    val discoveryResult: OcrResult,
    val primaryVetoResults: Map<Int, VetoResult>,
    val vehicleResultsMap: Map<Int, SingleVehicleResult>,
    val hardcodedWinner: String?,
    val annotatedCrops: Map<String, String>,
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap()
)

private suspend fun runExperiment(experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, onLog: (String) -> Unit, onProgress: (PhotoResultSummary, Float) -> Unit) = withContext(Dispatchers.IO) {
    val photos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val paddleEngineV2 = NativePaddleEngine(context, variant = "V2")
    val paddleEngineV3 = NativePaddleEngine(context, variant = "V3")
    val paddleEngineV3Mono = NativePaddleEngine(context, variant = "V3", useMono = true)

    val cachedRefs = vehicles.map { v ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, v.referenceDashPhotoUrl!!) ?: BitmapFactory.decodeFile(v.referenceDashPhotoUrl)
        val curated = getFullLandmarksFromJson(v.landmarkTextBlocksJson, "ML Kit", bmp.width, bmp.height)
        val annotatedBmp = drawCropBoxesOnReference(bmp, v); val refBase64 = createScaledBase64(annotatedBmp, 400, 70); annotatedBmp.recycle()
        ReferenceCache(v, refBase64, curated, bmp, bmp.width, bmp.height)
    }
    
    // Phase 115: Vehicle-Specific BufferSet Pools (Zero-Allocation Anchor)
    val vehicleBufferSets = mutableMapOf<Int, BufferSet>()
    val vehicleArgbCrops = mutableMapOf<Int, Bitmap>()
    val vehicleArgbScratches = mutableMapOf<Int, Bitmap>()
    withContext(Dispatchers.Main) {
        cachedRefs.forEach { ref ->
            val l = ref.vehicle.odometerCropLeft
            if (l != null) {
                val srcW = (((ref.vehicle.odometerCropRight ?: 1f) - l) * ref.bmp.width).toInt()
                val srcH = (((ref.vehicle.odometerCropBottom ?: 1f) - (ref.vehicle.odometerCropTop ?: 0f)) * ref.bmp.height).toInt()
                val targetW = if (srcW % 32 == 0) srcW else (srcW / 32 + 1) * 32
                val targetH = if (srcH % 2 == 0) srcH else (srcH / 2 + 1) * 2
                
                if (targetW > 0 && targetH > 0) {
                    vehicleBufferSets[ref.vehicle.id] = BufferSet(targetW, targetH)
                    vehicleArgbCrops[ref.vehicle.id] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    vehicleArgbScratches[ref.vehicle.id] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

                    // Register long-term odometer ROI on the full-res dashboard set
                    NativePaddleEngine.fullBufferSet.createCropNormalizedWithId(
                        ref.vehicle.id,
                        ref.vehicle.odometerCropLeft ?: 0f,
                        ref.vehicle.odometerCropTop ?: 0f,
                        (ref.vehicle.odometerCropRight ?: 1f) - (ref.vehicle.odometerCropLeft ?: 0f),
                        (ref.vehicle.odometerCropBottom ?: 1f) - (ref.vehicle.odometerCropTop ?: 0f)
                    )
                }
            }
        }
    }
    
    val jsonFile = File(reportDir, "alignment_results_$timestamp.json")
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"total_photos\": $total,\n  \"results\": [\n")
    
    var partCount = 1; val maxSizeBytes = 2 * 1024 * 1024; var currentSize = 0
    val footer = "</table></body></html>"
    
    // Phase 115: Global Experiment-Level Buffers (Zero-Allocation Anchor)
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentDetSet512x128 = BufferSet(512, 128)

    // Pre-populate with iterative engines to fix HTML header alignment
    val harnessEngineNames = mutableListOf(
        "ML Kit Mono Diagnostic", 
        "ML Kit Mono Native", 
        "Paddle V3 Valley Mono", 
        "Paddle V3 Valley Mono Native"
    )

    fun startNewFile() = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html").apply { 
        writeText(buildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, emptyList(), harnessEngineNames)) 
    }
    var currentFile = startNewFile()
    
    photos.forEachIndexed { index, file ->
        var currentResult: ProcessedPhotoResult? = null
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"
        try {
            withContext(Dispatchers.Main) { onLog("") }
            val rawBitmap = OdometerOcrUtils.decodeBitmapSafely(context, file.absolutePath) ?: throw Exception("Bitmap decode failed")
            val imgW = rawBitmap.width
            val imgH = rawBitmap.height

            // Phase 115: Safe Dynamic Resizing for Dashboard set
            NativePaddleEngine.fullBufferSet.resize(imgW, imgH)

            // Phase 115: Per-Row Master Buffers (Native Resolution)
            val masterBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            val scratchBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            val masterCanvas = android.graphics.Canvas(masterBmp)
            masterCanvas.drawColor(android.graphics.Color.BLACK)
            masterCanvas.drawBitmap(rawBitmap, 0f, 0f, null)
            
            // Capture ORIGINAL Thumbnail for Report (Before filters/rotation)
            val originalBase64 = createScaledBase64(masterBmp!!, 225, 50, scratchBmp)

            rawBitmap.recycle()

            // Apply global filters to established baseline
            OdometerOcrUtils.applyGrayscaleInPlace(masterBmp)
            // OdometerOcrUtils.applyBilateralInPlace(masterBmp, scratchBmp)
            
            try {
                // Step 2 (Deskew): Draw a scaled version into 2048 buffer and calculate tilt
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(masterBmp)
                
                val tilt = deskewRes.angle
                val tMl = deskewRes.mlTimeMs
                val tPd = deskewRes.paddleTimeMs

                var tRotate = 0L
                if (Math.abs(tilt) > 0.2f) { 
                    val tRot0 = System.currentTimeMillis()
                    // Rotate masterBmp in-place using scratch buffer
                    val scratchCanvas = android.graphics.Canvas(scratchBmp)
                    scratchCanvas.drawColor(android.graphics.Color.BLACK)
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(-tilt, masterBmp.width / 2f, masterBmp.height / 2f)
                    scratchCanvas.drawBitmap(masterBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    
                    masterCanvas.drawBitmap(scratchBmp, 0f, 0f, null)
                    tRotate = System.currentTimeMillis() - tRot0
                }
                
                // Phase 115: Synchronize the unaligned raw masterBmp into the global Dashboard set before discovery/alignment
                com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils.syncMatFromArgb(masterBmp!!, NativePaddleEngine.fullBufferSet.primary.yMat)

                val tDiscoveryStart = System.currentTimeMillis()
                val (queryOcrDiscovery, queryLandmarksRaw) = performLandmarkDiscovery(masterBmp, context)
                val tDiscoveryTotal = System.currentTimeMillis() - tDiscoveryStart
                
                var queryOcrDiscoveryMono: OcrResult? = null
                var tDiscoveryMonoTotal = 0L
                
                val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarksRaw, cachedRefs.map { it.vehicle }, "ML Kit")
                val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()

                // Identification Pass: Find the winning vehicle
                val winnerId = primaryVetoResults.entries.find { !it.value.isVetoed }?.key
                val winnerRef = cachedRefs.find { it.vehicle.id == winnerId }
                
                var alignedBase64 = ""

                // Winner-Only Processing block
                if (winnerRef != null) {
                    finalWinnerName = winnerRef.vehicle.name
                    Log.d("DISAMB_TRACE", "--- Processing Winner: $finalWinnerName for ${file.name} ---")
                    
                    // Phase 115: Actual Native Discovery Pass
                    val tDiscMono0 = System.currentTimeMillis()
                    val (ocrMono, queryLandmarksMonoRaw) = performLandmarkDiscovery(NativePaddleEngine.fullBufferSet.primary, context)
                    queryOcrDiscoveryMono = ocrMono
                    tDiscoveryMonoTotal = System.currentTimeMillis() - tDiscMono0

                    // Phase 108: Independent Disambiguation
                    val queryLandmarksPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksRaw, winnerRef.curatedLandmarks)
                    val queryLandmarksMonoPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksMonoRaw, winnerRef.curatedLandmarks)
                    
                    // 1. Standard Alignment (In-place on masterBmp)
                    val t0 = System.currentTimeMillis()
                    val alignRes = ImageAlignmentUtils.anchorAlign(masterBmp!!, winnerRef.curatedLandmarks, queryLandmarksPrimary, winnerRef.vehicle, winnerRef.width, winnerRef.height, imgW, imgH, scratchBmp)
                    val elapsedAlign = System.currentTimeMillis() - t0

                    // 1.2 Native Alignment (In-place on fullBufferSet)
                    val nativeAlignRes = ImageAlignmentUtils.anchorAlignNative(
                        NativePaddleEngine.fullBufferSet, 
                        winnerRef.curatedLandmarks, 
                        queryLandmarksMonoPrimary, 
                        winnerRef.vehicle, 
                        winnerRef.width, 
                        winnerRef.height, 
                        imgW, 
                        imgH, 
                        scratchBmp
                    )
                    
                    // Capture ALIGNED Thumbnail for Report
                    alignedBase64 = createScaledBase64(masterBmp!!, 600, 50, scratchBmp)

                    // 2. Mono Alignment (Native OpenCV)
                    val alignmentTraceMono = AlignmentTraceResult(nativeAlignRes.success, nativeAlignRes.timeMs, "", nativeAlignRes.metadata)

                    if (alignRes.success) {
                        val alignmentTrace = AlignmentTraceResult(true, elapsedAlign, createScaledBase64(masterBmp!!, 600, 70, scratchBmp), alignRes.metadata)
                        val refinementTraces = mutableMapOf<String, RefinementTrace>()
                        val harnessResultsMap = mutableMapOf<String, OcrHarnessResult>()
                        
                        // Phase 58: Refinement Loop (Only executed on successful alignment)
                        val exactCrop = vehicleArgbCrops[winnerRef.vehicle.id]
                        if (exactCrop != null) {

                            // High-Quality Extraction: Draw from masterBmp into pre-allocated exactCrop
                            val l = winnerRef.vehicle.odometerCropLeft ?: 0f
                            val t = winnerRef.vehicle.odometerCropTop ?: 0f
                            val r = winnerRef.vehicle.odometerCropRight ?: 1f
                            val b = winnerRef.vehicle.odometerCropBottom ?: 1f
                            
                            val srcW = (r - l) * masterBmp!!.width
                            val srcH = (b - t) * masterBmp.height
                            val scaleX = exactCrop.width.toFloat() / srcW
                            val scaleY = exactCrop.height.toFloat() / srcH
                            
                            val cropCanvas = android.graphics.Canvas(exactCrop)
                            cropCanvas.drawColor(android.graphics.Color.BLACK)
                            val matrix = NativePaddleEngine.sharedMatrix
                            matrix.reset()
                            matrix.postTranslate(-l * masterBmp.width, -t * masterBmp.height)
                            matrix.postScale(scaleX, scaleY)
                            cropCanvas.drawBitmap(masterBmp, matrix, NativePaddleEngine.srcPaint)

                            val cropFile = File(debugCropDir, "crop_${file.name.replace(".dng", ".jpg")}")
                            try { cropFile.outputStream().use { out -> exactCrop.compress(Bitmap.CompressFormat.JPEG, 95, out) } } catch (e: Exception) { Log.e(TAG, "Failed to save crop", e) }

                            val report = object : ReportCollector {
                                override fun add(engineName: String, result: OcrHarnessResult) {
                                    harnessResultsMap[engineName] = result
                                    val steps = result.jsonSection.getAsJsonObject("stages")?.entrySet()?.map { (stage, data) ->
                                        val obj = data.asJsonObject
                                        OcrStepResult(stageName = stage, thumbB64 = result.thumbB64 ?: "", text = obj.get("text")?.asString, metadata = mapOf("loop_time" to obj.get("time")?.asString.toString()))
                                    } ?: emptyList()
                                    refinementTraces[engineName] = RefinementTrace(engineName, result.totalTimeMs, steps)
                                    Log.d("OCR_DEBUG", "Harness $engineName returned: ${result.odometerValue}")
                                }
                            }

                            suspend fun runPaddleValleyMonoIterative(displayName: String, masterBuffer: Any, masterW: Int, masterH: Int, report: ReportCollector) {
                                val tHarnessStart = System.currentTimeMillis()
                                val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: throw IllegalStateException("Vehicle bridge not initialized")
                                val argbCrop = vehicleArgbCrops[winnerRef.vehicle.id] ?: throw IllegalStateException("Vehicle ARGB crop not initialized")
                                
                                // Discovery & Recognition Pools (Zero-Allocation)

                                val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
                                val jsonStages = com.google.gson.JsonObject()
                                val allOdo = mutableListOf<String>()

                                val l = winnerRef.vehicle.odometerCropLeft ?: 0f
                                val t = winnerRef.vehicle.odometerCropTop ?: 0f
                                val r = winnerRef.vehicle.odometerCropRight ?: 1f
                                val b = winnerRef.vehicle.odometerCropBottom ?: 1f

                                val roiW = ((r - l) * masterW).toInt().coerceAtMost(masterW)
                                val roiH = ((b - t) * masterH).toInt().coerceAtMost(masterH)
                                val startX = (l * masterW).toInt().coerceIn(0, masterW - 1)
                                val startY = (t * masterH).toInt().coerceIn(0, masterH - 1)

                                val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
                                var lastThumbB64 = ""

                                stages.forEach { stage ->
                                    val tStart = System.currentTimeMillis()

                                    // 4.1 Pristine Refresh (Explicit Type Handling)
                                    when (masterBuffer) {
                                        is Bitmap -> {
                                            val canvas = android.graphics.Canvas(argbCrop)
                                            canvas.drawColor(android.graphics.Color.BLACK)
                                            val matrix = android.graphics.Matrix()
                                            val scaleX = argbCrop.width.toFloat() / roiW.toFloat()
                                            val scaleY = argbCrop.height.toFloat() / roiH.toFloat()
                                            matrix.postTranslate(-startX.toFloat(), -startY.toFloat())
                                            matrix.postScale(scaleX, scaleY)
                                            canvas.drawBitmap(masterBuffer, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                            com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils.syncMatFromArgb(argbCrop, odoBuffer.primary.yMat)
                                        }
                                        is com.davidlang.vehicleexpensesautomated.ui.util.BufferSet -> {
                                            odoBuffer.primary.clear()
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            // Direct query of managed crop (Anti-Pattern safe)
                                            val interp = if (masterBuffer.getCropMat(winnerRef.vehicle.id).cols() > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(masterBuffer.getCropMat(winnerRef.vehicle.id), odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                        }
                                        is org.opencv.core.Mat -> {
                                            odoBuffer.primary.clear()
                                            val sourceRoi = masterBuffer.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                        }
                                        is java.nio.ByteBuffer -> {
                                            odoBuffer.primary.clear()
                                            masterBuffer.rewind()
                                            val nv21Mat = org.opencv.core.Mat(masterH, masterW, org.opencv.core.CvType.CV_8UC1, masterBuffer, 4000L)
                                            val sourceRoi = nv21Mat.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                            nv21Mat.release()
                                        }
                                    }

                                    fun applyStretch(src: org.opencv.core.Mat, dst: org.opencv.core.Mat, threshold: Double) {
                                        val totalPixels = src.cols() * src.rows()
                                        val hist = NativePaddleEngine.histResult
                                        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(src), NativePaddleEngine.histChannels, NativePaddleEngine.histMask, hist, NativePaddleEngine.histSize, NativePaddleEngine.histRanges)
                                        var floorBin = 0; var ceilingBin = 255; var sum = 0.0
                                        for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * threshold) { floorBin = i; break } }
                                        sum = 0.0; for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * 0.98) { ceilingBin = i; break } }
                                        val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0; val beta = -floorBin * alpha
                                        src.convertTo(dst, org.opencv.core.CvType.CV_8U, alpha, beta)
                                    }
                                    
                                    when (stage) {
                                        "80% Stretch Only" -> { 
                                            applyStretch(odoBuffer.primary.yMat, odoBuffer.scratch.yMat, 0.80)
                                            odoBuffer.flip()
                                        }
                                        "78% Stretch" -> {
                                            applyStretch(odoBuffer.primary.yMat, odoBuffer.scratch.yMat, 0.78)
                                            odoBuffer.flip()
                                        }
                                    }

                                    // --- 2-STAGE PADDLE V3 MONO ---
                                    
                                    // 1. Discovery Stage (Scale to 512x128)
                                    experimentDetSet512x128.primary.clear()
                                    val detScale = kotlin.math.min(512f / odoBuffer.primary.yMat.cols().toFloat(), 128f / odoBuffer.primary.yMat.rows().toFloat())
                                    val fitDetW = (odoBuffer.primary.yMat.cols() * detScale).toInt().coerceAtMost(512)
                                    val fitDetH = (odoBuffer.primary.yMat.rows() * detScale).toInt().coerceAtMost(128)
                                    
                                    val detCropId = experimentDetSet512x128.createCrop(0, 0, fitDetW, fitDetH)
                                    org.opencv.imgproc.Imgproc.resize(odoBuffer.primary.yMat, experimentDetSet512x128.getCropMat(detCropId), org.opencv.core.Size(fitDetW.toDouble(), fitDetH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                    experimentDetSet512x128.releaseCrop(detCropId)
                                    
                                    val detThumbB64 = OcrUtils.takeSnapshot(
                                        sourceMat = experimentDetSet512x128.primary.yMat,
                                        rawFragments = emptyList(),
                                        consolidatedRows = emptyList(),
                                        argbScratch = NativePaddleEngine.sharedBmpOdoScratch,
                                        subsetW = 320,
                                        subsetH = 48
                                    )

                                    val det = paddleEngineV3Mono.detectMono(experimentDetSet512x128.primary)
                                    val rawBlocks = if (det != null) OdometerOcrUtils.processPaddleHeatmap(det.heatmap, det.width, det.height, detScale, odoBuffer.primary.yMat, "Paddle") else emptyList()
                                    
                                    // 2. Valley Expansion (Pixel Walking)
                                    fun getLineAverage(mat: org.opencv.core.Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Double {
                                        var sum = 0.0; var count = 0; val maxD = if (horizontal) mat.cols() else mat.rows()
                                        if (fixed < 0 || fixed >= (if (horizontal) mat.rows() else mat.cols())) return 0.0
                                        for (i in start until end) { if (i < 0 || i >= maxD) continue; sum += if (horizontal) mat.get(fixed, i)[0] else mat.get(i, fixed)[0]; count++ }
                                        return if (count > 0) sum / count else 0.0
                                    }
                                    
                                    fun expandByValleyStop(redFloor: android.graphics.Rect, bufferSet: com.davidlang.vehicleexpensesautomated.ui.util.BufferSet): android.graphics.Rect {
                                        val gray = bufferSet.primary.yMat
                                        val maxH = gray.rows(); val maxW = gray.cols()
                                        
                                        // 1. Clamp redFloor to 1px from the edge before submat
                                        val safeL = redFloor.left.coerceIn(0, maxW - 1)
                                        val safeT = redFloor.top.coerceIn(0, maxH - 1)
                                        val safeR = redFloor.right.coerceIn(safeL + 1, maxW)
                                        val safeB = redFloor.bottom.coerceIn(safeT + 1, maxH)
                                        
                                        val sampleId = bufferSet.createCrop(safeL, safeT, safeR - safeL, safeB - safeT)
                                        val hillBrightness = org.opencv.core.Core.mean(bufferSet.getCropMat(sampleId)).`val`[0]
                                        bufferSet.releaseCrop(sampleId)
                                        
                                        val valleyThreshold = hillBrightness * 0.40 
                                        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
                                        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
                                        
                                        val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
                                        val hL = (maxX - minX) * 12.0; val vL = (maxY - minY) * 1.0
                                        val lookAhead = (maxY - minY) * 4.0
                                        
                                        fun isValley(avg: Double): Boolean = avg < 15.0 || avg < valleyThreshold
                                        
                                        // Vertical Expansion (Simple Stop)
                                        while (minY > 0 && (sY - minY) < vL) { if (isValley(getLineAverage(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true))) break; minY -= 1.0 }
                                        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (isValley(getLineAverage(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true))) break; maxY += 1.0 }
                                        
                                        // Horizontal Expansion (Jump and Collapse)
                                        var walkL = minX
                                        var lastGoodL = minX
                                        while (walkL > 0 && (sX - walkL) < hL) {
                                            walkL -= 1.0
                                            if (!isValley(getLineAverage(gray, minY.toInt(), maxY.toInt(), walkL.toInt(), false))) {
                                                minX = walkL; lastGoodL = walkL
                                            } else {
                                                if ((lastGoodL - walkL) > lookAhead) break
                                            }
                                        }

                                        var walkR = maxX
                                        var lastGoodR = maxX
                                        while (walkR < maxW - 1 && (walkR - sXX) < hL) {
                                            walkR += 1.0
                                            if (!isValley(getLineAverage(gray, minY.toInt(), maxY.toInt(), walkR.toInt(), false))) {
                                                maxX = walkR; lastGoodR = walkR
                                            } else {
                                                if ((walkR - lastGoodR) > lookAhead) break
                                            }
                                        }
                                        
                                        return android.graphics.Rect(kotlin.math.max(0, minX.toInt()), kotlin.math.max(0, minY.toInt()), kotlin.math.min(maxW, maxX.toInt()), kotlin.math.min(maxH, maxY.toInt()))
                                    }

                                    val orangeFragments = rawBlocks.map { expandByValleyStop(it.boundingBox, odoBuffer) }
                                    
                                    // 3. Clustering
                                    val clusters = mutableListOf<MutableList<android.graphics.Rect>>()
                                    for (frag in orangeFragments) {
                                        val matchingClusters = mutableListOf<Int>()
                                        for ((idx, cluster) in clusters.withIndex()) {
                                            if (cluster.any { c ->
                                                val overlapTop = kotlin.math.max(frag.top, c.top); val overlapBottom = kotlin.math.min(frag.bottom, c.bottom)
                                                val overlapHeight = overlapBottom - overlapTop
                                                overlapHeight > 0 && overlapHeight >= kotlin.math.min(frag.height(), c.height()) * 0.20
                                            }) matchingClusters.add(idx)
                                        }
                                        if (matchingClusters.isEmpty()) clusters.add(mutableListOf(frag))
                                        else {
                                            val firstIdx = matchingClusters[0]; clusters[firstIdx].add(frag)
                                            for (k in matchingClusters.size - 1 downTo 1) { clusters[firstIdx].addAll(clusters[matchingClusters[k]]); clusters.removeAt(matchingClusters[k]) }
                                        }
                                    }
                                    val consolidatedBoxes = clusters.map { cluster -> 
                                        android.graphics.Rect(cluster.minOf { it.left }, cluster.minOf { it.top }, cluster.maxOf { it.right }, cluster.maxOf { it.bottom }) 
                                    }.sortedBy { it.top }
                                    
                                    // 4. Recognition Stage (4-Pixel Padding)
                                    val odoBuilder = StringBuilder()
                                    val finalBoxes = mutableListOf<android.graphics.Rect>()
                                    consolidatedBoxes.forEach { box ->
                                        // 2. Clamp final box to matrix edges
                                        val safeL = box.left.coerceIn(0, odoBuffer.primary.yMat.cols() - 1)
                                        val safeT = box.top.coerceIn(0, odoBuffer.primary.yMat.rows() - 1)
                                        val safeR = box.right.coerceIn(safeL + 1, odoBuffer.primary.yMat.cols())
                                        val safeB = box.bottom.coerceIn(safeT + 1, odoBuffer.primary.yMat.rows())
                                        
                                        val recSrcId = odoBuffer.createCrop(safeL, safeT, safeR - safeL, safeB - safeT)
                                        
                                        experimentRecSet320x48.primary.clear()
                                        
                                        // Aspect-ratio scaling to fit within 312x40, anchored at (4,4)
                                        val recScale = kotlin.math.min(312f / (safeR - safeL).toFloat(), 40f / (safeB - safeT).toFloat())
                                        val fitRecW = ((safeR - safeL) * recScale).toInt().coerceAtMost(312)
                                        val fitRecH = ((safeB - safeT) * recScale).toInt().coerceAtMost(40)
                                        
                                        val offX = 4; val offY = 4
                                        val recCropId = experimentRecSet320x48.createCrop(offX, offY, fitRecW, fitRecH)
                                        org.opencv.imgproc.Imgproc.resize(odoBuffer.getCropMat(recSrcId), experimentRecSet320x48.getCropMat(recCropId), org.opencv.core.Size(fitRecW.toDouble(), fitRecH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                        odoBuffer.releaseCrop(recSrcId); experimentRecSet320x48.releaseCrop(recCropId)
                                        
                                        val ocrResult = paddleEngineV3Mono.runConstrainedStaticMono(experimentRecSet320x48.primary, paddleEngineV3Mono.getDictionary())
                                        if (ocrResult.text.isNotBlank()) {
                                            odoBuilder.append(ocrResult.text).append(" ")
                                            finalBoxes.add(box)
                                        }
                                    }
                                    val odo = odoBuilder.toString().trim()
                                    allOdo.add(odo)
                                    
                                    // 4.6 Snapshot Scaling (Scale to 320x48 for scratch bitmap)
                                    val aspect = odoBuffer.primary.yMat.cols().toFloat() / odoBuffer.primary.yMat.rows().toFloat()
                                    val fitW: Int; val fitH: Int
                                    if (aspect > (320f / 48f)) {
                                        fitW = 320
                                        fitH = (320f / aspect).toInt().coerceAtLeast(1)
                                    } else {
                                        fitH = 48
                                        fitW = (48f * aspect).toInt().coerceAtLeast(1)
                                    }
                                    
                                    experimentRecSet320x48.primary.clear()
                                    val snapCropId = experimentRecSet320x48.createCrop(0, 0, fitW, fitH)
                                    org.opencv.imgproc.Imgproc.resize(odoBuffer.primary.yMat, experimentRecSet320x48.getCropMat(snapCropId), org.opencv.core.Size(fitW.toDouble(), fitH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                    experimentRecSet320x48.releaseCrop(snapCropId)
                                    
                                    val scaleSnapX = fitW.toFloat() / odoBuffer.primary.yMat.cols().toFloat()
                                    val scaleSnapY = fitH.toFloat() / odoBuffer.primary.yMat.rows().toFloat()
                                    
                                    val scaledRaw = rawBlocks.map { b ->
                                        android.graphics.Rect((b.boundingBox.left * scaleSnapX).toInt(), (b.boundingBox.top * scaleSnapY).toInt(), (b.boundingBox.right * scaleSnapX).toInt(), (b.boundingBox.bottom * scaleSnapY).toInt())
                                    }
                                    val scaledConsolidated = finalBoxes.map { b ->
                                        android.graphics.Rect((b.left * scaleSnapX).toInt(), (b.top * scaleSnapY).toInt(), (b.right * scaleSnapX).toInt(), (b.bottom * scaleSnapY).toInt())
                                    }

                                    lastThumbB64 = OcrUtils.takeSnapshot(
                                        sourceMat = experimentRecSet320x48.primary.yMat,
                                        rawFragments = scaledRaw,
                                        consolidatedRows = scaledConsolidated,
                                        argbScratch = NativePaddleEngine.sharedBmpOdoScratch,
                                        subsetW = fitW,
                                        subsetH = fitH
                                    )

                                    val tLoop = System.currentTimeMillis() - tStart
                                    htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tLoop ms)<br><img src='data:image/jpeg;base64,$lastThumbB64'><br>$odo</div>")
                                    
                                    val stageObj = com.google.gson.JsonObject()
                                    stageObj.addProperty("text", odo)
                                    stageObj.addProperty("time", tLoop)
                                    jsonStages.add(stage, stageObj)
                                }

                                val meta = com.google.gson.JsonObject()
                                meta.addProperty("inputW", masterW); meta.addProperty("inputH", masterH)
                                meta.add("stages", jsonStages)

                                val result = OcrHarnessResult(
                                    htmlHeader = displayName,
                                    htmlCell = htmlOutput.toString(),
                                    jsonSection = meta,
                                    odometerValue = allOdo.groupBy { it }.maxByOrNull { it.value.size }?.key,
                                    thumbB64 = lastThumbB64,
                                    totalTimeMs = System.currentTimeMillis() - tHarnessStart
                                )
                                report.add(displayName, result)
                            }

                            suspend fun runMLKitIterative(displayName: String, masterBuffer: Any, masterW: Int, masterH: Int, report: ReportCollector) {
                                val tHarnessStart = System.currentTimeMillis()
                                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                                val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: throw IllegalStateException("Vehicle bridge not initialized")
                                val argbCrop = vehicleArgbCrops[winnerRef.vehicle.id] ?: throw IllegalStateException("Vehicle ARGB crop not initialized")
                                
                                val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
                                val jsonStages = com.google.gson.JsonObject()
                                val allOdo = mutableListOf<String>()

                                val l = winnerRef.vehicle.odometerCropLeft ?: 0f
                                val t = winnerRef.vehicle.odometerCropTop ?: 0f
                                val r = winnerRef.vehicle.odometerCropRight ?: 1f
                                val b = winnerRef.vehicle.odometerCropBottom ?: 1f

                                val roiW = ((r - l) * masterW).toInt().coerceAtMost(masterW)
                                val roiH = ((b - t) * masterH).toInt().coerceAtMost(masterH)
                                val startX = (l * masterW).toInt().coerceIn(0, masterW - 1)
                                val startY = (t * masterH).toInt().coerceIn(0, masterH - 1)

                                val aspect = roiW.toFloat() / roiH.toFloat()
                                val fitW: Int; val fitH: Int
                                if (aspect > (320f / 48f)) {
                                    fitW = 320
                                    fitH = (320f / aspect).toInt().coerceAtLeast(1)
                                } else {
                                    fitH = 48
                                    fitW = (48f * aspect).toInt().coerceAtLeast(1)
                                }

                                val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
                                var lastThumbB64 = ""

                                stages.forEach { stage ->
                                    val tStart = System.currentTimeMillis()

                                    // 4.1 Pristine Refresh (Explicit Type Handling)
                                    when (masterBuffer) {
                                        is Bitmap -> {
                                            val canvas = android.graphics.Canvas(argbCrop)
                                            canvas.drawColor(android.graphics.Color.BLACK)
                                            val matrix = android.graphics.Matrix()
                                            val scaleX = argbCrop.width.toFloat() / roiW.toFloat()
                                            val scaleY = argbCrop.height.toFloat() / roiH.toFloat()
                                            matrix.postTranslate(-startX.toFloat(), -startY.toFloat())
                                            matrix.postScale(scaleX, scaleY)
                                            canvas.drawBitmap(masterBuffer, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

                                            // Use new zero-buffer JNI fast sync to populate the iterative bridge
                                            com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils.syncMatFromArgb(argbCrop, odoBuffer.primary.yMat)
                                        }
                                        is com.davidlang.vehicleexpensesautomated.ui.util.BufferSet -> {
                                            odoBuffer.primary.clear()
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            // Direct query of managed crop (Anti-Pattern safe)
                                            val interp = if (masterBuffer.getCropMat(winnerRef.vehicle.id).cols() > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(masterBuffer.getCropMat(winnerRef.vehicle.id), odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                        }
                                        is org.opencv.core.Mat -> {
                                            odoBuffer.primary.clear()
                                            val sourceRoi = masterBuffer.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                        }
                                        is java.nio.ByteBuffer -> {
                                            odoBuffer.primary.clear()
                                            masterBuffer.rewind()
                                            // Directly use fixed step for dashboard bridge
                                            val nv21Mat = org.opencv.core.Mat(masterH, masterW, org.opencv.core.CvType.CV_8UC1, masterBuffer, 4000L)
                                            val sourceRoi = nv21Mat.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.primary.yMat.cols()
                                            val bridgeH = odoBuffer.primary.yMat.rows()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.primary.yMat, org.opencv.core.Size(bridgeW.toDouble(), bridgeH.toDouble()), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                            nv21Mat.release()
                                        }
                                    }

                                    fun applyStretch(src: org.opencv.core.Mat, dst: org.opencv.core.Mat, threshold: Double) {
                                        val totalPixels = src.cols() * src.rows()
                                        val hist = NativePaddleEngine.histResult
                                        org.opencv.imgproc.Imgproc.calcHist(
                                            java.util.Collections.singletonList(src), 
                                            NativePaddleEngine.histChannels, 
                                            NativePaddleEngine.histMask, 
                                            hist, 
                                            NativePaddleEngine.histSize, 
                                            NativePaddleEngine.histRanges
                                        )
                                        var floorBin = 0; var ceilingBin = 255; var sum = 0.0
                                        for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * threshold) { floorBin = i; break } }
                                        sum = 0.0; for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * 0.98) { ceilingBin = i; break } }
                                        val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0; val beta = -floorBin * alpha
                                        src.convertTo(dst, org.opencv.core.CvType.CV_8U, alpha, beta)
                                    }
                                    
                                    // 4.2 Preprocessing Application (Native OpenCV Mat Logic)
                                    when (stage) {
                                        "80% Stretch Only" -> {
                                            applyStretch(odoBuffer.primary.yMat, odoBuffer.scratch.yMat, 0.80)
                                            odoBuffer.flip()
                                        }
                                        "78% Stretch" -> {
                                            applyStretch(odoBuffer.primary.yMat, odoBuffer.scratch.yMat, 0.78)
                                            odoBuffer.flip()
                                        }
                                    }

                                    // 4.4 Scale-to-Fit (Recognition)
                                    experimentRecSet320x48.primary.clear()
                                    val mlRecCropId = experimentRecSet320x48.createCrop(0, 0, fitW, fitH)
                                    org.opencv.imgproc.Imgproc.resize(odoBuffer.primary.yMat, experimentRecSet320x48.getCropMat(mlRecCropId), org.opencv.core.Size(fitW.toDouble(), fitH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                    
                                    // 4.4 ML Kit Recognition (Zero-Copy ByteBuffer)
                                    val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                        experimentRecSet320x48.primary.nv21,
                                        320, 48, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                                    )
                                    val visionText = recognizer.process(img).await()
                                    
                                    experimentRecSet320x48.releaseCrop(mlRecCropId)
                                    
                                    // 4.5 Text Sanitization
                                    val odoBuilder = StringBuilder()
                                    val visionBlocks = visionText.textBlocks
                                    for (j in 0 until visionBlocks.size) {
                                        val block = visionBlocks[j]
                                        for (k in 0 until block.lines.size) {
                                            val line = block.lines[k]
                                            val isUpsideDown = Math.abs(line.angle) > 135f
                                            val cleaned = OdometerOcrUtils.clean7SegmentDigits(line.text, isUpsideDown).filter { it.isDigit() }
                                            if (cleaned.isNotBlank()) odoBuilder.append(cleaned)
                                        }
                                    }
                                    val odo = odoBuilder.toString()
                                    allOdo.add(odo)

                                    // 4.6 Diagnostic Snapshot (Mat-Direct Visualization)
                                    val boxes = mutableListOf<android.graphics.Rect>()
                                    for (j in 0 until visionBlocks.size) {
                                        val b = visionBlocks[j].boundingBox
                                        if (b != null) boxes.add(b)
                                    }
                                    lastThumbB64 = OcrUtils.takeSnapshot(
                                        sourceMat = experimentRecSet320x48.primary.yMat,
                                        rawFragments = emptyList(),
                                        consolidatedRows = boxes,
                                        argbScratch = NativePaddleEngine.sharedBmpOdoScratch,
                                        subsetW = fitW,
                                        subsetH = fitH
                                    )

                                    experimentRecSet320x48.primary.clear()

                                    val tLoop = System.currentTimeMillis() - tStart
                                    htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tLoop ms)<br><img src='data:image/jpeg;base64,$lastThumbB64'><br>$odo</div>")
                                    
                                    val stageObj = com.google.gson.JsonObject()
                                    stageObj.addProperty("text", odo)
                                    stageObj.addProperty("time", tLoop)
                                    jsonStages.add(stage, stageObj)
                                }

                                val meta = com.google.gson.JsonObject()
                                meta.addProperty("inputW", masterW); meta.addProperty("inputH", masterH)
                                meta.add("stages", jsonStages)

                                val result = OcrHarnessResult(
                                    htmlHeader = displayName,
                                    htmlCell = htmlOutput.toString(),
                                    jsonSection = com.google.gson.JsonObject().apply { add("stages", jsonStages) },
                                    odometerValue = allOdo.firstOrNull { it.isNotBlank() },
                                    thumbB64 = lastThumbB64,
                                    totalTimeMs = System.currentTimeMillis() - tHarnessStart
                                )
                                report.add(displayName, result)
                            }

                            // --- Sequential Execution ---
                            runMLKitIterative("ML Kit Mono Diagnostic", masterBmp!!, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                            runMLKitIterative("ML Kit Mono Native", NativePaddleEngine.fullBufferSet, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                            runPaddleValleyMonoIterative("Paddle V3 Valley Mono", masterBmp!!, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                            runPaddleValleyMonoIterative("Paddle V3 Valley Mono Native", NativePaddleEngine.fullBufferSet, masterW = masterBmp.width, masterH = masterBmp.height, report = report)

                        }
                        
                        val allResults = refinementTraces.values.flatMap { it.steps }.mapNotNull { it.text }.filter { it.isNotBlank() }
                        if (allResults.isNotEmpty()) bestOdometer = allResults.groupBy { it }.mapValues { it.value.size }.maxByOrNull { it.value }?.key ?: "FAILED"

                        // Reporting Pass: Store result for winner
                        vehicleResultsMap[winnerRef.vehicle.id] = SingleVehicleResult(winnerRef.vehicle.name, "", 0L, tDiscoveryMonoTotal, alignmentTrace, alignmentTraceMono, refinementTraces, emptyList(), emptyList(), emptyList(), true, harnessResultsMap)
                    } else { 
                        Log.d(TAG, "Vehicle identified as ${winnerRef.vehicle.name}, but alignment failed: ${alignRes.message}")
                        vehicleResultsMap[winnerRef.vehicle.id] = SingleVehicleResult(winnerRef.vehicle.name, "", 0L, 0L, AlignmentTraceResult(false, elapsedAlign, "", alignRes.metadata), alignmentTraceMono, emptyMap(), emptyList(), emptyList(), emptyList(), true, emptyMap())
                    }
                }

                // Reporting Pass: Populate status for all other vehicles (Veto results)
                cachedRefs.forEach { ref ->
                    if (ref.vehicle.id != winnerId) {
                        val veto = primaryVetoResults[ref.vehicle.id] ?: VetoResult(false)
                        vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(ref.vehicle.name, veto.reasonWord, 0L, 0L, null, null, emptyMap(), veto.queryWords, veto.myManifest.toList(), veto.vetoPool.toList(), false, emptyMap())
                    }
                }

                val rowHtml = buildHtmlRowDynamic(index + 1, file.name, imgW, imgH, originalBase64, alignedBase64, queryOcrDiscovery.debugText, vehicleResultsMap, cachedRefs, finalWinnerName, emptyList(), harnessEngineNames, (tMl + tPd + tRotate), tDiscoveryTotal, tilt, deskewRes)

                val photoJson = serializePhotoResultToJson(
                    index + 1, file.name, finalWinnerName, bestOdometer, (tMl + tPd), tRotate, tilt, tDiscoveryTotal, 
                    queryOcrDiscovery, queryOcrDiscoveryMono, 
                    primaryVetoResults, vehicleResultsMap, vehicles, emptyList(), deskewRes
                )
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma\n")
                
                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length
                
                val resultSummary = PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer)
                val winnerHarnessResults = winnerRef?.let { vehicleResultsMap[it.vehicle.id]?.harnessResults } ?: emptyMap()
                currentResult = ProcessedPhotoResult(finalWinnerName, bestOdometer, bestOdometer, (tMl + tPd + tRotate), tDiscoveryTotal, originalBase64, queryOcrDiscovery.debugText, queryOcrDiscovery, primaryVetoResults, vehicleResultsMap, null, emptyMap(), winnerHarnessResults)

                // Ensure UI update is dispatched BEFORE we move to cleanup
                withContext(Dispatchers.Main) { 
                    onProgress(resultSummary, (index + 1).toFloat() / total) 
                }
                
                val runtime = Runtime.getRuntime(); val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.i("MEMORY_CHECK", "[Image ${index + 1}/${total}] Used Heap: ${usedMem}MB / ${runtime.maxMemory() / 1024 / 1024}MB")
                
                delay(150)
            } finally {
                masterBmp?.recycle()
                scratchBmp?.recycle()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "FATAL: Experiment failed for row $index (${file.name}):\n" + Log.getStackTraceString(e))
        } finally {
            // DEFENSIVE CLEANUP: Bitmaps are now recycled immediately in the OCR loop.
            currentResult = null // Explicitly nullify to prevent re-access
            System.gc()
        }
    }
    currentFile.appendText(footer)
    jsonFile.appendText("\n  ]\n}")
    cachedRefs.forEach { it.bmp.recycle() }
    
    // Phase 115: Release native handles for vehicle pools
    experimentRecSet320x48.release()
    experimentDetSet512x128.release()
    vehicleBufferSets.values.forEach { it.release() }
    vehicleArgbCrops.values.forEach { it.recycle() }
    vehicleArgbScratches.values.forEach { it.recycle() }
    vehicleArgbCrops.clear()
    vehicleArgbScratches.clear()
}

private fun serializePhotoResultToJson(
    lineNumber: Int, fileName: String, winner: String, odo: String, tDeskew: Long, tRotate: Long, deskewAngle: Float, tDiscovery: Long,
    discovery: OcrResult, discoveryMono: OcrResult?, vetoSweep: Map<Int, VetoResult>, vResults: Map<Int, SingleVehicleResult>,
    vehicles: List<Vehicle>, strategies: List<String>, deskewRes: OdometerOcrUtils.DeskewResult, deskewResMono: OdometerOcrUtils.DeskewResult? = null
): JSONObject {
    return JSONObject().apply {
        put("line_number", lineNumber); put("file", fileName); put("winner", winner); put("ground_truth", "unmapped"); put("odometer", odo)
        
        // Benchmarking: Alignment (Standard vs Mono)
        val winnerVehicle = vehicles.find { it.name == winner }
        val winnerRes = winnerVehicle?.let { vResults[it.id] }
        if (winnerRes?.alignmentTrace != null && winnerRes.alignmentTraceMono != null) {
            put("alignment_time_std_ms", winnerRes.alignmentTrace.timeMs)
            put("alignment_time_mono_ms", winnerRes.alignmentTraceMono.timeMs)
            
            val stdS = winnerRes.alignmentTrace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val stdTx = winnerRes.alignmentTrace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val stdTy = winnerRes.alignmentTrace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            
            val monoS = winnerRes.alignmentTraceMono.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val monoTx = winnerRes.alignmentTraceMono.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val monoTy = winnerRes.alignmentTraceMono.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            
            put("alignment_delta_scale", monoS - stdS)
            put("alignment_delta_tx", monoTx - stdTx)
            put("alignment_delta_ty", monoTy - stdTy)
            put("alignment_consensus_std", winnerRes.alignmentTrace.metadata["Consensus"] ?: "")
            put("alignment_consensus_mono", winnerRes.alignmentTraceMono?.metadata["Consensus"] ?: "")
            put("aligned_image_native_b64", winnerRes.alignmentTraceMono?.alignedImageBase64 ?: "")
            }

        
        put("deskew_time_ms", tDeskew + tRotate); put("deskew_time_rotation_ms", tRotate)
        
        val timingsObj = JSONObject().apply {
            put("paddle", JSONObject().apply { 
                put("standard_ms", deskewRes.paddleTimeMs)
                put("mono_ms", deskewResMono?.paddleTimeMs ?: 0L)
            })
            put("mlkit", JSONObject().apply {
                put("standard_ms", deskewRes.mlTimeMs)
                put("mono_ms", deskewResMono?.mlTimeMs ?: 0L)
            })
        }
        put("deskew_timings", timingsObj)
        
        val anglesObj = JSONObject().apply {
            put("paddle", JSONObject().apply { 
                put("standard", deskewAngle)
                put("mono", deskewResMono?.angle ?: 0f)
            })
            put("mlkit", JSONObject().apply {
                put("standard", deskewRes.mlAngle)
                put("mono", deskewResMono?.mlAngle ?: 0f)
            })
        }
        put("deskew_angles", anglesObj)
        
        put("discovery_time_ms", tDiscovery)
        put("discovery_time_mono_ms", winnerRes?.discoveryTimeMonoMs ?: 0L)
        
        if (winnerRes?.alignmentTrace != null) {
            put("alignment_time_std_ms", winnerRes.alignmentTrace.timeMs)
            put("alignment_time_mono_ms", winnerRes.alignmentTraceMono?.timeMs ?: 0L)
            
            val stdS = winnerRes.alignmentTrace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val stdTx = winnerRes.alignmentTrace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val stdTy = winnerRes.alignmentTrace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            
            val monoS = winnerRes.alignmentTraceMono?.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val monoTx = winnerRes.alignmentTraceMono?.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val monoTy = winnerRes.alignmentTraceMono?.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            
            put("alignment_delta_scale", monoS - stdS)
            put("alignment_delta_tx", monoTx - stdTx)
            put("alignment_delta_ty", monoTy - stdTy)
        }
        
        val mlArray = JSONArray()
        deskewRes.mlBlocks.forEach { block ->
            mlArray.put(JSONObject().apply {
                put("text", block.text)
                put("cx", block.boundingBox.centerX().toDouble() / discovery.imageWidth.toDouble())
                put("cy", block.boundingBox.centerY().toDouble() / discovery.imageHeight.toDouble())
                put("w", block.boundingBox.width().toDouble() / discovery.imageWidth.toDouble())
                put("h", block.boundingBox.height().toDouble() / discovery.imageHeight.toDouble())
                put("angle", block.angle)
            })
        }
        put("deskew_data", mlArray) // Keep primary as 'deskew_data' for backwards compatibility
        put("deskew_data_mlkit", mlArray)

        val pdArray = JSONArray()
        deskewRes.paddleBlocks.forEach { block ->
            pdArray.put(JSONObject().apply {
                put("text", block.text)
                put("cx", block.boundingBox.centerX().toDouble() / discovery.imageWidth.toDouble())
                put("cy", block.boundingBox.centerY().toDouble() / discovery.imageHeight.toDouble())
                put("w", block.boundingBox.width().toDouble() / discovery.imageWidth.toDouble())
                put("h", block.boundingBox.height().toDouble() / discovery.imageHeight.toDouble())
                put("angle", block.angle)
            })
        }
        put("deskew_data_paddle", pdArray)

        val fullImageOcrTimings = JSONObject(); fullImageOcrTimings.put("ML Kit", tDeskew + discovery.executionTimeMs)
        put("has_heatmap", discovery.rawHeatmap != null); put("full_image_ocr_timings", fullImageOcrTimings)
        val vSweepJson = JSONObject(); val safeVehicles = vetoSweep.filter { !it.value.isVetoed }.map { vRes -> vehicles.find { it.id == vRes.key }?.name ?: "Unknown" }
        vSweepJson.put("ML Kit", JSONObject().apply { put("safe_count", safeVehicles.size); put("safe_vehicles", JSONArray(safeVehicles)) }); put("veto_accuracy_sweep", vSweepJson)
        val dResults = JSONObject()
        val landmarksArray = JSONArray()
        discovery.textBlocks.forEach { block -> 
            val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text)
            if (cleanedText.length > 1) {
                landmarksArray.put(JSONObject().apply { 
                    put("text", cleanedText); put("cx", block.boundingBox.centerX().toDouble() / discovery.imageWidth.toDouble()); put("cy", block.boundingBox.centerY().toDouble() / discovery.imageHeight.toDouble())
                    put("w", block.boundingBox.width().toDouble() / discovery.imageWidth.toDouble()); put("h", block.boundingBox.height().toDouble() / discovery.imageHeight.toDouble())
                    put("angle", block.angle)
                    put("instance", block.instanceId)
                })
            }
        }
        dResults.put("ML Kit", landmarksArray)

        if (discoveryMono != null) {
            val monoArray = JSONArray()
            discoveryMono.textBlocks.forEach { block -> 
                val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text)
                if (cleanedText.length > 1) {
                    monoArray.put(JSONObject().apply { 
                        put("text", cleanedText); put("cx", block.boundingBox.centerX().toDouble() / discoveryMono.imageWidth.toDouble()); put("cy", block.boundingBox.centerY().toDouble() / discoveryMono.imageHeight.toDouble())
                        put("w", block.boundingBox.width().toDouble() / discoveryMono.imageWidth.toDouble()); put("h", block.boundingBox.height().toDouble() / discoveryMono.imageHeight.toDouble())
                        put("angle", block.angle)
                        put("instance", block.instanceId)
                    })
                }
            }
            dResults.put("ML Kit Mono", monoArray)
        }
        put("discovery_landmarks", dResults)
        
        val vehicleResults = JSONArray(); vResults.values.forEach { vr -> 
            vehicleResults.put(JSONObject().apply { 
                put("vehicle", vr.vehicleName); put("veto_reason", vr.vetoReason)
                put("veto_query_words", JSONArray(vr.vetoQueryWords))
                put("veto_my_manifest", JSONArray(vr.vetoMyManifest))
                put("veto_pool", JSONArray(vr.vetoPool))
                val refDetails = JSONObject()
                vr.refinementTraces.forEach { (strat, trace) -> 
                    val stratObj = JSONObject(); stratObj.put("time_ms", trace.timeMs)
                    val stepsArr = JSONArray()
                    trace.steps.forEach { step -> 
                        val stepObj = JSONObject()
                        stepObj.put("stage", step.stageName)
                        stepObj.put("text", step.text)
                        step.rawBox?.let { box ->
                            stepObj.put("raw_box", JSONArray().apply { put(box.left); put(box.top); put(box.right); put(box.bottom) })
                        }
                        step.refinedBox?.let { box ->
                            stepObj.put("refined_box", JSONArray().apply { put(box.left); put(box.top); put(box.right); put(box.bottom) })
                        }
                        if (step.metadata.isNotEmpty()) {
                            val metaObj = JSONObject()
                        step.metadata.forEach { (k, v) ->
                            if (!k.startsWith("forensic_")) {
                                metaObj.put(k, v)
                            }
                        }
                        stepObj.put("metadata", metaObj)
                        }
                        stepsArr.put(stepObj)
                    }
                    stratObj.put("steps", stepsArr); refDetails.put(strat, stratObj)
                }
                put("refinement_details", refDetails)

                val harnessObj = JSONObject()
                vr.harnessResults.forEach { (engine, res) ->
                    val engineObj = JSONObject()
                    engineObj.put("odometer", res.odometerValue)
                    engineObj.put("time_ms", res.totalTimeMs)
                    // Convert GSON JsonObject to org.json.JSONObject via string
                    engineObj.put("metadata", JSONObject(res.jsonSection.toString()))
                    harnessObj.put(engine, engineObj)
                }
                put("harness_diagnostics", harnessObj)
            }) 
        }; put("vehicles", vehicleResults)
    }
}

private fun buildHtmlHeader(time: String, total: Int, version: String, strategies: List<String>, harnessEngines: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }</style></head><body>")
    appendLine("<h1>OCR Refinement Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th><th style='width:650px;'>Aligned & Stats</th>")
    harnessEngines.forEach { appendLine("<th style='width:300px;'>$it</th>") }
    strategies.forEach { appendLine("<th style='width:300px;'>$it</th>") }
    appendLine("<th style='width:300px;'>Refinement Consensus</th></tr>")
}

private fun buildHtmlRowDynamic(
    rowIndex: Int, 
    fileName: String, 
    imgW: Int,
    imgH: Int,
    originalBase64: String, 
    alignedBase64: String,
    discovery: String, 
    vehicleResults: Map<Int, SingleVehicleResult>, 
    cachedRefs: List<ReferenceCache>, 
    winnerName: String, 
    strategies: List<String>, 
    harnessEngines: List<String>,
    tDeskew: Long, 
    tDiscovery: Long,
    tilt: Float,
    deskewRes: OdometerOcrUtils.DeskewResult
): String = buildString {
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>Res: ${imgW}x${imgH}</small><br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms<br><img src='data:image/jpeg;base64,$originalBase64'></td>")
    
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    // Aligned Column
    appendLine("<td>")
    if (alignedBase64.isNotEmpty()) {
        appendLine("<img src='data:image/jpeg;base64,$alignedBase64'><br>")
        vRes?.alignmentTrace?.let { trace ->
            val s = trace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val tx = trace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val ty = trace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            val enginesHtml = deskewRes.engines.entries.joinToString(" | ") { "${it.key}: %.3f° (%sms)".format(it.value.angle, it.value.timesMs.joinToString(",")) }
            appendLine("<small>Applied: %.3f°<br>$enginesHtml<br>Scale: %.3f<br>TX: %.1f, TY: %.1f</small>".format(tilt, s, tx, ty))
        }
    } else {
        appendLine("<i>Not Aligned</i>")
    }
    appendLine("</td>")
    
    val allReadings = mutableListOf<String>()
    harnessEngines.forEach { engine ->
        appendLine("<td>")
        val hRes = vRes?.harnessResults?.get(engine)
        if (hRes != null) {
            appendLine("<b>Time:</b> ${hRes.totalTimeMs}ms<br>")
            appendLine(hRes.htmlCell)
        } else {
            appendLine("<i>No harness data</i>")
        }
        appendLine("</td>")
    }

    strategies.forEach { strat ->
        appendLine("<td>")
        if (vRes != null) {
            val trace = vRes.refinementTraces[strat]
            if (trace != null) {
                appendLine("<b>Time:</b> ${trace.timeMs}ms<br>")
                trace.steps.forEach { step -> 
                    if (step.text?.isNotBlank() == true) allReadings.add(step.text)
                    appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br><img src='data:image/jpeg;base64,${step.thumbB64}'>")
                    val rawText = step.metadata["raw_text"]
                    if (rawText != null) {
                        appendLine("<br><small>Raw: $rawText</small>")
                    }

                    appendLine("${step.text ?: "---"}</div>") 
                }
            } else appendLine("<i>No refinement data</i>")
        } else appendLine("<i>No refinement data</i>")
        appendLine("</td>")
    }
    
    appendLine("<td><b>Winner:</b> $winnerName<br><br><b>Consensus:</b><br>")
    val freq = allReadings.groupBy { it }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
    freq.forEach { (text, count) -> appendLine("<b>$text</b> ($count/48)<br>") }
    appendLine("</td></tr>")
}

private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val outputStream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream); return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int, targetBuffer: Bitmap? = null): String {
    if (bitmap.isRecycled) return ""
    
    val scale = targetWidth.toFloat() / bitmap.width
    val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    
    // Phase 115: Scaled thumbnail via target buffer (zero-allocation if buffer provided)
    val target = targetBuffer ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val targetCanvas = android.graphics.Canvas(target)
    
    // Clear target if reused
    if (targetBuffer != null) targetCanvas.drawColor(android.graphics.Color.BLACK)
    
    val matrix = android.graphics.Matrix()
    matrix.postScale(scale, scale)
    targetCanvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    
    // CRITICAL: Create a view of only the scaled image area to preserve aspect ratio in HTML
    val view = Bitmap.createBitmap(target, 0, 0, targetWidth, targetHeight)
    val b64 = bitmapToBase64(view, quality)
    
    // Only recycle if we allocated it here
    if (targetBuffer == null) target.recycle()
    
    return b64
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true); val canvas = android.graphics.Canvas(annotated); val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f; color = android.graphics.Color.RED }
    vehicle.odometerCropLeft?.let { l -> canvas.drawRect(l * bmp.width, (vehicle.odometerCropTop ?: 0f) * bmp.height, (vehicle.odometerCropRight ?: 1f) * bmp.width, (vehicle.odometerCropBottom ?: 1f) * bmp.height, paint) }
    return annotated
}

private fun getFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else { Log.e("ExperimentAlignment", "Manifest missing data for engine: $engineName"); return emptyList() }
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0)
                val instanceId = if (obj.has("instance")) obj.getInt("instance") else -1
                val cleanText = OdometerOcrUtils.cleanLandmarkString(text); val left = ((cx - w/2.0) * imgW).toInt(); val top = ((cy - h/2.0) * imgH).toInt(); val right = ((cx + w/2.0) * imgW).toInt(); val bottom = ((cy + h/2.0) * imgH).toInt()
                list.add(TextBlock(cleanText, android.graphics.Rect(left, top, right, bottom), instanceId = instanceId))
            } catch (e: Exception) { 
                Log.w("ExperimentAlignment", "Skipping malformed landmark entry in JSON: ${e.message}")
            }
        }
    } catch (e: Exception) { Log.e("ExperimentAlignment", "Failed to parse landmarks", e) }
    return list
}

private suspend fun extractZipToPhotos(uri: Uri, targetDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        if (targetDir.exists()) targetDir.deleteRecursively(); targetDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry; while (entry != null) {
                    val file = File(targetDir, entry.name); if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }; true
    } catch (e: Exception) { false }
}

private suspend fun performLandmarkDiscovery(
    input: Any,
    context: Context
): Pair<OcrResult, List<TextBlock>> {
    val queryOcrDiscovery = OcrHarness.runDiscovery(input, context)
    val landmarks = OdometerOcrUtils.processRawLandmarks(
        queryOcrDiscovery.textBlocks, 
        null, 
        null, 
        queryOcrDiscovery.imageWidth, 
        queryOcrDiscovery.imageHeight
    )
    return Pair(queryOcrDiscovery, landmarks)
}
