# Theme and overlay appearance

Implemented; visual/physical verification pending. No new dependency, Room
migration, ride/control/service lifecycle change or APK generation.

Settings -> Appearance and overlay:
- Five app palettes: Mint (existing default), Blue, Violet, Amber and Rose.
- Light (default), Dark and System modes change full background, cards and text,
  not just accent colors. Old preferences without a mode retain their palette and
  default to Light; selecting Dark restores a dark presentation. System bars and
  overlay brightness also follow the selected mode.
- Overlay color: Follow app theme (default) or independently select one of five colors.
- Layout: Panel (default), Strip (three columns), Gauge (large power with HR/cadence beneath).
- Preview uses localized No data, not fake sensor readings.

SharedPreferences `appearance` persists stable enum names in `theme`,
`overlay_palette` (absent = follow theme), `overlay_style`, and `mode`. Unknown names fall
back safely. Compose observes preference changes; the live overlay re-reads them
at its existing approximately 1 Hz refresh and recreates only its window views.
The same overlay object retains collapse/position, and original `ride_overlay`
transparency/font/position preferences are untouched. BLE stays in its existing service.
All labels support Chinese/English; BPM and cadence retain their sensor colors in charts.

Checks: appearance preference and full-palette contrast unit tests; application and Android test Kotlin
compilation passed. Device UI tests/visual verification were not run here.

On-device checks:
1. Select each theme in Settings. Confirm buttons/navigation/ERG controls and power
   chart/title change color immediately. Switch tabs, rotate, reopen app: choice remains.
   Try Light/Dark/System: backgrounds/cards and readable system-bar icons must
   change too. In System mode change Android dark mode and verify app/overlay follow.
2. Connect trainer/HR and start a ride; enable overlay. Try Follow app theme, then
   an independent overlay color. Change the app theme: independent overlay color stays.
3. Try all three layouts while pedaling. Power/BPM/cadence remain real and update.
   Set transparency and font size, drag/collapse overlay, then change its appearance.
   Confirm those settings/position are retained, close/header remain reachable,
   including landscape, narrow screens and 140% text size.
4. Switch app language to English; verify all settings/overlay labels update.
5. Switch apps/lock screen/return: ride still runs, no appearance-triggered GATT
   disconnect, recording stop, session creation or ERG command.

Changed files:
- app/src/main/java/com/isaac/ridelab/data/model/Appearance.kt
- app/src/main/java/com/isaac/ridelab/data/repository/AppearanceSettings.kt
- app/src/main/java/com/isaac/ridelab/ui/theme/Color.kt
- app/src/main/java/com/isaac/ridelab/ui/theme/Theme.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/AppearanceControls.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/AppShell.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/RideDetailAnalysis.kt
- app/src/main/java/com/isaac/ridelab/ui/overlay/RideOverlayWindow.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values-en/strings.xml
- app/src/test/java/com/isaac/ridelab/data/model/AppearanceTest.kt
- app/src/test/java/com/isaac/ridelab/ui/theme/ThemeColorsTest.kt
- PROJECT.md
- APPEARANCE_VERIFICATION.md
