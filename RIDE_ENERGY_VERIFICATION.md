# Estimated calories and food-energy comparisons

## Calculation and limitations

The detail screen derives this read-only estimate from existing mechanical work:

`estimated kcal = recorded mechanical kJ / (4.184 * 0.24)`

24% gross efficiency is a fixed model assumption, not an individual measurement.
The displayed sensitivity range uses 25% efficiency for the lower estimate and
20% for the upper. This is not a guaranteed accuracy/confidence interval. Actual
efficiency varies with the rider, intensity and cadence. No separate resting,
unloaded pedaling or zero-power metabolic expenditure is modelled. A genuine
zero-power recording yields zero *estimated power-derived* kcal, not a claim
that the body burns no energy. This is not total daily calorie expenditure.

Existing raw-work integration is unchanged: use valid nonnegative power over
positive adjacent intervals up to 1.5 seconds; no extrapolation of gaps, nulls
or final tail. Missing power means partial estimates, shown with coverage and a
warning. No covered power intervals means No data and no food equivalents.
No body weight/FTP is needed or guessed. No BLE, recording, Room/schema, saved
samples or CSV changes, and no new dependencies.

## Food references

Each alternative independently compares the same central estimate to a reference
portion, not a sum or a recommendation to eat that amount. Fractions are shown
to one decimal place. Reference energy values are rounded for visual comparison:

- Medium banana: 105 kcal (reference 105.02).
- Large hard-boiled egg: 78 kcal (reference 77.5).
- Cooked medium-grain white rice, one measuring cup: 242 kcal (reference 241.8).
  This is a measuring-cup portion, not an arbitrary household rice bowl.

Reference nutrition and portion preparation vary. Sources verified 2026-10-03:

- [Cycling gross efficiency review](https://kar.kent.ac.uk/36044/1/Hopker%20et%20al.%202009%20Efficiency%20and%20training%20review.pdf)
- [Medium banana](https://www.urmc.rochester.edu/encyclopedia/content?contentid=09040-5&contenttypeid=76)
- [Hard-boiled egg](https://www.urmc.rochester.edu/encyclopedia/content?contentid=01129-3+&contenttypeid=76)
- [Cooked white rice, one cup](https://www.urmc.rochester.edu/encyclopedia/content?ContentID=20051-1&ContentTypeID=76)

## Verification

1. Open a completed ride: the new card appears after the summary, before power.
   Confirm approximate kcal, sensitivity range, power coverage and three
   independent food comparisons. Existing mechanical work still displays kJ.
2. For 100 W recorded continuously for 60 minutes, expect about 359 kcal
   (344–430 kcal sensitivity range), approximately 3.4 bananas, 4.6 eggs or
   1.5 measuring cups of rice. Only uninterrupted valid intervals contribute.
3. Open a ride without power: No data and no food comparison. Open a ride with
   power missing for a period: show partial coverage and its note, without
   expanding to full ride duration or filling the missing power with zero.
4. Switch Chinese/English, test narrow screen/larger font, and check all labels,
   including estimation caveats and portion units. Existing ride detail,
   year/month folding, export/delete and sensor charts must remain available.

Run CyclingEnergyTest and RideInsightsTest plus application/instrumentation
compile checks. No APK generated; physical/visual verification remains manual.

Result: 20 relevant unit tests passed (8 energy + 12 existing insights);
compileDebugKotlin and compileDebugAndroidTestKotlin passed. Existing SDK/plugin
compatibility and instrumentation API deprecation warnings remain unrelated.

## Changed files

- app/src/main/java/com/isaac/ridelab/analytics/CyclingEnergy.kt
- app/src/main/java/com/isaac/ridelab/analytics/RideInsights.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/HistoryScreen.kt
- app/src/main/java/com/isaac/ridelab/ui/screen/RideDetailAnalysis.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values-en/strings.xml
- app/src/test/java/com/isaac/ridelab/analytics/CyclingEnergyTest.kt
- RIDE_DETAIL_INSIGHTS_VERIFICATION.md
- RIDE_ENERGY_VERIFICATION.md
