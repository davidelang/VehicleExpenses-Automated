package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.FirebaseTabularConfig
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.RowDbTabularConfig
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetConfig
import org.json.JSONArray
import org.json.JSONObject

class SyncDestinationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SyncDestinations {
        migrateLegacyIfNeeded()
        val json = prefs.getString(KEY_SYNC_DESTINATIONS_JSON, null) ?: return SyncDestinations()
        val parsed = parseJson(json)
        val needsResave = json.contains("\"provider\":\"rclone\"") ||
            parsed.spreadsheet.any { it.provider == SpreadsheetProvider.OTHER && it.configJson.contains("backendType") }
        if (needsResave) {
            save(parsed)
        }
        return parsed
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_SYNC_DESTINATIONS_MIGRATED, false)) return
        if (prefs.contains(KEY_SYNC_DESTINATIONS_JSON)) {
            prefs.edit().putBoolean(KEY_SYNC_DESTINATIONS_MIGRATED, true).apply()
            return
        }

        var spreadsheet = emptyList<SpreadsheetDestination>()
        var photo = emptyList<PhotoDestination>()

        val legacySheetId = prefs.getString("sheet_id", "")?.trim().orEmpty()
        if (legacySheetId.isNotBlank()) {
            spreadsheet = listOf(
                SpreadsheetDestination(
                    targetId = legacySheetId,
                    enabled = prefs.getBoolean("sync_enabled", false),
                    wifiOnly = prefs.getBoolean("wifi_only", true),
                    chargingOnly = prefs.getBoolean("charging_only", false),
                    frequencyMinutes = (prefs.getInt("frequency_hours", 6) * 60)
                        .coerceIn(SpreadsheetDestination.MIN_FREQUENCY_MINUTES, SpreadsheetDestination.MAX_FREQUENCY_MINUTES),
                ),
            )
        }

        val photoProvider = prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive"
        if (photoProvider == "google_drive") {
            photo = listOf(
                PhotoDestination(
                    folderName = prefs.getString("drive_folder", "Vehicle Expenses Photos")
                        ?: "Vehicle Expenses Photos",
                ),
            )
        }

        if (spreadsheet.isNotEmpty() || photo.isNotEmpty()) {
            save(SyncDestinations(spreadsheet = spreadsheet, photo = photo))
        }
        prefs.edit().putBoolean(KEY_SYNC_DESTINATIONS_MIGRATED, true).apply()
    }

    fun save(destinations: SyncDestinations) {
        prefs.edit().putString(KEY_SYNC_DESTINATIONS_JSON, toJson(destinations)).apply()
    }

    private fun parseJson(json: String): SyncDestinations {
        return try {
            val root = JSONObject(json)
            SyncDestinations(
                spreadsheet = parseSpreadsheetArray(root.optJSONArray(KEY_SPREADSHEET)),
                photo = parsePhotoArray(root.optJSONArray(KEY_PHOTO)),
            )
        } catch (_: Exception) {
            SyncDestinations()
        }
    }

    private fun parseSpreadsheetArray(array: JSONArray?): List<SpreadsheetDestination> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val rawProvider = SpreadsheetProvider.fromJson(
                    obj.optString("provider", SpreadsheetProvider.GOOGLE_SHEETS.jsonValue),
                )
                val configJson = obj.optString("configJson", "")
                val provider = migrateOtherProvider(rawProvider, configJson)
                add(
                    SpreadsheetDestination(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        provider = provider,
                        displayName = obj.optString("displayName", ""),
                        targetId = obj.optString("targetId", ""),
                        targetUrl = obj.optString("targetUrl", ""),
                        configJson = configJson,
                        accountHint = obj.optString("accountHint", ""),
                        enabled = obj.optBoolean("enabled", false),
                        wifiOnly = obj.optBoolean("wifiOnly", true),
                        chargingOnly = obj.optBoolean("chargingOnly", false),
                        frequencyMinutes = resolveFrequencyMinutes(obj),
                    ),
                )
            }
        }
    }

    private fun parsePhotoArray(array: JSONArray?): List<PhotoDestination> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    PhotoDestination(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        provider = PhotoProvider.fromJson(
                            obj.optString("provider", PhotoProvider.GOOGLE_DRIVE.jsonValue),
                        ),
                        displayName = obj.optString("displayName", ""),
                        folderName = obj.optString("folderName", "Vehicle Expenses Photos"),
                        folderId = obj.optString("folderId", ""),
                        configJson = obj.optString("configJson", ""),
                        accountHint = obj.optString("accountHint", ""),
                        enabled = obj.optBoolean("enabled", false),
                        wifiOnly = obj.optBoolean("wifiOnly", true),
                        chargingOnly = obj.optBoolean("chargingOnly", false),
                        frequencyMinutes = resolveFrequencyMinutes(obj),
                    ),
                )
            }
        }
    }

    private fun resolveFrequencyMinutes(obj: JSONObject): Int {
        val minutes = obj.optInt("frequencyMinutes", 0)
        if (minutes > 0) {
            return minutes.coerceIn(
                SpreadsheetDestination.MIN_FREQUENCY_MINUTES,
                SpreadsheetDestination.MAX_FREQUENCY_MINUTES,
            )
        }
        val hours = obj.optInt("frequencyHours", 6)
        return (hours * 60).coerceIn(
            SpreadsheetDestination.MIN_FREQUENCY_MINUTES,
            SpreadsheetDestination.MAX_FREQUENCY_MINUTES,
        )
    }

    private fun toJson(destinations: SyncDestinations): String {
        val root = JSONObject()
        root.put(KEY_SPREADSHEET, JSONArray().apply {
            destinations.spreadsheet.forEach { dest ->
                put(
                    JSONObject().apply {
                        put("id", dest.id)
                        put("provider", dest.provider.jsonValue)
                        put("displayName", dest.displayName)
                        put("targetId", dest.targetId)
                        put("targetUrl", dest.targetUrl)
                        put("configJson", dest.configJson)
                        put("accountHint", dest.accountHint)
                        put("enabled", dest.enabled)
                        put("wifiOnly", dest.wifiOnly)
                        put("chargingOnly", dest.chargingOnly)
                        put("frequencyMinutes", dest.resolvedFrequencyMinutes())
                    },
                )
            }
        })
        root.put(KEY_PHOTO, JSONArray().apply {
            destinations.photo.forEach { dest ->
                put(
                    JSONObject().apply {
                        put("id", dest.id)
                        put("provider", dest.provider.jsonValue)
                        put("displayName", dest.displayName)
                        put("folderName", dest.folderName)
                        put("folderId", dest.folderId)
                        put("configJson", dest.configJson)
                        put("accountHint", dest.accountHint)
                        put("enabled", dest.enabled)
                        put("wifiOnly", dest.wifiOnly)
                        put("chargingOnly", dest.chargingOnly)
                        put("frequencyMinutes", dest.resolvedFrequencyMinutes())
                    },
                )
            }
        })
        return root.toString()
    }

    fun allSpreadsheet(): List<SpreadsheetDestination> = load().spreadsheet

    fun allPhoto(): List<PhotoDestination> = load().photo

    fun enabledSpreadsheet(): List<SpreadsheetDestination> =
        load().spreadsheet.filter { it.enabled && isSpreadsheetConfigured(it) }

    fun enabledPhoto(): List<PhotoDestination> =
        load().photo.filter { it.enabled && isPhotoConfigured(it) }

    fun upsertSpreadsheet(dest: SpreadsheetDestination) {
        val all = load()
        val existing = all.spreadsheet.indexOfFirst { it.id == dest.id }
        val updated = if (existing >= 0) {
            all.spreadsheet.toMutableList().apply { set(existing, dest) }
        } else {
            all.spreadsheet + dest
        }
        save(all.copy(spreadsheet = updated))
    }

    fun upsertPhoto(dest: PhotoDestination) {
        val all = load()
        val existing = all.photo.indexOfFirst { it.id == dest.id }
        val updated = if (existing >= 0) {
            all.photo.toMutableList().apply { set(existing, dest) }
        } else {
            all.photo + dest
        }
        save(all.copy(photo = updated))
    }

    fun removeSpreadsheet(id: String) {
        val all = load()
        save(all.copy(spreadsheet = all.spreadsheet.filter { it.id != id }))
    }

    fun removePhoto(id: String) {
        val all = load()
        save(all.copy(photo = all.photo.filter { it.id != id }))
    }

    /** Primary = first enabled configured dest, else first configured dest. */
    fun spreadsheetDestination(): SpreadsheetDestination? =
        enabledSpreadsheet().firstOrNull() ?: load().spreadsheet.firstOrNull()

    /** Primary = first enabled configured dest, else first configured dest. */
    fun photoDestination(): PhotoDestination? =
        enabledPhoto().firstOrNull() ?: load().photo.firstOrNull()

    fun pendingCount(): Int = prefs.getInt(KEY_SYNC_PENDING_COUNT, 0)

    fun setPendingCount(count: Int) {
        prefs.edit().putInt(KEY_SYNC_PENDING_COUNT, count.coerceAtLeast(0)).apply()
    }

    fun pendingBadgeText(): String = "Pending (${pendingCount()})"

    companion object {
        const val MAX_DESTINATIONS_PER_TYPE = 5
        const val KEY_SYNC_PENDING_COUNT = "sync_pending_count"
        const val PREFS_NAME = "vehicle_settings"
        const val KEY_SYNC_DESTINATIONS_JSON = "sync_destinations_json"
        const val KEY_SYNC_DESTINATIONS_MIGRATED = "sync_destinations_migrated"
        private const val KEY_SPREADSHEET = "spreadsheet"
        private const val KEY_PHOTO = "photo"

        private fun migrateOtherProvider(
            provider: SpreadsheetProvider,
            configJson: String,
        ): SpreadsheetProvider {
            if (provider != SpreadsheetProvider.OTHER || configJson.isBlank()) return provider
            val backendType = try {
                JSONObject(configJson).optString("backendType", "")
            } catch (_: Exception) {
                ""
            }
            return RowDbTabularConfig.providerFromBackendType(backendType) ?: provider
        }

        fun isSpreadsheetConfigured(dest: SpreadsheetDestination?): Boolean {
            if (dest == null) return false
            return when (dest.provider) {
                SpreadsheetProvider.GOOGLE_SHEETS ->
                    dest.targetId.isNotBlank() || dest.targetUrl.isNotBlank()
                SpreadsheetProvider.EXCEL ->
                    dest.targetId.isNotBlank() || dest.configJson.isNotBlank()
                SpreadsheetProvider.ETHERCALC ->
                    dest.configJson.isNotBlank() || dest.targetUrl.isNotBlank()
                SpreadsheetProvider.BASEROW,
                SpreadsheetProvider.NOCODB,
                SpreadsheetProvider.POCKETBASE,
                SpreadsheetProvider.SUPABASE,
                SpreadsheetProvider.AIRTABLE,
                -> RowDbTabularConfig.isConfigured(
                    RowDbTabularConfig.parse(
                        dest.configJson,
                        dest.targetUrl,
                        dest.provider.jsonValue,
                    ),
                )
                SpreadsheetProvider.FIREBASE -> FirebaseTabularConfig.isConfigured(
                    FirebaseTabularConfig.parse(dest.configJson, dest.targetUrl),
                )
                SpreadsheetProvider.ZOHO_SHEET -> ZohoSheetConfig.isConfigured(
                    ZohoSheetConfig.parse(dest.configJson, dest.targetId),
                )
                SpreadsheetProvider.ONLYOFFICE,
                SpreadsheetProvider.COLLABORA,
                -> false
                SpreadsheetProvider.OTHER ->
                    dest.configJson.isNotBlank()
            }
        }

        fun isPhotoConfigured(dest: PhotoDestination?, context: Context? = null): Boolean {
            if (dest == null) return false
            return when (dest.provider) {
                PhotoProvider.GOOGLE_DRIVE -> dest.folderId.isNotBlank() || dest.folderName.isNotBlank()
                PhotoProvider.ONEDRIVE, PhotoProvider.S3, PhotoProvider.OTHER -> {
                    val config = RcloneDestConfig.parse(dest.configJson) ?: return false
                    if (config.remote.isBlank()) return false
                    context?.let { RcloneConfStorage.hasConf(it, dest.id, config) } ?: false
                }
                PhotoProvider.NONE -> false
            }
        }

        fun spreadsheetSummaryLine(dest: SpreadsheetDestination?): String =
            spreadsheetSummaryLine(listOfNotNull(dest))

        fun spreadsheetSummaryLine(dests: List<SpreadsheetDestination>): String {
            val configured = dests.filter { isSpreadsheetConfigured(it) }
            if (configured.isEmpty()) return "Not set up"
            val enabledCount = configured.count { it.enabled }
            val primary = configured.firstOrNull { it.enabled } ?: configured.first()
            val nameHint = destDisplayName(primary, configured.size)
            return when {
                configured.size == 1 -> singleSpreadsheetLine(primary)
                else -> {
                    val kinds = configured.map { it.provider }.distinct()
                    val kindLabel = if (kinds.size == 1) kinds.first().displayLabel() else "Spreadsheet"
                    "$kindLabel · ${configured.size} configured · $enabledCount on · $nameHint"
                }
            }
        }

        fun photoSummaryLine(dest: PhotoDestination?): String =
            photoSummaryLine(listOfNotNull(dest))

        fun photoSummaryLine(dests: List<PhotoDestination>): String {
            val configured = dests.filter { isPhotoConfigured(it) }
            if (configured.isEmpty()) return "Not set up"
            val enabledCount = configured.count { it.enabled }
            val primary = configured.firstOrNull { it.enabled } ?: configured.first()
            val nameHint = destDisplayName(primary, configured.size)
            return when {
                configured.size == 1 -> singlePhotoLine(primary)
                else -> {
                    val kinds = configured.map { it.provider }.distinct()
                    val kindLabel = if (kinds.size == 1) {
                        when (kinds.first()) {
                            PhotoProvider.ONEDRIVE -> "OneDrive"
                            PhotoProvider.S3 -> "S3"
                            PhotoProvider.OTHER -> "Other"
                            else -> "Google Drive"
                        }
                    } else {
                        "Photo"
                    }
                    "$kindLabel · ${configured.size} configured · $enabledCount on · $nameHint"
                }
            }
        }

        private fun singleSpreadsheetLine(dest: SpreadsheetDestination): String {
            val idHint = when {
                dest.targetId.isNotBlank() -> {
                    val prefix = dest.targetId.take(12)
                    if (dest.targetId.length > 12) "$prefix…" else prefix
                }
                dest.targetUrl.isNotBlank() -> {
                    val prefix = dest.targetUrl.take(20)
                    if (dest.targetUrl.length > 20) "$prefix…" else prefix
                }
                else -> ""
            }
            val schedule = formatSchedule(dest.enabled, dest.resolvedFrequencyMinutes())
            return "${dest.provider.displayLabel()} · $idHint · $schedule"
        }

        private fun singlePhotoLine(dest: PhotoDestination): String {
            val schedule = formatSchedule(dest.enabled, dest.resolvedFrequencyMinutes())
            return when (dest.provider) {
                PhotoProvider.ONEDRIVE -> {
                    val path = RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "OneDrive" } ?: "OneDrive"
                    "OneDrive · $path · $schedule"
                }
                PhotoProvider.S3 -> {
                    val path = RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "S3" } ?: "S3"
                    "S3 · $path · $schedule"
                }
                PhotoProvider.OTHER -> {
                    val remote = RcloneDestConfig.parse(dest.configJson)?.remote?.ifBlank { "Other" } ?: "Other"
                    "Other · $remote · $schedule"
                }
                else -> {
                    val folder = dest.folderName.ifBlank { "Vehicle Expenses Photos" }
                    "Google Drive · $folder · $schedule"
                }
            }
        }

        private fun destDisplayName(dest: SpreadsheetDestination, total: Int): String {
            if (dest.displayName.isNotBlank()) return dest.displayName
            if (total > 1) return dest.targetId.take(8).ifBlank { "Sheet" }
            return dest.targetId.take(12).ifBlank { "Sheet" }
        }

        private fun destDisplayName(dest: PhotoDestination, total: Int): String {
            if (dest.displayName.isNotBlank()) return dest.displayName
            val folder = dest.folderName.ifBlank { "Vehicle Expenses Photos" }
            return if (total > 1) folder.take(20) else folder
        }

        private fun formatSchedule(enabled: Boolean, minutes: Int): String {
            if (!enabled) return "Off"
            return if (minutes >= 60 && minutes % 60 == 0) {
                "every ${minutes / 60}h"
            } else {
                "every ${minutes}m"
            }
        }
    }
}