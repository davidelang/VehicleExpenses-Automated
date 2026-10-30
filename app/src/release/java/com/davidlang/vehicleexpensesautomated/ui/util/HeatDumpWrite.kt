package com.davidlang.vehicleexpensesautomated.ui.util

import java.io.File

/** Shipped builds do not write heat dumps. Qa calls HeatmapU8Dump. */
@Suppress("UNUSED_PARAMETER")
internal fun writeHeatDumpU8z(
    file: File,
    u8: ByteArray,
    w: Int,
    h: Int,
    meta: Map<String, Any?>,
): Boolean = false
