package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Represents a single hunk of text found by an OCR engine.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect, // Final Crop Pixel coordinates
    val angle: Float = 0f,
    val points: List<org.opencv.core.Point> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    /**
     * Phase 109: Instance tracking for landmark disambiguation.
     * -1: Unmapped/Initial state (default for discovered landmarks).
     *  0: Globally unique landmark (appears only once in the vehicle manifest).
     * 1+: Specific instance of a duplicate landmark (1st, 2nd, etc. occurrence).
     */
    val instanceId: Int = -1,
    val confidence: Float = 0f
)

data class AlignmentTraceResult(
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
    val metadata: Map<String, String> = emptyMap()
)

data class RefinementTrace(
    val strategyName: String,
    val timeMs: Long,
    val steps: List<OcrStepResult>,
    val metadata: Map<String, String> = emptyMap()
)

data class SingleVehiclePathwayResult(
    val alignmentTrace: AlignmentTraceResult?,
    val refinementTraces: Map<String, RefinementTrace>,
    val disambiguatedLandmarks: List<TextBlock> = emptyList(),
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap()
)

data class SingleVehicleResult(
    val vehicleName: String,
    val vetoReason: String,
    val tMatchMs: Long,
    val discoveryTimeMs: Long = 0,
    val pathResults: Map<String, SingleVehiclePathwayResult>, // Keys: "set_a", "set_b", "standard"
    val vetoQueryWords: List<String>,
    val vetoMyManifest: List<String>,
    val vetoPool: List<String>,
    val isWinner: Boolean
)

data class PhotoPathwayResult(
    val winnerName: String,
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

/**
 * Encapsulates the results of an OCR operation.
 */
data class OcrResult(
    val engineName: String = "Unknown",
    val executionTimeMs: Long = 0,
    val discoveryTimeMs: Long = 0, // NEW PROFILING METRIC
    val odometer: String? = null,
    val possibleOdometers: List<String> = emptyList(),
    val gallons: String? = null,
    val cost: String? = null,
    val debugText: String,
    val errorMessage: String? = null,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val rawHeatmap: FloatArray? = null,
    val heatmapWidth: Int = 0,
    val heatmapHeight: Int = 0,
    val discoveryHeatmap: FloatArray? = null,
    val scaleFactor: Float = 1.0f,
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
    val perCharProbs: String = ""
) {
    fun filterByCrops(odoCrop: android.graphics.RectF?, otherCrop: android.graphics.RectF?): OcrResult {
        val filteredBlocks = textBlocks.filter { block ->
            if (imageWidth <= 0 || imageHeight <= 0) return@filter true
            val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), imageWidth, imageHeight)
            val inOdo = odoCrop?.let { icrs.x >= it.left && icrs.x <= it.right && icrs.y >= it.top && icrs.y <= it.bottom } ?: false
            val inOther = otherCrop?.let { icrs.x >= it.left && icrs.x <= it.right && icrs.y >= it.top && icrs.y <= it.bottom } ?: false
            !inOdo && !inOther
        }
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.filter { it.text.isNotBlank() }.joinToString(" ") { it.text }
        )
    }
}

data class OcrStepResult(
    val stageName: String,
    val thumbB64: String,
    val ocrInputB64: String? = null, // Phase 63: Exact 320x48 buffer passed to model
    val text: String?,
    val boxes: List<Rect> = emptyList(),
    val normalizedBoxes: List<TextBlock> = emptyList(),
    val rawBox: Rect? = null,
    val refinedBox: Rect? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class Shape { LINE, RECTANGLE }

data class SnapshotAnnotation(
    val x1: Int, val y1: Int, val x2: Int, val y2: Int,
    val shape: Shape,
    val color: Int, // ARGB color
    val strokeWidth: Int
)

enum class DiscoveryExpansion { UNCLIP, VALLEY }

interface OcrEngine {
    val name: String
    suspend fun recognize(input: Any): OcrResult
}

class MlKitEngine : OcrEngine {
    override val name = "ML Kit"
    override suspend fun recognize(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()

        val image = when (input) {
            is Bitmap -> com.google.mlkit.vision.common.InputImage.fromBitmap(input, 0)
            is BufferSet.Slice -> {
                val w = input.width
                val h = input.height
                com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                    input.nv21,
                    w,
                    h,
                    0,
                    com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
                )
            }
            else -> throw IllegalArgumentException("Unsupported input type for MlKitEngine: ${input.javaClass.name}")
        }

        val res = OdometerOcrUtils.extractFromPhotoBitmapRaw(image)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }
}

