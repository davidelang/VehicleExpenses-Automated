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

private val GOLDEN_SUBSET = mapOf(
    "PXL_20220701_020707365.dng" to 1,
    "PXL_20220821_051055938.dng" to 2,
    "PXL_20221029_002946498.dng" to 4,
    "PXL_20221020_215546513.dng" to 3,
    "PXL_20221221_205939873.dng" to 9,
    "PXL_20221228_164725812.dng" to 12,
    "PXL_20221222_211445685.dng" to 10,
    "PXL_20230113_231330881.dng" to 14,
    "PXL_20221121_021330418.jpg" to 5,
    "PXL_20221126_210323823.jpg" to 7,
    "PXL_20221128_172727575.jpg" to 8
)

private val FAILING_SUBSET = mapOf(
    "PXL_20221121_021330418.jpg" to 5,
    "PXL_20221228_164725812.dng" to 12,
    "PXL_20230430_042448627.dng" to 22,
    "PXL_20231221_212942380.jpg" to 40,
    "PXL_20231223_074744139.jpg" to 41,
    "PXL_20240114_162249446.jpg" to 45,
    "PXL_20240414_010409990.jpg" to 50,
    "PXL_20240717_235836312.jpg" to 55,
    "PXL_20240722_200247194.jpg" to 56,
    "PXL_20251111_071548876.jpg" to 124,
    "PXL_20260214_204037399.jpg" to 131,
    "PXL_20260413_083458977.jpg" to 141
)

@Immutable
data class PhotoResultSummary(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float
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
                val subset = allFiles.filter { it.name in GOLDEN_SUBSET.keys }
                totalPhotos = subset.size
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }, GOLDEN_SUBSET) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Limited Report saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Limited Experiment (Golden Subset)") }
        Button(onClick = { 
            if (vehicles.isEmpty()) { status = "Error: No vehicles in DB."; return@Button }
            scope.launch { 
                val allFiles = experimentDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") } ?: emptyArray()
                val subset = allFiles.filter { it.name in FAILING_SUBSET.keys }
                totalPhotos = subset.size
                isRunning = true; resultsList.clear()
                runExperiment(experimentDir, reportDir, debugCropDir, vehicles, context, { detailLog = it }, FAILING_SUBSET) { res, p -> 
                    resultsList.add(res); progress = p; currentPhotoName = res.photoName 
                }
                isRunning = false; status = "Complete! Failing Subset Report saved." 
            } 
        }, enabled = !isRunning && experimentDir.exists(), modifier = Modifier.fillMaxWidth()) { Text("Run Failing Subset") }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(resultsList) { index, res ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.width(8.dp))
                        Column { Text(res.photoName, style = MaterialTheme.typography.labelSmall); Text("Match: ${res.matchedVehicle}", color = MaterialTheme.colorScheme.primary) }
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

data class PipelineConfig(
    val key: String,
    val displayName: String,
    val getDeskewTime: (OdometerOcrUtils.DeskewResult) -> Long,
    val getAngle: (OdometerOcrUtils.DeskewResult) -> Float
)

