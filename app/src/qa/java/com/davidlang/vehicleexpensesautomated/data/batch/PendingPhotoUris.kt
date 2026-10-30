package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson

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
    // Strict role filters: never re-union unfiltered [candidates] (leaked wrong role into ODO/BAD_PUMP).
    val filtered = when (role) {
        PhotoRole.DASH -> {
            val fromEntries = entryPhotoUrls.flatMap { dashPhotoPaths(it) }
            val dashish = candidates.filter { isDashPathHint(it) }
            (fromEntries + dashish).distinct()
        }
        PhotoRole.PUMP -> {
            val fromEntries = entryPhotoUrls.flatMap { pumpPhotoPaths(it) }
            val pumpish = candidates.filter { isPumpPathHint(it) }
            (fromEntries + pumpish).distinct()
        }
        PhotoRole.BOTH -> candidates
    }
    return dedupePhotoPaths(filtered)
}

/**
 * Path heuristics for **dash** shots only — excludes pump_photos / pump_ prefixes.
 * Used by [pendingPhotoUris] DASH role and Stage C ODO UIs.
 */
fun isDashPathHint(path: String): Boolean {
    val p = path.trim()
    if (p.isEmpty()) return false
    val lower = p.lowercase()
    if (lower.contains("pump_photos")) return false
    if (Regex("""(?:^|[/\\])pump_\d+_""", RegexOption.IGNORE_CASE).containsMatchIn(p)) return false
    if (lower.contains("experiment_photos")) return true
    if (Regex("""(?:^|[/\\])dash_\d+_""", RegexOption.IGNORE_CASE).containsMatchIn(p)) return true
    if (lower.contains("dash")) return true
    // Plain path without pump signals — treat as dash for ODO role
    return !lower.contains("pump")
}

/** Path heuristics for **pump** shots only — excludes pure dash/experiment_photos sources. */
fun isPumpPathHint(path: String): Boolean {
    val p = path.trim()
    if (p.isEmpty()) return false
    val lower = p.lowercase()
    if (lower.contains("pump_photos")) return true
    if (Regex("""(?:^|[/\\])pump_\d+_""", RegexOption.IGNORE_CASE).containsMatchIn(p)) return true
    if (lower.contains("pump")) return true
    return false
}

fun isDngPath(path: String): Boolean =
    path.substringAfterLast('.').equals("dng", ignoreCase = true)

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
