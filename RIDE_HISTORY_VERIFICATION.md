# Minimal ride history verification

History reads the existing Room database through RoomRideRepository and HistoryViewModel.
The single grouped LEFT JOIN query excludes ACTIVE sessions, orders by start time and ID
newest first, counts actual samples, and computes SQL AVG/MAX (ignoring nulls, including
real zeros). Empty sessions have count zero and null metrics/boundaries. These are sample
averages, not time-weighted averages. No schema, dependencies or recording/BLE changes.

## Physical procedure

1. Launch from Android Studio. Open Ride history from the top bar. On a fresh database,
   expect the localized empty state. Existing completed/interrupted rides should appear.
2. Return to Devices, connect trainer and HR, and Start ride. Open history during the
   ride: the ACTIVE session must not appear as a finished ride. Return to Devices;
   power/cadence/HR/ERG should still work.
3. Pedal for about ten minutes. Spend part in another app and part with the phone locked.
   Return, rotate the screen, and End ride. Wait for IDLE before opening history.
4. Expect exactly one new Completed ride at the top, about ten minutes long, and around
   600 samples (often 601 including endpoints; scheduler delay can reduce this).
5. Tap it. Check start/end time, duration, count, first elapsed near 00:00:00 and last
   elapsed near the full ride duration. Check plausible average/max power, BPM and cadence.
   Counts and endpoints help detect missing coverage but cannot prove there are no gaps.
6. Back returns to history, then Devices. Reopen the app and verify the ride remains.
   Change language and rotate while viewing details: selection and translated labels remain.
7. For no-data behavior, record a separate brief ride with an unavailable sensor. Its
   metrics should say No data / 暂无数据 when all samples for that metric are null, not zero.
8. Optional interruption check: force-stop during a separate ride, reopen, and wait for
   service recovery. History should show Interrupted, never Completed for that ride.

## Checks

- `tools/test_history_query.py`: four host SQLite tests execute the actual DAO query
  against the exported Room schema: ordering/ACTIVE filtering, counts and null-aware
  averages/maxima including zeros, empty history, empty sessions/all-null measurements.
- Focused existing RideRecorderTest, LiveRideDataTest and RideSessionTest regression tests.
- Kotlin app and Android-test compilation; no APK generation or device tests in this task.
- RideDatabaseTest includes a device-side Room Flow/projection mapping test; run from
  Android Studio if desired. Physical acceptance remains pending.

## Files changed in this task

- MainActivity.kt: history navigation only; existing BLE and lifecycle callbacks unchanged.
- ui/screen/HistoryScreen.kt and HistoryViewModel.kt.
- data/model/RideHistoryEntry.kt, data/local/RideDao.kt,
  data/repository/RideRepository.kt: read-only history query and projection.
- res/values/strings.xml and res/values-en/strings.xml.
- androidTest/.../RideDatabaseTest.kt and tools/test_history_query.py.
- PROJECT.md, ROADMAP.md and this document.

Existing uncommitted persistence implementation files predate this task.
