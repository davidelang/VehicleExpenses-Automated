package com.davidlang.vehicleexpensesautomated.data.sync

import java.util.UUID

enum class SpreadsheetProvider(val jsonValue: String) {
    GOOGLE_SHEETS("google_sheets"),
    EXCEL("excel"),
    ETHERCALC("ethercalc"),
    BASEROW("baserow"),
    NOCODB("nocodb"),
    POCKETBASE("pocketbase"),
    SUPABASE("supabase"),
    AIRTABLE("airtable"),
    FIREBASE("firebase"),
    ZOHO_SHEET("zoho_sheet"),
    ONLYOFFICE("onlyoffice"),
    COLLABORA("collabora"),
    /** Unknown / not-yet-listed Tier A subtype fallback. */
    OTHER("other");

    companion object {
        fun fromJson(value: String): SpreadsheetProvider =
            entries.find { it.jsonValue == value } ?: GOOGLE_SHEETS
    }

    fun displayLabel(): String = when (this) {
        GOOGLE_SHEETS -> "Google Sheets"
        EXCEL -> "Excel"
        ETHERCALC -> "EtherCalc"
        BASEROW -> "Baserow"
        NOCODB -> "NocoDB"
        POCKETBASE -> "PocketBase"
        SUPABASE -> "Supabase"
        AIRTABLE -> "Airtable"
        FIREBASE -> "Firebase"
        ZOHO_SHEET -> "Zoho Sheet"
        ONLYOFFICE -> "OnlyOffice"
        COLLABORA -> "Collabora"
        OTHER -> "Other"
    }

    fun isRowDbBackend(): Boolean = when (this) {
        BASEROW, NOCODB, POCKETBASE, SUPABASE, AIRTABLE -> true
        else -> false
    }
}

enum class PhotoProvider(val jsonValue: String) {
    GOOGLE_DRIVE("google_drive"),
    ONEDRIVE("onedrive"),
    /** First-class S3 / S3-compatible (rclone `type=s3` managed remote). */
    S3("s3"),
    /** Generic rclone-backed storage (WebDAV, SFTP, Azure, …). Legacy JSON value was `rclone`. */
    OTHER("other"),
    NONE("none");

    companion object {
        fun fromJson(value: String): PhotoProvider = when (value) {
            "rclone" -> OTHER
            else -> entries.find { it.jsonValue == value } ?: GOOGLE_DRIVE
        }
    }

    /** True when uploads/downloads use [RclonePhotoBackend] (wire manifest provider stays `rclone`). */
    fun usesRcloneBackend(): Boolean = this == OTHER || this == ONEDRIVE || this == S3
}

data class SpreadsheetDestination(
    val id: String = UUID.randomUUID().toString(),
    val provider: SpreadsheetProvider = SpreadsheetProvider.GOOGLE_SHEETS,
    val displayName: String = "",
    val targetId: String = "",
    val targetUrl: String = "",
    /** Opaque provider binding: Graph workbook id, EtherCalc base+room, Other backend config. */
    val configJson: String = "",
    val accountHint: String = "",
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    /** Background interval in minutes (min 15, max 24h). Legacy [frequencyHours] migrated on load. */
    val frequencyMinutes: Int = 60,
    @Deprecated("Use frequencyMinutes", ReplaceWith("frequencyMinutes"))
    val frequencyHours: Int = 0,
) {
    fun resolvedFrequencyMinutes(): Int = when {
        frequencyMinutes > 0 -> frequencyMinutes.coerceIn(MIN_FREQUENCY_MINUTES, MAX_FREQUENCY_MINUTES)
        frequencyHours > 0 -> (frequencyHours * 60).coerceIn(MIN_FREQUENCY_MINUTES, MAX_FREQUENCY_MINUTES)
        else -> 60
    }

    companion object {
        const val MIN_FREQUENCY_MINUTES = 15
        const val MAX_FREQUENCY_MINUTES = 24 * 60
    }
}

data class PhotoDestination(
    val id: String = UUID.randomUUID().toString(),
    val provider: PhotoProvider = PhotoProvider.GOOGLE_DRIVE,
    val displayName: String = "",
    val folderName: String = "Vehicle Expenses Photos",
    val folderId: String = "",
    /** Rclone: JSON with remote, pathPrefix, confFileName. */
    val configJson: String = "",
    val accountHint: String = "",
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val frequencyMinutes: Int = 60,
    @Deprecated("Use frequencyMinutes", ReplaceWith("frequencyMinutes"))
    val frequencyHours: Int = 0,
) {
    fun resolvedFrequencyMinutes(): Int = when {
        frequencyMinutes > 0 -> frequencyMinutes.coerceIn(MIN_FREQUENCY_MINUTES, MAX_FREQUENCY_MINUTES)
        frequencyHours > 0 -> (frequencyHours * 60).coerceIn(MIN_FREQUENCY_MINUTES, MAX_FREQUENCY_MINUTES)
        else -> 60
    }

    companion object {
        const val MIN_FREQUENCY_MINUTES = 15
        const val MAX_FREQUENCY_MINUTES = 24 * 60
    }
}

data class SyncDestinations(
    val spreadsheet: List<SpreadsheetDestination> = emptyList(),
    val photo: List<PhotoDestination> = emptyList(),
)