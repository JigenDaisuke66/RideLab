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

Long-running connections and recording must eventually be owned by
RideRecordingService.

Compose is presentation only.

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

Default language: English.

Additional initial language:

- zh-CN

All UI strings must use Android resources.

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