package com.isaac.ridelab.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.isaac.ridelab.R

@Composable
internal fun OverlayControl(state: com.isaac.ridelab.ui.overlay.RideOverlayState, active: Boolean,
    enable: () -> Unit, disable: () -> Unit) {
    val visible = state == com.isaac.ridelab.ui.overlay.RideOverlayState.VISIBLE
    var help by remember { mutableStateOf(false) }
    val note = stringResource(when {
        state == com.isaac.ridelab.ui.overlay.RideOverlayState.FAILED -> R.string.overlay_failure
        !active -> R.string.overlay_need_ride
        else -> R.string.overlay_permission_note
    })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = if (visible) disable else enable, enabled = active) {
            Text(stringResource(if (visible) R.string.overlay_disable else R.string.overlay_enable))
        }
        TextButton(onClick = { help = true }) { Text(stringResource(R.string.ui_help)) }
    }
    if (help) AlertDialog(onDismissRequest = { help = false },
        title = { Text(stringResource(R.string.overlay_title)) }, text = { Text(note) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.erg_confirm)) } })
}
