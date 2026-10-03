# Ride detail: additional statistics and presentation

## Scope

Read-only analysis of existing repository samples. No BLE, FE-C, foreground
service, recording, Room schema, stored samples, existing NP/IF/TSS, best-effort,
power-zone or CSV logic changed. No new dependencies.

The detail screen groups each sensor's average, maximum and chart together.
Power also shows the existing best continuous 20-minute power, NP/IF/TSS and
ride-time FTP, plus average W/kg, mechanical work and time at zero power.
Cadence adds recorded pedaling time and fixed cadence-band duration bars.
Each sensor shows its own valid-time coverage. Charts retain existing splitting
and downsampling; area fills are drawn separately for each segment.

## Additional calculations

- Sort by elapsed time, retain the last row at duplicate timestamps, and ignore
  samples outside the session duration. Room normally prevents duplicates.
- Integrate a valid reading over `[sample.elapsed, next.elapsed)` only when the
  interval is positive and at most 1,500 ms, matching existing best-effort
  tolerance. Do not bridge longer intervals or assume a final tail.
- Mechanical work (kJ) = sum(power watts * interval milliseconds / 1,000,000).
  This is not metabolic calories; no calorie estimate is presented.
- Recorded distance (km) = sum(speed km/h * interval milliseconds / 3,600,000).
  Missing speed means no distance, including trainers that expose only power
  and cadence. Partial speed coverage means partial recorded distance.
- Coverage = valid sensor interval duration / full session duration. It is not
  the number of database rows. A zero-length session has no percentage.
- Real zero power, cadence and speed are valid. Null, negative or non-finite
  measurements are excluded; BPM must be positive. Missing data never becomes
  zero. No covered intervals means unavailable work/time/distance.
- Cadence bands: 0, >0 to <60, 60 to <80, 80 to <100, >=100 rpm. Percentages
  use valid cadence duration, not total ride duration; these are descriptive
  bands, not physiological training zones. Pedaling means valid cadence >0.
- Average W/kg uses the null-aware sample mean and the session's saved body
  weight. Unset historical weight means unavailable, not the current setting.
- Existing charts retain their >3-second recording-gap threshold. Statistics
  use the stricter 1.5-second integration threshold and can show less than full
  coverage even when the chart appears continuous.

## Verification

1. Open History and an existing completed ride. Confirm summary and export/
   delete controls remain available. Inspect power, cadence and heart-rate
   sections, followed by best efforts, power duration and recording coverage.
2. Confirm sensor colors distinguish the three charts, lines and translucent
   fills stop at missing-data gaps, and true zero readings remain at zero.
3. Check a ride without HR, speed, FTP or saved weight. Corresponding metrics
   must show No data; IF/TSS keep the existing FTP message. No invented km/kcal.
4. Record and finish a ride with trainer and HR while backgrounded/locked.
   Inspect the resulting charts, first/last elapsed time, sample count and
   sensor coverage. Disconnect a sensor briefly in a separate test ride and
   confirm missing intervals reduce its coverage without affecting the other
   sensor's coverage.
5. Compare with exported raw CSV: integrate only consecutive intervals up to
   1.5 seconds. A contiguous 60-second interval at 100 W equals 6.00 kJ; ten
   seconds at 36 km/h equals 0.10 km. Cadence band times sum to valid cadence
   duration and percentages sum to 100% before display rounding.
6. Switch Chinese/English and test a narrow display with larger font size.
   Labels should wrap; right-hand values remain readable. Scroll long rides
   and return/back to the history list.

Automated: RideInsightsTest covers integration, sensor independence, real zero,
null/invalid data, gaps, jitter, distance, cadence boundaries, empty/single rows,
ordering/duplicates and historical weight. Existing PowerAnalysisTest,
TrainingAnalyticsTest and RideChartsTest are also run. Compile application and
instrumentation sources only; no APK generated. Physical/visual acceptance
requires running the updated app on a device.
