# Incremental UI architecture

This cleanup preserves product behavior and the current navigation/saveable-state
mechanism. No dependency, service/BLE change, Room migration or APK generation.

Ownership:
- MainActivity: platform permissions, locale selection, CSV document picker and
  Activity-scoped ViewModel creation. No ride/GATT ownership changes.
- ui/screen/AppShell: navigation, state collection and presentation-only effects
  (screen awake, selecting the displayed trainer preference, refreshing FTP after
  accepting a test). Keep platform permission callbacks supplied by Activity.
- ui/ride/RideRoute: named ViewModel action wiring plus immutable RideUiState.
  State is a projection of existing flows; the service remains authoritative.
  RideNavigationActions supplies platform/navigation actions. RideScreen remains
  the existing presentation renderer; its low-level signature is retained for
  direct UI tests and incremental migration, not a new state owner.
- ui/settings/SettingsDestination: render SettingsUiState and invoke SettingsActions.
  Confirmation/edit dialog visibility stays local; successful save callbacks close
  dialogs exactly as before. Profile writes still go through FtpViewModel.
- ui/settings/AppearanceViewModel: observe existing appearance preferences and
  apply typed AppearanceAction to the latest persisted selection. This prevents
  independent app/overlay selections from overwriting one another. Listener is
  released on ViewModel clear; no ride/device commands are accessible here.
  It also exposes the language selection via AppLocale and handles language commands.
  SettingsUiState carries the selected AppLanguage; SettingsActions forwards language
  intent through the Activity adapter for legacy recreation. No locale persistence
  lives in SettingsDestination or AppShell. Native Android 13 configuration changes
  refresh this projection; platform applicationLocales remains authoritative.
- ui/screen/AppearanceControls: state-only reusable rendering, no repository writes.
- ui/shared/OverlayControl: shared permission/help/enable UI used by Ride and Settings.
  The Ride and Settings entry points expose a prominent enable button and an adjustment dialog.
  AppearanceViewModel persists only proportional size and transparency via OverlayPreferencesStore.
  Legacy transparency is preserved; scale_percent falls back to the former width_percent,
  clamped to 60–140. Independent font/width/height and overlay style/palette controls are retired.
  CapsuleView renders the SVG-inspired dark horizontal capsule and is shared verbatim by
  the real window and CapsulePreview. The latter uses explicitly labelled example data only,
  requires no sensor, service or overlay permission, and cannot affect recording.
  CapsuleGeometry fits both axes proportionally; the window keeps drag, close and collapse.
  OverlayReadout still uses the service RideState clock, including pauses. No timer owner changed.
  Short power bars and HR/cadence traces use real bounded history. They do not claim FTP zones
  or ERG acceptance. Legacy OverlayPanel/OverlayTrendView rendering has been removed.
  The requested power-band extension now projects the saved ride FTP into CapsuleView:
  overlayPowerBand groups the existing seven zones into four display colors, and
  recentOverlayPower retains fifteen time slots with gaps. Missing FTP stays neutral.
  The service's overlay loop reads that session snapshot once, through the repository;
  Canvas never queries Room or settings, and global FTP changes do not recolor a ride.
- ui/theme/RideLabComponents: keep existing RideLabCard, MetricTile, StatusPill and
  SettingRow. Extraction solely for renaming these would not reduce duplication.
- HistoryViewModel owns repository queries, analytics orchestration, deletion and
  CSV export; FtpViewModel and ErgTargetViewModel retain their existing responsibilities.
- RideRecordingService exclusively owns BLE, workout timing and recording. Untouched.

Where to add features:
Optional AI connection setup lives in ui/ai. AiSettingsRoute collects its separate
ViewModel; AiSettingsCard renders immutable state and dispatches typed edits. The
controller/store/probe own configuration, encrypted persistence and explicit network
requests. MainActivity supplies this route through an optional settings content slot
in AppShell/SettingsDestination, preserving existing ride/settings callbacks. There
is no BLE, Room, ride state or trainer tool reference in the settings component. New providers
belong in ai/AiProvider and their wire protocol adapter, never Compose. See AI_SERVICES.md.
The requested analysis extension has a separate AiAnalysisRoute/immutable state and
ViewModel/controller. AiRideDataRepository is the only SQL data adapter: a bounded
OPEN_READONLY handle on the existing Room file plus bound current-profile values.
Existing repositories/calculators provide exact metrics. AI has no write or trainer
tools. Instructions live in assets/ai_skills; native tool protocols and budgets live
in ai, never in Compose. Saved credentials are reused without passing them into the
analysis renderer. See AI_ANALYSIS.md for disclosure, data dictionary and verification.

