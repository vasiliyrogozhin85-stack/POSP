# uPOSPa Wizard architecture

## State flow

`SETUP -> USB_WAIT -> ADB_CONNECT -> CLIENT_SNAPSHOT -> ADB_DIAGNOSTICS`

Quick mode continues to:

`FINAL_ANDROID -> CLIENT_COMPLETE -> REPORT_READY`

Full mode continues to:

`REBOOT_BOOTLOADER -> FASTBOOT_WAIT -> FASTBOOT_GETVAR -> SLOT_MAP -> PARTITION_MAP -> REBOOT_ANDROID -> ADB_WAIT -> CLIENT_COMPLETE -> REPORT_READY`

OS Preparation adds:

`FASTBOOTD_REQUEST -> FASTBOOTD_WAIT -> DYNAMIC_PARTITION_MAP`

before rebooting to Android.

## Reconnection model

Each transition waits for the expected USB interface instead of assuming the device remains continuously attached. USB permission is requested again when Android exposes a new USB device instance after a mode change. This lets the Wizard tolerate normal detach/attach events caused by ADB -> Fastboot -> Fastbootd -> Android transitions.

## Client bridge

Host starts `.ClientActivity` over ADB with a Wizard session id. Client creates a local `DeviceSnapshot` and writes an atomic snapshot plus `ready.txt` into its external app directory. Host reads that snapshot through the already-authorized ADB shell and merges it into the final report. No user documents are read.

## Safety

Wizard contains no flash, erase, unlock or userdata commands. Fastboot usage is restricted to getvar/partition/slot reads plus reboot/reboot-fastboot transitions. On failure or cancellation the engine performs a best-effort reboot to Android and reopens uPOSPa if ADB becomes available.
