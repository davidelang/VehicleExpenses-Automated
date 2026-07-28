package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.PumpCostVolUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single-photo **pump experiment Set I** result for batch import.
 *
 * Uses the same Set I hybrid extraction body as experiment `procI`
 * ([PumpCostVolUtils.runSetICostVolExtraction] — port of experiment D+E+G hybrid).
 * Ingest mirrors experiment: probe, resize shared buffer, [ImageIngestionProvider.ingestFromFile].
 */
data class PumpSetIResult(
    val cost: String?,
    val volume: String?,
    val error: String? = null,
)

object PumpSetIRunner {
    private const val TAG = "PumpSetIRunner"

    suspend fun runOnePhoto(
        context: Context,
        photoFile: File,
    ): PumpSetIResult = withContext(Dispatchers.IO) {
        if (!photoFile.isFile) {
            return@withContext PumpSetIResult(null, null, "Missing ${photoFile.name}")
        }
        val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, photoFile.absolutePath)
        if (imgW <= 0 || imgH <= 0) {
            return@withContext PumpSetIResult(null, null, "Bad dimensions")
        }
        val master = NativePaddleEngine.bufferSetA
        master.resize(imgW, imgH)
        ImageIngestionProvider.ingestFromFile(context, photoFile.absolutePath, master.p)

        val paddleEngine = NativePaddleEngine(context, "Numeric")
        val recBuffer = NativePaddleEngine.recBufferSet
        // Experiment Set I body (deskew + G/D/E hybrid + classify) — not Quick Fill G--
        val cv = PumpCostVolUtils.runSetICostVolExtraction(
            master, paddleEngine, recBuffer, master.width, master.height,
        )
        val cost = cv.cost.takeIf { it != "N/A" && it.isNotBlank() }
        val volume = cv.vol.takeIf { it != "N/A" && it.isNotBlank() }
        if (cost == null && volume == null) {
            Log.w(TAG, "Set I unreadable ${photoFile.name}")
            return@withContext PumpSetIResult(null, null, "Could not read pump display (Set I)")
        }
        Log.i(TAG, "SetI ${photoFile.name} cost=$cost vol=$volume")
        PumpSetIResult(cost = cost, volume = volume)
    }
}
