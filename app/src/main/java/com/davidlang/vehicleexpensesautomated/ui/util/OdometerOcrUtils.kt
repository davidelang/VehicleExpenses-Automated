package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class TextBlock(
    val text: String,
    val boundingBox: android.graphics.Rect,
    val angle: Float = 0f
)

data class OcrResult(
    val engineName: String = "Unknown",
    val executionTimeMs: Long = 0,
    val odometer: String? = null,
    val possibleOdometers: List<String> = emptyList(),
    val gallons: String? = null,
    val cost: String? = null,
    val debugText: String,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun filterByCrops(odoCrop: android.graphics.RectF?, otherCrop: android.graphics.RectF?): OcrResult {
        val filteredBlocks = textBlocks.filter { block ->
            !OdometerOcrUtils.isBlockInCrop(block, odoCrop, imageWidth, imageHeight) &&
            !OdometerOcrUtils.isBlockInCrop(block, otherCrop, imageWidth, imageHeight)
        }
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.joinToString(" ") { it.text }
        )
    }
}

data class OcrStepResult(
    val stageName: String,
    val bitmap: Bitmap,
    val text: String?
)

interface OcrEngine {
    val name: String
    suspend fun recognize(bitmap: Bitmap): OcrResult
}

class TesseractEngine : OcrEngine {
    override val name = "Tesseract"
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val (text, blocks) = OdometerOcrUtils.runRawOcr(bitmap, "0123456789")
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = text.takeIf { it.length in 4..6 },
            debugText = text,
            textBlocks = blocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}

class MlKitEngine : OcrEngine {
    override val name = "ML Kit"
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val res = OdometerOcrUtils.extractFromPhotoBitmap(bitmap)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }
}

class NativeTfliteEngine(private val context: Context) : OcrEngine {
    override val name = "Native TFLite"
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val engine = TfLiteOcrEngine(context)
        val text = engine.runInference(bitmap)
        engine.close()
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = text,
            debugText = text,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}

object OcrHarness {
    suspend fun runDiscovery(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        val paddleEngine = PaddleOcrEngine(context)
        val enginesList = mutableListOf<OcrEngine>(MlKitEngine(), NativeTfliteEngine(context))
        if (paddleEngine.isAvailable) enginesList.add(paddleEngine)
        
        return enginesList.associate { engine ->
            engine.name to engine.recognize(bitmap)
        }
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        val paddleEngine = PaddleOcrEngine(context)
        val enginesList = mutableListOf<OcrEngine>(TesseractEngine(), MlKitEngine(), NativeTfliteEngine(context))
        if (paddleEngine.isAvailable) enginesList.add(paddleEngine)
        
        return enginesList.associate { engine ->
            engine.name to engine.recognize(bitmap)
        }
    }
}

object OdometerOcrUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        val out = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(gray, out)
        mat.release(); gray.release()
        return out
    }

    fun applyBilateral(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 9, 75.0, 75.0)
        val out = Bitmap.createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(filtered, out)
        mat.release(); gray.release(); filtered.release()
        return out
    }

    fun applyClahe(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val outMat = Mat()
        clahe.apply(gray, outMat)
        val out = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(outMat, out)
        mat.release(); gray.release(); outMat.release()
        return out
    }

    fun applyOtsu(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val threshed = Mat()
        Imgproc.threshold(gray, threshed, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val out = Bitmap.createBitmap(threshed.cols(), threshed.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(threshed, out)
        mat.release(); gray.release(); threshed.release()
        return out
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null) return false
        if (w <= 0 || h <= 0) return false
        val bx = block.boundingBox.centerX().toFloat() / w
        val by = block.boundingBox.centerY().toFloat() / h
        return crop.contains(bx, by)
    }

    fun runRawOcr(bitmap: Bitmap, whitelist: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"): Pair<String, List<TextBlock>> {
        val tess = TessBaseAPI()
        val blocks = mutableListOf<TextBlock>()
        try {
            val tessDataPath = "/data/user/0/com.davidlang.vehicleexpensesautomated/files"
            if (!tess.init(tessDataPath, "eng")) {
                tess.clear()
                return "(Tesseract init failed)" to emptyList()
            }

            tess.setVariable("tessedit_char_whitelist", whitelist)

            tess.setImage(bitmap)

            val fullText = tess.getUTF8Text()?.trim() ?: "(no text)"

            val iterator = tess.getResultIterator()
            if (iterator != null) {
                do {
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD) ?: continue
                    val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (rect != null && text.isNotBlank()) {
                        blocks.add(TextBlock(text.trim(), rect))
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            }

            tess.clear()
            return fullText to blocks
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Raw Tesseract failed", e)
            return "(Raw Tesseract error: ${e.message})" to emptyList()
        } finally {
            tess.clear()
        }
    }

    suspend fun runMultiStepOcr(crop: Bitmap, context: android.content.Context): List<OcrStepResult> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<OcrStepResult>()
        val variations = listOf(
            "Raw" to crop.copy(Bitmap.Config.ARGB_8888, true),
            "Grayscale" to applyGrayscale(crop),
            "Bilateral" to applyBilateral(crop),
            "CLAHE" to applyClahe(crop),
            "OTSU" to applyOtsu(crop)
        )
        
        variations.forEach { (name, bmp) ->
            val results = StringBuilder()
            val ocrMap = OcrHarness.runRefinement(bmp, context)
            ocrMap.forEach { (eng, res) ->
                val refined = refineNumericResult(res)
                results.append("<b>$eng (${refined.executionTimeMs}ms):</b> ${refined.debugText}<br>")
            }
            steps.add(OcrStepResult(name, bmp, results.toString()))
        }
        steps
    }

    fun annotateImageWithBoxes(original: Bitmap, blocks: List<TextBlock>): Bitmap {
        val annotated = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.RED
        }
        for (block in blocks) {
            val rect = block.boundingBox
            canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
        }
        return annotated
    }

    /** Shared helper — identical to manualCropOdometer and the blue-box drawing */
    private fun cropBitmap(bitmap: Bitmap, cropRect: RectF): Bitmap? {
        val origW = bitmap.width
        val origH = bitmap.height
        val left = (cropRect.left * origW).toInt().coerceAtLeast(0)
        val top = (cropRect.top * origH).toInt().coerceAtLeast(0)
        val right = (cropRect.right * origW).toInt().coerceAtMost(origW)
        val bottom = (cropRect.bottom * origH).toInt().coerceAtMost(origH)
        Log.i("CropDebug", "cropBitmap: cropRect=$cropRect, image=${origW}x${origH}, pixels=left=$left top=$top right=$right bottom=$bottom")
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun extractFromPhotoForDebug(bitmap: Bitmap): Pair<String, List<TextBlock>> = runRawOcr(bitmap)

    fun rotateImageIfRequired(img: Bitmap, path: String): Bitmap {
        val ei = android.media.ExifInterface(path)
        val orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    fun decodeBitmapSafely(context: Context, path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            if (path.lowercase().endsWith(".dng") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(file)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                BitmapFactory.decodeFile(path)
            }
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Safe decode failed for $path", e)
            null
        }
    }

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null, context: Context? = null): OcrResult = withContext(Dispatchers.IO) {
        val loc = LocationUtils.getLatLongFromExif(photoPath)
        val rawBitmap = if (context != null) decodeBitmapSafely(context, photoPath) else BitmapFactory.decodeFile(photoPath)
        if (rawBitmap == null) return@withContext OcrResult(debugText = "Failed decode", originalPhotoPath = photoPath)

        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        var bitmap = rotated
        if (cropRect != null) {
            val cropped = cropBitmap(rotated, cropRect)
            if (cropped != null) {
                if (rotated != cropped) rotated.recycle()
                bitmap = cropped
            }
        }
        val res = extractFromPhotoBitmap(bitmap)
        if (bitmap != rotated) bitmap.recycle()
        rotated.recycle()
        res.copy(originalPhotoPath = photoPath, latitude = loc?.latitude, longitude = loc?.longitude)
    }

    suspend fun extractFullImageOcr(photoPath: String, deduplicateWith: List<TextBlock>? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(debugText = "Photo file not found", originalPhotoPath = photoPath)
        
        val loc = LocationUtils.getLatLongFromExif(photoPath)
        
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(debugText = "Failed to decode bitmap", originalPhotoPath = photoPath)
        val bitmap = rotateImageIfRequired(rawBitmap, photoPath)
        
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 9, 75.0, 75.0)
        
        val preprocessed = Bitmap.createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(filtered, preprocessed)
        
        val (rawTesseract, textBlocksOut) = runRawOcr(preprocessed)
        
        mat.release()
        gray.release()
        filtered.release()
        
        val debugText = buildString {
            appendLine("=== FULL IMAGE OCR (preprocessed Tesseract) ===\n")
            appendLine("Tesseract: $rawTesseract")
        }
        val cleanRaw = clean7SegmentDigits(rawTesseract).trim()
        val possible = mutableListOf<String>()
        if (cleanRaw.matches(Regex("\\d{4,7}"))) possible.add(cleanRaw)
        
        OcrResult(
            engineName = "Tesseract",
            odometer = cleanRaw.takeIf { it.length in 4..7 },
            possibleOdometers = possible,
            debugText = debugText.toString(),
            originalPhotoPath = photoPath,
            textBlocks = textBlocksOut,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            latitude = loc?.latitude,
            longitude = loc?.longitude
        )
    }

    fun cleanLandmarkString(text: String): String {
        return text.replace(".", "").replace(",", "").trim()
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun clean7SegmentDigits(text: String, isFlipped: Boolean = false): String {
        val standardMap = mapOf(
            'I' to '1', 'l' to '1', '|' to '1', '!' to '1', '/' to '1', '\\' to '1',
            'O' to '0', 'D' to '0',
            'S' to '5', 's' to '5',
            'B' to '8', 'G' to '6', 'A' to '4'
        )
        val flipMap = mapOf(
            '6' to '9', '9' to '6',
            'L' to '7', 'V' to '7',
            'h' to '4', 'H' to '4',
            'E' to '3',
            'G' to '9',
            'B' to '8',
            'S' to '5', '!' to '1', 'I' to '1', 'l' to '1', '|' to '1', '/' to '1', '\\' to '1',
            'A' to 'V'
        )
        val workingText = if (isFlipped) text.reversed() else text
        val activeMap = if (isFlipped) flipMap else standardMap
        return workingText.map { char ->
            activeMap[char] ?: (activeMap[char.uppercaseChar()] ?: char)
        }.joinToString("")
    }

    fun refineNumericResult(result: OcrResult): OcrResult {
        val refinedBlocks = result.textBlocks.map { block ->
            val isFlipped = Math.abs(block.angle) > 165f
            val cleaned = clean7SegmentDigits(block.text, isFlipped)
            if (isFlipped) {
                Log.i("OdometerOcr", "v45_FLIP_RECOVERED: '${block.text}' -> '$cleaned' (Angle: ${block.angle})")
            }
            block.copy(text = cleaned)
        }
        val refinedText = refinedBlocks.joinToString(" ") { it.text }
        val digits = refinedText.filter { it.isDigit() }
        // Attempt to find a 4-7 digit odometer in the refined text
        val odoCandidate = Regex("\\d{4,7}").find(refinedText)?.value
        
        return result.copy(
            textBlocks = refinedBlocks,
            debugText = refinedText,
            odometer = odoCandidate ?: result.odometer
        )
    }

    suspend fun calculateAverageTextAngle(bitmap: Bitmap): Float {
        // 1. Scale to 1500px wide for consistency
        val scale = 1500f / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, 1500, (bitmap.height * scale).toInt(), true)
        
        // 2. OCR Full Image (ML Kit)
        val ocrResult = extractFromPhotoBitmap(scaled)
        scaled.recycle()
        
        val angles = ocrResult.textBlocks.map { it.angle }
        if (angles.isEmpty()) return 0f
        
        // 3. Use Median to ignore outliers (like dial numbers which might be rotated)
        val sortedAngles = angles.sorted()
        return sortedAngles[sortedAngles.size / 2]
    }

    fun manualCropFromRectF(bmp: Bitmap, rect: android.graphics.RectF): Bitmap? {
        val left = (rect.left * bmp.width).toInt().coerceAtLeast(0)
        val top = (rect.top * bmp.height).toInt().coerceAtLeast(0)
        val width = ((rect.right - rect.left) * bmp.width).toInt().coerceAtMost(bmp.width - left)
        val height = ((rect.bottom - rect.top) * bmp.height).toInt().coerceAtMost(bmp.height - top)
        if (width <= 0 || height <= 0) return null
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }

    fun pickBestOdometer(allSteps: List<OcrStepResult>): String? {
        val candidates = allSteps.mapNotNull { it.text }.flatMap { text ->
            val cleanedText = text.replace("<b>", "").replace("</b>", "").replace("<br>", " ")
            Regex("\\d{4,7}").findAll(cleanedText).map { it.value }
        }
        return candidates.groupBy { it }.maxByOrNull { it.value.size }?.key ?: candidates.maxByOrNull { it.length }
    }

    suspend fun discoverLandmarksFromBitmap(
        bitmap: Bitmap,
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null
    ): List<TextBlock> {
        // 1. Scale to 1500px wide
        val scale = 1500f / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, 1500, (bitmap.height * scale).toInt(), true)
        
        // 2. MASKING: Draw black boxes over the areas we want the OCR to ignore
        val maskedBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(maskedBitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.FILL }
        
        odometerCrop?.let { 
            canvas.drawRect(it.left * scaled.width, it.top * scaled.height, it.right * scaled.width, it.bottom * scaled.height, paint)
        }
        otherTextCrop?.let {
            canvas.drawRect(it.left * scaled.width, it.top * scaled.height, it.right * scaled.width, it.bottom * scaled.height, paint)
        }

        // 3. OCR the MASKED image
        val ocrResult = extractFromPhotoBitmap(maskedBitmap)
        val allBlocks = ocrResult.textBlocks
        
        // 4. Filter & Clean (Just length and punctuation now, as masking handled the areas)
        val landmarks = allBlocks.map { block ->
            TextBlock(
                text = cleanLandmarkString(block.text),
                boundingBox = block.boundingBox,
                angle = block.angle
            )
        }.filter { it.text.length > 1 }.sortedBy { it.text }

        scaled.recycle()
        maskedBitmap.recycle()
        return landmarks
    }

    suspend fun discoverLandmarks(
        photoPath: String, 
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null
    ): List<TextBlock> = withContext(Dispatchers.IO) {
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext emptyList()
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        val landmarks = discoverLandmarksFromBitmap(rotated, odometerCrop, otherTextCrop)
        if (rotated != rawBitmap) rotated.recycle()
        rawBitmap.recycle()
        landmarks
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            // Filter: Alpha-numeric and /
            val filtered = result.text.filter { it.isLetterOrDigit() || it == '/' }
            if (filtered.isEmpty()) "(no text)" else filtered
        } catch (e: Exception) {
            "(ML Kit Error: ${e.message})"
        }
    }

    suspend fun extractFromPhotoBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val blocks = mutableListOf<TextBlock>()
            val filteredText = StringBuilder()
            
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val rect = element.boundingBox
                        val rawWord = element.text
                        val angle = element.angle // Capture fine-grained angle
                        val filteredWord = rawWord.filter { it.isLetterOrDigit() || it in "/.,- " }.trim()
                        
                        if (rect != null && filteredWord.isNotBlank()) {
                            blocks.add(TextBlock(filteredWord, rect, angle))
                            filteredText.append(filteredWord).append(" ")
                        }
                    }
                }
            }
            val finalOcrText = filteredText.toString().trim()
            OcrResult(
                engineName = "ML Kit",
                debugText = finalOcrText, 
                textBlocks = blocks,
                imageWidth = bitmap.width, 
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        }
    }
}
