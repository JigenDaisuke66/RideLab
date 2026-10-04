package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.*
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
                (ride.phase == RidePhase.ACTIVE || trainerConnected) &&
                ride.phase !in setOf(
            RidePhase.STARTING,
            RidePhase.STOPPING
        )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        // 明确指定背景色，避免系统默认 surface 和 surfaceContainer 偏色不一致
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar( // 替换掉 LargeTopAppBar
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Black, // 字重加粗，突出 Logo 感
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, // 透明背景，融入页面
                    scrolledContainerColor = MaterialTheme.colorScheme.surface // 滚动时变为不透明
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            /* ---------- Start Ride ---------- */

            item {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        if (ride.phase == RidePhase.ACTIVE) {
                            openRide()
                        } else {
                            start()
                        }
                    },
                    enabled = startEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = stringResource(
                            when (ride.phase) {
                                RidePhase.ACTIVE -> R.string.ui_return_ride
                                RidePhase.STARTING -> R.string.ride_starting
                                RidePhase.STOPPING -> R.string.ride_stopping
                                else -> R.string.ride_start
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!trainerConnected || !ready) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!trainerConnected) {
                            Surface( // 用 Surface 包裹，做成一个类似小 Tip 的样式
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_trainer_needed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        if (!ready) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.ride_service_waiting),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            /* ---------- Devices ---------- */

            item {
                OutlinedButton(onClick = openWorkouts, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.workout_library))
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface // 使用纯粹的表面色
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_devices),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        CompactDeviceRow(
                            title = stringResource(R.string.role_trainer),
                            deviceName = trainer.device?.name,
                            connected = trainerConnected,
                            onSelect = selectTrainer,
                            onDisconnect = disconnectTrainer
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        CompactDeviceRow(
                            title = stringResource(R.string.ui_heart_rate),
                            deviceName = hr.device?.name,
                            connected = hrConnected,
                            onSelect = selectHr,
                            onDisconnect = disconnectHr
                        )
                    }
                }
            }

            /* ---------- Summary ---------- */

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.ftp_label),
                        value = ftp.ftp?.let {
                            stringResource(R.string.power_value, it)
                        } ?: stringResource(R.string.history_no_data)
                    )

                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.body_weight),
                        value = ftp.weightKg?.let { stringResource(R.string.weight_value, it) }
                            ?: stringResource(R.string.history_no_data)
                    )
                }
            }

            /* ---------- Latest Ride ---------- */

            if (latest != null) {
                item {
                    Card(
                        onClick = openHistory,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface // 使用纯粹的表面色
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_recent_ride),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = formatter.format(Date(latest.session.startTimeEpochMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(stringResource(R.string.ui_recent_summary,
                                    latest.session.durationMillis / 60000,
                                    latest.averagePower?.let { stringResource(R.string.history_watts, it) }
                                        ?: stringResource(R.string.history_no_data)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace)
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                fontWeight = FontWeight.Medium
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
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}
