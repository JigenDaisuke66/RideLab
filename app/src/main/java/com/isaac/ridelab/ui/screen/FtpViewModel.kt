package com.isaac.ridelab.ui.screen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.RideLabApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FtpState(val loaded: Boolean = false, val ftp: Int? = null, val failed: Boolean = false, val weightKg: Double? = null)
class FtpViewModel(application: Application) : AndroidViewModel(application) {
    private val weightSettings = (application as RideLabApplication).weightSettings
    private val settings = (application as RideLabApplication).ftpSettings
    private val mutable = MutableStateFlow(FtpState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() { viewModelScope.launch {
        try { mutable.value = FtpState(true, settings.read(), weightKg = weightSettings.read()) }
        catch (e: Exception) { Log.e("RideLabAnalytics", "FTP read failed", e); mutable.value = FtpState(failed = true) }
    } }
    fun saveWeight(value: Double?, done: () -> Unit) { viewModelScope.launch {
        try { weightSettings.save(value); mutable.value = mutable.value.copy(weightKg = value, failed = false); done() }
        catch (e: Exception) { Log.e("RideLabAnalytics", "Weight save failed", e); mutable.value = mutable.value.copy(failed = true) }
    } }
    fun save(value: Int?, done: () -> Unit) { viewModelScope.launch {
        try { settings.save(value); mutable.value = mutable.value.copy(loaded = true, ftp = value, failed = false); done() }
        catch (e: Exception) { Log.e("RideLabAnalytics", "FTP save failed", e); mutable.value = mutable.value.copy(failed = true) }
    } }
}
