package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Card chrome for **tappable** list/hub items only (navigates or activates).
 * Non-tappable KPIs, form fields, switches stay bare (no Card).
 */
@Composable
fun TappableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Do not force fillMaxWidth on the outer Card — AdaptiveItemGrid measures natural width first.
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(12.dp),
            content = content,
        )
    }
}

/**
 * Multi-column grid driven by **content-measured** widest item (not fixed breakpoint tables).
 *
 * 1. Available width [W] from parent.
 * 2. Measure each item natural width; [itemW] = max.
 * 3. [cols] = max(1, floor((W + gap) / (itemW + gap))).
 * 4. Equal-weight cells in row-major rows; column count reacts to fontScale / density / content.
 *
 * Prefer inside an existing vertical scroll; does not nest its own scroll.
 */
@Composable
fun <T> AdaptiveItemGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        if (maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
            Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
                items.forEach { item -> itemContent(item) }
            }
            return@BoxWithConstraints
        }
        val hGapPx = with(density) { horizontalGap.roundToPx() }
        val vGapPx = with(density) { verticalGap.roundToPx() }

        SubcomposeLayout(Modifier.fillMaxWidth()) {
            // Pass 1: natural (wrap) widths — items should not force full parent width
            val naturalWidths = items.mapIndexed { index, item ->
                subcompose("nat_$index") {
                    itemContent(item)
                }.first().measure(Constraints(maxWidth = maxWidthPx)).width
            }
            val itemW = naturalWidths.maxOrNull()?.coerceAtLeast(1) ?: 1
            val cols = max(1, min(items.size, (maxWidthPx + hGapPx) / (itemW + hGapPx)))
            val cellW = if (cols <= 1) {
                maxWidthPx
            } else {
                (maxWidthPx - hGapPx * (cols - 1)) / cols
            }.coerceAtLeast(1)

            // Pass 2: equal cell width
            val cells = items.mapIndexed { index, item ->
                subcompose("cell_$index") {
                    Box(Modifier = Modifier.width(with(density) { cellW.toDp() })) {
                        itemContent(item)
                    }
                }.first().measure(
                    Constraints(minWidth = cellW, maxWidth = cellW),
                )
            }

            val rows = cells.chunked(cols)
            val rowHeights = rows.map { row -> row.maxOf { it.height } }
            val totalHeight = rowHeights.sum() + vGapPx * (rows.size - 1).coerceAtLeast(0)

            layout(maxWidthPx, totalHeight) {
                var y = 0
                rows.forEachIndexed { rowIndex, row ->
                    var x = 0
                    row.forEach { placeable ->
                        placeable.placeRelative(x, y)
                        x += cellW + hGapPx
                    }
                    y += rowHeights[rowIndex] + vGapPx
                }
            }
        }
    }
}

/** Empty list / empty filter message (Lab + production). */
@Composable
fun EmptyStateText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = true,
        modifier = modifier.padding(vertical = 16.dp),
    )
}

/**
 * Primary date/time **trigger**: full-width [OutlinedButton] showing [label].
 * Caller owns DatePicker / TimePicker dialogs.
 */
@Composable
fun AppDateTimeField(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(label, softWrap = true, maxLines = 2)
    }
}

/** Dialog / inline form footer Cancel. */
@Composable
fun AppTextCancel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Cancel",
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, softWrap = true)
    }
}

/** Full-width leave / back-to-list secondary action. */
@Composable
fun AppOutlinedBack(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Cancel",
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text, softWrap = true, maxLines = 2)
    }
}

/** Feature page title + optional subtitle (not camera Quick Fill shell). */
@Composable
fun FeatureScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            softWrap = true,
            maxLines = 3,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
        }
    }
}

/** Toolbar/row icon defaults: 24.dp, theme content color. Material [ImageVector] only. */
@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp),
        tint = tint,
    )
}
