package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

private const val TAG = "ZipExtractUtils"

object ZipExtractUtils {

    /**
     * Resolve a safe output path for a ZIP entry under [targetDir].
     * Returns null when the entry name is unsafe (zip-slip / traversal).
     */
    fun resolveSafeZipEntryFile(
        targetDir: File,
        entryName: String,
        flattenToBasename: Boolean = false
    ): File? {
        if (entryName.contains('\u0000')) return null
        val name = if (flattenToBasename) entryName.substringAfterLast('/') else entryName
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.startsWith("/") || name.startsWith("\\")) return null
        val segments = name.split('/', '\\')
        if (segments.any { it == ".." }) return null
        val file = File(targetDir, name)
        return try {
            val targetCanonical = targetDir.canonicalFile
            val resolvedCanonical = file.canonicalFile
            val targetPath = targetCanonical.path
            val resolvedPath = resolvedCanonical.path
            if (resolvedPath == targetPath || resolvedPath.startsWith("$targetPath${File.separator}")) {
                file
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract all entries from [zis] into [targetDir]. Fails closed on the first unsafe entry.
     */
    fun extractZipStreamToDir(
        zis: ZipInputStream,
        targetDir: File,
        flattenToBasename: Boolean = false
    ): Boolean {
        var entry = zis.nextEntry
        while (entry != null) {
            val file = resolveSafeZipEntryFile(targetDir, entry.name, flattenToBasename)
            if (file == null) {
                Log.w(TAG, "Rejected unsafe zip entry: ${entry.name}")
                return false
            }
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.outputStream().use { out -> zis.copyTo(out) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return true
    }
}