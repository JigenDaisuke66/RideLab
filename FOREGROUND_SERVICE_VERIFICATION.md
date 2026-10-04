# Foreground ride service verification

Lifecycle update: the original End/disconnect expectations below are superseded
by RIDE_PAUSE_VERIFICATION.md. PAUSED retains BLE/foreground mode. End finishes
recording and retains connections with a device notification until manually disconnected.

## Old disconnect path

MainActivity.onStop, except for configuration changes, called
DevicesViewModel.leaveForeground. It stopped the scanner and called
FtmsClient.disconnect and HeartRateClient.disconnect. ViewModel.onCleared also
closed both clients. Home/lock thus intentionally released both GATT sessions.

## Ownership and lifetime now

RideRecordingService is the sole creator/owner of FtmsClient and HeartRateClient.
It owns them even before Start ride, so promotion requires no transfer or duplicate
connectGatt. The existing scanner and BLE protocol implementations are unchanged.
The ViewModel binds via application context and a local Binder, observes separate
trainer/HR/ride StateFlows, and forwards connect/disconnect/ERG commands. UI controls
wait for the binder to be ready. Rebinding never initiates a BLE connection.

Before a ride, UI disappearance disconnects preview sessions through the service.
During STARTING or ACTIVE, uiHidden is a no-op for BLE. This is the policy invoked
by onStop, ViewModel teardown, service unbinding and task removal. Rotation retains
the ViewModel; a newly created UI can bind to the existing started service.

Start ride begins STARTING once, checks Bluetooth/notification access and calls
startForegroundService from the visible UI's command. onStartCommand immediately
promotes the service, acquires a partial wake lock and publishes ACTIVE. A
SystemClock.elapsedRealtime timestamp supplies elapsed time including device sleep;
there is no dependence on UI timer ticks. The notification has an independent
chronometer, tap-to-open action and End ride action. No automatic target is sent.

End transitions to STOPPING before disconnecting, cancels pending reconnect jobs,
clears both desired devices, removes the notification, releases the wake lock,
returns to IDLE and calls stopSelf. If the visible UI still binds, an idle service
object remains until unbound; no GATT connection/foreground work remains.

Manifest: FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE (already present),
BLUETOOTH_SCAN/CONNECT (already present), POST_NOTIFICATIONS and WAKE_LOCK.
Service is non-exported, stopWithTask=false, foregroundServiceType=connectedDevice.
Start asks for notification permission on Android 13+. RideLab requires notifications
and the ride channel enabled so the active ride can be shown; denial gives a localized
error/settings action. Android does not itself require notification permission to
start an FGS; this is RideLab's visibility requirement.

## Recovery limits

Each role has an independent retry budget and cancellable job. During ACTIVE, an
unexpected loss/connect failure/timeout/Bluetooth-off failure schedules one attempt
after three seconds. The attempt uses the selected address, rechecks ACTIVE/current
selection/not already connecting, and calls the existing client's connect method.
Existing clients close failed sessions, reject old callbacks, discover and subscribe
again, and already have bounded connection timeouts. Repeated failure updates cannot
create a second attempt; success does not replenish the budget. Explicit selection
or a new ride resets that role's budget. End/manual disconnect cancels recovery.
Permission/protocol failures do not repeatedly retry. An exhausted role remains in
its typed BLE failure state; the other device and ride timer continue.

The FE-C encoder/control path is unchanged. After trainer recovery notifications
are re-established and manual ERG commands can be sent, but the previous target is
not automatically replayed. Confirm hardware behavior after reconnect.

## Automated verification and limits

RideSessionTest exercises no disconnect on STARTING/ACTIVE UI hide, elapsed time
across 25 minutes without ticks, duplicate start, explicit stop ordering/cleanup,
startup errors, one-attempt gating and independent retry budgets. FE-C/HR parser
regression tests are included. Production and existing instrumented-test sources
compile; the native click-path test now waits for service binding. No APK is built
and no instrumented/device test is claimed to have run.

The lifecycle policy tests cannot prove radio stability or vendor battery behavior.
START_NOT_STICKY intentionally avoids pretending to restore an unrecorded ride
after process death. Force-stop/reboot/process termination ends the in-memory ride;
there is no persistence or sample recording. A foreground service plus wake lock
does not guarantee survival of OS/OEM termination.

## Physical acceptance

1. Run from Android Studio. Connect X2 MAX and the HR sensor before starting a ride.
   Confirm power, available cadence, BPM, and explicit FE-C ERG commands work.
2. Press Start ride. Grant notification permission. Confirm ACTIVE, elapsed time and
   the persistent notification. Logcat must show no new connectGatt calls caused by
   starting the ride; it must retain the existing sessions.
3. Rotate the screen twice; verify ACTIVE, same devices, increasing timer and data.
4. Press Home/open another app for five minutes while continuing the test. Return
   briefly and confirm no intentional disconnect and elapsed time includes that period.
5. Lock the phone for at least twenty minutes while pedaling with the HR sensor on.
6. Return to RideLab. Expect ACTIVE, elapsed time including the full background period,
   connected/recovered devices, fresh power/cadence/BPM and working explicit ERG commands.
7. Separately simulate one short sensor dropout (return within the retry window).
   Expect one reconnect for that role, with the other uninterrupted. A long outage
   can exhaust the single attempt and requires manual reconnect; there is no retry loop.
8. Press End ride. Verify both disconnected, no ongoing notification and no reconnect
   after waiting thirty seconds. Repeat End through the notification while backgrounded.
9. Before Start ride, verify Home still closes preview sessions. Also test denied
   notification permission/start failure: no stuck ACTIVE state or foreground work.

Capture RideLabRide, RideLabGatt and RideLabHeartRate from before Start through return:

- Service created, Ride STARTING, Ride ACTIVE with start timestamp and device addresses.
- UI hidden phase=ACTIVE preserve=true (must not be followed by intentional GATT disconnect).
- Existing connection state, raw measurement logs and FEC write/subscription results.
- Reconnect scheduled/invoked role/address/attempt=1/1 and corresponding BLE statuses.
- Ride STOPPING/IDLE, foreground start errors or unexpected Service destroyed.

Send phone/Android version, notification permission/channel state, timing, screenshots
of ACTIVE/timer/data on return, both device statuses, and complete relevant logs.

Platform references: [FGS launch](https://developer.android.com/develop/background-work/services/fgs/launch),
[connectedDevice type](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device),
[bound service lifetime](https://developer.android.com/develop/background-work/services/bound-services),
[notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
