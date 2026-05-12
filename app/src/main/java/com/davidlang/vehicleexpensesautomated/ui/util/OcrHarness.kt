package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.JsonObject

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context): OcrResult {
        val rawResult = MlKitEngine().recognize(bitmap)
        
        val cleanedBlocks = rawResult.textBlocks.map { block ->
            block.copy(text = OdometerOcrUtils.cleanLandmarkString(block.text))
        }.filter { it.text.length > 1 }
        
        val sanitizedResult = rawResult.copy(
            textBlocks = cleanedBlocks,
            debugText = cleanedBlocks.joinToString(" ") { it.text }
        )

        return sanitizedResult
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
            val enginesList = mutableListOf<OcrEngine>(MlKitEngine())
            val paddle = NativePaddleEngine(context, variant = "V3")
            if (paddle.isAvailable) enginesList.add(paddle)
            val results = enginesList.associate { engine ->
                engine.name to engine.recognize(bitmap)
            }
            return results
    }

}

data class OcrHarnessResult(
    val htmlHeader: String,
    val htmlCell: String,
    val jsonSection: JsonObject,
    val odometerValue: String?,
    val thumbB64: String? = null
)

data class HarnessRunDef(
    val strategy: OcrEngineStrategy,
    val buffer: Any,
    val width: Int,
    val height: Int
)

interface OcrEngineStrategy {
    val displayName: String
    suspend fun execute(
        masterBuffer: Any,
        masterW: Int,
        masterH: Int,
        report: ReportCollector
    ): OcrHarnessResult
}

interface ReportCollector {
    fun add(engineName: String, result: OcrHarnessResult)
}
