package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class,
        parentColumns = ["id"],
        childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val amount: Double,
    val dateMillis: Long,          // ← changed to Long (epoch millis)
    val category: String,
    val description: String? = null,
    val receiptPath: String? = null
)
