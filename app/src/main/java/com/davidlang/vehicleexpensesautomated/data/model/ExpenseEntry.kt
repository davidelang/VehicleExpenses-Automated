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
    val photoUrl: String? = null   // ← NEW: stores Drive public URL
)