object OcrUtils {
    fun icrsRectToSnapshotAnnotation(icrs: android.graphics.RectF, srcW: Int, srcH: Int): SnapshotAnnotation? {
        val p1 = IcrsMath.icrsToPixel(icrs.left, icrs.top, srcW, srcH)
        val p2 = IcrsMath.icrsToPixel(icrs.right, icrs.bottom, srcW, srcH)
        val x1 = min(p1.x, p2.x).toInt().coerceIn(0, srcW)
        val y1 = min(p1.y, p2.y).toInt().coerceIn(0, srcH)
        val x2 = max(p1.x, p2.x).toInt().coerceIn(0, srcW)
        val y2 = max(p1.y, p2.y).toInt().coerceIn(0, srcH)
        return if (x1 < x2 && y1 < y2) SnapshotAnnotation(x1, y1, x2, y2, Shape.RECTANGLE, android.graphics.Color.RED, 2) else null
    }


    /**
     * Stateless Native Snapshot Utility: Produces a high-fidelity Base64 JPEG thumbnail.
     * Supports Bitmap, Mat, and BufferSet.Slice sources.
     *
     * @param source The image source (Bitmap, Mat, or BufferSet.Slice).
     * @param sourceRect Optional ROI within the source.
     * @param targetW Requested width for the thumbnail (aspect ratio preserved).
     * @param targetH Requested height for the thumbnail (aspect ratio preserved).
     * @param annotations List of SnapshotAnnotation (pixel Int coords) or RectF (ICRS Float coords). ICRS entries are converted internally using the actual srcW/srcH of the source buffer.
     * @param scratchArgb Optional reusable Bitmap for ARGB workspace.
     * @param scratchYuv Optional reusable BufferSet for YUV processing.
     */
    suspend fun takeSnapshot(
        source: Any,
        sourceRect: Rect? = null,
        targetW: Int = 0,
        targetH: Int = 0,
        annotations: List<Any> = emptyList(),
        scratchArgb: Bitmap? = null,
        scratchYuv: BufferSet? = null
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val srcW: Int
        val srcH: Int
        when (source) {
            is Bitmap -> {
                srcW = source.width
                srcH = source.height
            }
            is org.opencv.core.Mat -> {
                srcW = source.cols()
                srcH = source.rows()
            }
            is BufferSet.Slice -> {
                srcW = source.width
                srcH = source.height
            }
            else -> throw IllegalArgumentException("Unsupported source type: ${source.javaClass.name}")
        }

        val roi = sourceRect ?: Rect(0, 0, srcW, srcH)
        val roiW = roi.width().coerceAtLeast(1)
        val roiH = roi.height().coerceAtLeast(1)
        val sourceAspect = roiW.toFloat() / roiH.toFloat()

        var finalW: Int
        var finalH: Int

        if (targetW > 0 && targetH > 0) {
            val targetAspect = targetW.toFloat() / targetH
            if (targetAspect > sourceAspect) {
                finalH = targetH; finalW = (targetH * sourceAspect).toInt()
            } else {
                finalW = targetW; finalH = (targetW / sourceAspect).toInt()
            }
        } else if (targetW > 0) {
            finalW = targetW; finalH = (targetW / sourceAspect).toInt()
        } else if (targetH > 0) {
            finalH = targetH; finalW = (targetH * sourceAspect).toInt()
        } else {
            finalW = roiW; finalH = roiH
        }

        Log.d("ExperimentPump", "takeSnapshot: buffer pointed at ${srcW}x${srcH} (roi ${roiW}x${roiH}) target size ${targetW}x${targetH} (0=unlimited) final ${finalW}x${finalH}")

        // 2-pixel alignment for YUV
        finalW = ((finalW + 1) / 2) * 2
        finalH = ((finalH + 1) / 2) * 2

        // Safety cap
        finalW = finalW.coerceIn(2, 4000)
        finalH = finalH.coerceIn(2, 3072)

        // Allocation padding (32x2)
        val allocW = ((finalW + 31) / 32) * 32
        val allocH = ((finalH + 1) / 2) * 2

        val bufferSet = scratchYuv ?: BufferSet(allocW, allocH)
        val workspace = if (scratchYuv != null) bufferSet.s else bufferSet.p

        workspace.clear()
        val snapCropId = workspace.createCrop(0, 0, finalW, finalH)
        // Red box crop must be done by caller only (createCrop on the red rect pixel coords after pump optimization), then pass crop[id] (Slice) as source to takeSnapshot. This pattern is required. The pixel-vs-ICRS / ICRS-at-boundary optimization is pump red box only and must not affect alignment or other experiments' ICRS sourceRect usage on full buffers for diagnostic crops. takeSnapshot's internal output crop creation (snapCropId) is unchanged from alignment-tested behavior.

        try {
            when (source) {
                is Bitmap -> {
                    val localScratch = scratchArgb ?: Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(localScratch)
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                    canvas.drawBitmap(source, roi, Rect(0, 0, finalW, finalH), Paint(Paint.FILTER_BITMAP_FLAG))

                    // Direct sync to Mat
                    NativeImageUtils.syncMatFromArgb(localScratch, bufferSet.c[snapCropId].mat)
                    if (scratchArgb == null) localScratch.recycle()
                }
                is org.opencv.core.Mat -> {
                    val safeLeft = max(0, min(roi.left, srcW - 1))
                    val safeTop = max(0, min(roi.top, srcH - 1))
                    val safeW = min(roiW, srcW - safeLeft).coerceAtLeast(1)
                    val safeH = min(roiH, srcH - safeTop).coerceAtLeast(1)
                    val sub = source.submat(org.opencv.core.Rect(safeLeft, safeTop, safeW, safeH))
                    val graySub = if (sub.channels() == 4) {
                        val g = org.opencv.core.Mat()
                        Imgproc.cvtColor(sub, g, Imgproc.COLOR_RGBA2GRAY)
                        g
                    } else sub
                    Imgproc.resize(graySub, bufferSet.c[snapCropId].mat, bufferSet.c[snapCropId].mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)
                    if (graySub !== sub) graySub.release()
                    sub.release()
                }
                is BufferSet.Slice -> {
                    val safeLeft = max(0, min(roi.left, srcW - 1))
                    val safeTop = max(0, min(roi.top, srcH - 1))
                    val safeW = min(roiW, srcW - safeLeft).coerceAtLeast(1)
                    val safeH = min(roiH, srcH - safeTop).coerceAtLeast(1)
                    val subY = source.mat.submat(org.opencv.core.Rect(safeLeft, safeTop, safeW, safeH))
                    Imgproc.resize(subY, bufferSet.c[snapCropId].mat, bufferSet.c[snapCropId].mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)

                    val subUV = source.uvMat.submat(org.opencv.core.Rect(safeLeft / 2, safeTop / 2, safeW / 2, safeH / 2))
                    Imgproc.resize(subUV, bufferSet.c[snapCropId].uvMat, bufferSet.c[snapCropId].uvMat.size(), 0.0, 0.0, Imgproc.INTER_AREA)

                    subY.release(); subUV.release()
                }
            }

            val icrsPixelAnns = emptyList<SnapshotAnnotation>()
            val allAnnsForScale = annotations.filterIsInstance<SnapshotAnnotation>()

            // Annotation scaling
            val scaleX = finalW.toFloat() / roiW.toFloat()
            val scaleY = finalH.toFloat() / roiH.toFloat()
            val scaledAnns = allAnnsForScale.map { ann ->
                ann.copy(
                    x1 = ((ann.x1 - roi.left) * scaleX).toInt(),
                    y1 = ((ann.y1 - roi.top) * scaleY).toInt(),
                    x2 = ((ann.x2 - roi.left) * scaleX).toInt(),
                    y2 = ((ann.y2 - roi.top) * scaleY).toInt()
                )
            }

            NativeImageUtils.drawYuvAnnotations(bufferSet.c[snapCropId].yuv, scaledAnns)
            val b64 = NativeImageUtils.compressYuvToBase64(bufferSet.c[snapCropId].yuv, 80)
            Pair(b64, System.currentTimeMillis() - tStart)
        } finally {
            bufferSet.c[snapCropId].release()
            if (scratchYuv == null) bufferSet.release()
        }
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val icrs = IcrsMath.pixelToIcrs(block.boundingBox.centerX().toFloat(), block.boundingBox.centerY().toFloat(), w, h)
        return icrs.x >= crop.left && icrs.x <= crop.right && icrs.y >= crop.top && icrs.y <= crop.bottom
    }

}
