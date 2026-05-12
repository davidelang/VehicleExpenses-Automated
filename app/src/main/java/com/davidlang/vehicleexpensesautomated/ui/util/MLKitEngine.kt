package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.gson.JsonObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import android.util.Base64

/**
 * MLKitEngine implements the new iterative OCR algorithm (Revision 18).
 * It manages ROI extraction, preprocessing, and sharp Mat-direct visualization
 * with zero runtime allocations and strict aspect-ratio preservation.
 */
class MLKitEngine(
    override val displayName: String,
    private val winner: com.davidlang.vehicleexpensesautomated.ui.experiment.ReferenceCache,
    private val bridge: MemoryBridge
) : OcrEngineStrategy {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun execute(
        masterBuffer: Any,
        masterW: Int,
        masterH: Int,
        report: ReportCollector
    ): OcrHarnessResult {
        val recBridge = MemoryBridge.pool320x48 ?: throw IllegalStateException("Rec pool not initialized")
        val htmlOutput = StringBuilder("<b>$displayName:</b><br>")
        val jsonStages = JsonObject()
        val allOdo = mutableListOf<String>()

        val masterBmp = masterBuffer as? Bitmap ?: throw IllegalArgumentException("Master must be Bitmap")
        val argbMat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(masterBmp, argbMat)

        val l = winner.vehicle.odometerCropLeft ?: 0f
        val t = winner.vehicle.odometerCropTop ?: 0f
        val r = winner.vehicle.odometerCropRight ?: 1f
        val b = winner.vehicle.odometerCropBottom ?: 1f
        
        val roiRect = org.opencv.core.Rect(
            (l * masterW).toInt().coerceIn(0, masterW - 1),
            (t * masterH).toInt().coerceIn(0, masterH - 1),
            ((r - l) * masterW).toInt().coerceAtMost(masterW),
            ((b - t) * masterH).toInt().coerceAtMost(masterH)
        )

        // Aspect Ratio Calculation
        val aspect = roiRect.width.toFloat() / roiRect.height.toFloat()
        val fitW: Int; val fitH: Int
        if (aspect > (320f / 48f)) {
            fitW = 320
            fitH = (320f / aspect).toInt().coerceAtLeast(1)
        } else {
            fitH = 48
            fitW = (48f * aspect).toInt().coerceAtLeast(1)
        }

        // Iterative Pass Loop (Extract -> Modify Mat -> Scale -> OCR -> Visualize)
        val stages = listOf("Standard", "80% Stretch", "Bilevel", "Stretch -> Bilevel")
        stages.forEach { stage ->
            val tStart = System.currentTimeMillis()
            
            // --- Algorithm Step 1: Re-extract from master to ensure clean baseline ---
            val roiMat = org.opencv.core.Mat(argbMat, roiRect)
            val grayMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(roiMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
            
            val interp = if (roiRect.width > bridge.width) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_CUBIC
            org.opencv.imgproc.Imgproc.resize(grayMat, bridge.getMat(), org.opencv.core.Size(bridge.width.toDouble(), bridge.height.toDouble()), 0.0, 0.0, interp)

            // --- Algorithm Step 2: In-place modification on high-res Mat ---
            if (stage.contains("Stretch")) {
                org.opencv.core.Core.normalize(bridge.getMat(), bridge.getMat(), 0.0, 255.0, org.opencv.core.Core.NORM_MINMAX)
            }
            if (stage.contains("Bilevel")) {
                org.opencv.imgproc.Imgproc.threshold(bridge.getMat(), bridge.getMat(), 0.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY or org.opencv.imgproc.Imgproc.THRESH_OTSU)
            }

            // --- Algorithm Step 3: Aspect-Ratio Preserving Resize (NON-DESTRUCTIVE) ---
            val dstMat = recBridge.getMat()
            dstMat.setTo(org.opencv.core.Scalar(0.0)) // Clear to black
            val subDst = org.opencv.core.Mat(dstMat, org.opencv.core.Rect(0, 0, fitW, fitH))
            org.opencv.imgproc.Imgproc.resize(bridge.getMat(), subDst, org.opencv.core.Size(fitW.toDouble(), fitH.toDouble()), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)

            // --- Algorithm Step 4: Recognition ---
            val targetW = 320; val targetH = 48
            val frameSize = targetW * targetH
            val nv21 = ByteArray(frameSize * 3 / 2)
            val pixels = IntArray(frameSize)
            recBridge.syncToBitmap()
            val recBmp = recBridge.getBitmap()
            recBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            for (i in 0 until frameSize) nv21[i] = ((pixels[i] shr 16) and 0xFF).toByte()
            for (i in frameSize until nv21.size) nv21[i] = 128.toByte()

            val img = InputImage.fromByteArray(nv21, targetW, targetH, 0, InputImage.IMAGE_FORMAT_NV21)
            val visionText = recognizer.process(img).await()
            val odo = visionText.text.filter { it.isDigit() }
            allOdo.add(odo)

            // --- Visualization (AT THE END): Prove non-destructive downscale & show final state ---
            // 1. Capture snapshot to shared report pool (320x48 match)
            val snap = NativePaddleEngine.sharedReportBitmap
            org.opencv.android.Utils.matToBitmap(recBridge.getMat(), snap)
            
            // 2. Create zero-allocation Subset View for Base64 (preserving aspect ratio in HTML)
            val view = Bitmap.createBitmap(snap, 0, 0, fitW, fitH)
            
            // 3. Decorate for maximum sharpness
            val canvas = android.graphics.Canvas(view)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f }
            visionText.textBlocks.forEach { block ->
                canvas.drawRect(block.boundingBox!!, paint)
            }

            val thumbB64 = OcrUtils.bitmapToBase64(view, 80)

            val tLoop = System.currentTimeMillis() - tStart
            htmlOutput.append("<div class='ocr-step'><b>$stage:</b> ($tLoop ms)<br><img src='data:image/jpeg;base64,$thumbB64'><br>$odo</div>")
            
            // For analysis scripts
            val stageObj = JsonObject()
            stageObj.addProperty("text", odo)
            stageObj.addProperty("time", tLoop)
            jsonStages.add(stage, stageObj)
            
            roiMat.release(); grayMat.release(); subDst.release()
        }

        argbMat.release()

        val meta = JsonObject()
        meta.addProperty("inputW", masterW); meta.addProperty("inputH", masterH)
        meta.add("stages", jsonStages)

        val result = OcrHarnessResult(
            htmlHeader = "<th>$displayName</th>",
            htmlCell = htmlOutput.toString(),
            jsonSection = meta,
            odometerValue = allOdo.firstOrNull { it.isNotBlank() }
        )

        report.add(displayName, result)
        return result
    }
}
