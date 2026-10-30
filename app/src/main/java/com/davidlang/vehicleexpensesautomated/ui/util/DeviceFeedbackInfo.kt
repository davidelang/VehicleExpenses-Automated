package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.davidlang.vehicleexpensesautomated.BuildConfig

/**
 * Non-sensitive device/app facts for user-edited feedback email bodies.
 * No serials, accounts, GPS, or photo content.
 */
/** Fixed-focus counts. Absent feature, empty id list, or a throw is false. */
fun deviceHasCamera(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        return false
    }
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        val ids = cameraManager?.cameraIdList
        ids != null && ids.isNotEmpty()
    // kept: failure stays on this call so the screen or caller can continue
    } catch (_: Exception) {
        false
    }
}

fun buildFeedbackSeedBody(context: Context): String {
    val abis = Build.SUPPORTED_ABIS.joinToString(", ")
    val hasAnyCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    val rearCount = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            cameraManager?.cameraIdList?.count { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: 0
        } else {
            -1
        }
    // kept: failure stays on this call so the screen or caller can continue
    } catch (_: Exception) {
        -1
    }
    val cameraLine = when {
        rearCount >= 0 -> "Camera: any=$hasAnyCamera, rearCount=$rearCount"
        else -> "Camera: any=$hasAnyCamera"
    }
    return buildString {
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("ABIs: $abis")
        appendLine(cameraLine)
        appendLine()
        appendLine("--- Your message below ---")
        appendLine()
    }
}
