package com.isaac.ridelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.isaac.ridelab.ui.theme.RideLabCard
import com.isaac.ridelab.ui.theme.SettingRow
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.locale.AppLocale

private enum class Destination(val title: Int) { HOME(R.string.ui_home), RIDE(R.string.ui_ride), HISTORY(R.string.history_title), SETTINGS(R.string.ftp_settings) }

@Composable
fun AppShell(devices: DevicesViewModel, history: HistoryViewModel, ftp: FtpViewModel, erg: ErgTargetViewModel,
    connectTrainer: (DiscoveredDevice) -> Unit, connectHr: (DiscoveredDevice) -> Unit,
    startRide: () -> Unit, scan: () -> Unit, appSettings: () -> Unit, bluetoothSettings: () -> Unit,
    language: (String) -> Unit, enableOverlay: () -> Unit = {}, exportCsv: (Long, Long) -> Unit = { _, _ -> },
    startWorkout: (com.isaac.ridelab.workout.WorkoutId) -> Unit = devices::startWorkout) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var library by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<Int?>(null) }
    val holder = rememberSaveableStateHolder()
    val trainer by devices.trainerState.collectAsState()
    val hr by devices.heartRateState.collectAsState()
    val ride by devices.rideState.collectAsState()
    val view = LocalView.current
    DisposableEffect(view, ride.phase) {
        val previous = view.keepScreenOn
        view.keepScreenOn = ride.phase == com.isaac.ridelab.service.RidePhase.ACTIVE
        onDispose { view.keepScreenOn = previous }
    }
    val workout by devices.workoutState.collectAsState()
    val rampResult by devices.rampResult.collectAsState()
    LaunchedEffect(rampResult?.phase) {
        if (rampResult?.phase == com.isaac.ridelab.workout.RampReviewPhase.ACCEPTED) ftp.refresh()
    }
    val ready by devices.serviceReady.collectAsState()
    val overlay by devices.overlayState.collectAsState()
    val scanState by devices.scanner.state.collectAsState()
    val historyState by history.state.collectAsState()
    val csvExport by history.csvExport.collectAsState()
    val deleting by history.deleting.collectAsState()
    val chartState by history.charts.collectAsState()
    val ergState by erg.state.collectAsState()
    LaunchedEffect(trainer.device?.address) { erg.select(trainer.device?.address, trainer.fec.lastTargetWatts) }
    val ftpState by ftp.state.collectAsState()
    BackHandler(destination != Destination.HOME) { destination = Destination.HOME }
    picker?.let { role ->
        val isHr = role == R.string.ui_heart_rate
        DevicePicker(role, scanState, if (isHr) hr.device?.address else trainer.device?.address,
            if (isHr) hr.isActive else trainer.isActive, if (isHr) connectHr else connectTrainer,
            scan, devices.scanner::stop, appSettings, bluetoothSettings, { picker = null })
    }
    Scaffold(topBar = {
        if (destination == Destination.RIDE) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                OverlayControl(overlay, ready && ride.ongoing,
                    enableOverlay, { devices.setOverlay(false) })
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Destination.entries.forEach { item ->
                NavigationBarItem(selected = destination == item, onClick = { library = false; destination = item },
                    icon = { NavigationGlyph(item) }, label = { Text(stringResource(item.title), maxLines = 1) })
            }
        }
    }) { padding ->
        holder.SaveableStateProvider(destination.name) {
            val modifier = Modifier.padding(padding)
            if (library) {
                WorkoutLibraryScreen(ftpState, ready && trainer.fec.canSend &&
                    ride.phase in setOf(com.isaac.ridelab.service.RidePhase.IDLE, com.isaac.ridelab.service.RidePhase.ERROR) && !workout.controlsTrainer,
                    { library = false }, { library = false; destination = Destination.SETTINGS },
                    { id -> library = false; destination = Destination.RIDE; startWorkout(id) }, modifier)
            } else
            when (destination) {
                Destination.HOME -> HomeScreen(trainer, hr, ftpState, historyState, ride, ready,
                    { picker = R.string.role_trainer }, { picker = R.string.ui_heart_rate },
                    devices::disconnectTrainer, devices::disconnectHeartRate,
                    { destination = Destination.RIDE; startRide() }, { destination = Destination.RIDE },
                    { destination = Destination.HISTORY }, modifier, { library = true })
                Destination.RIDE -> RideScreen(trainer, hr, ride, ready, startRide, devices::endRide, devices::sendFecTarget, appSettings, modifier, ergState, erg::edit, erg::rememberApplied,
                    workout, devices::pauseWorkout, devices::resumeWorkout, devices::skipWorkout, devices::endWorkout,
                    rampResult, ftpState, devices::acceptRampResult, devices::keepRampResult,
                    { destination = Destination.SETTINGS }, devices::pauseRide, devices::resumeRide)
                Destination.HISTORY -> HistoryScreen(historyState, { destination = Destination.HOME }, history::retry, chartState, history::loadCharts, modifier, deleting, history::deleteRide, csvExport == CsvExportState.WRITING, exportCsv)
                Destination.SETTINGS -> SettingsDestination(ftpState, { value, done -> ftp.save(value, done) }, ftp::saveWeight, language, modifier,
                    overlay, ready && ride.ongoing,
                    enableOverlay, { devices.setOverlay(false) })
            }
        }
    }
}

