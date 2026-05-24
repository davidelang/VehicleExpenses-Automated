package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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

private val GOLDEN_SUBSET = listOf(
    "PXL_20220701_020707365.dng",
    "PXL_20220821_051055938.dng",
    "PXL_20221029_002946498.dng",
    "PXL_20221020_215546513.dng",
    "PXL_20221221_205939873.dng",
    "PXL_20221228_164725812.dng",
    "PXL_20221222_211445685.dng",
    "PXL_20230113_231330881.dng",
    "PXL_20221121_021330418.jpg",
    "PXL_20221126_210323823.jpg",
    "PXL_20221128_172727575.jpg"
)

// Phase 115: Row-Level Global Scratch Buffers

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
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                totalPhotos = allFiles.size
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }, null) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Reports saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Test") }
        Button(onClick = { 
            if (vehicles.isEmpty()) { status = "Error: No vehicles in DB."; return@Button }
            scope.launch { 
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                val subset = allFiles.filter { it.name in GOLDEN_SUBSET }
                totalPhotos = subset.size
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }, GOLDEN_SUBSET) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Limited Report saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Limited Experiment (Golden Subset)") }
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

data class SingleVehiclePathwayResult(
    val alignmentTrace: AlignmentTraceResult?,
    val refinementTraces: Map<String, RefinementTrace>,
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap()
)

data class SingleVehicleResult(
    val vehicleName: String,
    val vetoReason: String,
    val tMatchMs: Long,
    val discoveryTimeMonoMs: Long = 0,
    val pathResults: Map<String, SingleVehiclePathwayResult>, // Keys: "set_a", "set_b", "standard"
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val isWinner: Boolean
)

data class AlignmentTraceResult(
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
    val metadata: Map<String, String> = emptyMap()
)

data class PhotoPathwayResult(
    val winnerName: String,
    val bestOdometer: String,
    val tDeskewTotal: Long,
    val tDiscoveryTotal: Long,
    val deskewedBase64: String,
    val discoveryResult: OcrResult,
    val discoveryLandmarks: List<TextBlock>,
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap()
)

data class ProcessedPhotoResult(
    val fileName: String,
    val pathways: Map<String, PhotoPathwayResult>, // Keys: "set_a", "set_b", "standard"
    val vehicleResultsMap: Map<Int, SingleVehicleResult>,
    val primaryVetoResults: Map<Int, VetoResult>
)

private suspend fun runExperiment(
    experimentDir: File, reportDir: File, debugCropDir: File, vehicles: List<Vehicle>, context: Context, 
    onLog: (String) -> Unit, subsetNames: List<String>?, onProgress: (PhotoResultSummary, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val allPhotos = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") }?.sortedBy { it.name } ?: return@withContext
    val photos = if (subsetNames != null) allPhotos.filter { it.name in subsetNames } else allPhotos
    
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
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

                    // Register long-term odometer ROI on the full-res dashboard sets (Phase 116)
                    listOf(NativePaddleEngine.bufferSetA, NativePaddleEngine.bufferSetB).forEach { set ->
                        if (ref.vehicle.isIcrs) {
                            set.p.createCrop(
                                ref.vehicle.odometerCropLeft ?: 0f,
                                ref.vehicle.odometerCropTop ?: 0f,
                                (ref.vehicle.odometerCropRight ?: 0f) - (ref.vehicle.odometerCropLeft ?: 0f),
                                (ref.vehicle.odometerCropBottom ?: 0f) - (ref.vehicle.odometerCropTop ?: 0f),
                                id = ref.vehicle.id
                            )
                        } else {
                            set.p.createCropLegacy(
                                ref.vehicle.odometerCropLeft ?: 0f,
                                ref.vehicle.odometerCropTop ?: 0f,
                                (ref.vehicle.odometerCropRight ?: 1f) - (ref.vehicle.odometerCropLeft ?: 0f),
                                (ref.vehicle.odometerCropBottom ?: 1f) - (ref.vehicle.odometerCropTop ?: 0f),
                                id = ref.vehicle.id
                            )
                        }
                    }
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
        "ML Diag", 
        "ML Native", 
        "Paddle Std", 
        "Set A",
        "Set B"
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
            
            // Phase 115: Probe Natural Resolution to bypass thumbnails
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)

            // Phase 115: Safe Dynamic Resizing for Dashboard sets (Phase 116)
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)

            // Phase 115: Per-Row Master Buffers (Native Resolution)
            val masterBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            val scratchBmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            
            // Ingest at full fidelity
            val meta = ImageIngestionProvider.ingestFromFile(
                context, 
                file.absolutePath, 
                NativePaddleEngine.bufferSetA, 
                NativePaddleEngine.bufferSetB, 
                scratchBmp, 
                masterBmp
            )
            
            // Capture ORIGINAL Thumbnail for Report (Before filters/rotation)
            val tSnapOrig0 = System.currentTimeMillis()
            val originalBase64 = createScaledBase64(masterBmp!!, 225, 50, null)
            val tSnapOrig = System.currentTimeMillis() - tSnapOrig0

            try {
                // Step 2 (Deskew): Calculate tilt independently for both pipelines
                val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(masterBmp)
                val deskewResMono = OdometerOcrUtils.calculateAverageTextAngle(NativePaddleEngine.bufferSetA.p)

                val tilt = deskewRes.angle
                val tMl = deskewRes.mlTimeMs
                val tPd = deskewRes.paddleTimeMs

                var tRotate = 0L
                if (Math.abs(tilt) > 0.2f) { 
                    val tRot0 = System.currentTimeMillis()
                    // ARGB: Rotate masterBmp in-place using scratch buffer
                    val scratchCanvas = android.graphics.Canvas(scratchBmp)
                    scratchCanvas.drawColor(android.graphics.Color.BLACK)
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(-tilt, masterBmp.width / 2f, masterBmp.height / 2f)
                    scratchCanvas.drawBitmap(masterBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    android.graphics.Canvas(masterBmp).drawBitmap(scratchBmp, 0f, 0f, null)
                    tRotate = System.currentTimeMillis() - tRot0
                }
                
                // Phase 116: Independent High-Quality Rotation (Cubic)
                suspend fun rotateNative(set: BufferSet, angle: Float): Long = withContext(Dispatchers.IO) {
                    val tRot0 = System.currentTimeMillis()
                    val src = set.p.mat
                    val dst = set.s.mat
                    
                    val m = android.graphics.Matrix()
                    m.postRotate(-angle, src.cols() / 2f, src.rows() / 2f)
                    val values = FloatArray(9)
                    m.getValues(values)

                    val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
                    rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
                    rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())

                    org.opencv.imgproc.Imgproc.warpAffine(src, dst, rotMat, src.size(), org.opencv.imgproc.Imgproc.INTER_CUBIC, org.opencv.core.Core.BORDER_CONSTANT, org.opencv.core.Scalar(0.0))
                    set.flip()
                    rotMat.release()
                    System.currentTimeMillis() - tRot0
                }

                // Path A: ML Kit Deskew
                val angleA = deskewResMono.engines["ML Kit"]?.angle ?: 0f
                val tRotateA = rotateNative(NativePaddleEngine.bufferSetA, angleA)
                
                // Path B: Paddle Deskew
                val angleB = deskewResMono.engines["Paddle V3"]?.angle ?: 0f
                val tRotateB = rotateNative(NativePaddleEngine.bufferSetB, angleB)

                val tDiscoveryStart = System.currentTimeMillis()
                val (queryOcrDiscovery, queryLandmarksRaw) = performLandmarkDiscovery(masterBmp, context)
                val tDiscoveryTotal = System.currentTimeMillis() - tDiscoveryStart
                
                // Path A Discovery
                val tDisc0A = System.currentTimeMillis()
                val (ocrA, queryLandmarksA) = performLandmarkDiscovery(NativePaddleEngine.bufferSetA.p, context)
                val tDiscoveryTotalA = System.currentTimeMillis() - tDisc0A
                
                // Path B Discovery
                val tDisc0B = System.currentTimeMillis()
                val (ocrB, queryLandmarksB) = performLandmarkDiscovery(NativePaddleEngine.bufferSetB.p, context)
                val tDiscoveryTotalB = System.currentTimeMillis() - tDisc0B
                
                val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarksRaw, cachedRefs.map { it.vehicle }, "ML Kit")
                // Phase 116: We primarily use the Standard path for identification (Winner selection)
                // but discovery is run independently for refinement downstream.
                val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
                val harnessResultsMap = mutableMapOf<String, OcrHarnessResult>()

                // Identification Pass: Find the winning vehicle
                val winnerId = primaryVetoResults.entries.find { !it.value.isVetoed }?.key
                val winnerRef = cachedRefs.find { it.vehicle.id == winnerId }
                
                var alignedBase64 = ""
                var alignedNativeBase64 = ""
                var alignedAB64 = ""
                var alignedBB64 = ""
                var tSnapAlign = 0L

                // Winner-Only Processing block
                
                if (winnerRef != null) {
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
                                var tSnapTotal = 0L

                                stages.forEach { stage ->
                                    val tStart = System.currentTimeMillis()

                                    // 4.1 Pristine Refresh (Explicit Type Handling)
                                    when (masterBuffer) {
                                        is Bitmap -> {
                                            val canvas = android.graphics.Canvas(argbCrop)
                                            canvas.drawColor(android.graphics.Color.BLACK)
                                            val matrix = android.graphics.Matrix()
                                            val ocrBitmapScaleX = argbCrop.width.toFloat() / roiW.toFloat()
                                            val ocrBitmapScaleY = argbCrop.height.toFloat() / roiH.toFloat()
                                            matrix.postTranslate(-startX.toFloat(), -startY.toFloat())
                                            matrix.postScale(ocrBitmapScaleX, ocrBitmapScaleY)
                                            canvas.drawBitmap(masterBuffer, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                            com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils.syncMatFromArgb(argbCrop, odoBuffer.p.mat)
                                        }
                                        is BufferSet -> {
                                            odoBuffer.p.clear()
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
                                        }
                                        is org.opencv.core.Mat -> {
                                            odoBuffer.p.clear()
                                            val sourceRoi = masterBuffer.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                        }
                                        is java.nio.ByteBuffer -> {
                                            odoBuffer.p.clear()
                                            masterBuffer.rewind()
                                            val nv21Mat = org.opencv.core.Mat(masterH, masterW, org.opencv.core.CvType.CV_8UC1, masterBuffer, 4000L)
                                            val sourceRoi = nv21Mat.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
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
                                            applyStretch(odoBuffer.p.mat, odoBuffer.s.mat, 0.80)
                                            odoBuffer.flip()
                                        }
                                        "78% Stretch" -> {
                                            applyStretch(odoBuffer.p.mat, odoBuffer.s.mat, 0.78)
                                            odoBuffer.flip()
                                        }
                                    }

                                    // --- 2-STAGE PADDLE V3 MONO ---
                                    
                                    // 1. Discovery Stage (Scale to 512x128)
                                    experimentDetSet512x128.p.clear()
                                    val detScale = kotlin.math.min(512f / odoBuffer.p.mat.cols().toFloat(), 128f / odoBuffer.p.mat.rows().toFloat())
                                    val fitDetW = (odoBuffer.p.mat.cols() * detScale).toInt().coerceAtMost(512)
                                    val fitDetH = (odoBuffer.p.mat.rows() * detScale).toInt().coerceAtMost(128)

                                    val toEvenDet = { v: Int -> ((v + 1) / 2) * 2 }
                                    val evenDetW = toEvenDet(fitDetW)
                                    val evenDetH = toEvenDet(fitDetH)

                                    val detCropId = experimentDetSet512x128.createCrop(0, 0, evenDetW, evenDetH)
                                    val detTargetMat = experimentDetSet512x128.c[detCropId].mat
                                    org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, detTargetMat, org.opencv.core.Size(evenDetW.toDouble(), evenDetH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                    experimentDetSet512x128.c[detCropId].release()

                                    val det = paddleEngineV3Mono.detectMono(experimentDetSet512x128.p)
                                    val rawBlocks = if (det != null) OdometerOcrUtils.processPaddleHeatmap(det.heatmap, det.width, det.height, detScale, odoBuffer.p.mat, "Paddle") else emptyList()
                                    
                                    // 2. Native Valley Expansion
                                    val orangeFragments = rawBlocks.map { NativeImageUtils.expandByValley(odoBuffer.p.mat, it.boundingBox) }
                                    
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
                                    val jsonStageMeta = com.google.gson.JsonObject()
                                    
                                    consolidatedBoxes.forEach { box ->
                                        // 2. Clamp final box to matrix edges
                                        val safeL = box.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
                                        val safeT = box.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
                                        val safeR = box.right.coerceIn(safeL + 1, odoBuffer.p.mat.cols())
                                        val safeB = box.bottom.coerceIn(safeT + 1, odoBuffer.p.mat.rows())
                                        
                                        val recSrcId = odoBuffer.createCrop(safeL, safeT, safeR - safeL, safeB - safeT)
                                        
                                        experimentRecSet320x48.p.clear()
                                        
                                        // Aspect-ratio scaling to fit within 312x40, anchored at (4,4)
                                        val recScale = kotlin.math.min(312f / (safeR - safeL).toFloat(), 40f / (safeB - safeT).toFloat())
                                        val fitRecW = ((safeR - safeL) * recScale).toInt().coerceAtMost(312)
                                        val fitRecH = ((safeB - safeT) * recScale).toInt().coerceAtMost(40)
                                        
                                        val offX = 4; val offY = 4
                                        val toEvenRec = { v: Int -> ((v + 1) / 2) * 2 }
                                        val evenRecW = toEvenRec(fitRecW)
                                        val evenRecH = toEvenRec(fitRecH)
                                        
                                        val recCropId = experimentRecSet320x48.createCrop(offX, offY, evenRecW, evenRecH)
                                        val recTargetMat = experimentRecSet320x48.c[recCropId].mat
                                        org.opencv.imgproc.Imgproc.resize(odoBuffer.c[recSrcId].mat, recTargetMat, org.opencv.core.Size(evenRecW.toDouble(), evenRecH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                        odoBuffer.c[recSrcId].release(); experimentRecSet320x48.c[recCropId].release()

                                        val ocrResult = paddleEngineV3Mono.runConstrainedStaticMono(experimentRecSet320x48.p, paddleEngineV3Mono.getDictionary())
                                        if (ocrResult.text.isNotBlank()) {
                                            odoBuilder.append(ocrResult.text).append(" ")
                                            finalBoxes.add(box)
                                        }
                                        // Capture micro-instrumentation for the last block of the stage
                                        ocrResult.metadata.forEach { (k, v) -> jsonStageMeta.addProperty(k, v) }
                                    }
                                    val odo = odoBuilder.toString().trim()
                                    allOdo.add(odo)

                                    val tLoop = System.currentTimeMillis() - tStart
                                    
                                    // 4.6 Recognition Snapshot (High-Res Visualization) - OUTSIDE TIMED LOOP
                                    val annotations = mutableListOf<SnapshotAnnotation>()
                                    val annPaddleScaleX = 1.0f
                                    val annPaddleScaleY = 1.0f
                                    rawBlocks.forEach { b -> 
                                        annotations.add(SnapshotAnnotation((b.boundingBox.left * annPaddleScaleX).toInt(), (b.boundingBox.top * annPaddleScaleY).toInt(), (b.boundingBox.right * annPaddleScaleX).toInt(), (b.boundingBox.bottom * annPaddleScaleY).toInt(), Shape.RECTANGLE, Color.RED, 2))
                                    }
                                    finalBoxes.forEach { b ->
                                        annotations.add(SnapshotAnnotation((b.left * annPaddleScaleX).toInt(), (b.top * annPaddleScaleY).toInt(), (b.right * annPaddleScaleX).toInt(), (b.bottom * annPaddleScaleY).toInt(), Shape.RECTANGLE, Color.rgb(255, 165, 0), 2))
                                    }

                                    // Source from high-res odoBuffer.p directly
                                    val (snapB64, tSnap) = OcrUtils.takeSnapshot(
                                        source = odoBuffer.p,
                                        sourceRect = null,
                                        targetW = 320,
                                        targetH = 48,
                                        annotations = annotations,
                                        scratchArgb = scratchBmp,
                                        scratchYuv = NativePaddleEngine.fullBufferSet
                                    )
                                    lastThumbB64 = snapB64; tSnapTotal += tSnap

                                    htmlOutput.append("<div class='ocr-step'><b>Recognition ($stage):</b> ($tLoop ms)<br><img src='data:image/jpeg;base64,$lastThumbB64'><br>$odo</div>")
                                    
                                    val stageObj = com.google.gson.JsonObject()
                                    stageObj.addProperty("text", odo)
                                    stageObj.addProperty("time", tLoop)
                                    jsonStageMeta.entrySet().forEach { entry -> stageObj.add(entry.key, entry.value) }
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
                                    totalTimeMs = System.currentTimeMillis() - tHarnessStart,
                                    tSnapshotMs = tSnapTotal
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
                                var tSnapTotal = 0L

                                stages.forEach { stage ->
                                    val tStart = System.currentTimeMillis()

                                    // 4.1 Pristine Refresh (Explicit Type Handling)
                                    when (masterBuffer) {
                                        is Bitmap -> {
                                            val canvas = android.graphics.Canvas(argbCrop)
                                            canvas.drawColor(android.graphics.Color.BLACK)
                                            val matrix = android.graphics.Matrix()
                                            val ocrBitmapScaleX = argbCrop.width.toFloat() / roiW.toFloat()
                                            val ocrBitmapScaleY = argbCrop.height.toFloat() / roiH.toFloat()
                                            matrix.postTranslate(-startX.toFloat(), -startY.toFloat())
                                            matrix.postScale(ocrBitmapScaleX, ocrBitmapScaleY)
                                            canvas.drawBitmap(masterBuffer, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

                                            // Use new zero-buffer JNI fast sync to populate the iterative bridge
                                            com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils.syncMatFromArgb(argbCrop, odoBuffer.p.mat)
                                        }
                                        is BufferSet -> {
                                            odoBuffer.p.clear()
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
                                        }
                                        is org.opencv.core.Mat -> {
                                            odoBuffer.p.clear()
                                            val sourceRoi = masterBuffer.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
                                            sourceRoi.release()
                                        }
                                        is java.nio.ByteBuffer -> {
                                            odoBuffer.p.clear()
                                            masterBuffer.rewind()
                                            // Directly use fixed step for dashboard bridge
                                            val nv21Mat = org.opencv.core.Mat(masterH, masterW, org.opencv.core.CvType.CV_8UC1, masterBuffer, 4000L)
                                            val sourceRoi = nv21Mat.submat(startY, startY + roiH, startX, startX + roiW)
                                            val bridgeW = odoBuffer.p.mat.cols()
                                            val interp = if (roiW > bridgeW) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
                                            org.opencv.imgproc.Imgproc.resize(sourceRoi, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
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
                                            applyStretch(odoBuffer.p.mat, odoBuffer.s.mat, 0.80)
                                            odoBuffer.flip()
                                        }
                                        "78% Stretch" -> {
                                            applyStretch(odoBuffer.p.mat, odoBuffer.s.mat, 0.78)
                                            odoBuffer.flip()
                                        }
                                    }

                                    // 4.4 Scale-to-Fit (Recognition)
                                    experimentRecSet320x48.p.clear()
                                    
                                    val toEven = { v: Int -> ((v + 1) / 2) * 2 }
                                    val evenW = toEven(fitW)
                                    val evenH = toEven(fitH)
                                    
                                    val mlRecCropId = experimentRecSet320x48.createCrop(0, 0, evenW, evenH)
                                    val targetMat = experimentRecSet320x48.c[mlRecCropId].mat
                                    org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, targetMat, org.opencv.core.Size(evenW.toDouble(), evenH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                                    
                                    // 4.4 ML Kit Recognition (Zero-Copy ByteBuffer)
                                    val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                                        experimentRecSet320x48.p.nv21,
                                        320, 48, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                                    )
                                    val visionText = recognizer.process(img).await()
                                    
                                    experimentRecSet320x48.c[mlRecCropId].release()
                                    
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

                                    val tLoop = System.currentTimeMillis() - tStart

                                    // 4.6 Recognition Snapshot (High-Res Visualization) - OUTSIDE TIMED LOOP
                                    val annotations = mutableListOf<SnapshotAnnotation>()
                                    val annMlKitScaleX = odoBuffer.p.mat.cols().toFloat() / evenW.toFloat()
                                    val annMlKitScaleY = odoBuffer.p.mat.rows().toFloat() / evenH.toFloat()
                                    for (j in 0 until visionBlocks.size) {
                                        val b = visionBlocks[j].boundingBox
                                        if (b != null) {
                                            annotations.add(SnapshotAnnotation(
                                                (b.left * annMlKitScaleX).toInt(), (b.top * annMlKitScaleY).toInt(), 
                                                (b.right * annMlKitScaleX).toInt(), (b.bottom * annMlKitScaleY).toInt(), 
                                                Shape.RECTANGLE, Color.rgb(255, 165, 0), 2
                                            ))
                                        }
                                    }

                                    // Source from high-res odoBuffer.p directly
                                    val (snapB64, tSnap) = OcrUtils.takeSnapshot(
                                        source = odoBuffer.p,
                                        sourceRect = null, // Full Odometer Area
                                        targetW = 320,
                                        targetH = 48,
                                        annotations = annotations,
                                        scratchArgb = scratchBmp,
                                        scratchYuv = NativePaddleEngine.fullBufferSet
                                    )
                                    lastThumbB64 = snapB64; tSnapTotal += tSnap

                                    htmlOutput.append("<div class='ocr-step'><b>Recognition ($stage):</b> ($tLoop ms)<br><img src='data:image/jpeg;base64,$lastThumbB64'><br>$odo</div>")
                                    
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
                                    totalTimeMs = System.currentTimeMillis() - tHarnessStart,
                                    tSnapshotMs = tSnapTotal
                                )
                                report.add(displayName, result)
                            }
                    finalWinnerName = winnerRef.vehicle.name

                    Log.d("DISAMB_TRACE", "--- Processing Winner: $finalWinnerName for ${file.name} ---")
                    
                    // Phase 116: Independent Disambiguation for A and B
                    val queryLandmarksPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksRaw, winnerRef.curatedLandmarks)
                    val queryLandmarksAPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksA, winnerRef.curatedLandmarks)
                    val queryLandmarksBPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksB, winnerRef.curatedLandmarks)
                    
                    // 1. Standard Alignment (In-place on masterBmp)
                    val t0 = System.currentTimeMillis()
                    val alignRes = ImageAlignmentUtils.anchorAlign(masterBmp!!, winnerRef.curatedLandmarks, queryLandmarksPrimary, winnerRef.vehicle, winnerRef.width, winnerRef.height, imgW, imgH, scratchBmp)
                    val elapsedAlign = System.currentTimeMillis() - t0

                    
                    // 1.2 Native Alignment A (In-place on bufferSetA)
                    val nativeAlignResA = ImageAlignmentUtils.anchorAlignNative(
                        NativePaddleEngine.bufferSetA, 
                        winnerRef.curatedLandmarks, 
                        queryLandmarksAPrimary, 
                        winnerRef.vehicle, 
                        winnerRef.width, 
                        winnerRef.height, 
                        imgW, 
                        imgH, 
                        scratchBmp
                    )
                    
                    // FIX: Capture Native A ALIGNED Thumbnail
                    val (snapA, tSnapA) = if (nativeAlignResA.success) { 
                        OcrUtils.takeSnapshot(
                            source = NativePaddleEngine.bufferSetA.p, 
                            sourceRect = null, 
                            targetW = 600, 
                            targetH = 450, 
                            annotations = emptyList(),
                            scratchArgb = scratchBmp,
                            scratchYuv = NativePaddleEngine.bufferSetA
                        ) 
                    } else Pair("", 0L)
                    alignedAB64 = snapA

                    // 1.3 Native Alignment B (In-place on bufferSetB)
                    val nativeAlignResB = ImageAlignmentUtils.anchorAlignNative(
                        NativePaddleEngine.bufferSetB, 
                        winnerRef.curatedLandmarks, 
                        queryLandmarksBPrimary, 
                        winnerRef.vehicle, 
                        winnerRef.width, 
                        winnerRef.height, 
                        imgW, 
                        imgH, 
                        scratchBmp
                    )
                    
                    // FIX: Capture Native B ALIGNED Thumbnail
                    val (snapB, tSnapB) = if (nativeAlignResB.success) { 
                        OcrUtils.takeSnapshot(
                            source = NativePaddleEngine.bufferSetB.p, 
                            sourceRect = null, 
                            targetW = 600, 
                            targetH = 450, 
                            annotations = emptyList(),
                            scratchArgb = scratchBmp,
                            scratchYuv = NativePaddleEngine.bufferSetB
                        ) 
                    } else Pair("", 0L)
                    alignedBB64 = snapB

                    // Capture Standard ALIGNED Thumbnail for Report (Use null to prevent clobbering)
                    alignedBase64 = createScaledBase64(masterBmp!!, 600, 50, null)
                    alignedNativeBase64 = alignedAB64 // Use A for legacy HTML row for now
                    tSnapAlign = tSnapA + tSnapB

                    val alignmentTrace = AlignmentTraceResult(alignRes.success, elapsedAlign, alignedBase64, alignRes.metadata)
                    val alignmentTraceA = AlignmentTraceResult(nativeAlignResA.success, nativeAlignResA.timeMs, alignedAB64, nativeAlignResA.metadata)
                    val alignmentTraceB = AlignmentTraceResult(nativeAlignResB.success, nativeAlignResB.timeMs, alignedBB64, nativeAlignResB.metadata)
                    
                    val refinementTraces = mutableMapOf<String, RefinementTrace>()
                    // Temporary: Map traces for Part 3.2
                    val refinementTracesA = mutableMapOf<String, RefinementTrace>()
                    val refinementTracesB = mutableMapOf<String, RefinementTrace>()
                    
                    // Phase 58: Refinement Loop (Always executed to provide diagnostic data)
                    val exactCrop = vehicleArgbCrops[winnerRef.vehicle.id]
                    if (exactCrop != null) {

                        // High-Quality Extraction: Draw from masterBmp into pre-allocated exactCrop
                        val l = winnerRef.vehicle.odometerCropLeft ?: 0f
                        val t = winnerRef.vehicle.odometerCropTop ?: 0f
                        val r = winnerRef.vehicle.odometerCropRight ?: 1f
                        val b = winnerRef.vehicle.odometerCropBottom ?: 1f
                        
                        val srcW = (r - l) * masterBmp!!.width
                        val srcH = (b - t) * masterBmp.height
                        val globalCropScaleX = exactCrop.width.toFloat() / srcW
                        val globalCropScaleY = exactCrop.height.toFloat() / srcH
                        
                        val cropCanvas = android.graphics.Canvas(exactCrop)
                        cropCanvas.drawColor(android.graphics.Color.BLACK)
                        val matrix = NativePaddleEngine.sharedMatrix
                        matrix.reset()
                        matrix.postTranslate(-l * masterBmp.width, -t * masterBmp.height)
                        matrix.postScale(globalCropScaleX, globalCropScaleY)
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

                        // --- Sequential Execution ---
                        // Sequential A/B Refinement (Phase 116)
                        runMLKitIterative("ML Diag", masterBmp!!, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                        runMLKitIterative("ML Native", NativePaddleEngine.bufferSetA, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                        runPaddleValleyMonoIterative("Paddle Std", masterBmp!!, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                        runPaddleValleyMonoIterative("Set A", NativePaddleEngine.bufferSetA, masterW = masterBmp.width, masterH = masterBmp.height, report = report)
                        runPaddleValleyMonoIterative("Set B", NativePaddleEngine.bufferSetB, masterW = masterBmp.width, masterH = masterBmp.height, report = report)

                    }
                    
                    val allResults = refinementTraces.values.flatMap { it.steps }.mapNotNull { it.text }.filter { it.isNotBlank() }
                    if (allResults.isNotEmpty()) bestOdometer = allResults.groupBy { it }.mapValues { it.value.size }.maxByOrNull { it.value }?.key ?: "FAILED"

                    // Reporting Pass: Store result for winner (Phase 116 Dual Paths)
                    val standardPath = SingleVehiclePathwayResult(alignmentTrace, refinementTraces, harnessResultsMap)
                    val setAPath = SingleVehiclePathwayResult(alignmentTraceA, refinementTracesA, harnessResultsMap)
                    val setBPath = SingleVehiclePathwayResult(alignmentTraceB, refinementTracesB, harnessResultsMap)
                    
                    vehicleResultsMap[winnerRef.vehicle.id] = SingleVehicleResult(
                        winnerRef.vehicle.name, 
                        "", 
                        0L, 
                        0L, // discoveryTimeMonoMs is now inside PhotoPathwayResult
                        mapOf("standard" to standardPath, "set_a" to setAPath, "set_b" to setBPath),
                        emptyList(), 
                        emptyList(), 
                        emptyList(), 
                        true
                    )
                }

                // Reporting Pass: Populate status for all other vehicles (Veto results)
                cachedRefs.forEach { ref ->
                    if (ref.vehicle.id != winnerId) {
                        val veto = primaryVetoResults[ref.vehicle.id] ?: VetoResult(false)
                        vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(
                            ref.vehicle.name, 
                            veto.reasonWord, 
                            0L, 0L, 
                            emptyMap(), // No paths for non-winners
                            veto.queryWords, 
                            veto.myManifest.toList(), 
                            veto.vetoPool.toList(), 
                            false
                        )
                    }
                }

                // Photo-level Pathway Results (Phase 116)
                val standardPhotoPath = PhotoPathwayResult(finalWinnerName, bestOdometer, (tMl + tPd), tDiscoveryTotal, alignedBase64, queryOcrDiscovery, queryLandmarksRaw, harnessResultsMap)
                val setAPhotoPath = PhotoPathwayResult(finalWinnerName, bestOdometer, (deskewResMono.mlTimeMs), tDiscoveryTotalA, alignedAB64, ocrA, queryLandmarksA, harnessResultsMap)
                val setBPhotoPath = PhotoPathwayResult(finalWinnerName, bestOdometer, (deskewResMono.paddleTimeMs), tDiscoveryTotalB, alignedBB64, ocrB, queryLandmarksB, harnessResultsMap)
                
                val pathways = mapOf("standard" to standardPhotoPath, "set_a" to setAPhotoPath, "set_b" to setBPhotoPath)
                val photoResult = ProcessedPhotoResult(file.name, pathways, vehicleResultsMap, primaryVetoResults)

                val rowHtml = buildHtmlRowDynamic(index + 1, file.name, imgW, imgH, meta.isDegraded, originalBase64, alignedBase64, alignedNativeBase64, queryOcrDiscovery.debugText, vehicleResultsMap, cachedRefs, finalWinnerName, emptyList(), harnessEngineNames, (tMl + tPd + tRotate), tDiscoveryTotal, tilt, deskewRes, meta.diagnostic)

                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length

                val photoJson = serializePhotoResultToJson(
                    index + 1, meta.originalWidth, meta.originalHeight, meta.decodedWidth, meta.decodedHeight, meta.isDegraded, 
                    meta.diagnostic, photoResult, vehicles, deskewRes, deskewResMono, tSnapOrig, tSnapAlign
                )
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma\n")
                
                val resultSummary = PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer)
                currentResult = photoResult

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
    lineNumber: Int, probedW: Int, probedH: Int, decodedW: Int, decodedH: Int, isDegraded: Boolean, 
    nativeProbe: String, photoResult: ProcessedPhotoResult, vehicles: List<Vehicle>, 
    deskewRes: OdometerOcrUtils.DeskewResult, deskewResMono: OdometerOcrUtils.DeskewResult? = null,
    tSnapOrig: Long = 0, tSnapAlign: Long = 0
): JSONObject {
    val root = JSONObject()
    root.apply {
        put("line_number", lineNumber)
        put("file", photoResult.fileName)
        put("probedWidth", probedW)
        put("probedHeight", probedH)
        put("imageWidth", decodedW)
        put("imageHeight", decodedH)
        put("isDegraded", isDegraded)
        put("nativeProbe", nativeProbe)
        put("t_thumb_orig_ms", tSnapOrig)
        put("t_snap_align_ms", tSnapAlign)

        // Pathway Serialization (Phase 116)
        val pathwaysJson = JSONObject()
        photoResult.pathways.forEach { (pathKey, pathRes) ->
            pathwaysJson.put(pathKey, serializePathwayToJson(pathRes))
        }
        put("pathways", pathwaysJson)

        // Legacy/Top-level Metrics for compatibility with existing analysis scripts
        put("winner", photoResult.pathways["standard"]?.winnerName ?: "No match")
        put("odometer", photoResult.pathways["standard"]?.bestOdometer ?: "FAILED")
        
        // Deskew Data (Combined map for A/B comparison)
        val deskewObj = JSONObject()
        deskewObj.put("std_angle", deskewRes.angle)
        deskewObj.put("mono_angle", deskewResMono?.angle ?: 0f)
        put("deskew", deskewObj)

        val safeW = (photoResult.pathways["standard"]?.discoveryResult?.imageWidth ?: 1).toDouble()
        val safeH = (photoResult.pathways["standard"]?.discoveryResult?.imageHeight ?: 1).toDouble()

        val mlArray = JSONArray()
        deskewRes.mlBlocks.forEach { block ->
            mlArray.put(JSONObject().apply {
                put("text", block.text)
                putSafe("cx", block.boundingBox.centerX().toDouble() / safeW, "")
                putSafe("cy", block.boundingBox.centerY().toDouble() / safeH, "")
                putSafe("w", block.boundingBox.width().toDouble() / safeW, "")
                putSafe("h", block.boundingBox.height().toDouble() / safeH, "")
                putSafe("angle", block.angle.toDouble(), "")
            })
        }
        put("deskew_data_mlkit", mlArray)

        val pdArray = JSONArray()
        deskewRes.paddleBlocks.forEach { block ->
            pdArray.put(JSONObject().apply {
                put("text", block.text)
                putSafe("cx", block.boundingBox.centerX().toDouble() / safeW, "")
                putSafe("cy", block.boundingBox.centerY().toDouble() / safeH, "")
                putSafe("w", block.boundingBox.width().toDouble() / safeW, "")
                putSafe("h", block.boundingBox.height().toDouble() / safeH, "")
                putSafe("angle", block.angle.toDouble(), "")
            })
        }
        put("deskew_data_paddle", pdArray)

        val landmarksArray = JSONArray()
        photoResult.pathways["standard"]?.discoveryResult?.textBlocks?.forEach { block -> 
            val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text)
            if (cleanedText.length > 1) {
                landmarksArray.put(JSONObject().apply { 
                    put("text", cleanedText)
                    putSafe("cx", block.boundingBox.centerX().toDouble() / safeW, "")
                    putSafe("cy", block.boundingBox.centerY().toDouble() / safeH, "")
                    putSafe("w", block.boundingBox.width().toDouble() / safeW, "")
                    putSafe("h", block.boundingBox.height().toDouble() / safeH, "")
                    putSafe("angle", block.angle.toDouble(), "")
                    put("instance", block.instanceId)
                })
            }
        }
        put("discovery_landmarks", JSONObject().apply { put("ML Kit", landmarksArray) })

        val vehicleResults = JSONArray()
        photoResult.vehicleResultsMap.values.forEach { vRes ->
            vehicleResults.put(JSONObject().apply {
                put("vehicle", vRes.vehicleName)
                put("is_winner", vRes.isWinner)
                put("veto_reason", vRes.vetoReason)
                
                val pathsObj = JSONObject()
                vRes.pathResults.forEach { (pathKey, pathRes) ->
                    pathsObj.put(pathKey, serializeVehiclePathwayToJson(pathRes))
                }
                put("path_results", pathsObj)
            }) 
        }; put("vehicles", vehicleResults)
    }
    return root
}

private fun serializePathwayToJson(res: PhotoPathwayResult): JSONObject {
    val root = JSONObject()
    root.apply {
        put("winner", res.winnerName)
        put("odometer", res.bestOdometer)
        put("t_deskew_ms", res.tDeskewTotal)
        put("t_discovery_ms", res.tDiscoveryTotal)
        put("discovery_debug", res.discoveryResult.debugText)
        
        val harnessTimings = JSONObject()
        res.harnessResults.forEach { (engine, hRes) ->
            val hObj = JSONObject()
            hObj.put("total_ms", hRes.totalTimeMs)
            hObj.put("snapshot_ms", hRes.tSnapshotMs)
            hObj.put("stages", JSONObject(hRes.jsonSection.toString()))
            harnessTimings.put(engine, hObj)
        }
        put("harness", harnessTimings)
    }
    return root
}

private fun serializeVehiclePathwayToJson(res: SingleVehiclePathwayResult): JSONObject {
    val root = JSONObject()
    root.apply {
        res.alignmentTrace?.let { trace ->
            val tObj = JSONObject()
            tObj.put("success", trace.success)
            tObj.put("time_ms", trace.timeMs)
            val meta = JSONObject()
            trace.metadata.forEach { (k, v) -> meta.put(k, v) }
            tObj.put("metadata", meta)
            put("alignment", tObj)
        }
        
        val refinementJson = JSONObject()
        res.refinementTraces.forEach { (strat, trace) ->
            val sObj = JSONObject()
            sObj.put("time_ms", trace.timeMs)
            val stepsArray = JSONArray()
            trace.steps.forEach { step ->
                val stepObj = JSONObject()
                stepObj.put("stage", step.stageName)
                stepObj.put("text", step.text)
                val meta = JSONObject()
                step.metadata.forEach { (k, v) -> meta.put(k, v) }
                stepObj.put("metadata", meta)
                stepsArray.put(stepObj)
            }
            sObj.put("steps", stepsArray)
            refinementJson.put(strat, sObj)
        }
        put("refinement", refinementJson)
    }
    return root
    }

    private fun buildHtmlHeader(
time: String, total: Int, version: String, strategies: List<String>, harnessEngines: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }</style></head><body>")
    appendLine("<h1>OCR Refinement Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th><th style='width:650px;'>Aligned & Stats</th><th style='width:650px;'>Native Aligned</th>")
    harnessEngines.forEach { appendLine("<th style='width:300px;'>$it</th>") }
    strategies.forEach { appendLine("<th style='width:300px;'>$it</th>") }
    appendLine("<th style='width:300px;'>Refinement Consensus</th></tr>")
}

private fun buildHtmlRowDynamic(
    rowIndex: Int, 
    fileName: String, 
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    originalBase64: String, 
    alignedBase64: String,
    alignedNativeBase64: String,
    discovery: String, 
    vehicleResults: Map<Int, SingleVehicleResult>, 
    cachedRefs: List<ReferenceCache>, 
    winnerName: String, 
    strategies: List<String>, 
    harnessEngines: List<String>,
    tDeskew: Long, 
    tDiscovery: Long,
    tilt: Float,
    deskewRes: OdometerOcrUtils.DeskewResult,
    diagnostic: String = ""
): String = buildString {
    val resHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty()) "<br><small>Native: $diagnostic</small>" else ""
    appendLine("<tr><td><b>#$rowIndex</b><br><small>$fileName</small><br><small>$resHtml</small>$diagHtml<br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms<br><img src='data:image/jpeg;base64,$originalBase64'></td>")
    
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    // Aligned Column (Standard Path)
    appendLine("<td>")
    if (alignedBase64.isNotEmpty()) {
        appendLine("<img src='data:image/jpeg;base64,$alignedBase64'><br>")
        vRes?.pathResults?.get("standard")?.alignmentTrace?.let { trace ->
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

    // Native Aligned Column (Set A Path)
    appendLine("<td>")
    vRes?.pathResults?.get("set_a")?.alignmentTrace?.let { trace ->
        if (trace.alignedImageBase64.isNotEmpty()) {
            appendLine("<img src='data:image/jpeg;base64,${trace.alignedImageBase64}'><br>")
            val s = trace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val tx = trace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val ty = trace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            appendLine("<small>Native Warp (Cubic)<br>Scale: %.3f<br>TX: %.1f, TY: %.1f<br>Time: ${trace.timeMs}ms</small>".format(s, tx, ty))
        } else {
            appendLine("<i>No native trace</i>")
        }
    } ?: appendLine("<i>Not Aligned</i>")
    appendLine("</td>")
    
    val allReadings = mutableListOf<String>()
    harnessEngines.forEach { engine ->
        appendLine("<td>")
        // Check harness results across all paths
        val hRes = vRes?.pathResults?.values?.firstNotNullOfOrNull { it.harnessResults[engine] }
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
            // Check refinement traces across all paths (Take first one for now)
            val trace = vRes.pathResults.values.firstNotNullOfOrNull { it.refinementTraces[strat] }
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
    
    val p1 = IcrsMath.icrsToPixel(vehicle.odometerCropLeft ?: 0f, vehicle.odometerCropTop ?: 0f, bmp.width, bmp.height)
    val p2 = IcrsMath.icrsToPixel(vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f, bmp.width, bmp.height)
    canvas.drawRect(p1.x, p1.y, p2.x, p2.y, paint)
    
    return annotated
}

private fun getFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else { Log.e("ExperimentAlignment", "Manifest missing data for engine: $engineName"); return emptyList() }
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i); val text = obj.getString("text")
                val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0)
                val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0)
                val isIcrs = obj.optBoolean("is_icrs", false)
                
                val centerPix = if (isIcrs) {
                    IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)
                } else {
                    android.graphics.PointF((cx * imgW).toFloat(), (cy * imgH).toFloat())
                }
                
                val shortEdge = minOf(imgW, imgH).toDouble()
                val pixW = if (isIcrs) (w * shortEdge) else (w * imgW)
                val pixH = if (isIcrs) (h * shortEdge) else (h * imgH)
                
                val instanceId = if (obj.has("instance")) obj.getInt("instance") else -1
                val cleanText = OdometerOcrUtils.cleanLandmarkString(text)
                
                val left = (centerPix.x - pixW/2.0).toInt()
                val top = (centerPix.y - pixH/2.0).toInt()
                val right = (centerPix.x + pixW/2.0).toInt()
                val bottom = (centerPix.y + pixH/2.0).toInt()
                
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

private fun toEvenInt(v: Float): Int = ((v + 1).toInt() / 2) * 2

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

private fun JSONObject.putSafe(key: String, value: Double, context: String = ""): JSONObject {
    return if (value.isFinite()) {
        this.put(key, value)
    } else {
        android.util.Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context")
        this.put(key, "ERR: $value")
    }
}

private fun JSONObject.putSafe(key: String, value: Float, context: String = ""): JSONObject {
    return if (value.isFinite()) {
        this.put(key, value)
    } else {
        android.util.Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context")
        this.put(key, "ERR: $value")
    }
}




