package com.davidlang.vehicleexpensesautomated.ui.components

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal location preview + confirm checkbox for Quick Fill / Trip / Expense.
 * Checkbox default checked = save place with confirmed=true; unchecked = keep coords only.
 *
 * Place name and address share one row (each ~half width) so landscape Panel C matches Notes
 * width rather than stacking two full-width fields.
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
    /** Shown next to the confirm checkbox. */
    confirmLabel: String = "Confirm this location",
    /**
     * When false, only name/address (and status) are shown — caller can place the
     * confirm checkbox on a shared row (e.g. next to “Time is now” on Start trip).
     */
    showConfirmCheckbox: Boolean = true,
) {
    Column(modifier = modifier) {
        // Only show non-blank status (callers should pass "" on happy-path resolved address).
        if (statusLine.isNotBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (editable) {
            // One line: name | address (half width each under Notes on QF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CaretEnabledOutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.fuel_place_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    showCaretButtons = false,
                )
                CaretEnabledOutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.fuel_address)) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 2,
                    showCaretButtons = false,
                )
            }
        }
        if (showConfirmCheckbox) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Checkbox(
                    checked = confirmChecked,
                    onCheckedChange = onConfirmChange,
                )
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
