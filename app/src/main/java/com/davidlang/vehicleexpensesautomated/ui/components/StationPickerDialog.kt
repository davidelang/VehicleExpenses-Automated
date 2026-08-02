package com.davidlang.vehicleexpensesautomated.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookup
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupResult
import com.davidlang.vehicleexpensesautomated.data.location.OverpassClient
import com.davidlang.vehicleexpensesautomated.ui.util.NetworkStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Nearby station/place picker for “Wrong station”. Capture GPS is not changed by selection.
 */
@Composable
fun StationPickerDialog(
    lat: Double,
    lon: Double,
    kind: LocationLookupKind,
    onSelect: (LocationLookupResult) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var radiusM by remember { mutableDoubleStateOf(OverpassClient.PICKER_INITIAL_RADIUS_M) }
    var loading by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<LocationLookupResult>>(emptyList()) }
    var errorLine by remember { mutableStateOf<String?>(null) }
    val online = NetworkStatus.hasUsableNetwork(context)

    fun reload(r: Double) {
        if (!online) {
            loading = false
            results = emptyList()
            errorLine = "Offline — no station search. Enter place manually."
            return
        }
        loading = true
        errorLine = null
        scope.launch {
            val list = LocationLookup.listNearby(
                lat = lat,
                lon = lon,
                kind = kind,
                radiusM = r,
                uiTimeout = true,
            )
            results = list
            loading = false
            errorLine = if (list.isEmpty()) {
                "No stations found within ${r.roundToInt()} m"
            } else {
                null
            }
        }
    }

    LaunchedEffect(lat, lon, kind, online) {
        reload(radiusM)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (kind == LocationLookupKind.AUTO_SERVICE) {
                    "Pick a place"
                } else {
                    "Pick a station"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 420.dp),
            ) {
                Text(
                    text = "Within ${radiusM.roundToInt()} m · nearest first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    errorLine?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (results.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                        ) {
                            items(results, key = { "${it.name}|${it.distanceM}|${it.poiLat}" }) { item ->
                                StationPickerRow(
                                    item = item,
                                    onClick = { onSelect(item) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val next = (radiusM * 2.0).coerceAtMost(OverpassClient.PICKER_MAX_RADIUS_M)
                    if (next <= radiusM + 0.5) {
                        Toast.makeText(
                            context,
                            "Already at max range (${OverpassClient.PICKER_MAX_RADIUS_M.roundToInt()} m)",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        radiusM = next
                        reload(next)
                    }
                },
                enabled = !loading && online,
            ) {
                Text("Extend range")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onManual) {
                    Text("Enter manually")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun StationPickerRow(
    item: LocationLookupResult,
    onClick: () -> Unit,
) {
    val dist = item.distanceM?.let { "${it.roundToInt()} m" } ?: ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(
            text = item.name.ifBlank { "(unnamed)" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
        )
        val sub = listOfNotNull(
            item.address.takeIf { it.isNotBlank() },
            dist.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
            )
        }
    }
}
