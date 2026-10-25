# Presentation-only polish

Scope: shared cards/metrics/status/settings rows, Home, Ride, History and Settings.
No ViewModel, subscription, navigation, callback contract, BLE, service, repository,
database, workout scheduler or analytics changes. No dependency and no APK/AAB.

- Cards use 16 dp corners, zero elevation and an 8% on-surface outline.
- Page spacing follows 24 dp with 8/12/16 dp internal gaps.
- RideHeader and RidePowerCard extract display-only layout from RideScreen.
  Power and its unit share a baseline; missing power uses a restrained placeholder.
- MetricValueText separates already-localized numeric values and units only for
  layout. It preserves the complete accessible phrase and never converts values.
- StatusPill animates color; device-card sizing animates; keyed history rows animate
  insertion/removal, with a rotating fold indicator.
- History loading/error/empty states use structured surfaces, progress and icons.
  Empty-state guidance is translated in all seven languages.
- SettingsSection and LanguageOption group existing controls with selected-state
  color feedback. Every existing action/confirmation is forwarded unchanged.

Verification:
1. Switch Home/Ride/History/Settings, back and ride detail; no navigation change.
2. Select trainer/HR, open disconnect menu, and confirm each action occurs once.
3. Check power 0, four-digit power, stale/disconnected sensors and all no-data states.
4. Adjust/cancel/confirm ERG entry; only Apply sends. Check +/- and disabled states.
5. Start/pause/resume/end; End still requires confirmation. Workout controls unchanged.
6. Fold history year/month, open detail, cancel/delete and export, preserving selection.
7. Save/cancel FTP and weight, change language, open offline overlay settings and
   drag sliders. No theme/language action must dispatch an unrelated command.
8. Check light/dark palettes, all languages, large font and a narrow/landscape viewport.

UiPolishPresentationTest covers accessible metric text, one-shot language forwarding
and empty history. Existing ride/picker/ERG/overlay UI tests remain intact.
Device tests are compile-checked; they are not physically executed by that check.
