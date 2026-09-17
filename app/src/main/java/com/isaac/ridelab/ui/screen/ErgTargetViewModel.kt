package com.isaac.ridelab.ui.screen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.data.repository.ErgTargetSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ErgTargetUi(val address: String? = null, val target: Int = 100, val loaded: Boolean = false, val failed: Boolean = false)
class ErgTargetViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = ErgTargetSettings(application)
    private val mutable = MutableStateFlow(ErgTargetUi())
    val state = mutable.asStateFlow()
    private var load: Job? = null
    fun select(address: String?, lastSent: Int?) {
        if (address == mutable.value.address && mutable.value.loaded) return
        load?.cancel()
        mutable.value = ErgTargetUi(address, restoredErgTarget(null, lastSent))
        load = viewModelScope.launch {
            try {
                val saved = address?.let { settings.read(it) }
                mutable.value = ErgTargetUi(address, restoredErgTarget(saved, lastSent), loaded = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabErg", "Target preference read failed", e)
                mutable.value = mutable.value.copy(loaded = true, failed = true)
            }
        }
    }
    fun edit(watts: Int) { mutable.value = mutable.value.copy(target = restoredErgTarget(watts, null)) }
    fun rememberApplied(watts: Int) {
        val current = mutable.value
        val address = current.address ?: return
        viewModelScope.launch {
            try {
                settings.save(address, watts)
                if (mutable.value.address == address) mutable.value = mutable.value.copy(failed = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabErg", "Target preference save failed", e)
                if (mutable.value.address == address) mutable.value = mutable.value.copy(failed = true)
            }
        }
    }
}
