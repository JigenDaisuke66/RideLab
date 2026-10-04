# Ride pause and retained BLE connections

Implementation complete; physical acceptance pending. No APK generated.

Checks: 83 relevant unit tests passed (RideSession, RideRecorder,
WorkoutController, RampTestResult, RideCharts, TrainingAnalytics, PowerAnalysis).
Main and Android UI-test Kotlin compilation passed; device UI tests were not run.
No new dependencies, BLE encoder changes or Room migration.

Changed files (relative to repository root):
- service: app/src/main/java/com/isaac/ridelab/service/RideSession.kt,
  RideRecordingService.kt, RideRecorder.kt (same directory).
- UI: app/src/main/java/com/isaac/ridelab/ui/screen/AppShell.kt,
  DevicesViewModel.kt, HomeScreen.kt, RideControls.kt, RideScreen.kt (same directory).
- resources: app/src/main/res/values/strings.xml, app/src/main/res/values-en/strings.xml.
- unit tests: app/src/test/java/com/isaac/ridelab/service/RideSessionTest.kt,
  app/src/test/java/com/isaac/ridelab/service/RideRecorderTest.kt,
  app/src/test/java/com/isaac/ridelab/workout/WorkoutControllerTest.kt.
- UI test: app/src/androidTest/java/com/isaac/ridelab/RidePresentationUiTest.kt.
- documents: AGENTS.md, PROJECT.md, ROADMAP.md,
  FOREGROUND_SERVICE_VERIFICATION.md, LOCAL_PERSISTENCE_VERIFICATION.md,
  WORKOUT_PHASE1_VERIFICATION.md, RIDE_PAUSE_VERIFICATION.md.

- ACTIVE -> PAUSED -> ACTIVE uses one service, one Room session and the same GATTs.
- Pause freezes active elapsed time, samples and workout steps. Live BLE preview continues.
- Already applied ERG resistance is unchanged. Resume during a workout explicitly
  resumes that step and reapplies its target using the existing verified controller.
- Sample elapsed time and stored duration exclude pauses. Real start/end wall-clock
  timestamps still include them. A null boundary sample at each pause prevents charts,
  NP and best-effort calculations from joining across the pause. A boundary at exactly
  the last sample timestamp advances 1 ms to avoid replacing measured data.
- End finalizes Room, releases the ride wake lock and cancels reconnect jobs, but
  retains healthy trainer/HR connections and a connectedDevice notification. No
  automatic ERG replay on End/new free ride. Disconnect each device explicitly from Home;
  when none remain connected, foreground mode/service is stopped. Process death,
  Bluetooth shutdown and unexpected radio disconnections are still possible.
- Preview before the first ride retains the existing foreground-only policy.

Physical checks:
1. Connect X2 Max and HR; start a free ride. Pedal for 1 minute.
2. Pause; verify paused badge/frozen timer and live sensor updates. Lock/switch apps
   for 2 minutes. Return: same devices connected; timer unchanged.
3. Resume and pedal 1 minute; end. History should contain one completed session with
   about 2 minutes active duration, real wall-clock end time, and chart breaks at pause.
4. Trainer and HR remain connected; persistent notification says devices are retained.
   Switch apps and return. Apply an explicit ERG target; start a second ride without
   rescanning/reconnecting. Confirm a new history session after ending.
5. Repeat pause/resume/end in a workout (including ending while paused). No step
   progression or automatic target writes during the pause; resume works normally.
6. Disconnect trainer only: HR stays connected. Disconnect HR: notification disappears.

Capture RideLabRide: PAUSED, ACTIVE resumed, STOPPING, Recording COMPLETED,
existing GATT retained, No retained devices. RideLabGatt/RideLabHeartRate should
show no deliberate disconnect or new connectGatt caused by Pause, Resume or End.
