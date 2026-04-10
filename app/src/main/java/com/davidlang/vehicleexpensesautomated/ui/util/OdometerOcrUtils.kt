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
    suspend fun runAll(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        val enginesList = listOf(TesseractEngine(), MlKitEngine(), PaddleOcrEngine(context), NativeTfliteEngine(context))
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

    suspend fun runMultiStepOcr(crop: Bitmap, context: android.content.Context, tfliteEngine: TfLiteOcrEngine? = null): List<OcrStepResult> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<OcrStepResult>()
        val engine = tfliteEngine ?: TfLiteOcrEngine(context)
        
        val variations = listOf(
            "Raw" to crop.copy(Bitmap.Config.ARGB_8888, true),
            "Grayscale" to applyGrayscale(crop),
            "Bilateral" to applyBilateral(crop),
            "CLAHE" to applyClahe(crop),
            "OTSU" to applyOtsu(crop)
        )
        
        variations.forEach { (name, bmp) ->
            val results = StringBuilder()
            
            val t0 = System.currentTimeMillis()
            val (tessText, _) = runRawOcr(bmp, "0123456789")
            results.append("<b>Tess (${System.currentTimeMillis() - t0}ms):</b> $tessText<br>")
            
            val t1 = System.currentTimeMillis()
            val mlResult = extractFromPhotoBitmap(bmp)
            val mlText = mlResult.textBlocks.joinToString(" ") { it.text }.filter { it.isDigit() }
            results.append("<b>MLKit (${System.currentTimeMillis() - t1}ms):</b> $mlText<br>")
            
            val t2 = System.currentTimeMillis()
            try {
                val tfliteText = engine.runInference(bmp)
                results.append("<b>TFLite (${System.currentTimeMillis() - t2}ms):</b> $tfliteText")
            } catch (e: Exception) {
                results.append("<b>TFLite:</b> Error")
            }
            
            steps.add(OcrStepResult(name, bmp, results.toString()))
        }
        
        if (tfliteEngine == null) engine.close()
        
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

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val loc = LocationUtils.getLatLongFromExif(photoPath)
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(debugText = "Failed decode", originalPhotoPath = photoPath)
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
        val cleanRaw = rawTesseract.replace("I", "1").replace("l", "1").replace("O", "0").replace("S", "5").replace("B", "8").trim()
        val possible = mutableListOf<String>()
        if (cleanRaw.matches(Regex("\\d{4,6}"))) possible.add(cleanRaw)
        
        OcrResult(
            engineName = "Tesseract",
            odometer = cleanRaw.takeIf { it.length in 4..6 },
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
                        val filteredWord = rawWord.filter { it.isLetterOrDigit() || it == '/' }.trim()
                        
                        if (rect != null && filteredWord.isNotBlank()) {
                            blocks.add(TextBlock(filteredWord, rect))
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
