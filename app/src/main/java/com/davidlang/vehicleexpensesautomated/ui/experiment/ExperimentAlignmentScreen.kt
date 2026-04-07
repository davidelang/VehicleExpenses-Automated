package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

private const val AMAZON_PHOTOS_LINK = "https://www.amazon.com/photos/shared/81xh078qSgydiVwUH9VWBw.EcItxhL_TTM9KNvR0akUC0"
private const val TAG = "ExperimentAlignment"
private const val PLACEHOLDER_BASE64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/4gHYSUNDX1BST0ZJTEUAAQEAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAEAAAAAmZmAADypwAADVkAABPQAAAKWwAAAAAAAAAAbWx1YwAAAAAAAAABAAAADGVuVVMAAAAgAAAAHABHAG8AbwBnAGwAZQAgAEkAbgBjAC4AIAAyADAAMQA2/9sAQwAQCwwODAoQDg0OEhEQExgoGhgWFhgxIyUdKDozPTw5Mzg3QEhcTkBEV0U3OFBtUVdfYmdoZz5NcXlwZHhcZWdj/8AACwgACgAOAQERAP/EABUAAQEAAAAAAAAAAAAAAAAAAAIG/8QAGREBAQEBAQAAAAAAAAAAAAAAACERAQH/4gAgTVBGAE1NACoAAAAIAAGwAAAHAAAABDAxMDAAAAAA/9oACAEBAAA/AMLx6QmsoA8bqyd82tjpPLNjX4MlFUA9FKiv/9k="

