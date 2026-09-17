package com.isaac.ridelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.StoredRideStatus
import com.isaac.ridelab.ui.chart.RideChart
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    charts: ChartState,
    onLoadCharts: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId) { onLoadCharts(selectedId) }

    val back = {
        if (selectedId != null) selectedId = null else onClose()
    }
    BackHandler(onBack = back)

    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val noData = "--"

    val dateTimeFormat = remember(locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            locale
        )
    }

    val timeFormat = remember(locale) {
        DateFormat.getTimeInstance(
            DateFormat.SHORT,
            locale
        )
    }

    fun date(value: Long?) =
        value?.let { dateTimeFormat.format(Date(it)) } ?: noData

    fun time(value: Long?) =
        value?.let { timeFormat.format(Date(it)) } ?: noData


    fun elapsedStr(value: Long?): String {
        if (value == null) return noData
        val seconds = value / 1000
        return String.format(locale, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (selectedId == null) R.string.history_title else R.string.history_detail),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedId != null) {
                        IconButton(
                            onClick = { selectedId = null }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Crossfade(targetState = selectedId, label = "HistoryContent", modifier = Modifier.padding(innerPadding)) { currentSelectedId ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    state.loading -> item { LoadingView(R.string.history_loading) }

                    state.failed -> item {
                        ErrorView(R.string.history_failed, onRetry, R.string.history_retry)
                    }

                    currentSelectedId != null -> {
                        val entry = state.rides.find { it.session.id == currentSelectedId }
                        if (entry == null) {
                            item { Text(stringResource(R.string.history_missing)) }
                        } else {
                            val ride = entry.session

                            // 1. 概要卡片
                            item {
                                SectionCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = date(ride.startTimeEpochMillis),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

                                        Row(Modifier.fillMaxWidth()) {
                                            StatBox(stringResource(R.string.history_duration), elapsedStr(ride.durationMillis), Modifier.weight(1f))
                                            StatBox(
                                                stringResource(R.string.history_status),
                                                stringResource(if (ride.status == StoredRideStatus.ABORTED) R.string.history_aborted else R.string.history_completed),
                                                Modifier.weight(1f),
                                                isMonospace = false
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth()) {
                                            StatBox(
                                                stringResource(R.string.history_start),
                                                time(ride.startTimeEpochMillis),
                                                Modifier.weight(1f),
                                                isMonospace = false
                                            )
                                            StatBox(
                                                stringResource(R.string.history_end),
                                                time(ride.endTimeEpochMillis),
                                                Modifier.weight(1f),
                                                isMonospace = false
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. 运动表现卡片 (双列网格)
                            item {
                                SectionCard(title = "Performance") {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            StatBox(stringResource(R.string.history_avg_power), entry.averagePower?.let { "$it ${stringResource(R.string.history_watts)}" } ?: noData, Modifier.weight(1f))
                                            StatBox(stringResource(R.string.history_max_power), entry.maximumPower?.let { "$it ${stringResource(R.string.history_watts)}" } ?: noData, Modifier.weight(1f))
                                        }
                                        Row(Modifier.fillMaxWidth()) {
                                            StatBox(stringResource(R.string.history_avg_hr), entry.averageHeartRate?.let { "$it ${stringResource(R.string.history_bpm)}" } ?: noData, Modifier.weight(1f))
                                            StatBox(stringResource(R.string.history_max_hr), entry.maximumHeartRate?.let { "$it ${stringResource(R.string.history_bpm)}" } ?: noData, Modifier.weight(1f))
                                        }
                                        Row(Modifier.fillMaxWidth()) {
                                            StatBox(stringResource(R.string.history_avg_cadence), entry.averageCadence?.let { "$it ${stringResource(R.string.history_rpm)}" } ?: noData, Modifier.weight(1f))
                                            StatBox(stringResource(R.string.history_max_cadence), entry.maximumCadence?.let { "$it ${stringResource(R.string.history_rpm)}" } ?: noData, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            // 3. 高阶分析卡片
                            val data = charts.data.takeIf { charts.sessionId == currentSelectedId }
                            item {
                                when {
                                    charts.sessionId == currentSelectedId && charts.failed -> {
                                        ErrorView(R.string.chart_failed, { onLoadCharts(currentSelectedId) }, R.string.history_retry)
                                    }
                                    data == null -> LoadingView(R.string.history_loading)
                                    else -> {
                                        SectionCard(title = stringResource(R.string.analytics_title)) {
                                            val analytics = charts.analytics
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Row(Modifier.fillMaxWidth()) {
                                                    StatBox(stringResource(R.string.analytics_np), analytics?.np?.let { "$it W" } ?: noData, Modifier.weight(1f))
                                                    StatBox(stringResource(R.string.ftp_label), analytics?.ftp?.let { "$it W" } ?: noData, Modifier.weight(1f))
                                                }
                                                Row(Modifier.fillMaxWidth()) {
                                                    StatBox(stringResource(R.string.analytics_tss), analytics?.tss?.toString() ?: noData, Modifier.weight(1f))
                                                    StatBox(stringResource(R.string.analytics_if), analytics?.intensityFactor?.toString() ?: noData, Modifier.weight(1f))
                                                }

                                                Text(
                                                    text = stringResource(if (analytics?.ftp == null) R.string.analytics_ftp_needed else R.string.analytics_ftp_saved),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. 图表区
                            if (data != null) {
                                item {
                                    val end = maxOf(ride.durationMillis, data.endMillis)
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        RideChart(data.power, R.string.chart_power, R.string.history_watts, end)
                                        RideChart(data.heartRate, R.string.chart_hr, R.string.history_bpm, end)
                                        RideChart(data.cadence, R.string.chart_cadence, R.string.history_rpm, end)
                                    }
                                    Spacer(Modifier.height(40.dp))
                                }
                            }
                        }
                    }

                    state.rides.isEmpty() -> item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 列表展示
                    else -> items(state.rides, key = { it.session.id }) { entry ->
                        Card(
                            onClick = { selectedId = entry.session.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = date(entry.session.startTimeEpochMillis),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = elapsedStr(entry.session.durationMillis),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(if (entry.session.status == StoredRideStatus.ABORTED) R.string.history_aborted else R.string.history_completed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (entry.session.status == StoredRideStatus.ABORTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== 通用基础组件 ==========

@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = if (isMonospace) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LoadingView(textRes: Int) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(textRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorView(textRes: Int, onRetry: () -> Unit, retryTextRes: Int) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(textRes), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text(stringResource(retryTextRes))
            }
        }
    }
}