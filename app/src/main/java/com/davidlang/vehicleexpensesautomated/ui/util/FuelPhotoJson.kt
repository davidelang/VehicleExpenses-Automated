package com.davidlang.vehicleexpensesautomated.ui.util

import org.json.JSONArray
import org.json.JSONObject

/** One photo pointer in [FuelEntry.photoUrl] JSON (tag dash|pump|pump_2|…). */
data class FuelPhotoRef(
    val tag: String,
    val uri: String,
    val ts: Long = 0L,
)

/**
 * Shared parse/serialize for multi-photo [FuelEntry.photoUrl].
 * Shape: `[{"tag":"dash","uri":"…","ts":…},{"tag":"pump","uri":"…","ts":…}]`
 * Legacy plain path → single dash entry.
 */
object FuelPhotoJson {
    fun parse(photoUrl: String?): List<FuelPhotoRef> {
        if (photoUrl.isNullOrBlank()) return emptyList()
        val trimmed = photoUrl.trim()
        if (!trimmed.startsWith("[")) {
            return listOf(FuelPhotoRef(tag = "dash", uri = trimmed, ts = 0L))
        }
        return try {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val tag = o.optString("tag", "dash").ifBlank { "dash" }
                    val uri = o.optString("uri", "")
                    if (uri.isBlank()) continue
                    val ts = o.optLong("ts", 0L)
                    add(FuelPhotoRef(tag = tag, uri = uri, ts = ts))
                }
            }
        } catch (_: Exception) {
            listOf(FuelPhotoRef(tag = "dash", uri = trimmed, ts = 0L))
        }
    }

    fun serialize(photos: List<FuelPhotoRef>): String? {
        if (photos.isEmpty()) return null
        val keys = photos.map { it.tag }.distinct().sortedWith(
            compareBy(
                { if (it == "dash") 0 else if (it.startsWith("pump")) 1 else 2 },
                { it },
            ),
        )
        val byTag = photos.associateBy { it.tag }
        val arr = JSONArray()
        for (tag in keys) {
            val p = byTag[tag] ?: continue
            arr.put(
                JSONObject().apply {
                    put("tag", p.tag)
                    put("uri", p.uri)
                    put("ts", p.ts)
                },
            )
        }
        return arr.toString()
    }

    fun single(tag: String, uri: String, ts: Long): String =
        serialize(listOf(FuelPhotoRef(tag = tag, uri = uri, ts = ts)))!!

    /** Next free pump tag: pump, pump_2, pump_3, … */
    fun nextPumpTag(existing: Collection<String>): String {
        if ("pump" !in existing) return "pump"
        var n = 2
        while ("pump_$n" in existing) n++
        return "pump_$n"
    }

    /**
     * Union photo lists by URI (first tag wins for same URI).
     * Used by Stage B merge when combining dash/pump partials.
     */
    fun unionPhotos(a: String?, b: String?): String? {
        val merged = LinkedHashMap<String, FuelPhotoRef>() // uri → ref
        for (p in parse(a) + parse(b)) {
            if (p.uri.isBlank()) continue
            if (!merged.containsKey(p.uri)) merged[p.uri] = p
        }
        // Re-tag pumps so we keep pump, pump_2, … without URI collisions
        val out = mutableListOf<FuelPhotoRef>()
        val usedTags = mutableSetOf<String>()
        for (p in merged.values) {
            val tag = when {
                p.tag == "dash" || p.tag.startsWith("dash") -> {
                    if ("dash" !in usedTags) "dash" else p.tag
                }
                p.tag.startsWith("pump") -> nextPumpTag(usedTags)
                else -> {
                    var t = p.tag
                    var i = 2
                    while (t in usedTags) {
                        t = "${p.tag}_$i"
                        i++
                    }
                    t
                }
            }
            usedTags.add(tag)
            out.add(p.copy(tag = tag))
        }
        return serialize(out)
    }

    /** Append a pump photo; assigns next free pump / pump_N tag. */
    fun addPumpPhoto(existing: String?, uri: String, ts: Long): String {
        val list = parse(existing).toMutableList()
        if (list.any { it.uri == uri }) return serialize(list) ?: single("pump", uri, ts)
        val tag = nextPumpTag(list.map { it.tag })
        list.add(FuelPhotoRef(tag = tag, uri = uri, ts = ts))
        return serialize(list)!!
    }

    fun addDashPhoto(existing: String?, uri: String, ts: Long): String {
        val list = parse(existing).toMutableList()
        if (list.any { it.uri == uri }) return serialize(list) ?: single("dash", uri, ts)
        if (list.none { it.tag == "dash" }) {
            list.add(0, FuelPhotoRef(tag = "dash", uri = uri, ts = ts))
        } else {
            list.add(FuelPhotoRef(tag = "dash_2", uri = uri, ts = ts))
        }
        return serialize(list)!!
    }
}
