package com.davidlang.vehicleexpensesautomated.ui.ocr

import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet

/** One row per blue from [ocrRotGmmRecognize]. Crop id -1 means nothing to snap or release. */
class OcrRotGmmOut(
    @JvmField val asis: Array<String>,
    @JvmField val asisProbs: Array<String>,
    @JvmField val digits: Array<String>,
    @JvmField val digitsProbs: Array<String>,
    @JvmField val recW: IntArray,
    @JvmField val recH: IntArray,
    @JvmField val recCropId: IntArray,
)

external fun ocrRotGmmRecognize(
    grayNativeObj: Long,
    quadPts: FloatArray,
    recBuffer: BufferSet,
    scratchW: Int,
    scratchH: Int,
    landmarkAlphabet: Boolean,
): OcrRotGmmOut
