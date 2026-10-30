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
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs

/**
 * Represents a single hunk of text found by an OCR engine.
 */


data class AlignmentTraceResult(
    val success: Boolean,
    val timeMs: Long,
    val alignedImageBase64: String,
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


data class ProcessedPhotoResult(
    val fileName: String,
    val pathways: Map<String, PhotoPathwayResult>, // Keys: "set_a", "set_b", "standard"
    val vehicleResultsMap: Map<Int, SingleVehicleResult>,
    val primaryVetoResults: Map<Int, VetoResult>
)

data class PhotoPathwayResult(
    val winnerName: String,
    val tDeskewTotal: Long,
    val tDiscoveryTotal: Long,
    val deskewedBase64: String,
    val discoveryResult: OcrResult,
    val discoveryLandmarks: List<TextBlock>,
    val harnessResults: Map<String, OcrHarnessResult> = emptyMap(),
    val tOdoReadMs: Long = 0,
    val similarity: Map<String, String> = emptyMap(),
)


    fun OcrUtils.bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
    }

