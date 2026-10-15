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
  Drag/collapse/opacity/resize remain in the existing native ui/overlay window.
- ui/theme/RideLabComponents: keep existing RideLabCard, MetricTile, StatusPill and
  SettingRow. Extraction solely for renaming these would not reduce duplication.
- HistoryViewModel owns repository queries, analytics orchestration, deletion and
  CSV export; FtpViewModel and ErgTargetViewModel retain their existing responsibilities.
- RideRecordingService exclusively owns BLE, workout timing and recording. Untouched.

Where to add features:
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
