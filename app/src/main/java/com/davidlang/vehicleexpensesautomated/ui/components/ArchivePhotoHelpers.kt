package com.davidlang.vehicleexpensesautomated.ui.components

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CloudManifest
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson

/** True when any cloud fuel role has a file id for [destId] (or Drive fallback). */
fun fuelHasArchiveIdentity(fuel: FuelEntry?, destId: String?): Boolean {
    if (fuel == null || destId.isNullOrBlank()) return false
    val m = fuel.cloudManifest
    return CloudManifest.hasRole(m, destId, CloudManifest.ROLE_FUEL_DASH) ||
        CloudManifest.hasRole(m, destId, CloudManifest.ROLE_FUEL_PUMP) ||
        CloudManifest.parse(m).any { it.role.startsWith("fuel_") && it.fileId.isNotBlank() }
}

fun expenseHasArchiveIdentity(expense: ExpenseEntry?, destId: String?): Boolean {
    if (expense == null || destId.isNullOrBlank()) return false
    val m = expense.cloudManifest
    return CloudManifest.hasRole(m, destId, CloudManifest.ROLE_EXPENSE_RECEIPT) ||
        CloudManifest.parse(m).any {
            it.role == CloudManifest.ROLE_EXPENSE_RECEIPT ||
                it.role.startsWith("${CloudManifest.ROLE_EXPENSE_RECEIPT}_")
        }
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
