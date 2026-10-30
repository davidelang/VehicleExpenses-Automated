package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Write GPS + orientation + capture datetime EXIF onto CameraX-saved JPEGs
 * (file path or MediaStore content URI). Errors are logged only — photo is already saved.
 */


    /**
     * Stamp DateTime* and/or OffsetTime* when [meta] is missing them.
     * Does not change orientation or GPS. Returns true when attributes were saved.
     */
    fun PhotoExifWriter.stampIfCaptureIncomplete(path: String, meta: PhotoExifMeta): Boolean {
        val missingTime = meta.source != "exif_original" && meta.source != "exif_datetime"
        val missingOffset = meta.offsetTimeOriginal.isNullOrBlank() && meta.offsetTime.isNullOrBlank()
        if (!missingTime && !missingOffset) return false
        val timestampMs = meta.timestampMs ?: System.currentTimeMillis()
        try {
            val file = File(path)
            if (!file.isFile) {
                Log.w(TAG, "Not a file: $path")
                return false
            }
            val exif = ExifInterface(path)
            var changed = false
            if (missingTime) {
                val wall = EXIF_DT.format(Date(timestampMs))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, wall)
                exif.setAttribute(ExifInterface.TAG_DATETIME, wall)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, wall)
                changed = true
            }
            if (missingTime || missingOffset) {
                val offset = formatOffset(timestampMs)
                try {
                    if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL).isNullOrBlank()) {
                        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                        changed = true
                    }
                    if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME).isNullOrBlank()) {
                        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                        changed = true
                    }
                    if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED).isNullOrBlank()) {
                        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
                        changed = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "OffsetTime tags not set: ${e.message}")
                }
            }
            if (!changed) return false
            exif.saveAttributes()
            Log.i(
                TAG,
                "EXIF datetime stamped path=$path ts=$timestampMs missingTime=$missingTime",
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "stampIfCaptureIncomplete failed for $path: ${e.message}")
            return false
        }
    }


    /**
     * After a zip lands in dash/pump storage, stamp images whose mtime changed
     * (or that are new) when capture datetime or offset is missing.
     */
    fun PhotoExifWriter.stampChangedImagesMissingCaptureTime(dir: File, mtimesBefore: Map<String, Long>) {
        if (!dir.isDirectory) return
        val exts = setOf("jpg", "jpeg", "png", "dng")
        dir.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            if (f.extension.lowercase(Locale.US) !in exts) return@forEach
            val prev = mtimesBefore[f.absolutePath]
            if (prev != null && prev == f.lastModified()) return@forEach
            val meta = PhotoExifMetaReader.read(f.absolutePath)
            stampIfCaptureIncomplete(f.absolutePath, meta)
        }
    }

