package com.isaac.ridelab.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.control.*

/** Presentation mapping only. Capability/protocol decisions belong to the GATT owner. */
@Composable
internal fun TrainerControlSummary(state: TrainerControlState) {
    Text(stringResource(when (state.capabilities.protocol) {
        ControlProtocol.FEC -> R.string.control_fec
        ControlProtocol.FTMS -> R.string.control_ftms
        ControlProtocol.NONE -> R.string.control_measurement_only
    }), style = MaterialTheme.typography.bodySmall)
    state.capabilities.powerRange?.let {
        Text(stringResource(R.string.erg_available_range, it.minErgWatts, it.maxErgWatts), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.control_increment, it.incrementWatts), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(when (state.issue) {
        ControlIssue.RANGE_UNAVAILABLE -> R.string.control_range_unknown
        ControlIssue.REQUEST_REJECTED -> R.string.control_request_failed
        ControlIssue.TARGET_REJECTED -> R.string.control_target_rejected
        ControlIssue.INVALID_TARGET -> R.string.control_invalid_target
        ControlIssue.TIMEOUT -> R.string.control_timeout
        ControlIssue.PERMISSION_LOST -> R.string.control_permission_lost
        ControlIssue.MALFORMED_RESPONSE, ControlIssue.TRANSPORT -> R.string.ui_erg_failed
        else -> when (state.status) {
            ControlStatus.REQUESTING -> R.string.control_requesting
            ControlStatus.WRITING -> R.string.ui_erg_sending
            ControlStatus.TRANSPORT_SENT -> R.string.ui_erg_sent
            ControlStatus.ACCEPTED -> R.string.control_accepted
            ControlStatus.READY -> R.string.ui_erg_ready
            ControlStatus.PREPARING -> R.string.ui_erg_preparing
            ControlStatus.FAILED -> R.string.ui_erg_failed
            else -> R.string.ui_erg_unavailable
        }
    }), style = MaterialTheme.typography.bodySmall)
    if (state.capabilities.supportsTargetPower && !state.capabilities.automaticErg)
        Text(stringResource(R.string.control_manual_only), style = MaterialTheme.typography.bodySmall)
    if (state.capabilities.limitSource == LimitSource.CONSERVATIVE)
        Text(stringResource(R.string.control_conservative), style = MaterialTheme.typography.bodySmall)
}
