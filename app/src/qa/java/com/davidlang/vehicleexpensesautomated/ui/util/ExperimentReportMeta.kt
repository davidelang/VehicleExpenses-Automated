package com.davidlang.vehicleexpensesautomated.ui.util

import android.os.Build
import com.davidlang.vehicleexpensesautomated.BuildConfig

/**
 * Common fields for experiment pump/align JSON headers so reports identify
 * which paddle light SOs were baked into the APK (not only git describe).
 */
object ExperimentReportMeta {
    /** Compact JSON fragment (no outer braces) for embedding in a header object. */
    fun jsonFields(): String {
        val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        // Escape not needed: hex hashes and controlled BuildConfig strings.
        return buildString {
            // Prefer last-loaded pack (precision A/B suite); fall back to ABI default.
            val pathId = NativePaddleEngine.activeProductPathId
            val prodDir = NativePaddleEngine.activeProductDir
            append("  \"version\": \"${BuildConfig.VERSION_NAME}\",\n")
            append("  \"paddle_so_stamp\": \"${BuildConfig.PADDLE_SO_STAMP}\",\n")
            append("  \"primary_abi\": \"$primary\",\n")
            append("  \"product_path\": \"$pathId\",\n")
            append("  \"product_dir\": \"$prodDir\",\n")
            append("  \"paddle_so\": {\n")
            append("    \"x86_64\": \"${BuildConfig.PADDLE_SO_X86_64}\",\n")
            append("    \"arm64-v8a\": \"${BuildConfig.PADDLE_SO_ARM64_V8A}\",\n")
            append("    \"armeabi-v7a\": \"${BuildConfig.PADDLE_SO_ARMEABI_V7A}\"\n")
            append("  }")
        }
    }
}
