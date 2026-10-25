package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Already-localized values remain one accessible phrase after baseline alignment. */
@Composable
internal fun MetricValueText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val split = value.indexOfLast { it.isWhitespace() }
    val number = if (split > 0) value.substring(0, split) else value
    val hasUnit = split > 0 && number.any { it.isDigit() } &&
        number.all { it.isDigit() || it.isWhitespace() || it in ".,+−-" }
    if (hasUnit) {
        Row(modifier.clearAndSetSemantics { text = AnnotatedString(value) },
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(number, Modifier.weight(1f, fill = false).alignByBaseline(),
                style = style, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.substring(split + 1), Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    } else {
        Text(value, modifier, style = style, fontWeight = FontWeight.SemiBold,
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