@Composable
fun ExperimentAlignmentScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val experimentDir = File(context.filesDir, "experiment_photos")
    val reportDir = File(context.filesDir, "experiment_reports").apply { mkdirs() }
    val debugCropDir = File(context.filesDir, "experiment_debug_crops").apply { mkdirs() }
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
                status = if (success) "ZIP extracted successfully!" else "Failed to extract ZIP"
            }
        }
    }
    var pickedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var cleanedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var isCleaning by remember { mutableStateOf(false) }
    var showDebugScreen by remember { mutableStateOf(false) }
    val debugSteps = remember { mutableStateListOf<CleaningDebugStep>() }

    LaunchedEffect(pickedPhotoUrl) {
        pickedPhotoUrl?.let { url ->
            isCleaning = true
            try {
                val bmp = BitmapFactory.decodeFile(url) ?: return@let
                val (cleanedBmp, _) = ImageAlignmentUtils.createCleanedReference(bmp)
                if (cleanedBmp != null) {
                    val tempFile = File(context.cacheDir, "temp_cleaned_${System.currentTimeMillis()}.jpg")
                    val out = java.io.FileOutputStream(tempFile)
                    cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    cleanedPhotoUrl = tempFile.absolutePath
                    cleanedBmp.recycle()
                    Log.i("Experiment", "Cleaned image ready for alignment test")
                }
            } catch (e: Exception) {
                Log.e("Experiment", "Cleaning failed", e)
            } finally {
                isCleaning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!experimentDir.exists()) experimentDir.mkdirs()
        val count = experimentDir.listFiles()?.size ?: 0
        status = if (count == 0) "Folder is empty.\nUse the buttons below." else "$count photos ready."
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Alignment Experiment") }) }) { padding ->
        if (showDebugScreen) {
            DebugCleaningPipelineScreen(
                steps = debugSteps,
                onClose = { showDebugScreen = false }
            )
        } else {
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
                        status = "Starting alignment test..."
                        scope.launch {
                            try {
                                val result = runFullExperiment(vehicles, experimentDir, debugCropDir, viewModel, context) { p, name ->
                                    progress = p
                                    currentPhoto = name
                                }
                                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                                val htmlFiles = writeSizeSplitHtmlReports(result.htmlReport, reportDir, timestamp, maxSizeKB = 300)
                                reportPath = htmlFiles.firstOrNull()?.absolutePath
                                status = "Test complete!\n${result.summary}\n${htmlFiles.size} report files written"
                                Log.i(TAG, "Reports written: ${htmlFiles.size} files")
                            } catch (e: Exception) {
                                status = "Report generation failed: ${e.message}"
                                Log.e(TAG, "Report generation failed", e)
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    enabled = !isRunning && experimentDir.listFiles()?.isNotEmpty() == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRunning) "Running..." else "Run Alignment Experiment Now")
                }
                Button(
                    onClick = {
                        val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") } ?: emptyList()
                        if (photos.isNotEmpty()) {
                            scope.launch {
                                Log.i(TAG, "Debug button clicked - starting pipeline")
                                debugSteps.clear()
                                showDebugScreen = true
                                val firstPhoto = photos.first()
                                val originalBitmap = BitmapFactory.decodeFile(firstPhoto.absolutePath) ?: return@launch
                                debugCleaningPipeline(originalBitmap) { step ->
                                    debugSteps.add(step)
                                    Log.i(TAG, "Step completed: ${step.description} → Text: ${step.ocrText}")
                                }
                                Log.i(TAG, "Debug pipeline completed with ${debugSteps.size} steps")
                            }
                        } else {
                            Toast.makeText(context, "No photos in experiment folder", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Debug Cleaning Pipeline (first photo)")
                }
                if (reportPath != null) {
                    Button(onClick = { Toast.makeText(context, "Reports written to: $reportPath (and siblings)", Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Latest Reports")
                    }
                }
                Button(onClick = { navController?.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Quick Fill-up")
                }
            }
        }
    }
}

@Composable
fun DebugCleaningPipelineScreen(
    steps: List<CleaningDebugStep>,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleaning Pipeline Debug") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(steps) { step ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.description, style = MaterialTheme.typography.titleMedium)
                            Image(
                                bitmap = step.image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Tesseract text:", style = MaterialTheme.typography.titleSmall)
                            Text(step.ocrText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

data class CleaningDebugStep(
    val description: String,
    val image: Bitmap,
    val ocrText: String
)

private suspend fun debugCleaningPipeline(original: Bitmap, onStep: (CleaningDebugStep) -> Unit) = withContext(Dispatchers.IO) {
    try {
        val (rawText, rawBlocks) = OdometerOcrUtils.runRawOcr(original)
        val rawAnnotated = OdometerOcrUtils.annotateImageWithBoxes(original, rawBlocks)
        onStep(CleaningDebugStep("Raw original", rawAnnotated, rawText))

        val grayMat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, grayMat)
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        val grayBmp = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(grayMat, grayBmp)
        val (grayText, grayBlocks) = OdometerOcrUtils.runRawOcr(grayBmp)
        val grayAnnotated = OdometerOcrUtils.annotateImageWithBoxes(grayBmp, grayBlocks)
        onStep(CleaningDebugStep("Grayscale", grayAnnotated, grayText))

        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(grayMat, enhanced)
        val enhancedBmp = Bitmap.createBitmap(enhanced.cols(), enhanced.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(enhanced, enhancedBmp)
        val (claheText, claheBlocks) = OdometerOcrUtils.runRawOcr(enhancedBmp)
        val claheAnnotated = OdometerOcrUtils.annotateImageWithBoxes(enhancedBmp, claheBlocks)
        onStep(CleaningDebugStep("CLAHE enhanced", claheAnnotated, claheText))

        val (cleanedBmp, _) = ImageAlignmentUtils.createCleanedReference(original)
        if (cleanedBmp != null) {
            onStep(CleaningDebugStep("Final cleaned", cleanedBmp.copy(Bitmap.Config.ARGB_8888, true), "N/A"))
        }

        grayMat.release()
        enhanced.release()
        grayBmp.recycle()
        enhancedBmp.recycle()
    } catch (e: Exception) {
        Log.e(TAG, "Debug pipeline crashed", e)
        onStep(CleaningDebugStep("Pipeline crashed: ${e.message}", original.copy(Bitmap.Config.ARGB_8888, true), "N/A"))
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
        Log.e(TAG, "ZIP extraction failed", e)
        false
    }
}

private suspend fun runFullExperiment(
    vehicles: List<Vehicle>,
    experimentDir: File,
    debugCropDir: File,
    viewModel: VehicleViewModel,
    context: android.content.Context,
    onProgress: (Float, String) -> Unit
): ExperimentResult {
    val photos = experimentDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") && !it.name.contains("pump", true) && !it.name.contains("receipt", true) } ?: emptyList()
    val total = photos.size
    if (total == 0) return ExperimentResult("No photos found", "<h1>No photos</h1>")
    val results = mutableListOf<PhotoResult>()
    var success = 0
    
    photos.forEachIndexed { index, file ->
        onProgress((index.toFloat() / total), "Processing ${file.name} (${index+1}/$total)")
        try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            val (cleanedBmp, dashTextBlocks) = ImageAlignmentUtils.createCleanedReference(originalBitmap)
            val queryOcr = OdometerOcrUtils.extractFromPhoto(file.absolutePath)
            
            val vehicleMatchResults = mutableListOf<VehicleMatchResult>()
            
            vehicles.forEach { vehicle ->
                val refUrl = vehicle.referenceDashPhotoUrl ?: vehicle.cleanedReferenceDashPhotoUrl
                if (refUrl == null) return@forEach
                val refFile = File(refUrl)
                if (!refFile.exists()) return@forEach
                val refBmp = BitmapFactory.decodeFile(refFile.absolutePath) ?: return@forEach
                
                val odometerCrop = vehicle.odometerCropLeft?.let {
                    android.graphics.RectF(it, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f)
                }
                val otherTextCrop = vehicle.otherTextCropLeft?.let {
                    android.graphics.RectF(it, vehicle.otherTextCropTop ?: 0f, vehicle.otherTextCropRight ?: 1f, vehicle.otherTextCropBottom ?: 1f)
                }
                
                val refOcr = OdometerOcrUtils.extractFromPhoto(refFile.absolutePath)
                val allResults = ImageAlignmentUtils.matchWithAllMethods(refBmp, originalBitmap, refOcr, queryOcr, odometerCrop, otherTextCrop)
                
                // Consensus result for this specific vehicle
                val consensusRes = allResults["consensus"] ?: allResults["feature"]!!
                val featureRes = allResults["feature"]!!
                
                val inliersMatch = Regex("with (\\d+) inliers").find(featureRes.message ?: "")
                val inliers = inliersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                
                // Store the result including the aligned image for THIS vehicle
                vehicleMatchResults.add(VehicleMatchResult(
                    vehicleName = vehicle.name,
                    score = consensusRes.confidence,
                    inliers = inliers,
                    message = consensusRes.message,
                    referenceBase64 = bitmapToBase64(drawCropBoxesOnReference(refBmp, vehicle), 200),
                    alignedBase64 = if (featureRes.alignedImage != null) bitmapToBase64(featureRes.alignedImage, 200) else "",
                    methodScores = allResults.mapValues { it.value.confidence }
                ))
            }
            
            val winner = vehicleMatchResults.maxByOrNull { it.score }
            val matchedVehicleName = winner?.vehicleName ?: "No match"
            val matchedConfidence = winner?.score ?: 0f
            
            var extractedOdometer: String? = null
            var odometerCropBase64 = ""
            var referenceTextBlocks = ""

            if (winner != null && winner.vehicleName != "No match") {
                val matchedVehicle = vehicles.find { it.name == winner.vehicleName }
                if (matchedVehicle != null) {
                    referenceTextBlocks = matchedVehicle.referenceTextBlocks ?: ""
                    
                    // Re-run JUST the winner's alignment to get the bitmap for OCR
                    val refUrl = matchedVehicle.referenceDashPhotoUrl ?: matchedVehicle.cleanedReferenceDashPhotoUrl
                    if (refUrl != null) {
                        val refBmp = BitmapFactory.decodeFile(refUrl)
                        if (refBmp != null) {
                            val odometerCrop = matchedVehicle.odometerCropLeft?.let {
                                android.graphics.RectF(it, matchedVehicle.odometerCropTop ?: 0f, matchedVehicle.odometerCropRight ?: 1f, matchedVehicle.odometerCropBottom ?: 1f)
                            }
                            val otherTextCrop = matchedVehicle.otherTextCropLeft?.let {
                                android.graphics.RectF(it, matchedVehicle.otherTextCropTop ?: 0f, matchedVehicle.otherTextCropRight ?: 1f, matchedVehicle.otherTextCropBottom ?: 1f)
                            }
                            
                            val finalAlign = ImageAlignmentUtils.alignImages(refBmp, originalBitmap, 15, odometerCrop, otherTextCrop)
                            
                            if (finalAlign.alignedImage != null) {
                                val cropBmp = manualCropOdometer(finalAlign.alignedImage, matchedVehicle, debugCropDir, file.name)
                                odometerCropBase64 = bitmapToBase64(cropBmp, 200)
                                
                                val tempAlignedFile = File(context.cacheDir, "aligned_${file.name}")
                                val out = java.io.FileOutputStream(tempAlignedFile)
                                finalAlign.alignedImage.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                out.close()
                                val ocrResult = OdometerOcrUtils.extractFromPhoto(tempAlignedFile.absolutePath)
                                extractedOdometer = ocrResult.odometer
                                tempAlignedFile.delete()
                                if (ocrResult.odometer != null) success++
                            }
                        }
                    }
                }
            }

            results.add(PhotoResult(
                photoName = file.name,
                matchedVehicle = matchedVehicleName,
                finalConfidence = matchedConfidence,
                alignmentMessage = winner?.message ?: "No Match Found",
                originalThumbBase64 = bitmapToBase64(originalBitmap, 200),
                cleanedDashBase64 = bitmapToBase64(cleanedBmp, 200),
                odometerCropBase64 = odometerCropBase64,
                odometer = extractedOdometer,
                referenceTextBlocks = referenceTextBlocks,
                dashTextBlocks = dashTextBlocks ?: "",
                allVehicleResults = vehicleMatchResults
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process ${file.name}", e)
            results.add(PhotoResult(
                photoName = file.name,
                matchedVehicle = "ERROR",
                finalConfidence = 0f,
                alignmentMessage = "Error: ${e.message}",
                originalThumbBase64 = "",
                cleanedDashBase64 = "",
                odometerCropBase64 = "",
                odometer = null,
                referenceTextBlocks = "",
                dashTextBlocks = "",
                allVehicleResults = emptyList()
            ))
        }
    }
    onProgress(1f, "Generating visual report...")
    val html = buildRichHtmlReport(results, total, vehicles)
    val summary = "Processed $total photos — $success successful alignments"
    return ExperimentResult(summary, html)
}

private fun manualCropOdometer(aligned: Bitmap, vehicle: Vehicle, debugDir: File, photoName: String): Bitmap? {
    val leftF = vehicle.odometerCropLeft ?: return null
    val topF = vehicle.odometerCropTop ?: 0f
    val rightF = vehicle.odometerCropRight ?: 1f
    val bottomF = vehicle.odometerCropBottom ?: 1f
    val w = aligned.width
    val h = aligned.height
    val left = (leftF * w).toInt().coerceAtLeast(0)
    val top = (topF * h).toInt().coerceAtLeast(0)
    val right = (rightF * w).toInt().coerceAtMost(w)
    val bottom = (bottomF * h).toInt().coerceAtMost(h)
    val cropW = right - left
    val cropH = bottom - top
    if (cropW < 1 || cropH < 1) return null
    return try {
        val cropped = Bitmap.createBitmap(aligned, left, top, cropW, cropH)
        if (photoName.contains("105") || photoName.contains("99") || photoName.contains("96") || photoName.contains("97") ||
            photoName.contains("56") || photoName.contains("54") || photoName.contains("49")) {
            val debugFile = File(debugDir, "crop_${photoName}")
            val out = java.io.FileOutputStream(debugFile)
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            Log.i(TAG, "Saved debug crop for photo $photoName to ${debugFile.absolutePath}")
        }
        cropped
    } catch (e: Exception) {
        Log.e(TAG, "Manual crop failed", e)
        null
    }
}

private fun drawCropBoxesOnReference(refBmp: Bitmap?, vehicle: Vehicle): Bitmap? {
    if (refBmp == null) return null
    val bitmap = refBmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.RED }
    val landmarkPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.GREEN }
    vehicle.odometerCropLeft?.let { left ->
        val l = left * bitmap.width
        val t = (vehicle.odometerCropTop ?: 0f) * bitmap.height
        val r = (vehicle.odometerCropRight ?: 1f) * bitmap.width
        val b = (vehicle.odometerCropBottom ?: 1f) * bitmap.height
        canvas.drawRect(l, t, r, b, paint)
    }
    vehicle.otherTextCropLeft?.let { left ->
        val l = left * bitmap.width
        val t = (vehicle.otherTextCropTop ?: 0f) * bitmap.height
        val r = (vehicle.otherTextCropRight ?: 1f) * bitmap.width
        val b = (vehicle.otherTextCropBottom ?: 1f) * bitmap.height
        canvas.drawRect(l, t, r, b, landmarkPaint)
    }
    return bitmap
}

private data class VehicleMatchResult(
    val vehicleName: String,
    val score: Float,
    val inliers: Int,
    val message: String,
    val referenceBase64: String,
    val alignedBase64: String,
    val methodScores: Map<String, Float>
)

private data class PhotoResult(
    val photoName: String,
    val matchedVehicle: String,
    val finalConfidence: Float,
    val alignmentMessage: String,
    val originalThumbBase64: String,
    val cleanedDashBase64: String,
    val odometerCropBase64: String,
    val odometer: String?,
    val referenceTextBlocks: String,
    val dashTextBlocks: String,
    val allVehicleResults: List<VehicleMatchResult>
)

private data class ExperimentResult(val summary: String, val htmlReport: String)

private fun buildRichHtmlReport(results: List<PhotoResult>, total: Int, allVehicles: List<Vehicle>): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("<html><head><title>Alignment Experiment - $time</title>")
        appendLine("<style>")
        appendLine("table { border-collapse: collapse; width: 100%; font-family: sans-serif; font-size: 12px; }")
        appendLine("th, td { border: 1px solid #ccc; padding: 4px; text-align: center; vertical-align: top; }")
        appendLine("img { max-width: 200px; height: auto; border: 1px solid #eee; }")
        appendLine(".score-box { text-align: left; font-size: 10px; background: #f9f9f9; padding: 4px; border-radius: 4px; }")
        appendLine(".winner { background-color: #e6ffed; border: 2px solid #28a745; }")
        appendLine(".pinwheel { color: #d73a49; font-weight: bold; }")
        appendLine("</style></head><body>")
        appendLine("<h1>Alignment Experiment Report</h1>")
        appendLine("<p><b>Run:</b> $time | <b>Total photos:</b> $total</p>")
        appendLine("<table>")
        
        // Dynamic Headers
        appendLine("<tr>")
        appendLine("<th># & Photo</th>")
        appendLine("<th>Original & Cleaned</th>")
        allVehicles.forEach { vehicle ->
            appendLine("<th>${vehicle.name} Match</th>")
        }
        appendLine("<th>Final Result</th>")
        appendLine("<th>Consensus Breakdown</th>")
        appendLine("</tr>")

        results.forEachIndexed { index, r ->
            appendLine("<tr>")
            // Column 1: Index and Filename
            appendLine("<td>${index + 1}<br><br><b>${r.photoName}</b></td>")
            
            // Column 2: Photo state
            appendLine("<td>")
            if (r.originalThumbBase64.isNotEmpty()) {
                appendLine("<img src='data:image/jpeg;base64,${r.originalThumbBase64}'><br>Original<br>")
            }
            if (r.cleanedDashBase64.isNotEmpty()) {
                appendLine("<img src='data:image/jpeg;base64,${r.cleanedDashBase64}'><br>Cleaned")
            }
            appendLine("</td>")

            // Dynamic Vehicle Match Columns (3+)
            allVehicles.forEach { vehicle ->
                val vRes = r.allVehicleResults.find { it.vehicleName == vehicle.name }
                val isWinner = r.matchedVehicle == vehicle.name
                val winnerClass = if (isWinner) "winner" else ""
                
                appendLine("<td class='$winnerClass'>")
                if (vRes != null) {
                    if (vRes.referenceBase64.isNotEmpty()) {
                        appendLine("<b>Reference:</b><br><img src='data:image/jpeg;base64,${vRes.referenceBase64}'><br>")
                    }
                    if (vRes.alignedBase64.isNotEmpty()) {
                        appendLine("<b>Aligned:</b><br><img src='data:image/jpeg;base64,${vRes.alignedBase64}'><br>")
                    } else {
                        appendLine("<div style='background:#fee; padding:10px;'>No Alignment</div>")
                    }
                    val msgClass = if (vRes.message.contains("pinwheel")) "pinwheel" else ""
                    appendLine("<span class='$msgClass'>${vRes.message}</span>")
                } else {
                    appendLine("No Data")
                }
                appendLine("</td>")
            }

            // Final Result Column
            appendLine("<td>")
            appendLine("<b>Matched:</b> ${r.matchedVehicle}<br>")
            appendLine("<b>Confidence:</b> ${"%.1f".format(r.finalConfidence * 100)}%<br>")
            appendLine("<b>Odometer:</b> ${r.odometer ?: "FAILED"}<br>")
            if (r.odometerCropBase64.isNotEmpty()) {
                appendLine("<img src='data:image/jpeg;base64,${r.odometerCropBase64}'>")
            }
            appendLine("</td>")

            // Breakdown Column
            appendLine("<td>")
            val winnerRes = r.allVehicleResults.find { it.vehicleName == r.matchedVehicle }
            if (winnerRes != null) {
                appendLine("<div class='score-box'>")
                winnerRes.methodScores.forEach { (method, score) ->
                    appendLine("<b>$method:</b> ${"%.3f".format(score)}<br>")
                }
                appendLine("<b>Ref Blocks:</b><br>${r.referenceTextBlocks.replace("|", "<br>")}")
                appendLine("</div>")
            }
            appendLine("</td>")
            appendLine("</tr>")
        }
        appendLine("</table></body></html>")
    }
}

private fun writeSizeSplitHtmlReports(fullHtml: String, reportDir: File, timestamp: String, maxSizeKB: Int = 300): List<File> {
    Log.i("Experiment", "Writing split reports, total HTML length: ${fullHtml.length}")
    
    val headerEndMatch = Regex("<tr><th>#</th>.*?</tr>").find(fullHtml)
    if (headerEndMatch == null) {
        Log.e("Experiment", "Could not find table header in HTML report")
        // Fallback: write the whole thing to one file if we can't split it cleanly
        val file = File(reportDir, "alignment_report_${timestamp}_full.html")
        file.writeText(fullHtml)
        return listOf(file)
    }
    
    val headerEndIndex = headerEndMatch.range.last + 1
    val header = fullHtml.substring(0, headerEndIndex)
    val footer = "</table></body></html>"
    val dataContent = fullHtml.substring(headerEndIndex).removeSuffix(footer)
    
    // Split by <tr> but keep the tag. We use a positive lookahead to split BEFORE <tr>
    val rows = dataContent.split(Regex("(?=<tr>)")).filter { it.isNotBlank() }
    
    Log.i("Experiment", "Splitting report: ${rows.size} rows found")
    
    val files = mutableListOf<File>()
    var currentChunk = StringBuilder()
    var currentSize = 0L
    val maxSizeBytes = maxSizeKB * 1024L

    for (row in rows) {
        if (currentSize + row.length > maxSizeBytes && currentChunk.isNotEmpty()) {
            val pageNum = files.size + 1
            val file = File(reportDir, "alignment_report_${timestamp}_part${pageNum}.html")
            file.writeText(header + "\n" + currentChunk.toString() + "\n" + footer)
            files.add(file)
            currentChunk = StringBuilder()
            currentSize = 0L
        }
        currentChunk.append(row)
        currentSize += row.length
    }
    
    if (currentChunk.isNotEmpty()) {
        val pageNum = files.size + 1
        val file = File(reportDir, "alignment_report_${timestamp}_part${pageNum}.html")
        file.writeText(header + "\n" + currentChunk.toString() + "\n" + footer)
        files.add(file)
    }
    
    Log.i("Experiment", "Report split into ${files.size} files")
    return files
}

private fun bitmapToBase64(bitmap: Bitmap?, maxWidth: Int): String {
    val bmp = bitmap ?: createPlaceholderBitmap()
    val scaled = if (bmp.width > maxWidth) {
        val scale = maxWidth.toFloat() / bmp.width
        Bitmap.createScaledBitmap(bmp, maxWidth, (bmp.height * scale).toInt(), true)
    } else bmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
    val bytes = out.toByteArray()
    return Base64.encodeToString(bytes, Base64.DEFAULT)
}

private fun createPlaceholderBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.GRAY)
    return bmp
}
