# Dark Performance Dashboard refresh

Presentation-only refresh. BLE clients, service ownership, recording, Room schema,
analytics calculations, CSV and ERG encoding/persistence are unchanged.

## Design and changed files

- `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`: stable dark-first palette independent
  of system wallpaper; shared metric trace colors, restrained typography, 18–24dp
  shapes. `MainActivity.kt` keeps system bar icons visible on the dark background.
- `ui/theme/RideLabComponents.kt`: RideLabCard, MetricTile, StatusPill, SettingRow,
  spacing tokens. Zero elevation, subtle outlines, secondary labels.
- `ui/screen/RideScreen.kt`: compact live power/HR/cadence, monospace metrics,
  outlined numeric target entry, 48dp +/- and Apply. The same send callback still
  runs only on Apply, after remembering the preferred target.
- `ui/screen/RideControls.kt`: persistent bottom ride action in the Ride screen;
  End opens confirmation before calling the existing finalization callback.
- `ui/screen/HomeScreen.kt`: whole device rows open the existing picker; disconnect
  is in a secondary menu. FTP/weight tiles, compact recent ride duration/power.
- `ui/screen/HistoryScreen.kt`: flat outlined rows with already-queried average
  power/HR, status pills, unchanged delete/export callbacks, localized metric units.
- `ui/screen/PowerAnalysisViews.kt`: compact best-effort rows and PR badges, zone
  bars drawn from existing percent values; no new analytics.
- `ui/chart/RideChart.kt`: muted grid/axes, power teal, HR warm red, cadence amber.
  Segment, gap, point and downsampling semantics are untouched.
- `ui/screen/AppShell.kt`: dark navigation, compact overlay help, grouped setting
  rows; existing overlay commands are also accessible in Settings.
- `ui/screen/DevicePicker.kt`: role labels, larger selection rows and light haptics;
  existing filtering/order and exact-device callbacks are retained.
- `ui/overlay/RideOverlayWindow.kt`: shared palette, prominent expanded power,
  collapsed live summary, 160ms collapse transition, insets-aware dragging/snap,
  saved position, scrollable expanded body on short screens. Existing transparency
  preference and live data source are preserved.
- `res/values/strings.xml`, `res/values-en/strings.xml`: matching new resources.
- `androidTest/.../RidePresentationUiTest.kt`: End cancel/confirm callback test and
  360x640dp viewport action visibility check (device execution remains pending).

No new dependencies, vibration permission, database migration or APK generation.
Native view haptic feedback respects system haptic settings. No vibration on samples.
History navigation crossfades for 180ms; no live-number animation at 1Hz.

## Manual acceptance (pending)

Run from Android Studio without uninstalling; retain existing ride data. Check a
small 360x640dp phone/emulator and a typical taller phone. Repeat Chinese and English
with normal fonts and larger system font scale (e.g. 1.3 and 1.5).

- [ ] Home: start disabled until trainer ready; connected/absent states legible;
      tap device row selects exact device, HR doesn't replace trainer; secondary
      disconnect works; return to an ACTIVE ride does not start another session.
- [ ] Ride IDLE/STARTING/ACTIVE/STOPPING/ERROR: power is most prominent, elapsed
      remains stable; 99 to 100 and four-digit watts don't jump horizontally.
- [ ] Missing/stale sensor values remain No data; actual zero is valid. Trainer and
      HR labels remain readable independently; status is not conveyed by color alone.
- [ ] +/- changes pending target by 5W within 50–2000W. Direct entry cancel keeps
      value; confirm changes pending only. Restored targets never send automatically.
      Only Apply sends once; disabled/writing behavior remains correct.
- [ ] Start works once. End opens confirmation; Cancel keeps recording; confirm
      saves the ride once. Start/End remains reachable without scrolling; smaller
      screens/large fonts may need to scroll the metrics/ERG content.
- [ ] History rows show correct duration/averages/state; whole row opens details.
      Overflow deletion still identifies ride and requires confirmation.
- [ ] Detail: summary/core/training values, best efforts, PR comparisons, PDC and
      zone bars agree with prior values. Charts preserve gaps, nulls and zeroes.
- [ ] Export CSV opens Save As; cancel is silent; headers follow app language and
      raw numeric data is unchanged. Stored ride deletion still cascades samples.
- [ ] Settings: FTP/weight dialogs/save/unset, language, overlay enable/disable/help.
- [ ] Overlay: expanded metrics, collapsed live summary, close without ending ride,
      drag/snap stays inside safe screen bounds after rotation; saved position and
      transparency survive reopening; short landscape body scrolls.
- [ ] Lock/background for 20 minutes: recording/connectivity unchanged; return to
      app/overlay remains functional, no duplicate GATT connection or auto ERG replay.
- [ ] Touch targets, TalkBack icon labels, system bar contrast and large-font layout.

Compile and pure unit tests cannot establish physical visual/haptic acceptance.
Device Compose cases are compile-checked here; run them from Android Studio.

## Automated results

36 relevant unit cases passed: RidePresentation (2), ErgTargetValues (6),
DevicePickerFilter (4), OverlayPosition (4), RideCharts (7), RideCsvExporter (13).
Final compileDebugKotlin and compileDebugAndroidTestKotlin passed. Existing AGP
compileSdk 37.1 compatibility warning remains; no toolchain change was made.
