package com.isaac.ridelab.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.isaac.ridelab.ui.theme.RideColors
import com.isaac.ridelab.R

@Composable
fun RideChart(series: ChartSeries, title: Int, unitFormat: Int, endMillis: Long, showTitle: Boolean = true) {
    val points = remember(series) { series.segments.flatten() }
    val low = remember(points) { minOf(0.0, points.minOfOrNull { it.value } ?: 0.0) }
    val high = remember(points) { maxOf(1.0, points.maxOfOrNull { it.value } ?: 1.0) }
    val color = when (title) {
        R.string.chart_hr -> RideColors.heartRate
        R.string.chart_cadence -> RideColors.cadence
        else -> MaterialTheme.colorScheme.primary
    }
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        if (points.isEmpty()) Text(stringResource(R.string.history_no_data)) else {
            Text(stringResource(unitFormat, high), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val pad = 3.dp.toPx()
                fun position(point: ChartPoint) = Offset(
                    pad + (point.elapsedMillis.toDouble() / endMillis.coerceAtLeast(1) * (size.width - pad * 2)).toFloat(),
                    pad + ((high - point.value) / (high - low) * (size.height - pad * 2)).toFloat())
                for (fraction in listOf(0f, 0.5f, 1f)) {
                    val y = pad + fraction * (size.height - pad * 2)
                    drawLine(grid, Offset(pad, y), Offset(size.width - pad, y))
                }
                series.segments.forEach { segment ->
                    if (segment.size == 1) drawCircle(color, 2.dp.toPx(), position(segment.first()))
                    else {
                        val path = Path()
                        segment.forEachIndexed { index, point ->
                            val p = position(point)
                            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        // Fill each existing segment separately; gaps remain visible.
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(position(segment.last()).x, size.height - pad)
                            lineTo(position(segment.first()).x, size.height - pad)
                            close()
                        }
                        drawPath(fill, color.copy(alpha = .12f))
                        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            Text(stringResource(unitFormat, low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.history_time_value, 0L, 0L, 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.history_time_value, endMillis / 3600000, endMillis / 60000 % 60, endMillis / 1000 % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.chart_elapsed), style = MaterialTheme.typography.labelSmall)
    }
}
