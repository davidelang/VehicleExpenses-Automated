package com.davidlang.vehicleexpensesautomated.data.sync

/**
 * User-facing sync result text. Full errors are logged; UI shows destination names only.
 */
internal object SyncResultMessages {

    fun spreadsheetSummary(
        results: List<Pair<String, SyncResult>>,
        anyFailure: Boolean,
        @Suppress("UNUSED_PARAMETER") totalVehicles: Int,
        @Suppress("UNUSED_PARAMETER") totalExpenses: Int,
        @Suppress("UNUSED_PARAMETER") totalFuel: Int,
    ): String {
        if (!anyFailure) {
            return buildString {
                appendLine("Sync complete:")
                for ((name, result) in results) {
                    if (result.success) {
                        appendLine(
                            "• $name: ${result.vehiclesMerged} vehicles, " +
                                "${result.expensesMerged} expenses, ${result.fuelMerged} fuel",
                        )
                    }
                }
            }.trimEnd()
        }
        return buildString {
            appendLine("Sync failed:")
            for ((name, result) in results) {
                if (!result.success) {
                    appendLine("• $name")
                }
            }
        }.trimEnd()
    }

    fun photoSummary(
        results: List<Pair<String, PhotoBackupResult>>,
        anyFailure: Boolean,
        @Suppress("UNUSED_PARAMETER") totalUploads: Int,
        @Suppress("UNUSED_PARAMETER") totalDownloads: Int,
    ): String {
        if (!anyFailure) {
            return buildString {
                appendLine("Photo backup complete:")
                for ((name, result) in results) {
                    if (result.success) {
                        val detail = when {
                            result.uploads == 0 && result.downloads == 0 -> "up to date"
                            else -> "${result.uploads} uploaded, ${result.downloads} downloaded"
                        }
                        appendLine("• $name: $detail")
                    }
                }
            }.trimEnd()
        }
        return buildString {
            appendLine("Photo backup failed:")
            for ((name, result) in results) {
                if (!result.success) {
                    appendLine("• $name")
                }
            }
        }.trimEnd()
    }

    fun consentWithDest(destName: String, shortMessage: String): String {
        val trimmed = shortMessage.trim().ifBlank { "Sync failed" }
        return "$destName: $trimmed"
    }

    fun failedNamesMessage(failedNames: List<String>): String = when {
        failedNames.isEmpty() -> "Sync failed"
        failedNames.size == 1 -> "Sync failed: ${failedNames.single()}"
        else -> "Sync failed: ${failedNames.joinToString(", ")}"
    }
}