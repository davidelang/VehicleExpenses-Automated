package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import java.io.File

/** Share payload builders for [ReportsLabShareIconButton]. */
data class ReportsLabShareActions(
    val subject: String,
    val textBody: () -> String,
    val csvFileName: String,
    val csvBody: () -> String,
    /** Defaults to CSV stem + `.pdf`. */
    val pdfFileName: String = csvFileName.removeSuffix(".csv") + ".pdf",
    /**
     * When non-null, Share → PDF writes a real `application/pdf` via FileProvider.
     * Prefer [ReportsLabPdf.fromPlainText] fed from the same builder as [textBody].
     */
    val pdfBody: (() -> ByteArray)? = null,
)

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

    fun sharePdf(context: Context, fileName: String, pdfBytes: ByteArray, subject: String) {
        try {
            if (pdfBytes.isEmpty()) {
                Toast.makeText(context, "PDF is empty", Toast.LENGTH_LONG).show()
                return
            }
            val dir = File(context.filesDir, "reports_lab")
            if (!dir.exists()) dir.mkdirs()
            val safeName = fileName.ifBlank { "lab_report.pdf" }.let {
                if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf"
            }
            val file = File(dir, safeName)
            file.writeBytes(pdfBytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Share PDF"))
            } else {
                Toast.makeText(context, "No app available to share PDF", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "PDF share failed", Toast.LENGTH_LONG).show()
        }
    }

    fun csvEscape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needs) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}

/**
 * Single share control → format picker (TEXT / CSV / PDF) → system share sheet.
 */
@Composable
fun ReportsLabShareIconButton(
    context: Context,
    actions: ReportsLabShareActions,
) {
    var showPicker by remember { mutableStateOf(false) }
    IconButton(onClick = { showPicker = true }) {
        Icon(Icons.Default.Share, contentDescription = "Share report")
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Share as") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showPicker = false
                            try {
                                ReportsLabShare.shareText(
                                    context,
                                    actions.subject,
                                    actions.textBody(),
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Share failed", Toast.LENGTH_LONG)
                                    .show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("TEXT")
                    }
                    TextButton(
                        onClick = {
                            showPicker = false
                            try {
                                ReportsLabShare.shareCsv(
                                    context,
                                    actions.csvFileName,
                                    actions.csvBody(),
                                    actions.subject,
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "CSV share failed", Toast.LENGTH_LONG)
                                    .show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("CSV")
                    }
                    TextButton(
                        onClick = {
                            showPicker = false
                            val body = actions.pdfBody
                            if (body != null) {
                                try {
                                    ReportsLabShare.sharePdf(
                                        context,
                                        actions.pdfFileName,
                                        body(),
                                        actions.subject,
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        e.message ?: "PDF share failed",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } else {
                                Toast.makeText(context, "PDF coming soon", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (actions.pdfBody != null) "PDF" else "PDF (coming soon)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        )
    }
}
