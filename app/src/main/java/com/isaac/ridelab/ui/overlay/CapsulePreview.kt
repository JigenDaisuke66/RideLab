package com.isaac.ridelab.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.repository.OverlayPreferences

/** Explicit example, isolated from BLE, services, ride state and recording. */
@Composable
fun CapsulePreview(preferences: OverlayPreferences) {
    Text(stringResource(R.string.capsule_preview), style = MaterialTheme.typography.labelMedium)
    val density = LocalDensity.current
    val screen = LocalConfiguration.current
    val fitted = CapsuleGeometry.fit(preferences.size,
        with(density) { (screen.screenWidthDp - 32).dp.roundToPx() },
        with(density) { 120.dp.roundToPx() }, density.density)
    val example = remember { OverlayReadout(LiveRideData(215, 92.0, 156), 2712, false) }
    val samples = remember {
        listOf(90, 100, 130, 150, 140, 180, 190, 230, 250, 220, 210, 215, 210, 215, 215)
            .mapIndexed { i, p -> OverlayTrend.Point(i * 1000L, p, 144 + i, 88.0 + i % 5) }
    }
    Box(Modifier.fillMaxWidth().height(124.dp).background(Color(0xFFE4E8EC))
        .horizontalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
        AndroidView(factory = { CapsuleView(it) },
            modifier = Modifier.size(with(density) { fitted.first.toDp() }, with(density) { fitted.second.toDp() }),
            update = { it.readout = example; it.samples = samples; it.alpha = (100 - preferences.transparency) / 100f })
    }
}
