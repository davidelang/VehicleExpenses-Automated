package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal location preview + confirm checkbox for Quick Fill / Trip / Expense.
 * Checkbox default checked = save place with confirmed=true; unchecked = keep coords only.
 */
@Composable
fun LocationConfirmBlock(
    statusLine: String,
    name: String,
    address: String,
    confirmChecked: Boolean,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirmChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (statusLine.isNotBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (editable) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Place name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                maxLines = 2,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Checkbox(
                checked = confirmChecked,
                onCheckedChange = onConfirmChange,
            )
            Text(
                text = "Confirm and save this location",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
