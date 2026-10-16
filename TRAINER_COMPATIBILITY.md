# Protocol-based trainer compatibility — requested Phase 3

This is the requested compatibility phase, not a replacement for the roadmap's historical
Phase 3 heart-rate milestone. No new dependency, database migration, signing/application ID
change, APK or AAB. UI/localization architecture is retained.

## Audit and ownership

Before this change, `FtmsClient` already owned the service's single trainer GATT and parsed
FTMS Indoor Bike Data or Cycling Power Measurement (one selected measurement source).
`HeartRateClient` had its own independent GATT. Both remain owned by RideRecordingService.
Actual GATT UUID/property diagnostics and device-picker filtering already existed and are retained.
CPS-only scan results remain selectable as potential trainers; this is NOT evidence of ERG.

There was no general GATT operation queue: measurement CCCD completion started the existing
FE-C CCCD operation; FE-C then allowed one write at a time and a two-second cooldown.
Both the manual path and WorkoutController used `sendFecTarget` and `FecState` directly.
FTMS measurement parsing existed; FTMS control procedures, feature/range reads and authorization
did not. The X2 MAX profile had leaked into all manual validation and course-preview limits.

Now the path is:

`RideRoute / DevicesViewModel.setTargetPower -> RideRecordingService.setTargetPower ->
FtmsClient.setTargetPower -> TrainerControlPolicy.dispatch -> selected FE-C or FTMS adapter`.

The existing service-owned WorkoutController uses the same FtmsClient entry, with WORKOUT
ownership instead of MANUAL. PREPARING, ACTIVE and PAUSED workouts exclude manual commands.
The service rejects manual commands during STOPPING. No new workout timer or BLE owner exists.
Workout timing, FTP estimates, recording timing, pause/end behavior and Room schema are unchanged.

`GattDeviceState` carries capabilities and FTMS procedure state, alongside existing FE-C state
and measurement source. Its `control` is a read-only projection, not another mutable controller.
Capabilities distinguish protocol, operating range/increment, range provenance and permission
for automatic ERG. Connection state remains separate. FTMS authorization is session-local;
FE-C has no equivalent Request Control handshake and still reports transport-only results.

## Discovery, serialization and selection

The original measurement subscription runs first. `FtmsGattControl` then borrows the same GATT
and reads 0x2ACC and 0x2AD8 when readable. `SerialGattOperations` serializes those reads,
FTMS CCCD writes and Control Point writes. For FE-C selection, setup ends before the original
FEC2 subscription starts; its existing write/cooldown implementation remains in place. Thus
the new queue and old FE-C operations never run concurrently. Neither adapter connects a device.

Selection is made once per connection from actual GATT capabilities:

1. A complete FEC1/FEC2 Notify+CCCD/FEC3 Write or Write Without Response layout takes precedence,
   including devices exposing both FE-C and FTMS. No target is sent through both.
2. Otherwise FTMS requires readable 0x2ACC with target-setting feature bit 3, Indoor Bike Data
   Notify+CCCD, Control Point Write+Indicate+CCCD, Status Notify+CCCD and a valid read power range.
   Requiring Status allows authorization loss to be observed. Missing requirements fail closed
   to measurement-only. No permission or target write is sent while merely connecting.
3. An automatic in-ride reconnect pins an already selected protocol. If discovery would select
   a different protocol, control stays unavailable. Failure does not trigger protocol fallback.
   Explicit device replacement uses the existing disconnect/replace path, pauses a workout,
   and still requires explicit manual Apply or explicit workout resume.

FTMS setup errors leave available measurement streaming intact. An ATT operation that never
completes times out after 10 seconds and asks the existing owner to close the ambiguous GATT;
the existing one-attempt reconnect budget applies during a ride. No new retry loop is added.

## FTMS protocol and transaction safety

