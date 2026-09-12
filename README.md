# Phone OS Profiler v0.3

Android/ADB profiler for collecting hardware and firmware information needed to design a custom OS/device tree for a specific phone.

Target used during v0.3 design: DOOGEE S97 Pro / MT6785 / Android 11.

## 1. Normal Android application

Builds an APK and writes one JSON report with schema `phone_os_profiler_report`, schema version 2.

Compared with v0.2 it adds normal-app probes for:

- graphics/GPU hints and SurfaceFlinger;
- thermal service and thermal zones where readable;
- block/partition hints;
- firmware/baseband/vendor fingerprints;
- existing CPU, RAM, storage, display, battery, sensors, cameras, network, audio, telephony, boot/Treble, kernel, system features and raw properties.

Android sandbox/SELinux may block some `/proc`, `/sys`, `/dev` and vendor data. Those failures are retained in the report instead of being hidden.

## 2. Advanced ADB collector

The `tools/` directory contains:

- `posp_adb_collector.py` — main collector;
- `run_advanced_termux.sh` — Termux launcher that installs Python + Android platform tools and runs the collector.

The advanced report is a separate JSON file with schema `phone_os_profiler_advanced_report`.

It attempts to collect:

- all Android properties and boot properties;
- kernel/cmdline/modules;
- partition map, fstab and mount information;
- VINTF manifests and HAL libraries;
- `lshal` and Android service list;
- SurfaceFlinger/display/GPU information;
- camera, sensors and audio dumpsys;
- thermal and power/battery sysfs;
- input devices;
- Wi-Fi, Bluetooth, telephony, USB and network state;
- available device-tree index;
- vendor/odm firmware directory listings;
- CPU frequency policies and memory information.

### Run from Termux

Enable Developer options and USB debugging on the target phone. The collector must run from a Termux/host environment that can see the target through ADB (for example another Android device with USB OTG, or use the Python collector from a PC).

```bash
cd tools
chmod +x run_advanced_termux.sh
./run_advanced_termux.sh
```

If the target phone already has working root and `su`:

```bash
./run_advanced_termux.sh --root
```

Without root the collector still gathers everything available to the ADB shell user.

## GitHub Actions APK build

Push to `main`/`master` or start the workflow manually. The workflow builds:

`app/build/outputs/apk/debug/app-debug.apk`

and uploads it as the artifact `PhoneOSProfiler-v0.3-debug`.

## v0.4 additions
- Dedicated **Send via Bluetooth** button for the generated JSON report.
- Uses Android's system Bluetooth/OBEX share handler through `ACTION_SEND` + `FileProvider`; falls back to the normal share chooser when an OEM Bluetooth component cannot be resolved directly.
- Runtime request for `READ_PHONE_STATE`; on Android 12+ also requests `BLUETOOTH_CONNECT` when collecting Bluetooth adapter metadata.
- Adds `permissions`, `bluetooth`, `transport_capabilities`, and `collection_notes` to the report.
- Report schema version is now 3; application version is 0.4.

### Recommended next improvements after v0.4
1. Merge the normal-app and ADB reports into one signed/hashed bundle.
2. Collect VINTF manifests/matrices and `lshal` into structured JSON rather than raw text.
3. Parse `/dev/block/by-name`, dynamic partitions and slot metadata into a normalized partition table.
4. Add structured GPU, thermal-zone and input-device sections.
5. Add Wi-Fi/Bluetooth chipset/firmware hints from properties and ADB dumpsys output.
6. Add report completeness scoring so missing privileged data is obvious.
7. Add export as ZIP containing JSON plus raw diagnostic attachments.
