package com.davidlang.vehicleexpensesautomated.data.model

import com.davidlang.vehicleexpensesautomated.data.sync.CloudManifest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for expense [ExpenseEntry.photoUrl]: legacy single local path or multi-page JSON
 * `[{"i":0,"uri":"…"},{"i":1,"uri":"…"}]`.
 */
object ExpensePhotoUrls {

    const val MAX_PAGES = 20

    data class Page(val index: Int, val uri: String)

    fun parse(photoUrl: String?): List<Page> {
        if (photoUrl.isNullOrBlank()) return emptyList()
        return try {
            if (photoUrl.trimStart().startsWith("[")) {
                val arr = JSONArray(photoUrl)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val index = obj.optInt("i", i)
                        val uri = obj.optString("uri", "")
                        if (uri.isNotBlank()) add(Page(index, uri))
                    }
                }.sortedBy { it.index }.take(MAX_PAGES)
            } else {
                listOf(Page(0, photoUrl))
            }
        } catch (_: Exception) {
            listOf(Page(0, photoUrl))
        }
    }

    fun format(pages: List<Page>): String? {
        val normalized = pages
            .filter { it.uri.isNotBlank() }
            .sortedBy { it.index }
            .take(MAX_PAGES)
        if (normalized.isEmpty()) return null
        if (normalized.size == 1 && normalized[0].index == 0) {
            return normalized[0].uri
        }
        val arr = JSONArray()
        for (page in normalized) {
            arr.put(
                JSONObject().apply {
                    put("i", page.index)
                    put("uri", page.uri)
                },
            )
        }
        return arr.toString()
    }

    fun isMulti(photoUrl: String?): Boolean =
        photoUrl?.trimStart()?.startsWith("[") == true

    fun listUris(photoUrl: String?): List<String> = parse(photoUrl).map { it.uri }

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
}