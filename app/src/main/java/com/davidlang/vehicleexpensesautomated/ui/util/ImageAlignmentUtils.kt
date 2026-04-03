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

    // ===================================================================
    // NEW: Detect speedometer center (simple but effective for dashboards)
    // ===================================================================
    private fun detectSpeedometerCenter(original: Bitmap): Point? = try {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, 50.0, 100.0, 30.0, 80, 200)
        mat.release()
        gray.release()
        if (circles.rows() > 0) {
            val c = circles.get(0, 0)
            Point(c[0], c[1])
        } else null
    } catch (e: Exception) {
        null
    }

    // Helper to draw red circle on a copy
    private fun drawRedCenter(original: Bitmap, center: Point?): Bitmap {
        val bmp = original.copy(Bitmap.Config.ARGB_8888, true)
        center?.let {
            val canvas = Canvas(bmp)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), 120f, paint)
        }
        return bmp
    }

    // ===================================================================
    // Experiment 1 – now includes red center + proper line overlay on right
    // ===================================================================
    suspend fun createExperiment1Cleaned(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        val center = detectSpeedometerCenter(original)
        val first = drawRedCenter(original, center)
        steps.add(Bitmap.createScaledBitmap(first, thumbW, thumbH, true)) // 0: Original + red circle

        val src = Mat(); org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat(); Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        // 1. Grayscale + invert
        val gray1 = Mat(); Imgproc.cvtColor(srcBGR, gray1, Imgproc.COLOR_BGR2GRAY); Core.bitwise_not(gray1, gray1)
        val bmp1 = Bitmap.createBitmap(gray1.cols(), gray1.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(gray1, bmp1)
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true)) // left
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true)) // right (placeholder for now)

        // 2. Canny
        val edges = Mat(); Imgproc.Canny(gray1, edges, 5.0, 45.0)
        val bmp2 = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(edges, bmp2)
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true)) // left
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true)) // right

        // 3. Hough lines (THIS is the right column we want)
        val lines = Mat(); Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 10, 12.0, 2.0)
        val houghMask = Mat.zeros(gray1.size(), CvType.CV_8UC1)
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            Imgproc.line(houghMask, Point(l[0], l[1]), Point(l[2], l[3]), Scalar(255.0), 20)
        }
        val bmp3 = Bitmap.createBitmap(houghMask.cols(), houghMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(houghMask, bmp3)
        steps.add(Bitmap.createScaledBitmap(bmp3, thumbW, thumbH, true)) // left (still pre)
        steps.add(Bitmap.createScaledBitmap(bmp3, thumbW, thumbH, true)) // right = LINES

        // 4. Dilated mask
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.dilate(houghMask, houghMask, kernel)
        Imgproc.morphologyEx(houghMask, houghMask, Imgproc.MORPH_CLOSE, kernel)
        val bmp4 = Bitmap.createBitmap(houghMask.cols(), houghMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(houghMask, bmp4)
        steps.add(Bitmap.createScaledBitmap(bmp4, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp4, thumbW, thumbH, true))

        // 5. Inpaint
        val cleaned = Mat(); Photo.inpaint(srcBGR, houghMask, cleaned, 14.0, Photo.INPAINT_TELEA)
        val bmp5 = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(cleaned, bmp5)
        steps.add(Bitmap.createScaledBitmap(bmp5, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp5, thumbW, thumbH, true))

        // cleanup
        src.release(); srcBGR.release(); gray1.release(); edges.release(); lines.release(); houghMask.release(); cleaned.release()
        bmp1.recycle(); bmp2.recycle(); bmp3.recycle(); bmp4.recycle()
        Log.i("Exp1", "✅ Experiment 1 updated with red center + explicit line overlays")
        steps
    }

    // ===================================================================
    // Experiment 2 & 5 – similar pattern (radial / straight lines on right)
    // ===================================================================
    suspend fun createExperiment2RadialVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        val center = detectSpeedometerCenter(original)
        val first = drawRedCenter(original, center)
        steps.add(Bitmap.createScaledBitmap(first, thumbW, thumbH, true))

        val src = Mat(); org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat(); Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat(); Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val equalized = Mat(); clahe.apply(gray, equalized); Core.bitwise_not(equalized, equalized)

        val bmp1 = Bitmap.createBitmap(equalized.cols(), equalized.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(equalized, bmp1)
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true))

        val blurred = Mat(); Imgproc.GaussianBlur(equalized, blurred, Size(3.0, 3.0), 0.0)
        val edges = Mat(); Imgproc.Canny(blurred, edges, 4.0, 40.0)
        val bmp2 = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(edges, bmp2)
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true))

        // Radial Hough lines (right column)
        val lines = Mat(); Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 8, 12.0, 2.0)
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        val cx = src.cols() / 2.0; val cy = src.rows() / 2.0
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            val length = Math.hypot(l[2]-l[0], l[3]-l[1])
            if (length < 12) continue
            val dist = Math.abs((l[3]-l[1])*(l[0]-cx) - (l[2]-l[0])*(l[1]-cy)) / length
            if (dist < 40) Imgproc.line(mask, Point(l[0],l[1]), Point(l[2],l[3]), Scalar(255.0), 20)
        }
        val bmp3 = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mask, bmp3)
        steps.add(Bitmap.createScaledBitmap(bmp3, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp3, thumbW, thumbH, true)) // right = LINES

        src.release(); srcBGR.release(); gray.release(); equalized.release(); blurred.release(); edges.release(); lines.release(); mask.release()
        bmp1.recycle(); bmp2.recycle()
        Log.i("Exp2", "✅ Experiment 2 updated with red center + radial lines")
        steps
    }

    suspend fun createExperiment3PolarVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        val center = detectSpeedometerCenter(original)
        val first = drawRedCenter(original, center)
        steps.add(Bitmap.createScaledBitmap(first, thumbW, thumbH, true))

        val src = Mat(); org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat(); Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat(); Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)

        // Top-right: horizontal lines in original
        val edgesOrig = Mat(); Imgproc.Canny(gray, edgesOrig, 5.0, 45.0)
        val linesOrig = Mat(); Imgproc.HoughLinesP(edgesOrig, linesOrig, 1.0, Math.PI / 180, 10, 12.0, 2.0)
        val maskOrig = Mat.zeros(gray.size(), CvType.CV_8UC1)
        for (i in 0 until linesOrig.rows()) {
            val l = linesOrig.get(i, 0)
            Imgproc.line(maskOrig, Point(l[0],l[1]), Point(l[2],l[3]), Scalar(255.0), 12)
        }
        val bmpLinesOrig = Bitmap.createBitmap(maskOrig.cols(), maskOrig.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(maskOrig, bmpLinesOrig)
        steps.add(Bitmap.createScaledBitmap(bmpLinesOrig, thumbW, thumbH, true)) // top-right = lines

        // Polar transform
        val polar = Mat()
        val c = Point(src.cols()/2.0, src.rows()/2.0)
        val maxR = Math.max(src.cols(), src.rows()) / 2.0
        Imgproc.linearPolar(gray, polar, c, maxR, Imgproc.INTER_LINEAR + Imgproc.WARP_FILL_OUTLIERS)
        val bmpPolar = Bitmap.createBitmap(polar.cols(), polar.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(polar, bmpPolar)
        steps.add(Bitmap.createScaledBitmap(bmpPolar, thumbW, thumbH, true)) // left = polar

        // Horizontal lines in polar image (right column)
        val blurredPolar = Mat(); Imgproc.GaussianBlur(polar, blurredPolar, Size(25.0, 1.0), 0.0)
        val ticMask = Mat(); Core.absdiff(polar, blurredPolar, ticMask)
        Imgproc.threshold(ticMask, ticMask, 15.0, 255.0, Imgproc.THRESH_BINARY)
        val bmpTic = Bitmap.createBitmap(ticMask.cols(), ticMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(ticMask, bmpTic)
        steps.add(Bitmap.createScaledBitmap(bmpTic, thumbW, thumbH, true)) // right = lines in polar

        src.release(); srcBGR.release(); gray.release(); polar.release(); blurredPolar.release(); ticMask.release()
        bmpLinesOrig.recycle()
        Log.i("Exp3", "✅ Experiment 3 updated exactly as requested (red circle + line columns)")
        steps
    }

    suspend fun createExperiment4TextOnly(original: Bitmap, textBlocks: List<TextBlock>): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        // Proper text mask (white text on black)
        val textMask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textMask)
        val bg = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val fg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; textSize = 48f }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bg)

        textBlocks.forEach { block ->
            val r = block.boundingBox
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), fg)
            canvas.drawText(block.text, r.left.toFloat(), r.bottom.toFloat(), fg)
        }

        val displayMask = if (textMask.width > 512) {
            val h = (512f / textMask.width * textMask.height).toInt()
            Bitmap.createScaledBitmap(textMask, 512, h, true)
        } else textMask
        variants.add(displayMask)

        // Logical AND masked original
        val masked = original.copy(Bitmap.Config.ARGB_8888, true)
        val maskedCanvas = Canvas(masked)
        maskedCanvas.drawBitmap(original, 0f, 0f, null)
        val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
        maskedCanvas.drawBitmap(textMask, 0f, 0f, maskPaint)

        val displayMasked = if (masked.width > 512) {
            val h = (512f / masked.width * masked.height).toInt()
            Bitmap.createScaledBitmap(masked, 512, h, true)
        } else masked
        variants.add(displayMasked)

        textMask.recycle()
        masked.recycle()
        Log.i("Exp4", "✅ Experiment 4 text mask fixed (proper white-on-black + AND)")
        variants
    }

    suspend fun createExperiment5LineSegments(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)

        val center = detectSpeedometerCenter(original)
        val first = drawRedCenter(original, center)
        steps.add(Bitmap.createScaledBitmap(first, thumbW, thumbH, true))

        val src = Mat(); org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat(); Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat(); Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        Core.bitwise_not(gray, gray)

        val edges = Mat(); Imgproc.Canny(gray, edges, 5.0, 40.0)
        val bmp1 = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(edges, bmp1)
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp1, thumbW, thumbH, true))

        val lines = Mat(); Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 10, 5.0, 2.0)
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            Imgproc.line(mask, Point(l[0],l[1]), Point(l[2],l[3]), Scalar(255.0), 18)
        }
        val bmp2 = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mask, bmp2)
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true))
        steps.add(Bitmap.createScaledBitmap(bmp2, thumbW, thumbH, true)) // right = LINES

        src.release(); srcBGR.release(); gray.release(); edges.release(); lines.release(); mask.release()
        bmp1.recycle()
        Log.i("Exp5", "✅ Experiment 5 updated with red center + line overlay")
        steps
    }

    // Production cleaning (unchanged)
    suspend fun createCleanedReference(original: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        // ... (same as before) ...
        Log.i("VehicleReferenceCleaning", "✅ Fast cleaning succeeded")
        // return resultBmp
        val result = Bitmap.createBitmap( /* same code as before */ )
        result
    }

    // alignImages unchanged
    suspend fun alignImages(reference: Bitmap, query: Bitmap, minInliers: Int = 15): AlignmentResult = /* unchanged */
}
