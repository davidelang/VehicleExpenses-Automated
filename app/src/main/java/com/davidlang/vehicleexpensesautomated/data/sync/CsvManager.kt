package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val fuelRepository: FuelEntryRepository
) {

    private val downloadsDir = context.getExternalFilesDir("Downloads")!!

    suspend fun exportToZip(): Uri = withContext(Dispatchers.IO) {
        val vehiclesCsv = getVehiclesCsv()
        val allExpenses = expenseRepository.getAllEntries().first()
        val allFuel = fuelRepository.getAllEntries().first()

        val zipFile = File(downloadsDir, "vehicle_expenses_backup_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            writeCsvToZip(zos, "Vehicles.csv", vehiclesCsv)

            allExpenses.groupBy { it.vehicleId }.forEach { (vehicleId, entries) ->
                val csv = getExpensesCsvForVehicle(entries)
                writeCsvToZip(zos, "Expenses - Vehicle $vehicleId.csv", csv)
            }

            allFuel.groupBy { it.vehicleId }.forEach { (vehicleId, entries) ->
                val csv = getFuelCsvForVehicle(entries)
                writeCsvToZip(zos, "Fuel - Vehicle $vehicleId.csv", csv)
            }
        }

        Log.i("CsvManager", "Exported ZIP with exact Google Sheets structure")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private suspend fun getVehiclesCsv(): String {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val sb = StringBuilder("ID,Make,Model,Year,License Plate,VIN,Notes\n")
        vehicles.forEach {
            sb.append("${it.id},${it.make},${it.model},${it.year},${it.licensePlate},${it.vin ?: ""},${it.notes ?: ""}\n")
        }
        return sb.toString()
    }

    private fun getExpensesCsvForVehicle(entries: List<ExpenseEntry>): String {
        val sb = StringBuilder("ID,Vehicle ID,Date,Amount,Category,Description,Receipt Image Path\n")
        entries.forEach {
            sb.append("${it.id},${it.vehicleId},${it.date},${it.amount},${it.category},${it.description},${it.receiptImagePath ?: ""}\n")
        }
        return sb.toString()
    }

    private fun getFuelCsvForVehicle(entries: List<FuelEntry>): String {
        val sb = StringBuilder("ID,Vehicle ID,Date,Amount,Gallons,Price Per Gallon,Total Cost,Station,Notes,Partial Fill\n")
        entries.forEach {
            sb.append("${it.id},${it.vehicleId},${it.timestamp},${it.gallons},${it.cost},${it.isPartialFill}\n")
        }
        return sb.toString()
    }

    private fun writeCsvToZip(zos: ZipOutputStream, fileName: String, csvContent: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(csvContent.toByteArray())
        zos.closeEntry()
    }
}
