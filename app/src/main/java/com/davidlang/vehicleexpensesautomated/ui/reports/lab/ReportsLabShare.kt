package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ReportsLabShare {
    fun shareText(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "Share report"))
        } else {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_LONG).show()
        }
    }

    fun shareCsv(context: Context, fileName: String, csvBody: String, subject: String) {
        try {
            // filesDir is covered by FileProvider files-path (not cacheDir)
            val dir = File(context.filesDir, "reports_lab")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(csvBody)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Share CSV"))
            } else {
                Toast.makeText(context, "No app available to share CSV", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "CSV share failed", Toast.LENGTH_LONG).show()
        }
    }

    fun csvEscape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needs) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
