package com.davidlang.vehicleexpensesautomated.data.sync

import kotlin.math.roundToInt
import java.util.UUID

/** Which destinations participate in a sync run. */
enum class SyncDestinationScope {
    /** Manual Sync now — all configured destinations. */
    CONFIGURED,
    /** Background workers — only destinations with background sync enabled. */
    ENABLED,
}

enum class SpreadsheetProvider(val jsonValue: String) {
    GOOGLE_SHEETS("google_sheets"),
    /** Microsoft Graph Excel Online (capability id excel-graph; legacy wire value was "excel"). */
    EXCEL_GRAPH("excel-graph"),
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
        fun fromJson(value: String): SpreadsheetProvider = when (value) {
            "excel" -> EXCEL_GRAPH // legacy pin/UI wire
            else -> entries.find { it.jsonValue == value } ?: GOOGLE_SHEETS
        }
    }

    fun displayLabel(): String = when (this) {
        GOOGLE_SHEETS -> "Google Sheets"
        EXCEL_GRAPH -> "Excel (Graph)"
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
    /** Background interval in minutes (min 15, max 24h). Legacy hours migrated on JSON load. */
    val frequencyMinutes: Int = 60,
) {
    fun resolvedFrequencyMinutes(): Int =
        SyncFrequencyMigration.resolveMinutes(frequencyMinutes)

    companion object {
        const val MIN_FREQUENCY_MINUTES = SyncFrequencyMigration.MIN_MINUTES
        const val MAX_FREQUENCY_MINUTES = SyncFrequencyMigration.MAX_MINUTES
        const val MIN_FREQUENCY_HOURS = 0.25f
        const val MAX_FREQUENCY_HOURS = 24f
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
) {
    fun resolvedFrequencyMinutes(): Int =
        SyncFrequencyMigration.resolveMinutes(frequencyMinutes)

    companion object {
        const val MIN_FREQUENCY_MINUTES = SyncFrequencyMigration.MIN_MINUTES
        const val MAX_FREQUENCY_MINUTES = SyncFrequencyMigration.MAX_MINUTES
    }
}

data class SyncDestinations(
    val spreadsheet: List<SpreadsheetDestination> = emptyList(),
    val photo: List<PhotoDestination> = emptyList(),
)

/** UI helpers: slider shows hours; persistence stays in minutes. */
object SyncFrequencyUi {
    fun minutesToDisplayHours(minutes: Int): Float =
        (minutes / 60f).coerceIn(
            SpreadsheetDestination.MIN_FREQUENCY_HOURS,
            SpreadsheetDestination.MAX_FREQUENCY_HOURS,
        )

    fun snapHours(hours: Float): Float {
        val clamped = hours.coerceIn(
            SpreadsheetDestination.MIN_FREQUENCY_HOURS,
            SpreadsheetDestination.MAX_FREQUENCY_HOURS,
        )
        return (clamped * 4f).roundToInt() / 4f
    }

    fun hoursToMinutes(hours: Float): Int =
        (snapHours(hours) * 60f).roundToInt().coerceIn(
            SpreadsheetDestination.MIN_FREQUENCY_MINUTES,
            SpreadsheetDestination.MAX_FREQUENCY_MINUTES,
        )

    fun formatHoursLabel(hours: Float): String {
        val snapped = snapHours(hours)
        return if (snapped == snapped.toInt().toFloat()) {
            "${snapped.toInt()} h"
        } else {
            String.format("%.2f h", snapped)
        }
    }
}