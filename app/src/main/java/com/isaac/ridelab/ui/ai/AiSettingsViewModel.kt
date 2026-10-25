package com.isaac.ridelab.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.ai.EncryptedAiSettingsStore
import com.isaac.ridelab.ai.HttpAiConnectionProbe

class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = AiSettingsController(viewModelScope,
        EncryptedAiSettingsStore(application), HttpAiConnectionProbe())
    val state = controller.state
    fun edit(action: AiSettingsAction) = controller.edit(action)
    fun save() = controller.save()
    fun test(prompt: String) = controller.test(prompt)
    fun forget() = controller.forget()
}
