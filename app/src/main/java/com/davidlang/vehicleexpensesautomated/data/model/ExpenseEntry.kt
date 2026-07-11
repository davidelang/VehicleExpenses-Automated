package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_entries")
data class ExpenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    val amount: Double,
    val description: String,
    val date: Long,
    val photoUrl: String? = null,
    val category: String = "Other",
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val cloudManifest: String? = null,
    /** Free-text vendor / payee (separate from description notes). */
    val vendor: String = "",
    /** Optional odometer at expense time. */
    val odometer: Int? = null
)
