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
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val fuelRepository: FuelEntryRepository
) {

    private val downloadsDir = context.getExternalFilesDir("Downloads")!!

    suspend fun exportToZip(): Uri = withContext(Dispatchers.IO) {
        val zipFile = File(downloadsDir, "vehicle_expenses_backup_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Vehicles (single tab)
            writeCsvToZip(zos, "Vehicles.csv") { getVehiclesCsv() }
            // Expenses (single tab)
            writeCsvToZip(zos, "Expenses.csv") { getExpensesCsv() }
            // Fuel — one tab per vehicle (exact match to Google Sheets)
            val fuelByVehicle = fuelRepository.getAllEntries().first().groupBy { it.vehicleId }
            fuelByVehicle.forEach { (vehicleId, entries) ->
                writeCsvToZip(zos, "Fuel - Vehicle $vehicleId.csv") { getFuelCsvForVehicle(entries) }
            }
        }
        Log.i("CsvManager", "✅ Exported ZIP with per-vehicle Fuel tabs")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private suspend fun getVehiclesCsv(): String {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val sb = StringBuilder("ID,Make,Model,Year,License Plate,VIN,Notes\n")
        vehicles.forEach { sb.append("${it.id},${it.make},${it.model},${it.year},${it.licensePlate},${it.vin ?: ""},${it.notes ?: ""}\n") }
        return sb.toString()
    }

    private suspend fun getExpensesCsv(): String {
        val expenses = expenseRepository.getAllEntries().first()
        val sb = StringBuilder("ID,Vehicle ID,Amount,Description,Date,Photo URL\n")
        expenses.forEach { sb.append("${it.id},${it.vehicleId},${it.amount},${it.description},${it.date},${it.photoUrl ?: ""}\n") }
        return sb.toString()
    }

    private fun getFuelCsvForVehicle(entries: List<FuelEntry>): String {
        val sb = StringBuilder("ID,Vehicle ID,Odometer,Gallons,Cost,Timestamp,Photo URL\n")
        entries.forEach { sb.append("${it.id},${it.vehicleId},${it.odometer},${it.gallons},${it.cost},${it.timestamp},${it.photoUrl ?: ""}\n") }
        return sb.toString()
    }

    private fun writeCsvToZip(zos: ZipOutputStream, filename: String, csvContent: () -> String) {
        zos.putNextEntry(ZipEntry(filename))
        zos.write(csvContent().toByteArray())
        zos.closeEntry()
    }

    suspend fun importFromZip(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "Vehicles.csv" -> importVehiclesCsv(zis)
                        entry.name == "Expenses.csv" -> importExpensesCsv(zis)
                        entry.name.startsWith("Fuel - Vehicle ") -> importFuelCsv(zis)
                    }
                    entry = zis.nextEntry
                }
            }
        }
        Log.i("CsvManager", "✅ Imported from CSV ZIP (including per-vehicle Fuel tabs)")
    }

    private suspend fun importVehiclesCsv(stream: InputStream) {
        val lines = stream.bufferedReader().readLines().drop(1)
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 7) {
                val v = Vehicle(
                    id = parts[0].toIntOrNull() ?: 0,
                    make = parts[1],
                    model = parts[2],
                    year = parts[3].toIntOrNull() ?: 0,
                    licensePlate = parts[4],
                    vin = parts[5].ifBlank { null },
                    notes = parts[6].ifBlank { null }
                )
                vehicleRepository.insert(v)
            }
        }
    }

    private suspend fun importExpensesCsv(stream: InputStream) {
        val lines = stream.bufferedReader().readLines().drop(1)
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 6) {
                val e = ExpenseEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    amount = parts[2].toDoubleOrNull() ?: 0.0,
                    description = parts[3],
                    date = parts[4].toLongOrNull() ?: 0,
                    photoUrl = parts[5].ifBlank { null }
                )
                expenseRepository.saveEntry(e)
            }
        }
    }

    private suspend fun importFuelCsv(stream: InputStream) {
        val lines = stream.bufferedReader().readLines().drop(1)
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 7) {
                val f = FuelEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    odometer = parts[2].toIntOrNull() ?: 0,
                    gallons = parts[3].toDoubleOrNull() ?: 0.0,
                    cost = parts[4].toDoubleOrNull() ?: 0.0,
                    timestamp = parts[5].toLongOrNull() ?: 0,
                    photoUrl = parts[6].ifBlank { null }
                )
                fuelRepository.saveEntry(f)
            }
        }
    }
}
