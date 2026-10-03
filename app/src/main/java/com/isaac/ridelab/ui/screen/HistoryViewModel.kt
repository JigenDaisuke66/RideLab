package com.isaac.ridelab.ui.screen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.RideLabApplication
import com.isaac.ridelab.data.model.RideHistoryEntry
import com.isaac.ridelab.ui.chart.RideCharts
import com.isaac.ridelab.ui.chart.buildRideCharts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class CsvExportState { IDLE, WRITING, SUCCESS, FAILED }

data class HistoryState(val loading: Boolean = true, val failed: Boolean = false,
    val rides: List<RideHistoryEntry> = emptyList())

data class ChartState(val sessionId: Long? = null, val data: RideCharts? = null, val failed: Boolean = false, val analytics: com.isaac.ridelab.analytics.TrainingMetrics? = null, val powerAnalysis: com.isaac.ridelab.analytics.PowerAnalysis? = null,
    val insights: com.isaac.ridelab.analytics.RideInsights? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as RideLabApplication).rideRepository
    private val _charts = MutableStateFlow(ChartState())
    val charts = _charts.asStateFlow()
    private var chartJob: Job? = null

    fun loadCharts(id: Long?) {
        chartJob?.cancel()
        _charts.value = ChartState(sessionId = id)
        if (id == null) return
        chartJob = viewModelScope.launch {
            try {
                val samples = repository.historySamples(id)
                val data = withContext(Dispatchers.Default) { buildRideCharts(samples) }
                val session = repository.analyticsSession(id) ?: error("Missing ride $id")
                val np = withContext(Dispatchers.Default) {
                    com.isaac.ridelab.analytics.TrainingAnalytics.normalizedPower(samples, session.durationMillis)
                }
                val metrics = com.isaac.ridelab.analytics.TrainingAnalytics.calculate(np, session.analyticsFtpWatts, session.durationMillis)
                val insights = withContext(Dispatchers.Default) {
                    com.isaac.ridelab.analytics.RideInsightsCalculator.calculate(samples, session)
                }
                _charts.value = ChartState(id, data, analytics = metrics, powerAnalysis = repository.powerAnalysis(session, samples), insights = insights)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("RideLabHistory", "Chart samples failed sessionId=$id", error)
                _charts.value = ChartState(id, failed = true)
            }
        }
    }

    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    fun deleteRide(id: Long, result: (Boolean) -> Unit) {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                val deleted = repository.deleteHistory(id)
                if (deleted && _charts.value.sessionId == id) loadCharts(null)
                result(deleted)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("RideLabHistory", "Delete failed sessionId=$id", error)
                result(false)
            } finally { _deleting.value = false }
        }
    }

    private val _csvExport = MutableStateFlow(CsvExportState.IDLE)
    val csvExport = _csvExport.asStateFlow()
    fun acknowledgeExport() { if (_csvExport.value != CsvExportState.WRITING) _csvExport.value = CsvExportState.IDLE }
    fun exportFailed(error: Exception) {
        Log.e("RideLabHistory", "CSV document picker failed", error)
        _csvExport.value = CsvExportState.FAILED
    }
    fun exportCsv(id: Long, uri: android.net.Uri, headers: List<String>) {
        if (_csvExport.value == CsvExportState.WRITING) return
        _csvExport.value = CsvExportState.WRITING
        viewModelScope.launch {
            try {
                com.isaac.ridelab.export.RideCsvDocumentWriter(getApplication<Application>().contentResolver, repository)
                    .write(id, uri, headers)
                _csvExport.value = CsvExportState.SUCCESS
                Log.i("RideLabHistory", "CSV exported sessionId=$id")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("RideLabHistory", "CSV export failed sessionId=$id", error)
                _csvExport.value = CsvExportState.FAILED
            }
        }
    }

    private val refresh = MutableStateFlow(0)
    val state = refresh.flatMapLatest {
        (application as RideLabApplication).rideRepository.observeHistory()
            .map { HistoryState(loading = false, rides = it) }
            .onStart { emit(HistoryState()) }
            .catch { Log.e("RideLabHistory", "History query failed", it)
                emit(HistoryState(loading = false, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun retry() { refresh.value += 1 }
}
