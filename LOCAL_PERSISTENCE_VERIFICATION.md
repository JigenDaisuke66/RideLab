# Local ride persistence (Phase 7)

## Scope and dependencies

The physically verified BLE clients/scanner, FE-C packet encoder, GATT ownership,
foreground promotion, wake lock and reconnect paths are retained. Persistence is an
addition to the existing service lifecycle, with shutdown delayed only for saving.

- AndroidX Room runtime/compiler/Gradle plugin: 2.8.5 (stable).
- KSP plugin: 2.3.12, using KSP2 with the existing AGP built-in Kotlin.
- No separate room-ktx dependency is needed; coroutine support is in the runtime.
- Version catalog, root/app Gradle files configure KSP and Room schema export.
- Database: `ridelab.db`, version 1. Exported schema is committed under
  `app/schemas/com.isaac.ridelab.data.local.RideDatabase/1.json`.
- No destructive fallback; future schema changes require explicit migrations.

References: [Room stable releases and Kotlin/KSP setup](https://developer.android.com/jetpack/androidx/releases/room),
[KSP releases](https://github.com/google/ksp/releases).

## Data and recording

`ride_sessions`: generated id, start/end epoch milliseconds, monotonic duration,
ACTIVE/COMPLETED/ABORTED status, initial trainer and HR names/addresses, last requested
target watts. An internal nullable activeSlot unique index permits only one ACTIVE
session, while completed/aborted sessions use NULL.

`ride_samples`: composite key (sessionId, elapsedMillis), FK to the session, wall
timestamp, elapsed-from-start milliseconds, nullable trainerPowerWatts, cadenceRpm,
heartRateBpm, targetPowerWatts, externalPowerWatts, speedKph, resistanceLevel and both
source addresses. The leading primary key also indexes session queries. Sources
are included per sample so later device replacement is distinguishable from initial
session metadata. External power/resistance stay null because no such source exists
in the verified path. Cycling Power does not synthesize speed from wheel counts.

The service samples LiveRideData from the existing latest trainer and HR state once
per monotonic second. Sensor values that are absent, disconnected or at least five
seconds old are NULL; stale values are not repeated indefinitely as fresh samples.
FE-C target is explicitly the last REQUESTED target in client state, not a measured
or acknowledged applied resistance. Target loss after a disconnect yields NULL in
samples; session lastTargetPowerWatts retains the last non-null recorded request.

Each sample is committed directly, along with a session duration/target checkpoint,
in a Room transaction. There is no memory buffer or notification-to-database path.
Room suspend operations run storage work off the main thread. Late scheduling skips
missed slots instead of manufacturing backfilled readings. Epoch timestamps are raw
wall time and can jump if the clock changes; duration/ordering use monotonic time.

Start remains STARTING while a single database session is created, then publishes
ACTIVE with sessionId in service RideState and begins sampling. The timestamp is
captured at foreground promotion, so a short database startup delay is included.
Duplicate Start is gated by the lifecycle, serialized recorder start returns its
existing ID, and the DB unique index is a final safeguard. Rebind/rotation/UI hiding
never call recorder.start or affect its loop.

End changes phase to STOPPING immediately, captures the final snapshot/time, waits
for startup if needed, cancels/joins the sampling job, and commits a final sample
plus COMPLETED/end/duration in one transaction. The final sub-second sample is
included unless an identical elapsed timestamp was already saved. Only then does
the service close BLE, remove foreground mode and release the wake lock. In-flight
transactions are non-cancellable so the finalizer knows which writes committed.

Storage failure is logged and represented as a localized recording error; the ride
is stopped and abort is attempted. Existing rows remain. Detectable service destruction
attempts ABORTED asynchronously. On next service startup/new recording, remaining
ACTIVE rows become ABORTED using their last persisted checkpoint and last sample
timestamp (or start time if no samples). Downtime is not counted, incomplete rides
are never silently COMPLETED, and no active ride/ERG command is automatically resumed.
Uncommitted work at process death cannot be guaranteed; there is no larger recovery framework.

## Tests

22 focused JVM tests passed: RideRecorderTest (9), LiveRideDataTest (3),
RideSessionTest (10). Covers session uniqueness/reuse, ownership, nullable values,
1 Hz gating, monotonic duration, final sample and finalization, abort/recovery,
storage errors and cancellation while a write is in flight. Recorder tests use an
in-memory repository fake. Existing protocol tests are preserved.

Host SQLite checks execute the exported Room schema and actual DAO checkpoint/recovery
SQL, validating unique ACTIVE slot, FK, null fields and recovery progress. KSP validates
the Room DAO queries and generates the database. RideDatabaseTest adds three Android
in-memory Room integration tests; these were compiled, not run. No APK was built.

## Ten-minute physical procedure

1. Run from Android Studio. Connect X2 MAX and HR; confirm power, cadence, BPM and ERG.
2. Press Start ride once. Capture `RideLabRide` → `Ride ACTIVE sessionId=...`.
3. Ride for about ten minutes, including several minutes in another app and several
   minutes locked. Return/rotate during the ride; confirm ACTIVE and the same sessionId.
4. Vary effort/ERG target to make samples distinguishable. If testing missing data,
   briefly disconnect HR and verify those sample BPM fields become NULL while trainer
   power continues. Do not interpret an absent cadence source as zero cadence.
5. End ride and wait for IDLE. Capture `Recording COMPLETED sessionId=...` before the
   GATT/foreground shutdown log. No completion should be reported on storage failure.
6. Inspect the database below. Expect ONE new COMPLETED session, duration near 600000 ms,
   and approximately 600 rows (typically around 601 with initial/final snapshots).
   Small scheduling variation is normal; extended gaps in the locked interval are not.
7. Start/end a second short ride: it must get a different ID without modifying the first.
8. Optional crash check: force-stop a separate test ride, reopen the app and inspect
   recovery. That session should become ABORTED at the last saved progress, not COMPLETED
   or extended to the reopening time. This deliberately ends BLE and is not a normal End.

## Inspect and count data

In Android Studio: **View → Tool Windows → App Inspection → Database Inspector**.
Select the running debuggable `com.isaac.ridelab` process and `ridelab.db`. Keep the app
open after End; the application-owned database remains available for inspection.

```sql
SELECT id, status, startTimeEpochMillis, endTimeEpochMillis, durationMillis,
       trainerName, heartRateSensorName, lastTargetPowerWatts
FROM ride_sessions ORDER BY id DESC;

SELECT sessionId, COUNT(*) AS sampleCount,
       MIN(elapsedMillis) AS firstElapsedMs, MAX(elapsedMillis) AS lastElapsedMs,
       SUM(trainerPowerWatts IS NULL) AS missingPower,
       SUM(cadenceRpm IS NULL) AS missingCadence,
       SUM(heartRateBpm IS NULL) AS missingHeartRate
FROM ride_samples GROUP BY sessionId;
```

Replace 1 with the session ID from the start log:

```sql
SELECT timestampEpochMillis, elapsedMillis, trainerPowerWatts, cadenceRpm,
       heartRateBpm, targetPowerWatts, speedKph, externalPowerWatts,
       trainerAddress, heartRateSensorAddress
FROM ride_samples WHERE sessionId = 1 ORDER BY elapsedMillis;

SELECT elapsedMillis, elapsedMillis - previousElapsed AS gapMs
FROM (SELECT elapsedMillis,
             LAG(elapsedMillis) OVER (ORDER BY elapsedMillis) AS previousElapsed
      FROM ride_samples WHERE sessionId = 1)
WHERE elapsedMillis - previousElapsed > 2500;
```

Export tables through Database Inspector if needed. Do not copy only the main database
file while it is open and ignore its WAL; that can omit recently committed data.
Capture RideLabRide recording recovery/start/completion/error logs and the existing
RideLabGatt/RideLabHeartRate streams. Send the session row, sample count, gap query
results and sensor samples spanning foreground/background/locked periods.

## Files for this phase

Build: `.gitignore`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.
Data: `RideLabApplication.kt`, `LiveRideData.kt`, model `RideSession.kt`, `RideSample.kt`,
`RideRepository.kt`, `RideDao.kt`, `RideDatabase.kt`, exported schema `1.json`.
Service/UI: `RideRecorder.kt`, `RideRecordingService.kt`, service `RideSession.kt`,
`RideControls.kt`, Chinese/English `strings.xml`.
Tests: `RideRecorderTest.kt`, `LiveRideDataTest.kt`, `RideSessionTest.kt`, `RideDatabaseTest.kt`.
Documentation: `AGENTS.md`, `PROJECT.md`, `ROADMAP.md`, this verification guide.
