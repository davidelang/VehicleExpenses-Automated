package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.util.ImageAlignmentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    val vehicles = repository.getAllVehicles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    suspend fun getVehicleById(id: Int): Vehicle? = repository.getVehicleById(id)

    fun createNewVehicleWithReference(
        name: String,
        make: String,
        model: String,
        year: Int?,
        licensePlate: String?,
        referenceDashPhotoUrl: String?,
        odometerCropRect: androidx.compose.ui.geometry.Rect?,
        initialOdometer: Int
    ) {
        viewModelScope.launch {
            val cleanedUrl = referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
            val newVehicle = Vehicle(
                name = name,
                make = make,
                model = model,
                year = year,
                licensePlate = licensePlate,
                referenceDashPhotoUrl = referenceDashPhotoUrl,
                cleanedReferenceDashPhotoUrl = cleanedUrl,
                odometerCropLeft = odometerCropRect?.left,
                odometerCropTop = odometerCropRect?.top,
                odometerCropRight = odometerCropRect?.right,
                odometerCropBottom = odometerCropRect?.bottom
            )
            repository.insert(newVehicle)
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            val cleanedUrl = vehicle.referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
            val updated = vehicle.copy(cleanedReferenceDashPhotoUrl = cleanedUrl)
            repository.updateVehicle(updated)
        }
    }

    /** Guarantees a cleaned (tick-free) reference exists */
    suspend fun ensureCleanedReference(vehicle: Vehicle): String? {
        val cleaned = vehicle.cleanedReferenceDashPhotoUrl
        if (cleaned != null) {
            val f = File(cleaned)
            if (f.exists()) return cleaned
        }
        val originalUrl = vehicle.referenceDashPhotoUrl ?: return null
        return createAndSaveCleanedReference(originalUrl)
    }

    private suspend fun createAndSaveCleanedReference(originalUrl: String): String? {
        val originalFile = File(originalUrl)
        if (!originalFile.exists()) return null
        val originalBmp = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return null
        val cleanedBmp = ImageAlignmentUtils.createCleanedReference(originalBmp) ?: return null
        val cleanedFile = File(originalFile.parent, "cleaned_${originalFile.name}")
        val out = java.io.FileOutputStream(cleanedFile)
        cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()
        Log.i("VehicleReferenceCleaning", "✅ Saved cleaned reference: ${cleanedFile.absolutePath}")
        return cleanedFile.absolutePath
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}
