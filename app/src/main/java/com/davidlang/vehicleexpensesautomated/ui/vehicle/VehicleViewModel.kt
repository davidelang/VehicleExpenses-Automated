package com.davidlang.vehicleexpensesautomated.ui.vehicle

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
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
    private val repository: VehicleRepository,
    private val photoBackupCoordinator: PhotoBackupCoordinator,
) : ViewModel() {

    val vehicles = repository.getAllVehicles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
        imgH: Int = 0,
        odometerDigitCount: Int = 6,
        odometerRolloverCount: Int = 0,
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
            landmarkTextBlocksJson = landmarkTextBlocksJson,
            odometerDigitCount = odometerDigitCount.coerceIn(3, 9),
            odometerRolloverCount = odometerRolloverCount.coerceAtLeast(0),
        )

        try {
            withContext(NonCancellable + Dispatchers.IO) {
                repository.insert(newVehicle)
                photoBackupCoordinator.enqueueAfterSave()
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
                photoBackupCoordinator.enqueueAfterSave()
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
        val odo = if (vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null &&
                     vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null) {
            androidx.compose.ui.geometry.Rect(
                vehicle.odometerCropLeft, vehicle.odometerCropTop,
                vehicle.odometerCropRight, vehicle.odometerCropBottom
            )
        } else null
        val other = if (vehicle.otherTextCropLeft != null && vehicle.otherTextCropTop != null &&
                        vehicle.otherTextCropRight != null && vehicle.otherTextCropBottom != null) {
            androidx.compose.ui.geometry.Rect(
                vehicle.otherTextCropLeft, vehicle.otherTextCropTop,
                vehicle.otherTextCropRight, vehicle.otherTextCropBottom
            )
        } else null
        return Pair(odo, other)
    }

    suspend fun ensureVehicleAssetsDownloaded(vehicleId: Int): Boolean =
        photoBackupCoordinator.downloadVehicleIfNeeded(vehicleId)

    suspend fun ensureCleanedReference(vehicle: Vehicle): String? {
        val cleaned = vehicle.cleanedReferenceDashPhotoUrl
        if (cleaned != null) {
            val f = java.io.File(cleaned)
            if (f.exists()) return cleaned
        }
        return null
    }

    // Phase 18: soft-delete tombstone for sync
    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.markVehicleDeleted(vehicle)
        }
    }
}
