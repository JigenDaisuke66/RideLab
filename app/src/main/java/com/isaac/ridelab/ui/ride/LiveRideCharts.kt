package com.isaac.ridelab.ui.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ui.chart.RideChart
import com.isaac.ridelab.ui.theme.RideLabCard

@Composable
fun LiveRideCharts(state: LiveTrendState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ride_live_charts), style = MaterialTheme.typography.titleMedium)
        val charts = state.charts
        RideLabCard(Modifier.fillMaxWidth()) {
            RideChart(charts.power, R.string.chart_power, R.string.history_watts, charts.endMillis, startMillis = state.startMillis)
        }
        RideLabCard(Modifier.fillMaxWidth()) {
            RideChart(charts.heartRate, R.string.chart_hr, R.string.history_bpm, charts.endMillis, startMillis = state.startMillis)
        }
        RideLabCard(Modifier.fillMaxWidth()) {
            RideChart(charts.cadence, R.string.chart_cadence, R.string.history_rpm, charts.endMillis, startMillis = state.startMillis)
        }
    }
}
