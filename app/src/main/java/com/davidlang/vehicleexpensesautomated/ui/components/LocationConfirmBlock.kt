package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind

/**
 * Location preview + editable name/address for Quick Fill / Trip / Expense.
 * Non-blank place is implicitly confirmed on save. **Wrong station** opens the picker
 * for FUEL_STATION / AUTO_SERVICE when coords are known.
 *
 * Place name and address share one row (each ~half width) so landscape Panel C matches Notes
 * width rather than stacking two full-width fields.
 */
@Composable
fun LocationConfirmBlock(
    statusLine: String,
    name: String,
    address: String,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    /** When non-null and coords known, show Wrong station / Wrong place. */
    pickerKind: LocationLookupKind? = null,
    hasCoords: Boolean = false,
    onWrongStationClick: (() -> Unit)? = null,
) {
    val showPickerButton = pickerKind != null &&
        pickerKind != LocationLookupKind.ADDRESS_ONLY &&
        hasCoords &&
        onWrongStationClick != null
    val wrongLabel = if (pickerKind == LocationLookupKind.AUTO_SERVICE) {
        "Wrong place"
    } else {
        "Wrong station"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (statusLine.isNotBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
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
                    label = { Text("Place name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    showCaretButtons = false,
                )
                CaretEnabledOutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 2,
                    showCaretButtons = false,
                )
            }
        }
        if (showPickerButton) {
            OutlinedButton(
                onClick = onWrongStationClick!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(wrongLabel)
            }
        }
    }
}
