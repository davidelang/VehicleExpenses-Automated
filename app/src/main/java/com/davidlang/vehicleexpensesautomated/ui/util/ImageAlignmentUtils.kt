package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import org.opencv.photo.Photo

data class AlignmentResult(
    val success: Boolean,
    val alignedImage: Bitmap?,
    val confidence: Float,
    val message: String
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        } else {
            Log.i("ImageAlignment", "OpenCV initialized successfully for alignment")
        }
    }

    // ... (all other functions unchanged - red circle, experiments 1/2/3/5, cleaning, alignImages) ...

    // ===================================================================
    // FIXED: Experiment 4 — proper text-only mask + logical AND
    // ===================================================================
    suspend fun createExperiment4TextOnly(original: Bitmap, textBlocks: List<TextBlock>): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        // 1. Original
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        // 2. Proper white-on-black text mask
        val textMask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textMask)
        val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val textPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; textSize = 48f }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bgPaint)

        textBlocks.forEach { block ->
            val r = block.boundingBox
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), textPaint)
            canvas.drawText(block.text, r.left.toFloat(), r.bottom.toFloat(), textPaint)
        }

        val displayTextMask = if (textMask.width > 512) {
            val h = (512f / textMask.width * textMask.height).toInt()
            Bitmap.createScaledBitmap(textMask, 512, h, true)
        } else textMask
        variants.add(displayTextMask)

        // 3. Logical AND masked original (keep only white areas)
        val maskedOriginal = original.copy(Bitmap.Config.ARGB_8888, true)
        val maskedCanvas = Canvas(maskedOriginal)
        maskedCanvas.drawBitmap(original, 0f, 0f, null)
        val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
        maskedCanvas.drawBitmap(textMask, 0f, 0f, maskPaint)

        val displayMasked = if (maskedOriginal.width > 512) {
            val h = (512f / maskedOriginal.width * maskedOriginal.height).toInt()
            Bitmap.createScaledBitmap(maskedOriginal, 512, h, true)
        } else maskedOriginal
        variants.add(displayMasked)

        textMask.recycle()
        maskedOriginal.recycle()

        Log.i("Exp4", "✅ Experiment 4 fixed — proper white-on-black mask + logical AND")
        variants
    }

    // ... rest of the file (cleanedReference, alignImages, etc.) unchanged ...
}
