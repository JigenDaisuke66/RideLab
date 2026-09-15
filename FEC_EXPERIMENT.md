# Phase 2 FE-C over BLE experiment

Physical GATT layout reported by the user on Think X211-0013186:

- Service `6e40fec1-b5a3-f393-e0a9-e50e24dcca9e`.
- Receive `6e40fec2-b5a3-f393-e0a9-e50e24dcca9e`: properties `0x10` (Notify), CCCD `00002902-0000-1000-8000-00805f9b34fb`.
- Send `6e40fec3-b5a3-f393-e0a9-e50e24dcca9e`: properties `0x0c` (Write and Write Without Response).

This layout is verified; acceptance of target-power commands is NOT yet verified.
Capability is checked again against the current connection's discovered database.
No trainer-name special cases. Missing UUIDs/properties/CCCD disable the experiment.

## Transport and encoder

The existing measurement CCCD operation completes first. Then the separate
FecOverBleTrainerController enables FEC2 locally and writes its CCCD `01 00`.
Only a successful subscription permits a target write. Measurement notifications
continue through the original parser. FE-C incoming bytes are logged without
claiming that they acknowledge a target command.

Call path: FecDebugControl → ConnectionDetails → DevicesScreen → MainActivity
callback → DevicesViewModel.sendFecTarget → FtmsClient.sendFecTarget →
FecOverBleTrainerController → FecProtocol encoder → native writeCharacteristic.

The debug UI starts at 100 W with 5 W adjustments. The encoder also clamps any
caller to 50–200 W before multiplication. One explicit button press sends one
13-byte packet; no auto-send, retry, periodic refresh or saved-target replay.
Further sends wait for the write callback and a two-second cooldown.
Prefer WRITE_TYPE_DEFAULT (2, ATT response); use WRITE_TYPE_NO_RESPONSE (1)
only if that is the sole discovered write property. A 10-second missing callback
timeout closes the connection to prevent late callbacks from affecting a new write.
Other FE-C errors disable control until reconnect; existing measurements remain
available unless the connection itself fails. The existing disconnect button and
foreground-only connection lifecycle remain in effect.

Frame offsets (hex):

| Offset | Content |
| --- | --- |
| 0 | A4: ANT sync |
| 1 | 09: payload length, channel plus eight-byte page |
| 2 | 4F: ANT acknowledged data message |
| 3 | 05: channel; ignored by FE-C over BLE trainer |
| 4 | 31: FE-C target-power page 49 |
| 5–9 | FF FF FF FF FF: reserved page bytes |
| 10–11 | target watts × 4, unsigned 16-bit little endian (0.25 W units) |
| 12 | XOR of bytes 0–11, including A4 |

Derived vectors:

| Watts | Frame |
| --- | --- |
| 50 | A4 09 4F 05 31 FF FF FF FF FF C8 00 E1 |
| 100 | A4 09 4F 05 31 FF FF FF FF FF 90 01 B8 |
| 150 | A4 09 4F 05 31 FF FF FF FF FF 58 02 73 |
| 200 | A4 09 4F 05 31 FF FF FF FF FF 20 03 0A |

## References and discrepancy

Original Kotlin code implements protocol fields; no reference source is copied or
bundled and no dependency is added.

- [ANT Message Protocol and Usage, rev 5.1, serial frame definition](https://www.thisisant.com/assets/resources/ANT%20Protocol/D00000652_ANT_Message_Protocol_and_Usage_Rev_5.1.pdf): checksum is XOR of all preceding bytes INCLUDING sync.
- [Tacx FE-C over BLE guide](https://github.com/jedla22/BleTrainerControl/blob/master/How-to%20FE-C%20over%20BLE%20v1_0_0.pdf): BLE mapping and complete ANT framing. Its page 5 prose says byte sum excluding sync, but its worked frame ends in 30, which agrees with ANT XOR including sync, not that sum. Its TX UUID prose also repeats FEC2; actual physical properties and both implementations identify FEC3 as TX.
- [pycycling Tacx controller](https://github.com/zacharyedwardbull/pycycling/blob/master/pycycling/tacx_trainer_control.py) (MIT): confirms target page 31, reserved bytes, quarter-watt scaling and UUIDs. Its arithmetic-sum checksum is deliberately NOT used.
- [ftms_emu FE-C implementation](https://github.com/OevreFlataeker/ftms_emu/blob/master/ble_fec/ble_fec.c) (file carries Nordic license restrictions): reviewed only as reference; actual checksum loop uses XOR including sync. No code copied into RideLab.

No speculative checksum variant is retried against the hardware.

## First physical 100 W test

1. Run from Android Studio, close other trainer-control apps, and connect to the trainer.
2. Open connection details. Confirm live Cycling Power still updates, FE-C is detected,
   FEC2 subscription completes with status 0, and the UI says ready with no target sent.
3. Pedal steadily at a comfortable cadence and note power before sending.
4. Leave the target at 100 W and press Send 100 W test once. Capture the exact outgoing
   frame and write dispatch/callback statuses. Do not repeatedly press while waiting.
5. Continue pedaling for 30–60 seconds. Vary cadence moderately: working ERG should
   adjust resistance so measured power tends back toward 100 W within the trainer's
   operating limits. A single power sample near 100 W does not establish acceptance.
6. Use the existing Disconnect action to end the BLE session. Disconnect is not a
   command to release resistance; stop pedaling and power-cycle the trainer if needed.

Capture RideLabGatt lines from connection through the test:

- Existing Service/Characteristic/Descriptor discovery and connection/failure status.
- `FE-C capability ...` (FEC2/FEC3 properties and CCCD).
- `FE-C FEC2 subscription started ...` and `completed status=...`.
- `FE-C target requestedWatts=100 clampedWatts=100 outgoing=... writeType=2`.
- `FE-C FEC3 write dispatched result=...` and `write completed status=...`.
- Continuous `FE-C FEC2 incoming=...` before/after sending, plus CyclingPowerMeasurement logs.
- Any `FE-C failure ...`, timeout or disconnect.

A GATT status 0 is transport evidence only. A decoded command-status rejection
for page 31 would show that this command was not accepted (possibly because of
state/configuration). No resistance response despite successful writes is an
inconclusive experiment, not proof the trainer lacks the protocol. Send the raw
notifications and measured behavior for analysis; this implementation does not yet
decode command status or send status-request pages.

Relevant automated checks: FecProtocolTest (four target vectors, independent
checksum example, bounds, capability and send gating), plus existing state and
Cycling Power parser/cadence unit tests. No APK or device tests are requested.
