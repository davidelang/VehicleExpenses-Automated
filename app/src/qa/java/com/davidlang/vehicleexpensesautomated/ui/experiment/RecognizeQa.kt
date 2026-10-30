package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.graphics.Bitmap
import android.graphics.Rect
import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat

suspend fun NativePaddleEngine.recognizeQa(input: Any): OcrResult = withContext(Dispatchers.IO) {
    val t0 = System.currentTimeMillis()
    val w: Int; val h: Int
    when (input) {
        is Bitmap -> { w = input.width; h = input.height }
        is BufferSet.Slice -> { w = input.width; h = input.height }
        is Mat -> { w = input.cols(); h = input.rows() }
        else -> throw IllegalArgumentException("Unsupported input type for recognize")
    }

    if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available", imageWidth = w, imageHeight = h)

    val res = processOcr(input, NativePaddleEngine.sharedRecognizerV3, NativePaddleEngine.dictionaryV3)
    OcrResult(
        engineName = "Paddle V3 Greedy",
        executionTimeMs = System.currentTimeMillis() - t0,
        debugText = res.text,
        textBlocks = listOf(TextBlock(res.text, Rect(0, 0, w, h), confidence = res.confidence)),
        imageWidth = w,
        imageHeight = h,
        metadata = res.metadata,
        perCharProbs = res.perCharProbs
    )
}
