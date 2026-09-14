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

A Foreground Service will own active ride recording.

The UI observes service/application state.

## Storage

Local-first.

No user account.
No mandatory cloud.
No server required.

Ride samples will later be stored using Room.

## Localization

English is the fallback language.

Initial translation:

zh-CN

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