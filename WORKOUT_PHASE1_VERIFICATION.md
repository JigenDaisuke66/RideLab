# Workout Engine Phase 1 + FTP Ramp Test

Implementation and compilation/unit verification are complete; physical acceptance
is PENDING. No APK generated, new dependency, FE-C encoder change or Room migration.

## Ownership and flow

Preview/confirmation -> MainActivity's existing notification permission gate ->
DevicesViewModel -> RideRecordingService.requestWorkout -> existing ride start /
Room session -> WorkoutController.start(saved session FTP) -> existing
FtmsClient.sendFecTarget -> existing FecOverBleTrainerController/native GATT.

WorkoutDefinition/Step/Target/State are pure Kotlin types. Names, descriptions,
phases and errors are rendered through Chinese/English resources. Definitions
support absolute watts, FTP fractions, optional linear FTP ramps and free-ride
targets. FreeRide issues no target and does NOT release an existing ERG target;
no such step is used in the built-in courses.

Exactly one controller belongs to the service; one 250ms monotonic runtime loop
runs independently of the existing approximately 1Hz recording loop. Compose
observes the service state and does not advance steps. Existing ride foreground
mode/wake lock and BLE reconnect budgets remain authoritative. UI disappearance,
Activity recreation or ViewModel rebind never recreate/restart the workout.

Before promotion, FTP and trainer readiness are checked. The authoritative FTP
is then read from the newly saved ride's session column, not from live settings.
If FTP was removed between preflight and session creation, no target is sent and
that just-created ride is finalized; no default FTP is invented. Normal missing
FTP is rejected before ride/session start.

## Control and timing safety

- Automatic commands begin only after explicit Start Workout confirmation and
  ride activation. Preview, reconnect alone, result publication and FTP acceptance
  do not send targets.
- Positive finite fractions resolve against the saved FTP; targets round to the
  nearest whole watt (positive .5 rounds up), then clamp to the unchanged X2 Max
  capability of 50–2000 W. Even a recovery target below 50 W is raised to 50 W.
- One logical request is issued for a changed step target. Repeated ticks or
  adjacent equal targets do not resend it. Optional linear ramp targets (not
  used for the staged FTP test) change at most once per 10 seconds.
- Delayed scheduling advances to the current step and never bursts expired
  commands. Recording still skips missed slots instead of inventing samples.
- Pause freezes progression and retains the current applied resistance; it does
  NOT make pedaling easy. Resume re-applies only the current correct target once,
  using remaining active time, without including the paused interval. A pause
  exactly at a boundary resumes the next step, never an expired target.
- Trainer loss/replacement and asynchronous FE-C failures pause progression.
  No controller reconnect/retry loop exists. The original service makes its
  single bounded reconnect attempt; recovery requires explicit Resume once FE-C
  is ready. Paused time never advances the workout.
- Skip is available only while ACTIVE and control is ready. It resets the next
  step timer and requests that target once; the last skip completes the course.
  Resume before skipping a paused course. Avoid skips during actual FTP testing.
- Manual ERG editing is hidden/locked during preparation, execution and pause.
  The service independently rejects manual writes, including during STOPPING.
  Automatic targets do not overwrite per-trainer manual target preferences.
- Normal course completion ends/saves the ride automatically. Explicit End
  Workout requires confirmation, cancels scheduling and invokes existing ride
  finalization. Samples are retained and the ride is COMPLETED, not invalidated
  by early course cancellation. No previous manual target is replayed.
- The existing ride notification/chronometer remains. During a workout its
  action opens the app so cancellation goes through UI confirmation. No extra
  notification timer or foreground service is introduced.
- Recording failure stops progression; unexpected scheduler failure is logged
  and invokes ride finalization. Process death never restarts/replays a course.
  Existing recovery marks interrupted recording ABORTED.

## Built-ins

- Recovery 30: 30 min at 50% FTP.
- Endurance 45: 5 min 50%, 35 min 65%, 5 min 45%.
- Sweet Spot 3x10: 5 min 50%, 5 min 65%, three 10 min 90% efforts,
  two 5 min 55% recoveries, 5 min 45% cooldown. Total 55 min.
- FTP Ramp Test: 5 min 50%, 5 min 60%; successive 60s efforts starting
  at starting FTP, adding 6% of that same FTP per stage (not compounding).
  Continue until explicit End Test, except the last clamped ceiling stage lasts
  one minute and ends. No exhaustion/cadence/HR/low-power failure detection.

## FTP test result

After final recording commit, the existing repository reads stored RAW trainer
power. Service-owned elapsed effort ranges exclude warmup, pauses and skip
boundaries. Each range is analyzed separately with the existing PowerAnalysis
60s window: time-weighted, real zeros valid, null/negative readings break coverage,
adjacent intervals above 1.5s uncovered, no assumed final tail or interpolation.
Ranges are never joined across pauses/skips. The best valid 60s average is used:

`estimated FTP = round(best measured continuous 60s watts * 0.75)`

