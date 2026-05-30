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

private suspend fun runExperiment(
    experimentDir: File, 
    reportDir: File, 
    debugCropDir: File, 
    vehicles: List<Vehicle>, 
    context: Context, 
    onLog: (String) -> Unit, 
    subsetNames: List<String>?, 
    onProgress: (PhotoResultSummary, Float) -> Unit
) = withContext(Dispatchers.IO) {
    val allPhotos = experimentDir.listFiles { f -> 
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng") 
    }?.sortedBy { it.name } ?: return@withContext
    
    val photos = if (subsetNames != null) {
        allPhotos.filter { it.name in subsetNames }
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
    
    var partCount = 1
    val maxSizeBytes = 2 * 1024 * 1024 // 2MB parts
    var currentSize = 0
    val footer = "</table></body></html>"
    val experimentRecSet320x48 = BufferSet(320, 48)
    val experimentDetSet512x128 = BufferSet(512, 128)
    val harnessEngineNames = mutableListOf(
        "Set A ML", "Set A Paddle", 
        "Set B ML", "Set B Paddle"
    )

    fun startNewFile(): File {
        val f = File(reportDir, "alignment_report_${timestamp}_part${partCount++}.html")
        f.writeText(buildHtmlHeader(timestamp, total, BuildConfig.VERSION_NAME, emptyList(), harnessEngineNames))
        return f
    }

    var currentFile = startNewFile()
    
    photos.forEachIndexed { index, file ->
        // Phase 116 Emergency Fix: Initialize photoResult early with "No Match" state
        // to prevent serializePhotoResultToJson crashes on failed identification.
        var photoResult: ProcessedPhotoResult? = ProcessedPhotoResult(file.name, emptyMap(), emptyMap(), emptyMap())
        var finalWinnerName = "No match"
        var bestOdometer = "FAILED"
        try {
            withContext(Dispatchers.Main) { onLog("Processing ${index + 1}/$total: ${file.name}") }
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
                // Step 2 (Deskew): Calculate tilt independently for both pipelines
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

                // Path A: ML Kit Deskew
                val angleA = deskewResA.engines["ML Kit"]?.angle ?: 0f
                val tRotateA = rotate(NativePaddleEngine.bufferSetA, angleA)
                
                // Path B: Paddle Deskew
                val angleB = deskewResA.engines["Paddle V3"]?.angle ?: 0f
                val tRotateB = rotate(NativePaddleEngine.bufferSetB, angleB)

                // Path A Discovery
                val tDisc0A = System.currentTimeMillis()
                val (ocrA, queryLandmarksA) = performLandmarkDiscovery(NativePaddleEngine.bufferSetA.p, context)
                val tDiscoveryTotalA = System.currentTimeMillis() - tDisc0A
                
                // Path B Discovery
                val tDisc0B = System.currentTimeMillis()
                val (ocrB, queryLandmarksB) = performLandmarkDiscovery(NativePaddleEngine.bufferSetB.p, context)
                val tDiscoveryTotalB = System.currentTimeMillis() - tDisc0B
                
                val primaryVetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarksA, cachedRefs.map { it.vehicle }, "ML Kit")
                // Phase 116: We primarily use the Standard path for identification (Winner selection)
                // but discovery is run independently for refinement downstream.
                val vehicleResultsMap = mutableMapOf<Int, SingleVehicleResult>()
                val harnessResultsMap = mutableMapOf<String, OcrHarnessResult>()

                // Identification Pass: Find the winning vehicle
                val winnerId = primaryVetoResults.entries.find { !it.value.isVetoed }?.key
                val winnerRef = cachedRefs.find { it.vehicle.id == winnerId }
                
                var alignedBase64 = ""
                var alignedA64 = ""
                var alignedB64 = ""
                var tSnapAlign = 0L

                val hStd = mutableMapOf<String, OcrHarnessResult>()
                val hA = mutableMapOf<String, OcrHarnessResult>()
                val hB = mutableMapOf<String, OcrHarnessResult>()
                
                val refinementTraces = mutableMapOf<String, RefinementTrace>()
                val refinementTracesA = mutableMapOf<String, RefinementTrace>()
                val refinementTracesB = mutableMapOf<String, RefinementTrace>()

                // Winner-Only Processing block
                
                if (winnerRef != null) {
                    finalWinnerName = winnerRef.vehicle.name

                    Log.d("DISAMB_TRACE", "--- Processing Winner: $finalWinnerName for ${file.name} ---")
                    
                    // Phase 116: Independent Disambiguation for A and B
                    val queryLandmarksAPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksA, winnerRef.curatedLandmarks)
                    val queryLandmarksBPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarksB, winnerRef.curatedLandmarks)
                    
                    // 1.2 Alignment A (In-place on bufferSetA)
                    val alignResA = ImageAlignmentUtils.anchorAlign(
                        NativePaddleEngine.bufferSetA, 
                        winnerRef.curatedLandmarks, 
                        queryLandmarksAPrimary, 
                        winnerRef.vehicle, 
                        winnerRef.width, 
                        winnerRef.height, 
                        imgW, 
                        imgH, 
                        null
                    )
                    
                    // Capture Aligned Thumbnail A
                    val (snapA, tSnapA) = if (alignResA.success) { 
                        OcrUtils.takeSnapshot(
                            source = NativePaddleEngine.bufferSetA.p, 
                            sourceRect = null, 
                            targetW = 600, 
                            targetH = 450, 
                            annotations = emptyList(),
                            scratchArgb = null,
                            scratchYuv = NativePaddleEngine.bufferSetA
                        ) 
                    } else Pair("", 0L)
                    alignedA64 = snapA

                    // 1.3 Alignment B (In-place on bufferSetB)
                    val alignResB = ImageAlignmentUtils.anchorAlign(
                        NativePaddleEngine.bufferSetB, 
                        winnerRef.curatedLandmarks, 
                        queryLandmarksBPrimary, 
                        winnerRef.vehicle, 
                        winnerRef.width, 
                        winnerRef.height, 
                        imgW, 
                        imgH, 
                        null
                    )
                    
                    // Capture Aligned Thumbnail B
                    val (snapB, tSnapB) = if (alignResB.success) { 
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
                    alignedB64 = snapB

                    tSnapAlign = tSnapA + tSnapB

                    val alignmentTraceA = AlignmentTraceResult(alignResA.success, alignResA.timeMs, alignedA64, alignResA.metadata)
                    val alignmentTraceB = AlignmentTraceResult(alignResB.success, alignResB.timeMs, alignedB64, alignResB.metadata)
                    
                    // Phase 58: Refinement Loop (Always executed to provide diagnostic data)
                    if (winnerRef.vehicle.id >= 0) {
                        // Diagnostic High-Quality Crop (Save to disk using native snapshot)
                        val l = winnerRef.vehicle.odometerCropLeft ?: 0f
                        val t = winnerRef.vehicle.odometerCropTop ?: 0f
                        val r = winnerRef.vehicle.odometerCropRight ?: 1f
                        val b = winnerRef.vehicle.odometerCropBottom ?: 1f
                        
                        val icrsRect = if (winnerRef.vehicle.isIcrs) RectF(l, t, r, b) else IcrsMath.legacyAnisotropicToIcrs(RectF(l, t, r, b), imgW, imgH)
                        val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, imgW, imgH)
                        val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, imgW, imgH)
                        val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                        
                        val (cropB64, _) = OcrUtils.takeSnapshot(
                            source = NativePaddleEngine.bufferSetA.p,
                            sourceRect = roi,
                            targetW = 320,
                            targetH = 48,
                            scratchArgb = null,
                            scratchYuv = NativePaddleEngine.bufferSetA
                        )
                        
                        val cropFile = File(debugCropDir, "crop_${file.name.replace(".dng", ".jpg")}")
                        try { cropFile.outputStream().use { out -> out.write(android.util.Base64.decode(cropB64, android.util.Base64.NO_WRAP)) } } catch (e: Exception) { Log.e(TAG, "Failed to save crop", e) }

                        // --- Sequential Execution (Phase 116 Restoration & Fixes) ---
                        // Path A
                        runMLKitIterative("Set A ML", NativePaddleEngine.bufferSetA, imgW, imgH, winnerRef, vehicleBufferSets, experimentRecSet320x48, hA, refinementTracesA)
                        runPaddleValleyIterative("Set A Paddle", NativePaddleEngine.bufferSetA, imgW, imgH, winnerRef, vehicleBufferSets, experimentDetSet512x128, experimentRecSet320x48, paddleEngine, hA, refinementTracesA, isNumeric = true)

                        // Path B
                        runMLKitIterative("Set B ML", NativePaddleEngine.bufferSetB, imgW, imgH, winnerRef, vehicleBufferSets, experimentRecSet320x48, hB, refinementTracesB)
                        runPaddleValleyIterative("Set B Paddle", NativePaddleEngine.bufferSetB, imgW, imgH, winnerRef, vehicleBufferSets, experimentDetSet512x128, experimentRecSet320x48, paddleEngine, hB, refinementTracesB, isNumeric = true)
                    }
                    
                    val allResults = refinementTracesA.values.flatMap { it.steps }.mapNotNull { it.text }.filter { it.isNotBlank() }
                    if (allResults.isNotEmpty()) bestOdometer = allResults.groupBy { it }.mapValues { it.value.size }.maxByOrNull { it.value }?.key ?: "FAILED"

                    // Reporting Pass: Store result for winner (Phase 116 Dual Paths)
                    val setAPath = SingleVehiclePathwayResult(alignmentTraceA, refinementTracesA, hA)
                    val setBPath = SingleVehiclePathwayResult(alignmentTraceB, refinementTracesB, hB)
                    
                    vehicleResultsMap[winnerRef.vehicle.id] = SingleVehicleResult(
                        winnerRef.vehicle.name, 
                        "", 
                        0L, 
                        0L, 
                        mapOf("set_a" to setAPath, "set_b" to setBPath),
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
                            emptyMap(),
                            veto.queryWords,
                            veto.myManifest.toList(),
                            veto.vetoPool.toList(),
                            false
                        )
                    }
                }

                // Photo-level Pathway Results (Phase 116)
                val setAPhotoPath = PhotoPathwayResult(finalWinnerName, bestOdometer, (deskewResA.mlTimeMs), tDiscoveryTotalA, alignedA64, ocrA, queryLandmarksA, hA)
                val setBPhotoPath = PhotoPathwayResult(finalWinnerName, bestOdometer, (deskewResA.paddleTimeMs), tDiscoveryTotalB, alignedB64, ocrB, queryLandmarksB, hB)
                
                val pathways = mapOf("set_a" to setAPhotoPath, "set_b" to setBPhotoPath)
                photoResult = ProcessedPhotoResult(file.name, pathways, vehicleResultsMap, primaryVetoResults)

                val rowHtml = buildHtmlRowDynamic(
                    index + 1, file.name, imgW, imgH, meta.isDegraded, originalBase64, 
                    alignedA64, alignedB64, ocrA.debugText, 
                    vehicleResultsMap, cachedRefs, finalWinnerName, emptyList(), 
                    harnessEngineNames, (tMl + tPd), (tDiscoveryTotalA + tDiscoveryTotalB), 
                    tilt, deskewResA, meta.diagnostic
                )

                if (currentSize + rowHtml.length > maxSizeBytes) { currentFile.appendText(footer); currentFile = startNewFile(); currentSize = 0 }
                currentFile.appendText(rowHtml); currentSize += rowHtml.length

                val photoJson = serializePhotoResultToJson(
                    index + 1, imgW, imgH, imgW, imgH, meta.isDegraded, 
                    meta.diagnostic, photoResult!!, vehicles, deskewResA, tSnapOrig, tSnapAlign
                )
                val comma = if (index < total - 1) "," else ""
                jsonFile.appendText(photoJson.toString(2) + "$comma\n")
                
                val resultSummary = PhotoResultSummary(file.name, finalWinnerName, 1.0f, bestOdometer)

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
        put("odometer", photoResult.pathways["set_a"]?.bestOdometer ?: "FAILED")
        
        // Deskew Data (Source from Path A)
        val deskewObj = JSONObject()
        deskewObj.putSafe("angle_a", (deskewResA?.angle ?: 0f).toDouble())
        
        // Add A/B Parity Checksums (Phase 117 Patch 3)
        deskewResA?.engines?.get("Paddle V3")?.metadata?.forEach { (k, v) -> 
            if (k.contains("chk") || k.contains("count")) deskewObj.put(k, v)
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
                put("is_icrs", true)
            })
        }
        put("deskew_data_paddle", pdArray)

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

