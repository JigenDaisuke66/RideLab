package com.isaac.ridelab.ui.screen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.isaac.ridelab.ble.BleScanner
import com.isaac.ridelab.ble.ftms.FtmsClient
import com.isaac.ridelab.data.model.DiscoveredDevice

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    val scanner = BleScanner(application)
    val client = FtmsClient(application)
    var pendingConnection: DiscoveredDevice? = null

    fun connect(device: DiscoveredDevice) {
        Log.i("RideLabGatt", "ViewModel connect address=${device.address}")
        if (client.state.value.isActive && client.state.value.device?.address == device.address) {
            Log.i("RideLabGatt", "Ignoring duplicate active selection address=${device.address}")
            return
        }
        scanner.stop()
        client.connect(device)
    }

    fun leaveForeground() {
        scanner.stop()
        if (client.state.value.isActive) client.disconnect()
    }

    override fun onCleared() {
        scanner.close()
        client.close()
    }
}
