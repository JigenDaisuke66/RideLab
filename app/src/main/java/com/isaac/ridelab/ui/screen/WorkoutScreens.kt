package com.isaac.ridelab.ui.screen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.control.TrainerControlState
import com.isaac.ridelab.workout.*
import com.isaac.ridelab.ui.theme.RideLabCard

internal fun workoutName(id: WorkoutId) = when (id) {
    WorkoutId.RECOVERY_30 -> R.string.workout_recovery
    WorkoutId.ENDURANCE_45 -> R.string.workout_endurance
    WorkoutId.SWEET_SPOT -> R.string.workout_sweet_spot
    WorkoutId.FTP_RAMP -> R.string.workout_ftp_ramp
    WorkoutId.INITIAL_FTP_RAMP -> R.string.workout_initial_ftp
}
private fun workoutPurpose(id: WorkoutId) = when (id) {
    WorkoutId.RECOVERY_30 -> R.string.workout_recovery_desc
    WorkoutId.ENDURANCE_45 -> R.string.workout_endurance_desc
    WorkoutId.SWEET_SPOT -> R.string.workout_sweet_desc
    WorkoutId.FTP_RAMP -> R.string.workout_ramp_desc
    WorkoutId.INITIAL_FTP_RAMP -> R.string.workout_initial_ftp_desc
}
private fun stepName(type: WorkoutStepType) = when (type) {
    WorkoutStepType.WARMUP -> R.string.workout_warmup
    WorkoutStepType.WORK -> R.string.workout_work
    WorkoutStepType.RECOVERY -> R.string.workout_recovery_step
    WorkoutStepType.COOLDOWN -> R.string.workout_cooldown
    WorkoutStepType.RAMP -> R.string.workout_ramp_step
    WorkoutStepType.FREE_RIDE -> R.string.workout_free_ride
}
@Composable
private fun workoutTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return stringResource(R.string.ride_elapsed, s / 3600, s / 60 % 60, s % 60)
}

@Composable
internal fun WorkoutLibraryScreen(ftp: FtpState, trainerReady: Boolean, close: () -> Unit,
    settings: () -> Unit, start: (WorkoutId) -> Unit, modifier: Modifier = Modifier,
    control: TrainerControlState = TrainerControlState()) {
    val profile = control.capabilities.powerRange
    var selected by rememberSaveable { mutableStateOf<WorkoutId?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    BackHandler { if (selected != null) selected = null else close() }
    val definition = selected?.let(BuiltInWorkouts::find)
    val allowed = trainerReady && control.capabilities.automaticErg && ftp.loaded && definition != null && (!definition.requiresFtp || ftp.ftp != null)
    if (confirm && definition != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.workout_start)) },
        text = { Text(stringResource(R.string.workout_auto_warning)) },
        confirmButton = { TextButton(enabled = allowed, onClick = {
            confirm = false; view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); start(definition.id)
        }) { Text(stringResource(R.string.workout_start)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.erg_cancel)) } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = { if (selected != null) selected = null else close() }) { Text(stringResource(R.string.back)) }
            Text(stringResource(definition?.id?.let(::workoutName) ?: R.string.workout_library), style = MaterialTheme.typography.headlineMedium)
        }
        if (definition == null) {
            item { Text(stringResource(R.string.workout_library_note)) }
            BuiltInWorkouts.all.forEach { workout -> item {
                RideLabCard(Modifier.fillMaxWidth(), onClick = { selected = workout.id }) {
                    Text(stringResource(workoutName(workout.id)), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(workoutPurpose(workout.id)), style = MaterialTheme.typography.bodyMedium)
                    Text(if (workout.rampTest) stringResource(R.string.workout_ramp_duration)
                        else stringResource(R.string.workout_duration, workout.steps.sumOf { it.durationSeconds } / 60))
                }
            } }
        } else {
            item {
                Text(stringResource(workoutPurpose(definition.id)))
                Text(if (definition.rampTest) stringResource(R.string.workout_ramp_duration)
                    else stringResource(R.string.workout_duration, definition.steps.sumOf { it.durationSeconds } / 60))
                if (definition.requiresFtp) {
                    DetailMetricRow(stringResource(R.string.workout_reference_ftp), ftp.ftp?.let {
                        stringResource(R.string.power_value, it)
                    } ?: stringResource(R.string.history_no_data))
                    Text(stringResource(R.string.workout_snapshot_note), style = MaterialTheme.typography.bodySmall)
                }
                if (profile != null && control.capabilities.automaticErg)
                    Text(stringResource(R.string.workout_clamp_note, profile.minErgWatts,
                        profile.maxErgWatts), style = MaterialTheme.typography.bodySmall)
                if (!control.capabilities.automaticErg) TrainerControlSummary(control)
            }
            definition.steps.forEachIndexed { index, step -> item {
                RideLabCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workout_preview_step, index + 1, stringResource(stepName(step.type))))
                    val watts = if (profile != null && (!definition.requiresFtp || ftp.ftp != null)) workoutWatts(step.target, ftp.ftp, profile) else null
                    DetailMetricRow(workoutTime(step.durationSeconds * 1000L),
                        watts?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data))
                    (step.target as? WorkoutTarget.FtpPercent)?.let {
                        Text(stringResource(R.string.workout_percent, it.fraction * 100), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } }
            if (definition.rampTest) item { Text(stringResource(if (definition.fixedRamp != null)
                R.string.workout_initial_ftp_protocol else R.string.workout_ramp_protocol)) }
            item {
                if (definition.requiresFtp && ftp.ftp == null) {
                    Text(stringResource(R.string.workout_ftp_required))
                    TextButton(onClick = settings) { Text(stringResource(R.string.ftp_settings)) }
                }
                if (!trainerReady) Text(stringResource(R.string.workout_trainer_required))
                Button(enabled = allowed, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.workout_start))
                }
            }
        }
    }
}

