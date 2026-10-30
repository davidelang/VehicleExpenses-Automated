package com.davidlang.vehicleexpensesautomated.ui.util
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine.Companion.bufferSetA
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine.DetectionResult

        /** Deskew / Hough angle from tensor or tiled host heat. */
        fun DetectionResult.deskewAngleCpp(threshold: Float = 0.20f): Float {
            val scratch = bufferSetA.s.mat
            outputTensor?.let { return NativeImageUtils.heatmapToAngle(it, threshold, scratch) }
            heatU8?.let {
                return NativeImageUtils.heatmapToAngleU8(it, width, height, threshold, scratch)
            }
            Log.w("PaddleLite", "deskew0:no_heat")
            return 0f
        }

