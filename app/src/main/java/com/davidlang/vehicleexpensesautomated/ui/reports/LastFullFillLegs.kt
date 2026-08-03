package com.davidlang.vehicleexpensesautomated.ui.reports

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.LabFullFillLeg
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.excludeMpgOutliers
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.formatLabDate
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.formatMpg
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.formatVolume
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

/**
 * Newest ≤5 full-fill legs with mpg bar (shared R&C / Lab vehicle summary style).
 * [legsChrono] should be oldest→newest from [com.davidlang.vehicleexpensesautomated.ui.reports.lab.allValidLegsChrono].
 */
@Composable
fun LastFullFillLegsBlock(
    legsChrono: List<LabFullFillLeg>,
    volumeUnitLabel: String,
    defaultSymbol: String,
    modifier: Modifier = Modifier,
    title: String = "Last 5 full fills",
) {
    val displayNewestFirst = excludeMpgOutliers(legsChrono).asReversed().take(5)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (displayNewestFirst.isEmpty()) {
            Text(stringResource(R.string.reports_no_full_fills), style = MaterialTheme.typography.bodySmall)
        } else {
            val maxMpg = displayNewestFirst.maxOfOrNull { it.mpg }?.takeIf { it > 0 } ?: 1.0
            displayNewestFirst.forEach { leg ->
                val barFrac = (leg.mpg / maxMpg).coerceIn(0.0, 1.0).toFloat()
                FullFillLegRowUi(
                    dateLabel = formatLabDate(leg.endFill.timestamp),
                    odo = leg.endFill.odometer,
                    costLabel = CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, defaultSymbol),
                    volumeLabel = formatVolume(leg.sumVol, volumeUnitLabel),
                    mpg = leg.mpg,
                    barFraction = barFrac,
                )
            }
        }
    }
}

/** One full-fill leg row: date/odo, cost·vol, mpg bar. */
@Composable
fun FullFillLegRowUi(
    dateLabel: String,
    odo: Int,
    costLabel: String,
    volumeLabel: String,
    mpg: Double,
    barFraction: Float,
) {
    val context = LocalContext.current
    val effLabel = UnitFormat.economyEfficiencyLabel(context)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$dateLabel · odo $odo",
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$costLabel · $volumeLabel",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                )
                Text(
                    "$effLabel ${formatMpg(mpg)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Share/text lines for last-5 legs (newest first after outlier filter). */
fun lastFullFillLegsShareLines(
    legsChrono: List<LabFullFillLeg>,
    volumeUnitLabel: String,
    defaultSymbol: String,
    efficiencyLabel: String = UnitFormat.economyEfficiencyLabel(),
): List<String> {
    val display = excludeMpgOutliers(legsChrono).asReversed().take(5)
    if (display.isEmpty()) return listOf("  (none)")
    return display.map { leg ->
        val cost = CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, defaultSymbol)
        val vol = formatVolume(leg.sumVol, volumeUnitLabel)
        "  ${formatLabDate(leg.endFill.timestamp)} odo ${leg.endFill.odometer} " +
            "$cost $vol $efficiencyLabel ${formatMpg(leg.mpg)}"
    }
}