@Composable
internal fun WorkoutProgress(state: WorkoutState, settings: () -> Unit) {
    val definition = state.definition ?: return
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(workoutName(definition.id)), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(when (state.phase) {
            WorkoutPhase.IDLE -> R.string.workout_idle
            WorkoutPhase.PREPARING -> R.string.workout_preparing
            WorkoutPhase.ACTIVE -> R.string.workout_active
            WorkoutPhase.PAUSED -> R.string.workout_paused
            WorkoutPhase.COMPLETED -> R.string.workout_completed
            WorkoutPhase.CANCELLED -> R.string.workout_cancelled
            WorkoutPhase.ERROR -> R.string.workout_error
        }), color = MaterialTheme.colorScheme.primary)
        state.step?.let { step ->
            Text(if (definition.rampTest) stringResource(R.string.workout_ramp_index, state.stepIndex + 1)
                else stringResource(R.string.workout_index, state.stepIndex + 1, definition.steps.size))
            Text(stringResource(stepName(step.type)))
            DetailMetricRow(stringResource(R.string.workout_target), state.targetWatts?.let {
                stringResource(R.string.power_value, it)
            } ?: stringResource(R.string.history_no_data))
            DetailMetricRow(stringResource(R.string.workout_remaining), workoutTime(state.remainingMs))
            DetailMetricRow(stringResource(R.string.workout_elapsed), workoutTime(state.elapsedMs))
            state.nextStep?.let { next ->
                Text(stringResource(R.string.workout_next, stringResource(stepName(next.type)),
                    state.nextWatts?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data),
                    workoutTime(next.durationSeconds * 1000L)), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.issue?.let { issue ->
            Text(stringResource(when (issue) {
                WorkoutIssue.FTP_REQUIRED -> R.string.workout_ftp_required
                WorkoutIssue.TRAINER_UNAVAILABLE -> R.string.workout_disconnected
                WorkoutIssue.CONTROL_FAILED -> R.string.workout_control_failed
                WorkoutIssue.RESULT_FAILED -> R.string.workout_result_failed
                WorkoutIssue.RIDE_FAILED -> R.string.workout_ride_failed
            }), color = MaterialTheme.colorScheme.error)
            if (issue == WorkoutIssue.FTP_REQUIRED) TextButton(onClick = settings) { Text(stringResource(R.string.ftp_settings)) }
        }
        if (state.phase in setOf(WorkoutPhase.ACTIVE, WorkoutPhase.PAUSED)) {
            Text(stringResource(R.string.workout_pause_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Fixed Ride-screen footer: always reachable while scrolling live metrics. */
@Composable
internal fun WorkoutActions(state: WorkoutState, canSend: Boolean,
    pause: () -> Unit, resume: () -> Unit, skip: () -> Unit, end: () -> Unit) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val endLabel = if (state.definition?.rampTest == true) R.string.workout_end_test else R.string.workout_end
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text(stringResource(endLabel)) },
        text = { Text(stringResource(R.string.workout_end_note)) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); end() }) {
            Text(stringResource(endLabel))
        } }, dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.erg_cancel)) } })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = state.phase == WorkoutPhase.ACTIVE || canSend, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (state.phase == WorkoutPhase.PAUSED) resume() else pause()
                    }) { Text(stringResource(if (state.phase == WorkoutPhase.PAUSED) R.string.workout_resume else R.string.workout_pause)) }
                OutlinedButton(enabled = canSend && state.phase == WorkoutPhase.ACTIVE,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); skip() }) {
                    Text(stringResource(R.string.workout_skip))
                }
            }
            OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(endLabel)) }
    }
}

