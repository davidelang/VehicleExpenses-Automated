package com.davidlang.vehicleexpensesautomated.data.sync

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import java.nio.charset.StandardCharsets
import java.util.UUID

object SyncIdGenerator {

    fun randomSyncId(): String = UUID.randomUUID().toString()

    fun deterministicVehicleSyncId(vehicle: Vehicle): String =
        nameUuid("vehicle|${vehicle.id}|${vehicle.name}|${vehicle.make ?: ""}|${vehicle.model ?: ""}|${vehicle.year ?: 0}")

    fun deterministicFuelSyncId(entry: FuelEntry): String =
        nameUuid(
            "fuel|${entry.id}|${entry.vehicleId}|${entry.odometer}|${entry.gallons}|${entry.cost}|${entry.timestamp}",
        )

    fun deterministicExpenseSyncId(entry: ExpenseEntry): String =
        nameUuid(
            "expense|${entry.id}|${entry.vehicleId}|${entry.date}|${entry.amount}|${entry.category}|${entry.description}|${entry.vendor}",
        )

    /** Derive deterministic syncId from sheet row columns when Sync ID column is missing. */
    fun deterministicVehicleFromSheet(
        id: Int,
        name: String,
        make: String?,
        model: String?,
        year: Int?,
    ): String = nameUuid("vehicle|$id|$name|${make ?: ""}|${model ?: ""}|${year ?: 0}")

    fun deterministicFuelFromSheet(
        id: Long,
        vehicleId: Int,
        odometer: Int,
        gallons: Double,
        cost: Double,
        timestamp: Long,
    ): String = nameUuid("fuel|$id|$vehicleId|$odometer|$gallons|$cost|$timestamp")

    fun deterministicExpenseFromSheet(
        id: Long,
        vehicleId: Int,
        date: Long,
        amount: Double,
        category: String,
        description: String,
        vendor: String,
    ): String = nameUuid("expense|$id|$vehicleId|$date|$amount|$category|$description|$vendor")

    private fun nameUuid(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
}