private suspend fun runExperiment(
    experimentDir: File, 
    reportDir: File, 
    debugCropDir: File, 
    vehicles: List<Vehicle>, 
    context: Context, 
    onLog: (String) -> Unit, 
    subsetMap: Map<String, Int>?, 
    onProgress: (PhotoResultSummary, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val allPhotos = experimentDir.listFiles { f -> 
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") 
    }?.sortedBy { it.name } ?: return@withContext
    
    val photos = if (subsetMap != null) {
        allPhotos.filter { it.name in subsetMap.keys }
    } else allPhotos
    
    val total = photos.size
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val paddleEngine = NativePaddleEngine(context)

    val cachedRefs = vehicles.map { vehicle ->
        val bmp = OdometerOcrUtils.decodeBitmapSafely(context, vehicle.referenceDashPhotoUrl!!) 
            ?: BitmapFactory.decodeFile(vehicle.referenceDashPhotoUrl)
        val curated = getFullLandmarksFromJson(vehicle.landmarkTextBlocksJson, "ML Kit", bmp.width, bmp.height)
        val annotatedBmp = drawCropBoxesOnReference(bmp, vehicle)
        val refBase64 = createScaledBase64(annotatedBmp, 400, 70)
        annotatedBmp.recycle()
        ReferenceCache(vehicle, refBase64, curated, bmp, bmp.width, bmp.height)
    }
    
    val vehicleBufferSets = mutableMapOf<Int, BufferSet>()
    withContext(Dispatchers.Main) {
        cachedRefs.forEach { ref ->
            val l = ref.vehicle.odometerCropLeft ?: 0f
            val t = ref.vehicle.odometerCropTop ?: 0f
            val r = ref.vehicle.odometerCropRight ?: 1f
            val b = ref.vehicle.odometerCropBottom ?: 1f
            
            val icrsRect = if (ref.vehicle.isIcrs) RectF(l, t, r, b) else IcrsMath.legacyAnisotropicToIcrs(RectF(l, t, r, b), ref.bmp.width, ref.bmp.height)
            
            if (l != null) {
                val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, ref.bmp.width, ref.bmp.height)
                val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, ref.bmp.width, ref.bmp.height)
                val srcW = (p2.x - p1.x).toInt()
                val srcH = (p2.y - p1.y).toInt()
                
                // Align to 32-pixel boundaries for efficient native processing
                val targetW = if (srcW % 32 == 0) srcW else (srcW / 32 + 1) * 32
                val targetH = if (srcH % 2 == 0) srcH else (srcH / 2 + 1) * 2
                
                if (targetW > 0 && targetH > 0) {
                    vehicleBufferSets[ref.vehicle.id] = BufferSet(targetW, targetH)

                    listOf(NativePaddleEngine.bufferSetA, NativePaddleEngine.bufferSetB).forEach { set ->
                        // DELIBERATE: We use the Vehicle ID as the explicit BufferSet crop ID here.
                        // This allows an arbitrary number of vehicles to maintain long-lived, 
                        // uniquely identifiable references within the shared global buffers.
                        set.p.createCrop(icrsRect.left, icrsRect.top, icrsRect.width(), icrsRect.height(), id = ref.vehicle.id)
                    }
                }
            }
        }
    }
    
    val jsonFile = File(reportDir, "alignment_results_$timestamp.json")
    jsonFile.writeText("{\n  \"timestamp\": \"$timestamp\",\n  \"version\": \"${BuildConfig.VERSION_NAME}\",\n  \"total_photos\": $total,\n  \"results\": [\n")
    
    // Pre-allocated JSON serialization buffer (16MB starting capacity)
    var jsonCharBuffer = StringBuilder(16 * 1024 * 1024)
    
    var partCount = 1
    val maxSizeBytes = 5 * 1024 * 1024 // 5MB parts
    var currentSize = 0
    val footer = "</table></body></html>"
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentDetSet512x128 = BufferSet(512, 128)

    val pipelines = listOf(
        PipelineConfig("set_a", "Set A", { it.mlTimeMs }) { it.mlAngle },
        PipelineConfig("set_e", "Set E", { it.paddleTimeMs }) { it.paddleCppAngle },
        PipelineConfig("set_h", "Set H (Char-Aware Expansion)", { it.paddleTimeMs }) { it.paddleCppAngle },
        PipelineConfig("set_i", "Set I (All Components Debug)", { it.paddleTimeMs }) { it.paddleCppAngle }
    )
    val harnessEngineNames = listOf("Set A ML") + pipelines.map { "${it.displayName} Paddle" }
    val pipelineNames = pipelines.map { it.displayName }

    fun startNewFile(): File {
        val f = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html")
        f.writeText(buildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, emptyList(), harnessEngineNames, pipelineNames))
        return f
    }

    var currentFile = startNewFile()
    
    photos.forEachIndexed { index, file ->
        val originalLineNumber = subsetMap?.get(file.name) ?: (index + 1)
        // Phase 116 Emergency Fix: Initialize photoResult early with "No Match" state
        // to prevent serializePhotoResultToJson crashes on failed identification.
        var photoResult: ProcessedPhotoResult? = ProcessedPhotoResult(file.name, emptyMap(), emptyMap(), emptyMap())
        var finalWinnerName = "No match"
        
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${index + 1}/$total: ${file.name} (#$originalLineNumber)") }
            val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, file.absolutePath)
            
            // Sequential A/B Ingestion
            NativePaddleEngine.bufferSetA.resize(imgW, imgH)
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)
            
            val meta = ImageIngestionProvider.ingestFromFile(context, file.absolutePath, NativePaddleEngine.bufferSetA.p)
            
            // Manual Distribution (A to B)
            NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
            NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)
            
            val (originalBase64, tSnapOrig) = OcrUtils.takeSnapshot(
                source = NativePaddleEngine.bufferSetA.p,
                sourceRect = null,
                targetW = 225,
                targetH = 0, // Aspect-aware
                annotations = emptyList(),
                scratchArgb = null,
                scratchYuv = NativePaddleEngine.bufferSetA
            )

            try {
                // Step 2 (Deskew): Calculate tilt independently for Set A/E
                val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(NativePaddleEngine.bufferSetA.p)

                val tilt = deskewResA.angle
                val tMl = deskewResA.mlTimeMs
                val tPd = deskewResA.paddleTimeMs

                // Phase 116: Independent High-Quality Rotation (Cubic)
                suspend fun rotate(set: BufferSet, angle: Float): Long = withContext(Dispatchers.IO) {
                    val tRot0 = System.currentTimeMillis()
                    val src = set.p.mat
                    val dst = set.s.mat
                    
                    val matrixLocal = android.graphics.Matrix()
                    matrixLocal.postRotate(-angle, src.cols() / 2f, src.rows() / 2f)
                    val values = FloatArray(9)
                    matrixLocal.getValues(values)

                    val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
                    rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
                    rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())

                    // Rotate Luma (Y)
                    org.opencv.imgproc.Imgproc.warpAffine(src, dst, rotMat, src.size(), org.opencv.imgproc.Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, org.opencv.core.Scalar(0.0))
                    
                    // Rotate Chroma (UV)
                    val srcUv = set.p.uvMat
                    val dstUv = set.s.uvMat
                    val uvScaleMat = rotMat.clone()
                    // Shift translation for half-res UV plane
                    uvScaleMat.put(0, 2, rotMat.get(0, 2)[0] / 2.0)
                    uvScaleMat.put(1, 2, rotMat.get(1, 2)[0] / 2.0)
                    org.opencv.imgproc.Imgproc.warpAffine(srcUv, dstUv, uvScaleMat, srcUv.size(), org.opencv.imgproc.Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, org.opencv.core.Scalar(128.0, 128.0))
                    
                    set.flip()
                    rotMat.release(); uvScaleMat.release()
                    System.currentTimeMillis() - tRot0
                }

                
                val pathways = mutableMapOf<String, PhotoPathwayResult>()
                val vehiclePathways = mutableMapOf<Int, MutableMap<String, SingleVehiclePathwayResult>>()
                val primaryVetoResultsMap = mutableMapOf<String, Map<Int, VetoResult>>()
                
                var tDiscoveryTotalCombined = 0L
                var tSnapAlign = 0L
                
                var globalWinnerId: Int? = null
                var primaryVetoResultsGlobal: Map<Int, VetoResult> = emptyMap()
                var ocrFirst: OcrResult? = null

                pipelines.forEach { pipeline ->
                    // Reset work buffer Set B by copying from ingested original Set A
                    NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
                    NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)

                    val extraImages = mutableMapOf<String, String>()

                                        // Use stable Raw deskew for all remaining sets
                    val currentDeskewRes = deskewResA
                    val angle = pipeline.getAngle(currentDeskewRes)
                    
                    // Rotate work buffer Set B
                    rotate(NativePaddleEngine.bufferSetB, angle)
                    
                    // Perform discovery on rotated work buffer Set B
                    val tDisc0 = System.currentTimeMillis()
                    val (ocr, queryLandmarks) = performLandmarkDiscovery(NativePaddleEngine.bufferSetB.p, context)
                    val tDiscoveryTotal = System.currentTimeMillis() - tDisc0
                    tDiscoveryTotalCombined += tDiscoveryTotal
                    
                    if (ocrFirst == null) {
                        ocrFirst = ocr
                    }
                    
                    val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, cachedRefs.map { it.vehicle }, "ML Kit")
                    primaryVetoResultsMap[pipeline.key] = primaryVetoResults
                    
                    val winnerId = primaryVetoResults.entries.find { !it.value.isVetoed }?.key
                    if (globalWinnerId == null) {
                        globalWinnerId = winnerId
                        primaryVetoResultsGlobal = primaryVetoResults
                    }
                    
                    val globalWinnerRef = cachedRefs.find { it.vehicle.id == globalWinnerId }
                    
                    var alignedBase64 = ""
                    val hMap = mutableMapOf<String, OcrHarnessResult>()
                    var landmarksForAudit: List<TextBlock> = emptyList()
                    val refinementTraces = mutableMapOf<String, RefinementTrace>()
                    var alignResSuccess = false
                    var alignResTimeMs = 0L
                    val alignResMetadata = mutableMapOf<String, String>()
                    
                    if (globalWinnerRef != null) {
                        val queryLandmarksPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, globalWinnerRef.curatedLandmarks)
                        landmarksForAudit = queryLandmarksPrimary.filter { it.instanceId >= 0 }
                        
                        val alignRes = ImageAlignmentUtils.anchorAlign(
                            NativePaddleEngine.bufferSetB,
                            globalWinnerRef.curatedLandmarks,
                            queryLandmarksPrimary,
                            globalWinnerRef.vehicle,
                            globalWinnerRef.width,
                            globalWinnerRef.height,
                            imgW,
                            imgH,
                            null
                        )
                        alignResSuccess = alignRes.success
                        alignResTimeMs = alignRes.timeMs
                        alignResMetadata.putAll(alignRes.metadata)
                        
                        val (snap, tSnap) = if (alignRes.success) {
                            OcrUtils.takeSnapshot(
                                source = NativePaddleEngine.bufferSetB.p,
                                sourceRect = null,
                                targetW = 600,
                                targetH = 450,
                                annotations = emptyList(),
                                scratchArgb = null,
                                scratchYuv = NativePaddleEngine.bufferSetB
                            )
                        } else Pair("", 0L)
                        alignedBase64 = snap
                        tSnapAlign += tSnap
                        
                        // Refinement Loop (Always executed to provide diagnostic data)
                        if (globalWinnerRef.vehicle.id >= 0) {
                            // Diagnostic High-Quality Crop (Save to disk using native snapshot)
                            val l = globalWinnerRef.vehicle.odometerCropLeft ?: 0f
                            val t = globalWinnerRef.vehicle.odometerCropTop ?: 0f
                            val r = globalWinnerRef.vehicle.odometerCropRight ?: 1f
                            val b = globalWinnerRef.vehicle.odometerCropBottom ?: 1f
                            
                            val icrsRect = if (globalWinnerRef.vehicle.isIcrs) RectF(l, t, r, b) else IcrsMath.legacyAnisotropicToIcrs(RectF(l, t, r, b), imgW, imgH)
                            val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, imgW, imgH)
                            val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, imgW, imgH)
                            val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                            
                            val (cropB64, _) = OcrUtils.takeSnapshot(
                                source = NativePaddleEngine.bufferSetB.p,
                                sourceRect = roi,
                                targetW = 320,
                                targetH = 48,
                                scratchArgb = null,
                                scratchYuv = NativePaddleEngine.bufferSetB
                            )
                            
                            val cropFile = File(debugCropDir, "crop_${file.name.replace(".dng", ".jpg")}")
                            try { cropFile.outputStream().use { out -> out.write(android.util.Base64.decode(cropB64, android.util.Base64.NO_WRAP)) } } catch (e: Exception) { Log.e(TAG, "Failed to save crop", e) }
                            
                            // For Set F and G, only run the "Raw" stage
                            val iterativeStages = listOf("Raw", "Bin-Trials", "Bin")

                            if (pipeline.key == "set_a") {
                                runMLKitIterative("${pipeline.displayName} ML", NativePaddleEngine.bufferSetB, imgW, imgH, globalWinnerRef, vehicleBufferSets, experimentRecSet320x48, hMap, refinementTraces, iterativeStages)
                            }
                            runPaddleValleyIterative("${pipeline.displayName} Paddle", NativePaddleEngine.bufferSetB, imgW, imgH, globalWinnerRef, vehicleBufferSets, experimentDetSet512x128, experimentRecSet320x48, paddleEngine, hMap, refinementTraces, isNumeric = true, iterativeStages, extraImages, useCharAware = (pipeline.key == "set_h" || pipeline.key == "set_i"), pipelineKey = pipeline.key)
                        }
                    }
                    
                    val photoPathway = PhotoPathwayResult(
                        winnerName = globalWinnerRef?.vehicle?.name ?: "No match",
                        
                        tDeskewTotal = pipeline.getDeskewTime(currentDeskewRes),
                        tDiscoveryTotal = tDiscoveryTotal,
                        deskewedBase64 = alignedBase64,
                        discoveryResult = ocr,
                        discoveryLandmarks = queryLandmarks,
                        harnessResults = hMap
                    )
                    pathways[pipeline.key] = photoPathway
                    
                    val alignmentTrace = AlignmentTraceResult(alignResSuccess, alignResTimeMs, alignedBase64, alignResMetadata)
                    val vehiclePathway = SingleVehiclePathwayResult(alignmentTrace, refinementTraces, landmarksForAudit, hMap)
                    
                    if (globalWinnerRef != null) {
                        val map = vehiclePathways.getOrPut(globalWinnerRef.vehicle.id) { mutableMapOf() }
                        map[pipeline.key] = vehiclePathway
                    }
                }
                
                val winnerRef = cachedRefs.find { it.vehicle.id == globalWinnerId }
                if (winnerRef != null) {
                    finalWinnerName = winnerRef.vehicle.name
                }
                
                val updatedPathways = pathways
                
                // Build vehicleResultsMap
                val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
                cachedRefs.forEach { ref ->
                    val isWinner = (ref.vehicle.id == globalWinnerId)
                    val pathMap = vehiclePathways[ref.vehicle.id] ?: emptyMap()
                    vehicleResultsMap[ref.vehicle.id] = SingleVehicleResult(
                        ref.vehicle.name,
                        if (isWinner) "" else (primaryVetoResultsGlobal[ref.vehicle.id]?.reasonWord ?: "Vetoed"),
                        0L, 0L,
                        pathMap,
                        primaryVetoResultsGlobal[ref.vehicle.id]?.queryWords ?: emptyList(),
                        primaryVetoResultsGlobal[ref.vehicle.id]?.myManifest?.toList() ?: emptyList(),
                        primaryVetoResultsGlobal[ref.vehicle.id]?.vetoPool?.toList() ?: emptyList(),
                        isWinner
                    )
                }
                
                photoResult = ProcessedPhotoResult(file.name, updatedPathways, vehicleResultsMap, primaryVetoResultsGlobal)
                
                val rowHtml = buildHtmlRowDynamic(
                    originalLineNumber, file.name, imgW, imgH, meta.isDegraded, originalBase64,
                    photoResult!!, vehicleResultsMap, cachedRefs, finalWinnerName, emptyList(),
                    harnessEngineNames, (tMl + tPd), tDiscoveryTotalCombined,
                    tilt, deskewResA, pipelines, meta.diagnostic, photoResult.pathways["set_g"]?.harnessResults?.get("Set G (Raw Angle + 80% Early) Paddle")?.extraImages ?: emptyMap()
                )
                
                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length

                val photoJson = serializePhotoResultToJson(
                    originalLineNumber, imgW, imgH, imgW, imgH, meta.isDegraded, 
                    meta.diagnostic, photoResult!!, vehicles, deskewResA, tSnapOrig, tSnapAlign
                )
                val comma = if (index < total - 1) "," else ""
                
                // Clear/reset or re-allocate the reusable buffer to keep memory bounded
                if (jsonCharBuffer.capacity() > 64 * 1024 * 1024) {
                    jsonCharBuffer = StringBuilder(16 * 1024 * 1024)
                } else {
                    jsonCharBuffer.setLength(0)
                }
                
                appendJsonObject(jsonCharBuffer, photoJson, 2, 0)
                jsonFile.appendText(jsonCharBuffer.toString() + "$comma\n")
                
                val resultSummary = PhotoResultSummary(file.name, finalWinnerName, 1.0f)

                // Ensure UI update is dispatched BEFORE we move to cleanup
                withContext(Dispatchers.Main) { 
                    onProgress(resultSummary, (index + 1).toFloat() / total) 
                }
                
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.i("MEMORY_CHECK", "[Image ${index + 1}/${total}] Used Heap: ${usedMem}MB / ${runtime.maxMemory() / 1024 / 1024}MB")
                
                delay(150)
            } finally {
                // BufferSets are cleaned up globally or at end of session
            }
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Experiment failed for row $index (${file.name}):\n" + Log.getStackTraceString(e))
        }
    }
    currentFile.appendText(footer)
    jsonFile.appendText("\n  ]\n}")
    
    cachedRefs.forEach { it.bmp.recycle() }
    experimentRecSet320x48.release()
    experimentDetSet512x128.release()
    vehicleBufferSets.values.forEach { it.release() }
    vehicleBufferSets.clear()
}

