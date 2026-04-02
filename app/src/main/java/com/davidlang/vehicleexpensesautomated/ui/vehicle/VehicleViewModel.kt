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
            Log.i("VehicleReferenceCleaning", "createNewVehicleWithReference called for $name with original $referenceDashPhotoUrl")
            val cleanedUrl = referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
            val newVehicle = Vehicle(
                name = name,
                make = make,
                model = model,
                year = year,
                licensePlate = licensePlate,
                referenceDashPhotoUrl = null,
                cleanedReferenceDashPhotoUrl = cleanedUrl,
                odometerCropLeft = odometerCropRect?.left,
                odometerCropTop = odometerCropRect?.top,
                odometerCropRight = odometerCropRect?.right,
                odometerCropBottom = odometerCropRect?.bottom
            )
            repository.insert(newVehicle)
            Log.i("VehicleReferenceCleaning", "✅ Vehicle inserted with cleaned photo: $cleanedUrl")
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            Log.i("VehicleReferenceCleaning", "updateVehicle called for ${vehicle.id}")
            val cleanedUrl = vehicle.referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
                ?: vehicle.cleanedReferenceDashPhotoUrl
            val updated = vehicle.copy(
                referenceDashPhotoUrl = null,
                cleanedReferenceDashPhotoUrl = cleanedUrl
            )
            repository.updateVehicle(updated)
            Log.i("VehicleReferenceCleaning", "✅ Vehicle updated with cleaned photo: $cleanedUrl")
        }
    }

    suspend fun ensureCleanedReference(vehicle: Vehicle): String? {
        val cleaned = vehicle.cleanedReferenceDashPhotoUrl
        if (cleaned != null) {
            val f = File(cleaned)
            if (f.exists()) {
                Log.i("VehicleReferenceCleaning", "✅ Using existing cleaned reference for vehicle ${vehicle.id}: $cleaned")
                return cleaned
            }
        }
        val originalUrl = vehicle.referenceDashPhotoUrl ?: return null
        return createAndSaveCleanedReference(originalUrl)
    }

    private suspend fun createAndSaveCleanedReference(originalUrl: String): String? {
        val originalFile = File(originalUrl)
        if (!originalFile.exists()) {
            Log.e("VehicleReferenceCleaning", "❌ Original file does not exist: $originalUrl")
            return null
        }
        Log.i("VehicleReferenceCleaning", "🔧 Starting cleaning for: $originalUrl")

        val originalBmp = BitmapFactory.decodeFile(originalFile.absolutePath)
        if (originalBmp == null) {
            Log.e("VehicleReferenceCleaning", "❌ Failed to decode original bitmap")
            return null
        }
        Log.i("VehicleReferenceCleaning", "✅ Decoded original bitmap (${originalBmp.width}x${originalBmp.height})")

        val cleanedBmp = ImageAlignmentUtils.createCleanedReference(originalBmp)
        if (cleanedBmp == null) {
            Log.e("VehicleReferenceCleaning", "❌ ImageAlignmentUtils.createCleanedReference returned null")
            originalBmp.recycle()
            return null
        }
        Log.i("VehicleReferenceCleaning", "✅ OpenCV cleaning succeeded")

        val cleanedFile = File(originalFile.parent, "cleaned_${originalFile.name}")
        return try {
            val out = java.io.FileOutputStream(cleanedFile)
            cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            Log.i("VehicleReferenceCleaning", "✅ Saved cleaned reference: ${cleanedFile.absolutePath}")
            cleanedFile.absolutePath
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "❌ Failed to write cleaned file", e)
            null
        } finally {
            originalBmp.recycle()
            cleanedBmp.recycle()
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}
