package com.davidlang.vehicleexpensesautomated

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.DbState
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
internal fun TransferBlockingScreen(state: DbState, progress: String) {
    val title = if (state is Importing) "Importing data…" else "Exporting data…"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(title)
            if (progress.isNotBlank()) Text(progress)
        }
    }
}

@Composable
internal fun ExportFailedNotice(holder: DatabaseHealthState) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(holder) {
        holder.exportFailures.receiveAsFlow().collect { message = it }
    }
    val text = message ?: return
    AlertDialog(
        onDismissRequest = { message = null },
        confirmButton = {
            TextButton(onClick = { message = null }) { Text("OK") }
        },
        text = { Text("Export failed: $text") },
    )
}
