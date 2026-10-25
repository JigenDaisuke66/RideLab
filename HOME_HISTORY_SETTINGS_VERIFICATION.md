# Professional-meter companion pages

Presentation-only continuation of the accepted Ride layout. HomeScreen, HistoryScreen,
HistoryBackupControls and SettingsDestination keep their public signatures and existing
actions/data. No service, BLE, Room schema, analytics, recording or ViewModel changes.

Home: devices first, then the same gated Start/Return action and workout entry.
FTP/weight use a compact unboxed summary. Latest completed ride uses the existing
selection and data, with explicit loading/error/empty states and the History entry.
No new recommendations, scores or invented measurements.

History: year/month grouping, newest-first data and selected-detail state are preserved.
HistoryRideRow emphasizes duration and keeps date, status, power/HR summary and the
delete menu/confirmation. Export/backup/restore live in the top-right data menu.
The backup component stays composed in toolbar actions, rather than mounting only
inside a menu or sheet: activity-result launchers and the existing ViewModel collection
remain alive after dismissing the menu and while choosing a document. Results use
localized confirmation dialogs; no data operations are moved into the screen.

Settings: profile, overlay, appearance, language and a data-location note. Appearance
and language choices open local dialogs; native language names are retained. Selecting
a language forwards the same action once and closes the dialog. Theme changes still
save immediately through the existing AppearanceActions; FTP/weight dialogs keep
their existing success/cancel behavior. Data operations are intentionally centralized
in History, without adding a second HistoryViewModel or changing navigation contracts.

Checks: compile app/device tests and run HistoryGroupsTest, RidePresentationTest,
AppearanceActionTest, ThemeColorsTest and TranslationResourcesTest. The updated
UiPolishPresentationTest follows the new language-dialog path. HubNavigationUiTest
covers independent Home actions and the persistent backup toolbar slot. Device tests
are compile-only here; physical execution remains required.

Physical verification:
1. Home: no trainer -> Start disabled, select trainer/HR independently. Connected ->
   Start once. Active/paused ride -> Return, without creating another session.
2. Select workouts, open recent ride/history, test empty/error/loading history states.
3. Fold year/month and open details; menu -> Delete -> Cancel/Confirm unchanged.
4. Data menu -> export CSV ZIP / backup; save a file and check completion. Restore
   a valid/invalid/cancelled document; existing merge, duplicate and rollback rules hold.
5. Open language picker, switch each language and System, restart and verify persistence.
6. Open appearance, change palette/mode, close/reopen; values persist and callbacks do
   not touch devices. Edit/cancel/save FTP and weight, open offline overlay preview.
7. Check narrow/landscape/large-font layouts and all seven languages. No APK/AAB is
   generated as part of compile/unit verification.
