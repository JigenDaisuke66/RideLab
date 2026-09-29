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
- Expanded window includes a background transparency slider (0–80%, default 6%).
  Text remains opaque. On release, ride_overlay/transparency preferences remember
  the setting across hiding, app restarts and new rides; restored values are clamped.
- Window resources explicitly use the Android per-app locale on API 33+, or the
  legacy app preference on API 31/32. Explicit System default remains supported.
  Each live refresh detects locale changes and recreates labels without touching BLE.
- Verify system English/app Chinese, then switch app to English and back while riding.
  Header, close, metric labels, units and transparency label must follow the app.
- Move the slider, close/reopen, restart the app and rotate; verify the value persists
  and text remains legible. New locale instrumentation cases are compile-checked;
  running them requires a device and is not part of the no-APK check.
