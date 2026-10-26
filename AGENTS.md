# RideLab Agent Instructions

## Project

RideLab is a native Android indoor cycling application.

Package:

com.isaac.ridelab

Technology:

- Kotlin
- Jetpack Compose
- Android minSdk 31
- targetSdk 36
- Kotlin DSL
- Native Android Bluetooth LE APIs
- Single Activity
- Foreground Service for active ride recording

## Core requirements

RideLab must support:

1. Bluetooth FTMS smart trainers.
2. Bluetooth Heart Rate Service sensors.
3. Bluetooth Cycling Power Service power meters.
4. Multiple BLE devices connected simultaneously.
5. FTMS trainer control, especially ERG target power.
6. Reliable ride recording while:
    - screen is off
    - app is backgrounded
    - another app is open
7. Local ride history.
8. Multiple UI languages.

## Architecture rules

BLE communication must NOT live inside Activity or Compose UI.

RideRecordingService owns trainer and heart-rate connections, including preview
connections before a ride. ACTIVE and PAUSED rides retain those connections in a connectedDevice
foreground service. Activity/ViewModel teardown must not disconnect an active ride.
RideRecordingService also owns approximately 1 Hz Room recording. BLE callbacks must
not write to the database. STOPPING retains connections until the final sample/session
transaction finishes. Activity recreation/binding must never create a database session.
Explicit End finishes recording without disconnecting BLE. Retained devices keep the
connectedDevice foreground notification until all devices are disconnected. Pauses
freeze active elapsed time/sampling and workout progression, without changing ERG resistance.

Compose is presentation only.

Optional AI analysis is explicitly user-started and read-only. Keep SQL execution in
AiRideDataRepository with its native read-only handle, SELECT guard, cancellation and
result limits. Reuse current settings and historical snapshots without backfilling.
Use existing analytics calculators; model instructions/skills are not new formulas.
Never expose API keys, file/network tools, writes or trainer control through SQL/tools.
Do not automatically upload rides or start analysis on restoration. See AI_ANALYSIS.md.

For UI work, follow UI_ARCHITECTURE.md. Keep screen rendering separate from
route/ViewModel wiring. Reuse existing service state rather than a second mutable
ride model. Settings render immutable state and dispatch typed preference actions;
repositories own persistence. Preserve feature-specific confirmation/navigation behavior.

Business/data layers must never contain translated UI strings.

Use enum/sealed/data types instead of values such as:

"Connected"
"已连接"

All visible UI text must come from Android string resources.

Keep trainer power and external power meter power as separate values.

Trainer control source and power measurement source are separate concepts.

Do not assume the trainer is the preferred power source.

## BLE

Use native Android BLE APIs unless there is a strong technical reason not to.

Primary standards:

FTMS
Service UUID: 0x1826

Heart Rate Service
Service UUID: 0x180D

Cycling Power Service
Service UUID: 0x1818

Do not implement vendor-specific protocols unless explicitly requested.

Do not hardcode ThinkRider behavior into generic FTMS classes.

## Development rules

Work incrementally.

Do not implement future roadmap phases unless requested.

Do not perform large unrelated refactors.

Before modifying architecture, explain why.

After each task:

1. Ensure project compiles.
2. Report files changed.
3. Report any new dependency.
4. Report remaining known issues.

Prefer simple readable Kotlin over abstraction-heavy architecture.

Do not introduce fake production data merely to make UI appear complete.

Do not silently swallow Bluetooth exceptions.

BLE failures should be logged and represented through state.

## Localization

Default language mode: Follow System. Unsupported system languages fall back to Simplified Chinese (zh-CN).

Additional initial language:

- English (en)
- Japanese (ja), Traditional Chinese Taiwan (zh-TW), Spanish (es),
  Brazilian Portuguese (pt-BR), French (fr)

Manual language selections persist and override system changes. Follow System clears
the override. Use AppLocale/LanguagePolicy through the Settings ViewModel; never seed
a new install with an explicit locale. Preserve legacy app_locale selections.

All UI strings must use Android resources. Put default Chinese in res/values/strings.xml and complete English translations in res/values-en/strings.xml. Set unqualifiedResLocale=zh-CN in resources.properties.

Compose example:

stringResource(R.string.power)

Never write:

Text("Power")

for user-visible production text.

## Current development philosophy

Reliability is more important than visual polish.

The first major milestone is:

Connect to trainer and sensors, record for at least 20 minutes while the
phone is locked/backgrounded, and lose no ride data.
