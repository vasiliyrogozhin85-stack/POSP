# POSP Research Host + Client v1.0

Production-candidate research suite for collecting read-only device information before POSP OS bring-up.

## Host v1.0
- Separate Connection, Boot modes, Data collection, Reports modules.
- Full research wizard: Android -> Fastboot -> Fastbootd -> Recovery -> final bundle.
- USB lifecycle recovery: stale ADB/AOA transports are closed on detach and rediscovered after attach.
- Read-only Fastboot research only; no flash/erase/unlock automation.
- Persistent staged research files and `missing_data.txt`.
- Final `POSP_OS_Research_Bundle_*.zip` export to Download/POSPReports.

## Client v1.0
- Android hardware/software profile, sensors, cameras, memory, storage, display, battery and build data.
- AOA Client Link with PING/PONG and report transfer.
- Session logs and local reports.

## Build
`gradle :host-app:assembleDebug :client-app:assembleDebug`

Package IDs remain unchanged for upgrade compatibility:
- `com.vasiliyrogozhin85.posp.host`
- `com.vasiliyrogozhin85.posp.client`
