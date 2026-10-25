package com.isaac.ridelab.ui.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.repository.OverlayPreferences
import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.shared.OverlaySettingsDialog

/** Compact platform-action entry points; no ride state or connection ownership. */
@Composable
internal fun RideToolbar(state: RideOverlayState, active: Boolean, preferences: OverlayPreferences,
    enable: () -> Unit, disable: () -> Unit, save: (OverlayPreferences) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    val visible = state == RideOverlayState.VISIBLE
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconToggleButton(checked = visible, enabled = active, onCheckedChange = {
            if (visible) disable() else enable()
        }) {
            Icon(Icons.Rounded.PictureInPictureAlt,
                contentDescription = stringResource(if (visible) R.string.overlay_disable else R.string.overlay_enable),
                tint = if (visible) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_actions))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.overlay_adjust)) },
                    onClick = { menu = false; settings = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.ui_help)) },
                    onClick = { menu = false; help = true })
            }
        }
    }
    if (settings) OverlaySettingsDialog(preferences, save) { settings = false }
    if (help) AlertDialog(onDismissRequest = { help = false },
        title = { Text(stringResource(R.string.overlay_title)) },
        text = { Text(stringResource(when {
            state == RideOverlayState.FAILED -> R.string.overlay_failure
            !active -> R.string.overlay_need_ride
            else -> R.string.overlay_permission_note
        })) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.erg_confirm)) } })
}
