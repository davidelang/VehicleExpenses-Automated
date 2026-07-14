package com.davidlang.vehicleexpensesautomated.ui.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherKind
import com.davidlang.vehicleexpensesautomated.data.sync.TabularOtherProviderCatalog

@Composable
fun SpreadsheetSyncScreen(
    navController: NavHostController,
    viewModel: SpreadsheetSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val store = remember { SyncDestinationStore(context) }
    var destinations by remember { mutableStateOf(store.allSpreadsheet()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pickingProvider by remember { mutableStateOf(false) }
    var pickingOtherKind by remember { mutableStateOf(false) }
    var pickingOtherProvider by remember { mutableStateOf<TabularOtherKind?>(null) }

    fun refreshList() {
        destinations = store.allSpreadsheet()
    }

    when {
        pickingOtherProvider != null -> SpreadsheetOtherProviderPicker(
            kind = pickingOtherProvider!!,
            onPick = { info ->
                pickingOtherProvider = null
                if (info.implemented) {
                    editingId = TabularOtherProviderCatalog.newDestIdFor(info.provider)
                } else {
                    Toast.makeText(
                        context,
                        "${info.label} is not yet implemented",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onCancel = { pickingOtherProvider = null },
        )
        pickingOtherKind -> SpreadsheetOtherKindPicker(
            onPick = { kind ->
                pickingOtherKind = false
                pickingOtherProvider = kind
            },
            onCancel = { pickingOtherKind = false },
        )
        pickingProvider -> SpreadsheetProviderPicker(
            onPick = { provider ->
                pickingProvider = false
                if (provider == SpreadsheetProvider.OTHER) {
                    pickingOtherKind = true
                } else {
                    editingId = when (provider) {
                        SpreadsheetProvider.EXCEL -> "new:excel"
                        SpreadsheetProvider.ETHERCALC -> "new:ethercalc"
                        else -> "new:sheets"
                    }
                }
            },
            onCancel = { pickingProvider = false },
        )
        editingId == null -> SpreadsheetDestList(
            destinations = destinations,
            onAdd = {
                if (destinations.size >= SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE) {
                    Toast.makeText(
                        context,
                        "Maximum ${SyncDestinationStore.MAX_DESTINATIONS_PER_TYPE} spreadsheet destinations",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pickingProvider = true
                }
            },
            onEdit = { editingId = it },
            viewModel = viewModel,
        )
        else -> SpreadsheetDestEditForm(
            destId = editingId!!,
            totalDestCount = destinations.size + if (editingId!!.startsWith("new")) 1 else 0,
            store = store,
            viewModel = viewModel,
            onBack = {
                editingId = null
                refreshList()
            },
            onRemoved = {
                editingId = null
                refreshList()
            },
        )
    }
}