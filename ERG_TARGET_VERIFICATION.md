# ERG target input and preference

Tap the pending watt value to open a digits-only numeric dialog. Confirm accepts only
ThinkRiderX2MaxProfile.minErgWatts..maxErgWatts (50–2000 W) and edits pending state only.
Cancel leaves pending state unchanged. +/-5 remains clamped to the same device limits.
Apply is still the only UI callback that dispatches DevicesViewModel.sendFecTarget.

ErgTargetViewModel keeps pending UI state independently from trainer.fec.lastTargetWatts.
It stores only explicit Apply choices through ErgTargetSettings into SharedPreferences
"erg_targets", keyed by uppercase trainer Bluetooth address. IO reads/committed writes
are serialized. Preference failures are logged under RideLabErg and shown in the card.
A saved preference is user intent, not proof of a successful write or trainer acceptance.
The card displays the existing last-requested FE-C state separately.

When selecting a trainer/new app session: saved value > existing last target > 100 W.
Restored values are clamped to current device limits. Restoration has no command callback
and cannot send power. Changing pending values does not save. Apply with no address cannot
persist a preference. No automatic command replay, dependency, Room or protocol changes.

Changed files: MainActivity.kt, AppShell.kt, RideScreen.kt; new ErgTargetViewModel.kt,
ErgTargetValues.kt, data/repository/ErgTargetSettings.kt; Chinese/English strings;
ErgTargetValuesTest.kt, RidePresentationUiTest.kt, ErgTargetSettingsTest.kt; this guide.

Physical checks:
1. Select trainer A, tap watts; try blank, 49/2001, valid 135. Invalid input cannot confirm.
2. Cancel an edit: pending unchanged. Confirm 135: pending changes but trainer must not.
3. Apply: existing FE-C write occurs and preference is saved. +/-5 alone must not save/send.
4. Restart and reconnect A: 135 restores pending only, without changing trainer resistance.
5. With trainer B, no preference uses its existing last target or 100. Apply a different
   target; return to A and verify independent preferences. Test both UI languages.

Pure tests cover numeric parsing, fallback precedence, clamping and +/-5. Device-side tests
cover independent persistent addresses and no sending during restore/Cancel/Confirm; these
are compiled only in this task. Physical UI acceptance remains pending. No APK generated.

Range update: TrainerErgProfile separates device capability from FE-C encoding. Only ThinkRiderX2MaxProfile (50–2000 W) is wired into the current controller/UI/preferences. The encoder retains identical quarter-watt scaling, bytes and checksum; its integer field capacity is 0..16383 W, not a trainer rating. Controller clamps to the device range before encoding. Original 50/100/150/200 W packet tests and a 2000 W packet test pass. Test invalid 2001, restored 1350, and upper-bound +/-5. High-power physical acceptance has not been performed by this task.
