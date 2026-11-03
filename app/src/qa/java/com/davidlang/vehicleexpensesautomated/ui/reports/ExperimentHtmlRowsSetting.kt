package com.davidlang.vehicleexpensesautomated.ui.reports

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R

/** Qa Settings slot. Main no longer names ExperimentReportHtml. */
@Composable
fun ExperimentHtmlRowsSetting() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ExperimentReportHtml.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var rows by remember { mutableIntStateOf(ExperimentReportHtml.htmlRowsPerFile(prefs)) }
    Text(
        "Photos per HTML part. 0 = unlimited (one _part01). Default 50.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
        softWrap = true,
        maxLines = 4,
    )
    OutlinedTextField(
        value = rows.toString(),
        onValueChange = { text ->
            val v = ExperimentReportHtml.sanitizeHtmlRowsPerFileWrite(text)
            rows = v
            prefs.edit().putInt(ExperimentReportHtml.KEY_HTML_ROWS_PER_FILE, v).apply()
        },
        label = { Text(stringResource(R.string.reports_experiment_html_photos_per_file_0_unlimite)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
