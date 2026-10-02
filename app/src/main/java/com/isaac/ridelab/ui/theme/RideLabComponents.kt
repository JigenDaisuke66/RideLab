package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object RideSpacing {
    val page = 20.dp
    val section = 24.dp
    val gap = 12.dp
}

@Composable
fun RideLabCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick == null) Card(modifier, shape = MaterialTheme.shapes.large, colors = colors,
        border = border, elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    } else Card(onClick, modifier, shape = MaterialTheme.shapes.large, colors = colors,
        border = border, elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier, shape = CircleShape, color = color.copy(alpha = .1f),
        border = BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(6.dp), CircleShape, color = color) {}
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
