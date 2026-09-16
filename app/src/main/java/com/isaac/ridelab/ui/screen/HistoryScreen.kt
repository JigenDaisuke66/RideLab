package com.isaac.ridelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.StoredRideStatus
import com.isaac.ridelab.ui.chart.RideChart
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(state: HistoryState, onClose: () -> Unit, onRetry: () -> Unit, charts: ChartState, onLoadCharts: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId) { onLoadCharts(selectedId) }
    val back = { if (selectedId != null) selectedId = null else onClose() }
    BackHandler(onBack = back)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val noData = stringResource(R.string.history_no_data)
    fun date(value: Long?) = value?.let { dateFormat.format(Date(it)) } ?: noData
    @Composable fun elapsed(value: Long?): String = if (value == null) noData else
        stringResource(R.string.history_time_value, value / 3600000, value / 60000 % 60, value / 1000 % 60)
    @Composable fun field(label: Int, value: String) {
        Text(stringResource(R.string.history_field, stringResource(label), value))
    }
    @Composable fun metric(label: Int, value: Double?, unit: Int) {
        field(label, value?.let { stringResource(unit, it) } ?: noData)
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = back) { Text(stringResource(R.string.history_back)) }
            Text(stringResource(if (selectedId == null) R.string.history_title else R.string.history_detail), style = MaterialTheme.typography.headlineSmall)
        }
        when {
            state.loading -> item { Text(stringResource(R.string.history_loading)) }
            state.failed -> item { Text(stringResource(R.string.history_failed)); TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) } }
            selectedId != null -> item {
                val entry = state.rides.find { it.session.id == selectedId }
                if (entry == null) Text(stringResource(R.string.history_missing)) else {
                    val ride = entry.session
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        field(R.string.history_start, date(ride.startTimeEpochMillis))
                        field(R.string.history_end, date(ride.endTimeEpochMillis))
                        field(R.string.history_duration, elapsed(ride.durationMillis))
                        field(R.string.history_status, stringResource(if (ride.status == StoredRideStatus.ABORTED) R.string.history_aborted else R.string.history_completed))
                        field(R.string.history_samples, stringResource(R.string.history_count, entry.sampleCount))
                        field(R.string.history_first, elapsed(entry.firstElapsedMillis))
                        field(R.string.history_last, elapsed(entry.lastElapsedMillis))
                        metric(R.string.history_avg_power, entry.averagePower, R.string.history_watts)
                        metric(R.string.history_max_power, entry.maximumPower?.toDouble(), R.string.history_watts)
                        metric(R.string.history_avg_hr, entry.averageHeartRate, R.string.history_bpm)
                        metric(R.string.history_max_hr, entry.maximumHeartRate?.toDouble(), R.string.history_bpm)
                        metric(R.string.history_avg_cadence, entry.averageCadence, R.string.history_rpm)
                        metric(R.string.history_max_cadence, entry.maximumCadence, R.string.history_rpm)
                        Text(stringResource(R.string.chart_gaps))
                        val data = charts.data.takeIf { charts.sessionId == selectedId }
                        when {
                            charts.sessionId == selectedId && charts.failed -> {
                                Text(stringResource(R.string.chart_failed))
                                TextButton(onClick = { onLoadCharts(selectedId) }) { Text(stringResource(R.string.history_retry)) }
                            }
                            data == null -> Text(stringResource(R.string.history_loading))
                            else -> {
                                Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleMedium)
                                val analytics = charts.analytics
                                metric(R.string.analytics_np, analytics?.np, R.string.history_watts)
                                metric(R.string.ftp_label, analytics?.ftp?.toDouble(), R.string.history_watts)
                                metric(R.string.analytics_if, analytics?.intensityFactor, R.string.analytics_ratio)
                                metric(R.string.analytics_tss, analytics?.tss, R.string.analytics_score)
                                Text(stringResource(if (analytics?.ftp == null) R.string.analytics_ftp_needed else R.string.analytics_ftp_saved))
                                Text(stringResource(R.string.analytics_coverage))
                                val end = maxOf(ride.durationMillis, data.endMillis)
                                RideChart(data.power, R.string.chart_power, R.string.history_watts, end)
                                RideChart(data.heartRate, R.string.chart_hr, R.string.history_bpm, end)
                                RideChart(data.cadence, R.string.chart_cadence, R.string.history_rpm, end)
                            }
                        }
                    }
                }
            }
            state.rides.isEmpty() -> item { Text(stringResource(R.string.history_empty)) }
            else -> items(state.rides, key = { it.session.id }) { entry ->
                Card(onClick = { selectedId = entry.session.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(date(entry.session.startTimeEpochMillis))
                        field(R.string.history_duration, elapsed(entry.session.durationMillis))
                        Text(stringResource(if (entry.session.status == StoredRideStatus.ABORTED) R.string.history_aborted else R.string.history_completed))
                        field(R.string.history_samples, stringResource(R.string.history_count, entry.sampleCount))
                    }
                }
            }
        }
    }
}

