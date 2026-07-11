package com.davidlang.vehicleexpensesautomated.ui.util

/**
 * Precision-path configs for Set G campaign.
 * Models load from filesDir/prec_paths/<id>/det_<abi>.nb, rec_v3_<abi>.nb, rec_numeric_<abi>.nb
 * Missing dir falls back to assets mono models (baseline).
 *
 * Honest device support (no silent remap to another path):
 * - baseline, int8_*, uint8_*: arm + x86 where models are real
 * - float_fp16: arm only (x86 has no real fp16 compute graphs)
 *
 * Feed contracts:
 * - float: ImageNet/rec float_norm
 * - int8: host xor-128 into ByteArray + setData (kInt8)
 * - uint8: host raw greyscale copy into ByteArray + setData (kUInt8) — no xor
 */
enum class PrecisionPath(
    val id: String,
    val detInt8Input: Boolean,
    val recInt8Input: Boolean,
    val label: String,
    /** Path has correct component models for this ABI family. */
    val armSupported: Boolean = true,
    val x86Supported: Boolean = true,
    /** Raw greyscale feed (kUInt8); mutually exclusive with int8 feed flags. */
    val detUInt8Input: Boolean = false,
    val recUInt8Input: Boolean = false,
) {
    BASELINE(
        "baseline",
        detInt8Input = false,
        recInt8Input = false,
        label = "float_norm det+rec fp32 compute",
    ),
    INT8_FP32(
        "int8_fp32",
        detInt8Input = true,
        recInt8Input = true,
        label = "int8 xor det/rec fp32 compute float heatmap",
    ),
    INT8_FP32_U8(
        "int8_fp32_u8",
        detInt8Input = true,
        recInt8Input = true,
        label = "int8 xor det fp32 compute uint8 heatmap",
    ),
    INT8_FP16_F32(
        "int8_fp16_f32",
        detInt8Input = true,
        recInt8Input = true,
        // arm: int8_to_fp16 + fp16 convs; x86: int8_to_fp16 + fp16_to_fp32 + float convs
        label = "int8 xor via int8_to_fp16 (arm fp16 compute; x86 float after cast)",
        x86Supported = true,
    ),
    INT8_FP16_U8(
        "int8_fp16_u8",
        detInt8Input = true,
        recInt8Input = true,
        label = "int8 xor via int8_to_fp16 + uint8 heatmap",
        x86Supported = true,
    ),
    FLOAT_FP16(
        "float_fp16",
        detInt8Input = false,
        recInt8Input = false,
        // arm: real fp16 compute; x86: enable_fp16 does not emit fp16 convs → PATH_SKIP
        label = "float_norm det fp16 compute",
        x86Supported = false,
    ),
    UINT8_FP32(
        "uint8_fp32",
        detInt8Input = false,
        recInt8Input = false,
        detUInt8Input = true,
        recUInt8Input = true,
        label = "uint8 raw det/rec via uint8_to_fp32 (no host xor) float heatmap",
        x86Supported = true,
    ),
    UINT8_FP16_F32(
        "uint8_fp16_f32",
        detInt8Input = false,
        recInt8Input = false,
        detUInt8Input = true,
        recUInt8Input = true,
        // arm: uint8_to_fp16 + fp16 convs; x86: uint8_to_fp16 + fp16_to_fp32 + float
        label = "uint8 raw via uint8_to_fp16 (arm fp16 compute; x86 float after cast)",
        x86Supported = true,
    ),
    UINT8_FP16_U8(
        "uint8_fp16_u8",
        detInt8Input = false,
        recInt8Input = false,
        detUInt8Input = true,
        recUInt8Input = true,
        label = "uint8 raw via uint8_to_fp16 + uint8 heatmap",
        x86Supported = true,
    );

    fun isSupportedOnDevice(isArm: Boolean): Boolean =
        if (isArm) armSupported else x86Supported

    companion object {
        /**
         * Resolve path id. Unknown ids throw — never silent-fallback to baseline
         * (that made campaign jsonl claim path=uint8_* while active_path=baseline).
         */
        fun fromId(id: String): PrecisionPath =
            values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException(
                    "Unknown PrecisionPath id='$id' " +
                        "(known: ${values().joinToString { it.id }})"
                )

        /**
         * @deprecated Silent remap was lying about which path ran (e.g. float_fp16 → int8_fp32).
         * Use [isSupportedOnDevice] + skip unsupported paths instead.
         */
        @Deprecated("Do not remap; skip unsupported paths via isSupportedOnDevice")
        fun forDevice(path: PrecisionPath, isArm: Boolean): PrecisionPath = path
    }
}
