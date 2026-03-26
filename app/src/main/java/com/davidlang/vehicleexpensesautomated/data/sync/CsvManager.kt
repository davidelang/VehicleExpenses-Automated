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
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        val fuelCsv = getFuelCsv()
        val expenseCsv = getExpenseCsv()

        val zipFile = File(downloadsDir, "vehicle_expenses_backup_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            writeCsvToZip(zos, "Vehicles.csv", vehiclesCsv)
            writeCsvToZip(zos, "Fuel_entries.csv", fuelCsv)
            writeCsvToZip(zos, "Expense_entries.csv", expenseCsv)
        }

        Log.i("CsvManager", "Exported ZIP with exact Google-Sheet-style flat CSVs (one per tab)")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private suspend fun getVehiclesCsv(): String {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val sb = StringBuilder("ID,Name,Make,Model,Year,License Plate,VIN,Notes\n")
        vehicles.forEach {
            sb.append("${it.id},${it.name},${it.make ?: ""},${it.model ?: ""},${it.year},${it.licensePlate},${it.vin ?: ""},${it.notes ?: ""}\n")
        }
        return sb.toString()
    }

    private suspend fun getFuelCsv(): String {
        val fuel = fuelRepository.getAllEntries().first()
        val sb = StringBuilder("ID,Vehicle ID,Odometer,Gallons,Cost,Timestamp,Photo URL,Partial Fill\n")
        fuel.forEach {
            sb.append("${it.id},${it.vehicleId},${it.odometer},${it.gallons},${it.cost},${it.timestamp},${it.photoUrl ?: ""},${it.isPartialFill}\n")
        }
        return sb.toString()
    }

    private suspend fun getExpenseCsv(): String {
        val expenses = expenseRepository.getAllEntries().first()
        val sb = StringBuilder("ID,Vehicle ID,Date,Amount,Category,Description,Receipt Image Path\n")
        expenses.forEach {
            sb.append("${it.id},${it.vehicleId},${it.date},${it.amount},${it.category},${it.description},${it.receiptImagePath ?: ""}\n")
        }
        return sb.toString()
    }

    private fun writeCsvToZip(zos: ZipOutputStream, fileName: String, csvContent: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(csvContent.toByteArray())
        zos.closeEntry()
    }

    suspend fun importFromZip(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val fileName = entry!!.name
                    val content = zis.readBytes().decodeToString()

                    when (fileName) {
                        "Vehicles.csv" -> importVehiclesCsv(content)
                        "Fuel_entries.csv" -> importFuelCsv(content)
                        "Expense_entries.csv" -> importExpenseCsv(content)
                    }
                }
            }
        }
        Log.i("CsvManager", "Import from ZIP complete (exact Google-Sheet-style format)")
    }

    private suspend fun importVehiclesCsv(csv: String) {
        val lines = csv.lines().drop(1).filter { it.isNotBlank() }
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 8) {
                val vehicle = Vehicle(
                    id = parts[0].toIntOrNull() ?: 0,
                    name = parts[1],
                    make = parts[2],
                    model = parts[3],
                    year = parts[4].toIntOrNull() ?: 0,
                    licensePlate = parts[5],
                    vin = parts[6].ifBlank { null },
                    notes = parts[7].ifBlank { null }
                )
                vehicleRepository.insert(vehicle)
            }
        }
    }

    private suspend fun importFuelCsv(csv: String) {
        val lines = csv.lines().drop(1).filter { it.isNotBlank() }
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 8) {
                val fuel = FuelEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    odometer = parts[2].toIntOrNull() ?: 0,
                    gallons = parts[3].toDoubleOrNull() ?: 0.0,
                    cost = parts[4].toDoubleOrNull() ?: 0.0,
                    timestamp = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                    photoUrl = parts[6].ifBlank { null },
                    isPartialFill = parts[7].toBoolean()
                )
                fuelRepository.insertFuelEntry(fuel)
            }
        }
    }

    private suspend fun importExpenseCsv(csv: String) {
        val lines = csv.lines().drop(1).filter { it.isNotBlank() }
        lines.forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 7) {
                val expense = ExpenseEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    amount = parts[3].toDoubleOrNull() ?: 0.0,
                    description = parts[5],
                    date = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                    category = parts[4],
                    receiptImagePath = parts[6].ifBlank { null }
                )
                expenseRepository.insertExpenseEntry(expense)
            }
        }
    }
}
