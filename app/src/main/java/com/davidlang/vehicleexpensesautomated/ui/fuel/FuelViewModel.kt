package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val fuelEntryRepository: FuelEntryRepository
) : ViewModel() {

    fun saveFuel(entry: FuelEntry) {
        viewModelScope.launch {
            fuelEntryRepository.insertFuelEntry(entry)
        }
    }
}
