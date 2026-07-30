package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseEntryRepository: ExpenseEntryRepository,
    private val photoBackupCoordinator: PhotoBackupCoordinator,
) : ViewModel() {

    val expenses: StateFlow<List<ExpenseEntry>> = expenseEntryRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Persist then return — callers must await before navigate. */
    suspend fun saveExpense(entry: ExpenseEntry) {
        withContext(Dispatchers.IO) {
            expenseEntryRepository.saveEntry(entry)
            photoBackupCoordinator.enqueueAfterSave()
        }
    }

    suspend fun downloadExpensePhoto(entry: ExpenseEntry): String? =
        photoBackupCoordinator.downloadExpensePhoto(entry)

    suspend fun scrubUnreadableExpensePhotos(entry: ExpenseEntry): ExpenseEntry =
        photoBackupCoordinator.scrubUnreadableExpensePhotos(entry)

    suspend fun getExpenseById(id: Long): ExpenseEntry? = expenseEntryRepository.getById(id)

    suspend fun deleteExpense(entry: ExpenseEntry) {
        withContext(Dispatchers.IO) {
            expenseEntryRepository.markExpenseDeleted(entry)
        }
    }
}
