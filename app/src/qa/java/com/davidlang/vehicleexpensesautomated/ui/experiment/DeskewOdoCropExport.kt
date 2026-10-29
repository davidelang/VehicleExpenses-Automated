package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.ui.util.IcrsMath
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrUtils
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Device export for deskew review HTML: per dash photo × angle bin → odometer crop JPEG
 * after deskew + vehicle lock + ICRS odo crop (same geometry as alignment Set J).
 *
 * Manifest: files/deskew_odo_export/dash_bins_for_device.json
 * Crops:    files/deskew_odo_export/crops/<preview_id>/odo_bin_<bin>.jpg
 *
 * logcat: DeskewOdoCrop
 */
object DeskewOdoCropExport {
    private const val TAG = "DeskewOdoCrop"

    data class Result(val outDir: File, val nOk: Int, val nFail: Int, val message: String)

    suspend fun run(
        context: Context,
        vehicleRepository: VehicleRepository,
        /** If non-null, only process these basenames (e.g. [SelectedSamplePhotos.DASH]). */
        allowNames: Collection<String>? = null,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val ext = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("No external files dir")
        // Manifest may be adb-pushed to either location (shell push often cannot own
        // deskew_odo_export/ with the setgid group adb needs for later pull).
        val manifest = listOf(
            File(ext, "deskew_odo_export/dash_bins_for_device.json"),
            File(ext, "dash_bins_for_device.json"),
        ).firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "Missing dash_bins_for_device.json under ${ext.absolutePath} " +
                    "(push to files/ or files/deskew_odo_export/)",
            )
        // Create export tree *as the app* so dirs inherit files/ setgid (ext_data_rw)
        // and remain adb-listable like heatmap_stage/.
        val base = File(ext, "deskew_odo_export").also { it.mkdirs(); worldOpen(it) }
        val files = JSONObject(manifest.readText()).getJSONObject("files")
        val dashDir = File(ext, "dash_photos").also { it.mkdirs(); worldOpen(it) }
        val pumpDir = File(ext, "pump_photos")

        val vehicles = vehicleRepository.getAllVehicles().first()
        val cachedRefs = vehicles.mapNotNull { vehicle ->
            val refUrl = vehicle.referenceDashPhotoUrl ?: return@mapNotNull null
            val (refW, refH) = probeReferenceDimensions(context, refUrl)
            val curated = getFullLandmarksFromJson(vehicle.landmarkTextBlocksJson, "ML Kit", refW, refH)
            if (curated.isEmpty()) null
            else ReferenceCache(vehicle, curated, refW, refH)
        }
        if (cachedRefs.isEmpty()) {
            throw IllegalStateException("No vehicle refs with landmarks")
        }
        onLog("vehicles_usable=${cachedRefs.size} manifest=${manifest.absolutePath}")

        val cropRoot = File(base, "crops").also { it.mkdirs(); worldOpen(it) }
        var ok = 0
        var fail = 0
        val allow = allowNames?.toSet()
        val names = files.keys().asSequence()
            .filter { allow == null || it in allow }
            .toList()
            .sorted()
        if (names.isEmpty()) {
            throw IllegalStateException(
                "No manifest entries after allowlist filter " +
                    "(allow=${allow?.size ?: "null"}, manifest_keys=${files.length()})",
            )
        }
        names.forEachIndexed { idx, name ->
            onProgress(idx + 1, names.size, name)
            val photo = File(dashDir, name).takeIf { it.isFile }
                ?: File(pumpDir, name).takeIf { it.isFile }
            if (photo == null) {
                onLog("MISSING $name")
                fail++
                return@forEachIndexed
            }
            val arr = files.getJSONArray(name)
            try {
                val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, photo.absolutePath)
                NativePaddleEngine.bufferSetA.resize(imgW, imgH)
                NativePaddleEngine.bufferSetB.resize(imgW, imgH)
                ImageIngestionProvider.ingestFromFile(context, photo.absolutePath, NativePaddleEngine.bufferSetA.p)

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val angle = o.getDouble("angle_raw_mean").toFloat()
                    val bin = o.getDouble("angle_bin")
                    val previewId = o.optString("preview_id", name)
                    val outSub = File(cropRoot, previewId).also { it.mkdirs(); worldOpen(it) }
                    val binLabel = String.format("%.1f", bin).replace("-", "m")
                    val outFile = File(outSub, "odo_bin_$binLabel.jpg")

                    NativePaddleEngine.bufferSetA.p.mat.copyTo(NativePaddleEngine.bufferSetB.p.mat)
                    NativePaddleEngine.bufferSetA.p.uvMat.copyTo(NativePaddleEngine.bufferSetB.p.uvMat)
                    rotateBuffer(NativePaddleEngine.bufferSetB, angle)

                    val (_, queryLandmarks) = performLandmarkDiscovery(
                        NativePaddleEngine.bufferSetB.p,
                        context,
                    )
                    val primaryVeto = ImageAlignmentUtils.performTier1Veto(
                        queryLandmarks,
                        cachedRefs.map { it.vehicle },
                        "ML Kit",
                    )
                    val winnerId = primaryVeto.entries.find { !it.value.isVetoed }?.key
                        ?: primaryVeto.keys.firstOrNull()
                    val ref = cachedRefs.find { it.vehicle.id == winnerId }
                    if (ref == null) {
                        onLog("NO_VEHICLE $name @$bin")
                        fail++
                        continue
                    }

                    val alignRes = ImageAlignmentUtils.anchorAlign(
                        NativePaddleEngine.bufferSetB,
                        ref.curatedLandmarks,
                        ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, ref.curatedLandmarks),
                        ref.vehicle,
                        ref.width,
                        ref.height,
                        NativePaddleEngine.bufferSetB.width,
                        NativePaddleEngine.bufferSetB.height,
                    )
                    if (!alignRes.success) {
                        onLog("ALIGN_FAIL $name @$bin ${alignRes.message}")
                        fail++
                        continue
                    }