private fun serializePhotoResultToJson(
    lineNumber: Int, probedW: Int, probedH: Int, decodedW: Int, decodedH: Int, isDegraded: Boolean, 
    nativeProbe: String, photoResult: ProcessedPhotoResult, vehicles: List<Vehicle>, 
    deskewResA: OdometerOcrUtils.DeskewResult? = null,
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

        // Top-level Metrics (Source from Set A as default)
        put("winner", photoResult.pathways["set_a"]?.winnerName ?: "No match")
        
        
        // Deskew Data (Source from Path A)
        val deskewObj = JSONObject()
        deskewObj.putSafe("angle_a", (deskewResA?.angle ?: 0f).toDouble())
        deskewObj.putSafe("paddle_cpp_angle", (deskewResA?.paddleCppAngle ?: 0f).toDouble())

        val paddleKtAngle = deskewResA?.engines?.get("Paddle V3")?.angle ?: 0f
        deskewObj.putSafe("paddle_kt_angle", paddleKtAngle.toDouble())
        
        // Add A/B Parity Checksums and timing metrics
        deskewResA?.engines?.get("Paddle V3")?.metadata?.forEach { (k, v) -> 
            deskewObj.put(k, v)
        }
        deskewResA?.engines?.get("ML Kit")?.metadata?.forEach { (k, v) -> 
            deskewObj.put(k, v)
        }
        deskewResA?.metadata?.forEach { (k, v) ->
            deskewObj.put(k, v)
        }
        // Also put ML Kit times explicitly
        val mlTimes = deskewResA?.engines?.get("ML Kit")?.timesMs
        if (mlTimes != null && mlTimes.size >= 2) {
            deskewObj.put("t_ml_prep_ms", mlTimes[0])
            deskewObj.put("t_ml_detect_ms", mlTimes[1])
        }
        put("deskew", deskewObj)

        val safeW = decodedW
        val safeH = decodedH
        val safeS = minOf(safeW, safeH).toDouble()

        val mlArray = JSONArray()
        deskewResA?.mlBlocks?.forEach { block ->
            mlArray.put(JSONObject().apply {
                put("text", block.text)
                val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), safeW, safeH)
                putSafe("cx", icrs.x.toDouble(), "")
                putSafe("cy", icrs.y.toDouble(), "")
                putSafe("w", block.boundingBox.width().toDouble() / safeS, "")
                putSafe("h", block.boundingBox.height().toDouble() / safeS, "")
                putSafe("angle", block.angle.toDouble(), "")
                putSafe("confidence", block.confidence.toDouble(), "")
                put("is_icrs", true)
            })
        }
        put("deskew_data_mlkit", mlArray)

        val pdArray = JSONArray()
        deskewResA?.paddleBlocks?.forEach { block ->
            pdArray.put(JSONObject().apply {
                put("text", block.text)
                val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), safeW, safeH)
                putSafe("cx", icrs.x.toDouble(), "")
                putSafe("cy", icrs.y.toDouble(), "")
                putSafe("w", block.boundingBox.width().toDouble() / safeS, "")
                putSafe("h", block.boundingBox.height().toDouble() / safeS, "")
                putSafe("angle", block.angle.toDouble(), "")
                putSafe("confidence", block.confidence.toDouble(), "")
                put("is_icrs", true)
            })
        }
        put("deskew_data_paddle", pdArray)

        val pdCppArray = JSONArray()
        deskewResA?.paddleCppBlocks?.forEach { block ->
            pdCppArray.put(JSONObject().apply {
                put("text", block.text)
                val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), safeW, safeH)
                putSafe("cx", icrs.x.toDouble(), "")
                putSafe("cy", icrs.y.toDouble(), "")
                putSafe("w", block.boundingBox.width().toDouble() / safeS, "")
                putSafe("h", block.boundingBox.height().toDouble() / safeS, "")
                putSafe("angle", block.angle.toDouble(), "")
                putSafe("confidence", block.confidence.toDouble(), "")
                put("is_icrs", true)
            })
        }
        put("deskew_data_paddle_cpp", pdCppArray)



        val landmarksArray = JSONArray()
        photoResult.pathways["set_a"]?.discoveryResult?.textBlocks?.forEach { block -> 
            val cleanedText = OdometerOcrUtils.cleanLandmarkString(block.text)
            if (cleanedText.length > 1) {
                landmarksArray.put(JSONObject().apply { 
                    put("text", cleanedText)
                    val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), safeW, safeH)
                    putSafe("cx", icrs.x.toDouble(), "")
                    putSafe("cy", icrs.y.toDouble(), "")
                    putSafe("w", block.boundingBox.width().toDouble() / safeS, "")
                    putSafe("h", block.boundingBox.height().toDouble() / safeS, "")
                    putSafe("angle", block.angle.toDouble(), "")
                    put("instance", block.instanceId)
                    put("is_icrs", true)
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

private fun appendJsonValue(sb: StringBuilder, value: Any?, indent: Int, indentLevel: Int) {
    if (sb.length > 64 * 1024 * 1024) {
        throw IllegalStateException("JSON serialization exceeded the 64MB safety ceiling")
    }
    when (value) {
        null -> sb.append("null")
        JSONObject.NULL -> sb.append("null")
        is JSONObject -> appendJsonObject(sb, value, indent, indentLevel)
        is JSONArray -> appendJsonArray(sb, value, indent, indentLevel)
        is String -> {
            sb.append('"')
            escapeJsonString(sb, value)
            sb.append('"')
        }
        is Boolean -> sb.append(value.toString())
        is Number -> sb.append(value.toString())
        else -> {
            sb.append('"')
            escapeJsonString(sb, value.toString())
            sb.append('"')
        }
    }
}

private fun appendJsonObject(sb: StringBuilder, json: JSONObject, indent: Int, indentLevel: Int) {
    sb.append("{\n")
    val keys = json.keys()
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    var first = true
    while (keys.hasNext()) {
        if (!first) {
            sb.append(",\n")
        }
        first = false
        val key = keys.next()
        val value = json.get(key)
        sb.append(indentStr).append('"').append(key).append("\": ")
        appendJsonValue(sb, value, indent, nextLevel)
    }
    sb.append("\n").append(" ".repeat(indentLevel * indent)).append("}")
}

private fun appendJsonArray(sb: StringBuilder, array: JSONArray, indent: Int, indentLevel: Int) {
    sb.append("[\n")
    val nextLevel = indentLevel + 1
    val indentStr = " ".repeat(nextLevel * indent)
    for (i in 0 until array.length()) {
        if (i > 0) {
            sb.append(",\n")
        }
        sb.append(indentStr)
        appendJsonValue(sb, array.get(i), indent, nextLevel)
    }
    sb.append("\n").append(" ".repeat(indentLevel * indent)).append("]")
}

private fun escapeJsonString(sb: StringBuilder, str: String) {
    for (i in 0 until str.length) {
        val ch = str[i]
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '/' -> sb.append("\\/")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch.code < 32 || ch.code > 126) {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
}

private fun serializePathwayToJson(res: PhotoPathwayResult): JSONObject {
    val root = JSONObject()
    root.apply {
        put("winner", res.winnerName)
        
        put("t_deskew_ms", res.tDeskewTotal)
        put("t_discovery_ms", res.tDiscoveryTotal)
        put("discovery_debug", res.discoveryResult.debugText)
        
        val harnessTimings = JSONObject()
        res.harnessResults.forEach { (engine, hRes) ->
            val hObj = JSONObject()
            hObj.put("total_ms", hRes.totalTimeMs)
            hObj.put("snapshot_ms", hRes.tSnapshotMs)
            hObj.put("odometer", hRes.odometerValue)
            hObj.put("stages", JSONObject(hRes.jsonSection.toString()))
            val extraObj = JSONObject()
            hRes.extraImages.forEach { (ek, ev) -> extraObj.put(ek, ev) }
            hObj.put("extraImages", extraObj)
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
                step.metadata.forEach { (k, v) -> 
                    if (k != "best_plain_pre" && k != "best_annotated_pre" && k != "best_plain_post" && k != "best_annotated_post") {
                        meta.put(k, v)
                    }
                }
                stepObj.put("metadata", meta)
                stepsArray.put(stepObj)
            }
            sObj.put("steps", stepsArray)
            refinementJson.put(strat, sObj)
        }
        put("refinement", refinementJson)
        val landArray = JSONArray()
        res.disambiguatedLandmarks.forEach { l ->
            val lObj = JSONObject()
            lObj.put("name", l.text)
            lObj.put("cx", l.boundingBox.centerX())
            lObj.put("cy", l.boundingBox.centerY())
            lObj.put("instance_id", l.instanceId)
            landArray.put(lObj)
        }
        put("disambiguated_landmarks", landArray)
    }
    return root
}

private fun matToPbmP4Base64(mat: org.opencv.core.Mat): String {
    val cols = mat.cols()
    val rows = mat.rows()
    val totalPixels = cols * rows
    val data = ByteArray(totalPixels)
    mat.get(0, 0, data)
    
    val packedSize = (totalPixels + 7) / 8
    val packed = ByteArray(packedSize)
    
    var byteIdx = 0
    var bitIdx = 0
    var currentByte = 0
    
    for (i in 0 until totalPixels) {
        val pixelVal = data[i].toInt() and 0xFF
        val bit = if (pixelVal <= 127) 1 else 0
        currentByte = (currentByte shl 1) or bit
        bitIdx++
        if (bitIdx == 8) {
            packed[byteIdx++] = currentByte.toByte()
            currentByte = 0
            bitIdx = 0
        }
    }
    if (bitIdx > 0) {
        currentByte = currentByte shl (8 - bitIdx)
        packed[byteIdx++] = currentByte.toByte()
    }
    
    val header = "P4\n$cols $rows\n".toByteArray(Charsets.US_ASCII)
    val fullData = ByteArray(header.size + packed.size)
    System.arraycopy(header, 0, fullData, 0, header.size)
    System.arraycopy(packed, 0, fullData, header.size, packed.size)
    
    return android.util.Base64.encodeToString(fullData, android.util.Base64.NO_WRAP)
}

private fun serializeAnnotations(anns: List<SnapshotAnnotation>): String {
    val arr = org.json.JSONArray()
    anns.forEach { ann ->
        val obj = org.json.JSONObject()
        obj.put("x1", ann.x1)
        obj.put("y1", ann.y1)
        obj.put("x2", ann.x2)
        obj.put("y2", ann.y2)
        obj.put("shape", ann.shape.name)
        obj.put("color", ann.color)
        obj.put("strokeWidth", ann.strokeWidth)
        arr.put(obj)
    }
    return arr.toString()
}

private suspend fun runBinTrialsPaddle(
    odoBuffer: BufferSet,
    masterBuffer: BufferSet,
    vehicleId: Int,
    experimentDetSet512x128: BufferSet,
    experimentRecSet320x48: BufferSet,
    paddleEngine: NativePaddleEngine,
    rawBins: FloatArray,
    useCharAware: Boolean,
    steps: List<OcrStepResult>,
    pipelineKey: String = ""
): Pair<String, Map<String, String>> {
    val midpoints = findValleyMidpoints(rawBins)
    val trialsHtml = StringBuilder("<div style='border:1px solid #ccc; padding:4px; margin-top:4px;'><b>Bin-Trials:</b><br>")
    val trialsMeta = mutableMapOf<String, String>()
    data class TrialData(
        val thresh: Double,
        val text: String,
        val sumProb: Float,
        val minProb: Float,
        val probsStr: String,
        val annotatedPreB64: String,
        val plainPreB64: String,
        val annotatedPostB64: String,
        val plainPostB64: String,
        val histB64: String,
        val avgConf: Float,
        val metadata: Map<String, String>,
        val post1bppB64: String,
        val annotationsStr: String
    )
    val trialsList = mutableListOf<TrialData>()

    midpoints.forEachIndexed { vIdx, binIdx ->
        val threshold = binIdx * 4.0

        // Step 1: Pull fresh raw grayscale crop from masterBuffer to odoBuffer.p
        odoBuffer.p.clear()
        val interp = if (masterBuffer.c[vehicleId].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        org.opencv.imgproc.Imgproc.resize(masterBuffer.c[vehicleId].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)

        // Step 2: Binarize into .s, then flip so .p = binary, .s = original grayscale (scratchpad).
        odoBuffer.s.clear()
        org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        odoBuffer.flip()
        
        val detSc = kotlin.math.min(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
        val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
        val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
        
        experimentDetSet512x128.p.clear()
        val dCrId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[dCrId].mat, experimentDetSet512x128.c[dCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes = paddleEngine.detect(experimentDetSet512x128.p, copyHeatmap = false)
        var tFullB = if (detRes != null) OdometerOcrUtils.processPaddleHeatmap(detRes.heatmap, detRes.width, detRes.height, detSc, experimentDetSet512x128.p, "Paddle", nativeBoxes = detRes.nativeBoxes) else emptyList<TextBlock>()
        experimentDetSet512x128.c[dCrId].release()
        
        var tRawB = tFullB.filter { b1 ->
            tFullB.none { b2 -> b1 !== b2 && b2.boundingBox.contains(b1.boundingBox.left + 5, b1.boundingBox.top + 5, b1.boundingBox.right - 5, b1.boundingBox.bottom - 5) }
        }
        
        val thresholdFactor = 128.0f

        val annsPre = mutableListOf<SnapshotAnnotation>()
        tRawB.forEach { b -> annsPre.add(SnapshotAnnotation(b.boundingBox.left, b.boundingBox.top, b.boundingBox.right, b.boundingBox.bottom, Shape.RECTANGLE, android.graphics.Color.RED, 2)) }

        val (tPlainPreB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, emptyList(), null, NativePaddleEngine.bufferSetA)
        val (tAnnotatedPreB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, annsPre, null, NativePaddleEngine.bufferSetA)

        if (tRawB.isEmpty()) {
            val annStr = serializeAnnotations(annsPre)
            trialsList.add(TrialData(
                threshold, "ERR: Peak detection failed (No bounding box detected)", 0f, 0f, "",
                tAnnotatedPreB64, tPlainPreB64, "", "",
                "Peak detection failed (No bounding box detected).", 0f, emptyMap(),
                "", annStr
            ))
            trialsMeta["trial_${vIdx}_annotations"] = annStr
            return@forEachIndexed
        }

        // Cache pre-cleaning histograms/plots (primitive types only)
        val cachedRawRedBoxHists = tRawB.map { b ->
            val redBoxCropId = odoBuffer.createCrop(b.boundingBox.left, b.boundingBox.top, b.boundingBox.width(), b.boundingBox.height())
            val cropRect = android.graphics.Rect(0, 0, odoBuffer.crop[redBoxCropId].width, odoBuffer.crop[redBoxCropId].height)
            val hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.crop[redBoxCropId].mat, listOf(cropRect), thresholdFactor)
            val b64 = if (hRes != null) generateDualHistogramB64(hRes.first.first, hRes.first.second) else null
            odoBuffer.crop[redBoxCropId].release()
            Pair(hRes, b64)
        }

        var rb = tRawB.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: tRawB.first()
        val rbIndex = tRawB.indexOf(rb)
        val rbCached = cachedRawRedBoxHists.getOrNull(rbIndex)
        val vSW_red = rbCached?.first?.second?.get(0)?.toFloat() ?: -1f
        val hSW_red = rbCached?.first?.second?.get(1)?.toFloat() ?: -1f

        if (vSW_red <= 0f || hSW_red <= 0f) {
            val histsHtml = StringBuilder()
            tRawB.forEachIndexed { rIdx, b ->
                val cached = cachedRawRedBoxHists.getOrNull(rIdx)
                if (cached != null) {
                    val hResCached = cached.first
                    val b64Cached = cached.second
                    if (hResCached != null && b64Cached != null) {
                        val meta = hResCached.second
                        histsHtml.append("<br><small>Red Box #$rIdx [${b.boundingBox.left},${b.boundingBox.top} - ${b.boundingBox.right},${b.boundingBox.bottom}] (${b.boundingBox.width()}x${b.boundingBox.height()}) vSW=${meta[0]} hSW=${meta[1]} Pitch=0 (Peak detection failed):</small><br><img src='data:image/jpeg;base64,$b64Cached'>")
                    }
                }
            }
            histsHtml.append("<br>Raw peak detection failed (vSW_red=$vSW_red, hSW_red=$hSW_red).")

            val annStr = serializeAnnotations(annsPre)
            trialsList.add(TrialData(
                threshold, "ERR: Peak detection failed (vSW_red=$vSW_red, hSW_red=$hSW_red)", 0f, 0f, "",
                tAnnotatedPreB64, tPlainPreB64, "", "",
                histsHtml.toString(), 0f, emptyMap(),
                "", annStr
            ))
            trialsMeta["trial_${vIdx}_annotations"] = annStr
            return@forEachIndexed
        }

        NativeImageUtils.filterComponents(odoBuffer.p.mat, vSW_red, hSW_red, 1)
        NativeImageUtils.filterComponents(odoBuffer.p.mat, vSW_red, hSW_red, 2)

        experimentDetSet512x128.p.clear()
        val dCrId2 = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[dCrId2].mat, experimentDetSet512x128.c[dCrId2].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes2 = paddleEngine.detect(experimentDetSet512x128.p, copyHeatmap = false)
        tFullB = if (detRes2 != null) OdometerOcrUtils.processPaddleHeatmap(detRes2.heatmap, detRes2.width, detRes2.height, detSc, experimentDetSet512x128.p, "Paddle", nativeBoxes = detRes2.nativeBoxes) else emptyList<TextBlock>()
        experimentDetSet512x128.c[dCrId2].release()

        rb = tFullB.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: rb
        var redBoxCropId = odoBuffer.createCrop(rb.boundingBox.left, rb.boundingBox.top, rb.boundingBox.width(), rb.boundingBox.height())
        var cropRect = android.graphics.Rect(0, 0, odoBuffer.crop[redBoxCropId].width, odoBuffer.crop[redBoxCropId].height)
        var hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.crop[redBoxCropId].mat, listOf(cropRect), thresholdFactor)
        val vSW = hRes?.second?.get(0)?.toFloat() ?: -1f
        val hSW = hRes?.second?.get(1)?.toFloat() ?: -1f
        odoBuffer.crop[redBoxCropId].release()

        if (vSW <= 0f || hSW <= 0f) {
            val histsHtml = StringBuilder()
            
            // 1. Raw Red Box Histograms
            tRawB.forEachIndexed { rIdx, b ->
                val cached = cachedRawRedBoxHists.getOrNull(rIdx)
                if (cached != null) {
                    val hResCached = cached.first
                    val b64Cached = cached.second
                    if (hResCached != null && b64Cached != null) {
                        val meta = hResCached.second
                        histsHtml.append("<br><small>Red Box #$rIdx [${b.boundingBox.left},${b.boundingBox.top} - ${b.boundingBox.right},${b.boundingBox.bottom}] (${b.boundingBox.width()}x${b.boundingBox.height()}) vSW=${meta[0]} hSW=${meta[1]} Pitch=0:</small><br><img src='data:image/jpeg;base64,$b64Cached'>")
                    }
                }
            }
            
            // 2. Cleaned Red Box Histogram
            redBoxCropId = odoBuffer.createCrop(rb.boundingBox.left, rb.boundingBox.top, rb.boundingBox.width(), rb.boundingBox.height())
            cropRect = android.graphics.Rect(0, 0, odoBuffer.crop[redBoxCropId].width, odoBuffer.crop[redBoxCropId].height)
            hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.crop[redBoxCropId].mat, listOf(cropRect), thresholdFactor)
            if (hRes != null) {
                val b64 = generateDualHistogramB64(hRes.first.first, hRes.first.second); val meta = hRes.second
                histsHtml.append("<br><small>Cleaned Red Box [${rb.boundingBox.left},${rb.boundingBox.top} - ${rb.boundingBox.right},${rb.boundingBox.bottom}] (${rb.boundingBox.width()}x${rb.boundingBox.height()}) vSW=${meta[0]} hSW=${meta[1]} Pitch=0 (Peak detection failed):</small><br><img src='data:image/jpeg;base64,$b64'>")
            }
            odoBuffer.crop[redBoxCropId].release()

            histsHtml.append("<br>Cleaned peak detection failed (vSW_clean=$vSW, hSW_clean=$hSW).")

            val (tPlainPostB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, emptyList(), null, NativePaddleEngine.bufferSetA)
            val (tAnnotatedPostB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, annsPre, null, NativePaddleEngine.bufferSetA)
            val post1bpp = matToPbmP4Base64(odoBuffer.p.mat)
            val annStr = serializeAnnotations(annsPre)

            trialsList.add(TrialData(
                threshold, "ERR: Cleaned peak detection failed (vSW_clean=$vSW, hSW_clean=$hSW)", 0f, 0f, "",
                tAnnotatedPreB64, tPlainPreB64, tAnnotatedPostB64, tPlainPostB64,
                histsHtml.toString(), 0f, emptyMap(),
                post1bpp, annStr
            ))
            trialsMeta["trial_${vIdx}_post_1bpp"] = post1bpp
            trialsMeta["trial_${vIdx}_annotations"] = annStr
            return@forEachIndexed
        }

        val trialMetaMap = mutableMapOf(
            "charaware_pitch"         to "0",
            "charaware_v_stroke"      to vSW.toInt().toString(),
            "charaware_h_stroke"      to hSW.toInt().toString(),
            "charaware_v_stroke_raw"  to vSW_red.toInt().toString(),
            "charaware_h_stroke_raw"  to hSW_red.toInt().toString(),
            "charaware_matched_slots" to "",
            "charaware_failed_slots"  to "",
            "charaware_vlimit"        to String.format("%.1f", vSW * 0.75f),
            "charaware_hlimit"        to String.format("%.1f", hSW * 0.75f)
        )

        val valleyResults = if (pipelineKey == "set_i") {
            NativeImageUtils.blackOutLargeComponentsH(odoBuffer.p.mat, 0.25f * odoBuffer.p.mat.cols())
            val compRects = NativeImageUtils.findAllComponentsH(odoBuffer.p.mat, vSW, hSW)
            compRects.map { Pair(it, trialMetaMap) }
        } else {
            val initialBounds = NativeImageUtils.expandBoundsH(odoBuffer.p.mat, rb.boundingBox, thresholdFactor, vSW, hSW)
            val pitchData = NativeImageUtils.calculatePitchH(odoBuffer.p.mat, initialBounds, thresholdFactor, vSW, hSW)
            val pitch      = pitchData?.get(0) ?: 0
            val anchorMode = pitchData?.get(1) ?: 0
            val bestShift  = pitchData?.get(2) ?: 0

            val gridResult = if (pitch > 0) NativeImageUtils.alignGridH(
                odoBuffer.p.mat, initialBounds, pitch, bestShift, anchorMode,
                vSW, hSW, thresholdFactor
            ) else null

            val finalBounds = if (useCharAware) initialBounds else (gridResult?.first ?: initialBounds)
            val matchedSlots = gridResult?.second ?: IntArray(0)
            val failedSlots  = gridResult?.third  ?: IntArray(0)

            trialMetaMap["charaware_pitch"] = pitch.toString()
            trialMetaMap["charaware_matched_slots"] = matchedSlots.joinToString(",")
            trialMetaMap["charaware_failed_slots"] = failedSlots.joinToString(",")
            trialMetaMap["charaware_vlimit"] = String.format("%.1f", vSW * 0.5f)
            trialMetaMap["charaware_hlimit"] = String.format("%.1f", hSW * 0.75f)

            val tValleyResults = tRawB.map { Pair(finalBounds, trialMetaMap) }
            if (useCharAware) tValleyResults
            else tRawB.map { NativeImageUtils.expandByValleyDiagnostic(odoBuffer.p.mat, it.boundingBox, 0.40f) }
        }

        val tFrags = valleyResults.map { it.first }
        val tCons = if (pipelineKey == "set_i") {
            if (tFrags.isNotEmpty()) {
                var minL = Int.MAX_VALUE
                var minT = Int.MAX_VALUE
                var maxR = Int.MIN_VALUE
                var maxB = Int.MIN_VALUE
                for (rect in tFrags) {
                    if (rect.left < minL) minL = rect.left
                    if (rect.top < minT) minT = rect.top
                    if (rect.right > maxR) maxR = rect.right
                    if (rect.bottom > maxB) maxB = rect.bottom
                }
                val orangeL = minL.coerceIn(0, odoBuffer.p.mat.cols() - 1)
                val orangeT = minT.coerceIn(0, odoBuffer.p.mat.rows() - 1)
                val orangeR = maxR.coerceIn(orangeL + 1, odoBuffer.p.mat.cols())
                val orangeB = maxB.coerceIn(orangeT + 1, odoBuffer.p.mat.rows())
                listOf(android.graphics.Rect(orangeL, orangeT, orangeR, orangeB))
            } else {
                emptyList()
            }
        } else {
            OdometerOcrUtils.clusterRects(tFrags).sortedBy { it.left }
        }
        val tOdoB = StringBuilder(); val tProbsB = StringBuilder(); var tCf = 0f; var tCnt = 0
        
        valleyResults.forEachIndexed { vI, res ->
            res.second.forEach { (k, v) -> trialsMeta["trial_${vIdx}_frag_${vI}_$k"] = v }
        }

        tCons.forEach { tBox ->
            val sL = tBox.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
            val sT = tBox.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
            val sR = tBox.right.coerceIn(sL + 1, odoBuffer.p.mat.cols())
            val sB = tBox.bottom.coerceIn(sT + 1, odoBuffer.p.mat.rows())
            if (sR > sL && sB > sT) {
                val bRecMat = odoBuffer.p.mat.submat(org.opencv.core.Rect(sL, sT, sR - sL, sB - sT))
                experimentRecSet320x48.p.clear()
                val rSc = kotlin.math.min(312f / bRecMat.cols(), 40f / bRecMat.rows())
                val ew = ((bRecMat.cols() * rSc + 1).toInt() / 2) * 2
                val eh = ((bRecMat.rows() * rSc + 1).toInt() / 2) * 2
                val rCrId = experimentRecSet320x48.createCrop(4, 4, ew, eh)
                org.opencv.imgproc.Imgproc.resize(bRecMat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                val ocrR = paddleEngine.recognizeNumeric(experimentRecSet320x48.p)
                if (ocrR.debugText.isNotBlank()) {
                    tOdoB.append(ocrR.debugText).append(" ")
                    ocrR.metadata["ocr_probs"]?.let { tProbsB.append(it).append(" ") }
                }
                tCf += (ocrR.textBlocks.firstOrNull()?.confidence ?: 0f) * ocrR.debugText.length
                tCnt += ocrR.debugText.length
                experimentRecSet320x48.c[rCrId].release()
                bRecMat.release()
            }
        }
        val tText = tOdoB.toString().trim(); val tProbsStr = tProbsB.toString().trim(); val tAvg = if (tCnt > 0) tCf / tCnt else 0f
        val tProbs = mutableListOf<Float>(); val regex = Regex("\\((0\\.\\d+|1\\.0+)\\)"); regex.findAll(tProbsStr).forEach { tProbs.add(it.groupValues[1].toFloatOrNull() ?: 0f) }
        val minP = if (tProbs.isNotEmpty()) tProbs.minOrNull() ?: 0f else 0f
        
        val annsPost = mutableListOf<SnapshotAnnotation>()
        tRawB.forEach { b -> annsPost.add(SnapshotAnnotation(b.boundingBox.left, b.boundingBox.top, b.boundingBox.right, b.boundingBox.bottom, Shape.RECTANGLE, android.graphics.Color.RED, 2)) }
        if (pipelineKey == "set_i") {
            tFrags.forEach { b -> annsPost.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, android.graphics.Color.BLUE, 2)) }
            tCons.forEach { b -> annsPost.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, android.graphics.Color.rgb(255, 165, 0), 2)) }
        } else {
            tCons.forEach { b -> annsPost.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, android.graphics.Color.rgb(255, 165, 0), 2)) }
        }
        
        val histsHtml = StringBuilder()
        if (useCharAware && valleyResults.isNotEmpty()) {
            valleyResults.forEach { res ->
                val mSlots = res.second["charaware_matched_slots"]
                if (!mSlots.isNullOrEmpty()) {
                    val pts = mSlots.split(",").mapNotNull { it.toIntOrNull() }
                    for (i in 0 until pts.size step 4) {
                        if (i + 3 < pts.size) annsPost.add(SnapshotAnnotation(pts[i], pts[i+1], pts[i+2], pts[i+3], Shape.RECTANGLE, android.graphics.Color.WHITE, 1))
                    }
                }
                val fSlots = res.second["charaware_failed_slots"]
                if (!fSlots.isNullOrEmpty()) {
                    val pts = fSlots.split(",").mapNotNull { it.toIntOrNull() }
                    for (i in 0 until pts.size step 4) {
                        if (i + 3 < pts.size) annsPost.add(SnapshotAnnotation(pts[i], pts[i+1], pts[i+2], pts[i+3], Shape.RECTANGLE, android.graphics.Color.BLUE, 1))
                    }
                }
            }

            valleyResults.firstOrNull()?.second?.let { meta ->
                val p = meta["charaware_pitch"] ?: "0"
                histsHtml.append("<br><b>Overall Pitch (from 1st Red Box, post-clean):</b> $p px")

                val vLimit = meta["charaware_vlimit"] ?: "?"
                val hLimit = meta["charaware_hlimit"] ?: "?"
                val vRaw = meta["charaware_v_stroke_raw"] ?: "?"
                val hRaw = meta["charaware_h_stroke_raw"] ?: "?"
                val vClean = meta["charaware_v_stroke"] ?: "?"
                val hClean = meta["charaware_h_stroke"] ?: "?"
                histsHtml.append("<br><small>Cleaned: narrow w&lt;${vLimit}, short h&lt;=${hLimit} | vSW raw=$vRaw → clean=$vClean | hSW raw=$hRaw → clean=$hClean</small>")
            }

            tRawB.forEachIndexed { rIdx, b ->
                val cached = cachedRawRedBoxHists.getOrNull(rIdx)
                if (cached != null) {
                    val hResCached = cached.first
                    val b64Cached = cached.second
                    if (hResCached != null && b64Cached != null) {
                        val meta = hResCached.second
                        val pitch = valleyResults.getOrNull(rIdx)?.second?.get("charaware_pitch") ?: "0"
                        histsHtml.append("<br><small>Red Box #$rIdx [${b.boundingBox.left},${b.boundingBox.top} - ${b.boundingBox.right},${b.boundingBox.bottom}] (${b.boundingBox.width()}x${b.boundingBox.height()}) vSW=${meta[0]} hSW=${meta[1]} Pitch=$pitch:</small><br><img src='data:image/jpeg;base64,$b64Cached'>")
                    }
                }
            }
            tCons.forEachIndexed { oIdx, ob ->
                val orangeBoxCropId = odoBuffer.createCrop(ob.left, ob.top, ob.width(), ob.height())
                val cropRect = android.graphics.Rect(0, 0, odoBuffer.crop[orangeBoxCropId].width, odoBuffer.crop[orangeBoxCropId].height)
                val hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.crop[orangeBoxCropId].mat, listOf(cropRect), thresholdFactor)
                if (hRes != null) {
                    val b64 = generateDualHistogramB64(hRes.first.first, hRes.first.second); val meta = hRes.second
                    val firstIntersect = tRawB.find { it.boundingBox.intersects(ob.left, ob.top, ob.right, ob.bottom) }
                    val pitch = if (firstIntersect != null) {
                        val rIdx = tRawB.indexOf(firstIntersect)
                        valleyResults.getOrNull(rIdx)?.second?.get("charaware_pitch") ?: "0"
                    } else "0"
                    histsHtml.append("<br><small>Orange Box #$oIdx [${ob.left},${ob.top} - ${ob.right},${ob.bottom}] (${ob.width()}x${ob.height()}) vSW=${meta[0]} hSW=${meta[1]} Pitch=$pitch:</small><br><img src='data:image/jpeg;base64,$b64'>")
                }
                odoBuffer.crop[orangeBoxCropId].release()
            }
        }

        val (tPlainPostB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, emptyList(), null, NativePaddleEngine.bufferSetA)
        val (tAnnotatedPostB64, _) = OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48, annsPost, null, NativePaddleEngine.bufferSetA)
        val post1bpp = matToPbmP4Base64(odoBuffer.p.mat)
        val annStr = serializeAnnotations(annsPost)
        
        trialsList.add(TrialData(
            threshold, tText, tProbs.sum(), minP, tProbsStr,
            tAnnotatedPreB64, tPlainPreB64, tAnnotatedPostB64, tPlainPostB64,
            histsHtml.toString(), tAvg, trialMetaMap,
            post1bpp, annStr
        ))
        trialsMeta["trial_${vIdx}_post_1bpp"] = post1bpp
        trialsMeta["trial_${vIdx}_annotations"] = annStr
    }
    
    val highQual = trialsList.filter { it.minProb >= 0.90f }
    val winner = if (highQual.isNotEmpty()) highQual.maxByOrNull { it.sumProb } else trialsList.maxByOrNull { it.sumProb }
    
    // Set winning binarization state
    if (winner != null) {
        odoBuffer.p.clear()
        val interp = if (masterBuffer.c[vehicleId].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        org.opencv.imgproc.Imgproc.resize(masterBuffer.c[vehicleId].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)

        odoBuffer.s.clear()
        org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, winner.thresh, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        odoBuffer.flip()
    }

    trialsList.forEachIndexed { idx, t ->
        val isWinner = (t == winner)
        val border = if (isWinner) "2px solid #00ff00" else "1px dashed #eee"
        val status = if (isWinner) "<b>[SELECTED]</b> " else if (t.minProb < 0.90f) "<span style=\"color:red\">[REJECTED: Min Prob < 0.90]</span> " else "[REJECTED: Sum defeated]"
        
        val preCleanPlain = if (t.plainPreB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${t.plainPreB64}'><br>" else ""
        val preCleanAnnot = if (t.annotatedPreB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${t.annotatedPreB64}'><br>" else ""
        val postCleanPlain = if (t.plainPostB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${t.plainPostB64}'><br>" else ""
        val postCleanAnnot = if (t.annotatedPostB64.isNotEmpty()) "<img src='data:image/jpeg;base64,${t.annotatedPostB64}'><br>" else ""
        
        trialsHtml.append("<div style=\"margin-bottom:8px; border-bottom:$border; padding:2px;\">$status T=${t.thresh.toInt()}: <b>${t.text}</b> (Conf: ${"%.2f".format(t.avgConf)})<br><small>${t.probsStr}</small><br><b>Pre-Cleaned (Binarized Only):</b><br>$preCleanPlain$preCleanAnnot<b>Post-Cleaned (OCR Input):</b><br>$postCleanPlain$postCleanAnnot${t.histB64}</div>")
        
        trialsMeta["trial_$idx"] = "${t.thresh}|${t.text}|${t.avgConf}"
        if (t.probsStr.isNotEmpty()) trialsMeta["trial_${idx}_probs"] = t.probsStr
    }
    
    val winnerMeta = if (winner != null) {
        mutableMapOf(
            "best_threshold" to winner.thresh.toString(),
            "best_text" to winner.text,
            "best_thumb" to winner.annotatedPostB64,
            "best_probs" to winner.probsStr,
            "selection_logic" to (if (winner.minProb >= 0.90f) "Filter(Min>=0.90)->Sum" else "Fallback(Sum)")
        ).apply {
            putAll(winner.metadata)
            put("best_plain_pre", winner.plainPreB64)
            put("best_annotated_pre", winner.annotatedPreB64)
            put("best_plain_post", winner.plainPostB64)
            put("best_annotated_post", winner.annotatedPostB64)
            put("best_post_1bpp", winner.post1bppB64)
            put("best_annotations", winner.annotationsStr)
        }
    } else emptyMap()
    trialsMeta.putAll(winnerMeta)
    return Pair(trialsHtml.toString(), trialsMeta)
}
private suspend fun runBinTrialsMLKit(
    odoBuffer: BufferSet,
    experimentRecSet320x48: BufferSet,
    rawBins: FloatArray,
    steps: List<OcrStepResult>
): Pair<String, Map<String, String>> {
    val midpoints = findValleyMidpoints(rawBins)
    val trialsHtml = StringBuilder("<div style='border:1px solid #ccc; padding:4px; margin-top:4px;'><b>Bin-Trials:</b><br>")
    val trialsMeta = mutableMapOf<String, String>()
    data class TrialData(val thresh: Double, val text: String, val base64: String)
    val trialsList = mutableListOf<TrialData>()

    val rawStep = steps.find { it.stageName == "Raw" }
    if (rawStep != null) {
        trialsList.add(TrialData(-1.0, rawStep.text?.trim() ?: "", rawStep.thumbB64))
    }

    midpoints.forEachIndexed { vIdx, binIdx ->
        val threshold = binIdx * 4.0
        odoBuffer.s.clear(); org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        val trialMat = odoBuffer.s.mat; experimentRecSet320x48.p.clear()
        val rSc = kotlin.math.min(320f / trialMat.cols(), 48f / trialMat.rows())
        val ew = ((trialMat.cols() * rSc + 1).toInt() / 2) * 2; val eh = ((trialMat.rows() * rSc + 1).toInt() / 2) * 2
        val rCrId = experimentRecSet320x48.createCrop(0, 0, ew, eh); org.opencv.imgproc.Imgproc.resize(trialMat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(experimentRecSet320x48.p.nv21, 320, 48, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
        val vText = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await()
        experimentRecSet320x48.c[rCrId].release()
        val tOdoB = StringBuilder()
        vText.textBlocks.forEach { blk -> blk.lines.forEach { line -> val cleaned = OdometerOcrUtils.clean7SegmentDigits(line.text, false).filter { it.isDigit() }; if (cleaned.isNotBlank()) tOdoB.append(cleaned) } }
        val tResText = tOdoB.toString()
        val anns = mutableListOf<SnapshotAnnotation>(); val snX = trialMat.cols().toFloat() / ew.toFloat(); val snY = trialMat.rows().toFloat() / eh.toFloat()
        var orangeArea = 0; val mlBoxes = mutableListOf<String>()
        vText.textBlocks.forEach { b -> b.boundingBox?.let { val l = (it.left * snX).toInt(); val t = (it.top * snY).toInt(); val r = (it.right * snX).toInt(); val bot = (it.bottom * snY).toInt(); anns.add(SnapshotAnnotation(l, t, r, bot, Shape.RECTANGLE, android.graphics.Color.rgb(255, 165, 0), 2)); orangeArea += (r - l) * (bot - t); mlBoxes.add("$l,$t,$r,$bot") } }
        val (tB64, _) = OcrUtils.takeSnapshot(trialMat, null, 200, 0, anns, null, NativePaddleEngine.bufferSetA)
        trialsList.add(TrialData(threshold, tResText, tB64))
        trialsMeta["trial_${vIdx}_orange_area"] = orangeArea.toString(); if (mlBoxes.isNotEmpty()) trialsMeta["trial_${vIdx}_orange_boxes"] = mlBoxes.joinToString(";")
    }
    val winner = trialsList.maxByOrNull { it.text.length }
    trialsList.forEachIndexed { idx, t ->
        if (t.thresh < 0) return@forEachIndexed
        val isWinner = (t == winner); val border = if (isWinner) "2px solid #00ff00" else "1px dashed #eee"; val status = if (isWinner) "<b>[SELECTED]</b> " else "[REJECTED]"
        trialsHtml.append("<div style=\"margin-bottom:8px; border-bottom:$border; padding:2px;\">$status T=${t.thresh.toInt()}: <b>${t.text}</b><br><img src=\"data:image/jpeg;base64,${t.base64}\"></div>")
        trialsMeta["trial_$idx"] = "${t.thresh}|${t.text}|1.0"
    }
    val winnerMeta = if (winner != null) {
        mapOf(
            "best_threshold" to winner.thresh.toString(),
            "best_text" to winner.text,
            "best_thumb" to winner.base64,
            "selection_logic" to (if (winner.thresh < 0) "RAW Selected" else "Max Length (ML Kit)")
        )
    } else emptyMap()
    trialsMeta.putAll(winnerMeta)
    return Pair(trialsHtml.toString(), trialsMeta)
}

private fun findValleyMidpoints(bins: FloatArray): List<Int> {
    if (bins.isEmpty()) return emptyList()
    val binCount = bins.size
    val smoothed = FloatArray(binCount)
    for (i in 0 until binCount) {
        val start = (i - 1).coerceAtLeast(0)
        val end = (i + 1).coerceAtMost(binCount - 1)
        smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
    }

    val midpoints = mutableListOf<Int>()
    var i = 1
    while (i < binCount - 1) {
        if (smoothed[i] <= smoothed[i - 1] && smoothed[i] <= smoothed[i + 1]) {
            val startIdx = i
            while (i < binCount - 1 && smoothed[i + 1] == smoothed[startIdx]) { i++ }
            val endIdx = i
            
            val risesLeft = smoothed[startIdx - 1] > smoothed[startIdx]
            val risesRight = if (endIdx < binCount - 1) smoothed[endIdx + 1] > smoothed[endIdx] else false
            
            if (risesLeft && risesRight) {
                midpoints.add((startIdx + endIdx) / 2)
            }
        }
        i++
    }
    return midpoints.distinct()
}

private fun buildHtmlHeader(time: String, total: Int, version: String, strategies: List<String>, harnessEngines: List<String>, pipelineNames: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }</style></head><body>")
    append("<h1>OCR Refinement Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th>")
    pipelineNames.forEach { append("<th style='width:650px;'>Native Aligned $it</th>") }
    harnessEngines.forEach { append("<th style='width:300px;'>$it</th>") }
    strategies.forEach { append("<th style='width:300px;'>$it</th>") }
    appendLine("<th style='width:300px;'>Refinement Consensus</th></tr>")
}

private fun buildHtmlRowDynamic(
    rowIndex: Int, 
    fileName: String, 
    imgW: Int,
    imgH: Int,
    isDegraded: Boolean,
    originalBase64: String, 
    photoResult: ProcessedPhotoResult,
    vehicleResults: Map<Int, SingleVehicleResult>, 
    cachedRefs: List<ReferenceCache>, 
    winnerName: String, 
    strategies: List<String>, 
    harnessEngines: List<String>,
    tDeskew: Long, 
    tDiscovery: Long,
    tilt: Float,
    deskewRes: OdometerOcrUtils.DeskewResult,
    pipelines: List<PipelineConfig>,
    diagnostic: String = "",
    extraImages: Map<String, String> = emptyMap(), useCharAware: Boolean = false
): String = buildString {
    val resHtml = if (isDegraded) "<span style='color:red;'>Res: ${imgW}x${imgH} (DEGRADED)</span>" else "Res: ${imgW}x${imgH}"
    val diagHtml = if (diagnostic.isNotEmpty()) "<br><small>Native: $diagnostic</small>" else ""
    val angMl = deskewRes.mlAngle
    val angV3 = deskewRes.engines["Paddle V3"]?.angle ?: 0f
    val angCpp = deskewRes.paddleCppAngle
    appendLine("<tr><td><b>#$rowIndex</b>")
    appendLine("<br><small>$fileName</small>")
    appendLine("<br><small>$resHtml</small>$diagHtml")
    appendLine("<br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms")
    appendLine("<br>ML: ${"%.1f".format(angMl)}&deg; | V3: ${"%.1f".format(angV3)}&deg; | CPP: ${"%.1f".format(angCpp)}&deg;")
    appendLine("<br><img src='data:image/jpeg;base64,$originalBase64'>")
    
    // For Set G, show histograms in first column
    if (extraImages.containsKey("hist1")) {
        appendLine("<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${extraImages["hist1"]}'><br><small>Before Hist (Yellow=Stretch, Magenta=80%)</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${extraImages["hist2"]}'><br><small>After Hist (Cyan=Original 80%)</small></td></tr></table>")
    }
    appendLine("</td>")
    
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    // Aligned Columns (Dynamic per Pipeline)
    pipelines.forEach { pipeline ->
        appendLine("<td>")
        val pathRes = photoResult.pathways[pipeline.key]
        val alignedB64 = pathRes?.deskewedBase64 ?: ""
        if (alignedB64.isNotEmpty()) {
            appendLine("<img src='data:image/jpeg;base64,$alignedB64'><br>")
            vRes?.pathResults?.get(pipeline.key)?.alignmentTrace?.let { trace ->
                val s = trace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
                val tx = trace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
                val ty = trace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
                appendLine("<small>Native Warp (Cubic)<br>Scale: %.3f<br>TX: %.1f, TY: %.1f<br>Time: ${trace.timeMs}ms</small>".format(s, tx, ty))
            }
        } else {
            appendLine("<i>Not Aligned</i>")
        }
        appendLine("</td>")
    }
    
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
                    
                    if (step.stageName == "Bin" && step.metadata.containsKey("best_plain_pre")) {
                        val preCleanPlain = step.metadata["best_plain_pre"] ?: ""
                        val preCleanAnnot = step.metadata["best_annotated_pre"] ?: ""
                        val postCleanPlain = step.metadata["best_plain_post"] ?: ""
                        val postCleanAnnot = step.metadata["best_annotated_post"] ?: ""
                        
                        appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br>")
                        appendLine("<b>Pre-Cleaned (Binarized Only):</b><br>")
                        if (preCleanPlain.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,$preCleanPlain'><br>")
                        if (preCleanAnnot.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,$preCleanAnnot'><br>")
                        appendLine("<b>Post-Cleaned (OCR Input):</b><br>")
                        if (postCleanPlain.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,$postCleanPlain'><br>")
                        if (postCleanAnnot.isNotEmpty()) appendLine("<img src='data:image/jpeg;base64,$postCleanAnnot'><br>")
                    } else {
                        appendLine("<div class='ocr-step'><b>${step.stageName}:</b><br>")
                        if (step.thumbB64.isNotEmpty()) {
                            appendLine("<img src='data:image/jpeg;base64,${step.thumbB64}'>")
                        }
                    }
                    
                    if (step.metadata.containsKey("before_hist")) {
                        appendLine("<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${step.metadata["before_hist"]}'><br><small>Before</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${step.metadata["after_hist"]}'><br><small>After</small></td></tr></table>")
                    }

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

private fun generateRunLengthHistogramB64(histStr: String?): String {
    if (histStr.isNullOrEmpty()) return ""
    val counts = histStr.split(",").map { it.toIntOrNull() ?: 0 }
    if (counts.size < 256) return ""
    
    val maxVal = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val bmp = Bitmap.createBitmap(256, 120, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint()
    
    paint.color = android.graphics.Color.BLUE
    for (i in 0..255) {
        val h = (counts[i].toFloat() / maxVal) * 100
        canvas.drawRect(i.toFloat(), 110f - h, (i + 1).toFloat(), 110f, paint)
    }
    
    // Draw base line
    paint.color = android.graphics.Color.BLACK
    paint.strokeWidth = 1f
    canvas.drawLine(0f, 110f, 255f, 110f, paint)

    val b64 = OcrUtils.bitmapToBase64(bmp, 80)
    bmp.recycle()
    return b64
}

private fun generateDualHistogramB64(hHist: IntArray?, vHist: IntArray?): String {
    if (hHist == null || vHist == null || hHist.size < 256 || vHist.size < 256) return ""
    
    val binSize = 2
    val numBins = (256 + binSize - 1) / binSize // 128 bins
    
    val bH = IntArray(numBins)
    val bV = IntArray(numBins)
    for (i in 0..255) {
        bH[i / binSize] += hHist[i]
        bV[i / binSize] += vHist[i]
    }
    
    val maxH = bH.maxOrNull()?.coerceAtLeast(1) ?: 1
    val maxV = bV.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    val bmp = Bitmap.createBitmap(522, 130, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint()
    
    val halfW = 256
    val barW = (256.0f / numBins).coerceAtLeast(1.0f)
    
    // Draw H Hist (Blue)
    paint.color = android.graphics.Color.BLUE
    for (i in 0 until numBins) {
        val h = (bH[i].toFloat() / maxH) * 100
        canvas.drawRect(i * barW, 110f - h, (i + 1) * barW, 110f, paint)
    }
    
    // Draw V Hist (Red)
    paint.color = android.graphics.Color.RED
    for (i in 0 until numBins) {
        val h = (bV[i].toFloat() / maxV) * 100
        canvas.drawRect(halfW + 5 + i * barW, 110f - h, halfW + 5 + (i + 1) * barW, 110f, paint)
    }
    
    // Draw base line and scale tics
    paint.color = android.graphics.Color.BLACK
    paint.strokeWidth = 1f
    canvas.drawLine(0f, 110f, 255f, 110f, paint)
    canvas.drawLine((halfW + 5).toFloat(), 110f, 521f, 110f, paint)
    
    for (i in 0..255 step 25) {
        val isLong = (i % 100 == 0)
        val ticH = if (isLong) 10f else 5f
        canvas.drawLine(i.toFloat(), 110f, i.toFloat(), 110f + ticH, paint)
        canvas.drawLine((halfW + 5 + i).toFloat(), 110f, (halfW + 5 + i).toFloat(), 110f + ticH, paint)
    }

    val b64 = OcrUtils.bitmapToBase64(bmp, 80)
    bmp.recycle()
    return b64
}

private fun createScaledBase64(bitmap: Bitmap, targetWidth: Int, quality: Int, targetBuffer: Bitmap? = null): String {
    if (bitmap.isRecycled) return ""
    val scale = targetWidth.toFloat() / bitmap.width; val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val target = targetBuffer ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888); val targetCanvas = android.graphics.Canvas(target)
    if (targetBuffer != null) targetCanvas.drawColor(android.graphics.Color.BLACK); val matrix = android.graphics.Matrix(); matrix.postScale(scale, scale); targetCanvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    val view = Bitmap.createBitmap(target, 0, 0, targetWidth, targetHeight); val b64 = OcrUtils.bitmapToBase64(view, quality); view.recycle()
    if (targetBuffer == null) target.recycle(); return b64
}

private fun drawCropBoxesOnReference(bmp: Bitmap, vehicle: Vehicle): Bitmap {
    val annotated = bmp.copy(Bitmap.Config.ARGB_8888, true); val canvas = android.graphics.Canvas(annotated); val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f; color = android.graphics.Color.RED }
    val icrsRect = if (vehicle.isIcrs) RectF(vehicle.odometerCropLeft ?: 0f, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f) else IcrsMath.legacyAnisotropicToIcrs(RectF(vehicle.odometerCropLeft ?: 0f, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f), bmp.width, bmp.height)
    val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, bmp.width, bmp.height); val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, bmp.width, bmp.height); canvas.drawRect(p1.x, p1.y, p2.x, p2.y, paint); return annotated
}

private fun getFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {
    if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
    try {
        val root = JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else return emptyList()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0); val isIcrs = obj.optBoolean("is_icrs", false)
            val centerPix = if (isIcrs) IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH) else android.graphics.PointF((cx * imgW).toFloat(), (cy * imgH).toFloat())
            val sE = minOf(imgW, imgH).toDouble(); val pW = if (isIcrs) (w * sE) else (w * imgW); val pH = if (isIcrs) (h * sE) else (h * imgH)
            val inst = if (obj.has("instance")) obj.getInt("instance") else -1; val cT = OdometerOcrUtils.cleanLandmarkString(text)
            list.add(TextBlock(cT, android.graphics.Rect((centerPix.x - pW/2.0).toInt(), (centerPix.y - pH/2.0).toInt(), (centerPix.x + pW/2.0).toInt(), (centerPix.y + pH/2.0).toInt()), instanceId = inst))
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
                    val file = File(targetDir, entry.name); if (entry.isDirectory) file.mkdirs() else { file.parentFile?.mkdirs(); file.outputStream().use { zis.copyTo(it) } }; zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }; true
    } catch (e: Exception) { false }
}

private fun toEvenInt(v: Float): Int = ((v + 1).toInt() / 2) * 2



private fun getHistStats(mat: org.opencv.core.Mat): OdometerOcrUtils.HistStats {
    val hist = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
    val bins = FloatArray(64); hist.get(0, 0, bins)
    val totalPixels = mat.rows() * mat.cols()

    val smoothed = FloatArray(64)
    for (i in 0..63) {
        val start = (i - 2).coerceAtLeast(0)
        val end = (i + 2).coerceAtMost(63)
        smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
    }

    // Low Limit: Climb to first peak, then drop to valley
    var lowPeakIdx = 0
    while (lowPeakIdx < 62 && smoothed[lowPeakIdx + 1] >= smoothed[lowPeakIdx]) lowPeakIdx++
    var lowIdx = lowPeakIdx
    while (lowIdx < 63 && smoothed[lowIdx + 1] <= smoothed[lowIdx]) lowIdx++

    // High Limit: Climb to first peak from right, then drop to valley
    var highPeakIdx = 63
    while (highPeakIdx > 1 && smoothed[highPeakIdx - 1] >= smoothed[highPeakIdx]) highPeakIdx--
    var highIdx = highPeakIdx
    while (highIdx > 0 && smoothed[highIdx - 1] <= smoothed[highIdx]) highIdx--

    val intensityLow = lowIdx * 4.0
    val intensityHigh = highIdx * 4.0
    Log.i("HIST_REFINEMENT", "Limits: Low=%d (Peak=%d), High=%d (Peak=%d)".format(lowIdx, lowPeakIdx, highIdx, highPeakIdx))

    var p80 = 0.0
    var sum = 0.0
    for (i in 0..63) {
        sum += bins[i]
        if (sum >= totalPixels * 0.80) { p80 = i * 4.0; break }
    }
    
    hist.release()
    return OdometerOcrUtils.HistStats(intensityLow, intensityHigh, p80, bins)
}

private fun generateGatedHistogramB64(mat: org.opencv.core.Mat, markers: List<OdometerOcrUtils.HistMarker> = emptyList(), skipEnds: Boolean = false): String {
    val hist = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
    val bins = FloatArray(64); hist.get(0, 0, bins)

    val maxVal = bins.maxOrNull() ?: 1.0f
    val bmp = Bitmap.createBitmap(256, 120, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint()
    
    paint.color = android.graphics.Color.BLACK
    val startBin = if (skipEnds) 1 else 0
    val endBin = if (skipEnds) 62 else 63
    val localMax = if (skipEnds) (startBin..endBin).map { bins[it] }.maxOrNull() ?: 1.0f else maxVal
    for (i in startBin..endBin) {
        val h = (bins[i] / localMax) * 100
        canvas.drawRect(i * 4f, 110f - h, (i + 1) * 4f, 110f, paint)
    }
    
    markers.forEach { marker ->
        paint.color = marker.color
        paint.strokeWidth = 2f
        canvas.drawLine(marker.value.toFloat(), 0f, marker.value.toFloat(), 120f, paint)
    }

    val b64 = OcrUtils.bitmapToBase64(bmp, 80); bmp.recycle(); hist.release(); return b64
}

private suspend fun performLandmarkDiscovery(input: Any, context: Context): Pair<OcrResult, List<TextBlock>> {
    val queryOcrDiscovery = OcrHarness.runDiscovery(input, context)
    val landmarks = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight)
    return Pair(queryOcrDiscovery, landmarks)
}

private fun JSONObject.putSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.putSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }



private suspend fun runPaddleValleyIterative(
    displayName: String, 
    masterBuffer: Any, 
    mWidth: Int, 
    mHeight: Int, 
    winnerRef: ReferenceCache,
    vehicleBufferSets: Map<Int, BufferSet>,
    experimentDetSet512x128: BufferSet,
    experimentRecSet320x48: BufferSet,
    paddleEngine: NativePaddleEngine,
    report: MutableMap<String, OcrHarnessResult>, 
    targetRefMap: MutableMap<String, RefinementTrace>,
    isNumeric: Boolean = false,
    stages: List<String> = listOf("Raw", "80%"),
    extraImages: Map<String, String> = emptyMap(), useCharAware: Boolean = false, pipelineKey: String = ""
) {
    val tH0 = System.currentTimeMillis()
    val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: return
    val htmlOutput = StringBuilder("<b>$displayName:</b><br>")

    val jsonStages = com.google.gson.JsonObject()
    val allOdo = mutableListOf<String>()
    
    val l = winnerRef.vehicle.odometerCropLeft ?: 0f
    val t = winnerRef.vehicle.odometerCropTop ?: 0f
    val r = winnerRef.vehicle.odometerCropRight ?: 1f
    val b = winnerRef.vehicle.odometerCropBottom ?: 1f
    
    val icrsRect = if (winnerRef.vehicle.isIcrs) RectF(l, t, r, b) else IcrsMath.legacyAnisotropicToIcrs(RectF(l, t, r, b), mWidth, mHeight)
    val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, mWidth, mHeight)
    val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, mWidth, mHeight)
    
    val roiW = (p2.x - p1.x).toInt().coerceAtMost(mWidth)
    val roiH = (p2.y - p1.y).toInt().coerceAtMost(mHeight)
    val startX = p1.x.toInt().coerceIn(0, mWidth - 1)
    val startY = p1.y.toInt().coerceIn(0, mHeight - 1)
    
    val stagesList = stages
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stagesList.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        val stageMeta = mutableMapOf<String, String>(); var trialsHtmlStr = ""
        var currentOdoStr = ""
        var currentThumb = ""

        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) {
            OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f)
        } else if (stage == "Hist") {
            val stats = getHistStats(odoBuffer.p.mat)
            val h1 = generateGatedHistogramB64(odoBuffer.p.mat, listOf(OdometerOcrUtils.HistMarker(stats.intensityLow, Color.YELLOW), OdometerOcrUtils.HistMarker(stats.intensityHigh, Color.YELLOW), OdometerOcrUtils.HistMarker(stats.p80, Color.MAGENTA)))
            OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, stats.intensityLow, stats.intensityHigh)
            val alpha = if (stats.intensityHigh > stats.intensityLow) 255.0 / (stats.intensityHigh - stats.intensityLow) else 1.0
            val beta = -stats.intensityLow * alpha
            val h2 = generateGatedHistogramB64(odoBuffer.p.mat, listOf(OdometerOcrUtils.HistMarker(stats.p80 * alpha + beta, Color.CYAN)), skipEnds = true)
            stageMeta["before_hist"] = h1
            stageMeta["after_hist"] = h2
        } else if (stage == "Bin-Trials") {
            val stats = getHistStats(odoBuffer.p.mat)
            val (tHtml, tMeta) = runBinTrialsPaddle(odoBuffer, masterBuffer as BufferSet, winnerRef.vehicle.id, experimentDetSet512x128, experimentRecSet320x48, paddleEngine, stats.rawBins, useCharAware, steps, pipelineKey)
            trialsHtmlStr = tHtml
            stageMeta["trials_html"] = trialsHtmlStr
            stageMeta.putAll(tMeta)
            currentOdoStr = tMeta["best_text"] ?: "---"
            currentThumb = tMeta["best_thumb"] ?: lastThumb
        } else if (stage == "Bin") {
            val binTrialsMeta = steps.find { it.stageName == "Bin-Trials" }?.metadata
            if (binTrialsMeta != null) {
                currentOdoStr = binTrialsMeta["best_text"] ?: "---"
                currentThumb = binTrialsMeta["best_thumb"] ?: lastThumb
                stageMeta.putAll(binTrialsMeta)
            }
        }
        
        if (stage != "Bin" && stage != "Bin-Trials") {
            val detSc = minOf(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
        val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
        val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
        
        val detCropId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[detCropId].mat, experimentDetSet512x128.c[detCropId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes = paddleEngine.detect(experimentDetSet512x128.p, copyHeatmap = false)
        val rawB = if (detRes != null) OdometerOcrUtils.processPaddleHeatmap(detRes.heatmap, detRes.width, detRes.height, detSc, experimentDetSet512x128.p, "Paddle", nativeBoxes = detRes.nativeBoxes) else emptyList<TextBlock>()
        experimentDetSet512x128.c[detCropId].release()
        
        val valleyResults = rawB.map { if (useCharAware) NativeImageUtils.expandByCharacterAwareDiagnostic(odoBuffer.p.mat, it.boundingBox) else NativeImageUtils.expandByValleyDiagnostic(odoBuffer.p.mat, it.boundingBox) }
        val frags = valleyResults.map { it.first }
        
        val cons = OdometerOcrUtils.clusterRects(frags).sortedBy { it.left }
        val odoB = StringBuilder()
        val fBoxes = mutableListOf<android.graphics.Rect>()
        val jMeta = com.google.gson.JsonObject()

        valleyResults.forEachIndexed { vIdx, res -> 
            res.second.forEach { (k, v) -> jMeta.addProperty("${k}_${vIdx}", v) }
        }
        
        cons.forEachIndexed { bIdx, box ->
            val sL = box.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
            val sT = box.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
            val sR = box.right.coerceIn(sL + 1, odoBuffer.p.mat.cols())
            val sB = box.bottom.coerceIn(sT + 1, odoBuffer.p.mat.rows())
            val rSrcId = odoBuffer.createCrop(sL, sT, sR - sL, sB - sT)
            experimentRecSet320x48.p.clear()
            val rSc = minOf(312f / (sR - sL), 40f / (sB - sT))
            val ew = (( (sR - sL) * rSc + 1).toInt() / 2) * 2
            val eh = (( (sB - sT) * rSc + 1).toInt() / 2) * 2
            val rCrId = experimentRecSet320x48.createCrop(4, 4, ew, eh)
            org.opencv.imgproc.Imgproc.resize(odoBuffer.c[rSrcId].mat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
            odoBuffer.c[rSrcId].release()
            experimentRecSet320x48.c[rCrId].release()
            
            val ocrR = paddleEngine.recognizeNumeric(experimentRecSet320x48.p)
            if (ocrR.debugText.isNotBlank()) { odoB.append(ocrR.debugText).append(" "); fBoxes.add(box) }
            ocrR.metadata.forEach { (k, v) -> jMeta.addProperty("${k}_${bIdx}", v) }
            }

            currentOdoStr = odoB.toString().trim()
            val anns = mutableListOf<SnapshotAnnotation>()
            rawB.forEach { b -> anns.add(SnapshotAnnotation(b.boundingBox.left, b.boundingBox.top, b.boundingBox.right, b.boundingBox.bottom, Shape.RECTANGLE, Color.RED, 2)) }
            fBoxes.forEach { b -> anns.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) }

            val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
            currentThumb = sB64
            if (stage == "Raw" || stage.contains("80%")) {
                val (plainB64, _) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, emptyList(), null, NativePaddleEngine.bufferSetA)
                stageMeta["plain_thumb"] = plainB64
                if (useCharAware && valleyResults.isNotEmpty()) {
                    stageMeta["run_hist"] = generateRunLengthHistogramB64(valleyResults[0].second["charaware_run_hist"])
                }
            }
            tSnTotal += ts
            jMeta.entrySet().forEach { e -> stageMeta[e.key] = e.value.asString }
            }
        val tL = System.currentTimeMillis() - tS0
        allOdo.add(currentOdoStr)
        lastThumb = currentThumb

        val plainImg = if (stageMeta.containsKey("plain_thumb")) "<img src='data:image/jpeg;base64,${stageMeta["plain_thumb"]}'><br>" else ""
        val histImg = if (stageMeta.containsKey("run_hist")) "<br><small>Run-Length Histogram:</small><br><img src='data:image/jpeg;base64,${stageMeta["run_hist"]}'>" else ""
        val hT = if (stageMeta.containsKey("before_hist")) {
        "<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${stageMeta["before_hist"]}'><br><small>Before</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${stageMeta["after_hist"]}'><br><small>After</small></td></tr></table>"
        } else ""

        if (stage == "Bin" && stageMeta.containsKey("best_plain_pre")) {
            val preCleanPlain = stageMeta["best_plain_pre"] ?: ""
            val preCleanAnnot = stageMeta["best_annotated_pre"] ?: ""
            val postCleanPlain = stageMeta["best_plain_post"] ?: ""
            val postCleanAnnot = stageMeta["best_annotated_post"] ?: ""
            
            htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br>")
            htmlOutput.append("<b>Pre-Cleaned (Binarized Only):</b><br>")
            if (preCleanPlain.isNotEmpty()) htmlOutput.append("<img src='data:image/jpeg;base64,$preCleanPlain'><br>")
            if (preCleanAnnot.isNotEmpty()) htmlOutput.append("<img src='data:image/jpeg;base64,$preCleanAnnot'><br>")
            htmlOutput.append("<b>Post-Cleaned (OCR Input):</b><br>")
            if (postCleanPlain.isNotEmpty()) htmlOutput.append("<img src='data:image/jpeg;base64,$postCleanPlain'><br>")
            if (postCleanAnnot.isNotEmpty()) htmlOutput.append("<img src='data:image/jpeg;base64,$postCleanAnnot'><br>")
            htmlOutput.append("$histImg$hT${trialsHtmlStr}<br>$currentOdoStr</div>")
        } else {
            val thumbImg = if (lastThumb.isNotEmpty()) "<img src='data:image/jpeg;base64,$lastThumb'>" else ""
            htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br>$plainImg$thumbImg$histImg$hT${trialsHtmlStr}<br>$currentOdoStr</div>")
        }

        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", currentOdoStr)
        sObj.addProperty("time", tL)
        stageMeta.forEach { (k, v) -> 
            if (k != "best_plain_pre" && k != "best_annotated_pre" && k != "best_plain_post" && k != "best_annotated_post") {
                sObj.addProperty(k, v)
            }
        }
        jsonStages.add(stage, sObj)
        steps.add(OcrStepResult(stage, lastThumb, null, currentOdoStr, emptyList(), emptyList(), null, null, stageMeta.toMap()))

    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, OdometerOcrUtils.pickBestOdometer(steps), lastThumb, System.currentTimeMillis() - tH0, tSnTotal, extraImages)
    report[displayName] = result
    targetRefMap[displayName] = RefinementTrace(displayName, System.currentTimeMillis() - tH0, steps)
}

