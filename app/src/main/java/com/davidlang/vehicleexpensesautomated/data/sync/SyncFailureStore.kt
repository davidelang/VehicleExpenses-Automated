package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists last sync failure per destination until the next success for that dest.
 * Separate from [SyncDestinationStore.pendingCount] (upload/download queue).
 *
 * [Failure.message] is the **full** API/user error (capped); hub UI shows short
 * summaries via [spreadsheetFailureSummary] / [photoFailureSummary]. Use
 * [failureFor] / [spreadsheetFailureDetails] for Details dialogs.
 *
 * Orphan destIds (deleted/recreated destinations) are dropped by
 * [pruneToKnownDestinations] so the hub badge cannot stick forever.
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

    fun failureFor(destId: String, type: DestType): Failure? =
        loadAll().firstOrNull { it.destId == destId && it.destType == type }

    fun spreadsheetFailure(destId: String): Failure? = failureFor(destId, DestType.SPREADSHEET)

    fun photoFailure(destId: String): Failure? = failureFor(destId, DestType.PHOTO)

    /**
     * Drop failures whose destId is not in the known destination sets.
     * Does **not** clear failures for still-present dests (those wait for success).
     * @return number of entries removed
     */
    fun pruneToKnownDestinations(
        spreadsheetIds: Set<String>,
        photoIds: Set<String>,
    ): Int {
        val all = loadAll()
        if (all.isEmpty()) return 0
        val kept = all.filter { failure ->
            when (failure.destType) {
                DestType.SPREADSHEET -> failure.destId in spreadsheetIds
                DestType.PHOTO -> failure.destId in photoIds
            }
        }
        val removed = all.size - kept.size
        if (removed > 0) {
            saveAll(kept)
        }
        return removed
    }

    /** Convenience: prune using current [SyncDestinationStore] ids. */
    fun pruneToKnownDestinations(destStore: SyncDestinationStore): Int {
        val loaded = destStore.load()
        return pruneToKnownDestinations(
            spreadsheetIds = loaded.spreadsheet.map { it.id }.toSet(),
            photoIds = loaded.photo.map { it.id }.toSet(),
        )
    }

    /**
     * User-facing hub summary: failed destination names, with a short rate-limit
     * title when the stored detail is a quota/rate-limit message.
     */
    fun spreadsheetFailureSummary(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.SPREADSHEET }
        if (failures.isEmpty()) return null
        val byId = destStore.load().spreadsheet.associateBy { it.id }
        val names = failures.map { failure ->
            val dest = byId[failure.destId]
            val name = dest?.let {
                it.displayName.ifBlank {
                    it.targetId.take(12).ifBlank { it.provider.displayLabel() }
                }
            } ?: orphanTitle(failure.destId)
            val rate = SyncRateLimit.shortTitle(failure.message, forSheets = true)
            if (rate != null) "$name ($rate)" else name
        }
        return SyncResultMessages.failedNamesMessage(names)
    }

    fun photoFailureSummary(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.PHOTO }
        if (failures.isEmpty()) return null
        val byId = destStore.load().photo.associateBy { it.id }
        val names = failures.map { failure ->
            val dest = byId[failure.destId]
            val name = dest?.let {
                it.displayName.ifBlank {
                    it.folderName.ifBlank { photoProviderLabel(it.provider) }
                }
            } ?: orphanTitle(failure.destId)
            val rate = SyncRateLimit.shortTitle(failure.message, forSheets = false)
            if (rate != null) "$name ($rate)" else name
        }
        return SyncResultMessages.failedNamesMessage(names)
    }

    /** Full detail text for all spreadsheet failures (Details dialog). */
    fun spreadsheetFailureDetails(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.SPREADSHEET }
        if (failures.isEmpty()) return null
        val byId = destStore.load().spreadsheet.associateBy { it.id }
        return failures.joinToString("\n\n———\n\n") { failure ->
            val dest = byId[failure.destId]
            val title = dest?.let {
                it.displayName.ifBlank {
                    it.targetId.take(12).ifBlank { it.provider.displayLabel() }
                }
            } ?: orphanTitle(failure.destId)
            val displayName = dest?.displayName?.takeIf { it.isNotBlank() }
            "$title:\n${displayDetailMessage(failure.message, displayName)}"
        }
    }

    fun photoFailureDetails(destStore: SyncDestinationStore): String? {
        val failures = loadAll().filter { it.destType == DestType.PHOTO }
        if (failures.isEmpty()) return null
        val byId = destStore.load().photo.associateBy { it.id }
        return failures.joinToString("\n\n———\n\n") { failure ->
            val dest = byId[failure.destId]
            val title = dest?.let {
                it.displayName.ifBlank {
                    it.folderName.ifBlank { photoProviderLabel(it.provider) }
                }
            } ?: orphanTitle(failure.destId)
            val displayName = dest?.displayName?.takeIf { it.isNotBlank() }
                ?: dest?.folderName?.takeIf { it.isNotBlank() }
            "$title:\n${displayDetailMessage(failure.message, displayName)}"
        }
    }

    private fun photoProviderLabel(provider: PhotoProvider): String = when (provider) {
        PhotoProvider.GOOGLE_DRIVE -> "Google Drive"
        PhotoProvider.ONEDRIVE -> "OneDrive"
        PhotoProvider.S3 -> "S3"
        PhotoProvider.OTHER -> "Other"
        PhotoProvider.NONE -> "Photo backup"
    }

    /** D1: never show a raw full UUID alone as the Details title. */
    private fun orphanTitle(destId: String): String {
        val prefix = destId.take(8)
        return if (prefix.isBlank()) {
            "(removed destination)"
        } else {
            "(removed destination) $prefix…"
        }
    }

    /**
     * D3: display-only cleanup for legacy name-only messages (not API text).
     * Does not rewrite prefs.
     */
    private fun displayDetailMessage(message: String, displayName: String?): String {
        val msg = message.trim()
        if (msg.isBlank()) return "Sync failed (no detail)"
        if (looksLikeApiOrErrorText(msg)) return msg
        if (displayName != null && msg.equals(displayName.trim(), ignoreCase = true)) {
            return "Sync failed (no detail)"
        }
        // Orphan / legacy: short product-looking name stored as "error"
        if (msg.length <= 80 && !msg.contains('\n')) {
            return "Sync failed (no detail)"
        }
        return msg
    }

    private fun looksLikeApiOrErrorText(message: String): Boolean {
        if (SyncRateLimit.isRateLimitError(message)) return true
        if (message.contains('\n')) return true
        if (message.length > 80) return true
        val lower = message.lowercase()
        return lower.contains("fail") ||
            lower.contains("error") ||
            lower.contains("exception") ||
            lower.contains("quota") ||
            lower.contains("http") ||
            lower.contains("denied") ||
            lower.contains("unauthorized") ||
            lower.contains("not configured") ||
            lower.contains("sign in")
    }

    private fun record(destId: String, type: DestType, message: String) {
        val capped = SyncRateLimit.capDetail(message)
        val all = loadAll().filterNot { it.destId == destId && it.destType == type }.toMutableList()
        all.add(
            Failure(
                destId = destId,
                destType = type,
                message = capped,
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
