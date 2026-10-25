package com.isaac.ridelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.isaac.ridelab.ui.theme.RideLabCard
import com.isaac.ridelab.ui.theme.StatusPill
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.StoredRideStatus
import java.text.DateFormat
import java.util.Date
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    charts: ChartState,
    onLoadCharts: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    deleting: Boolean = false,
    onDelete: (Long, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    exporting: Boolean = false,
    onExport: (Long, Long) -> Unit = { _, _ -> },
    backupControls: @Composable () -> Unit = {}
) {
    val view = LocalView.current
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteFailed by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toggledGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
    val zone = ZoneId.systemDefault()
    val groups = remember(state.rides, zone) { groupRideHistory(state.rides, zone) }
    fun toggleGroup(key: String) {
        toggledGroups = if (key in toggledGroups) toggledGroups - key else toggledGroups + key
    }
    LaunchedEffect(selectedId) { onLoadCharts(selectedId) }

    val back = {
        if (selectedId != null) selectedId = null else onClose()
    }
    BackHandler(onBack = back)

    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val noData = stringResource(R.string.history_no_data)

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

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete)) },
            text = { Column {
                Text(stringResource(R.string.history_delete_message,
                    date(state.rides.find { it.session.id == id }?.session?.startTimeEpochMillis)))
                if (deleteFailed) Text(stringResource(R.string.history_delete_failed), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !deleting, onClick = {
                deleteFailed = false
                onDelete(id) { success ->
                    if (success) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        pendingDelete = null
                        if (selectedId == id) selectedId = null
                    } else deleteFailed = true
                }
            }) { Text(stringResource(if (deleting) R.string.history_deleting else R.string.history_delete)) } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = null }) {
                Text(stringResource(R.string.erg_cancel))
            } }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (selectedId == null) R.string.history_title else R.string.history_detail),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    backupControls()
                    if (selectedId != null && state.rides.any { it.session.id == selectedId }) {
                        TextButton(enabled = !deleting, onClick = { deleteFailed = false; pendingDelete = selectedId }) {
                            Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
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
        Crossfade(targetState = selectedId, animationSpec = tween(180), label = "HistoryContent", modifier = Modifier.padding(innerPadding)) { currentSelectedId ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
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

                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = !exporting && !deleting,
                                        onClick = { onExport(ride.id, ride.startTimeEpochMillis) }) {
                                        Text(stringResource(if (exporting) R.string.csv_exporting else R.string.csv_export))
                                    }
                                }
                            }
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

                            val detail = charts.takeIf { it.sessionId == currentSelectedId }
                            when {
                                detail?.failed == true -> item {
                                    ErrorView(R.string.chart_failed, { onLoadCharts(currentSelectedId) }, R.string.history_retry)
                                }
                                detail?.data == null -> item { LoadingView(R.string.history_loading) }
                                else -> {
                                    item { RideEnergyPanel(detail.insights) }
                                    item { DetailSensorPanel(DetailSensor.POWER, entry, detail) }
                                    detail.powerAnalysis?.let { analysis ->
                                        item { PowerZonesView(analysis) }
                                    }
                                    item { DetailSensorPanel(DetailSensor.CADENCE, entry, detail) }
                                    item { CadenceDistribution(detail.insights) }
                                    item { DetailSensorPanel(DetailSensor.HEART_RATE, entry, detail) }
                                    detail.powerAnalysis?.let { analysis ->
                                        item { BestEffortsView(analysis, ride.bodyWeightKg) }
                                        item { PowerDurationView(analysis) }
                                    }
                                    item { DetailRecordingInfo(entry, detail.insights) }
                                    item { Spacer(Modifier.height(24.dp)) }
                                }
                            }
                        }
                    }

                    state.rides.isEmpty() -> item {
                        HistoryEmptyState()
                    }

                    // 列表展示
                    else -> groups.forEach { year ->
                        val yearKey = "year-${year.year}"
                        val yearExpanded = (year.year == groups.first().year) xor (yearKey in toggledGroups)
                        item(key = yearKey) {
                            HistoryGroupHeader(stringResource(R.string.history_group_year, year.year),
                                year.rideCount, yearExpanded, false) { toggleGroup(yearKey) }
                        }
                        if (yearExpanded) year.months.forEach { month ->
                            val monthKey = "month-${year.year}-${month.month}"
                            val monthExpanded = (year.year == groups.first().year && month.month == year.months.first().month) xor
                                (monthKey in toggledGroups)
                            item(key = monthKey) {
                                HistoryGroupHeader(Month.of(month.month).getDisplayName(TextStyle.FULL, locale),
                                    month.rides.size, monthExpanded, true) { toggleGroup(monthKey) }
                            }
                            if (monthExpanded) items(month.rides, key = { "ride-${it.session.id}" }) { entry ->
                                HistoryRideRow(entry, date(entry.session.startTimeEpochMillis),
                                    elapsedStr(entry.session.durationMillis), deleting, Modifier.animateItem(),
                                    open = { selectedId = entry.session.id },
                                    delete = { deleteFailed = false; pendingDelete = entry.session.id })
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun HistoryRideRow(entry: com.isaac.ridelab.data.model.RideHistoryEntry,
    date: String, duration: String, deleting: Boolean, modifier: Modifier,
    open: () -> Unit, delete: () -> Unit) {
    RideLabCard(modifier.fillMaxWidth(), onClick = open) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(date, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(enabled = !deleting, onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_delete),
                        color = MaterialTheme.colorScheme.error) }, enabled = !deleting,
                        onClick = { menu = false; delete() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(duration, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            StatusPill(stringResource(if (entry.session.status == StoredRideStatus.ABORTED)
                R.string.history_aborted else R.string.history_completed),
                entry.session.status == StoredRideStatus.COMPLETED)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.ui_history_summary,
            entry.averagePower?.let { stringResource(R.string.history_watts, it) }
                ?: stringResource(R.string.history_no_data),
            entry.averageHeartRate?.let { stringResource(R.string.history_bpm, it) }
                ?: stringResource(R.string.history_no_data)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ========== 通用基础组件 ==========

@Composable
private fun HistoryGroupHeader(title: String, count: Int, expanded: Boolean,
    isMonth: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "HistoryChevron")
    val description = stringResource(if (expanded) R.string.history_group_expanded else R.string.history_group_collapsed)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()
        .padding(start = if (isMonth) 12.dp else 0.dp)
        .semantics { stateDescription = description },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                style = if (isMonth) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.history_group_count, count),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable () -> Unit
) {
    RideLabCard(Modifier.fillMaxWidth()) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
            }
            content()
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
private fun HistoryEmptyState() {
    RideLabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DirectionsBike, contentDescription = null,
                        modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.ui_history_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingView(textRes: Int) {
    RideLabCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorView(textRes: Int, onRetry: () -> Unit, retryTextRes: Int) {
    RideLabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null,
                modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
            Text(stringResource(textRes), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(retryTextRes))
            }
        }
    }
}