Insufficient effort returns no result; the ride remains saved. The review shows
best 60s, estimated FTP, current FTP and difference. Out-of-settings-range
estimates (<50 or >500 W) remain visible but cannot be saved, not silently clamped.
Use Estimated FTP opens a replacement confirmation; only confirmation calls the
same existing FtpSettings.save function. Keep Current FTP never writes. Save
failure is visible and retry is explicit. Old session FTP/weight and analytics
remain unchanged. No automatic FTP update or power command follows acceptance.

The review is process-local application state so ending the service while in
background does not erase a pending review. Process death/restart DOES discard
an unaccepted result; it never silently accepts it. Accepted FTP uses the normal
persistent settings. Raw sessions/samples remain durable. No workout metadata,
runtime recovery or pending-result persistence is added to Room in this phase;
history retains normal ride details but has no workout-name/status association.

## Physical procedure (required)

1. From Android Studio run the updated app. Set FTP 150 W, connect X2 MAX + HR.
   Confirm normal free ride/manual ERG and overlay still work before testing.
2. Home -> Workouts -> Endurance 45. Preview 75 W / 98 W / 68 W and all steps.
   Open/cancel the Start confirmation: no target request/ride must be created.
   Clear FTP separately and verify Start is disabled with a Settings link.
3. Restore FTP 150 and confirm Start. A single ride is created and the first
   requested target is 75 W. Manual Apply is absent; Pause/Skip/End are in the
   fixed footer. Compare actual pedaling load/power, BPM and cadence.
4. Press Home for 6 minutes, then lock/pedal for at least 20 minutes. Return:
   ride/course still active, elapsed ride time includes background, course at
   the expected step/time and 98 W. Capture commands around the five-minute
   boundary; do NOT expect a command every second. Rotate and navigate away/back:
   no restart, duplicate controller, session or GATT connection.
5. Pause mid-step; wait 30s: remaining time freezes and resistance stays unchanged.
   Resume: one current target request, then remaining time continues. Skip while
   active: next target/timer updates. End/cancel the confirmation once, then
   confirm End: scheduling stops, history/raw samples remain, BLE cleanly closes.
6. In another course, interrupt trainer BLE/power. Progress must enter PAUSED.
   Original recovery budget can reconnect once. Even after FE-C ready, no target
   is sent until Resume; resume requests only the preserved current step. HR
   stays independent; missing trainer samples remain null.
7. Start at FTP 150, change Settings FTP to 170 during the course, return: targets
   and session FTP remain 150-based. Next ride uses 170. Verify old NP/IF/TSS/zones.
8. Restore FTP 150 and start Ramp Test. Warmup 75 W then 90 W, followed by
   150/159/168/177 W at one-minute intervals. End Test manually after sufficient
   effort. Actual FTP assessment must follow warmup/stages without skipping;
   this app does not detect exhaustion automatically.
9. Compare Best 60s with stored raw CSV during ramp-only active ranges. Example
   best actual 212 W -> estimate 159 W. Keep Current FTP: setting unchanged.
   Repeat/review and select Use Estimated FTP, cancel first (unchanged), then
   confirm: normal FTP setting updates and old rides do not change.
10. End another test before 60s of valid ramp effort (or during warmup): No data,
    no invented estimate/automatic FTP write, but ride history is retained.
11. Verify Chinese/English, narrow/larger-font screens, notification action,
    overlay, history grouping/deletion/export, charts and basic analytics.

Useful Logcat: RideLabWorkout (start/session/FTP snapshot, target request, step,
auto-pause reason, resume and result); RideLabGatt (complete outgoing bytes,
write dispatch/callback status, connection and reconnect errors); RideLabRide
(STARTING/ACTIVE/STOPPING/COMPLETED, insert errors and recovery).
GATT write success confirms transport only: verify the trainer's actual load and
measured power change while pedaling, not merely the displayed target.

## Verification and changed files

85 focused unit tests passed: 18 WorkoutController, 10 RampTestResult, 7 FE-C,
6 ERG values, 9 RideRecorder, 10 RideSession, 10 TrainingAnalytics, 15 PowerAnalysis.
Application and Android-test Kotlin compile checks passed. Four new WorkoutUiTest
cases (confirmation, missing FTP, manual lock/footer, FTP acceptance confirmation)
are compile-checked only; NOT executed on hardware. Existing Room migration tests
are preserved/compile-checked. Physical safety/background/ERG acceptance pending.

- workout/WorkoutModel.kt, WorkoutController.kt, RampTestResult.kt
- service/RideRecordingService.kt
- MainActivity.kt, RideLabApplication.kt
- ui/screen/WorkoutScreens.kt, AppShell.kt, HomeScreen.kt, RideScreen.kt,
  DevicesViewModel.kt, FtpViewModel.kt
- res/values/strings.xml, res/values-en/strings.xml
- test/workout/WorkoutControllerTest.kt, RampTestResultTest.kt
- androidTest/WorkoutUiTest.kt
- PROJECT.md, ROADMAP.md, WORKOUT_PHASE1_VERIFICATION.md

No protocol, native GATT client, recorder, repository, DAO, schema, analytics
formula, chart, overlay, CSV implementation or dependency edits.
