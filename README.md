<div align="center">

# RideLab

**Turn your Android phone into an indoor cycling dashboard.**

BLE trainer control · Heart rate · Structured training · Background recording · Local analytics

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

[Compatibility](TRAINER_COMPATIBILITY.md) · [Releases](https://github.com/JigenDaisuke66/RideLab/releases) · [Issues](https://github.com/JigenDaisuke66/RideLab/issues)

RideLab is a native Kotlin and Jetpack Compose application for indoor cycling. Connect a trainer and a heart-rate sensor, set an ERG target, follow a workout and review the ride locally. No account or cloud service is required.

## Features

- **Live dashboard:** power, heart rate, cadence, elapsed time and five-minute live charts.
- **Manual ERG:** numeric entry, ±5 W adjustments, explicit Apply and saved targets per trainer. Restoring a preference never sends a command.
- **Training:** built-in courses, FTP ramp testing and an initial test that does not require a known FTP. Automatic workout ERG is currently restricted to recognized ThinkRider X2 MAX devices.
- **Background rides:** service-owned connections and approximately 1 Hz Room recording while switching apps or locking the screen.
- **Ride lifecycle:** start, pause, resume and finish. Pause freezes active time, sampling and workout progress while retaining connections and applied resistance. Finishing retains connections until explicitly disconnected.
- **History:** collapsible year/month groups, individual deletion, sensor charts and recording-continuity details.
- **Analytics:** averages/maxima, NP/IF/TSS, fixed-duration best power, power zones, W/kg, historical comparisons and power-derived calorie estimates with food-energy equivalents.
- **Portable data:** single-ride CSV, all rides as a CSV ZIP, and full ride backup/restore.
- **Transparent floating HUD:** time, power, BPM and cadence, optional sparklines, drag/collapse and adjustable size, font and opacity.
- **Appearance:** five palettes, light/dark/system modes, three overlay layouts and seven UI languages.

Missing readings remain missing; real zeroes remain valid. FTP and body weight are optional and snapshotted at ride start. Later preference changes do not overwrite historical snapshots. IF/TSS require a saved ride FTP; NP requires sufficient continuous power data. Calories are estimates, not individual metabolic measurements.

## Device compatibility

Control capabilities are discovered from actual GATT services and characteristics. Power measurement alone does not imply ERG support.

| Device / protocol | Current behavior | Verification |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power measurement + FE-C over BLE ERG, 50–2000 W; existing automatic workouts | Primary physically verified trainer |
| FTMS trainer | Indoor Bike Data; manual ERG when features, control characteristics and a valid reported range are available | Protocol/unit-tested; hardware verification pending; automatic workouts disabled |
| Other FE-C over BLE trainer | Manual ERG with a conservative 50–200 W range when the complete FEC1/FEC2/FEC3 layout is present | Unverified devices; automatic workouts disabled |
| Cycling Power only | Power measurement and cadence when crank revolution data exists | No ERG inferred; separate external power-meter role remains incomplete |
| BLE heart-rate sensor | Standard Heart Rate Service, UINT8/UINT16 BPM | Simultaneous operation with X2 MAX verified; other models need testing |

The verified X2 MAX exposes Cycling Power and FE-C, not FTMS. When both control layouts are available, FE-C takes precedence. Targets are never dispatched through both protocols.

FTMS requires a valid device-reported power range and control authorization. A successful GATT write alone is not treated as protocol acceptance. Reconnecting never replays a saved target automatically.

RideLab does **not** claim universal trainer compatibility. See the [compatibility guide](TRAINER_COMPATIBILITY.md) for capability rules and safe hardware testing.

## Getting started

1. Use an Android 12+ device with Bluetooth LE. Install an APK if provided in [Releases](https://github.com/JigenDaisuke66/RideLab/releases), or run the source with Android Studio. An AAB cannot be installed by tapping it like an APK.
2. Enable Bluetooth and grant nearby-device permissions.
3. Select the trainer and heart-rate sensor independently; check the live readings.
4. Optionally set FTP and body weight. An initial FTP test is available on supported trainers when FTP is unknown; accepting a result requires confirmation.
5. Start a ride. Allow notifications to keep its background status visible.
6. For manual ERG, edit a target and explicitly Apply. Pause/resume as needed, finish, then open History.

The floating HUD requires the separate “display over other apps” permission. Enable and customize it from Ride or Settings. Folded mode retains time and power.

Test a short ride and a locked-screen ride on your phone first: background behavior also depends on device policies. See [background verification](FOREGROUND_SERVICE_VERIFICATION.md).

## Export and restore

| File | Use | Restore into RideLab |
| --- | --- | --- |
| Single `.csv` | One ride’s samples, readable in Excel/WPS | No |
| CSV `.zip` | All finished rides: `summary.csv` and one sample CSV per ride | No |
| `.ridelab` | Full completed/interrupted ride backup, including samples and historical FTP/weight | Yes |

Bulk export and restore are at the top of History; single-ride CSV is in ride details. Restore merges records, preserves existing data and skips identical rides. Active rides and app settings are excluded from backups.

CSV blanks mean unavailable data, not zero. Exports may contain sensor Bluetooth addresses. Backups are compressed and integrity-checked, not encrypted. See [format details and limits](HISTORY_BACKUP.md).

## Languages

Simplified Chinese, English, Japanese, Traditional Chinese (Taiwan), Spanish, Brazilian Portuguese and French. The default follows the system, falling back to Simplified Chinese for unsupported languages. Explicit choices persist; Android per-app language selection is supported.

## Development

Use Android Studio compatible with the project’s Android Gradle Plugin, preferably its bundled JDK 21. Install the configured Android SDK: compile SDK 37.1, target SDK 36, minimum API 31. Dependency and wrapper versions are defined in [libs.versions.toml](gradle/libs.versions.toml) and [gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties).

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
```

Open the project, sync Gradle and run `app` on a real Android device for BLE testing.

Unit tests and compilation checks without generating an APK:

```bash
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Compiling device tests does not execute them; protocol unit tests do not establish hardware compatibility.

Service-owned BLE and recording live under `service/` and `ble/`; repositories and Room under `data/`; workouts, analytics, export, localization and UI have their own packages. Compose renders state and dispatches events. Read [PROJECT.md](PROJECT.md), [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md) and [ROADMAP.md](ROADMAP.md) before contributing.

## Feedback

Report issues or hardware results with the app version, phone/Android version, trainer/sensor model and firmware, reproduction steps, discovered services, selected protocol/range and relevant logs:

- `RideLabGatt`: discovery, connections and trainer commands.
- `RideLabWorkout`: course behavior.
- `RideLabOverlay`: floating window.

Remove device addresses or personal ride data before sharing if desired. Keep contributions focused, include relevant tests and add all seven translations for new UI strings.

Cloud sync, accounts, FIT/TCX export, PowerMatch and slope simulation are not currently provided. Feature implementation and physical acceptance status are documented separately in the repository.
