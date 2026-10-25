package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Speed
import com.isaac.ridelab.ui.theme.RideLabCard
import com.isaac.ridelab.ui.theme.MetricValueText
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


@Composable
private fun LiveMetric(
    title: Int,
    value: String,
    modifier: Modifier = Modifier
) {
    RideLabCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (title == R.string.ui_heart_rate) Icons.Rounded.FavoriteBorder else Icons.Rounded.Speed,
                contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(title), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        MetricValueText(value, style = MaterialTheme.typography.headlineMedium)
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
    modifier: Modifier = Modifier,
    ergTarget: ErgTargetUi = ErgTargetUi(loaded = true),
    editTarget: (Int) -> Unit = {},
    rememberApplied: (Int) -> Unit = {},
    workout: WorkoutState = WorkoutState(),
    pauseWorkout: () -> Unit = {}, resumeWorkout: () -> Unit = {},
    skipWorkout: () -> Unit = {}, endWorkout: () -> Unit = {},
    rampResult: RampTestResult? = null, ftp: FtpState = FtpState(),
    acceptRamp: () -> Unit = {}, keepRamp: () -> Unit = {}, ftpSettings: () -> Unit = {},
    pauseRide: () -> Unit = {}, resumeRide: () -> Unit = {},
    liveTrend: com.isaac.ridelab.ui.ride.LiveTrendState = com.isaac.ridelab.ui.ride.LiveTrendState()
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
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (ride.ongoing && workout.phase in setOf(WorkoutPhase.ACTIVE, WorkoutPhase.PAUSED))
                    WorkoutActions(workout, trainer.control.canWorkout, pauseWorkout, resumeWorkout, skipWorkout, endWorkout)
                else RideControls(ride, controlsReady && workout.phase != WorkoutPhase.PREPARING, start, end, appSettings,
                    showElapsed = false, pause = pauseRide, resume = resumeRide)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(8.dp))

            RideHeader(ride, elapsed)
            Spacer(Modifier.height(8.dp))
            RidePowerCard(live.trainerPowerWatts, noData)
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

            Spacer(Modifier.height(8.dp))

            com.isaac.ridelab.ui.ride.LiveRideCharts(liveTrend)
            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.history_field, stringResource(R.string.role_trainer),
                    stringResource(friendlyConnection(trainer.connection))),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.history_field, stringResource(R.string.ui_heart_rate),
                    stringResource(friendlyConnection(hr.connection))),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RideHeader(ride: RideState, elapsed: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            StatusPill(stringResource(when (ride.phase) {
                RidePhase.ACTIVE -> R.string.ride_active
                RidePhase.PAUSED -> R.string.ride_paused
                RidePhase.STARTING -> R.string.ride_starting
                RidePhase.STOPPING -> R.string.ride_stopping
                RidePhase.ERROR -> R.string.ride_error
                else -> R.string.ride_idle
            }), ride.phase == RidePhase.ACTIVE)
        }
        Text(elapsed, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RidePowerCard(watts: Int?, noData: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 144.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.ui_power), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (watts != null) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fontScale = LocalDensity.current.fontScale
                val fontSize = minOf(92f, maxWidth.value / fontScale / (watts.toString().length * .64f + 1f)).sp
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(watts.toString(), Modifier.alignByBaseline(), fontSize = fontSize,
                        lineHeight = fontSize * 1.08f, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.ui_watts), Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Text(noData, style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErgControls(
    trainer: GattDeviceState,
    preference: ErgTargetUi = ErgTargetUi(target = trainer.lastRequestedTarget ?: 100, loaded = true, profile = trainer.capabilities.powerRange),
    edit: (Int) -> Unit = {},
    rememberApplied: (Int) -> Unit = {},
    send: (Int) -> Unit
) {
    val state = trainer.control
    val profile = state.capabilities.powerRange
    val view = LocalView.current
    val target = preference.target
    var dialog by rememberSaveable(trainer.device?.address) { mutableStateOf(false) }
    var input by rememberSaveable(trainer.device?.address) { mutableStateOf("") }
    val ready = preference.loaded && preference.address == trainer.device?.address &&
        profile != null && preference.profile == profile
    val enabled = ready && trainer.connection.readyForRide() && state.canSend && profile?.accepts(target) == true
    if (dialog) {
        val valid = profile?.let { parseErgTarget(input, it) }
        AlertDialog(onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.erg_set_target)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                profile?.let { Text(stringResource(R.string.erg_available_range, it.minErgWatts, it.maxErgWatts))
                    Text(stringResource(R.string.control_increment, it.incrementWatts)) }
                OutlinedTextField(value = input,
                    onValueChange = { text -> if (text.all { it in '0'..'9' }) input = text },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.ui_watts)) }, isError = valid == null)
            } },
            confirmButton = { TextButton(enabled = valid != null && ready, onClick = { valid?.let(edit); dialog = false }) { Text(stringResource(R.string.erg_confirm)) } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.erg_cancel)) } })
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ui_erg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_requested_target,
                state.requestedWatts?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val inline = maxWidth >= 264.dp && LocalDensity.current.fontScale <= 1.25f
                val applyButton: @Composable (Modifier) -> Unit = { applyModifier ->
                    Button(
                        onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); rememberApplied(target); send(target) },
                        enabled = enabled, modifier = applyModifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp), shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.ui_apply_target), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalIconButton(
                            onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); profile?.let { edit(adjustedErgTarget(target, -5, it)) } },
                            enabled = ready && target > (profile?.minErgWatts ?: 0), modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.fec_minus)) }
                        OutlinedButton(onClick = { input = target.toString(); dialog = true }, enabled = ready,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(R.string.power_value, target), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        FilledTonalIconButton(
                            onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); profile?.let { edit(adjustedErgTarget(target, 5, it)) } },
                            enabled = ready && target < (profile?.maxErgWatts ?: 0), modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fec_plus)) }
                        if (inline) applyButton(Modifier.width(84.dp))
                    }
                    if (!inline) applyButton(Modifier.fillMaxWidth())
                }
            }
            if (preference.failed) Text(stringResource(R.string.erg_preference_error))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { TrainerControlSummary(state) }
            if (profile != null && !profile.accepts(target)) Text(stringResource(R.string.control_invalid_target))
        }
    }
}
