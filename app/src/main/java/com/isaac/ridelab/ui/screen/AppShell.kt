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

import com.isaac.ridelab.ui.settings.*
import com.isaac.ridelab.ui.shared.OverlayControl
import com.isaac.ridelab.ui.ride.*

private enum class Destination(val title: Int) { HOME(R.string.ui_home), RIDE(R.string.ui_ride), HISTORY(R.string.history_title), SETTINGS(R.string.ftp_settings) }

@Composable
fun AppShell(devices: DevicesViewModel, history: HistoryViewModel, ftp: FtpViewModel, erg: ErgTargetViewModel,
    connectTrainer: (DiscoveredDevice) -> Unit, connectHr: (DiscoveredDevice) -> Unit,
    startRide: () -> Unit, scan: () -> Unit, appSettings: () -> Unit, bluetoothSettings: () -> Unit,
    language: (String) -> Unit, enableOverlay: () -> Unit = {}, exportCsv: (Long, Long) -> Unit = { _, _ -> },
    startWorkout: (com.isaac.ridelab.workout.WorkoutId) -> Unit = devices::startWorkout,
    appearanceModel: AppearanceViewModel) {
    val overlayPreferences by appearanceModel.overlayPreferences.collectAsState()
    val appearance by appearanceModel.state.collectAsState()
    val selectedLanguage by appearanceModel.language.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    LaunchedEffect(configuration) { appearanceModel.refreshLanguage() }
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
    LaunchedEffect(trainer.device?.address, trainer.capabilities.powerRange) {
        erg.select(trainer.device?.address, trainer.lastRequestedTarget, trainer.capabilities.powerRange)
    }
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
                    enableOverlay, { devices.setOverlay(false) }, overlayPreferences, appearanceModel::saveOverlay)
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Destination.entries.forEach { item ->
                NavigationBarItem(selected = destination == item, onClick = { library = false; destination = item },
                    icon = { NavigationGlyph(item) }, label = { Text(stringResource(item.title), maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
            }
        }
    }) { padding ->
        holder.SaveableStateProvider(destination.name) {
            val modifier = Modifier.padding(padding)
            if (library) {
                WorkoutLibraryScreen(ftpState, ready && trainer.control.canWorkout &&
                    ride.phase in setOf(com.isaac.ridelab.service.RidePhase.IDLE, com.isaac.ridelab.service.RidePhase.ERROR) && !workout.controlsTrainer,
                    { library = false }, { library = false; destination = Destination.SETTINGS },
                    { id -> library = false; destination = Destination.RIDE; startWorkout(id) }, modifier, control = trainer.control)
            } else
            when (destination) {
                Destination.HOME -> HomeScreen(trainer, hr, ftpState, historyState, ride, ready,
                    { picker = R.string.role_trainer }, { picker = R.string.ui_heart_rate },
                    devices::disconnectTrainer, devices::disconnectHeartRate,
                    { destination = Destination.RIDE; startRide() }, { destination = Destination.RIDE },
                    { destination = Destination.HISTORY }, modifier, { library = true })
                Destination.RIDE -> RideRoute(
                    RideUiState(trainer, hr, ride, ready, ergState, workout, rampResult, ftpState), devices, erg,
                    RideNavigationActions(startRide, appSettings, { destination = Destination.SETTINGS }), modifier)
                Destination.HISTORY -> HistoryScreen(historyState, { destination = Destination.HOME }, history::retry, chartState, history::loadCharts, modifier, deleting, history::deleteRide, csvExport == CsvExportState.WRITING, exportCsv,
                    backupControls = { HistoryBackupControls(history) })
                Destination.SETTINGS -> SettingsDestination(
                    SettingsUiState(ftpState, overlay, ready && ride.ongoing, appearance, selectedLanguage, overlayPreferences),
                    SettingsActions(ftp::save, ftp::saveWeight, language, enableOverlay,
                        { devices.setOverlay(false) }, appearanceModel::onAction, appearanceModel::saveOverlay), modifier)
            }
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