private suspend fun runMLKitIterative(
    displayName: String, 
    masterBuffer: Any, 
    mWidth: Int, 
    mHeight: Int, 
    winnerRef: ReferenceCache,
    vehicleBufferSets: Map<Int, BufferSet>,
    experimentRecSet320x48: BufferSet,
    report: MutableMap<String, OcrHarnessResult>, 
    targetRefMap: MutableMap<String, RefinementTrace>,
    stages: List<String> = listOf("Raw", "80%")
) {
    val tH0 = System.currentTimeMillis()
    val odoBuffer = vehicleBufferSets[winnerRef.vehicle.id] ?: return
    val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
    val jsonStages = com.google.gson.JsonObject()
    val allOdo = mutableListOf<String>()
    
    val l = winnerRef.vehicle.odometerCropLeft ?: 0f
    val t = winnerRef.vehicle.odometerCropTop ?: 0f
    val r = winnerRef.vehicle.odometerCropRight ?: 1f
    val b = winnerRef.vehicle.odometerCropBottom ?: 1f
    
    val icrsRect = if (winnerRef.vehicle.isIcrs) RectF(l, t, r, b) else IcrsMath.legacyAnisotropicToIcrs(RectF(l, t, r, b), mWidth, mHeight)
    val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, mWidth, mHeight)
    val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, mWidth, mHeight)
    
    val roiW = (p2.x - p1.x).toInt().coerceAtMost(mWidth)
    val roiH = (p2.y - p1.y).toInt().coerceAtMost(mHeight)
    val sX = p1.x.toInt().coerceIn(0, mWidth - 1)
    val sY = p1.y.toInt().coerceIn(0, mHeight - 1)
    
    val stagesList = stages
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stagesList.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        val stageMeta = mutableMapOf<String, String>(); var trialsHtmlStr = ""
        var currentOdoStr = ""
        var currentThumb = ""

        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) {
            OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f)
        } else if (stage == "Hist") {
            val stats = getHistStats(odoBuffer.p.mat)
            val h1 = generateGatedHistogramB64(odoBuffer.p.mat, listOf(OdometerOcrUtils.HistMarker(stats.intensityLow, Color.YELLOW), OdometerOcrUtils.HistMarker(stats.intensityHigh, Color.YELLOW), OdometerOcrUtils.HistMarker(stats.p80, Color.MAGENTA)))
            OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, stats.intensityLow, stats.intensityHigh)
            val alpha = if (stats.intensityHigh > stats.intensityLow) 255.0 / (stats.intensityHigh - stats.intensityLow) else 1.0
            val beta = -stats.intensityLow * alpha
            val h2 = generateGatedHistogramB64(odoBuffer.p.mat, listOf(OdometerOcrUtils.HistMarker(stats.p80 * alpha + beta, Color.CYAN)), skipEnds = true)
            stageMeta["before_hist"] = h1
            stageMeta["after_hist"] = h2
        } else if (stage == "Bin-Trials") {
            val stats = getHistStats(odoBuffer.p.mat)
            val (tHtml, tMeta) = runBinTrialsMLKit(odoBuffer, experimentRecSet320x48, stats.rawBins, steps)
            trialsHtmlStr = tHtml
            stageMeta["trials_html"] = trialsHtmlStr
            stageMeta.putAll(tMeta)
        } else if (stage == "Bin") {
            val binTrialsMeta = steps.find { it.stageName == "Bin-Trials" }?.metadata
            if (binTrialsMeta != null) {
                currentOdoStr = binTrialsMeta["best_text"] ?: "---"
                currentThumb = binTrialsMeta["best_thumb"] ?: lastThumb
                stageMeta["selection_logic"] = binTrialsMeta["selection_logic"] ?: "Heuristic"
                if (binTrialsMeta.containsKey("best_threshold")) stageMeta["selected_threshold"] = binTrialsMeta["best_threshold"]!!
            }
        }
        
        if (stage != "Bin") {
            experimentRecSet320x48.p.clear()
            val rSc = minOf(320f / odoBuffer.p.mat.cols(), 48f / odoBuffer.p.mat.rows())
            val ew = ((odoBuffer.p.mat.cols() * rSc + 1).toInt() / 2) * 2
            val eh = ((odoBuffer.p.mat.rows() * rSc + 1).toInt() / 2) * 2
            val rCrId = experimentRecSet320x48.createCrop(0, 0, ew, eh)
            org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentRecSet320x48.c[rCrId].mat, experimentRecSet320x48.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
            
            val img = com.google.mlkit.vision.common.InputImage.fromByteBuffer(experimentRecSet320x48.p.nv21, 320, 48, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21)
            val vText = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await()
            experimentRecSet320x48.c[rCrId].release()
            
            val odoB = StringBuilder()
            vText.textBlocks.forEach { blk -> 
                blk.lines.forEach { line -> 
                    val cleaned = OdometerOcrUtils.clean7SegmentDigits(line.text, Math.abs(line.angle) > 135f).filter { it.isDigit() }
                    if (cleaned.isNotBlank()) odoB.append(cleaned) 
                } 
            }
            currentOdoStr = odoB.toString()
            
            val anns = mutableListOf<SnapshotAnnotation>()
            val snX = odoBuffer.p.mat.cols().toFloat() / ew.toFloat()
            val snY = odoBuffer.p.mat.rows().toFloat() / eh.toFloat()
            vText.textBlocks.forEach { b -> 
                b.boundingBox?.let { anns.add(SnapshotAnnotation((it.left * snX).toInt(), (it.top * snY).toInt(), (it.right * snX).toInt(), (it.bottom * snY).toInt(), Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) } 
            }
            
            val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
            currentThumb = sB64
            tSnTotal += ts
        }

        val tL = System.currentTimeMillis() - tS0
        allOdo.add(currentOdoStr)
        lastThumb = currentThumb
        
        val hT = if (stageMeta.containsKey("before_hist")) {
            "<table style='width:100%; border:none;'><tr style='border:none;'><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${stageMeta["before_hist"]}'><br><small>Before</small></td><td style='border:none; padding:1px;'><img src='data:image/jpeg;base64,${stageMeta["after_hist"]}'><br><small>After</small></td></tr></table>"
        } else ""
        
        htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br><img src='data:image/jpeg;base64,$lastThumb'>$hT${trialsHtmlStr}<br>$currentOdoStr</div>")
        
        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", currentOdoStr)
        sObj.addProperty("time", tL)
        stageMeta.forEach { (k, v) -> sObj.addProperty(k, v) }
        jsonStages.add(stage, sObj)
        steps.add(OcrStepResult(stage, lastThumb, null, currentOdoStr, emptyList(), emptyList(), null, null, stageMeta.toMap()))
    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, OdometerOcrUtils.pickBestOdometer(steps), lastThumb, System.currentTimeMillis() - tH0, tSnTotal)
    report[displayName] = result
    targetRefMap[displayName] = RefinementTrace(displayName, System.currentTimeMillis() - tH0, steps)
}
