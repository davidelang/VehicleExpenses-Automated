package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFrequencyUi

data class SyncScheduleUiState(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val frequencyHours: Float,
)

@Composable
fun SyncBackgroundScheduleSection(
    title: String,
    enableLabel: String,
    intervalSliderLabel: String,
    state: SyncScheduleUiState,
    onEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
    onFrequencyHoursChange: (Float) -> Unit,
    enableSwitchEnabled: Boolean = true,
    deferredStubMessage: String? = null,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    deferredStubMessage?.let { message ->
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    SyncScheduleSwitchSetting(
        label = enableLabel,
        checked = state.enabled,
        enabled = enableSwitchEnabled,
        onCheckedChange = onEnabledChange,
    )
    SyncScheduleSwitchSetting("Wi-Fi only", state.wifiOnly, onCheckedChange = onWifiOnlyChange)
    SyncScheduleSwitchSetting("Charging only", state.chargingOnly, onCheckedChange = onChargingOnlyChange)
    SyncScheduleSliderSetting(
        label = intervalSliderLabel,
        value = state.frequencyHours,
        range = SpreadsheetDestination.MIN_FREQUENCY_HOURS..SpreadsheetDestination.MAX_FREQUENCY_HOURS,
        onValueChange = onFrequencyHoursChange,
    )
}

@Composable
fun SyncScheduleSwitchSetting(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun SyncScheduleSliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(SyncFrequencyUi.formatHoursLabel(value), style = MaterialTheme.typography.labelSmall)
    }
}