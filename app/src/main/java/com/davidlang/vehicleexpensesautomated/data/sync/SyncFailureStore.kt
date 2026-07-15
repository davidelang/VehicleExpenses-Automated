package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists last sync failure per destination until the next success for that dest.
 * Separate from [SyncDestinationStore.pendingCount] (upload/download queue).
 */
class SyncFailureStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Failure(
        val destId: String,
        val destType: DestType,
        val message: String,
        val timestamp: Long,
    )

    enum class DestType(val json: String) {
        SPREADSHEET("spreadsheet"),
        PHOTO("photo"),
    }

    fun recordSpreadsheetFailure(destId: String, message: String) =
        record(destId, DestType.SPREADSHEET, message)

    fun recordPhotoFailure(destId: String, message: String) =
        record(destId, DestType.PHOTO, message)

    fun clearSpreadsheetFailure(destId: String) = clear(destId, DestType.SPREADSHEET)

    fun clearPhotoFailure(destId: String) = clear(destId, DestType.PHOTO)

    fun hasAnyFailure(): Boolean = loadAll().isNotEmpty()

    fun hasSpreadsheetFailure(): Boolean = loadAll().any { it.destType == DestType.SPREADSHEET }

    fun hasPhotoFailure(): Boolean = loadAll().any { it.destType == DestType.PHOTO }

    fun firstSpreadsheetFailure(): Failure? =
        loadAll().firstOrNull { it.destType == DestType.SPREADSHEET }

    fun firstPhotoFailure(): Failure? =
        loadAll().firstOrNull { it.destType == DestType.PHOTO }

    /** User-facing summary: failed destination names only (not stored error text). */
    fun spreadsheetFailureSummary(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.SPREADSHEET }
        if (failures.isEmpty()) return null
        val byId = destStore.load().spreadsheet.associateBy { it.id }
        val names = failures.map { failure ->
            byId[failure.destId]?.let { dest ->
                dest.displayName.ifBlank {
                    dest.targetId.take(12).ifBlank { dest.provider.displayLabel() }
                }
            } ?: shortLegacyMessage(failure.message)
        }
        return SyncResultMessages.failedNamesMessage(names)
    }

    fun photoFailureSummary(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.PHOTO }
        if (failures.isEmpty()) return null
        val byId = destStore.load().photo.associateBy { it.id }
        val names = failures.map { failure ->
            byId[failure.destId]?.let { dest ->
                dest.displayName.ifBlank {
                    dest.folderName.ifBlank { photoProviderLabel(dest.provider) }
                }
            } ?: shortLegacyMessage(failure.message)
        }
        return SyncResultMessages.failedNamesMessage(names)
    }

    private fun photoProviderLabel(provider: PhotoProvider): String = when (provider) {
        PhotoProvider.GOOGLE_DRIVE -> "Google Drive"
        PhotoProvider.ONEDRIVE -> "OneDrive"
        PhotoProvider.S3 -> "S3"
        PhotoProvider.OTHER -> "Other"
        PhotoProvider.NONE -> "Photo backup"
    }

    private fun shortLegacyMessage(message: String): String {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return "Sync failed"
        if (trimmed.length > 80 || trimmed.contains('\n') || trimmed.contains("http", ignoreCase = true)) {
            return "Sync failed"
        }
        return trimmed
    }

    private fun record(destId: String, type: DestType, message: String) {
        val trimmed = message.trim().ifBlank { "Sync failed" }
        val all = loadAll().filterNot { it.destId == destId && it.destType == type }.toMutableList()
        all.add(
            Failure(
                destId = destId,
                destType = type,
                message = trimmed,
                timestamp = System.currentTimeMillis(),
            ),
        )
        saveAll(all)
    }

    private fun clear(destId: String, type: DestType) {
        val all = loadAll().filterNot { it.destId == destId && it.destType == type }
        saveAll(all)
    }

    private fun loadAll(): List<Failure> {
        val json = prefs.getString(KEY_FAILURES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = when (obj.optString("destType")) {
                        DestType.PHOTO.json -> DestType.PHOTO
                        else -> DestType.SPREADSHEET
                    }
                    add(
                        Failure(
                            destId = obj.optString("destId", ""),
                            destType = type,
                            message = obj.optString("message", ""),
                            timestamp = obj.optLong("timestamp", 0L),
                        ),
                    )
                }
            }.filter { it.destId.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(failures: List<Failure>) {
        val arr = JSONArray()
        failures.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("destId", f.destId)
                    put("destType", f.destType.json)
                    put("message", f.message)
                    put("timestamp", f.timestamp)
                },
            )
        }
        prefs.edit().putString(KEY_FAILURES_JSON, arr.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = SyncDestinationStore.PREFS_NAME
        private const val KEY_FAILURES_JSON = "sync_failures_json"
    }
}