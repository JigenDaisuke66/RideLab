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

The Material 3 shell has five tabs: Home, Ride, History, AI, Settings. AI opens the existing read-only analysis page directly and includes provider configuration; it never starts analysis on navigation. Home provides friendly device status/selection, FTP and latest completed ride. Ride emphasizes large live power with BPM/cadence/time and explicit bounded ERG Apply controls. Technical diagnostics remain internal. See UI_ROUND1_VERIFICATION.md.

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

Appearance preferences add five palettes (mint, blue, violet, amber, rose) with
Light (default), Dark or System modes for full backgrounds, cards, text and system bars,
with independently selected overlay colors or Follow app theme. Three overlay
layouts are available: panel, horizontal strip and dashboard. Changes apply live,
are saved in the appearance SharedPreferences, and require no Activity/service
restart or BLE changes. Existing overlay transparency, font size and position
preferences remain in ride_overlay. See APPEARANCE_VERIFICATION.md.

Home now opens a small built-in Workout Library. Service-owned WorkoutController
uses the ride's existing FTP snapshot and verified FE-C path for explicit-confirmed
automatic ERG courses and FTP Ramp Test. Disconnects pause progression; resume is
explicit. Raw recording/history remain unchanged. Ramp results use measured ramp
effort best 60s x75%; accepting requires confirmation and uses existing FTP settings.
No Room migration/workout metadata. Pending review is process-local only; accepted
FTP persists normally. See WORKOUT_PHASE1_VERIFICATION.md; physical acceptance pending.

First FTP test is now available without any prefilled FTP: 5 minutes at 50 W,
then a fixed ramp starting at 50 W and increasing 10 W every minute. It ignores
existing FTP for targets and reuses measured ramp-only best 60s x75%, explicit
acceptance, service/recording and FE-C control. Existing FTP-relative tests remain.
See INITIAL_FTP_VERIFICATION.md; physical acceptance pending.

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

Requested compatibility Phase 3 adds manual FTMS target power via actual feature/range
discovery, CP indications and Request Control. FE-C remains preferred when both layouts
exist; only the recognized X2 MAX profile retains automatic workouts and 50–2000 W.
Unverified FE-C is manual 50–200 W; FTMS uses its reported range and stays manual-only.
Missing valid FTMS ranges disable ERG, retaining measurements. Both callers share the
service-owned control path. FTMS hardware verification is pending; see TRAINER_COMPATIBILITY.md.

Implemented control:

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
partial wake lock protect ACTIVE and PAUSED rides. Pause freezes elapsed time,
sampling and workout progression, retaining BLE and applied ERG resistance.
End completes recording, releases the wake lock and cancels recovery, but retains
both existing connections in connectedDevice foreground mode with a device-connected
notification. Explicitly disconnecting all devices removes that foreground mode.
Each device gets one automatic
reconnect attempt per ride after an unexpected disconnect. No automatic ERG target
replay is performed. Room recording is sampled by the service at approximately 1 Hz,
independently of BLE notifications and Activity lifecycle. Explicit End first commits
the last sample and COMPLETED session without disconnecting BLE.
Paused time is excluded from stored duration/sample elapsed time; wall-clock start/end
timestamps remain real. Each pause writes one null boundary row so charts and rolling
analytics cannot bridge the pause (same-tick boundaries may add 1 ms). No schema change.

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

Follow System is the default selection. Simplified Chinese (zh-CN) remains the unqualified fallback.

Supported languages: Simplified Chinese, English, Japanese, Traditional Chinese (Taiwan),
Spanish, Brazilian Portuguese and French.

Fresh installs leave the override empty and resolve supported system languages,
falling back to zh-CN when none match. Android 13+ LocaleManager is the sole manual
override store; an empty applicationLocales means Follow System. The existing
one-time platform_initialized migration copies only a present legacy_language value,
never a new Chinese default. Android 12/12L retains app_locale/legacy_language;
Follow System removes that key. Existing explicit Chinese/English choices are preserved.
Old auto-seeded Chinese is indistinguishable from manual Chinese and is also retained.
Activity, notification and overlay contexts share LanguagePolicy. Settings ViewModel
owns selection actions; Activity only performs legacy recreation. See LOCALIZATION_PHASE2.md.

Chinese strings live in res/values/strings.xml; English translations live in res/values-en/strings.xml. Every visible string must exist in both languages. resources.properties declares unqualifiedResLocale=zh-CN.

Architecture must support adding Japanese and other languages without
changing business logic.

## Non-goals for V1

The explicitly requested optional AI feature includes encrypted saved provider profiles,
connection tests and user-started read-only SQL cycling analysis, including current and
historical FTP/weight. Required query results go to the selected endpoint only during
analysis. Four bundled analyst presets reuse existing metrics with bounded queries and
token budgets. No SQL writes, trainer tools or mandatory network/backend dependency.
The offline ride path is unchanged. See AI_SERVICES.md and AI_ANALYSIS.md.

No social feed.
No cloud sync.
No route/map system.
No virtual world.
No Zwift clone.
No automatic real-time AI coaching or AI trainer control.
No online account requirement.

Current experimental FE-C trainer profile: ThinkRider X2 Max supports ERG targets 50–2000 W. TrainerErgProfile controls UI, restored preferences and controller validation independently of FE-C field encoding limits. Apply remains explicit; no restored target is auto-sent. See ERG_TARGET_VERIFICATION.md.
