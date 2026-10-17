package com.isaac.ridelab.ui.ride

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.service.RideState
import com.isaac.ridelab.ui.screen.*
import com.isaac.ridelab.workout.*

/** Read-only projection of the existing owners; never another mutable ride session. */
data class RideUiState(val trainer: GattDeviceState, val heartRate: HeartRateState,
    val ride: RideState, val serviceReady: Boolean, val erg: ErgTargetUi,
    val workout: WorkoutState, val rampResult: RampTestResult?, val profile: FtpState)

/** Navigation/platform actions stay supplied by the shell/Activity. */
class RideNavigationActions(val start: () -> Unit, val appSettings: () -> Unit,
    val ftpSettings: () -> Unit)

/** Route is the single ViewModel-to-presentation adapter. No BLE or persistence ownership. */
@Composable
internal fun RideRoute(state: RideUiState, devices: DevicesViewModel, erg: ErgTargetViewModel,
    navigation: RideNavigationActions, modifier: Modifier = Modifier) {
    val trend by devices.liveTrend.collectAsState()
    RideScreen(trainer = state.trainer, hr = state.heartRate, ride = state.ride,
        ready = state.serviceReady, start = navigation.start, end = devices::endRide,
        send = devices::setTargetPower, appSettings = navigation.appSettings, modifier = modifier,
        ergTarget = state.erg, editTarget = erg::edit, rememberApplied = erg::rememberApplied,
        workout = state.workout, pauseWorkout = devices::pauseWorkout,
        resumeWorkout = devices::resumeWorkout, skipWorkout = devices::skipWorkout,
        endWorkout = devices::endWorkout, rampResult = state.rampResult, ftp = state.profile,
        acceptRamp = devices::acceptRampResult, keepRamp = devices::keepRampResult,
        ftpSettings = navigation.ftpSettings, pauseRide = devices::pauseRide, resumeRide = devices::resumeRide,
        liveTrend = trend)
}
