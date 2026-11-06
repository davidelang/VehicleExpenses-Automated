package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.davidlang.vehicleexpensesautomated.data.model.ExpensePhotoUrls
import com.davidlang.vehicleexpensesautomated.data.model.StoredParse
import com.davidlang.vehicleexpensesautomated.ui.util.S

@Composable
fun ExpenseMissingMark(photoUrl: String, modifier: Modifier = Modifier) {
    val text = when (val parsed = ExpensePhotoUrls.read(photoUrl)) {
        is StoredParse.Corrupt -> S("photo reference unreadable")
        is StoredParse.Valid -> "missing"
        is StoredParse.Blank -> "missing"
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
