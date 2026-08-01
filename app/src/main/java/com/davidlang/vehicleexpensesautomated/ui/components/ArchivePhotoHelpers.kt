package com.davidlang.vehicleexpensesautomated.ui.components

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CloudManifest
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson

/**
 * True when any configured photo dest has a fuel role with a file id in the manifest
 * (multi-dest / any fuel_* role). [destId] is optional preferred identity only.
 */
fun fuelHasArchiveIdentity(fuel: FuelEntry?, destId: String?): Boolean {
    if (fuel == null) return false
    val m = fuel.cloudManifest
    // Any dest: fuel role with non-blank fileId (F1.1 multi-dest).
    if (CloudManifest.parse(m).any {
            (it.role == CloudManifest.ROLE_FUEL_DASH ||
                it.role == CloudManifest.ROLE_FUEL_PUMP ||
                it.role.startsWith("fuel_")) &&
                it.fileId.isNotBlank()
        }
    ) {
        return true
    }
    if (destId.isNullOrBlank()) return false
    return CloudManifest.hasRole(m, destId, CloudManifest.ROLE_FUEL_DASH) ||
        CloudManifest.hasRole(m, destId, CloudManifest.ROLE_FUEL_PUMP)
}

/** True when any expense receipt role has a cloud file id (any dest). */
fun expenseHasArchiveIdentity(expense: ExpenseEntry?, destId: String?): Boolean {
    if (expense == null) return false
    val m = expense.cloudManifest
    if (CloudManifest.parse(m).any {
            (it.role == CloudManifest.ROLE_EXPENSE_RECEIPT ||
                it.role.startsWith("${CloudManifest.ROLE_EXPENSE_RECEIPT}_")) &&
                it.fileId.isNotBlank()
        }
    ) {
        return true
    }
    if (destId.isNullOrBlank()) return false
    return CloudManifest.hasRole(m, destId, CloudManifest.ROLE_EXPENSE_RECEIPT)
}

/** First readable local fuel photo uri, or null. */
fun firstReadableFuelPhotoUri(photoUrl: String?, photoStorage: PhotoStorageManager): String? =
    FuelPhotoJson.parse(photoUrl).firstOrNull { photoStorage.isLocalReadable(it.uri) }?.uri

/** True if any stored local path is present but none are readable. */
fun fuelHasDeadLocalOnly(photoUrl: String?, photoStorage: PhotoStorageManager): Boolean {
    val photos = FuelPhotoJson.parse(photoUrl)
    if (photos.isEmpty()) return false
    return photos.none { photoStorage.isLocalReadable(it.uri) }
}

fun expenseLocalMissingOrDead(photoUrl: String?, photoStorage: PhotoStorageManager): Boolean {
    if (photoUrl.isNullOrBlank()) return true
    // Multi-page: missing if none readable
    val pages = com.davidlang.vehicleexpensesautomated.data.model.ExpensePhotoUrls.parse(photoUrl)
    if (pages.isEmpty()) return !photoStorage.isLocalReadable(photoUrl)
    return pages.none { photoStorage.isLocalReadable(it.uri) }
}
