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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.locale.AppLocale

private enum class Destination(val title: Int) { HOME(R.string.ui_home), RIDE(R.string.ui_ride), HISTORY(R.string.history_title), SETTINGS(R.string.ftp_settings) }

@Composable
fun AppShell(devices: DevicesViewModel, history: HistoryViewModel, ftp: FtpViewModel, erg: ErgTargetViewModel,
    connectTrainer: (DiscoveredDevice) -> Unit, connectHr: (DiscoveredDevice) -> Unit,
    startRide: () -> Unit, scan: () -> Unit, appSettings: () -> Unit, bluetoothSettings: () -> Unit,
    language: (String) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var picker by rememberSaveable { mutableStateOf<Int?>(null) }
    val holder = rememberSaveableStateHolder()
    val trainer by devices.trainerState.collectAsState()
    val hr by devices.heartRateState.collectAsState()
    val ride by devices.rideState.collectAsState()
    val ready by devices.serviceReady.collectAsState()
    val scanState by devices.scanner.state.collectAsState()
    val historyState by history.state.collectAsState()
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
    Scaffold(bottomBar = {
        NavigationBar {
            Destination.entries.forEach { item ->
                NavigationBarItem(selected = destination == item, onClick = { destination = item },
                    icon = { NavigationGlyph(item) }, label = { Text(stringResource(item.title), maxLines = 1) })
            }
        }
    }) { padding ->
        holder.SaveableStateProvider(destination.name) {
            val modifier = Modifier.padding(padding)
            when (destination) {
                Destination.HOME -> HomeScreen(trainer, hr, ftpState, historyState, ride, ready,
                    { picker = R.string.role_trainer }, { picker = R.string.ui_heart_rate },
                    devices::disconnectTrainer, devices::disconnectHeartRate,
                    { destination = Destination.RIDE; startRide() }, { destination = Destination.RIDE },
                    { destination = Destination.HISTORY }, modifier)
                Destination.RIDE -> RideScreen(trainer, hr, ride, ready, startRide, devices::endRide, devices::sendFecTarget, appSettings, modifier, ergState, erg::edit, erg::rememberApplied)
                Destination.HISTORY -> HistoryScreen(historyState, { destination = Destination.HOME }, history::retry, chartState, history::loadCharts, modifier)
                Destination.SETTINGS -> SettingsDestination(ftpState, { value, done -> ftp.save(value, done) }, ftp::saveWeight, language, modifier)
            }
        }
    }
}

@Composable
private fun SettingsDestination(state: FtpState, save: (Int?, () -> Unit) -> Unit, saveWeight: (Double?, () -> Unit) -> Unit, language: (String) -> Unit, modifier: Modifier) {
    var editWeight by rememberSaveable { mutableStateOf(false) }
    if (editWeight) WeightDialog(state, { value -> saveWeight(value) { editWeight = false } }, { editWeight = false })
    var edit by rememberSaveable { mutableStateOf(false) }
    if (edit) SettingsScreen(state, { save(it) { edit = false } }, { edit = false })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ftp_settings), style = MaterialTheme.typography.headlineLarge) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ftp_label), style = MaterialTheme.typography.titleLarge)
            Text(state.ftp?.let { stringResource(R.string.power_value, it) } ?: stringResource(R.string.history_no_data), style = MaterialTheme.typography.headlineMedium)
            if (state.failed) Text(stringResource(R.string.ftp_error))
            Button(onClick = { edit = true }) { Text(stringResource(R.string.ui_edit_ftp)) }
        } } }
        item { Card(onClick = { editWeight = true }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.body_weight), style = MaterialTheme.typography.titleLarge)
            Text(state.weightKg?.let { stringResource(R.string.weight_value, it) } ?: stringResource(R.string.history_no_data))
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ui_language), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { language(AppLocale.CHINESE) }) { Text(stringResource(R.string.language_chinese)) }
            TextButton(onClick = { language(AppLocale.ENGLISH) }) { Text(stringResource(R.string.language_english)) }
        } } }
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
