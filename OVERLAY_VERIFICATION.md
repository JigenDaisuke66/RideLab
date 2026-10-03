# Cross-app ride metrics overlay

The existing RideRecordingService owns a read-only TYPE_APPLICATION_OVERLAY window.
No new BLE connection, recorder, foreground service, dependency or database schema.
The user enables it from the Ride tab during ACTIVE, after granting SYSTEM_ALERT_WINDOW.
Granting permission alone does not enable it: return and tap the button again.
Power, BPM and cadence refresh approximately once per second from LiveRideData.snapshot;
disconnected, absent and stale measurements show localized No data. Real zero remains valid.
Drag the title to move/snap to an edge; tap the title to collapse/expand. Close only hides
the overlay. Leaving ACTIVE or destroying the service removes it. It is not automatically
enabled on future rides. Lock/screen-off temporarily hides it; recording is unaffected.

## Physical acceptance (pending)
1. Connect trainer and HR sensor, start a ride, enable floating metrics from Ride.
2. Deny permission first: recording must continue. Grant permission in system settings,
   return, tap enable again. Compare the three readings with RideLab while pedaling.
3. Open another app for five minutes: numbers keep updating. Drag to both screen edges,
   collapse/expand and rotate. Controls remain reachable and the other app remains usable.
4. Stop sensor transmission: its reading becomes No data after the freshness timeout;
   actual zero power must display 0 W. Check both Chinese and English.
5. Lock for 20 minutes while pedaling, unlock: overlay resumes; elapsed time and recorded
   history remain continuous. System/security-sensitive screens may hide overlays.
6. Close the window: ride continues. Reopen from Ride, then End ride: window disappears.
7. Revoke overlay permission during use: no crash, ride remains active; regrant and retry.
8. On failure capture RideLabOverlay plus RideLabRide logs and phone/Android version.

Compile/unit checks do not verify OEM overlay permission or window behavior on hardware.


## Changed files
- app/src/main/AndroidManifest.xml: special overlay permission.
- app/src/main/java/com/isaac/ridelab/MainActivity.kt: system permission entry.
- app/src/main/java/com/isaac/ridelab/service/RideRecordingService.kt: overlay lifetime and updates.
- app/src/main/java/com/isaac/ridelab/ui/screen/DevicesViewModel.kt: service state/commands.
- app/src/main/java/com/isaac/ridelab/ui/screen/AppShell.kt: Ride tab control.
- app/src/main/java/com/isaac/ridelab/ui/overlay/RideOverlayWindow.kt: floating window presentation.
- app/src/main/java/com/isaac/ridelab/ui/overlay/OverlayPosition.kt: typed state and edge positioning.
- app/src/main/res/values/strings.xml and values-en/strings.xml: localized labels.
- app/src/test/java/com/isaac/ridelab/ui/overlay/OverlayPositionTest.kt: positioning checks.
- OVERLAY_VERIFICATION.md: behavior and physical checks.

## Transparency and language follow-up
- Expanded window includes an overall transparency slider (0–80%, default 6%).
  Background, text and controls now fade together. The previous transparency
  preference is preserved; restored values are clamped to this range.
- A separate font-size slider (80–140%, default 100%) scales metric values,
  labels, header/close text and the collapsed summary. It multiplies the existing
  sp sizes, preserving Android system font scaling. The window body remains
  scrollable, and resizing re-clamps the position within the screen.
- User changes immediately save to ride_overlay/transparency and
  ride_overlay/font_percent SharedPreferences, including keyboard/accessibility
  adjustments. Close/reopen, language recreation, new rides and app restarts
  restore these settings without touching the ride or BLE connections.
- Window resources explicitly use the Android per-app locale on API 33+, or the
  legacy app preference on API 31/32. Explicit System default remains supported.
  Each live refresh detects locale changes and recreates labels without touching BLE.
- Verify system English/app Chinese, then switch app to English and back while riding.
  Header, close, metric labels, units and both slider labels must follow the app.
- Move the slider, close/reopen, restart the app and rotate; verify the value persists
  and both settings persist. Verify text fades with the background at 80%, and
  all text resizes at 80%/140%, including the collapsed summary. Repeat in
  landscape and with Android larger fonts; the close control and sliders must
  remain reachable. New locale instrumentation cases are compile-checked;
  running them requires a device and is not part of the no-APK check.

Latest adjustment: RideOverlayWindow.kt, Chinese/English strings.xml, and this
document changed. Four existing OverlayPositionTest cases passed; application
and instrumentation Kotlin compile checks passed. No APK/new dependencies.
Font/opacity rendering and gestures still require physical verification.
