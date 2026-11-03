package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.CorruptIncoming
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

@Composable
fun CorruptIncomingSection(syncGeneration: Any?) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick, syncGeneration) { CorruptIncoming.openItems() }
    LaunchedEffect(syncGeneration) {
        val unseen = CorruptIncoming.takeUnseen()
        if (unseen > 0) {
            Toast.makeText(context, AppStrings.get(R.string.settings_corrupt_incoming_data), Toast.LENGTH_LONG).show()
        }
        tick++
    }
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.settings_corrupt_incoming_data), style = MaterialTheme.typography.titleMedium)
        items.forEach { item ->
            val choice = when (item.disposition) {
                CorruptIncoming.LEAVE -> AppStrings.get(R.string.settings_keep_local_and_leave_the_sheet)
                CorruptIncoming.WRITE_BACK -> AppStrings.get(R.string.settings_keep_local_and_write_it_back)
                CorruptIncoming.ACCEPT -> AppStrings.get(R.string.settings_accept_remote_field_cleared)
                else -> item.disposition
            }
            Text(
                "${item.sheet} ${item.syncId} ${item.column}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                choice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                CorruptIncoming.setDisposition(item.key, CorruptIncoming.WRITE_BACK)
                tick++
            }) { Text(AppStrings.get(R.string.settings_keep_local_and_write_it_back)) }
            TextButton(onClick = {
                CorruptIncoming.setDisposition(item.key, CorruptIncoming.LEAVE)
                tick++
            }) { Text(AppStrings.get(R.string.settings_keep_local_and_leave_the_sheet)) }
            TextButton(onClick = {
                CorruptIncoming.setDisposition(item.key, CorruptIncoming.ACCEPT)
                tick++
            }) { Text(AppStrings.get(R.string.settings_accept_remote_field_cleared)) }
        }
    }
}
