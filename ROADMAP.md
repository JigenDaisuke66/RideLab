# RideLab Roadmap

## Phase 0 - Project skeleton

Status: complete

Acceptance:

- project builds
- project launches
- localization works
- BLE permissions declared
- package structure established

Do not implement BLE connection yet.

---

## Phase 1 - BLE Scanner

Status: physically verified.

ThinkRider X2 MAX was discovered as Think X211-0013186. Advertised services
included 0x1818, 0xFFF0, and 6e40fec1-b5a3-f393-e0a9-e50e24dcca9e.
FTMS 0x1826 was not advertised; Phase 2 must inspect the GATT database
before concluding whether FTMS is available.

Goal:

Discover nearby BLE cycling devices.

Requirements:

- runtime Bluetooth permission handling
- BLE scanning
- display device name
- display Bluetooth address
- display signal RSSI
- detect advertised services when available
- classify possible:
    - trainer
    - heart rate sensor
    - power meter

Acceptance:

ThinkRider X2 MAX appears in scan results.

No trainer control yet.

---

## Phase 2 - FTMS Connection

Status: ThinkRider X2 MAX trainer path physically verified. Connection and GATT service discovery passed
physical-device verification. FTMS 0x1826 and Indoor Bike Data 0x2AD2 were absent.
Cycling Power 0x1818 exposes 0x2A63 Notify, 0x2A65 Read, 0x2A5D Read and
0x2A66 Write/Indicate. The requested Phase 2 extension reads standard 0x2A63
power/revolution notifications; live trainer power is physically verified.
An explicitly requested Phase 2 FE-C over BLE experiment adds manual 50–200 W
target-power writes, separate from FTMS/Cycling Power. The physical FEC1 service,
FEC2 Notify + CCCD, and FEC3 Write/Write Without Response layout is verified.
FE-C notifications, writes and ERG target-power control are physically verified.
See FEC_EXPERIMENT.md for the original protocol/test notes.
No FTMS/Cycling Power control point writes or later roadmap phases are included.

Goal:

Connect to a smart trainer.

Requirements:

- connect GATT
- discover services
- print/log available services and characteristics
- verify FTMS service 0x1826
- subscribe to Indoor Bike Data
- parse:
    - power
    - cadence
    - speed

Acceptance:

RideLab continuously displays live trainer data.

---

## Phase 3 - Heart Rate

Status: simultaneous trainer + heart-rate foreground operation physically verified.

Connect independently to Heart Rate Service 0x180D and subscribe to 0x2A37.
HeartRateClient owns a separate BluetoothGatt and typed state; connecting,
disconnecting or failing this sensor does not replace the trainer session.
Parse unsigned 8/16-bit BPM according to flags and validate optional fields.
The original foreground-only lifecycle is superseded by the requested Phase 6 service work.
See PHASE3_VERIFICATION.md.

Acceptance:

Live heart rate displayed simultaneously with trainer data.

---

## Phase 4 - Cycling Power Meter

Connect to Cycling Power Service 0x1818.

Keep trainer power and external meter power separate.

Acceptance:

Both power values can coexist.

---

## Phase 5 - Trainer Control

Implement FTMS Control Point.

Initial feature:

ERG target power.

Acceptance:

User changes target power and X2 MAX changes resistance accordingly.

---

## Phase 6 - Foreground Ride Service

Status: physically verified, including Home, app switching and screen lock.

RideRecordingService owns both BLE clients from initial preview connection onward.
Start ride promotes the same bound service to a started connectedDevice foreground
service, without new GATT connections. ACTIVE sessions survive UI lifecycle changes.
One bounded reconnect per role per ride; explicit End closes both sessions.
No sample recording/persistence or Phase 4/5 implementation is added by this task.
See FOREGROUND_SERVICE_VERIFICATION.md.

Acceptance test:

1. Start ride.
2. Lock phone.
3. Leave RideLab in background for 20 minutes.
4. Return to RideLab.
5. BLE connection remained active.
6. Elapsed time includes the full background period; live data and ERG work on return.
7. Ride sample persistence verification is tracked in Phase 7.

---

## Phase 7 - Local Storage

Status: current implementation; physical recording acceptance pending.

Room schema v1 stores service-owned sessions and approximately 1 Hz raw snapshots.
Start creates one ACTIVE session. End commits a final sample and marks the same
session COMPLETED before existing shutdown. Interrupted sessions recover as ABORTED.
No UI-owned recording or advanced analytics. See LOCAL_PERSISTENCE_VERIFICATION.md and RIDE_HISTORY_VERIFICATION.md.

---

## Phase 8 - Ride History

Status: minimal history/detail implementation; physical UI/persistence acceptance pending.

Display COMPLETED and ABORTED sessions, sample counts, timing and basic null-aware averages/maxima. ACTIVE sessions are excluded. Simple elapsed-time power/HR/cadence charts are implemented; physical chart verification is pending. Advanced analytics remain deferred. See RIDE_HISTORY_VERIFICATION.md and RIDE_CHARTS_VERIFICATION.md.

---

## Phase 9 - Training Analytics

Phase 1 implemented: optional FTP, NP, IF and TSS; physical verification pending. Historical FTP is captured at Start ride using the existing v2 column; details never backfill nulls. The explicit v1-to-v2 migration remains unchanged. See TRAINING_ANALYTICS_VERIFICATION.md. Other analytics below remain deferred.

Later metrics:

- average power
- max power
- normalized power
- IF
- TSS
- heart-rate zones
- power zones
- Pw:Hr / aerobic decoupling
- best 5 s
- best 15 s
- best 30 s
- best 1 min
- best 5 min
- best 20 min
- best 60 min

---

## Phase 10 - Fitness Profile

Later:

- FTP
- eFTP
- W/kg
- power curve
- aerobic efficiency
- long-term trends

Do not implement until core recording is stable.

## UI refactor round 1

Presentation-only Home/Ride/History/Settings navigation implemented. Device diagnostics retained internally; Developer Mode and extensive history redesign deferred. Physical/visual acceptance pending. See UI_ROUND1_VERIFICATION.md.

## Analytics Layer 1

Implemented fixed 5s/15s/30s/1m/5m/20m/60m best efforts, fixed-point power duration, earlier-completed-ride PR comparison, ride-time weight snapshots/W/kg, and 7 FTP power zones. Explicit Room v2-to-v3 nullable weight migration. Physical/manual verification pending; see ANALYTICS_LAYER1_VERIFICATION.md. No eFTP, training load, workout automation or ride-pipeline changes.

## Requested Workout Engine Phase 1 + FTP Ramp Test

Implemented service-owned monotonic workouts, explicit-confirmed automatic ERG,
pause/resume/skip, recovery/endurance/sweet-spot courses and FTP-relative ramp test.
Uses ride-time FTP snapshots and existing FE-C/recording/reconnect systems. Test
result is measured ramp-only best continuous 60s x75%, accepted only on explicit
confirmation through normal FTP settings. No workout editor, metadata migration,
automatic exhaustion detection or automatic FTP update. Unit/compile verification
complete; physical acceptance PENDING. See WORKOUT_PHASE1_VERIFICATION.md.
