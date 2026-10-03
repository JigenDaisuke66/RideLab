package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.analytics.RideInsights
import com.isaac.ridelab.analytics.SensorCoverage
import com.isaac.ridelab.data.model.RideHistoryEntry
import com.isaac.ridelab.ui.chart.RideChart
import com.isaac.ridelab.ui.theme.*

internal enum class DetailSensor { POWER, CADENCE, HEART_RATE }

@Composable
internal fun DetailMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun detailDuration(millis: Long?): String {
    if (millis == null) return stringResource(R.string.history_no_data)
    val seconds = millis / 1000
    return stringResource(R.string.ride_elapsed, seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
private fun coverage(coverage: SensorCoverage?): String = coverage?.percent?.let {
    stringResource(R.string.detail_percent, it)
} ?: stringResource(R.string.history_no_data)

@Composable
internal fun DetailSensorPanel(sensor: DetailSensor, entry: RideHistoryEntry, state: ChartState) {
    val noData = stringResource(R.string.history_no_data)
    val insights = state.insights
    val (title, unit, color) = when (sensor) {
        DetailSensor.POWER -> Triple(R.string.chart_power, R.string.history_watts, RideColors.accent)
        DetailSensor.CADENCE -> Triple(R.string.chart_cadence, R.string.history_rpm, RideColors.cadence)
        DetailSensor.HEART_RATE -> Triple(R.string.chart_hr, R.string.history_bpm, RideColors.heartRate)
    }
    val average = when (sensor) {
        DetailSensor.POWER -> entry.averagePower
        DetailSensor.CADENCE -> entry.averageCadence
        DetailSensor.HEART_RATE -> entry.averageHeartRate
    }
    val maximum = when (sensor) {
        DetailSensor.POWER -> entry.maximumPower?.toDouble()
        DetailSensor.CADENCE -> entry.maximumCadence
        DetailSensor.HEART_RATE -> entry.maximumHeartRate?.toDouble()
    }
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = color)
        Row(Modifier.fillMaxWidth()) {
            MetricTile(stringResource(R.string.detail_average), average?.let { stringResource(unit, it) } ?: noData, Modifier.weight(1f))
            MetricTile(stringResource(R.string.detail_maximum), maximum?.let { stringResource(unit, it) } ?: noData, Modifier.weight(1f))
        }
        state.data?.let { data ->
            val series = when (sensor) {
                DetailSensor.POWER -> data.power
                DetailSensor.CADENCE -> data.cadence
                DetailSensor.HEART_RATE -> data.heartRate
            }
            RideChart(series, title, unit, maxOf(entry.session.durationMillis, data.endMillis), showTitle = false)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
        if (sensor == DetailSensor.POWER) {
            val metrics = state.analytics
            DetailMetricRow(stringResource(R.string.detail_best_20m),
                state.powerAnalysis?.efforts?.find { it.seconds == 1200 }?.watts?.let { stringResource(R.string.history_watts, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.analytics_np), metrics?.np?.let { stringResource(R.string.history_watts, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.analytics_if), metrics?.intensityFactor?.let { stringResource(R.string.analytics_ratio, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.analytics_tss), metrics?.tss?.let { stringResource(R.string.analytics_score, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.ftp_label), metrics?.ftp?.let { stringResource(R.string.power_value, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.detail_average_wkg), insights?.averageWattsPerKg?.let { stringResource(R.string.watts_per_kg, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.detail_work), insights?.workKilojoules?.let { stringResource(R.string.detail_kj, it) } ?: noData)
            DetailMetricRow(stringResource(R.string.detail_zero_power), detailDuration(insights?.zeroPowerMillis))
            Text(stringResource(if (metrics?.ftp == null) R.string.analytics_ftp_needed else R.string.analytics_ftp_saved),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.detail_work_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sensor == DetailSensor.CADENCE) {
            DetailMetricRow(stringResource(R.string.detail_pedaling), detailDuration(insights?.pedalingMillis))
        }
        val sensorCoverage = when (sensor) {
            DetailSensor.POWER -> insights?.powerCoverage
            DetailSensor.CADENCE -> insights?.cadenceCoverage
            DetailSensor.HEART_RATE -> insights?.heartRateCoverage
        }
        DetailMetricRow(stringResource(R.string.detail_coverage), coverage(sensorCoverage))
        if (sensorCoverage?.percent?.let { it < 99 } == true)
            Text(stringResource(R.string.detail_partial), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CadenceDistribution(insights: RideInsights?) {
    val labels = listOf(R.string.detail_cadence_0, R.string.detail_cadence_low, R.string.detail_cadence_60,
        R.string.detail_cadence_80, R.string.detail_cadence_100)
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_cadence_distribution), style = MaterialTheme.typography.titleMedium)
        if (insights?.cadenceBands == null) Text(stringResource(R.string.history_no_data)) else {
            insights.cadenceBands.forEach { band ->
                DetailMetricRow(stringResource(labels[band.index]), stringResource(R.string.detail_distribution_value,
                    detailDuration(band.millis), band.percent))
                LinearProgressIndicator(progress = { (band.percent / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp), color = RideColors.cadence,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.detail_cadence_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DetailRecordingInfo(entry: RideHistoryEntry, insights: RideInsights?) {
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_recording), style = MaterialTheme.typography.titleMedium)
        DetailMetricRow(stringResource(R.string.history_samples), entry.sampleCount.toString())
        DetailMetricRow(stringResource(R.string.detail_first_sample), detailDuration(entry.firstElapsedMillis))
        DetailMetricRow(stringResource(R.string.detail_last_sample), detailDuration(entry.lastElapsedMillis))
        DetailMetricRow(stringResource(R.string.detail_distance), insights?.recordedDistanceKm?.let {
            stringResource(R.string.detail_km, it)
        } ?: stringResource(R.string.history_no_data))
        DetailMetricRow(stringResource(R.string.detail_speed_coverage), coverage(insights?.speedCoverage))
        Text(stringResource(R.string.detail_coverage_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