                    val vehicle = ref.vehicle
                    val icrs = if (
                        vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null &&
                        vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null
                    ) {
                        RectF(
                            vehicle.odometerCropLeft!!,
                            vehicle.odometerCropTop!!,
                            vehicle.odometerCropRight!!,
                            vehicle.odometerCropBottom!!,
                        )
                    } else {
                        IcrsMath.fullImageIcrsRect(
                            NativePaddleEngine.bufferSetB.width,
                            NativePaddleEngine.bufferSetB.height,
                        )
                    }
                    NativePaddleEngine.bufferSetB.p.createCrop(
                        icrs.left, icrs.top, icrs.width(), icrs.height(), id = vehicle.id,
                    )
                    val cropSlice = NativePaddleEngine.bufferSetB.c[vehicle.id]
                    val (b64, _) = OcrUtils.takeSnapshot(
                        source = cropSlice,
                        sourceRect = null,
                        targetW = 480,
                        targetH = 0,
                        annotations = emptyList(),
                        scratchArgb = null,
                        scratchYuv = NativePaddleEngine.bufferSetB,
                    )
                    if (b64.isNotBlank()) {
                        FileOutputStream(outFile).use {
                            it.write(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                        }
                        worldOpen(outFile)
                        ok++
                        onLog("OK $name $bin")
                    } else {
                        fail++
                        onLog("EMPTY_SNAP $name @$bin")
                    }
                    try {
                        cropSlice.release()
                    } catch (_: Throwable) {
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "fail $name", t)
                onLog("FAIL $name ${t.message}")
                fail++
            }
        }
        worldOpen(cropRoot)
        val msg = "odo crop export ok=$ok fail=$fail out=${cropRoot.absolutePath}"
        Log.i(TAG, msg)
        onLog(msg)
        Result(cropRoot, ok, fail, msg)
    }

    /** Make dirs/files adb-shell readable (group/other), matching heatmap_stage dumps. */
    private fun worldOpen(f: File) {
        f.setReadable(true, /* ownerOnly = */ false)
        f.setWritable(true, false)
        if (f.isDirectory) f.setExecutable(true, false)
        else f.setExecutable(false, false)
    }

    /** App deskew: postRotate(-angle). */
    private fun rotateBuffer(set: BufferSet, angle: Float) {
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
            src, dst, rotMat, Size(src.cols().toDouble(), src.rows().toDouble()),
            Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, Scalar(0.0),
        )
        val srcUv = set.p.uvMat
        val dstUv = set.s.uvMat
        val uvScaleMat = rotMat.clone()
        uvScaleMat.put(0, 2, rotMat.get(0, 2)[0] / 2.0)
        uvScaleMat.put(1, 2, rotMat.get(1, 2)[0] / 2.0)
        Imgproc.warpAffine(
            srcUv, dstUv, uvScaleMat, Size(srcUv.cols().toDouble(), srcUv.rows().toDouble()),
            Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, Scalar(128.0, 128.0),
        )
        set.flip()
        rotMat.release()
        uvScaleMat.release()
    }
}
