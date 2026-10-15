package com.isaac.ridelab.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.data.repository.AppearanceSettings
import com.isaac.ridelab.locale.AppLanguage
import com.isaac.ridelab.locale.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AppearanceAction {
    data class Palette(val value: ThemePalette) : AppearanceAction
    data class Mode(val value: ThemeMode) : AppearanceAction
    data class OverlayPalette(val value: ThemePalette?) : AppearanceAction
    data class Style(val value: OverlayStyle) : AppearanceAction
}

internal fun Appearance.changedBy(action: AppearanceAction): Appearance = when (action) {
    is AppearanceAction.Palette -> copy(theme = action.value)
    is AppearanceAction.Mode -> copy(mode = action.value)
    is AppearanceAction.OverlayPalette -> copy(overlayPalette = action.value)
    is AppearanceAction.Style -> copy(overlayStyle = action.value)
}

/** UI preferences only. Ride/service state is never copied or controlled here. */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppearanceSettings(application)
    private val mutable = MutableStateFlow(settings.read())
    val state = mutable.asStateFlow()
    private val mutableLanguage = MutableStateFlow(AppLocale.selection(application))
    val language = mutableLanguage.asStateFlow()
    fun refreshLanguage() { mutableLanguage.value = AppLocale.selection(getApplication()) }
    fun selectLanguage(language: AppLanguage): Boolean {
        val recreate = AppLocale.select(getApplication(), language.tag)
        refreshLanguage()
        return recreate
    }
    private val unsubscribe = settings.observe { mutable.value = settings.read() }
    fun onAction(action: AppearanceAction) { settings.save(settings.read().changedBy(action)) }
    override fun onCleared() { unsubscribe(); super.onCleared() }
}
