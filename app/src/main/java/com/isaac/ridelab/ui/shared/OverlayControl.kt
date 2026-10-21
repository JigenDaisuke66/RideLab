package com.isaac.ridelab.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R

@Composable
internal fun OverlayControl(state: com.isaac.ridelab.ui.overlay.RideOverlayState, active: Boolean,
    enable: () -> Unit, disable: () -> Unit,
    preferences: com.isaac.ridelab.data.repository.OverlayPreferences = com.isaac.ridelab.data.repository.OverlayPreferences(),
    save: (com.isaac.ridelab.data.repository.OverlayPreferences) -> Unit = {}) {
    val visible = state == com.isaac.ridelab.ui.overlay.RideOverlayState.VISIBLE
    var settings by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val note = stringResource(when {
        state == com.isaac.ridelab.ui.overlay.RideOverlayState.FAILED -> R.string.overlay_failure
        !active -> R.string.overlay_need_ride
        else -> R.string.overlay_permission_note
    })
    Column(Modifier.fillMaxWidth()) {
        Button(onClick = if (visible) disable else enable, enabled = active, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (visible) R.string.overlay_disable else R.string.overlay_enable))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { settings = true }) { Text(stringResource(R.string.overlay_adjust)) }
        TextButton(onClick = { help = true }) { Text(stringResource(R.string.ui_help)) }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false },
        title = { Text(stringResource(R.string.overlay_adjust)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            com.isaac.ridelab.ui.overlay.CapsulePreview(preferences)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.overlay_transparency, preferences.transparency))
            Slider(preferences.transparency.toFloat(), { save(preferences.copy(transparency = it.toInt())) }, valueRange = 0f..80f)
            Text(stringResource(R.string.capsule_size, preferences.size))
            Slider(preferences.size.toFloat(), { save(preferences.copy(size = it.toInt())) }, valueRange = 60f..140f)
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.erg_confirm)) } })
    if (help) AlertDialog(onDismissRequest = { help = false },
        title = { Text(stringResource(R.string.overlay_title)) }, text = { Text(note) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.erg_confirm)) } })
}
