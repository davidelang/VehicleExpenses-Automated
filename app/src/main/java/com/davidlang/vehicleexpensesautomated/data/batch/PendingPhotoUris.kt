package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import java.io.File

/**
 * Stage C: resolve **deduped** display photo paths for a pending question.
 *
 * Preference: [photoPath] / [durablePhotoPath] (often same source path) → extra → entry photos.
 * Collapse copies of the same shot (legacy durable mirror + source, or `dash_ts_` /
 * `pump_ts_` prefixes) by **PXL_… stem** so the UI shows one thumb per shot.
 *
 * Batch no longer writes `batch_import_photos`; prefer live source dirs when choosing.
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
    add(item.photoPath)
    add(item.durablePhotoPath)
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
    val role = StageCPhaseStore.photoRole(item)
    val filtered = when (role) {
        StageCPhaseStore.PhotoRole.DASH -> {
            // Prefer dash-tagged from entry JSON; else paths that look like dash sources
            val fromEntries = entryPhotoUrls.flatMap { dashPhotoPaths(it) }
            val dashish = candidates.filter {
                it.contains("experiment_photos", ignoreCase = true) ||
                    it.contains("dash", ignoreCase = true) ||
                    !it.contains("pump", ignoreCase = true)
            }
            (fromEntries + dashish + candidates).distinct()
        }
        StageCPhaseStore.PhotoRole.PUMP -> {
            val fromEntries = entryPhotoUrls.flatMap { pumpPhotoPaths(it) }
            val pumpish = candidates.filter {
                it.contains("pump_photos", ignoreCase = true) ||
                    it.contains("pump", ignoreCase = true) ||
                    !it.contains("experiment_photos", ignoreCase = true)
            }
            (fromEntries + pumpish + candidates).distinct()
        }
        StageCPhaseStore.PhotoRole.BOTH -> candidates
    }
    return dedupePhotoPaths(filtered)
}

/**
 * One path per shot: group by [photoStem]. Prefer an existing **source** path
 * (experiment_photos / pump_photos) over legacy batch_import_photos mirrors.
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
        group.firstOrNull {
            photoPathExists(it) && !it.contains("batch_import_photos")
        }
            ?: group.firstOrNull { photoPathExists(it) }
            ?: group.firstOrNull { !it.contains("batch_import_photos") }
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

/**
 * Dash-only photo URIs for odo questions (never pump tags).
 * Legacy plain path (no JSON tags) is treated as a single dash image.
 */
fun dashPhotoPaths(photoUrl: String?): List<String> {
    if (photoUrl.isNullOrBlank()) return emptyList()
    val refs = FuelPhotoJson.parse(photoUrl)
    if (refs.isEmpty()) return emptyList()
    // Single legacy plain path parses as tag "dash"
    val dash = refs.filter {
        it.tag == "dash" || it.tag.startsWith("dash")
    }.map { it.uri }.filter { it.isNotBlank() }
    return dedupePhotoPaths(dash)
}

fun dashPhotoPaths(entry: com.davidlang.vehicleexpensesautomated.data.model.FuelEntry): List<String> =
    dashPhotoPaths(entry.photoUrl)

/** Pump-only photo URIs (tag pump / pump_N). */
fun pumpPhotoPaths(photoUrl: String?): List<String> {
    if (photoUrl.isNullOrBlank()) return emptyList()
    val refs = FuelPhotoJson.parse(photoUrl)
    if (refs.isEmpty()) return emptyList()
    val pump = refs.filter {
        it.tag == "pump" || it.tag.startsWith("pump")
    }.map { it.uri }.filter { it.isNotBlank() }
    return dedupePhotoPaths(pump)
}

fun pumpPhotoPaths(entry: com.davidlang.vehicleexpensesautomated.data.model.FuelEntry): List<String> =
    pumpPhotoPaths(entry.photoUrl)
