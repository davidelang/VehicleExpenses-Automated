package com.davidlang.vehicleexpensesautomated.data.transfer

import org.json.JSONArray
import org.json.JSONObject

data class BackupFileEntry(
    val path: String,
    val bytes: Long,
    val sha256: String,
)

/** Fields shared with the Fuel Smoothing plugin. Import does not check them. */
data class BackupManifest(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val dbSchemaVersion: Int?,
    val createdUtc: String,
    val androidSdkInt: Int,
    val dbExport: String,
    val dbOpenError: String?,
    val entries: List<BackupFileEntry>,
    val pendingCommit: JSONObject? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", BackupContents.FORMAT)
        put("formatVersion", BackupContents.FORMAT_VERSION)
        put("appKey", BackupContents.APP_KEY)
        put("applicationId", applicationId)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("buildType", buildType)
        put("dbSchemaVersion", dbSchemaVersion ?: JSONObject.NULL)
        put("createdUtc", createdUtc)
        put("androidSdkInt", androidSdkInt)
        put("dbExport", dbExport)
        put("dbOpenError", dbOpenError ?: JSONObject.NULL)
        put("entries", JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("path", entry.path)
                    put("bytes", entry.bytes)
                    put("sha256", entry.sha256)
                })
            }
        })
        put("excluded", JSONArray().apply {
            BackupContents.EXCLUDED_LABELS.forEach { put(it) }
        })
        if (pendingCommit != null) put("pendingCommit", pendingCommit)
    }
}
