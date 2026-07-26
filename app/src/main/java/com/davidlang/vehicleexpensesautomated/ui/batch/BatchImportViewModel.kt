package com.davidlang.vehicleexpensesautomated.ui.batch

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BatchImportViewModel @Inject constructor(
    val coordinator: BatchFuelImportCoordinator,
) : ViewModel()
