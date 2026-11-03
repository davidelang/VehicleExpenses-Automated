package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.davidlang.vehicleexpensesautomated.R

object SyncSetupDocs {
    const val BASE = "https://github.com/davidelang/VehicleExpenses-Automated/blob/master/docs/reference/self-host"

    fun index(): String = "$BASE/INDEX.md"

    fun photosReadme(): String = "$BASE/photos/README.md"

    fun tabularReadme(): String = "$BASE/tabular/README.md"

    fun photo(backend: String): String = "$BASE/photos/$backend.md"

    fun tabular(backend: String): String = "$BASE/tabular/$backend.md"

    /** Maps rclone provider type names to cheatsheet file stems (without .md). */
    fun photoCheatsheetForRcloneType(type: String): String? = when (type.lowercase()) {
        "webdav" -> "webdav"
        "sftp" -> "sftp"
        "ftp" -> "ftp"
        "smb" -> "smb"
        "seafile" -> "seafile"
        else -> null
    }

    fun photoUrlForRcloneType(type: String): String {
        val stem = photoCheatsheetForRcloneType(type)
        return if (stem != null) photo(stem) else photosReadme()
    }

    fun open(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        // kept: failure stays on this call so the screen or caller can continue
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_could_not_open_link), Toast.LENGTH_LONG).show()
            }
        }
    }
}