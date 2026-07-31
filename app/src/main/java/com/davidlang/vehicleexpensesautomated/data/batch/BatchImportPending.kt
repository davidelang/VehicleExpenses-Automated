package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * End-of-batch questions / unassigned items (survive process death via JSON file).
 * Stage A writes pending; Stage C applies answers (minimal UI stub can list them).
 */
enum class BatchPendingKind {
    ASSIGN_VEHICLE,
    SKIP_OR_ASSIGN_VEHICLE,
    UNREADABLE_PUMP,
    UNREADABLE_DASH_NO_VEHICLE,
    CONFLICT_ODO,
    AMBIGUOUS_MULTI_PUMP,
    /** Live fuel row with vehicleId=0 that still needs assignment. */
    ASSIGN_UNKNOWN_VEHICLE,
    /** Economy-ignored row needs review / unignore / edit. */
    ECONOMY_IGNORED,
    /** MPG outlier leg endpoint (3× vs median baseline). */
    MPG_OUTLIER,
    /**
     * Odometer reverse / digit jump / gap (detect only).
     * extra.mode = simple|complex; simple has suggestedOdo for short UI.
     */
    ODO_SUSPECT,
    /**
     * Pump cost/vol ratio absurd (phase 3): e.g. $/G outside [2,7].
     * Edit cost/vol or mark unreadable → gap.
     */
    BAD_PUMP_RATIO,
    OTHER,
}

data class BatchPendingItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: BatchPendingKind,
    val message: String,
    val photoPath: String? = null,
    val durablePhotoPath: String? = null,
    val timestampMs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Horizontal accuracy meters from EXIF when known; omitted when null. */
    val accuracyM: Double? = null,
    val suggestedVehicleId: Int? = null,
    val fuelEntryId: Long? = null,
    val extra: Map<String, String> = emptyMap(),
)

object BatchImportPendingStore {
    private const val TAG = "BatchFuelImport"
    private const val FILE_NAME = "batch_import_pending.json"

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun load(context: Context): MutableList<BatchPendingItem> {
        val f = file(context)
        if (!f.isFile) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<BatchPendingItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = try {
                    BatchPendingKind.valueOf(o.optString("kind", BatchPendingKind.OTHER.name))
                } catch (_: Exception) {
                    BatchPendingKind.OTHER
                }
                val extraObj = o.optJSONObject("extra")
                val extra = mutableMapOf<String, String>()
                if (extraObj != null) {
                    val keys = extraObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        extra[k] = extraObj.optString(k, "")
                    }
                }
                out.add(
                    BatchPendingItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        kind = kind,
                        message = o.optString("message", ""),
                        photoPath = o.optString("photoPath", "").takeIf { it.isNotBlank() },
                        durablePhotoPath = o.optString("durablePhotoPath", "")
                            .takeIf { it.isNotBlank() },
                        timestampMs = if (o.has("timestampMs") && !o.isNull("timestampMs")) {
                            o.optLong("timestampMs")
                        } else null,
                        latitude = if (o.has("latitude") && !o.isNull("latitude")) {
                            o.optDouble("latitude")
                        } else null,
                        longitude = if (o.has("longitude") && !o.isNull("longitude")) {
                            o.optDouble("longitude")
                        } else null,
                        accuracyM = if (o.has("accuracyM") && !o.isNull("accuracyM")) {
                            o.optDouble("accuracyM")
                        } else null,
                        suggestedVehicleId = if (o.has("suggestedVehicleId") && !o.isNull("suggestedVehicleId")) {
                            o.optInt("suggestedVehicleId")
                        } else null,
                        fuelEntryId = if (o.has("fuelEntryId") && !o.isNull("fuelEntryId")) {
                            o.optLong("fuelEntryId")
                        } else null,
                        extra = extra,
                    ),
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "load pending failed: ${e.message}")
            mutableListOf()
        }
    }

    fun save(context: Context, items: List<BatchPendingItem>) {
        try {
            val arr = JSONArray()
            for (item in items) {
                val o = JSONObject()
                o.put("id", item.id)
                o.put("kind", item.kind.name)
                o.put("message", item.message)
                item.photoPath?.let { o.put("photoPath", it) }
                item.durablePhotoPath?.let { o.put("durablePhotoPath", it) }
                item.timestampMs?.let { o.put("timestampMs", it) }
                item.latitude?.let { o.put("latitude", it) }
                item.longitude?.let { o.put("longitude", it) }
                item.accuracyM?.let { o.put("accuracyM", it) }
                item.suggestedVehicleId?.let { o.put("suggestedVehicleId", it) }
                item.fuelEntryId?.let { o.put("fuelEntryId", it) }
                if (item.extra.isNotEmpty()) {
                    val ex = JSONObject()
                    item.extra.forEach { (k, v) -> ex.put(k, v) }
                    o.put("extra", ex)
                }
                arr.put(o)
            }
            file(context).writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "save pending failed: ${e.message}")
        }
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
        }
    }

    fun remove(context: Context, id: String) {
        val items = load(context).filter { it.id != id }
        save(context, items)
    }

    fun removeAll(context: Context, ids: Collection<String>) {
        val drop = ids.toSet()
        val items = load(context).filter { it.id !in drop }
        save(context, items)
    }

    /** Cheap count for title-bar yellow indicator. */
    fun count(context: Context): Int = load(context).size
}
