package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Name-only list when a dash veto does not leave exactly one vehicle.
 * [survivorIds] empty, or the same ids as [vehicles]: the full list, no expand control.
 * A non-empty proper subset: those names, and "Show all vehicles" swaps in [vehicles].
 */
@Composable
fun VehicleNotIdentifiedDialog(
    vehicles: List<Vehicle>,
    survivorIds: List<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val settled = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (settled.compareAndSet(false, true)) onDismiss()
        }
    }
    fun dismiss() {
        if (settled.compareAndSet(false, true)) onDismiss()
    }
    fun select(id: Int) {
        if (settled.compareAndSet(false, true)) onSelect(id)
    }

    val vehicleIds = remember(vehicles) { vehicles.map { it.id }.toSet() }
    val survivorSet = remember(survivorIds) { survivorIds.toSet() }
    val properSubset = survivorIds.isNotEmpty() &&
        survivorSet.all { it in vehicleIds } &&
        survivorSet != vehicleIds
    var showAll by remember { mutableStateOf(false) }
    val rows = if (properSubset && !showAll) {
        vehicles.filter { it.id in survivorSet }
    } else {
        vehicles
    }

    Dialog(onDismissRequest = { dismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .padding(24.dp),
            ) {
                Text(
                    text = "Vehicle not identified",
                    style = MaterialTheme.typography.headlineSmall,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .heightIn(max = 320.dp),
                ) {
                    items(rows, key = { it.id }) { vehicle ->
                        Text(
                            text = vehicle.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select(vehicle.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                if (properSubset && !showAll) {
                    TextButton(onClick = { showAll = true }) {
                        Text("Show all vehicles")
                    }
                }
            }
        }
    }
}
