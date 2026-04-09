package com.davidlang.vehicleexpensesautomated.ui.util

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
    val boundingBox: android.graphics.Rect
)

data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?,
    val debugText: String,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class OcrStepResult(
    val stageName: String,
    val bitmap: Bitmap,
    val text: String?
)

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
        val numericWhitelist = "0123456789"
        
        // 0. Raw
        val (text0, _) = runRawOcr(crop, numericWhitelist)
        steps.add(OcrStepResult("Raw", crop.copy(Bitmap.Config.ARGB_8888, true), text0))
        
        // 0.5 Advanced (ML Kit - TPU Optimized)
        val mlKitText = runMlKitOcr(crop)
        steps.add(OcrStepResult("ML Kit", crop.copy(Bitmap.Config.ARGB_8888, true), mlKitText))

        // 0.75 TFLite Custom (Mechanical Digits)
        try {
            val engine = tfliteEngine ?: TfLiteOcrEngine(context)
            val tfliteText = engine.runInference(crop)
            steps.add(OcrStepResult("TFLite", crop.copy(Bitmap.Config.ARGB_8888, true), tfliteText))
            if (tfliteEngine == null) engine.close()
        } catch (e: Exception) {
            steps.add(OcrStepResult("TFLite", crop.copy(Bitmap.Config.ARGB_8888, true), "(Error)"))
        }

        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(crop, mat)
        
        // 1. Grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        val bmpGray = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(gray, bmpGray)
        val (text1, _) = runRawOcr(bmpGray, numericWhitelist)
        steps.add(OcrStepResult("Grayscale", bmpGray, text1))
        
        // 2. Bilateral Filter (Noise reduction)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 9, 75.0, 75.0)
        val bmpFiltered = Bitmap.createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(filtered, bmpFiltered)
        val (text2, _) = runRawOcr(bmpFiltered, numericWhitelist)
        steps.add(OcrStepResult("Bilateral", bmpFiltered, text2))
        
        // 3. CLAHE (Contrast Limited Adaptive Histogram Equalization) + Adaptive Threshold
        val clahe = Imgproc.createCLAHE(1.2, org.opencv.core.Size(8.0, 8.0)) // lowered clipLimit
        val claheMat = Mat()
        clahe.apply(gray, claheMat)
        val adaptiveThresh = Mat()
        // Block size 11, constant 2.0 (more sensitive to detail than 15/5.0)
        Imgproc.adaptiveThreshold(claheMat, adaptiveThresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        val bmpAdaptive = Bitmap.createBitmap(adaptiveThresh.cols(), adaptiveThresh.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(adaptiveThresh, bmpAdaptive)
        val (text3, _) = runRawOcr(bmpAdaptive, numericWhitelist)
        steps.add(OcrStepResult("CLAHE+Adapt", bmpAdaptive, text3))
        
        // 4. OTSU Threshold (Good for high contrast, e.g. OLED screens)
        val otsuThresh = Mat()
        Imgproc.threshold(gray, otsuThresh, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val bmpOtsu = Bitmap.createBitmap(otsuThresh.cols(), otsuThresh.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(otsuThresh, bmpOtsu)
        val (text4, _) = runRawOcr(bmpOtsu, numericWhitelist)
        steps.add(OcrStepResult("Otsu", bmpOtsu, text4))

        mat.release()
        gray.release()
        filtered.release()
        claheMat.release()
        adaptiveThresh.release()
        otsuThresh.release()
        
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

    private fun rotateImageIfRequired(img: Bitmap, path: String): Bitmap {
        val ei = android.media.ExifInterface(path)
        val orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val loc = LocationUtils.getLatLongFromExif(photoPath)
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed decode", photoPath, null, null, emptyList())
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
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null, null, emptyList())
        
        val loc = LocationUtils.getLatLongFromExif(photoPath)
        
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null, null, emptyList())
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
        val textBlocks = textBlocksOut.toMutableList()
        
        OcrResult(
            odometer = cleanRaw.takeIf { it.length in 4..6 },
            possibleOdometers = possible,
            gallons = null,
            cost = null,
            debugText = debugText.toString(),
            originalPhotoPath = photoPath,
            croppedBitmap = null,
            openCvProcessedBitmap = null,
            textBlocks = textBlocks,
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
                odometer = null, possibleOdometers = emptyList(), gallons = null, cost = null,
                debugText = finalOcrText, textBlocks = blocks,
                imageWidth = bitmap.width, imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(null, emptyList(), null, null, "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        }
    }
}
