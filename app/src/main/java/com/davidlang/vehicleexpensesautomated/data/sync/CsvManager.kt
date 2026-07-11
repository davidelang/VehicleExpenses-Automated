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

    /** Quote field if it contains comma, quote, or newline (RFC-style). */
    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /** Split a CSV line honoring double-quoted fields. */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                }
                !inQuotes && c == '"' -> {
                    inQuotes = true
                    i++
                    continue
                }
                !inQuotes && c == ',' -> {
                    result.add(cur.toString())
                    cur.clear()
                    i++
                    continue
                }
                else -> {
                    cur.append(c)
                    i++
                }
            }
        }
        result.add(cur.toString())
        return result
    }

    private fun csvRow(vararg fields: Any?): String {
        return fields.joinToString(",") { f ->
            when (f) {
                null -> ""
                is String -> csvEscape(f)
                else -> f.toString()
            }
        } + "\n"
    }

    private suspend fun getVehiclesCsv(): String {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val sb = StringBuilder("ID,Name,Make,Model,Year,License Plate,VIN,Notes\n")
        vehicles.forEach {
            sb.append(
                csvRow(
                    it.id,
                    it.name,
                    it.make ?: "",
                    it.model ?: "",
                    it.year,
                    it.licensePlate ?: "",
                    it.vin ?: "",
                    it.notes ?: ""
                )
            )
        }
        return sb.toString()
    }

    private suspend fun getFuelCsv(): String {
        val fuel = fuelRepository.getAllEntries().first()
        val sb = StringBuilder(
            "ID,Vehicle ID,Odometer,Gallons,Cost,Timestamp,Photo URL,Partial Fill,Latitude,Longitude,Location,Cloud Manifest\n"
        )
        fuel.forEach {
            sb.append(
                csvRow(
                    it.id,
                    it.vehicleId,
                    it.odometer,
                    it.gallons,
                    it.cost,
                    it.timestamp,
                    it.photoUrl ?: "",
                    it.isPartialFill,
                    it.latitude ?: "",
                    it.longitude ?: "",
                    it.location ?: "",
                    it.cloudManifest ?: ""
                )
            )
        }
        return sb.toString()
    }

    private suspend fun getExpenseCsv(): String {
        val expenses = expenseRepository.getAllEntries().first()
        val sb = StringBuilder(
            "ID,Vehicle ID,Date,Amount,Category,Description,Vendor,Odometer,Photo URL,Receipt Image Path,Latitude,Longitude,Location,Cloud Manifest\n"
        )
        expenses.forEach {
            sb.append(
                csvRow(
                    it.id,
                    it.vehicleId,
                    it.date,
                    it.amount,
                    it.category,
                    it.description,
                    it.vendor,
                    it.odometer ?: "",
                    it.photoUrl ?: "",
                    it.receiptImagePath ?: "",
                    it.latitude ?: "",
                    it.longitude ?: "",
                    it.location ?: "",
                    it.cloudManifest ?: ""
                )
            )
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
            val parts = parseCsvLine(line)
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
            val parts = parseCsvLine(line)
            if (parts.size >= 11) {
                val fuel = FuelEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    odometer = parts[2].toIntOrNull() ?: 0,
                    gallons = parts[3].toDoubleOrNull() ?: 0.0,
                    cost = parts[4].toDoubleOrNull() ?: 0.0,
                    timestamp = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                    photoUrl = parts[6].ifBlank { null },
                    isPartialFill = parts[7].toBoolean(),
                    latitude = parts[8].toDoubleOrNull(),
                    longitude = parts[9].toDoubleOrNull(),
                    location = parts[10].ifBlank { null },
                    cloudManifest = if (parts.size > 11) parts[11].ifBlank { null } else null
                )
                fuelRepository.insertFuelEntry(fuel)
            }
        }
    }

    private suspend fun importExpenseCsv(csv: String) {
        val lines = csv.lines().drop(1).filter { it.isNotBlank() }
        lines.forEach { line ->
            val parts = parseCsvLine(line)
            // New export: 14 cols with Vendor, Odometer, Photo URL after Description
            // Legacy: ID,Vehicle,Date,Amount,Category,Description,Receipt,Lat,Long,Loc,Cloud (>=10)
            if (parts.size >= 14) {
                val expense = ExpenseEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    date = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                    amount = parts[3].toDoubleOrNull() ?: 0.0,
                    category = parts[4],
                    description = parts[5],
                    vendor = parts[6],
                    odometer = parts[7].toIntOrNull(),
                    photoUrl = parts[8].ifBlank { null },
                    receiptImagePath = parts[9].ifBlank { null },
                    latitude = parts[10].toDoubleOrNull(),
                    longitude = parts[11].toDoubleOrNull(),
                    location = parts[12].ifBlank { null },
                    cloudManifest = parts[13].ifBlank { null }
                )
                expenseRepository.insertExpenseEntry(expense)
            } else if (parts.size >= 10) {
                val expense = ExpenseEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    date = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                    amount = parts[3].toDoubleOrNull() ?: 0.0,
                    category = parts[4],
                    description = parts[5],
                    receiptImagePath = parts[6].ifBlank { null },
                    latitude = parts[7].toDoubleOrNull(),
                    longitude = parts[8].toDoubleOrNull(),
                    location = parts[9].ifBlank { null },
                    cloudManifest = if (parts.size > 10) parts[10].ifBlank { null } else null
                )
                expenseRepository.insertExpenseEntry(expense)
            }
        }
    }
}
