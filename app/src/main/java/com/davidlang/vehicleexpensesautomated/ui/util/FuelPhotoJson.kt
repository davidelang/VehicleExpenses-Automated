package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.StoredParse
import org.json.JSONArray
import org.json.JSONObject

/** One photo pointer in [FuelEntry.photoUrl] JSON (tag dash|pump|pump_2|…). */
data class FuelPhotoRef(
    val tag: String,
    val uri: String,
    val ts: Long = 0L,
    val missing: Boolean = false,
    val fallbackFrame: Boolean = false,
)

/**
 * Shared parse/serialize for multi-photo [FuelEntry.photoUrl].
 * Shape: `[{"tag":"dash","uri":"…","ts":…},{"tag":"pump","uri":"…","ts":…}]`
 * Legacy plain path → single dash entry.
 */
object FuelPhotoJson {
    private const val TAG = "FuelPhotoJson"

    fun read(photoUrl: String?): StoredParse<List<FuelPhotoRef>> {
        if (photoUrl.isNullOrBlank()) return StoredParse.Blank()
        val trimmed = photoUrl.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return StoredParse.Valid(listOf(FuelPhotoRef(tag = "dash", uri = trimmed, ts = 0L)))
        }
        if (!trimmed.startsWith("[")) {
            Log.e(TAG, "photo reference unreadable")
            return StoredParse.Corrupt(trimmed)
        }
        return try {
            val arr = JSONArray(trimmed)
            val refs = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: return StoredParse.Corrupt(trimmed)
                    val tag = o.optString("tag", "dash").ifBlank { "dash" }
                    val uri = o.optString("uri", "")
                    if (uri.isBlank()) continue
                    val ts = o.optLong("ts", 0L)
                    add(
                        FuelPhotoRef(
                            tag = tag,
                            uri = uri,
                            ts = ts,
                            missing = o.optBoolean("missing", false),
                            fallbackFrame = o.optBoolean("fallback", false),
                        ),
                    )
                }
            }
            StoredParse.Valid(refs)
        } catch (e: Exception) {
            Log.e(TAG, "photo reference unreadable", e)
            StoredParse.Corrupt(trimmed)
        }
    }

    /** Valid refs only. Blank and corrupt are empty; use [read] for the marker. */
    fun parse(photoUrl: String?): List<FuelPhotoRef> = when (val parsed = read(photoUrl)) {
        is StoredParse.Valid -> parsed.value
        is StoredParse.Blank -> emptyList()
        is StoredParse.Corrupt -> emptyList()
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
                    if (p.missing) put("missing", true)
                    if (p.fallbackFrame) put("fallback", true)
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
}
