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

Round 1 uses a four-tab Material 3 shell: Home, Ride, History, Settings. Home provides friendly device status/selection, FTP and latest completed ride. Ride emphasizes large live power with BPM/cadence/time and explicit bounded ERG Apply controls. Technical diagnostics remain internal. See UI_ROUND1_VERIFICATION.md.

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

Lists completed and interrupted local sessions newest first, with duration, status and sample count. A simple detail view shows stored timing, first/last sample elapsed time, and null-aware average/maximum trainer power, heart rate and cadence. Room query flows are exposed through the existing repository and a HistoryViewModel; history never owns recording.

Ride details also show separate Canvas power, heart-rate and cadence charts from stored samples. Null values and elapsed sample gaps over three seconds break the lines; deterministic min/max time buckets reduce long rides without changing raw data. See RIDE_CHARTS_VERIFICATION.md.

Training analytics phase 1 adds optional FTP settings and NP/IF/TSS. Schema v2 explicitly migrates v1 with one nullable session FTP snapshot, captured at Start ride (unset stays null; legacy nulls are not backfilled). Raw samples and recording are unchanged. See TRAINING_ANALYTICS_VERIFICATION.md. Analytics Layer 1 adds fixed-duration best efforts/PDC, historical PR comparison, saved ride-weight W/kg and FTP power-zone time. Body weight is an optional local setting snapshotted at ride start via explicit schema v3 migration. See ANALYTICS_LAYER1_VERIFICATION.md. eFTP and other advanced metrics remain deferred.

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
replay is performed. Room recording is sampled by the service at approximately 1 Hz,
independently of BLE notifications and Activity lifecycle. Explicit End first commits
the last sample and COMPLETED session before disconnecting/stopping foreground mode.

The UI observes service/application state.

## Storage

Local-first.

No user account.
No mandatory cloud.
No server required.

Room database ridelab.db (schema version 3) stores ride_sessions and ride_samples.
Statuses are ACTIVE, COMPLETED and ABORTED, never localized strings. Samples preserve
nullable trainer power, cadence, BPM, requested target, source addresses and optional
external power/speed/resistance separately. Missing/disconnected/stale sensor data is
null; no fabricated samples fill scheduling gaps. Duration uses monotonic elapsed time.
Interrupted ACTIVE sessions are marked ABORTED on next service startup using the last
saved checkpoint. No destructive migrations or advanced analytics. Minimal read-only history is available for persistence verification.

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

Current experimental FE-C trainer profile: ThinkRider X2 Max supports ERG targets 50–2000 W. TrainerErgProfile controls UI, restored preferences and controller validation independently of FE-C field encoding limits. Apply remains explicit; no restored target is auto-sent. See ERG_TARGET_VERIFICATION.md.
