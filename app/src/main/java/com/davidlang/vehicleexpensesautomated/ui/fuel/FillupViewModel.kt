package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FillupViewModel @Inject constructor(
    private val repository: FuelEntryRepository
) : ViewModel() {

    private val _fuelEntries = MutableStateFlow<List<FuelEntry>>(emptyList())
    val fuelEntries: StateFlow<List<FuelEntry>> = _fuelEntries

    fun loadFuelEntries(vehicleId: Int) {
        viewModelScope.launch {
            repository.getEntriesForVehicle(vehicleId).collect { entries ->
                _fuelEntries.value = entries
            }
        }
    }

    suspend fun saveFuelEntry(entry: FuelEntry) {
        repository.saveEntry(entry)
    }
}
