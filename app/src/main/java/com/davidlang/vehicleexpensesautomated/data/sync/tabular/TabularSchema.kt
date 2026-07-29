package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes

/** Canonical tab names, headers, and row encode/decode shared by all tabular backends + CSV zip. */
object TabularSchema {

    const val TAB_VEHICLES = "Vehicles"
    const val TAB_EXPENSES = "Expenses"
    const val TAB_SYNC_TEST = "__sync_test"
    const val FUEL_TAB_PREFIX = "Fuel - "

    val VEHICLE_HEADERS = listOf(
        "Sync ID", "ID", "Name", "Make", "Model", "Year", "License Plate", "VIN", "Notes",
        "Odo Crop L", "Odo Crop T", "Odo Crop R", "Odo Crop B",
        "Other Crop L", "Other Crop T", "Other Crop R", "Other Crop B",
        "Landmark Text Blocks JSON",
        "Cloud Manifest", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
    )
    val FUEL_HEADERS = listOf(
        "Sync ID", "ID", "Vehicle Sync ID", "Vehicle ID", "Odometer", "Gallons", "Cost", "Currency", "Timestamp",
        "Photo URL", "Partial Fill", "Economy Ignored", "Latitude", "Longitude", "Location", "Cloud Manifest",
        "Origin Device ID", "Updated At", "Deleted", "Deleted At",
    )
    val EXPENSE_HEADERS = listOf(
        "Sync ID", "ID", "Vehicle Sync ID", "Vehicle Sync IDs", "Vehicle ID", "Date", "Amount", "Currency", "Category", "Description", "Vendor",
        "Odometer", "Photo URL", "Receipt Image Path", "Latitude", "Longitude",
        "Location", "Cloud Manifest", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
    )

    fun fuelTabName(vehicleName: String): String = FUEL_TAB_PREFIX + sanitizeTabName(vehicleName)

    fun sanitizeTabName(name: String): String {
        val cleaned = name.replace(Regex("""[\[\]*?:/\\]"""), " ").trim()
        return cleaned.take(100).ifBlank { "Vehicle" }
    }

    fun headerIndex(headers: List<String>): Map<String, Int> =
        headers.mapIndexed { index, name -> name to index }.toMap()

    fun syncIdFromRow(row: List<String>, headerIndex: Map<String, Int>, column: String = "Sync ID"): String {
        val idx = headerIndex[column] ?: -1
        return row.getOrElse(idx) { "" }.trim()
    }

