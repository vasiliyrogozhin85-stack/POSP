# Changelog

## 0.8 beta + Wizard (build 81)
- Added four-step Host/Client Wizard UI.
- Added Quick / Full / OS Preparation diagnostic scenarios.
- Automatic USB device discovery, permission wait and ADB connection.
- Client-side Wizard waiting, local DeviceSnapshot bridge and completion screen.
- Combined Wizard JSON report with phase timeline and warnings.
- Automatic Bootloader/Fastboot diagnostics in Full mode.
- Automatic Fastbootd attempt and dynamic partition inspection in OS Preparation mode.
- Automatic return to Android and relaunch of uPOSPa Client after successful collection.
- Best-effort Android recovery on Wizard error/cancel.
- Host `SAVE REPORT` button after completion.
- Android notification channel for Client completion; in-app completion remains the fallback.
- Report schema advanced to v9; app build version `0.8-beta-wizard`.
- Wizard explicitly excludes personal file/content collection and does not flash/unlock/wipe.

## 0.8 beta
- Unified Dashboard and production-style navigation.
- Device history and session persistence.
- OEM profile engine.
- Hardware quick test module.
- Firmware package library.
- uPOSPa package manifest support.
- SafetyGate and operation journal foundations.
- Extended report schema v8.
- Preserved POSP ADB/AOA compatibility and v0.3 Fastboot/partition/slot functions.
