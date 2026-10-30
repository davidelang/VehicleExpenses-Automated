package com.davidlang.vehicleexpensesautomated.data.db

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.transfer.BackupContents
import com.davidlang.vehicleexpensesautomated.data.transfer.DataTransferEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Opens Room once from [Unchecked]. Does not run while a transfer gate is held,
 * and does not run again from [Ok] or [Failed].
 */
object DatabaseHealth {
    fun verify(app: VehicleExpensesApplication): DbState {
        val holder = app.dbState
        when (val current = holder.states.value) {
            is Exporting, is Importing, Ok, is Failed -> return current
            Unchecked -> Unit
        }
        var fromVersion: Int? = null
        try {
            val dbFile = app.getDatabasePath(BackupContents.DB_FILE_NAME)
            if (dbFile.isFile) {
                val opened = SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    DatabaseErrorHandler { /* the default handler deletes the file */ },
                )
                try {
                    fromVersion = opened.version
                } finally {
                    opened.close()
                }
            }
            val database = EntryPointAccessors.fromApplication(
                app,
                DataTransferEntryPoint::class.java,
            ).appDatabase()
            database.openHelper.writableDatabase
            app.casLogged(Unchecked, Ok)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            app.casLogged(
                Unchecked,
                Failed(fromVersion, APP_DB_SCHEMA_VERSION, t),
            )
        }
        return holder.states.value
    }
}
