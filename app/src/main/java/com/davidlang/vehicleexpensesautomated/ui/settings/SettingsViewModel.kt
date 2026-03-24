package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CsvManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val csvManager: CsvManager,
    val photoStorageManager: PhotoStorageManager
) : ViewModel()
