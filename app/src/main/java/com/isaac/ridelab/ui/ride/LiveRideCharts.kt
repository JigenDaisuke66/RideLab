package com.isaac.ridelab.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ui.chart.ChartPoint
import com.isaac.ridelab.ui.chart.ChartSeries
import com.isaac.ridelab.ui.theme.RideColors

/** One compact viewport; selecting a metric changes presentation only, never recording. */
@Composable
fun LiveRideCharts(state: LiveTrendState) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(R.string.ui_power, R.string.ui_heart_rate, R.string.cadence)
    val titles = listOf(R.string.chart_power, R.string.chart_hr, R.string.chart_cadence)
    val units = listOf(R.string.history_watts, R.string.history_bpm, R.string.history_rpm)
    val series = when (selected) {
        1 -> state.charts.heartRate
        2 -> state.charts.cadence
        else -> state.charts.power
    }
    val color = when (selected) {
        1 -> RideColors.heartRate
        2 -> RideColors.cadence
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected == index, onClick = { selected = index },
                    label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) })
            }
        }
        val points = remember(series) { series.segments.flatten() }
        val high = remember(points) { maxOf(1.0, points.maxOfOrNull { it.value } ?: 1.0) }
        val low = remember(points) { minOf(0.0, points.minOfOrNull { it.value } ?: 0.0) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ride_live_charts), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.isNotEmpty()) Text(stringResource(units[selected], high),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val description = stringResource(titles[selected])
        val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
        Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
            if (points.isEmpty()) Text(stringResource(R.string.history_no_data),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                val pad = 3.dp.toPx()
                val duration = (state.charts.endMillis - state.startMillis).coerceAtLeast(1)
                fun position(point: ChartPoint) = Offset(
                    pad + ((point.elapsedMillis - state.startMillis).toDouble() / duration * (size.width - 2 * pad)).toFloat(),
                    pad + ((high - point.value) / (high - low) * (size.height - 2 * pad)).toFloat())
                for (fraction in listOf(0f, .5f, 1f)) {
                    val y = pad + fraction * (size.height - 2 * pad)
                    drawLine(grid, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
                }
                series.segments.forEach { segment ->
                    if (segment.size == 1) drawCircle(color, 2.dp.toPx(), position(segment.first()))
                    else {
                        val path = Path()
                        segment.forEachIndexed { i, point ->
                            val p = position(point)
                            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(position(segment.last()).x, size.height - pad)
                            lineTo(position(segment.first()).x, size.height - pad)
                            close()
                        }
                        drawPath(fill, color.copy(alpha = .08f))
                        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (time in listOf(state.startMillis, state.charts.endMillis)) {
                Text(stringResource(R.string.history_time_value, time / 3600000, time / 60000 % 60, time / 1000 % 60),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
