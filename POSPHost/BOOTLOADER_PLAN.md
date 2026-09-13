# DOOGEE S97 Pro bootloader research plan — POSP Host v0.7

Goal: determine the supported unlock path for the user's actual S97 Pro using read-only evidence before any state-changing command.

## Phase A — Android / ADB

Run **Подробный опрос для разблокировки** while Android is booted and USB debugging is authorized.

The report captures:
- target identity / build fingerprint / MT6785 and board identity;
- `ro.boot.flash.locked`, vbmeta device state, verified boot state and verity mode;
- OEM-unlock support/allowed signals exposed through properties/settings;
- A/B slot, dynamic partition and Virtual A/B properties;
- `bootctl` availability and slot state where readable;
- USB debugging state;
- visible partition map;
- AVB / vbmeta / unlock related properties;
- MediaTek boot/security-related properties that are visible to ADB shell.

No property is changed.

## Phase B — Fastboot

Reboot to bootloader, reconnect POSP Host and run the same questionnaire again.

The report captures:
- product / unlock / secure / critical-unlocked state;
- bootloader/baseband/version/hardware revision when exposed;
- current slot and slot count;
- slot successful/unbootable/retry counters;
- userspace Fastboot / fastbootd signals;
- battery voltage and `battery-soc-ok` when exposed;
- partition slot presence, type, size and logical-partition flags;
- raw `getvar all` plus a parsed key/value map.

No unlock, erase, flash, OEM or BROM command is sent during this phase.

## Phase C — Review before unlock

Combine both reports into the diagnostic bundle and review:
- identity consistency;
- whether OEM unlocking appears supported/allowed;
- whether bootloader is still locked;
- whether standard Fastboot is available;
- whether the device is in bootloader Fastboot or userspace fastbootd;
- exact response vocabulary returned by this bootloader;
- A/B and partition layout relevant to rescue/recovery.

Only after this review should a separate explicit `fastboot flashing unlock` attempt be considered. POSP Host v0.7 does not automatically try alternative OEM commands or protection bypasses when the standard command is rejected.
