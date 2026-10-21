# SVG-inspired capsule overlay

Supersedes the three transparent HUD layouts described in OVERLAY_HUD_VERIFICATION.md.
Reference: user-provided gemini-svg.svg. Dark rounded horizontal capsule with time/status,
large power, recent power bars and heart-rate/cadence with short traces.
No FTP zone or ERG-stability claims are fabricated. Null slots remain empty; true zero
power has a baseline mark. Traces split on nulls and gaps over three seconds.

Only size (60–140%) and transparency (0–80%, including text) are user controls now.
The same scale applies to fonts, spacing and both dimensions, with proportional screen fitting.
Existing transparency persists. The new scale_percent key falls back to legacy width_percent,
then clamps; old font/height preferences and old style/palette selections are ignored.
No database, BLE, trainer command or service ownership changes.

Settings and Ride both open an offline preview, even before a trainer is connected or a ride
has started. The real overlay still requires an active/paused ride and overlay permission.
CapsulePreview shares CapsuleView with the window. Its example values are explicitly labelled,
never published to live state or recording. The preview scrolls horizontally if needed.

Verification:
1. With no devices connected, open Settings -> Overlay settings. Confirm only two sliders,
   a labelled example, immediate size/opacity changes and persistence after restarting.
2. Start a ride and show the overlay over another app. Check proportional scaling, narrow
   phone and landscape fitting, real values, timer agreement and pause/resume.
3. Drag and edge-snap; fold/unfold and close. Fold retains time/power and must not restart
   the clock or send a trainer command. Fold/unfold retains the most recent readout.
4. Disconnect HR or stop cadence data. Missing values must show No data; no replacement zero.
   Power 0 must remain 0. Check mini-traces stop across missing notification intervals.
5. Repeat in seven languages. Check labels/units fit and preview matches the live layout.

Run CapsuleGeometryTest, existing Overlay tests, resource tests, and app/device-test compilation.
OverlaySettingsUiTest, OverlayPanelTest and OverlayPreferencesTest require separate device
execution; compile checks alone are not physical acceptance. No APK/AAB is generated here.
