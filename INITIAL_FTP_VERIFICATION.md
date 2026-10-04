# First FTP test: unknown FTP

Implemented; physical acceptance pending. No APK, new dependency or Room migration.

Home -> Workout Library -> First FTP test (FTP unknown) -> preview -> confirm Start.
No FTP setting is required. Other FTP-dependent workouts still require it.

Protocol:
- 5-minute warmup at 50 W.
- 60-second ramp stages at 50, 60, 70, 80… W, independent of global FTP.
- Device profile clamps targets; after the first ceiling stage (currently 2000 W)
  ends, the course ends. Normally the rider manually ends when unable to continue;
  reaching the ceiling is not the objective. No automatic exhaustion detection.
- Pausing/end retain existing behavior: no automatic resistance reduction, BLE
  stays connected after End. Apply a lower manual target after ending if desired.
- Result uses stored measured trainer watts during ramp stages only, highest
  continuous 60-second average x0.75, rounded. Existing null/gap/pause rules remain.
- This is an estimate, not a definitive threshold measurement. Resting mid-test
  affects interpretation even though the calculator does not join paused segments.
- FTP remains unset until explicit result acceptance. Results outside the existing
  50–500 W settings range cannot be accepted; no automatic clamping or guessed FTP.
  Historical ride-time FTP snapshot stays null if unset at Start, including after
  accepting the result. Result review remains process-local as before.

Design reference: Zwift describes a beginner ramp increasing by 10 W/minute:
https://www.zwift.com/ca/news/32912-how-to-measure-your-cycling-fitness-effectively
RideLab uses its own fixed 50 W/5-minute warmup; this is not an identical Zwift test.

Verification:
1. Clear FTP in Settings. Connect trainer (HR optional); select First FTP test.
   Preview must show 50 W warmup and an enabled Start button, with no Set FTP gate.
2. Cancel start confirmation: no ride/control commands. Confirm: one session starts
   with null FTP snapshot, warmup requests 50 W via the existing FE-C path.
3. After 5 minutes, stage becomes ramp at 50 W; then 60 W at 6 minutes and 70 W
   at 7 minutes. Existing global FTP (if present) must not change these targets.
4. End during warmup: no estimate. End after sufficient uninterrupted ramp data:
   compare displayed best minute x75% with suggested FTP. It must use actual power.
5. Keep result: FTP remains unchanged/unset. Accept plus confirmation: FTP saves;
   later regular workouts use it; the completed test session snapshot is not backfilled.
6. Pause/Resume, Home/lock and HR remain functional; End preserves BLE. Also verify
   original FTP-relative Ramp still requires FTP and follows its 6% progression.

Unit checks: WorkoutControllerTest (includes no-FTP start, fixed targets, existing
FTP independence, ceiling, warmup effort exclusion and pause/resume) and
RampTestResultTest (existing measured-data/explicit-save regression).
UI tests compile-checked only; run the new no-FTP confirmation test on device.
Results: all 32 relevant unit tests passed (22 controller, 10 result). Application
and Android-test Kotlin compilation passed. Physical testing has not been run.

Changed files:
- app/src/main/java/com/isaac/ridelab/workout/WorkoutModel.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/WorkoutScreens.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values-en/strings.xml
- app/src/test/java/com/isaac/ridelab/workout/WorkoutControllerTest.kt
- app/src/androidTest/java/com/isaac/ridelab/WorkoutUiTest.kt
- PROJECT.md, ROADMAP.md, INITIAL_FTP_VERIFICATION.md
