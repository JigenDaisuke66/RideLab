# Analytics Layer 1

## Data and algorithms

New pure Kotlin PowerAnalysisCalculator reads trainer power only. NP/IF/TSS algorithms,
BLE, FE-C, ERG profiles, service ownership and recording cadence are unchanged.

Power interval semantics: sort by elapsed milliseconds; duplicate timestamps are combined
by average power unless any value is null/negative, in which case that timestamp is invalid.
A valid nonnegative reading covers [its timestamp, next timestamp), only when that interval
is <=1,500 ms. This tolerates normal 1 Hz jitter while conservatively excluding missing
seconds, and always excludes the existing >3-second large gaps. There is no interpolation
or assumed final tail. Null intervals and excluded gaps split continuity. Zero remains valid.
Only points within the saved session duration are used. This step-function model is a
conservative estimate from sampled measurements, not a claim of continuous sensor coverage.

Best efforts: 5/15/30/60/300/1200/3600 seconds. Each continuous segment has a cumulative
watt-millisecond integral. For each duration evaluate starts at every interval boundary
and every boundary minus duration; binary-search integrals handle fractional intervals.
This finds the maximum elapsed-time-weighted mean without assuming row count equals time.
Cost is O(7*n*log n), not quadratic; insufficient coverage returns null. Raw samples stay intact.

Power duration uses the same seven results, shown as equally spaced labeled points.
No dense PDC or additional analytics. Missing points are omitted and lines never bridge them.

PR comparison: only COMPLETED rides compete. Candidates must have earlier start timestamps,
or the same timestamp with a smaller ID. Self/future/ACTIVE/ABORTED rides are excluded.
Selected ABORTED rides retain efforts/zones but receive no PR badges. Missing candidates
are ignored. No prior value yields First PR; comparisons use a 0.000001 W equality tolerance.
No PR rows are stored. Repository loads prior session metadata once and each ride's samples
once for all seven durations, sequentially, with only one prior ride's samples in memory.
Calculation runs on Default via ViewModel load, not during recomposition.

Zones use only saved analyticsFtpWatts. Exact fractional boundaries (no rounded-percent
holes): Z1 <55%; Z2 [55,75]%; Z3 (75,90]%; Z4 (90,105]%; Z5 (105,120]%;
Z6 (120,150]%; Z7 >150%. Interval durations accumulate in milliseconds. Zero counts as Z1;
nulls/gaps do not. Percentages use total valid power time. No FTP or no valid intervals
means unavailable. Displayed durations use whole seconds; internal totals retain milliseconds.

## Weight and database

Optional 20–300 kg, decimals (dot/comma) accepted, blank unset. SharedPreferences
training_settings/body_weight_kg stores the exact Double string, without a default.
RoomRideRepository.start captures current weight together with the existing FTP snapshot.
Schema v3 adds only nullable ride_sessions.bodyWeightKg REAL, via explicit MIGRATION_2_3;
MIGRATION_1_2 stays registered for existing v1 installs. No destructive migration.
Legacy rides keep null weight and unavailable W/kg; changing settings never updates snapshots.
W/kg is best-effort watts divided by that ride's saved kilograms; watts stay available.

## UI

Existing summary/core metrics/NP-FTP-IF-TSS followed by compact best efforts (including PR
and W/kg), power-duration chart, power zones, then existing time-series charts. Settings
adds one weight row and a small edit dialog consistent with FTP. All added labels use
Chinese/English resources. No new library dependency.

## Physical/manual checks

1. Update from the previous app WITHOUT uninstalling. Verify older rides, samples, charts,
   FTP snapshots and NP/IF/TSS remain readable. Old rides must have no weight/W/kg.
2. Settings: save 100.5 kg, restart, confirm it persists. Blank clears; invalid/out-of-range
   values cannot save. Test English/Chinese and decimal input.
3. Start a ride with configured weight and FTP, record >5 minutes with varied power,
   including zeros, background use and phone lock. End and view the seven efforts/PDC/zones.
   Longer-than-ride efforts must be unavailable, not zero. Gaps must not yield long efforts.
4. Change global weight/FTP, reopen that ride: W/kg and zones must still use its snapshots.
   Start another ride and verify the new snapshots. Unset weight should leave watts available.
5. Record a stronger later ride: new PR indicators should appear as appropriate. Return to
   the earlier ride: it must not compare against the newer result. Check tied/first results
   and an interrupted ride (no PR badge).
6. Check zones use only valid coverage, with zero power counted in Z1 and null gaps absent.
   Scroll and rotate on long ride detail; existing charts and active-ride behavior stay intact.

## Changed files

- analytics/PowerAnalysis.kt; data/repository/WeightSettings.kt (new).
- RideSession.kt, RideDao.kt, RideDatabase.kt, RideRepository.kt, RideLabApplication.kt;
  exported Room schema 3.json.
- HistoryViewModel.kt, HistoryScreen.kt, FtpViewModel.kt, AppShell.kt;
  PowerAnalysisViews.kt and WeightDialog.kt (new).
- Chinese/English strings.xml.
- PowerAnalysisTest.kt; RideFtpSnapshotTest.kt; tools/test_weight_migration.py,
  test_analytics_migration.py and test_history_query.py.
- PROJECT.md, ROADMAP.md and this guide.

Tests: focused new power/PR/weight/zone tests plus existing FTP/NP/IF/TSS/recorder tests.
Host SQLite tests exercise actual migration SQL, schema equality, preservation and PR query
filtering, along with existing history queries. App/Android-test compilation only; no APK
or device tests executed. Manual/physical acceptance remains pending.
