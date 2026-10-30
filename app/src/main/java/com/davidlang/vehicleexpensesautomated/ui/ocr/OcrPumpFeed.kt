package com.davidlang.vehicleexpensesautomated.ui.ocr

import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.RecBufferFeed
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Scheduled pump columns do not call this. */
internal fun recognizeRotGmmQuads(
    grayNativeObj: Long,
    quadPts: FloatArray,
    recBuffer: BufferSet,
    scratchW: Int,
    scratchH: Int,
    landmarkAlphabet: Boolean = false,
): OcrRotGmmOut = ocrRotGmmRecognize(grayNativeObj, quadPts, recBuffer, scratchW, scratchH, landmarkAlphabet)

/**
 * Expand the blue quad to the 40-in-48 source window (4/s on −u/−v, remainder +u/+v),
 * warp that window, then [RecBufferFeed.scaleIsotropicNoPartial] into destW×48.
 * Writes the warp into [scratch]'s `s` plane. [scratch]'s primary must not be [gray].
 * Scheduled pump columns keep their current calls.
 */
internal fun ocrPumpFeedOriented40(
    gray: org.opencv.core.Mat,
    quad: ContentExpandUtils.OrientedQuad,
    scratch: BufferSet,
    recBuffer: BufferSet,
): RecBufferFeed.Result? {
    if (quad.shortAxisBh() < 2f || quad.longAxisBw() < 2f) return null
    val bw = quad.longAxisBw()
    val bh = quad.shortAxisBh()
    val plan = RecBufferFeed.planContent40In48(bw, bh, RecBufferFeed.DEFAULT_REC_H)
    val srcW = plan.srcW
    val srcH = plan.srcH
    growBuffer(scratch, srcW, srcH)
    growBuffer(recBuffer, plan.destW, plan.destH)
    val minusU = plan.marginSrc
    val minusV = plan.marginSrc
    val plusU = srcW - minusU - bw
    val plusV = srcH - minusV - bh
    val warpQuad = ocrGeomExpandQuadFrame(quad, minusU, plusU, minusV, plusV)
    val nativeId = scratch.s.createCrop(0, 0, srcW, srcH)
    val native = scratch.c[nativeId]
    val ok = ContentExpandUtils.warpQuadToHorizontalStrip(gray, warpQuad, native.mat, targetH = 0)
    if (!ok || native.mat.empty()) {
        native.release()
        return null
    }
    recBuffer.p.clear()
    val recId = recBuffer.createCrop(0, 0, plan.destW, plan.destH)
    RecBufferFeed.scaleIsotropicNoPartial(native.mat, recBuffer.c[recId].mat, plan.s)
    native.release()
    val slice = recBuffer.c[recId]
    return RecBufferFeed.Result(
        plan.s,
        plan.marginSrc.roundToInt().coerceAtLeast(0),
        recId,
        slice.width,
        slice.height,
    )
}

/** Allocation failure throws from [BufferSet.resize]. */
private fun growBuffer(set: BufferSet, needW: Int, needH: Int) {
    val w = if (set.width >= needW) set.width else needW
    val h = if (set.height >= needH) set.height else needH
    if (w != set.width || h != set.height) set.resize(w, h)
}

/** Grow blue to the dest-frame parallelogram: −u/−v pad, +u/+v remainder (source px). */
internal fun ocrGeomExpandQuadFrame(
    quad: ContentExpandUtils.OrientedQuad,
    minusU: Float,
    plusU: Float,
    minusV: Float,
    plusV: Float,
): ContentExpandUtils.OrientedQuad {
    if (minusU == 0f && plusU == 0f && minusV == 0f && plusV == 0f) return quad
    val ang = Math.toRadians(quad.uAngleDeg().toDouble())
    val ux = cos(ang).toFloat()
    val uy = sin(ang).toFloat()
    var vx = -uy
    var vy = ux
    if (vy < 0f) {
        vx = -vx
        vy = -vy
    }
    val pts = quad.copyPts()
    val us = FloatArray(4) { i -> pts[i * 2] * ux + pts[i * 2 + 1] * uy }
    val vs = FloatArray(4) { i -> pts[i * 2] * vx + pts[i * 2 + 1] * vy }
    var uMin = us[0]
    var uMax = us[0]
    var vMin = vs[0]
    var vMax = vs[0]
    for (i in 1 until 4) {
        if (us[i] < uMin) uMin = us[i]
        if (us[i] > uMax) uMax = us[i]
        if (vs[i] < vMin) vMin = vs[i]
        if (vs[i] > vMax) vMax = vs[i]
    }
    val uMid = (uMin + uMax) * 0.5f
    val vMid = (vMin + vMax) * 0.5f
    val out = FloatArray(8)
    for (i in 0 until 4) {
        val du = if (us[i] <= uMid) -minusU else plusU
        val dv = if (vs[i] <= vMid) -minusV else plusV
        out[i * 2] = pts[i * 2] + du * ux + dv * vx
        out[i * 2 + 1] = pts[i * 2 + 1] + du * uy + dv * vy
    }
    return ContentExpandUtils.OrientedQuad(out)
}
