package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import com.isaac.ridelab.ui.theme.StatusPill
import com.isaac.ridelab.ui.theme.RideLabCard
import com.isaac.ridelab.R
import com.isaac.ridelab.analytics.*

private val durationLabels = listOf(R.string.effort_5s, R.string.effort_15s, R.string.effort_30s, R.string.effort_1m, R.string.effort_5m, R.string.effort_20m, R.string.effort_60m)
private val zoneLabels = listOf(R.string.zone_1, R.string.zone_2, R.string.zone_3, R.string.zone_4, R.string.zone_5, R.string.zone_6, R.string.zone_7)
private val zoneRanges = listOf(R.string.zone_range_1, R.string.zone_range_2, R.string.zone_range_3, R.string.zone_range_4, R.string.zone_range_5, R.string.zone_range_6, R.string.zone_range_7)

@Composable
internal fun BestEffortsView(analysis: PowerAnalysis, weight: Double?) {
    val noData = stringResource(R.string.history_no_data)
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.best_efforts), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.ride_weight, weight?.let { stringResource(R.string.weight_value, it) } ?: noData), style = MaterialTheme.typography.bodySmall)
        analysis.efforts.forEachIndexed { index, effort ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(durationLabels[index]), Modifier.weight(.7f))
                Text(effort.watts?.let { stringResource(R.string.history_watts, it) } ?: noData,
                    Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                Text(effort.wattsPerKg?.let { stringResource(R.string.watts_per_kg, it) } ?: noData,
                    Modifier.weight(1.2f), fontFamily = FontFamily.Monospace)
            }
            val pr = analysis.prs.getOrNull(index)
            if (pr != null && pr.status != PrStatus.UNAVAILABLE) {
                val badge = stringResource(when (pr.status) {
                    PrStatus.FIRST -> R.string.pr_first
                    PrStatus.NEW -> R.string.pr_new
                    PrStatus.MATCH -> R.string.pr_match
                    else -> R.string.pr_below
                })
                if (pr.status in setOf(PrStatus.NEW, PrStatus.FIRST, PrStatus.MATCH))
                    StatusPill(badge, true)
                else Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pr.previousWatts != null) Text(stringResource(R.string.pr_comparison, badge, pr.previousWatts),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        }
        Text(stringResource(R.string.effort_coverage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PowerDurationView(analysis: PowerAnalysis) {
    val values = analysis.efforts.map { it.watts }
    val high = maxOf(1.0, values.filterNotNull().maxOrNull() ?: 1.0)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.power_duration), style = MaterialTheme.typography.titleLarge)
        if (values.all { it == null }) Text(stringResource(R.string.history_no_data)) else {
            Text(stringResource(R.string.history_watts, high), style = MaterialTheme.typography.labelSmall)
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val margin = 5.dp.toPx()
                fun point(index: Int, watts: Double) = Offset(margin + index * (size.width - margin * 2) / 6,
                    margin + ((1 - watts / high) * (size.height - margin * 2)).toFloat())
                drawLine(grid, Offset(margin, size.height-margin), Offset(size.width-margin, size.height-margin))
                values.forEachIndexed { i, watts -> if (watts != null) {
                    val p = point(i, watts)
                    if (i > 0) values[i-1]?.let { drawLine(color, point(i-1, it), p, 2.dp.toPx()) }
                    drawCircle(color, 3.dp.toPx(), p)
                } }
            }
            Text(stringResource(R.string.history_watts, 0.0), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            durationLabels.forEach { Text(stringResource(it), fontSize = 11.sp) }
        }
        Text(stringResource(R.string.pdc_fixed_points), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PowerZonesView(analysis: PowerAnalysis) {
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.power_zones), style = MaterialTheme.typography.titleLarge)
        if (analysis.zones == null) Text(stringResource(R.string.zones_unavailable)) else {
            analysis.zones.forEach { zone ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(zoneLabels[zone.index]))
                        Text(stringResource(zoneRanges[zone.index]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        val seconds = zone.millis / 1000
                        Text(stringResource(R.string.ride_elapsed, seconds / 3600, seconds / 60 % 60, seconds % 60), fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.zone_percent, zone.percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(progress = { (zone.percent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp),
                    color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
