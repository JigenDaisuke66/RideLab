package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.isaac.ridelab.ui.theme.MetricValueText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.rounded.MoreVert
import com.isaac.ridelab.ui.theme.StatusPill
import com.isaac.ridelab.ui.theme.RideLabCard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.service.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    trainer: GattDeviceState,
    hr: HeartRateState,
    ftp: FtpState,
    history: HistoryState,
    ride: RideState,
    ready: Boolean,
    selectTrainer: () -> Unit,
    selectHr: () -> Unit,
    disconnectTrainer: () -> Unit,
    disconnectHr: () -> Unit,
    start: () -> Unit,
    openRide: () -> Unit,
    openHistory: () -> Unit,
    modifier: Modifier = Modifier,
    openWorkouts: () -> Unit = {}
) {
    val latest = history.rides.firstOrNull {
        it.session.status == StoredRideStatus.COMPLETED
    }

    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val view = LocalView.current

    val trainerConnected = trainer.connection.readyForRide()
    val hrConnected = hr.connection.readyForRide()

    val startEnabled =
        ready &&
                (ride.ongoing || trainerConnected) &&
                ride.phase !in setOf(
            RidePhase.STARTING,
            RidePhase.STOPPING
        )


    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text(stringResource(R.string.home_prepare), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.home_prepare_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                RideLabCard(Modifier.fillMaxWidth()) {
                    CompactDeviceRow(stringResource(R.string.role_trainer), trainer.device?.name,
                        trainerConnected, selectTrainer, disconnectTrainer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                    CompactDeviceRow(stringResource(R.string.ui_heart_rate), hr.device?.name,
                        hrConnected, selectHr, disconnectHr)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        if (ride.ongoing) openRide() else start()
                    }, enabled = startEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(when (ride.phase) {
                            RidePhase.ACTIVE, RidePhase.PAUSED -> R.string.ui_return_ride
                            RidePhase.STARTING -> R.string.ride_starting
                            RidePhase.STOPPING -> R.string.ride_stopping
                            else -> R.string.ride_start
                        }), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    }
                    if (!trainerConnected) Text(stringResource(R.string.ui_trainer_needed),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!ready) Text(stringResource(R.string.ride_service_waiting),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = openWorkouts, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.workout_library), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard(stringResource(R.string.ftp_label),
                        ftp.ftp?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data),
                        Modifier.weight(1f))
                    MetricCard(stringResource(R.string.body_weight),
                        ftp.weightKg?.let { stringResource(R.string.weight_value, it) } ?: stringResource(R.string.history_no_data),
                        Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_recent_ride), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = openHistory) { Text(stringResource(R.string.history_title)) }
                }
            }
            item {
                when {
                    history.loading -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.history_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    history.failed -> Text(stringResource(R.string.history_failed),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latest != null -> RideLabCard(Modifier.fillMaxWidth(), onClick = openHistory) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(formatter.format(Date(latest.session.startTimeEpochMillis)),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.ui_recent_summary, latest.session.durationMillis / 60000,
                                    latest.averagePower?.let { stringResource(R.string.history_watts, it) }
                                        ?: stringResource(R.string.history_no_data)),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                    else -> RideLabCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ui_history_empty_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDeviceRow(
    title: String,
    deviceName: String?,
    connected: Boolean,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val view = LocalView.current
    ListItem(
        modifier = Modifier.clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onSelect() },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        overlineContent = {
            Text(text = title)
        },
        headlineContent = {
            Text(
                text = deviceName ?: stringResource(R.string.ui_no_device),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            StatusPill(stringResource(if (connected) R.string.ui_connected else R.string.ui_disconnected), connected)
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (connected) Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_actions))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.ui_disconnect), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDisconnect() })
                }
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    )
}


@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricValueText(value, style = MaterialTheme.typography.titleMedium)
    }
}
