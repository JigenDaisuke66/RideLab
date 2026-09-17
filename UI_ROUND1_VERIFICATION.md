# UI refactor round 1

Four bottom destinations: Home, Ride, History, Settings. Tab/detail state is saveable
through navigation and Activity recreation. Switching tabs never invokes disconnect or
changes service ownership. All labels remain Chinese/English; the locale policy is unchanged.

Home shows friendly trainer/HR names and connection state, role-specific device choosers,
current FTP, most recent COMPLETED ride date/duration, and Start/Return to ride. Trainer
must be connected before Start is enabled; HR is optional. Choosers show friendly names
and reuse the exact scanner device objects and existing Activity permission/click callbacks.
MACs, RSSI, UUIDs, properties, packet bytes and subscription diagnostics are absent here.

Ride shows large trainer watts, fresh BPM/cadence, elapsed service time, connection status,
localized ride lifecycle/errors, Start/End, and ERG. It calls existing LiveRideData.snapshot
for freshness and missing-value handling, without new metrics or live NP.
ERG +/-5 changes the pending UI target, 50–200 W. Apply target explicitly calls the same
DevicesViewModel.sendFecTarget -> service -> verified FE-C implementation. No command is
sent on composition, navigation, connection or +/- adjustment. The last requested target
is shown separately; a successful write is not represented as trainer acceptance.

History/detail implementation is reused unchanged. Settings presents existing FTP editing
and language selection. Material 3 uses a restrained teal accent, light/dark palettes,
16dp cards/spacing and larger ride typography. No new dependencies.

Diagnostics (DevicesScreen, ConnectionDetails, HeartRateSummary, FecDebugControl) and all
Logcat logging remain available internally, but are not wired into the normal app shell.
Developer Mode, extensive history/detail styling, advanced visual polish and new analytics
are deferred. Same-name devices have no address disambiguation in this first-round picker.

## Files changed in this round

- MainActivity.kt: presentation entry point only; permission and lifecycle callbacks unchanged.
- ui/screen/AppShell.kt and DevicePicker.kt (new).
- ui/screen/HomeScreen.kt and RideScreen.kt (replace placeholders).
- ui/screen/RideControls.kt (vertical responsive actions; avoid duplicated elapsed label).
- ui/theme/Theme.kt; values/strings.xml and values-en/strings.xml.
- test/.../ui/screen/RidePresentationTest.kt (new).
- androidTest/.../RidePresentationUiTest.kt (new).
- PROJECT.md, ROADMAP.md, and this guide.

## Verification

56 focused JVM tests passed, including 2 presentation tests plus existing service,
recording, FE-C, role selection, FTP snapshots, charts and analytics regression tests.
Kotlin app/Android-test compile checks pass. Two new device-side Compose tests cover exact
device selection and no ERG send until Apply; compiled only, not executed in this task.
No APK generated. Hash comparison confirms BLE, service, data and analytics source trees
are unchanged from the beginning of this round.

Physical/visual acceptance remains pending:

1. Launch in Chinese and English, light and dark mode. Verify all four tabs fit, including
   at enlarged font size and after rotation. Home/Ride must contain no raw BLE diagnostics.
2. Home -> trainer Choose device -> Search -> select X2 MAX. Repeat for HR. Check names
   and connection state, including unavailable/permission-denied/Bluetooth-off handling.
3. Start ride -> Ride. Check power is dominant and live BPM/cadence match the sensors;
   absent or stale data must show No data, not fabricated zero.
4. Adjust ERG +/-5: trainer must not receive a command yet. Apply -> verify requested
   target and actual trainer response. Check 50/200 W bounds and busy/failed handling.
5. Switch through all four tabs while riding, rotate, press Home, lock screen and return.
   Ride must remain active, timer continuous, and BLE/ERG/recording unchanged.
6. End ride, verify it in existing History, charts and analytics. Settings FTP edits and
   language selection must persist; historical FTP stays tied to its session.
