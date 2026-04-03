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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        odometerCropRect: androidx.compose.ui.geometry.Rect?,
        initialOdometer: Int
    ) {
        Log.i("VehicleReferenceCleaning", "createNewVehicleWithReference called for $name with original $referenceDashPhotoUrl")
        var cleanedUrl: String? = null
        try {
            cleanedUrl = referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Cleaning failed, falling back to original photo", e)
            cleanedUrl = referenceDashPhotoUrl
        }

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

        try {
            withContext(NonCancellable + Dispatchers.IO) {
                repository.insert(newVehicle)
            }
            Log.i("VehicleReferenceCleaning", "Vehicle inserted successfully")
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Insert failed", e)
            throw e
        }
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        Log.i("VehicleReferenceCleaning", "updateVehicle called for ${vehicle.id}")
        var cleanedUrl: String? = vehicle.cleanedReferenceDashPhotoUrl
        try {
            cleanedUrl = vehicle.referenceDashPhotoUrl?.let { createAndSaveCleanedReference(it) }
                ?: vehicle.cleanedReferenceDashPhotoUrl
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Cleaning failed, keeping original", e)
        }

        val updated = vehicle.copy(
            referenceDashPhotoUrl = null,
            cleanedReferenceDashPhotoUrl = cleanedUrl
        )

        try {
            withContext(NonCancellable + Dispatchers.IO) {
                repository.updateVehicle(updated)
            }
            Log.i("VehicleReferenceCleaning", "Vehicle updated successfully")
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Update failed", e)
            throw e
        }
    }

    suspend fun ensureCleanedReference(vehicle: Vehicle): String? {
        val cleaned = vehicle.cleanedReferenceDashPhotoUrl
        if (cleaned != null) {
            val f = File(cleaned)
            if (f.exists()) {
                Log.i("VehicleReferenceCleaning", "Using existing cleaned reference for vehicle ${vehicle.id}: $cleaned")
                return cleaned
            }
        }
        val originalUrl = vehicle.referenceDashPhotoUrl ?: return null
        return createAndSaveCleanedReference(originalUrl)
    }

    fun loadDiagnosticGrid(url: String) {
        viewModelScope.launch {
            Log.i("CropDebug", "loadDiagnosticGrid called for: $url")
            try {
                val file = File(url)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        // Exp 1 - 6 aggressive cleaned images
                        val cleaned = ImageAlignmentUtils.createExperiment1Cleaned(bmp)
                        _exp1Cleaned.value = cleaned

                        // Exp 2 - 6 radial masks
                        val radial = ImageAlignmentUtils.createExperiment2RadialVariants(bmp)
                        _exp2Radial.value = radial

                        // Exp 3 - 6 polar masks
                        val polar = ImageAlignmentUtils.createExperiment3PolarVariants(bmp)
                        _exp3Polar.value = polar

                        // Exp 4 - text-only + masked-original
                        val ocrResult = com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils.extractFromPhoto(url)
                        val textVariants = ImageAlignmentUtils.createExperiment4TextOnly(bmp, ocrResult.textBlocks)
                        _exp4TextOnly.value = textVariants

                        // Exp 5 - 6 line segment masks
                        val lines = ImageAlignmentUtils.createExperiment5LineSegments(bmp)
                        _exp5LineSegments.value = lines

                        Log.i("CropDebug", "All 5 experiment grids loaded (6 images each)")
                    } else {
                        Log.e("CropDebug", "Failed to decode bitmap from $url")
                    }
                } else {
                    Log.e("CropDebug", "File does not exist: $url")
                }
            } catch (e: Exception) {
                Log.e("CropDebug", "loadDiagnosticGrid FAILED", e)
                e.printStackTrace()
            }
        }
    }

    private suspend fun createAndSaveCleanedReference(originalUrl: String): String? {
        val originalFile = File(originalUrl)
        if (!originalFile.exists()) {
            Log.e("VehicleReferenceCleaning", "Original file does not exist: $originalUrl")
            return null
        }
        Log.i("VehicleReferenceCleaning", "Starting fast cleaning for: $originalUrl")

        val originalBmp = BitmapFactory.decodeFile(originalFile.absolutePath)
        if (originalBmp == null) {
            Log.e("VehicleReferenceCleaning", "Failed to decode original bitmap")
            return null
        }

        val cleanedBmp = ImageAlignmentUtils.createCleanedReference(originalBmp)
        if (cleanedBmp == null) {
            Log.e("VehicleReferenceCleaning", "Fast cleaning returned null")
            originalBmp.recycle()
            return null
        }

        val cleanedFile = File(originalFile.parent, "cleaned_${originalFile.name}")
        return try {
            val out = java.io.FileOutputStream(cleanedFile)
            cleanedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            Log.i("VehicleReferenceCleaning", "Saved cleaned reference: ${cleanedFile.absolutePath}")
            cleanedFile.absolutePath
        } catch (e: Exception) {
            Log.e("VehicleReferenceCleaning", "Failed to write cleaned file", e)
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