Home/History/Settings companion polish keeps screen/action contracts intact. Home puts
device readiness before Start and uses compact profile values. HistoryBackupControls
is composed exactly once in toolbar actions (including detail mode) so document-picker
launchers survive menu dismissal. Its menu changes presentation only; existing export/
restore methods and result state remain authoritative. Settings appearance/language
dialogs are local UI state; native names, preference actions and save/cancel paths stay.
Data management is centralized in History without a new settings navigation contract.
See HOME_HISTORY_SETTINGS_VERIFICATION.md for callback and document-picker checks.

The selected professional-meter Ride layout uses RideToolbar for compact overlay
actions. It reuses OverlaySettingsDialog from the existing settings control without
moving permissions or preference writes into the renderer. LiveRideCharts now selects
one existing series in a compact plot; selection is saveable presentation-only state.
RideControls/ErgControls only rearrange existing actions with a narrow/large-font fallback.
All public signatures and service/ViewModel paths remain as before; see RIDE_METER_VERIFICATION.md.

The presentation polish adds MetricValueText for baseline-aligned, already-localized
values while preserving complete accessibility text. RideHeader/RidePowerCard and
SettingsSection/LanguageOption are private layout helpers. Existing screen and shared
component signatures, state sources and event paths remain unchanged. Use shared
RideLabCard/MetricTile/StatusPill/SettingRow for the same surface/spacing hierarchy.
See UI_POLISH_VERIFICATION.md for callback, navigation and visual smoke checks.

Ride page live curves use a bounded five-minute LiveRideTrend in DevicesViewModel,
sampling existing fresh sensor state once per second while the Activity is visible.
RideRoute passes immutable chart state to LiveRideCharts. This UI buffer is not
recording: it does not backfill background/process-loss intervals from Room.
Pause/missing readings and missed updates break segments. Rotation/tab switching
retain the ViewModel buffer; a new ride clears it. RideChart accepts an optional
start time for scrolling windows; history charts retain their zero-based default.

Put new feature route/contracts beside ui/ride or ui/settings. Add settings controls
as state plus events, with writes in a ViewModel and existing repository. Continue
using existing ui/screen home/history/workout/devices files until a concrete change
justifies extracting that feature; no mass moves or compatibility wrappers for names.
Use ui/shared only when multiple screens actually share a component. Calculations
belong in analytics/domain helpers, invoked off the rendering path by the existing
ViewModel. Never construct a second recording session in a UI state class.

Preserved interaction checks:
- Trainer/HR picker and connect/disconnect wiring are unchanged.
- Start keeps its Activity permission path; End/Pause/Resume/ERG Apply and workout
  commands forward once to the same DevicesViewModel methods via RideRoute.
- Pending ERG edits and persistence remain with ErgTargetViewModel; applying remains
  explicit. No preference restoration dispatches trainer commands.
- Settings FTP/weight save success/cancel, language and overlay help retain behavior.
- History navigation, delete confirmation, CSV picker, source data and saveable UI
  states are unchanged. Theme reads still use the existing observer for live updates.

Verification: run all existing unit tests and compile app/Android tests. New pure
action tests check independent preference edits and follow-theme behavior. New UI
test checks app versus overlay palette buttons dispatch exactly once to their
respective event. Device UI tests are compile-checked, not executed without an APK.
Manual smoke test: switch all tabs/back; open/cancel/start workout; select both
devices; edit/cancel/apply ERG; pause/end ride; save/cancel FTP and weight; change
app/overlay appearance and language; reopen settings; open/delete/export a ride;
drag/collapse/change opacity of overlay during background recording.

Remaining debt: older home/history/workout/device renderers still live in ui/screen;
RideScreen still has its original fine-grained renderer parameters; theme preference
observation remains a read-only Compose adapter. These are deliberately retained,
not claimed as fully migrated. No generic event bus or extra domain layer was added.

Changed files for this cleanup:
- MainActivity.kt
- ui/screen/AppShell.kt, ui/screen/AppearanceControls.kt
- ui/settings/SettingsDestination.kt, SettingsContract.kt, AppearanceViewModel.kt
- ui/shared/OverlayControl.kt
- ui/ride/RideRoute.kt
- test ui/settings/AppearanceActionTest.kt
- androidTest AppearanceActionsUiTest.kt
- AGENTS.md, UI_ARCHITECTURE.md
