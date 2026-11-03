package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.StoredParse
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

fun fuelPhotoMarkText(photoUrl: String?): String? = when (val parsed = FuelPhotoJson.read(photoUrl)) {
    is StoredParse.Corrupt -> AppStrings.get(R.string.expense_photo_reference_unreadable)
    is StoredParse.Blank -> null
    is StoredParse.Valid -> when {
        parsed.value.any { it.missing } -> "missing"
        parsed.value.any { it.fallbackFrame } -> AppStrings.get(R.string.fill_fallback_frame)
        else -> null
    }
}

fun fuelLocationMarkText(location: String?): String? =
    if (FuelLocationJson.readBlob(location) is StoredParse.Corrupt) AppStrings.get(R.string.fill_location_unreadable) else null

@Composable
fun FuelPhotoMarkLine(photoUrl: String?) {
    val text = fuelPhotoMarkText(photoUrl) ?: return
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun FuelLocationMarkLine(location: String?) {
    val text = fuelLocationMarkText(location) ?: return
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
}

/** Marks with no readable thumb (missing, fallback, unreadable photo or location). */
@Composable
fun FuelMarksWhenNoThumb(photoUrl: String?, location: String?) {
    FuelPhotoMarkLine(photoUrl)
    FuelLocationMarkLine(location)
}
