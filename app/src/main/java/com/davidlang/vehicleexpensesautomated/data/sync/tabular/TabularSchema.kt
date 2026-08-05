package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes

/** Canonical tab names, headers, and row encode/decode shared by all tabular backends + CSV zip. */
object TabularSchema {

    const val TAB_VEHICLES = "Vehicles"
    const val TAB_EXPENSES = "Expenses"
    const val TAB_SYNC_TEST = "__sync_test"
    /** Durable merge / Stage C “looks correct” acks (LWW by Sync ID = ackId). */
    const val TAB_MERGE_ACKS = "Merge acks"
    const val FUEL_TAB_PREFIX = "Fuel - "

    val VEHICLE_HEADERS = listOf(
        "Sync ID", "ID", "Name", "Make", "Model", "Year", "License Plate", "VIN", "Notes",
        "Odo Crop L", "Odo Crop T", "Odo Crop R", "Odo Crop B",
        "Other Crop L", "Other Crop T", "Other Crop R", "Other Crop B",
        "Landmark Text Blocks JSON",
        /** Ordered trip type names JSON array; blank → seed/inherit on local insert. */
        "Trip Types JSON",
        /** Ordered expense category names JSON array; blank → seed/inherit on local insert. */
        "Expense Categories JSON",
        "Cloud Manifest", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
    )
    /**
     * Canonical fuel column order for **new / empty** tabs and CSV zip export.
     * Human fields first; machine IDs last.
     *
     * **Location** = station place JSON `{"name":"…","address":"…"}` (or legacy plain text).
     * **Notes** = freeform / batch provenance (e.g. `batch_import_dash:…`).
     * **Trip Type** = non-empty open-only trip start reason; blank = normal fill.
     *
     * [ensureHeaders] / sheet write: existing non-empty headers keep their order;
     * missing columns (e.g. Notes, Trip Type) are **appended** only — never rewrite known column order.
     * [rowToFuel] is name-based (order-independent).
     */
    val FUEL_HEADERS = listOf(
        "Timestamp",
        "Odometer",
        "Gallons",
        "Cost",
        "Currency",
        "Partial Fill",
        "Economy Ignored",
        "Location",
        "Notes",
        "Trip Type",
        "Vehicle Sync ID",
        "Vehicle ID",
        "Photo URL",
        "Cloud Manifest",
        "Sync ID",
        "ID",
        "Origin Device ID",
        "Updated At",
        "Deleted",
        "Deleted At",
    )
    val EXPENSE_HEADERS = listOf(
        "Sync ID", "ID", "Vehicle Sync ID", "Vehicle Sync IDs", "Vehicle ID", "Date", "Amount", "Currency", "Category", "Description", "Vendor",
        "Odometer", "Photo URL", "Receipt Image Path",
        "Location", "Cloud Manifest", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
    )
    val MERGE_ACK_HEADERS = listOf(
        "Sync ID", "Kind", "Member Sync IDs",
        "Created At", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
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
        vehicle.tripTypesJson,
        vehicle.expenseCategoriesJson,
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
            tripTypesJson = cell("Trip Types JSON"),
            expenseCategoriesJson = cell("Expense Categories JSON"),
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
     *
     * @param columnOrder sheet header order to emit; default [FUEL_HEADERS] for new tabs/CSV.
     *   Existing sheets should pass their preserved header row (plus any appended columns).
     */
    fun fuelToRow(
        entry: FuelEntry,
        vehicleSyncId: String = "",
        columnOrder: List<String> = FUEL_HEADERS,
    ): List<String> {
        val byName = fuelFieldMap(entry, vehicleSyncId)
        return columnOrder.map { name -> byName[name] ?: "" }
    }

    /** Named fuel cells for encode/decode; includes all [FUEL_HEADERS] keys. */
    fun fuelFieldMap(entry: FuelEntry, vehicleSyncId: String = ""): Map<String, String> = mapOf(
        "Timestamp" to entry.timestamp.toString(),
        "Odometer" to entry.odometer.toString(),
        "Gallons" to entry.gallons.toString(),
        "Cost" to entry.cost.toString(),
        "Currency" to entry.currency,
        "Partial Fill" to entry.isPartialFill.toString(),
        "Economy Ignored" to entry.economyIgnored.toString(),
        "Location" to (entry.location ?: ""),
        "Notes" to (entry.notes ?: ""),
        "Trip Type" to entry.tripType,
        "Vehicle Sync ID" to vehicleSyncId,
        "Vehicle ID" to entry.vehicleId.toString(),
        "Photo URL" to (entry.photoUrl ?: ""),
        "Cloud Manifest" to (entry.cloudManifest ?: ""),
        "Sync ID" to entry.syncId,
        "ID" to entry.id.toString(),
        "Origin Device ID" to entry.originDeviceId,
        "Updated At" to entry.updatedAt.toString(),
        "Deleted" to entry.deleted.toString(),
        "Deleted At" to (entry.deletedAt?.toString() ?: ""),
    )

    /** Default hard-required columns for entity tabs (Vehicles / Expenses / Fuel / Merge acks). */
    val REQUIRED_IDENTITY_HEADERS: List<String> = listOf("Sync ID")

    /**
     * Required header names from [required] that are **not** present in [firstRow]
     * (trimmed, non-empty cells only). Empty [firstRow] → all [required] missing.
     * Callers treat a **completely blank grid** as case 2 (not missing columns).
     */
    fun missingRequiredHeaders(
        firstRow: List<String>?,
        required: List<String> = REQUIRED_IDENTITY_HEADERS,
    ): List<String> {
        val present = firstRow.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return required.map { it.trim() }.filter { it.isNotEmpty() && it !in present }
    }

    /**
     * True when the entire grid has no non-empty cells (blank tab / empty values).
     * Distinct from corrupt headers (cells exist but required names missing).
     */
    fun isCompletelyBlankGrid(grid: List<List<String>>?): Boolean {
        if (grid.isNullOrEmpty()) return true
        return grid.none { row -> row.any { it.isNotBlank() } }
    }

    /**
     * True when [firstRow] is a usable tabular header for VE entity tabs.
     *
     * **Hard requirement:** trimmed cell equal to **`Sync ID`** must appear
     * (poison grids that treat a UUID data row as headers fail this).
     */
    fun isValidHeaderRow(firstRow: List<String>?, expected: List<String> = emptyList()): Boolean {
        return missingRequiredHeaders(firstRow, REQUIRED_IDENTITY_HEADERS).isEmpty() &&
            firstRow.orEmpty().any { it.isNotBlank() }
    }

    /**
     * Preserve [existingHeader] order; append any [canonical] names not already present.
     * Empty existing → [canonical] (new / blank tab).
     * **Corrupt** headers (cells present without required identity) are **not** silently
     * replaced here — coordinator fails the dest sync instead (see missing-columns plan).
     *
     * Used by tabular [ensureHeaders] when the header row is already valid:
     * - empty / no usable header → write full canonical list
     * - valid non-empty → this merge only (**never reorder** known columns)
     */
    fun mergeHeaderOrder(existingHeader: List<String>, canonical: List<String>): List<String> {
        val existing = existingHeader.map { it.trim() }.filter { it.isNotEmpty() }
        if (existing.isEmpty()) return canonical
        if (!isValidHeaderRow(existing, canonical)) {
            // Caller should have failed already; fall back to canonical without inventing layout.
            return canonical
        }
        val have = existing.toSet()
        return existing + canonical.filter { it !in have }
    }

    /** Pad / truncate a single row to [width] cells (empty string fill). */
    fun padRowToWidth(row: List<String>, width: Int): List<String> {
        if (width <= 0) return emptyList()
        if (row.size == width) return row
        if (row.size > width) return row.take(width)
        return row + List(width - row.size) { "" }
    }

    /**
     * Pad each data row to [headerWidth] so appended header columns do not
     * silently misalign when a backend rewrites the full grid.
     */
    fun padDataRowsToWidth(dataRows: List<List<String>>, headerWidth: Int): List<List<String>> =
        dataRows.map { padRowToWidth(it, headerWidth) }

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
            // Prefer Location JSON blob; fold legacy Latitude/Longitude cells if still present on sheet
            location = FuelLocationJson.foldLegacy(
                cell("Latitude").toDoubleOrNull(),
                cell("Longitude").toDoubleOrNull(),
                cell("Location").ifBlank { null },
            ),
            notes = cell("Notes").ifBlank { null },
            tripType = cell("Trip Type"),
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
            location = FuelLocationJson.foldLegacy(
                cell("Latitude").toDoubleOrNull(),
                cell("Longitude").toDoubleOrNull(),
                cell("Location").ifBlank { null },
            ),
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

    fun ackToRow(ack: MergeAck): List<String> = listOf(
        ack.ackId,
        ack.kind,
        ack.memberSyncIds,
        ack.createdAt.toString(),
        ack.originDeviceId,
        ack.updatedAt.toString(),
        ack.deleted.toString(),
        ack.deletedAt?.toString() ?: "",
    )

    fun rowToAck(row: List<String>, headerIndex: Map<String, Int>): MergeAck {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        val ackId = cell("Sync ID").ifBlank { SyncIdGenerator.randomSyncId() }
        return MergeAck(
            ackId = ackId,
            kind = cell("Kind").ifBlank { MergeAck.KIND_MERGE_EXEMPT },
            memberSyncIds = MergeAck.sortedMembersCsv(
                cell("Member Sync IDs").split(',', '|').map { it.trim() },
            ),
            createdAt = cell("Created At").toLongOrNull() ?: 0L,
            originDeviceId = cell("Origin Device ID"),
            updatedAt = cell("Updated At").toLongOrNull() ?: 0L,
            deleted = cell("Deleted").equals("true", ignoreCase = true),
            deletedAt = cell("Deleted At").toLongOrNull(),
        )
    }
}