@Composable
internal fun RampResultPanel(result: RampTestResult, ftp: FtpState, accept: () -> Unit, keep: () -> Unit) {
    var confirm by rememberSaveable(result.sessionId) { mutableStateOf(false) }
    val view = LocalView.current
    val noData = stringResource(R.string.history_no_data)
    val editable = result.phase in setOf(RampReviewPhase.READY, RampReviewPhase.FAILED)
    if (confirm && result.estimatedFtp != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.workout_use_estimate, result.estimatedFtp)) },
        text = { Text(stringResource(R.string.workout_replace_ftp)) },
        confirmButton = { TextButton(enabled = editable, onClick = { confirm = false; view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); accept() }) {
            Text(stringResource(R.string.erg_confirm))
        } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.erg_cancel)) } })
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.workout_result_title), style = MaterialTheme.typography.titleLarge)
        DetailMetricRow(stringResource(R.string.workout_best60), result.best60Watts?.let { stringResource(R.string.history_watts, it) } ?: noData)
        DetailMetricRow(stringResource(R.string.workout_estimated_ftp), result.estimatedFtp?.let { stringResource(R.string.power_value, it) } ?: noData)
        DetailMetricRow(stringResource(R.string.workout_current_ftp), ftp.ftp?.let { stringResource(R.string.power_value, it) } ?: noData)
        DetailMetricRow(stringResource(R.string.workout_difference), if (result.estimatedFtp != null && ftp.ftp != null)
            stringResource(R.string.workout_difference_value, result.estimatedFtp - ftp.ftp) else noData)
        Text(stringResource(R.string.workout_result_note), style = MaterialTheme.typography.bodySmall)
        if (result.best60Watts == null) Text(stringResource(R.string.workout_insufficient))
        else if (!result.canAccept) Text(stringResource(R.string.workout_result_bounds))
        Text(stringResource(when (result.phase) {
            RampReviewPhase.READY -> R.string.workout_result_review
            RampReviewPhase.APPLYING -> R.string.workout_result_saving
            RampReviewPhase.ACCEPTED -> R.string.workout_result_saved
            RampReviewPhase.KEPT -> R.string.workout_result_kept
            RampReviewPhase.FAILED -> R.string.ftp_error
        }))
        if (editable) {
            if (result.estimatedFtp != null) Button(enabled = result.canAccept && ftp.loaded,
                onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workout_use_estimate, result.estimatedFtp))
            }
            OutlinedButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); keep() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workout_keep_ftp))
            }
        }
    }
}
