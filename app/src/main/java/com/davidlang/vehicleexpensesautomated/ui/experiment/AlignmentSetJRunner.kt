package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.ui.util.IcrsMath
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarnessResult
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.RefinementTrace
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * Single-photo **alignment experiment Set J** runner for batch import.
 *
 * Implements the same sequence as [runExperiment] for the Set J column:
 * Set A vehicle-ID lock → Set J rotate/discover/align → [runPaddleValleyIterative]
 * with Raw+Bin-Trials, useCharAware, pipelineKey=set_j.
 *
 * Does **not** go through [com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness.runAutoFillPipeline].
 */
data class AlignmentSetJResult(
    val vehicleId: Int?,
    val vehicleName: String?,
    val odometer: String?,
    val error: String? = null,
)

object AlignmentSetJRunner {
    private const val TAG = "AlignmentSetJRunner"

    /**
     * @param forcedVehicleId if non-null, skip Tier-1 ID and use this vehicle (pending answers).
     */
    suspend fun runOnePhoto(
        context: Context,
        photoFile: File,
        vehicles: List<Vehicle>,
        forcedVehicleId: Int? = null,
    ): AlignmentSetJResult = withContext(Dispatchers.IO) {
        if (!photoFile.isFile) {
            return@withContext AlignmentSetJResult(null, null, null, "Missing file ${photoFile.name}")
        }

        val active = vehicles.filter { !it.deleted }
        val cachedRefs = active.mapNotNull { vehicle ->
            val refUrl = vehicle.referenceDashPhotoUrl ?: return@mapNotNull null
            val (refW, refH) = probeReferenceDimensions(context, refUrl)
            val curated = getFullLandmarksFromJson(vehicle.landmarkTextBlocksJson, "ML Kit", refW, refH)
            ReferenceCache(vehicle, curated, refW, refH)
        }
        if (cachedRefs.isEmpty()) {
            return@withContext AlignmentSetJResult(null, null, null, "No vehicles with reference dash")
        }

        val vehicleBufferSets = mutableMapOf<Int, BufferSet>()
        cachedRefs.forEach { ref ->
            val icrsRect = if (
                ref.vehicle.odometerCropLeft != null && ref.vehicle.odometerCropTop != null &&
                ref.vehicle.odometerCropRight != null && ref.vehicle.odometerCropBottom != null
            ) {
                RectF(
                    ref.vehicle.odometerCropLeft!!, ref.vehicle.odometerCropTop!!,
                    ref.vehicle.odometerCropRight!!, ref.vehicle.odometerCropBottom!!,
                )
            } else {
                IcrsMath.fullImageIcrsRect(ref.width, ref.height)
            }
            val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, ref.width, ref.height)
            val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, ref.width, ref.height)
            val srcW = (p2.x - p1.x).toInt()
            val srcH = (p2.y - p1.y).toInt()
            val targetW = if (srcW % 32 == 0) srcW else (srcW / 32 + 1) * 32
            val targetH = if (srcH % 2 == 0) srcH else (srcH / 2 + 1) * 2
            if (targetW > 0 && targetH > 0) {
                vehicleBufferSets[ref.vehicle.id] = BufferSet(targetW, targetH)
                listOf(NativePaddleEngine.bufferSetA, NativePaddleEngine.bufferSetB).forEach { set ->
                    set.p.createCrop(
                        icrsRect.left, icrsRect.top, icrsRect.width(), icrsRect.height(),
                        id = ref.vehicle.id,
                    )
                }
            }
        }

        val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, photoFile.absolutePath)
        if (imgW <= 0 || imgH <= 0) {
            return@withContext AlignmentSetJResult(null, null, null, "Bad dimensions")
        }

        NativePaddleEngine.bufferSetA.resize(imgW, imgH)
        NativePaddleEngine.bufferSetB.resize(imgW, imgH)
        ImageIngestionProvider.ingestFromFile(context, photoFile.absolutePath, NativePaddleEngine.bufferSetA.p)
        NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
        NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)

        // Experiment deskew: calculateAverageTextAngle on A (not paddle-optimized-only Quick Fill path)
        val deskewResA = OdometerOcrUtils.calculateAverageTextAngle(NativePaddleEngine.bufferSetA.p)

        suspend fun rotate(set: BufferSet, angle: Float) {
            val src = set.p.mat
            val dst = set.s.mat
            val matrixLocal = android.graphics.Matrix()
            matrixLocal.postRotate(-angle, src.cols() / 2f, src.rows() / 2f)
            val values = FloatArray(9)
            matrixLocal.getValues(values)
            val rotMat = Mat(2, 3, CvType.CV_64F)
            rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
            rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())
            Imgproc.warpAffine(
                src, dst, rotMat, src.size(),
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0),
            )
            val srcUv = set.p.uvMat
            val dstUv = set.s.uvMat
            val uvScaleMat = rotMat.clone()
            uvScaleMat.put(0, 2, rotMat.get(0, 2)[0] / 2.0)
            uvScaleMat.put(1, 2, rotMat.get(1, 2)[0] / 2.0)
            Imgproc.warpAffine(
                srcUv, dstUv, uvScaleMat, srcUv.size(),
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(128.0, 128.0),
            )
            set.flip()
            rotMat.release()
            uvScaleMat.release()
        }

        // --- Set A: lock vehicle ID (experiment report rule) ---
        val globalWinnerId: Int? = if (forcedVehicleId != null) {
            forcedVehicleId
        } else {
            NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
            NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)
            rotate(NativePaddleEngine.bufferSetB, deskewResA.mlAngle)
            val (_, queryLandmarksA) = performLandmarkDiscovery(NativePaddleEngine.bufferSetB.p, context)
            val vetoA = ImageAlignmentUtils.performTier1Veto(
                queryLandmarksA, cachedRefs.map { it.vehicle }, "ML Kit",
            )
            vetoA.entries.find { !it.value.isVetoed }?.key
        }

        val globalWinnerRef = cachedRefs.find { it.vehicle.id == globalWinnerId }
            ?: return@withContext AlignmentSetJResult(
                null, null, null, "Vehicle not identified",
            )

        // --- Set J: paddleOptimizedAngle, discovery, align, valley iterative ---
        NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
        NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)
        rotate(NativePaddleEngine.bufferSetB, deskewResA.paddleOptimizedAngle)

        val (_, queryLandmarksJ) = performLandmarkDiscovery(NativePaddleEngine.bufferSetB.p, context)
        val queryLandmarksPrimary = ImageAlignmentUtils.disambiguateLandmarks(
            queryLandmarksJ, globalWinnerRef.curatedLandmarks,
        )
        val alignRes = ImageAlignmentUtils.anchorAlign(
            NativePaddleEngine.bufferSetB,
            globalWinnerRef.curatedLandmarks,
            queryLandmarksPrimary,
            globalWinnerRef.vehicle,
            globalWinnerRef.width,
            globalWinnerRef.height,
            imgW,
            imgH,
            null,
        )
        if (!alignRes.success) {
            Log.w(TAG, "align failed ${photoFile.name}: ${alignRes.metadata}")
            // Experiment still runs extraction when winner exists; keep going if buffer usable
        }

        if (NativePaddleEngine.bufferSetB.p.mat.cols() != imgW ||
            NativePaddleEngine.bufferSetB.p.mat.rows() != imgH
        ) {
            NativePaddleEngine.bufferSetB.resize(imgW, imgH)
        }

        val paddleEngine = NativePaddleEngine(context)
        val experimentRecSet320x48 = BufferSet(320, 48)
        val experimentDetSet512x128 = BufferSet(512, 128)
        val hMap = mutableMapOf<String, OcrHarnessResult>()
        val refinementTraces = mutableMapOf<String, RefinementTrace>()
        val iterativeStages = listOf("Raw", "Bin-Trials")

        try {
            runPaddleValleyIterative(
                "Set J (CC Speedup) Paddle",
                NativePaddleEngine.bufferSetB,
                imgW,
                imgH,
                globalWinnerRef,
                vehicleBufferSets,
                experimentDetSet512x128,
                experimentRecSet320x48,
                paddleEngine,
                hMap,
                refinementTraces,
                isNumeric = true,
                stages = iterativeStages,
                extraImages = emptyMap(),
                useCharAware = true,
                pipelineKey = "set_j",
            )
        } finally {
            experimentRecSet320x48.release()
            experimentDetSet512x128.release()
            vehicleBufferSets.values.forEach { it.release() }
        }

        val harness = hMap["Set J (CC Speedup) Paddle"]
        val odo = harness?.odometerValue
        Log.i(
            TAG,
            "SetJ ${photoFile.name} vehicle=${globalWinnerRef.vehicle.id} " +
                "${globalWinnerRef.vehicle.name} odo=$odo",
        )
        AlignmentSetJResult(
            vehicleId = globalWinnerRef.vehicle.id,
            vehicleName = globalWinnerRef.vehicle.name,
            odometer = odo,
            error = null,
        )
    }
}
