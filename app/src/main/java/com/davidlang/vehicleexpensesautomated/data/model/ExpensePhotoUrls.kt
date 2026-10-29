package com.davidlang.vehicleexpensesautomated.data.model

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.StoredParse
import com.davidlang.vehicleexpensesautomated.data.sync.CloudManifest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for expense [ExpenseEntry.photoUrl]: legacy single local path or multi-page JSON
 * `[{"i":0,"uri":"…"},{"i":1,"uri":"…"}]`.
 */
object ExpensePhotoUrls {

    const val MAX_PAGES = 20

    data class Page(val index: Int, val uri: String, val missing: Boolean = false)

    fun read(photoUrl: String?): StoredParse<List<Page>> {
        if (photoUrl.isNullOrBlank()) return StoredParse.Blank()
        val trimmed = photoUrl.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return StoredParse.Valid(listOf(Page(0, trimmed)))
        }
        if (!trimmed.startsWith("[")) {
            Log.e(TAG, "photo reference unreadable")
            return StoredParse.Corrupt(trimmed)
        }
        return try {
            val arr = JSONArray(trimmed)
            val pages = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: return StoredParse.Corrupt(trimmed)
                    val index = obj.optInt("i", i)
                    val uri = obj.optString("uri", "")
                    if (uri.isNotBlank()) add(Page(index, uri, obj.optBoolean("missing", false)))
                }
            }.sortedBy { it.index }.take(MAX_PAGES)
            StoredParse.Valid(pages)
        } catch (e: Exception) {
            Log.e(TAG, "photo reference unreadable", e)
            StoredParse.Corrupt(trimmed)
        }
    }

    fun parse(photoUrl: String?): List<Page> = when (val parsed = read(photoUrl)) {
        is StoredParse.Valid -> parsed.value
        is StoredParse.Blank -> emptyList()
        is StoredParse.Corrupt -> emptyList()
    }

    fun format(pages: List<Page>): String? {
        val normalized = pages
            .filter { it.uri.isNotBlank() }
            .sortedBy { it.index }
            .take(MAX_PAGES)
        if (normalized.isEmpty()) return null
        val only = normalized.singleOrNull()
        if (only != null && only.index == 0 && !only.missing) {
            return only.uri
        }
        val arr = JSONArray()
        for (page in normalized) {
            arr.put(
                JSONObject().apply {
                    put("i", page.index)
                    put("uri", page.uri)
                    if (page.missing) put("missing", true)
                },
            )
        }
        return arr.toString()
    }



    fun isExpenseReceiptRole(role: String): Boolean =
        role == CloudManifest.ROLE_EXPENSE_RECEIPT ||
            role.startsWith("${CloudManifest.ROLE_EXPENSE_RECEIPT}_")

    fun pageIndexFromRole(role: String): Int {
        if (role == CloudManifest.ROLE_EXPENSE_RECEIPT) return 0
        if (!role.startsWith("${CloudManifest.ROLE_EXPENSE_RECEIPT}_")) return 0
        return role.removePrefix("${CloudManifest.ROLE_EXPENSE_RECEIPT}_").toIntOrNull() ?: 0
    }

    /** CloudManifest role: page 0 keeps legacy [CloudManifest.ROLE_EXPENSE_RECEIPT]. */
    fun roleForPage(pageIndex: Int): String =
        if (pageIndex <= 0) {
            CloudManifest.ROLE_EXPENSE_RECEIPT
        } else {
            "${CloudManifest.ROLE_EXPENSE_RECEIPT}_$pageIndex"
        }

    fun remoteFileName(expenseSyncId: String, pageIndex: Int): String =
        if (pageIndex <= 0) {
            "expense_${expenseSyncId}_receipt.jpg"
        } else {
            "expense_${expenseSyncId}_receipt_$pageIndex.jpg"
        }

    /**
     * Merge incoming vs existing multi-page JSON, preferring readable local paths per page index.
     */
    fun mergePreferredReadable(
        incoming: String?,
        existing: String?,
        isReadable: (String) -> Boolean,
    ): String? {
        val incomingPages = parse(incoming).associateBy { it.index }
        val existingPages = parse(existing).associateBy { it.index }
        val allIndexes = (incomingPages.keys + existingPages.keys).sorted()
        if (allIndexes.isEmpty()) return null
        val merged = allIndexes.mapNotNull { index ->
            val inc = incomingPages[index]
            val ex = existingPages[index]
            val uri = when {
                inc != null && isReadable(inc.uri) -> inc.uri
                ex != null && isReadable(ex.uri) -> ex.uri
                inc != null && inc.uri.isNotBlank() -> inc.uri
                ex != null && ex.uri.isNotBlank() -> ex.uri
                else -> null
            }
            uri?.let { Page(index, it) }
        }
        return format(merged)
    }

    private const val TAG = "ExpensePhotoUrls"
}