# Wizard physical-device test checklist

## Before test
- Install the same `uPOSPa 0.8 beta + Wizard` APK on Host and Client.
- Host: confirm USB OTG / USB Host support.
- Client: enable Developer options and USB debugging.
- Use a data-capable USB cable and the correct OTG adapter/role.
- Charge both phones above 40% before Full / OS Preparation tests.

## Quick diagnostic
1. Client: `WIZARD` -> Quick -> CLIENT -> review data -> `ПЕРЕЙТИ В ОЖИДАНИЕ`.
2. Host: `WIZARD` -> Quick -> HOST -> review data -> `НАЧАТЬ WIZARD`.
3. Accept the Android RSA prompt on Client when it appears.
4. Verify Host progresses through USB/ADB -> Client snapshot -> ADB diagnostics -> complete.
5. Verify Client shows `WIZARD ЗАВЕРШЁН`.
6. Verify Host enables `СОХРАНИТЬ ОТЧЁТ` and the JSON contains `client_snapshot`, `adb`, `target`, `timeline`.

## Full diagnostic
1. Repeat setup with `ПОЛНАЯ ДИАГНОСТИКА`.
2. Verify Client automatically reboots into Bootloader/Fastboot.
3. Verify Host detects the Fastboot USB interface without cable reconnection.
4. Verify report contains `fastboot.summary`, `fastboot.slots`, `fastboot.partitions`.
5. Verify Client automatically returns to Android and uPOSPa opens.

## OS preparation
1. Repeat with `ПОДГОТОВКА К УСТАНОВКЕ ОС`.
2. Verify Fastboot data collection.
3. If OEM supports userspace Fastboot, verify transition to Fastbootd and `fastbootd.available=true`.
4. If unsupported, verify a warning is recorded and no flash/erase/unlock command is issued.
5. Verify Android return and final report save.

## Failure recovery
- Disconnect/reconnect the USB cable while Host is waiting for a mode transition; verify the Wizard keeps waiting until timeout.
- Cancel while the device is in Fastboot; verify uPOSPa attempts a best-effort reboot to Android.
- Deny USB permission; verify the Wizard reports a clear permission timeout rather than continuing blindly.
- Leave notification permission disabled on Android 13+; verify the Client still shows the in-app completion card.

## Safety audit
Search the Wizard implementation for prohibited write operations. Wizard flow must not issue `flash:`, `erase:`, `flashing unlock`, `fastboot oem unlock`, factory reset or userdata wipe commands. Direct IMG flashing remains isolated in the existing manual SERVICE screen and is not called by Wizard.