References: [Bluetooth SIG FTMS 1.0.1](https://www.bluetooth.com/specifications/specs/fitness-machine-service-1-0-1/),
[official FTMS test suite, Request Control and Set Target Power cases](https://files.bluetooth.com/wp-content/uploads/dlm_uploads/2024/10/FTMS.TS_.p6.pdf),
and [SIG GATT Supplement, Supported Power Range](https://btprodspecificationrefs.blob.core.windows.net/gatt-specification-supplement/GATT_Specification_Supplement.pdf).

Implemented wire operations:
- CP CCCD: 02 00 (indications); Status CCCD: 01 00 (notifications).
- Request Control: `00`; successful response: `80 00 01`.
- Set Target Power: `05 LL HH`, signed little-endian watts at 1 W resolution.
  A 100 W request is `05 64 00`; success is `80 05 01`.
- Responses decode request opcode and result: success, unsupported opcode, invalid parameter,
  operation failed, or control not permitted. Status FF revokes permission; status 01 resets it.
- Range: signed 16-bit minimum/maximum watts, unsigned 16-bit minimum increment, little-endian.

Only explicit Apply starts Request Control if needed, followed by that same requested target
after authorization succeeds. A matching indication AND successful ATT write callback are
required before advancing, including indication-before-write-callback ordering. GATT write
success alone never produces ACCEPTED. No target queue, periodic command, automatic retry,
start/resume-machine command, resistance, slope or reset command is emitted.

Only one procedure is in flight. Busy requests are rejected, not deferred. Each procedure has
a 30-second deadline. Rejection, malformed matching response, permission loss or response timeout
quarantines FTMS control until reconnect, without intentionally stopping measurement. Unexpected
opcodes and unsolicited/duplicate responses cannot advance the current transaction. On disconnect
all pending operations, targets, authorization and deadlines are discarded; the GATT owner's
existing identity check ignores callbacks from replaced sessions. Nothing is replayed on reconnect.
Devices requiring extra proprietary activation or additional machine-start commands are unsupported.

## Limits, preferences and UI

- Confirmed FE-C layout plus known X2 MAX model identification retains 50–2000 W and existing
  automatic workouts. Current identifiers: observed `Think X211-` name prefix, or exact
  `ThinkRider X2 MAX` / `ThinkRider X2Max` (case-insensitive). Name alone never enables control.
  This is model recognition, not authenticated hardware identity; renamed/unrecognized devices
  intentionally receive conservative behavior until their identity is verified.
- Other FE-C devices use 50–200 W, manual-only, visibly marked conservative/unverified.
- FTMS uses its valid device-reported range and increment, restricted to nonnegative signed-16
  targets. Negative lower limits are advanced to the first nonnegative aligned value. Missing,
  unreadable, truncated, reversed, wholly negative or zero-increment ranges disable FTMS ERG.
  No X2 MAX limit is substituted. FTMS encoding capacity is separate from operating limits.
- Every dispatch validates range and increment. Invalid requests return false and are logged;
  no dispatched target is silently clamped. Existing X2 workout target calculations and their
  visible range-adjustment notice remain unchanged.
- Manual +/- remains exactly 5 W, bounded by operating limits. If a device uses a different
  increment and an edit is off-grid, Apply is disabled with an explanation; direct input can
  select an aligned value. The dialog and card show range and increment.
- Existing per-address `erg_targets` preferences are reused. Save occurs only on explicit Apply,
  recording an explicitly requested preference, not evidence of trainer acceptance. Restoration
  selects saved target, else last requested, else 100 W; pending-only bounds/increment adjustment
  is visible before Apply and never writes a trainer command or overwrites the saved value.
- FTMS shows ACCEPTED only after the successful protocol response. FE-C retains its transport-only
  wording. The UI exposes control protocol, measurement-only state, failures and manual-only policy
  through existing state/ViewModel wiring. All 14 new strings exist in seven languages.
- Raw ride `targetPowerWatts` continues to mean last requested target, now from the selected
  adapter. It does not claim measured output or accepted resistance. No schema change.

## Compatibility claims and tests

Physically verified before this change: ThinkRider X2 MAX CPS power/cadence, FE-C FEC2/FEC3 ERG,
heart rate in parallel, existing ride/background functionality (user-reported verification).
Its FE-C encoder is unchanged, including the exact 2000 W frame/checksum test. This change still
requires a physical regression run on that trainer. No FTMS hardware has been tested here.

Focused unit coverage uses fake ATT dispatch/completion and the production serialized queue and
pure control state machine: X2 CPS+FE-C; FTMS ERG; FTMS/CPS measurement-only; dual-protocol selection;
missing/invalid ranges; characteristics/properties; request/target rejection; write vs indication;
timeouts; malformed/wrong/duplicate responses; authorization loss; disconnect/reconnect cleanup;
no protocol fallback; no dual dispatch; invalid targets; manual/workout ownership and FTMS workout
lockout. Existing FE-C encoder, measurement parser, workout, UI value, picker, recording snapshot
and seven-language resource tests are included. UI tests cover measurement-only disabled Apply,
FTMS acceptance state and existing explicit-Apply behavior; they are compile-checked, not run.

Verification: 110 focused unit tests passed, zero failures/errors/skips, including the unchanged
2000 W FE-C packet/checksum test. Application Kotlin, resources and Android-test Kotlin compile
successfully. UI tests are compile-only; no physical device/emulator run was performed here.
Only `testDebugUnitTest` and compilation/resource prerequisites were run, never assemble/package
APK/AAB. Existing compile-SDK compatibility and Android/Compose API deprecation warnings remain.

## Physical verification

1. X2 regression: connect the known trainer and HR. Confirm FE-C control, 50–2000 W, live power,
   cadence/BPM. Restore a saved target: no FEC3 write until Apply. Safely apply 100 W then 105 W;
   test numeric input/cancel, and confirm only one FEC3 packet per Apply. The 2000 W encoder test
   does NOT require a person to pedal at 2000 W.
2. Start a familiar X2 workout. Confirm existing targets/timing and no manual Apply while course
   owns control. Pause/resume/skip, initial FTP test entry and ending retain existing behavior.
   Test disconnect: course pauses; recovery does not send a target until explicit resume.
3. New FTMS trainer: close other controlling apps. Pedal comfortably; connect, inspect GATT and
   reported range/step. Confirm FTMS control and manual-only message. Merely connecting/restoring
   a preference must produce no CP command. Use a low supported target (100 W if in range/aligned).
4. Apply once. Log sequence must show CP/Status CCCD success; outgoing 00, ATT success and
   80 00 01; outgoing 05 64 00 (for 100 W), ATT success and 80 05 01. Only then show acceptance.
   Keep pedaling and check measured watts/resistance settles near target; protocol acceptance
   alone does not prove physical ERG regulation. Try one small aligned adjustment.
5. Test busy double tap; unavailable range or read-only trainer; rejection/busy control from
   another central where possible; power-cycle/disconnect and reconnect. No old target may replay.
   Fault-injection timeout/rejection tests are unit tests, not a claim of hardware coverage.
6. With both trainer and HR, test recording, rotation, Home and screen lock; return and explicitly
   apply a target. Check records/history and values remain separate. Confirm automatic workout/FTP
   test start is disabled for FTMS and unverified FE-C, while free rides still work.
7. Check the range, step, messages and disabled states in all seven languages on a narrow screen.

Capture `RideLabGatt` from connect through failure: complete service/characteristic/descriptor
listing; `FTMS read invoked/read` with raw 2ACC/2AD8 and status; `Trainer control selected`;
`FTMS CCCD dispatch/CCCD`; `Trainer target dispatch/rejected`; `FTMS outgoing`, dispatch result,
GATT write status, indication bytes, machine status, control state/authorization/issue/result;
connection errors and reconnect events. For X2 include existing FE-C capability, CCCD, outgoing,
write callback and FEC2 lines. For course conflicts also capture `RideLabWorkout`.

Send trainer model/firmware, Android version, selected range/protocol, exact requested watts,
these logs and whether physical resistance/power changed. Do not infer support from a device
name or advertisement alone. Universal trainer compatibility is NOT claimed.

## Changed files

New main files (under `app/src/main/java/com/isaac/ridelab`):
- `ble/control/TrainerControl.kt`, `SerialGattOperations.kt`
- `ble/ftms/FtmsControlProtocol.kt`, `FtmsControlSession.kt`, `FtmsGattControl.kt`
- `ui/screen/TrainerControlSummary.kt`

Updated main files:
- `ble/fec/TrainerErgProfile.kt`, `FecOverBleTrainerController.kt` (validation only, no encoding change)
- `ble/ftms/FtmsClient.kt`
- `data/model/GattDeviceState.kt`, `LiveRideData.kt`; `data/repository/ErgTargetSettings.kt`
- `service/RideRecordingService.kt` (control routing/readiness and protocol pinning only)
- `ui/ride/RideRoute.kt`; `ui/screen/DevicesViewModel.kt`, `AppShell.kt`, `RideScreen.kt`,
  `ErgTargetValues.kt`, `ErgTargetViewModel.kt`, `ConnectionDetails.kt`, `FecDebugControl.kt`, `WorkoutScreens.kt`
- `res/values/strings.xml`, `values-en`, `values-ja`, `values-zh-rTW`, `values-es`, `values-pt-rBR`, `values-fr`

Tests: new `ble/ftms/TrainerCompatibilityTest.kt`, `FtmsControlSessionTest.kt`, `SerialGattOperationsTest.kt`;
updated `ui/screen/ErgTargetValuesTest.kt`; Android tests `RidePresentationUiTest.kt`, `WorkoutUiTest.kt`.
Documentation: this file, PROJECT.md and ROADMAP.md.
