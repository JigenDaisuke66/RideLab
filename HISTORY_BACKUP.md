# Local history backup

History has Export all rides and Restore backup actions using the Android document
picker. Single-ride CSV remains available for spreadsheet use; it is not a backup.

Export all CSV creates a standard ZIP containing summary.csv and one CSV per
finished ride. The summary links each unique sample filename to UTC start/end,
elapsed milliseconds, localized status and sample count. Sample CSVs reuse the
existing 12-column UTF-8/BOM exporter with localized headers, nulls blank and real
zeroes retained. Files can be opened in Excel after extracting the ZIP. This is
for external analysis, not app restore; .ridelab remains the full-fidelity backup.

The version-1 .ridelab archive is GZIP-compressed, contains a version marker and a
SHA-256 integrity trailer, and preserves every session/sample field except database
IDs (remapped on import). Only COMPLETED/ABORTED rides are included. FTP/weight
snapshots, sensor addresses, nulls, zeroes and elapsed-time gaps are retained.
It is not encrypted. App preferences and ongoing rides are excluded.

HistoryViewModel performs IO off the main thread through RoomRideRepository.
RideDao takes a consistent transactional snapshot and restores a validated archive
in a single transaction. Restore merges without updating/deleting existing rides;
only completely identical session metadata and samples (ignoring local IDs) are
skipped. Same start time with different data is retained as a separate ride.
Repeated import is idempotent, including duplicates within one archive.
Failure rolls back all inserts; active recording is never replaced.
No schema change, database file copying, or BLE/service modification.

Version mismatch, truncation, checksum errors, invalid status/order and invalid
numeric values fail before import. To bound resource use, version 1 supports up to
100,000 sessions, 2,000,000 samples and 128 MiB uncompressed payload. Reading and
snapshotting currently materialize data in memory; streaming larger archives is
future work. A failed export may leave an incomplete destination file; do not use
it unless the app reports export success.

Verification:
1. Finish several rides, including zero/missing sensor data. Export all from History.
2. Restore the same file: all existing rides should be skipped.
3. Delete one completed ride, restore: one added, others skipped.
4. Compare dates, duration, sample count, historical FTP/weight, charts and metrics.
5. Test on another installation/device; all finished rides should be restored.
6. Select a CSV/truncated file: failure message, no history changes.
7. Export during an active ride: active ride excluded and recording remains active.

Pure archive tests run on JVM. Room merge/remapping/rollback tests are device-side;
compile checks do not constitute physical execution of those tests.
