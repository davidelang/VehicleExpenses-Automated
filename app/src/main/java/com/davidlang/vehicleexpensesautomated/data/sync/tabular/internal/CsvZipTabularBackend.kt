package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.CsvZipSource
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.CsvZipTarget
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularExportRequest
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularExportResult
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularImportRequest
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularImportResult
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Hidden CSV zip adapter — same schema as remote sync; not a user-visible destination. */
@Singleton
class CsvZipTabularBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val fuelRepository: FuelEntryRepository,
) {

    suspend fun exportZip(target: CsvZipTarget, request: TabularExportRequest): TabularExportResult =
        withContext(Dispatchers.IO) {
            val vehiclesCsv = buildVehiclesCsv()
            val perVehicleFuelCsvs = buildPerVehicleFuelCsvs()
            val expenseCsv = buildExpenseCsv()

            val zipFile = File(target.outputDir, "vehicle_expenses_backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                writeCsvToZip(zos, "Vehicles.csv", vehiclesCsv)
                perVehicleFuelCsvs.forEach { (fileName, csv) ->
                    writeCsvToZip(zos, fileName, csv)
                }
                writeCsvToZip(zos, "Expenses.csv", expenseCsv)
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            TabularExportResult(uri)
        }

    suspend fun importZip(source: CsvZipSource, request: TabularImportRequest): TabularImportResult =
        withContext(Dispatchers.IO) {
            val zipEntries = mutableListOf<Pair<String, String>>()
            context.contentResolver.openInputStream(source.uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        val fileName = zipEntryBaseName(entry!!.name)
                        val content = zis.readBytes().decodeToString()
                        zipEntries.add(fileName to content)
                    }
                }
            }

            zipEntries.filter { it.first == "Vehicles.csv" }.forEach { (_, content) ->
                importVehiclesCsv(content)
            }

            val vehicleIdByFuelTabName = vehicleRepository.getAllIncludingDeleted()
                .filter { !it.deleted }
                .associate { TabularSchema.fuelTabName(it.name) to it.id }

            zipEntries.forEach { (fileName, content) ->
                when {
                    fileName == "Vehicles.csv" -> Unit
                    fileName == "Expenses.csv" || fileName == "Expense_entries.csv" ->
                        importExpenseCsv(content)
                    fileName == "Fuel_entries.csv" ->
                        importFuelCsv(content)
                    fileName.startsWith(TabularSchema.FUEL_TAB_PREFIX) && fileName.endsWith(".csv") -> {
                        val tabName = fileName.removeSuffix(".csv")
                        importFuelCsv(content, fuelTabVehicleId = vehicleIdByFuelTabName[tabName])
                    }
                }
            }

            TabularImportResult(success = true)
        }

    private suspend fun buildVehiclesCsv(): String {
        val vehicles = vehicleRepository.getAllIncludingDeleted()
            .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
        val sb = StringBuilder(TabularSchema.VEHICLE_HEADERS.joinToString(",") + "\n")
        vehicles.forEach {
            sb.append(csvRow(*TabularSchema.vehicleToRow(it).toTypedArray()))
        }
        return sb.toString()
    }

    private suspend fun vehicleSyncIdById(): Map<Int, String> =
        vehicleRepository.getAllIncludingDeleted().associate { it.id to it.syncId }

    private suspend fun buildPerVehicleFuelCsvs(): List<Pair<String, String>> {
        val vehicles = vehicleRepository.getAllIncludingDeleted().filter { !it.deleted }
        val fuelByVehicleId = fuelRepository.getAllIncludingDeleted().groupBy { it.vehicleId }
        return vehicles.map { vehicle ->
            val fileName = "${TabularSchema.fuelTabName(vehicle.name)}.csv"
            val sb = StringBuilder(TabularSchema.FUEL_HEADERS.joinToString(",") + "\n")
            fuelByVehicleId[vehicle.id].orEmpty()
                .sortedWith(compareBy({ it.timestamp }, { it.syncId }))
                .forEach { entry ->
                    sb.append(csvRow(*TabularSchema.fuelToRow(entry, vehicle.syncId).toTypedArray()))
                }
            fileName to sb.toString()
        }
    }

    private suspend fun buildExpenseCsv(): String {
        val vehicleSyncIds = vehicleSyncIdById()
        val expenses = expenseRepository.getAllIncludingDeleted()
            .sortedWith(compareBy({ it.date }, { it.syncId }))
        val sb = StringBuilder(TabularSchema.EXPENSE_HEADERS.joinToString(",") + "\n")
        expenses.forEach {
            sb.append(
                csvRow(
                    *TabularSchema.expenseToRow(it, vehicleSyncIds[it.vehicleId].orEmpty()).toTypedArray(),
                ),
            )
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

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

    private fun csvRow(vararg fields: Any?): String =
        fields.joinToString(",") { f ->
            when (f) {
                null -> ""
                is String -> csvEscape(f)
                else -> f.toString()
            }
        } + "\n"

    private fun writeCsvToZip(zos: ZipOutputStream, fileName: String, csvContent: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(csvContent.toByteArray())
        zos.closeEntry()
    }

    private fun zipEntryBaseName(name: String): String = name.substringAfterLast('/')

    private suspend fun importVehiclesCsv(csv: String) {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val headerIndex = parseCsvLine(lines.first()).withIndex().associate { (i, h) -> h to i }
        lines.drop(1).forEach { line ->
            val parts = parseCsvLine(line)
            if (parts.size < 2) return@forEach
            val vehicle = TabularSchema.rowToVehicle(parts, headerIndex)
            if (vehicle.name.isBlank()) return@forEach
            vehicleRepository.insert(vehicle)
        }
    }

    private suspend fun vehicleIdBySyncId(): Map<String, Int> =
        vehicleRepository.getAllIncludingDeleted()
            .filter { it.syncId.isNotBlank() }
            .associate { it.syncId to it.id }

    private fun resolveVehicleIdFromSyncId(
        vehicleSyncId: String,
        fallbackVehicleId: Int,
        vehicleIdBySyncId: Map<String, Int>,
    ): Int? {
        if (vehicleSyncId.isNotBlank()) {
            vehicleIdBySyncId[vehicleSyncId]?.let { return it }
        }
        if (fallbackVehicleId != 0) return fallbackVehicleId
        return null
    }

    private suspend fun importFuelCsv(csv: String, fuelTabVehicleId: Int? = null) {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val headerIndex = TabularSchema.headerIndex(parseCsvLine(lines.first()))
        val vehicleIdBySyncId = vehicleIdBySyncId()
        lines.drop(1).forEach { line ->
            val parts = parseCsvLine(line)
            if (parts.isEmpty()) return@forEach
            val parsed = TabularSchema.rowToFuel(parts, headerIndex)
            val vehicleSyncId = TabularSchema.rowToFuelVehicleSyncId(parts, headerIndex)
            val resolvedVehicleId = resolveVehicleIdFromSyncId(
                vehicleSyncId,
                parsed.vehicleId,
                vehicleIdBySyncId,
            ) ?: fuelTabVehicleId ?: return@forEach
            fuelRepository.insertFuelEntry(parsed.copy(vehicleId = resolvedVehicleId))
        }
    }

    private suspend fun importExpenseCsv(csv: String) {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val headerIndex = TabularSchema.headerIndex(parseCsvLine(lines.first()))
        if (!headerIndex.containsKey("Vehicle ID")) {
            importExpenseCsvLegacy(lines)
            return
        }
        val vehicleIdBySyncId = vehicleIdBySyncId()
        lines.drop(1).forEach { line ->
            val parts = parseCsvLine(line)
            if (parts.isEmpty()) return@forEach
            val parsed = TabularSchema.rowToExpense(parts, headerIndex)
            val vehicleSyncIds = TabularSchema.rowToExpenseVehicleSyncIds(parts, headerIndex)
            val resolved = ExpenseVehicleSyncIds.applyResolvedVehicles(
                parsed,
                vehicleSyncIds,
                vehicleIdBySyncId,
            )
            if (resolved.vehicleId == 0 && vehicleSyncIds.isEmpty()) return@forEach
            expenseRepository.insertExpenseEntry(resolved)
        }
    }

    private suspend fun importExpenseCsvLegacy(lines: List<String>) {
        lines.drop(1).forEach { line ->
            val parts = parseCsvLine(line)
            if (parts.size >= 10) {
                val receiptPath = parts[6].ifBlank { null }
                val expense = ExpenseEntry(
                    id = parts[0].toLongOrNull() ?: 0,
                    vehicleId = parts[1].toIntOrNull() ?: 0,
                    date = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                    amount = parts[3].toDoubleOrNull() ?: 0.0,
                    category = parts[4],
                    description = parts[5],
                    photoUrl = receiptPath,
                    latitude = parts[7].toDoubleOrNull(),
                    longitude = parts[8].toDoubleOrNull(),
                    location = parts[9].ifBlank { null },
                    cloudManifest = if (parts.size > 10) parts[10].ifBlank { null } else null,
                )
                expenseRepository.insertExpenseEntry(expense)
            }
        }
    }
}