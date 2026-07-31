package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3 [ExposedDropdownMenu] with a **scrollable catalog** and a **pinned footer**
 * (e.g. “Manage categories…”) that stays visible without scrolling the list.
 *
 * Must be called as a member of [ExposedDropdownMenuBoxScope] (inside [androidx.compose.material3.ExposedDropdownMenuBox]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.ExposedDropdownMenuWithPinnedFooter(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxCatalogHeight: Dp = 280.dp,
    /** When true, show a “Scroll for more…” line above the catalog. */
    showScrollHint: Boolean = true,
    catalogContent: @Composable ColumnScope.() -> Unit,
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showScrollHint) {
                Text(
                    text = "Scroll for more…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCatalogHeight)
                    .verticalScroll(rememberScrollState()),
                content = catalogContent,
            )
            HorizontalDivider()
            footerContent()
        }
    }
}

/**
 * Convenience: string catalog items + a single manage footer row.
 * Call inside [androidx.compose.material3.ExposedDropdownMenuBox].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.ExposedDropdownMenuWithManageFooter(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<String>,
    onItemClick: (String) -> Unit,
    manageLabel: String,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxCatalogHeight: Dp = 280.dp,
) {
    ExposedDropdownMenuWithPinnedFooter(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        maxCatalogHeight = maxCatalogHeight,
        // Hint when several rows so Manage stays discoverable even if the list fits.
        showScrollHint = items.size >= 4,
        catalogContent = {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = { onItemClick(item) },
                )
            }
        },
        footerContent = {
            DropdownMenuItem(
                text = {
                    Text(
                        manageLabel,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onManageClick,
            )
        },
    )
}
