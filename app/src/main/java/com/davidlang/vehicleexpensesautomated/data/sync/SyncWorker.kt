package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val vehicleRepository: VehicleRepository,
    private val googleSheetsClient: GoogleSheetsClient
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Full bidirectional sync as discussed
            val vehicles = vehicleRepository.getAllVehicles().first()
            googleSheetsClient.pushVehicles(vehicles)
            val pulled = googleSheetsClient.pullVehicles()
            pulled.forEach { vehicleRepository.insert(it) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
