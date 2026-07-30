package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
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
) : ViewModel() {

    val fuelEntries: StateFlow<List<FuelEntry>> = fuelEntryRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveFuel(entry: FuelEntry) {
        viewModelScope.launch {
            fuelEntryRepository.insertFuelEntry(entry)
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
