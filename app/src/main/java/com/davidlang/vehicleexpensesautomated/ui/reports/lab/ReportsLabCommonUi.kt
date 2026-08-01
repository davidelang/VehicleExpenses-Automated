package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Title line: optional page title + Share.
 * Page help goes to top-bar Info via [RegisterPageHelp] (no mid-screen Info icon).
 * Prefer empty [title] on hub (app bar already says Reports).
 */
@Composable
fun ReportsLabTitleRow(
    title: String?,
    infoTitle: String,
    infoText: String?,
    shareActions: ReportsLabShareActions?,
) {
    val context = LocalContext.current
    if (!infoText.isNullOrBlank()) {
        RegisterPageHelp(infoTitle, infoText)
    }
    if (title.isNullOrBlank() && shareActions == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                softWrap = true,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Spacer so icons sit end-aligned on hub
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
        if (shareActions != null) {
            ReportsLabShareIconButton(context = context, actions = shareActions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsLabFilterBar(
    state: ReportsLabFilterState,
    vehicles: List<Vehicle>,
    onChange: (ReportsLabFilterState) -> Unit,
) {
    val context = LocalContext.current
    var vehicleExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filters", style = MaterialTheme.typography.titleSmall)
        // Simple Row — never host ExposedDropdownMenuBox inside AdaptiveItemGrid
        // (double subcompose measure yields flaky / dead menu hits).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = vehicleExpanded,
                onExpandedChange = { vehicleExpanded = it },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 140.dp, max = 280.dp),
            ) {
                val label = when (state.vehicleMode) {
                    LabVehicleMode.ALL -> "All vehicles"
                    LabVehicleMode.EACH -> "Each vehicle"
                    LabVehicleMode.SINGLE -> {
                        val id = state.vehicleId
                        when (id) {
                            null -> "All vehicles"
                            0 -> "Unknown"
                            else -> vehicles.firstOrNull { it.id == id }?.name
                                ?: "Vehicle $id"
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vehicle") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = vehicleExpanded,
                    onDismissRequest = { vehicleExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All vehicles") },
                        onClick = {
                            val next = state.copy(
                                vehicleMode = LabVehicleMode.ALL,
                                vehicleId = null,
                            )
                            ReportsLabPrefs.save(context, next)
                            onChange(next)
                            vehicleExpanded = false
                            Log.i("ReportsLabFilter", "vehicleMode=ALL")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Each vehicle") },
                        onClick = {
                            val next = state.copy(
                                vehicleMode = LabVehicleMode.EACH,
                                vehicleId = null,
                            )
                            ReportsLabPrefs.save(context, next)
                            onChange(next)
                            vehicleExpanded = false
                            Log.i("ReportsLabFilter", "vehicleMode=EACH")
                        },
                    )
                    vehicles.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.name) },
                            onClick = {
                                val next = state.copy(
                                    vehicleMode = LabVehicleMode.SINGLE,
                                    vehicleId = v.id,
                                )
                                ReportsLabPrefs.save(context, next)
                                onChange(next)
                                vehicleExpanded = false
                                Log.i("ReportsLabFilter", "vehicleMode=SINGLE id=${v.id}")
                            },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = periodExpanded,
                onExpandedChange = { periodExpanded = it },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 140.dp, max = 280.dp),
            ) {
                val periodLabelUi = when (state.period) {
                    LabPeriod.ALL_TIME -> "All time"
                    LabPeriod.YTD -> "YTD"
                    LabPeriod.LAST_12_MONTHS -> "Last 12 months"
                    LabPeriod.LAST_90_DAYS -> "Last 90 days"
                    LabPeriod.CUSTOM -> "Custom range"
                }
                OutlinedTextField(
                    value = periodLabelUi,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Period") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = periodExpanded,
                    onDismissRequest = { periodExpanded = false },
                ) {
                    LabPeriod.entries.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (p) {
                                        LabPeriod.ALL_TIME -> "All time"
                                        LabPeriod.YTD -> "YTD"
                                        LabPeriod.LAST_12_MONTHS -> "Last 12 months"
                                        LabPeriod.LAST_90_DAYS -> "Last 90 days"
                                        LabPeriod.CUSTOM -> "Custom range"
                                    },
                                )
                            },
                            onClick = {
                                val next = state.copy(period = p)
                                ReportsLabPrefs.save(context, next)
                                onChange(next)
                                periodExpanded = false
                                Log.i("ReportsLabFilter", "period=$p")
                            },
                        )
                    }
                }
            }
        }
        if (state.period == LabPeriod.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.wrapContentWidth(),
                ) {
                    Text("Start ${dateFmt.format(Date(state.customStartMs))}")
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.wrapContentWidth(),
                ) {
                    Text("End ${dateFmt.format(Date(state.customEndMs))}")
                }
            }
        }
        Text(
            periodLabel(state),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.customStartMs)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { day ->
                        val next = state.copy(customStartMs = startOfDay(day), period = LabPeriod.CUSTOM)
                        ReportsLabPrefs.save(context, next)
                        onChange(next)
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
    if (showEndPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.customEndMs)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { day ->
                        val next = state.copy(customEndMs = endOfDay(day), period = LabPeriod.CUSTOM)
                        ReportsLabPrefs.save(context, next)
                        onChange(next)
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun startOfDay(ms: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun endOfDay(ms: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

/**
 * Child report page shell: no dual banners; optional title/info/share on one row;
 * filters content-width; content then secondary share is only via icon.
 */
@Composable
fun ReportsLabScreenScaffold(
    title: String,
    infoText: String? = null,
    filterState: ReportsLabFilterState,
    vehicles: List<Vehicle>,
    onFilterChange: (ReportsLabFilterState) -> Unit,
    shareActions: ReportsLabShareActions? = null,
    /** @deprecated Use [shareActions]; ignored when shareActions is set. */
    shareRow: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Filters live outside verticalScroll so ExposedDropdown menus own pointer hits
    // (scrollable content under a menu was eating taps → Edit Fill / no-op selects).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportsLabTitleRow(
            title = title,
            infoTitle = title,
            infoText = infoText,
            shareActions = shareActions,
        )
        ReportsLabFilterBar(state = filterState, vehicles = vehicles, onChange = onFilterChange)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        // Legacy shareRow only if no shareActions (migration path)
        if (shareActions == null) {
            shareRow()
        }
    }
}

@Composable
fun ReportsLabEmpty(message: String) {
    com.davidlang.vehicleexpensesautomated.ui.components.EmptyStateText(message)
}
