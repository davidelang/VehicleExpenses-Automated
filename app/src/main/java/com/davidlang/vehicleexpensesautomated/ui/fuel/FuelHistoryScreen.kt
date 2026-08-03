package com.davidlang.vehicleexpensesautomated.ui.fuel

import com.davidlang.vehicleexpensesautomated.R

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.EmptyStateText
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import com.davidlang.vehicleexpensesautomated.ui.components.ZoomablePhotoDialog
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasArchiveIdentity
import com.davidlang.vehicleexpensesautomated.ui.components.firstReadableFuelPhotoUri
import com.davidlang.vehicleexpensesautomated.ui.components.fuelHasDeadLocalOnly
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelHistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    RegisterPageHelp(
        title = stringResource(R.string.nav_fuel_history),
        stringResource(R.string.fuel_per_vehicle_tabs_list_fuel_fills_only_trip_start),
        stringResource(R.string.fuel_tap_a_row_to_edit_missing_photos_can_be_fetched_),
    )
    val photoStorage = settingsViewModel.photoStorageManager
    val fills by fuelViewModel.fuelEntries.collectAsState()
    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val destId = remember { SyncDestinationStore(context).photoDestination()?.id }

    val vehicleTabs = remember(vehicles) {
        vehicles.filter {
            it.id != 0 &&
                it.syncId != com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedVehicleId = vehicleTabs.getOrNull(selectedTab)?.id
    val rows = remember(fills, selectedVehicleId) {
        if (selectedVehicleId == null) {
            emptyList()
        } else {
            // F4.1: fills only — trip starts live on Trip miles report.
            fills
                .filter { it.vehicleId == selectedVehicleId && it.tripType.isBlank() }
                .sortedByDescending { it.timestamp }
        }
    }
    // Local row refresh map after fetch/scrub
    var rowOverrides by remember { mutableStateOf<Map<Long, FuelEntry>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FeatureScreenHeader(
            title = stringResource(R.string.nav_fuel_history),
            subtitle = stringResource(R.string.fuel_per_vehicle_fills_no_trip_starts_tap_a_c),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (vehicleTabs.isEmpty()) {
            EmptyStateText(stringResource(R.string.fuel_no_vehicles_yet))
            return
        }
        ScrollableTabRow(selectedTabIndex = selectedTab.coerceIn(0, vehicleTabs.lastIndex)) {
            vehicleTabs.forEachIndexed { index, v ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(v.name) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (rows.isEmpty()) {
            EmptyStateText(stringResource(R.string.fuel_no_fuel_entries_for_this_vehicle))
        } else {
            val displayRows = rows.map { base -> rowOverrides[base.id] ?: base }
            AdaptiveItemGrid(items = displayRows) { entry ->
                FuelHistoryRow(
                    entry = entry,
                    dateLabel = dateFmt.format(Date(entry.timestamp)),
                    defaultSymbol = defaultSymbol,
                    photoStorage = photoStorage,
                    destId = destId,
                    onOpen = { navController.navigate("fuel/${entry.id}") },
                    onFetched = { refreshed ->
                        rowOverrides = rowOverrides + (refreshed.id to refreshed)
                    },
                    onScrubbed = { updated ->
                        rowOverrides = rowOverrides + (updated.id to updated)
                    },
                    scrub = { e -> fuelViewModel.scrubUnreadableFuelPhotos(e) },
                    download = { e -> fuelViewModel.downloadFuelPhoto(e) },
                    reload = { id -> fuelViewModel.getFuelById(id) },
                )
            }
        }
    }
}

@Composable
private fun FuelHistoryRow(
    entry: FuelEntry,
    dateLabel: String,
    defaultSymbol: String,
    photoStorage: PhotoStorageManager,
    destId: String?,
    onOpen: () -> Unit,
    onFetched: (FuelEntry) -> Unit,
    onScrubbed: (FuelEntry) -> Unit,
    scrub: suspend (FuelEntry) -> FuelEntry,
    download: suspend (FuelEntry) -> String?,
    reload: suspend (Long) -> FuelEntry?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var display by remember(entry.id, entry.photoUrl, entry.updatedAt) { mutableStateOf(entry) }
    var fetching by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id, entry.photoUrl) {
        display = entry
        if (fuelHasDeadLocalOnly(entry.photoUrl, photoStorage)) {
            val scrubbed = scrub(entry)
            display = scrubbed
            onScrubbed(scrubbed)
        }
    }

    val thumbUri = firstReadableFuelPhotoUri(display.photoUrl, photoStorage)
    val canFetch = thumbUri == null && fuelHasArchiveIdentity(display, destId)
    val flags = buildList {
        if (display.isPartialFill) add("partial")
        if (display.economyIgnored) add("ignored")
    }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }

    TappableCard(onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 88.dp, max = 120.dp)
                    .heightIn(min = 56.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    thumbUri != null -> {
                        var showZoom by remember { mutableStateOf(false) }
                        Image(
                            painter = rememberAsyncImagePainter(thumbUri),
                            contentDescription = stringResource(R.string.fuel_fill_photo),
                            modifier = Modifier
                                .size(56.dp)
                                .fillMaxSize()
                                .clickable { showZoom = true },
                            contentScale = ContentScale.Crop,
                        )
                        if (showZoom) {
                            ZoomablePhotoDialog(
                                uris = listOf(thumbUri),
                                onDismiss = { showZoom = false },
                            )
                        }
                    }
                    canFetch -> {
                        TextButton(
                            onClick = {
                                if (fetching) return@TextButton
                                scope.launch {
                                    fetching = true
                                    try {
                                        val scrubbed = scrub(display)
                                        display = scrubbed
                                        val local = download(scrubbed)
                                        val refreshed = reload(display.id) ?: scrubbed
                                        display = refreshed
                                        onFetched(refreshed)
                                        if (local != null) {
                                            Toast.makeText(context, context.getString(R.string.fuel_image_fetched), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.fuel_could_not_fetch_image), Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        fetching = false
                                    }
                                }
                            },
                            enabled = !fetching,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                if (fetching) "Fetching…" else "Fetch image from archive",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 3,
                                softWrap = true,
                            )
                        }
                    }
                    else -> {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$dateLabel$flags",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    softWrap = true,
                )
                if (display.tripType.isNotBlank()) {
                    Text(
                        "Trip: ${display.tripType}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = true,
                    )
                }
                Text(
                    "${UnitFormat.odometerReadingLabel(display.odometer, context)} · " +
                        "${CurrencyCodes.formatAmount(display.cost, display.currency, defaultSymbol)} · " +
                        VolumeUnits.formatVolume(context, display.gallons, decimals = 3),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    softWrap = true,
                )
                display.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, softWrap = true)
                }
            }
        }
    }
}
