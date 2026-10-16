package com.isaac.ridelab.ui.screen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.data.repository.ErgTargetSettings
import com.isaac.ridelab.ble.fec.TrainerErgProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ErgTargetUi(val address: String? = null, val target: Int = 100, val loaded: Boolean = false, val failed: Boolean = false,
    val profile: TrainerErgProfile? = null)
class ErgTargetViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = ErgTargetSettings(application)
    private val mutable = MutableStateFlow(ErgTargetUi())
    val state = mutable.asStateFlow()
    private var load: Job? = null
    fun select(address: String?, lastSent: Int?, profile: TrainerErgProfile?) {
        if (address == mutable.value.address && profile == mutable.value.profile && mutable.value.loaded) return
        load?.cancel()
        mutable.value = ErgTargetUi(address, profile?.let { restoredErgTarget(null, lastSent, it) } ?: 100, profile = profile)
        load = viewModelScope.launch {
            try {
                val saved = address?.let { settings.read(it) }
                mutable.value = ErgTargetUi(address, profile?.let { restoredErgTarget(saved, lastSent, it) } ?: 100, loaded = true, profile = profile)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabErg", "Target preference read failed", e)
                mutable.value = mutable.value.copy(loaded = true, failed = true)
            }
        }
    }
    fun edit(watts: Int) {
        val profile = mutable.value.profile ?: return
        // Pending +/- edits may be off the device increment; Apply stays disabled until valid.
        mutable.value = mutable.value.copy(target = profile.clamp(watts))
    }
    fun rememberApplied(watts: Int) {
        val current = mutable.value
        val address = current.address ?: return
        val profile = current.profile ?: return
        if (!profile.accepts(watts)) return
        viewModelScope.launch {
            try {
                settings.save(address, watts, profile)
                if (mutable.value.address == address) mutable.value = mutable.value.copy(failed = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabErg", "Target preference save failed", e)
                if (mutable.value.address == address) mutable.value = mutable.value.copy(failed = true)
            }
        }
    }
}
