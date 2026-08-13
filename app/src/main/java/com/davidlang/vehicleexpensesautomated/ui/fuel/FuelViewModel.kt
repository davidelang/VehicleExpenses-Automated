package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.location.KnownStationStore
import com.davidlang.vehicleexpensesautomated.data.location.StationMatch
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val fuelEntryRepository: FuelEntryRepository,
    private val photoBackupCoordinator: PhotoBackupCoordinator,
    val knownStationStore: KnownStationStore,
) : ViewModel() {

    val fuelEntries: StateFlow<List<FuelEntry>> = fuelEntryRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun matchKnownStation(lat: Double, lon: Double): StationMatch =
        knownStationStore.matchNearest(lat, lon)

    suspend fun upsertKnownStation(
        name: String,
        address: String,
        lat: Double,
        lon: Double,
        accuracyM: Double? = null,
        source: String = KnownStation.SOURCE_USER,
    ) {
        knownStationStore.upsertFromConfirm(
            name = name,
            address = address,
            lat = lat,
            lon = lon,
            accuracyM = accuracyM,
            source = source,
        )
    }

    fun saveFuel(entry: FuelEntry) {
        viewModelScope.launch {
            fuelEntryRepository.insertFuelEntry(entry)
            val blob = FuelLocationJson.parseBlob(entry.location)
            val slat = blob?.lat
            val slon = blob?.lon
            if (blob != null && blob.hasPlace() && slat != null && slon != null) {
                knownStationStore.upsertFromConfirm(
                    name = blob.name,
                    address = blob.address,
                    lat = slat,
                    lon = slon,
                    accuracyM = blob.accuracyM,
                    source = KnownStation.SOURCE_USER,
                )
            }
            photoBackupCoordinator.enqueueAfterSave()
        }
    }

    fun updateFuel(entry: FuelEntry) {
        viewModelScope.launch {
            fuelEntryRepository.updateFuelEntry(entry)
            photoBackupCoordinator.enqueueAfterSave()
        }
    }

    suspend fun getFuelById(id: Long): FuelEntry? = fuelEntryRepository.getById(id)

    suspend fun downloadFuelPhoto(entry: FuelEntry): String? =
        photoBackupCoordinator.downloadFuelPhoto(entry)

    suspend fun scrubUnreadableFuelPhotos(entry: FuelEntry): FuelEntry =
        photoBackupCoordinator.scrubUnreadableFuelPhotos(entry)

    fun convertAllVolumes(fromUnit: String, toUnit: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            fuelEntryRepository.convertAllVolumes(fromUnit, toUnit)
            onComplete()
        }
    }

    fun deleteFuelEntry(entry: FuelEntry) {
        viewModelScope.launch {
            fuelEntryRepository.markFuelDeleted(entry)
        }
    }
}