@Composable
private fun SettingsDestination(state: FtpState, save: (Int?, () -> Unit) -> Unit, saveWeight: (Double?, () -> Unit) -> Unit,
    language: (String) -> Unit, modifier: Modifier, overlay: com.isaac.ridelab.ui.overlay.RideOverlayState,
    active: Boolean, enableOverlay: () -> Unit, disableOverlay: () -> Unit) {
    var editWeight by rememberSaveable { mutableStateOf(false) }
    if (editWeight) WeightDialog(state, { value -> saveWeight(value) { editWeight = false } }, { editWeight = false })
    var edit by rememberSaveable { mutableStateOf(false) }
    if (edit) SettingsScreen(state, { save(it) { edit = false } }, { edit = false })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ftp_settings), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleMedium) }
        item {
            Column {
                SettingRow(stringResource(R.string.ftp_label), state.ftp?.let { stringResource(R.string.power_value, it) }
                    ?: stringResource(R.string.history_no_data), { edit = true })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SettingRow(stringResource(R.string.body_weight), state.weightKg?.let { stringResource(R.string.weight_value, it) }
                    ?: stringResource(R.string.history_no_data), { editWeight = true })
                if (state.failed) Text(stringResource(R.string.ftp_error), color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(stringResource(R.string.ui_display), style = MaterialTheme.typography.titleMedium)
            OverlayControl(overlay, active, enableOverlay, disableOverlay)
            Text(stringResource(R.string.ui_overlay_opacity_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { AppearanceControls() }
        item {
            Text(stringResource(R.string.ui_language), style = MaterialTheme.typography.titleMedium)
            SettingRow(stringResource(R.string.language_chinese), "", { language(AppLocale.CHINESE) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(stringResource(R.string.language_english), "", { language(AppLocale.ENGLISH) })
        }
    }
}

@Composable
private fun NavigationGlyph(destination: Destination) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        val stroke = 2.dp.toPx()
        when (destination) {
            Destination.HOME -> {
                val p = Path().apply { moveTo(w*.1f,h*.45f); lineTo(w*.5f,h*.1f); lineTo(w*.9f,h*.45f); lineTo(w*.9f,h*.9f); lineTo(w*.1f,h*.9f); close() }
                drawPath(p,color,style=Stroke(stroke))
            }
            Destination.RIDE -> {
                val p = Path().apply { moveTo(w*.3f,h*.1f); lineTo(w*.85f,h*.5f); lineTo(w*.3f,h*.9f); close() }
                drawPath(p,color,style=Stroke(stroke))
            }
            Destination.HISTORY -> {
                drawCircle(color, w*.4f, style=Stroke(stroke))
                drawLine(color,Offset(w*.5f,h*.25f),Offset(w*.5f,h*.5f),stroke)
                drawLine(color,Offset(w*.5f,h*.5f),Offset(w*.7f,h*.65f),stroke)
            }
            Destination.SETTINGS -> {
                for (f in listOf(.25f,.5f,.75f)) drawLine(color,Offset(w*.1f,h*f),Offset(w*.9f,h*f),stroke)
                drawCircle(color,stroke,Offset(w*.35f,h*.25f)); drawCircle(color,stroke,Offset(w*.65f,h*.5f)); drawCircle(color,stroke,Offset(w*.4f,h*.75f))
            }
        }
    }
}

@Composable
private fun OverlayControl(state: com.isaac.ridelab.ui.overlay.RideOverlayState, active: Boolean,
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
