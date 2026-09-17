package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.fec.*
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.service.*
import kotlinx.coroutines.delay

internal fun adjustedErgTarget(
    current: Int,
    step: Int
) = (current + step).coerceIn(
    FecProtocol.MIN_WATTS,
    FecProtocol.MAX_WATTS
)

@Composable
private fun LiveMetric(
    title: Int,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RideScreen(
    trainer: GattDeviceState,
    hr: HeartRateState,
    ride: RideState,
    ready: Boolean,
    start: () -> Unit,
    end: () -> Unit,
    send: (Int) -> Unit,
    appSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val live = LiveRideData.snapshot(
        trainer,
        hr,
        now
    )

    val noData = "--" // 使用更专业的占位符代替 "暂无数据" 提升高级感

    val seconds = ride.elapsedMs(now) / 1000

    val elapsed = stringResource(
        R.string.ride_elapsed,
        seconds / 3600,
        seconds / 60 % 60,
        seconds % 60
    )

    val trainerReady = trainer.connection.readyForRide()

    val controlsReady =
        ready &&
                (ride.phase == RidePhase.ACTIVE || trainerReady)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(24.dp))

            /* ---------- Header ---------- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ui_ride),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = when (ride.phase) {
                            RidePhase.ACTIVE -> stringResource(R.string.ride_active)
                            RidePhase.STARTING -> stringResource(R.string.ride_starting)
                            RidePhase.STOPPING -> stringResource(R.string.ride_stopping)
                            else -> stringResource(R.string.ride_idle)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ride.phase == RidePhase.ACTIVE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(28.dp))

            /* ---------- Main Power ---------- */
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.ui_power),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    if (live.trainerPowerWatts != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = live.trainerPowerWatts.toString(),
                                fontSize = 84.sp,
                                lineHeight = 84.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "W", // 简写更具科技感
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                            )
                        }
                    } else {
                        Text(
                            text = noData,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            /* ---------- Heart rate + cadence ---------- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LiveMetric(
                    title = R.string.ui_heart_rate,
                    value = live.heartRateBpm?.let {
                        stringResource(R.string.hr_value, it)
                    } ?: noData,
                    modifier = Modifier.weight(1f)
                )

                LiveMetric(
                    title = R.string.cadence,
                    value = live.cadenceRpm?.let {
                        stringResource(R.string.cadence_value, it)
                    } ?: noData,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            /* ---------- Compact connection status ---------- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.history_field,
                        stringResource(R.string.role_trainer),
                        stringResource(friendlyConnection(trainer.connection))
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(
                        R.string.history_field,
                        stringResource(R.string.ui_heart_rate),
                        stringResource(friendlyConnection(hr.connection))
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            /* ---------- ERG ---------- */
            ErgControls(
                trainer = trainer,
                send = send
            )

            Spacer(modifier = Modifier.height(32.dp))

            /* ---------- Ride actions ---------- */
            RideControls(
                state = ride,
                ready = controlsReady,
                start = start,
                end = end,
                settings = appSettings,
                showElapsed = false
            )

            Spacer(modifier = Modifier.height(100.dp)) // 为底部导航栏留出空间
        }
    }
}

@Composable
internal fun ErgControls(
    trainer: GattDeviceState,
    send: (Int) -> Unit
) {
    val state = trainer.fec

    var target by rememberSaveable(
        trainer.device?.address,
        state.lastTargetWatts
    ) {
        mutableIntStateOf(
            state.lastTargetWatts ?: 100
        )
    }

    val enabled = trainer.connection.readyForRide() && state.canSend

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_erg),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            when (state.status) {
                                FecStatus.WRITING -> R.string.ui_erg_sending
                                FecStatus.SENT -> R.string.ui_erg_sent
                                FecStatus.FAILED -> R.string.ui_erg_failed
                                FecStatus.READY -> R.string.ui_erg_ready
                                FecStatus.WAITING, FecStatus.SUBSCRIBING -> R.string.ui_erg_preparing
                                else -> R.string.ui_erg_unavailable
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "$target W",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { target = adjustedErgTarget(target, -5) },
                    enabled = enabled && target > FecProtocol.MIN_WATTS,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "-5W")
                }

                Button(
                    onClick = { send(target) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(R.string.ui_apply_target),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalIconButton(
                    onClick = { target = adjustedErgTarget(target, 5) },
                    enabled = enabled && target < FecProtocol.MAX_WATTS,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "+5W")
                }
            }
        }
    }
}
