package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import android.content.Intent
import android.net.Uri
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider

enum class TabKind {
    VEHICLES,
    EXPENSES,
    FUEL,
}

data class LogicalTab(val kind: TabKind, val name: String)

data class TabularCapabilities(
    val renameTab: Boolean = true,
    val incrementalWrite: Boolean = true,
    val browse: Boolean = false,
)

data class TabularTestResult(
    val success: Boolean,
    val message: String,
    val needsRemoteConsent: Boolean = false,
    val recoveryIntent: Intent? = null,
)

data class TabularEndpoint(
    val destId: String,
    val provider: SpreadsheetProvider,
    val configJson: String = "",
    val accountHint: String = "",
    val targetId: String = "",
    val targetUrl: String = "",
)

data class CsvZipTarget(val outputDir: java.io.File)

data class CsvZipSource(val uri: Uri)

data class TabularExportRequest(val includeDeleted: Boolean = true)

data class TabularExportResult(val uri: Uri, val message: String = "Export complete")

data class TabularImportRequest(val mergeIntoRoom: Boolean = true)

data class TabularImportResult(val success: Boolean, val message: String = "Import complete")