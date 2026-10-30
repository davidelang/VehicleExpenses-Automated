package com.davidlang.vehicleexpensesautomated.data.sync

import com.davidlang.vehicleexpensesautomated.data.model.ExpensePhotoUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CloudManifestRolesTest {
    @Test
    fun originalRolesAreNotReceipts() {
        assertEquals("vehicle_ref_original", CloudManifest.ROLE_VEHICLE_REF_ORIGINAL)
        assertEquals("expense_original", CloudManifest.ROLE_EXPENSE_ORIGINAL)
        assertFalse(ExpensePhotoUrls.isExpenseReceiptRole("expense_original"))
        assertFalse(ExpensePhotoUrls.isExpenseReceiptRole(CloudManifest.ROLE_EXPENSE_ORIGINAL))
    }

    @Test
    fun photoSummaryNamesMissingItems() {
        val result = PhotoBackupResult(
            success = true,
            message = "ok",
            missing = listOf("fuel: dash.jpg"),
        )
        val text = SyncResultMessages.photoSummary(
            results = listOf("Drive" to result),
            anyFailure = false,
            totalUploads = 0,
            totalDownloads = 0,
        )
        assertTrue(text.contains("missing"))
        assertTrue(text.contains("dash.jpg"))
    }
}
