# Transparent ride HUD refresh

The three existing saved styles remain supported. All use a transparent background,
light text with a dark shadow, palette-accented power and distinct HR/cadence icons.
Panel emphasizes power with two secondary metrics; strip aligns three metrics;
dashboard centers a larger power reading. Icon controls replace the old text buttons.
The always-visible header shows ride status and elapsed time. Folded mode keeps
time and power. The optional two-minute power/HR sparklines remain independent
scales, preserving missing values and gaps. No decorative sample data is used.

RideRecordingService supplies its existing RideState to the read-only window.
OverlayReadout uses elapsedMs(SystemClock.elapsedRealtime()); opening the overlay
late includes the full ride, pauses freeze the clock, resume excludes paused time.
BLE ownership, ERG, recording, sample timing, wake locks and ride transitions are
unchanged. Settings remain in the app, using existing preference keys and ranges.

Design reference: compact live stats and elapsed-time hierarchy in
https://support.strava.com/en-us/articles/15401559-strava-live-activities-on-ios
The native Android HUD retains RideLab's transparent-background requirement.

Verification:
1. Connect trainer and HR, start a ride and open the overlay after one minute.
   Timer must agree with the Ride screen, not start at zero.
2. Open other apps with both white and dark content. Check readability, units,
   colored icons, drag/edge snap and the 48 dp close/fold controls.
3. Fold/unfold: time and power remain visible, body returns, no extra command sent.
4. Pause for a minute: status is paused and timer freezes. Resume: timer continues
   excluding the pause. Power/HR remain live as on the Ride screen.
5. Expand curves; check zero power, disconnect one sensor, inspect gaps. Fold and
   reopen curves; the selected expansion state persists within the window instance.
6. Test all three styles, widths 80–160%, font 80–140%, height 40–140%, opacity,
   all seven languages, rotation and landscape. Short windows scroll their body;
   header/close remain reachable. Position remains on-screen.
7. Lock/unlock: window follows existing lock policy; ride continues, and timer
   includes active locked time when the HUD returns.
8. End/start another ride: elapsed time and sparkline history reset for the new ride.

JVM tests cover service-clock projection, pause/resume, missing/zero values and
compact sizing, plus existing drag/position/trend and localization checks.
OverlayPanelTest draws native layouts and verifies one-shot actions on a device;
compiling that test does not execute it or replace physical visual acceptance.