    fun rowsEqual(a: List<String>, b: List<String>): Boolean {
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            if (a.getOrElse(i) { "" } != b.getOrElse(i) { "" }) return false
        }
        return true
    }

    fun vehicleToRow(vehicle: Vehicle): List<String> = listOf(
        vehicle.syncId,
        vehicle.id.toString(),
        vehicle.name,
        vehicle.make ?: "",
        vehicle.model ?: "",
        vehicle.year?.toString() ?: "",
        vehicle.licensePlate ?: "",
        vehicle.vin ?: "",
        vehicle.notes ?: "",
        vehicle.odometerCropLeft?.toString() ?: "",
        vehicle.odometerCropTop?.toString() ?: "",
        vehicle.odometerCropRight?.toString() ?: "",
        vehicle.odometerCropBottom?.toString() ?: "",
        vehicle.otherTextCropLeft?.toString() ?: "",
        vehicle.otherTextCropTop?.toString() ?: "",
        vehicle.otherTextCropRight?.toString() ?: "",
        vehicle.otherTextCropBottom?.toString() ?: "",
        vehicle.landmarkTextBlocksJson ?: "",
        vehicle.cloudManifest ?: "",
        vehicle.originDeviceId,
        vehicle.updatedAt.toString(),
        vehicle.deleted.toString(),
        vehicle.deletedAt?.toString() ?: "",
    )

    fun rowToVehicle(row: List<String>, headerIndex: Map<String, Int>): Vehicle {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        fun optionalFloat(name: String): Float? = cell(name).toFloatOrNull()
        val id = cell("ID").toIntOrNull() ?: 0
        val name = cell("Name")
        val make = cell("Make").ifBlank { null }
        val model = cell("Model").ifBlank { null }
        val year = cell("Year").toIntOrNull()
        val syncIdCell = cell("Sync ID")
        val syncId = syncIdCell.ifBlank {
            SyncIdGenerator.deterministicVehicleFromSheet(id, name, make, model, year)
        }
        return Vehicle(
            syncId = syncId,
            id = id,
            name = name,
            make = make,
            model = model,
            year = year,
            licensePlate = cell("License Plate").ifBlank { null },
            vin = cell("VIN").ifBlank { null },
            notes = cell("Notes").ifBlank { null },
            odometerCropLeft = optionalFloat("Odo Crop L"),
            odometerCropTop = optionalFloat("Odo Crop T"),
            odometerCropRight = optionalFloat("Odo Crop R"),
            odometerCropBottom = optionalFloat("Odo Crop B"),
            otherTextCropLeft = optionalFloat("Other Crop L"),
            otherTextCropTop = optionalFloat("Other Crop T"),
            otherTextCropRight = optionalFloat("Other Crop R"),
            otherTextCropBottom = optionalFloat("Other Crop B"),
            landmarkTextBlocksJson = cell("Landmark Text Blocks JSON").ifBlank { null },
            cloudManifest = cell("Cloud Manifest").ifBlank { null },
            originDeviceId = cell("Origin Device ID"),
            updatedAt = cell("Updated At").toLongOrNull() ?: 0L,
            deleted = cell("Deleted").equals("true", ignoreCase = true),
            deletedAt = cell("Deleted At").toLongOrNull(),
        )
    }

    /**
     * Fuel sheet **ID** column is **device-local** (Room PK of the writer).
     * Cross-device identity is **Sync ID** only — never use sheet ID as Room PK on pull.
     */
    fun fuelToRow(entry: FuelEntry, vehicleSyncId: String = ""): List<String> = listOf(
        entry.syncId,
        entry.id.toString(),
        vehicleSyncId,
        entry.vehicleId.toString(),
        entry.odometer.toString(),
        entry.gallons.toString(),
        entry.cost.toString(),
        entry.currency,
        entry.timestamp.toString(),
        entry.photoUrl ?: "",
        entry.isPartialFill.toString(),
        entry.economyIgnored.toString(),
        entry.latitude?.toString() ?: "",
        entry.longitude?.toString() ?: "",
        entry.location ?: "",
        entry.cloudManifest ?: "",
        entry.originDeviceId,
        entry.updatedAt.toString(),
        entry.deleted.toString(),
        entry.deletedAt?.toString() ?: "",
    )

    fun expenseToRow(entry: ExpenseEntry, vehicleSyncId: String = ""): List<String> {
        val syncIds = ExpenseVehicleSyncIds.parse(entry.vehicleSyncIdsJson)
        val primarySyncId = syncIds.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: vehicleSyncId
        val syncIdsJson = if (syncIds.isNotEmpty()) {
            entry.vehicleSyncIdsJson
        } else if (vehicleSyncId.isNotBlank()) {
            ExpenseVehicleSyncIds.format(listOf(vehicleSyncId))
        } else {
            ""
        }
        return listOf(
        entry.syncId,
        entry.id.toString(),
        primarySyncId,
        syncIdsJson,
        entry.vehicleId.toString(),
        entry.date.toString(),
        entry.amount.toString(),
        entry.currency,
        entry.category,
        entry.description,
        entry.vendor,
        entry.odometer?.toString() ?: "",
        entry.photoUrl ?: "",
        "",
        entry.latitude?.toString() ?: "",
        entry.longitude?.toString() ?: "",
        entry.location ?: "",
        entry.cloudManifest ?: "",
        entry.originDeviceId,
        entry.updatedAt.toString(),
        entry.deleted.toString(),
        entry.deletedAt?.toString() ?: "",
    )
    }

    fun rowToFuel(row: List<String>, headerIndex: Map<String, Int>): FuelEntry {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        // Sheet ID is legacy / device-local only — never materialize as Room PK
        val sheetIdLegacy = cell("ID").toLongOrNull() ?: 0L
        val vehicleId = cell("Vehicle ID").toIntOrNull() ?: 0
        val odometer = cell("Odometer").toIntOrNull() ?: 0
        val gallons = cell("Gallons").toDoubleOrNull() ?: 0.0
        val cost = cell("Cost").toDoubleOrNull() ?: 0.0
        val currency = CurrencyCodes.fromSymbolOrCode(cell("Currency"))
        val timestamp = cell("Timestamp").toLongOrNull() ?: 0L
        val syncIdCell = cell("Sync ID")
        val syncId = syncIdCell.ifBlank {
            SyncIdGenerator.deterministicFuelFromSheet(
                sheetIdLegacy, vehicleId, odometer, gallons, cost, timestamp,
            )
        }
        return FuelEntry(
            syncId = syncId,
            id = 0,
            vehicleId = vehicleId,
            odometer = odometer,
            gallons = gallons,
            cost = cost,
            currency = currency,
            timestamp = timestamp,
            photoUrl = cell("Photo URL").ifBlank { null },
            isPartialFill = cell("Partial Fill").equals("true", ignoreCase = true),
            economyIgnored = cell("Economy Ignored").equals("true", ignoreCase = true),
            latitude = cell("Latitude").toDoubleOrNull(),
            longitude = cell("Longitude").toDoubleOrNull(),
            location = cell("Location").ifBlank { null },
            cloudManifest = cell("Cloud Manifest").ifBlank { null },
            originDeviceId = cell("Origin Device ID"),
            updatedAt = cell("Updated At").toLongOrNull() ?: 0L,
            deleted = cell("Deleted").equals("true", ignoreCase = true),
            deletedAt = cell("Deleted At").toLongOrNull(),
        )
    }

    fun rowToFuelVehicleSyncId(row: List<String>, headerIndex: Map<String, Int>): String {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        return cell("Vehicle Sync ID")
    }

    fun rowToExpense(row: List<String>, headerIndex: Map<String, Int>): ExpenseEntry {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        // Sheet ID is device-local only — Room PK auto-generated on insert
        val sheetIdLegacy = cell("ID").toLongOrNull() ?: 0L
        val vehicleId = cell("Vehicle ID").toIntOrNull() ?: 0
        val date = cell("Date").toLongOrNull() ?: 0L
        val amount = cell("Amount").toDoubleOrNull() ?: 0.0
        val currency = CurrencyCodes.fromSymbolOrCode(cell("Currency"))
        val category = cell("Category").ifBlank { "Other" }
        val description = cell("Description")
        val vendor = cell("Vendor")
        val syncIdCell = cell("Sync ID")
        val syncId = syncIdCell.ifBlank {
            SyncIdGenerator.deterministicExpenseFromSheet(
                sheetIdLegacy, vehicleId, date, amount, category, description, vendor,
            )
        }
        val vehicleSyncIdsJson = rowToExpenseVehicleSyncIdsJson(row, headerIndex)
        return ExpenseEntry(
            syncId = syncId,
            id = 0,
            vehicleId = vehicleId,
            vehicleSyncIdsJson = vehicleSyncIdsJson,
            amount = amount,
            currency = currency,
            description = description,
            date = date,
            photoUrl = cell("Photo URL").ifBlank { null },
            category = category,
            vendor = vendor,
            odometer = cell("Odometer").toIntOrNull(),
            latitude = cell("Latitude").toDoubleOrNull(),
            longitude = cell("Longitude").toDoubleOrNull(),
            location = cell("Location").ifBlank { null },
            cloudManifest = cell("Cloud Manifest").ifBlank { null },
            originDeviceId = cell("Origin Device ID"),
            updatedAt = cell("Updated At").toLongOrNull() ?: 0L,
            deleted = cell("Deleted").equals("true", ignoreCase = true),
            deletedAt = cell("Deleted At").toLongOrNull(),
        )
    }

    fun rowToExpenseVehicleSyncId(row: List<String>, headerIndex: Map<String, Int>): String {
        val fromList = rowToExpenseVehicleSyncIds(row, headerIndex).firstOrNull()
        if (!fromList.isNullOrBlank()) return fromList
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        return cell("Vehicle Sync ID")
    }

    fun rowToExpenseVehicleSyncIds(row: List<String>, headerIndex: Map<String, Int>): List<String> {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        val multiCell = cell("Vehicle Sync IDs")
        val parsed = ExpenseVehicleSyncIds.parse(multiCell)
        if (parsed.isNotEmpty()) return parsed
        val single = cell("Vehicle Sync ID").trim()
        return if (single.isNotBlank()) listOf(single) else emptyList()
    }

    fun rowToExpenseVehicleSyncIdsJson(row: List<String>, headerIndex: Map<String, Int>): String {
        val syncIds = rowToExpenseVehicleSyncIds(row, headerIndex)
        return ExpenseVehicleSyncIds.format(syncIds)
    }
}