package com.isaac.ridelab.ui.settings

import com.isaac.ridelab.data.model.Appearance
import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.screen.FtpState
import com.isaac.ridelab.locale.AppLanguage

data class SettingsUiState(val profile: FtpState, val overlay: RideOverlayState,
    val overlayAvailable: Boolean, val appearance: Appearance,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val overlayPreferences: com.isaac.ridelab.data.repository.OverlayPreferences = com.isaac.ridelab.data.repository.OverlayPreferences())

class SettingsActions(
    val saveFtp: (Int?, () -> Unit) -> Unit,
    val saveWeight: (Double?, () -> Unit) -> Unit,
    val language: (String) -> Unit,
    val enableOverlay: () -> Unit,
    val disableOverlay: () -> Unit,
    val appearance: (AppearanceAction) -> Unit,
    val saveOverlay: (com.isaac.ridelab.data.repository.OverlayPreferences) -> Unit = {},
)
