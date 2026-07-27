package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import java.io.File

/**
 * Stage C: resolve **deduped** display photo paths for a pending question.
 *
 * Preference: existing [durablePhotoPath] → [photoPath] → extra["photoPaths"] → entry photos.
 * Collapse copies of the same shot (durable + source, or `dash_ts_` / `pump_ts_` prefixes)
 * by **PXL_… stem** so the UI shows one thumb per shot.
 */
fun pendingPhotoUris(
    item: BatchPendingItem,
    entryPhotoUrls: List<String> = emptyList(),
): List<String> {
    val candidates = ArrayList<String>()
    fun add(p: String?) {
        val t = p?.trim().orEmpty()
        if (t.isNotBlank()) candidates.add(t)
    }
    // Prefer durable when present (existing file preferred at collapse time)
    add(item.durablePhotoPath)
    add(item.photoPath)
    item.extra["photoPaths"]
        ?.split('|')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.forEach { candidates.add(it) }
    for (url in entryPhotoUrls) {
        for (ref in FuelPhotoJson.parse(url)) {
            add(ref.uri)
        }
        if (!url.trimStart().startsWith("[")) add(url)
    }
    return dedupePhotoPaths(candidates)
}

/**
 * One path per shot: group by [photoStem], prefer an existing file, then durable-looking path.
 */
fun dedupePhotoPaths(paths: List<String>): List<String> {
    if (paths.isEmpty()) return emptyList()
    val groups = LinkedHashMap<String, MutableList<String>>()
    for (p in paths) {
        val t = p.trim()
        if (t.isBlank()) continue
        val key = photoStem(t)
        groups.getOrPut(key) { mutableListOf() }.add(t)
    }
    return groups.values.map { group ->
        group.firstOrNull { photoPathExists(it) }
            ?: group.firstOrNull { it.contains("batch_import_photos") }
            ?: group.first()
    }
}

/**
 * Stable stem for duplicate detection: strip path, optional `dash_<digits>_` / `pump_<digits>_`
 * durable prefix, keep `PXL_…` (or full basename).
 */
fun photoStem(path: String): String {
    val base = path.trim().substringAfterLast('/').substringAfterLast('\\')
    val stripped = base
        .replace(Regex("""^(?:dash|pump)_\d+_""", RegexOption.IGNORE_CASE), "")
        .ifBlank { base }
    val pxl = Regex("""(PXL_[A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE).find(stripped)
    return (pxl?.groupValues?.get(1) ?: stripped).lowercase()
}

/** True if the path looks like an on-disk file we can try to open. */
fun photoPathExists(path: String): Boolean {
    val p = path.trim()
    if (p.isBlank()) return false
    if (p.startsWith("content://")) return true
    val filePath = if (p.startsWith("file://")) p.removePrefix("file://") else p
    return File(filePath).isFile
}

fun isDngPath(path: String): Boolean =
    path.substringAfterLast('.').equals("dng", ignoreCase = true)