private fun buildHtmlHeader(time: String, total: Int, version: String, strategies: List<String>, harnessEngines: List<String>): String = buildString {
    appendLine("<html><head><title>Deep Trace - $time</title>")
    appendLine("<style>table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 24px; table-layout: fixed; } th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; word-wrap: break-word; overflow: hidden; } img { max-width: 100%; height: auto; border: 1px solid #eee; margin-bottom: 2px; } .ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }</style></head><body>")
    appendLine("<h1>OCR Refinement Experiment</h1><p><b>Run:</b> $time | <b>Version:</b> $version | <b>Total:</b> $total</p><table><tr><th style='width:375px;'># & Original</th><th style='width:650px;'>Native Aligned A</th><th style='width:650px;'>Native Aligned B</th>")
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
    alignedA64: String,
    alignedB64: String,
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
    val angMl = deskewRes.mlAngle
    val angV3 = deskewRes.engines["Paddle V3"]?.angle ?: 0f
    val angCpp = deskewRes.paddleCppAngle
    appendLine("<tr><td><b>#$rowIndex</b>")
    appendLine("<br><small>$fileName</small>")
    appendLine("<br><small>$resHtml</small>$diagHtml")
    appendLine("<br><b>Deskew:</b> ${tDeskew}ms<br><b>Discover:</b> ${tDiscovery}ms")
    appendLine("<br>ML: ${"%.1f".format(angMl)}&deg; | V3: ${"%.1f".format(angV3)}&deg; | CPP: ${"%.1f".format(angCpp)}&deg;")
    appendLine("<br><img src='data:image/jpeg;base64,$originalBase64'></td>")
    
    val winnerRef = cachedRefs.find { it.vehicle.name == winnerName }; val vRes = winnerRef?.let { vehicleResults[it.vehicle.id] }
    
    // Aligned Column (Set A Path)
    appendLine("<td>")
    if (alignedA64.isNotEmpty()) {
        appendLine("<img src='data:image/jpeg;base64,$alignedA64'><br>")
        vRes?.pathResults?.get("set_a")?.alignmentTrace?.let { trace ->
            val s = trace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val tx = trace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val ty = trace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            appendLine("<small>Native Warp (Cubic)<br>Scale: %.3f<br>TX: %.1f, TY: %.1f<br>Time: ${trace.timeMs}ms</small>".format(s, tx, ty))
        }
    } else {
        appendLine("<i>Not Aligned</i>")
    }
    appendLine("</td>")

    // Aligned Column (Set B Path)
    appendLine("<td>")
    if (alignedB64.isNotEmpty()) {
        appendLine("<img src='data:image/jpeg;base64,$alignedB64'><br>")
        vRes?.pathResults?.get("set_b")?.alignmentTrace?.let { trace ->
            val s = trace.metadata["raw_scale"]?.toDoubleOrNull() ?: 0.0
            val tx = trace.metadata["raw_tx"]?.toDoubleOrNull() ?: 0.0
            val ty = trace.metadata["raw_ty"]?.toDoubleOrNull() ?: 0.0
            appendLine("<small>Native Warp (Cubic)<br>Scale: %.3f<br>TX: %.1f, TY: %.1f<br>Time: ${trace.timeMs}ms</small>".format(s, tx, ty))
        }
    } else {
        appendLine("<i>Not Aligned</i>")
    }
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

private suspend fun performLandmarkDiscovery(input: Any, context: Context): Pair<OcrResult, List<TextBlock>> {
    val queryOcrDiscovery = OcrHarness.runDiscovery(input, context)
    val landmarks = OdometerOcrUtils.processRawLandmarks(queryOcrDiscovery.textBlocks, null, null, queryOcrDiscovery.imageWidth, queryOcrDiscovery.imageHeight)
    return Pair(queryOcrDiscovery, landmarks)
}

private fun JSONObject.putSafe(key: String, value: Double, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }
private fun JSONObject.putSafe(key: String, value: Float, context: String = ""): JSONObject { return if (value.isFinite()) this.put(key, value) else { Log.e("ExperimentAlignment", "NON-FINITE value [$value] for key [$key] in $context"); this.put(key, "ERR: $value") } }

private fun clusterRects(fragments: List<android.graphics.Rect>): List<android.graphics.Rect> {
    val clusters = mutableListOf<MutableList<android.graphics.Rect>>()
    for (frag in fragments) {
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
    return clusters.map { cluster -> 
        android.graphics.Rect(cluster.minOf { it.left }, cluster.minOf { it.top }, cluster.maxOf { it.right }, cluster.maxOf { it.bottom }) 
    }
}

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
    isNumeric: Boolean = false
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
    
    val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stages.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f) 
        else if (stage.contains("78%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.78f)
        
        val detSc = minOf(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
        val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
        val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
        
        val detCropId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
        org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[detCropId].mat, experimentDetSet512x128.c[detCropId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
        val detRes = paddleEngine.detect(experimentDetSet512x128.p)
        val rawB = if (detRes != null) OdometerOcrUtils.processPaddleHeatmap(detRes.heatmap, detRes.width, detRes.height, detSc, odoBuffer.p.mat, "Paddle") else emptyList<TextBlock>()
        experimentDetSet512x128.c[detCropId].release()
        
        val frags = rawB.map { NativeImageUtils.expandByValley(odoBuffer.p.mat, it.boundingBox) }
        val cons = clusterRects(frags).sortedBy { it.left }
        val odoB = StringBuilder()
        val fBoxes = mutableListOf<android.graphics.Rect>()
        val jMeta = com.google.gson.JsonObject()
        
        cons.forEach { box ->
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
            ocrR.metadata.forEach { (k, v) -> jMeta.addProperty(k, v) }
        }
        
        val odoStr = odoB.toString().trim()
        allOdo.add(odoStr)
        val tL = System.currentTimeMillis() - tS0
        steps.add(OcrStepResult(stage, "", null, odoStr, emptyList(), emptyList(), null, null, jMeta.asMap().mapValues { it.value.asString }))
        
        val anns = mutableListOf<SnapshotAnnotation>()
        rawB.forEach { b -> anns.add(SnapshotAnnotation(b.boundingBox.left, b.boundingBox.top, b.boundingBox.right, b.boundingBox.bottom, Shape.RECTANGLE, Color.RED, 2)) }
        fBoxes.forEach { b -> anns.add(SnapshotAnnotation(b.left, b.top, b.right, b.bottom, Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) }
        
        val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
        lastThumb = sB64
        tSnTotal += ts
        htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br><img src='data:image/jpeg;base64,$lastThumb'><br>$odoStr</div>")
        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", odoStr)
        sObj.addProperty("time", tL)
        jMeta.entrySet().forEach { e -> sObj.add(e.key, e.value) }
        jsonStages.add(stage, sObj)
    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, allOdo.firstOrNull { it.isNotBlank() }, lastThumb, System.currentTimeMillis() - tH0, tSnTotal)
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
    targetRefMap: MutableMap<String, RefinementTrace>
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
    
    val stages = listOf("Raw", "80% Stretch Only", "78% Stretch")
    var lastThumb = ""
    var tSnTotal = 0L
    val steps = mutableListOf<OcrStepResult>()
    
    stages.forEach { stage ->
        val tS0 = System.currentTimeMillis()
        when (masterBuffer) {
            is BufferSet -> {
                odoBuffer.p.clear()
                val interp = if (masterBuffer.c[winnerRef.vehicle.id].mat.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
                org.opencv.imgproc.Imgproc.resize(masterBuffer.c[winnerRef.vehicle.id].mat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            }
        }
        
        if (stage.contains("80%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.80f) 
        else if (stage.contains("78%")) OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.78f)
        
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
        
        val odoStr = odoB.toString()
        allOdo.add(odoStr)
        val tL = System.currentTimeMillis() - tS0
        steps.add(OcrStepResult(stage, "", null, odoStr, emptyList(), emptyList(), null, null, emptyMap()))
        
        val anns = mutableListOf<SnapshotAnnotation>()
        val snX = odoBuffer.p.mat.cols().toFloat() / ew.toFloat()
        val snY = odoBuffer.p.mat.rows().toFloat() / eh.toFloat()
        vText.textBlocks.forEach { b -> 
            b.boundingBox?.let { anns.add(SnapshotAnnotation((it.left * snX).toInt(), (it.top * snY).toInt(), (it.right * snX).toInt(), (it.bottom * snY).toInt(), Shape.RECTANGLE, Color.rgb(255, 165, 0), 2)) } 
        }
        
        val (sB64, ts) = OcrUtils.takeSnapshot(odoBuffer.p, null, 320, 48, anns, null, NativePaddleEngine.bufferSetA)
        lastThumb = sB64
        tSnTotal += ts
        htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tL ms)<br><img src='data:image/jpeg;base64,$lastThumb'><br>$odoStr</div>")
        val sObj = com.google.gson.JsonObject()
        sObj.addProperty("text", odoStr)
        sObj.addProperty("time", tL)
        jsonStages.add(stage, sObj)
    }
    
    val result = OcrHarnessResult(displayName, htmlOutput.toString(), com.google.gson.JsonObject().apply { add("stages", jsonStages) }, allOdo.firstOrNull { it.isNotBlank() }, lastThumb, System.currentTimeMillis() - tH0, tSnTotal)
    report[displayName] = result
    targetRefMap[displayName] = RefinementTrace(displayName, System.currentTimeMillis() - tH0, steps)
}
