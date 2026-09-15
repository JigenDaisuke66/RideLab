# Phase 3: heart rate alongside the verified trainer

The trainer scanner, GATT client, Cycling Power parser and FE-C controller/encoder
are unchanged. HeartRateClient owns a second BluetoothGatt, callback instance,
timeout and state flow. HeartRateState exposes device/source, connection enum,
heartRateBpm: Int?, timestamp, discovery flags, typed failure and packet error.
No trainer state is stored or modified by HeartRateClient.

Selection path: advertised 0x180D card tap (or explicit Connect as heart rate
sensor button) → MainActivity.onHeartRateSelected → DevicesViewModel.connectHeartRate
→ HeartRateClient.connect → connectGatt on the selected address. Permission retry
retains the heart-rate role. Disconnect heart rate calls only that client. Trainer
selection/disconnect retains its original path. Both clients close on leaving the
foreground, as before; rotation retains the ViewModel and connections.

## Protocol

[Bluetooth Heart Rate Service 1.0, section 3.1](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/HRS_v1.0/out/en/index-en.html)
defines flags and the measurement. Bit 0 selects unsigned 8-bit or little-endian
16-bit BPM. Contact bits 1/2 add no payload. Bit 3 includes a 16-bit energy value
(kJ); bit 4 includes one or more 16-bit RR intervals (1/1024 s). Optional values
are retained by the parser, but only BPM is displayed. Missing optional fields
do not create readings. Reserved flags, missing bytes, incomplete RR pairs and
unflagged trailing bytes are rejected before publishing BPM.

CCCD 0x2902 is written 0100 only after checking 0x2A37 Notify support. Native
Android 12 and 13+ descriptor APIs/callbacks are supported. Each connection,
discovery and subscription has a 20-second timeout. Failures/disconnect clear BPM;
malformed packets preserve the last valid reading and its timestamp, so it becomes
stale after 5 seconds. No readings shows the localized no-data text.

## Physical verification

1. Run from Android Studio. Wear/activate the BLE heart-rate sensor; close other
   apps connected to it if it permits only one connection.
2. Connect Think X211-0013186 as usual. Pedal and confirm live power and the verified
   FE-C controls still work. Do not send a new target unless you intend to.
3. Scan again with the trainer connected. Tap the heart-rate sensor's card if
   classified as heart rate. Otherwise use Connect as heart rate sensor on its row.
4. Confirm the separate heart-rate source name/address and immediate connecting
   state, then discovery/subscription and BPM. Trainer selection/address must stay
   unchanged and trainer power must keep updating. Check both Chinese and English.
5. Disconnect only heart rate. BPM should clear; trainer power and FE-C remain usable.
   Reconnect the sensor and check BPM starts with no old reading. If available,
   select a second heart-rate sensor and verify only the first sensor is replaced.
6. Disconnect only the trainer. Heart rate must continue. Reconnect the trainer
   while heart rate streams; verify both. Repeat connecting heart rate first.
7. Switch off/out-range the sensor. After disconnect or timeout, heart rate should
   show failure/no data while trainer stays connected. If notifications merely
   stop, BPM becomes stale after 5 seconds. Rotate the device and check both roles.
8. Leave the app: both sessions intentionally disconnect in this foreground-only
   phase. Return and reconnect independently. No recording/persistence is added.

## Logcat if the sensor fails

Capture `RideLabHeartRate` from selection through failure, alongside `RideLabGatt`
to check trainer continuity:

- `Device tapped`, `Selected`, address/name, `connectGatt invoked`.
- `Connection ... status=... newState=...`, `discoverServices invoked`.
- `Services discovered`, `Detection service=0x180D ... measurement=0x2A37 ... properties=...`.
- `CCCD detected`, `CCCD subscription dispatched ... result=...`, `completed status=...`.
- `Measurement uuid=0x2A37 raw=... flags=... bpm=...` or `Malformed ... reason=...`.
- `Failure ... phase=... reason=... status=...`, permission/platform exceptions,
  disconnect/close failures and `Disconnect requested`.

Send sensor make/model, Android version, whether it was worn/awake and connected
elsewhere, selected address and role, UI state, and the logs. No-data without
notifications is not a parser success. Physical dual-connection verification is
pending; unit tests cannot prove simultaneous radio operation.

Focused unit checks cover UINT8/UINT16 BPM, contact/energy/RR fields, malformed
packets, default device-role routing (including the verified trainer), and the
existing scanner UUID classification. Compile checks do not generate an APK.
