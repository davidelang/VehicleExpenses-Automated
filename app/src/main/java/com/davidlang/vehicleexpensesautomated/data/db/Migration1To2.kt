package com.davidlang.vehicleexpensesautomated.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Rename fuel_fills → fuel_entries
        db.execSQL("ALTER TABLE fuel_fills RENAME TO fuel_entries")

        // Rename expenses → expense_entries
        db.execSQL("ALTER TABLE expenses RENAME TO expense_entries")
    }
}
