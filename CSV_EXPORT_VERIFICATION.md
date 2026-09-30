# Single-ride CSV export

## Scope and implementation
- History Detail has a compact Export CSV action above the summary card.
- MainActivity uses CreateDocument("text/csv"), retains the selected session ID across
  recreation, and resolves header resources using the current localized Activity when
  Save As returns. Cancel clears the pending ID without writing or showing an error.
- HistoryViewModel owns export progress/results. RideCsvDocumentWriter performs one
  transactional session check + sample query via the existing repository, then writes
  sequentially through a UTF-8 buffered writer on Dispatchers.IO. No giant CSV string.
- The transaction rejects missing/ACTIVE sessions, accepts COMPLETED/ABORTED, and
  prevents deletion during the read from yielding a silently empty export.
- Raw samples are authoritative. Ordering uses elapsedMillis, matching the existing
  Room query, even if the wall clock moved backwards. No interpolation/downsampling.
- Filename uses stored session start time in UTC, e.g. RideLab_2026-09-30_02-20-00Z.csv.
  It is language/timezone independent. Android may suffix filenames to avoid collisions.
- Timestamps use ISO-8601 UTC (Z), epoch/elapsed milliseconds are preserved directly.
- UTF-8 BOM and CRLF records are used. Commas, quotes and CR/LF are escaped; quotes
  are doubled. Numeric toString is locale-independent (decimal dot), without units.
- Null is an empty field; real integer zero is 0. No analytics or session metadata rows.
- Stored target power is the recorded requested target, not proof of trainer acceptance.
- No new permission, dependency, schema change, BLE/recording/control changes.

## Columns (in order)
| Chinese | English | Persisted source |
| --- | --- | --- |
| 时间戳_UTC | Timestamp_UTC | timestampEpochMillis converted to ISO UTC |
| Epoch毫秒 | Epoch_ms | timestampEpochMillis |
| 骑行时间毫秒 | Elapsed_ms | elapsedMillis |
| 骑行台功率_W | Trainer_power_W | trainerPowerWatts |
| 踏频_rpm | Cadence_rpm | cadenceRpm |
| 心率_bpm | Heart_rate_bpm | heartRateBpm |
| 目标功率_W | Target_power_W | targetPowerWatts |
| 外部功率_W | External_power_W | externalPowerWatts |
| 速度_km_h | Speed_km_h | speedKph |
| 阻力等级 | Resistance_level | resistanceLevel |
| 骑行台地址 | Trainer_address | trainerAddress |
| 心率传感器地址 | Heart_rate_sensor_address | heartRateSensorAddress |

## Physical verification (pending)
1. Install/update from Android Studio without uninstalling, preserving existing rides.
2. Open an existing completed ride, tap Export CSV, confirm the Android Save As picker.
3. Save to Downloads or another selected provider, confirm localized success and file.
4. Open in a text editor/spreadsheet, verify headers match RideLab's app language,
   including when system English and app Chinese differ.
5. Switch app language and export the same ride again. Headers should change; all
   data rows remain identical. Expect decimal dots and no localized units in cells.
6. Count CSV records excluding the header: count should exactly match stored sample
   count for that ride. Compare power/cadence/HR against charts (charts can downsample).
7. Verify missing values are blank, actual 0 W is 0, gaps remain in elapsed times,
   and recorded ERG target changes are visible. No inferred fields or filled rows.
8. Cancel Save As once: no error, write or partial file from the app.
9. Export a long ride: UI remains responsive. Rotate with Save As open and during
   writing; export still refers to the original selected ride, no duplicate write.
10. Test an interrupted ride and an empty ride (header only). Try an unavailable or
    failing document provider: concise failure, history unchanged, RideLabHistory log.
    An I/O failure after document creation can leave a partial file at the provider;
    it is never reported as success. Remove that file manually and retry.
11. Test existing active recording/ERG independently: this export does not own BLE.

## Changed files
- MainActivity.kt: SAF launch/result and localized result feedback.
- ui/screen/AppShell.kt, HistoryScreen.kt: action and progress wiring.
- ui/screen/HistoryViewModel.kt: export orchestration/state.
- export/RideCsvExporter.kt: pure serializer and deterministic filename.
- export/RideCsvDocumentWriter.kt: URI/UTF-8/IO handling.
- data/local/RideDao.kt, data/repository/RideRepository.kt: transactional raw read.
- res/values/csv_headers.xml, values/strings.xml, values-en/strings.xml: headers/UI.
- test/.../export/RideCsvExporterTest.kt: serialization and actual resource tests.
- androidTest/.../RideDatabaseTest.kt: selected/ordered/protected export query checks.
- CSV_EXPORT_VERIFICATION.md: this guide.

Device integration checks require Android Studio/device execution; no APK is built by
this task. Existing uncommitted history overflow-menu edits are preserved.
