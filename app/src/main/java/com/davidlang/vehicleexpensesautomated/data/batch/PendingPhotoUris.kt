package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import java.io.File

/**
 * Stage C: resolve display photo paths for a pending question.
 *
 * Load order: durablePhotoPath → photoPath → extra["photoPaths"] (`|`-split)
 * → optional fuel-entry photoUrl tags via [entryPhotoUrls].
 */
fun pendingPhotoUris(
    item: BatchPendingItem,
    entryPhotoUrls: List<String> = emptyList(),
): List<String> {
    val out = LinkedHashSet<String>()
    fun add(p: String?) {
        val t = p?.trim().orEmpty()
        if (t.isNotBlank()) out.add(t)
    }
    add(item.durablePhotoPath)
    add(item.photoPath)
    item.extra["photoPaths"]
        ?.split('|')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.forEach { out.add(it) }
    for (url in entryPhotoUrls) {
        for (ref in FuelPhotoJson.parse(url)) {
            add(ref.uri)
        }
        // legacy plain path
        if (!url.trimStart().startsWith("[")) add(url)
    }
    return out.toList()
}

/** True if the path looks like an on-disk file we can try to open. */
fun photoPathExists(path: String): Boolean {
    val p = path.trim()
    if (p.isBlank()) return false
    if (p.startsWith("content://") || p.startsWith("file://")) return true
    return File(p).isFile
}

fun isDngPath(path: String): Boolean =
    path.substringAfterLast('.').equals("dng", ignoreCase = true)
