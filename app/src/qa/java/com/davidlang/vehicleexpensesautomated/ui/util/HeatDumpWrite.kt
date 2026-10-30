package com.davidlang.vehicleexpensesautomated.ui.util

import java.io.File

internal fun writeHeatDumpU8z(
    file: File,
    u8: ByteArray,
    w: Int,
    h: Int,
    meta: Map<String, Any?>,
): Boolean = HeatmapU8Dump.writeU8z(file, u8, w, h, meta)
