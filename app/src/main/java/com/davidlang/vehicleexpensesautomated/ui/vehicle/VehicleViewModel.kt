package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _exp1Cleaned = MutableStateFlow<List<Bitmap>>(emptyList())
    val exp1Cleaned: StateFlow<List<Bitmap>> = _exp1Cleaned
    private val _exp2Radial = MutableStateFlow<List<Bitmap>>(emptyList())
    val exp2Radial: StateFlow<List<Bitmap>> = _exp2Radial
    private val _exp3Polar = MutableStateFlow<List<Bitmap>>(emptyList())
    val exp3Polar: StateFlow<List<Bitmap>> = _exp3Polar
    private val _exp4TextOnly = MutableStateFlow<List<Bitmap>>(emptyList())
    val exp4TextOnly: StateFlow<List<Bitmap>> = _exp4TextOnly
    private val _exp5LineSegments = MutableStateFlow<List<Bitmap>>(emptyList())
    val exp5LineSegments: StateFlow<List<Bitmap>> = _exp5LineSegments

    suspend fun getVehicleById(id: Int): Vehicle? = repository.getVehicleById(id)

    suspend fun createNewVehicleWithReference(
        name: String,
        make: String,
        model: String,
        year: Int?,
        licensePlate: String?,
        referenceDashPhotoUrl: String?,
        cleanedReferenceDashPhotoUrl: String?,
        odometerCropRect: androidx.compose.ui.geometry.Rect?,
        otherTextCropRect: androidx.compose.ui.geometry.Rect?,
        initialOdometer: Int,
        landmarkTextBlocksJson: String? = null,
        imgW: Int = 0,
        imgH: Int = 0
    ) {
        val newVehicle = Vehicle(
            name = name,
            make = make,
            model = model,
            year = year,
            licensePlate = licensePlate,
            referenceDashPhotoUrl = referenceDashPhotoUrl,
            cleanedReferenceDashPhotoUrl = cleanedReferenceDashPhotoUrl,
            odometerCropLeft = odometerCropRect?.left,
            odometerCropTop = odometerCropRect?.top,
            odometerCropRight = odometerCropRect?.right,
            odometerCropBottom = odometerCropRect?.bottom,
            otherTextCropLeft = otherTextCropRect?.left,
            otherTextCropTop = otherTextCropRect?.top,
            otherTextCropRight = otherTextCropRect?.right,
            otherTextCropBottom = otherTextCropRect?.bottom,
            landmarkTextBlocksJson = landmarkTextBlocksJson
        )

        try {
            withContext(NonCancellable + Dispatchers.IO) {
                repository.insert(newVehicle)
            }
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Insert failed", e)
            throw e
        }
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        Log.i("VehicleReferenceCleaning", "updateVehicle called for ${vehicle.id}")

        try {
            withContext(NonCancellable + Dispatchers.IO) {
                repository.updateVehicle(vehicle)
            }
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Update failed", e)
            throw e
        }
    }

    /**
     * Get the crop rectangles for a vehicle.
     */
    fun getCrops(vehicle: Vehicle): Pair<androidx.compose.ui.geometry.Rect?, androidx.compose.ui.geometry.Rect?> {
        val odo = vehicle.odometerCropLeft?.let { l ->
            androidx.compose.ui.geometry.Rect(l, vehicle.odometerCropTop ?: 0f, vehicle.odometerCropRight ?: 1f, vehicle.odometerCropBottom ?: 1f)
        }
        val other = vehicle.otherTextCropLeft?.let { l ->
            androidx.compose.ui.geometry.Rect(l, vehicle.otherTextCropTop ?: 0f, vehicle.otherTextCropRight ?: 1f, vehicle.otherTextCropBottom ?: 1f)
        }
        return Pair(odo, other)
    }

    suspend fun ensureCleanedReference(vehicle: Vehicle): String? {
        val cleaned = vehicle.cleanedReferenceDashPhotoUrl
        if (cleaned != null) {
            val f = java.io.File(cleaned)
            if (f.exists()) return cleaned
        }
        return null
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}
