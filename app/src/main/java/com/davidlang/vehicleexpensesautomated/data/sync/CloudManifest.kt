package com.davidlang.vehicleexpensesautomated.data.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for entity [cloudManifest] JSON (multi-destination-ready v1).
 *
 * Pending rules:
 * - **Upload:** per exact [destId] — [hasEntryForDest] (no cross-dest fallback).
 * - **Download:** [getFileId] may fall back to any `google_drive` entry for the role.
 * - **After download:** [bindLocalDestAfterDownload] merges a local dest entry; other destIds stay.
 *
 * ```json
 * { "destinations": [ { "destId", "provider", "fileId", "name", "role", "mimeType", "updatedAt" } ] }
 * ```
 */
object CloudManifest {

    const val PROVIDER_GOOGLE_DRIVE = "google_drive"
    const val PROVIDER_RCLONE = "rclone"

    const val ROLE_VEHICLE_REF = "vehicle_ref"
    const val ROLE_VEHICLE_REF_CLEANED = "vehicle_ref_cleaned"
    const val ROLE_VEHICLE_LANDMARKS = "vehicle_landmarks"
    const val ROLE_FUEL_DASH = "fuel_dash"
    const val ROLE_FUEL_PUMP = "fuel_pump"
    const val ROLE_EXPENSE_RECEIPT = "expense_receipt"

    data class Entry(
        val destId: String,
        val provider: String,
        val fileId: String,
        val name: String,
        val role: String,
        val mimeType: String,
        val updatedAt: Long,
    )

    fun parse(json: String?): List<Entry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            val array = root.optJSONArray(KEY_DESTINATIONS) ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val destId = obj.optString("destId", "")
                    val fileId = obj.optString("fileId", "")
                    val role = obj.optString("role", "")
                    if (destId.isBlank() || fileId.isBlank() || role.isBlank()) continue
                    add(
                        Entry(
                            destId = destId,
                            provider = obj.optString("provider", PROVIDER_GOOGLE_DRIVE),
                            fileId = fileId,
                            name = obj.optString("name", ""),
                            role = role,
                            mimeType = obj.optString("mimeType", "image/jpeg"),
                            updatedAt = obj.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Removes obsolete [ROLE_VEHICLE_LANDMARKS] entries (no longer uploaded to Drive). */
    fun stripObsoleteRoles(json: String?): String? {
        if (json.isNullOrBlank()) return json
        val entries = parse(json)
        val filtered = entries.filter { it.role != ROLE_VEHICLE_LANDMARKS }
        return if (filtered.size == entries.size) json else toJson(filtered)
    }

    fun merge(existingJson: String?, newEntries: List<Entry>): String {
        val existing = parse(stripObsoleteRoles(existingJson))
            .associateBy { key(it.destId, it.role) }
            .toMutableMap()
        val now = System.currentTimeMillis()
        for (entry in newEntries.filter { it.role != ROLE_VEHICLE_LANDMARKS }) {
            val merged = entry.copy(updatedAt = if (entry.updatedAt > 0) entry.updatedAt else now)
            existing[key(merged.destId, merged.role)] = merged
        }
        return toJson(existing.values.toList())
    }

    fun hasRole(json: String?, destId: String, role: String): Boolean =
        getFileId(json, destId, role) != null

    /** True when manifest has an entry for exact [destId] + [role] (no cross-device fallback). */
    fun hasEntryForDest(json: String?, destId: String, role: String): Boolean {
        if (role == ROLE_VEHICLE_LANDMARKS) return false
        return parse(json).any { it.destId == destId && it.role == role }
    }

    /** Exact [destId] + [role] file id (no cross-dest fallback). Used to upsert Drive objects. */
    fun fileIdForDest(json: String?, destId: String, role: String): String? {
        if (role == ROLE_VEHICLE_LANDMARKS) return null
        return parse(json).firstOrNull { it.destId == destId && it.role == role }?.fileId
    }

    /**
     * Preferred [destId] first.
     * For Google Drive dests, may fall back to any `google_drive` entry (multi-device pull).
     * For rclone dests, **no** cross-provider fallback.
     */
    fun getFileId(json: String?, destId: String, role: String, provider: String? = null): String? {
        if (role == ROLE_VEHICLE_LANDMARKS) return null
        val entries = parse(json)
        val exact = entries.firstOrNull { it.destId == destId && it.role == role }
        if (exact != null) return exact.fileId
        if (provider == PROVIDER_RCLONE) return null
        return entries.firstOrNull { it.provider == PROVIDER_GOOGLE_DRIVE && it.role == role }?.fileId
    }

    /**
     * After cross-device download, add [localDestId] entries for pulled roles (same fileIds).
     * Preserves entries for other destIds — does not rewrite foreign bindings.
     */
    fun bindLocalDestAfterDownload(
        json: String?,
        localDestId: String,
        bindings: List<DownloadBinding>,
        provider: String = PROVIDER_GOOGLE_DRIVE,
    ): String {
        if (bindings.isEmpty()) return json ?: toJson(emptyList())
        val now = System.currentTimeMillis()
        val entries = bindings.map { binding ->
            Entry(
                destId = localDestId,
                provider = provider,
                fileId = binding.fileId,
                name = binding.name,
                role = binding.role,
                mimeType = binding.mimeType,
                updatedAt = now,
            )
        }
        return merge(json, entries)
    }

    data class DownloadBinding(
        val role: String,
        val fileId: String,
        val name: String,
        val mimeType: String = "image/jpeg",
    )

    /** @deprecated Collapses multi-dest history; use [bindLocalDestAfterDownload] instead. */
    @Deprecated("Rewrites foreign destIds; use bindLocalDestAfterDownload for add-only merge")
    fun remintDestIdForRoles(json: String?, localDestId: String, roles: Collection<String>): String? {
        if (json.isNullOrBlank() || roles.isEmpty()) return json
        val roleSet = roles.toSet()
        val entries = parse(json)
        if (entries.isEmpty()) return json
        var changed = false
        val reminted = entries.map { entry ->
            if (entry.role in roleSet &&
                entry.destId != localDestId &&
                entry.provider == PROVIDER_GOOGLE_DRIVE
            ) {
                changed = true
                entry.copy(destId = localDestId)
            } else {
                entry
            }
        }
        return if (changed) toJson(reminted) else json
    }

    fun toJson(entries: List<Entry>): String {
        val root = JSONObject()
        val array = JSONArray()
        entries.sortedWith(compareBy({ it.destId }, { it.role })).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("destId", entry.destId)
                    put("provider", entry.provider)
                    put("fileId", entry.fileId)
                    put("name", entry.name)
                    put("role", entry.role)
                    put("mimeType", entry.mimeType)
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        root.put(KEY_DESTINATIONS, array)
        return root.toString()
    }

    private fun key(destId: String, role: String): String = "$destId::$role"

    private const val KEY_DESTINATIONS = "destinations"
}