package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
 *
 * **Width contract:** Prefer **no** [Modifier.fillMaxWidth] on [modifier] when used inside
 * [AdaptiveItemGrid] (grid natural pass measures wrap width). When the parent assigns a
 * finite width (grid cell), [fillMaxWidth] on the card/column fills that **cell** only.
 * [AdaptiveItemGrid] wraps the natural pass in [wrapContentWidth] so a child fillMaxWidth
 * does not expand to the full screen during column-count measure.
 */
@Composable
fun TappableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
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
 * Content-measured multi-column grid (not fixed breakpoint tables).
 *
 * Algorithm:
 * 1. W = parent max width (px).
 * 2. Natural pass: measure each item with minWidth=0, maxWidth=Infinity (wrapped so
 *    fillMaxWidth children do not expand to parent W); natural_i = min(measured, W).
 * 3. itemW = max(natural_i) (at least 1; if 0 use W).
 * 4. cols = max(1, min(n, floor((W + gap) / (itemW + gap)))).
 * 5. cellW = W when cols==1 else (W - (cols-1)*gap) / cols.
 * 6. Layout pass: measure each item with minWidth=maxWidth=cellW (fill cell); row-major place.
 *
 * **Children must wrap for multi-col** — do not put [Modifier.fillMaxWidth] on the outermost
 * composable inside the [itemContent] lambda. Prefer [TappableCard] (wraps) or bare wrap content.
 * No dp floor in column-count math.
 *
 * Prefer inside an existing vertical scroll; no internal vertical scroll.
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
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        if (maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
            Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
                items.forEach { item -> itemContent(item) }
            }
            return@BoxWithConstraints
        }
        val hGapPx = with(density) { horizontalGap.roundToPx() }
        val vGapPx = with(density) { verticalGap.roundToPx() }

        SubcomposeLayout(Modifier.fillMaxWidth()) { _ ->
            // Natural (wrap) measure — infinite max so fillMaxWidth does not snap to parent W
            val naturalWidths = items.mapIndexed { index, item ->
                val placeable = subcompose("nat$index") {
                    Box(
                        modifier = Modifier.wrapContentWidth(
                            align = Alignment.Start,
                            unbounded = true,
                        ),
                    ) {
                        itemContent(item)
                    }
                }.first().measure(
                    Constraints(minWidth = 0, maxWidth = Constraints.Infinity),
                )
                min(placeable.width, maxWidthPx).coerceAtLeast(0)
            }
            var itemW = naturalWidths.maxOrNull()?.coerceAtLeast(1) ?: 1
            if (itemW <= 0) itemW = maxWidthPx

            val cols = max(1, min(items.size, (maxWidthPx + hGapPx) / (itemW + hGapPx)))
            val cellW = if (cols <= 1) {
                maxWidthPx
            } else {
                (maxWidthPx - hGapPx * (cols - 1)) / cols
            }.coerceAtLeast(1)

            // Fill cell — equal columns
            val cells = items.mapIndexed { index, item ->
                subcompose("cell$index") {
                    Box(
                        modifier = Modifier
                            .width(with(density) { cellW.toDp() })
                            .fillMaxWidth(),
                    ) {
                        itemContent(item)
                    }
                }.first().measure(Constraints(minWidth = cellW, maxWidth = cellW))
            }

            val rows = cells.chunked(cols)
            val rowHeights = rows.map { row -> row.maxOf { it.height } }
            val totalH = rowHeights.sum() + vGapPx * (rows.size - 1).coerceAtLeast(0)

            layout(maxWidthPx, totalH) {
                var y = 0
                rows.forEachIndexed { ri, row ->
                    var x = 0
                    row.forEach { p ->
                        p.placeRelative(x, y)
                        x += cellW + hGapPx
                    }
                    y += rowHeights[ri] + vGapPx
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
