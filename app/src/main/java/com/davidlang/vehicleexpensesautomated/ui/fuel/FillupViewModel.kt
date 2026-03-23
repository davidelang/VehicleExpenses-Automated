package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.repository.FuelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FillupViewModel @Inject constructor(val repository: FuelRepository) : ViewModel() {
    fun getFillups(vehicleId: Int) = repository.getFillupsForVehicle(vehicleId)
}
