package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.isaac.ridelab.ui.theme.MetricTile
import com.isaac.ridelab.ui.theme.StatusPill
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
import com.isaac.ridelab.workout.*

internal fun adjustedErgTarget(
    current: Int,
    step: Int
) = (current + step).coerceIn(
    ThinkRiderX2MaxProfile.minErgWatts,
    ThinkRiderX2MaxProfile.maxErgWatts
)

@Composable
private fun LiveMetric(
    title: Int,
    value: String,
    modifier: Modifier = Modifier
) {
    MetricTile(stringResource(title), value, modifier)
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
    modifier: Modifier = Modifier,
    ergTarget: ErgTargetUi = ErgTargetUi(loaded = true),
    editTarget: (Int) -> Unit = {},
    rememberApplied: (Int) -> Unit = {},
    workout: WorkoutState = WorkoutState(),
    pauseWorkout: () -> Unit = {}, resumeWorkout: () -> Unit = {},
    skipWorkout: () -> Unit = {}, endWorkout: () -> Unit = {},
    rampResult: RampTestResult? = null, ftp: FtpState = FtpState(),
    acceptRamp: () -> Unit = {}, keepRamp: () -> Unit = {}, ftpSettings: () -> Unit = {},
    pauseRide: () -> Unit = {}, resumeRide: () -> Unit = {}
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

    val noData = stringResource(R.string.history_no_data)

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
                (ride.ongoing || trainerReady)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (ride.ongoing && workout.phase in setOf(WorkoutPhase.ACTIVE, WorkoutPhase.PAUSED))
                    WorkoutActions(workout, trainer.fec.canSend, pauseWorkout, resumeWorkout, skipWorkout, endWorkout)
                else RideControls(ride, controlsReady && workout.phase != WorkoutPhase.PREPARING, start, end, appSettings,
                    showElapsed = false, pause = pauseRide, resume = resumeRide)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(8.dp))

            /* ---------- Header ---------- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    StatusPill(
                        text = when (ride.phase) {
                            RidePhase.ACTIVE -> stringResource(R.string.ride_active)
                            RidePhase.PAUSED -> stringResource(R.string.ride_paused)
                            RidePhase.STARTING -> stringResource(R.string.ride_starting)
                            RidePhase.STOPPING -> stringResource(R.string.ride_stopping)
                            RidePhase.ERROR -> stringResource(R.string.ride_error)
                            else -> stringResource(R.string.ride_idle)
                        },
                        active = ride.phase == RidePhase.ACTIVE
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

            Spacer(Modifier.height(12.dp))

            /* ---------- Main Power ---------- */
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                                fontSize = 72.sp,
                                lineHeight = 76.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.ui_watts),
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

            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(12.dp))

            /* ---------- ERG ---------- */
            if (workout.definition != null) {
                WorkoutProgress(workout, ftpSettings)
                Spacer(Modifier.height(12.dp))
            }
            if (rampResult != null) {
                RampResultPanel(rampResult, ftp, acceptRamp, keepRamp)
                Spacer(Modifier.height(12.dp))
            }
            if (workout.controlsTrainer || ride.phase == RidePhase.STOPPING) Text(stringResource(
                if (ride.phase == RidePhase.STOPPING) R.string.ride_stopping else R.string.workout_controls_target),
                style = MaterialTheme.typography.bodyMedium)
            else ErgControls(
                trainer = trainer,
                preference = ergTarget,
                edit = editTarget,
                rememberApplied = rememberApplied,
                send = send
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun ErgControls(
    trainer: GattDeviceState,
    preference: ErgTargetUi = ErgTargetUi(target = trainer.fec.lastTargetWatts ?: 100, loaded = true),
    edit: (Int) -> Unit = {},
    rememberApplied: (Int) -> Unit = {},
    send: (Int) -> Unit
) {
    val state = trainer.fec
    val view = LocalView.current
    val target = preference.target
    var dialog by rememberSaveable(trainer.device?.address) { mutableStateOf(false) }
    var input by rememberSaveable(trainer.device?.address) { mutableStateOf("") }
    val ready = preference.loaded && preference.address == trainer.device?.address
    val enabled = ready && trainer.connection.readyForRide() && state.canSend
    if (dialog) {
        val valid = parseErgTarget(input)
        AlertDialog(onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.erg_set_target)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.erg_available_range, ThinkRiderX2MaxProfile.minErgWatts, ThinkRiderX2MaxProfile.maxErgWatts))
                OutlinedTextField(value = input,
                    onValueChange = { text -> if (text.all { it in '0'..'9' }) input = text },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.ui_watts)) }, isError = valid == null)
            } },
            confirmButton = { TextButton(enabled = valid != null && ready, onClick = { valid?.let(edit); dialog = false }) { Text(stringResource(R.string.erg_confirm)) } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.erg_cancel)) } })
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_erg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_requested_target,
                state.lastTargetWatts?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data)),
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); edit(adjustedErgTarget(target, -5)) }, enabled = ready && target > ThinkRiderX2MaxProfile.minErgWatts, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.fec_minus))
                }
                OutlinedButton(onClick = { input = target.toString(); dialog = true }, enabled = ready, modifier = Modifier.weight(1f).padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.power_value, target), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                FilledTonalIconButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); edit(adjustedErgTarget(target, 5)) }, enabled = ready && target < ThinkRiderX2MaxProfile.maxErgWatts, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fec_plus))
                }
            }
            Button(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); rememberApplied(target); send(target) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.ui_apply_target), fontWeight = FontWeight.Bold)
            }
            if (preference.failed) Text(stringResource(R.string.erg_preference_error))
            Text(stringResource(when (state.status) {
                FecStatus.WRITING -> R.string.ui_erg_sending
                FecStatus.SENT -> R.string.ui_erg_sent
                FecStatus.FAILED -> R.string.ui_erg_failed
                FecStatus.READY -> R.string.ui_erg_ready
                FecStatus.WAITING, FecStatus.SUBSCRIBING -> R.string.ui_erg_preparing
                else -> R.string.ui_erg_unavailable
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
