# RideLab

RideLab is a local-first native Android application for indoor cycling.

## Goal

Provide reliable BLE sensor recording and smart trainer control without
requiring an online account or cloud service.

## Initial supported devices

### Smart trainer

Bluetooth FTMS.

Primary development hardware:

ThinkRider X2 MAX.

### Heart rate

Bluetooth Heart Rate Service.

Phase 3 uses an independent HeartRateClient (0x180D / 0x2A37) alongside the
verified ThinkRider Cycling Power / FE-C trainer client. DevicesViewModel exposes
separate trainer and heart-rate state flows, selected sources and disconnect actions.
RideRecordingService now owns both clients. The ViewModel binds to its typed state
flows and forwards commands; it never owns a BluetoothGatt. Preview connections
close when the UI disappears, but starting a ride promotes the existing service
and retains the same sessions through Home, screen lock and UI recreation.

### Power meter

Bluetooth Cycling Power Service.

## Main screens

### Home

Shows:

- connected devices
- start ride
- recent ride
- current FTP/profile summary later

### Devices

Allows separate selection of:

- smart trainer
- heart rate sensor
- power meter

Displays:

- device name
- address
- device type
- connection state

### Ride

Live values:

- current power
- heart rate
- cadence
- speed
- elapsed time
- target power

Controls:

- start
- pause
- finish
- ERG target power + / -
- trainer resistance later

### History

Lists recorded ride sessions.

Detailed analytics will be added later.

### Settings

Initial concepts:

- preferred power source
- FTP
- body weight
- language
- device preferences

## Live ride data

The application must preserve raw values whenever possible:

timestamp
trainerPowerWatts
externalPowerWatts
heartRateBpm
cadenceRpm
speedKph
targetPowerWatts
resistanceLevel

Derived metrics must not replace raw samples.

## Trainer control

RideLab will eventually support:

ERG target power

Example:

Target: 100 W

User selects +5 W

New FTMS target power:

105 W

Trainer control and power measurement are separate systems.

## Recording

Active rides must continue recording while:

- screen is locked
- Activity is destroyed/recreated
- user switches apps

A connectedDevice Foreground Service owns active ride BLE connections and elapsed
time (SystemClock.elapsedRealtime). Start/End ride is explicit. A notification and
partial wake lock last only for the active ride; End disconnects both devices,
cancels recovery and removes foreground mode. Each device gets one automatic
reconnect attempt per ride after an unexpected disconnect. No automatic ERG target
replay, ride samples or recording persistence is implemented in this phase.

The UI observes service/application state.

## Storage

Local-first.

No user account.
No mandatory cloud.
No server required.

Ride samples will later be stored using Room.

## Localization

Simplified Chinese (zh-CN) is the default/fallback language.

English remains fully supported as an alternative language.

Fresh installations explicitly select zh-CN on first startup regardless of system language. On Android 13+, a one-time app_locale/platform_initialized preference seeds LocaleManager only if no existing per-app choice exists; Android persists all subsequent choices, including System default. On Android 12/12L, app_locale/legacy_language stores the selection and the Activity uses a localized context. The in-app language buttons support Chinese and English on all supported versions. A saved legacy choice migrates once when upgrading to Android 13+.

Chinese strings live in res/values/strings.xml; English translations live in res/values-en/strings.xml. Every visible string must exist in both languages. resources.properties declares unqualifiedResLocale=zh-CN.

Architecture must support adding Japanese and other languages without
changing business logic.

## Non-goals for V1

No social feed.
No cloud sync.
No route/map system.
No virtual world.
No Zwift clone.
No AI coaching.
No online account requirement.
