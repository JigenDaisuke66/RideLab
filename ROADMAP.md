# RideLab Roadmap

## Phase 0 - Project skeleton

Status: complete

Acceptance:

- project builds
- project launches
- localization works
- BLE permissions declared
- package structure established

Do not implement BLE connection yet.

---

## Phase 1 - BLE Scanner

Status: physically verified.

ThinkRider X2 MAX was discovered as Think X211-0013186. Advertised services
included 0x1818, 0xFFF0, and 6e40fec1-b5a3-f393-e0a9-e50e24dcca9e.
FTMS 0x1826 was not advertised; Phase 2 must inspect the GATT database
before concluding whether FTMS is available.

Goal:

Discover nearby BLE cycling devices.

Requirements:

- runtime Bluetooth permission handling
- BLE scanning
- display device name
- display Bluetooth address
- display signal RSSI
- detect advertised services when available
- classify possible:
    - trainer
    - heart rate sensor
    - power meter

Acceptance:

ThinkRider X2 MAX appears in scan results.

No trainer control yet.

---

## Phase 2 - FTMS Connection

Status: current. ThinkRider X2 MAX connection and GATT service discovery passed
physical-device verification. FTMS 0x1826 and Indoor Bike Data 0x2AD2 were absent.
Cycling Power 0x1818 exposes 0x2A63 Notify, 0x2A65 Read, 0x2A5D Read and
0x2A66 Write/Indicate. The requested Phase 2 extension reads standard 0x2A63
power/revolution notifications; live power/cadence verification is pending.
An explicitly requested Phase 2 FE-C over BLE experiment adds manual 50–200 W
target-power writes, separate from FTMS/Cycling Power. The physical FEC1 service,
FEC2 Notify + CCCD, and FEC3 Write/Write Without Response layout is verified.
Actual FE-C ERG acceptance remains pending physical testing; see FEC_EXPERIMENT.md.
No FTMS/Cycling Power control point writes or later roadmap phases are included.

Goal:

Connect to a smart trainer.

Requirements:

- connect GATT
- discover services
- print/log available services and characteristics
- verify FTMS service 0x1826
- subscribe to Indoor Bike Data
- parse:
    - power
    - cadence
    - speed

Acceptance:

RideLab continuously displays live trainer data.

---

## Phase 3 - Heart Rate

Connect to Heart Rate Service 0x180D.

Acceptance:

Live heart rate displayed simultaneously with trainer data.

---

## Phase 4 - Cycling Power Meter

Connect to Cycling Power Service 0x1818.

Keep trainer power and external meter power separate.

Acceptance:

Both power values can coexist.

---

## Phase 5 - Trainer Control

Implement FTMS Control Point.

Initial feature:

ERG target power.

Acceptance:

User changes target power and X2 MAX changes resistance accordingly.

---

## Phase 6 - Foreground Ride Service

Move long-running BLE ride ownership out of Activity.

Acceptance test:

1. Start ride.
2. Lock phone.
3. Leave RideLab in background for 20 minutes.
4. Return to RideLab.
5. BLE connection remained active.
6. No ride samples were lost because the Activity was backgrounded.

---

## Phase 7 - Local Storage

Use Room.

Store ride sessions and raw ride samples.

---

## Phase 8 - Ride History

Display saved sessions and charts.

---

## Phase 9 - Training Analytics

Later metrics:

- average power
- max power
- normalized power
- IF
- TSS
- heart-rate zones
- power zones
- Pw:Hr / aerobic decoupling
- best 5 s
- best 15 s
- best 30 s
- best 1 min
- best 5 min
- best 20 min
- best 60 min

---

## Phase 10 - Fitness Profile

Later:

- FTP
- eFTP
- W/kg
- power curve
- aerobic efficiency
- long-term trends

Do not implement until core recording is stable.
