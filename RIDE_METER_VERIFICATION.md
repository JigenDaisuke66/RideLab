# Professional meter ride layout

Implements the chosen "professional meter" concept on the Ride screen, retaining
existing Home/History/Settings work and all business-state owners.

- Compact RideToolbar replaces the tall overlay enable/settings/help banner.
  Quick overlay toggle keeps the same callbacks and enable conditions. More opens
  the shared OverlaySettingsDialog directly; it remains usable while disconnected.
- Large unboxed power, baseline-aligned unit and two secondary sensor tiles.
  Font size fits available width, including four-digit power and large system fonts.
- LiveRideCharts shows one selected metric in a compact 76 dp plot. The selected
  index is saveable presentation state; all three existing series remain unchanged.
  Null/time-gap segments and real zeroes retain their existing representation.
  Historical RideChart and stored data are untouched.
- ERG +/-5 W, numeric dialog, explicit Apply, last-requested status, protocol/range
  information and safety messages remain. Apply sits inline when there is room;
  narrow widths and large fonts retain the stacked layout.
- RideControls lays out existing pause/resume and end side-by-side. Start/end
  enable conditions, end confirmation, callbacks and workout ownership are intact.
- Existing navigation destinations now use Material icons. No navigation parameter,
  flow subscription, ViewModel method, BLE/service/recording/analytics logic changed.

Relevant unit tests: RidePresentationTest, ErgTargetValuesTest, LiveRideTrendTest,
RideChartsTest and TranslationResourcesTest. Compile app and Android tests only;
do not generate an APK/AAB. RideMeterUiTest supplements existing RidePresentationUiTest
for chart selection, exactly-once overlay commands and offline settings access.
Compiling device tests does not execute them or establish visual acceptance.

Physical acceptance:
1. Open Ride with no devices. Compact header, no-data readings and Start disabled
   as previously; More -> overlay settings opens the example and two sliders.
2. Connect trainer/HR, Start, compare time and measurements. Test Home/lock/return.
3. Switch power/HR/cadence plots, including real 0 W, absent cadence and a disconnected
   HR sensor. Switch tabs/rotate; selected metric survives ordinary UI recreation.
4. +/- and direct input only edit pending target. Apply sends once. Restore/reconnect
   never sends a target. Confirm range/protocol and rejected/busy messages remain.
5. Pause, resume, cancel End and confirm End. Check simultaneous connections and
   existing recording behavior remain. Test workout and FTP-test controls separately.
6. Check 320/360 dp widths, landscape, large fonts, light/dark and seven languages.
   Ride actions remain reachable; content scrolls when needed, ERG falls back to
   stacked layout. Overlay quick toggle, drag/close and settings must still work.
