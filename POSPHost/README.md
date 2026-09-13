# POSP Host v0.7

POSP Host is an Android USB-host utility for a second phone. It connects to the target DOOGEE S97 Pro over USB OTG and provides ADB/Fastboot diagnostics, bootloader research, recovery transitions and safety checks for future POSP OS installation.

## Main v0.7 change: Unlock Research

v0.7 adds a dedicated **read-only bootloader questionnaire** for later supported unlocking.

Run it twice:

1. In Android/ADB mode, where it records OEM-unlock signals, AVB/vbmeta state, flash lock state, A/B and dynamic-partition properties, `bootctl`, USB debugging state, partition visibility and MediaTek boot/security-related properties.
2. In Fastboot mode, where it records an expanded `getvar` set, raw + parsed `getvar all`, slot state, fastbootd/userspace signals, battery signals, `has-slot`, partition sizes/types and logical-partition indicators.

The questionnaire itself never sends `unlock`, `erase`, `flash`, OEM probing or MediaTek BROM commands.

Generated files include:

- `POSP_Unlock_Research_ADB_*.json`
- `POSP_Unlock_Research_FASTBOOT_*.json`
- `POSP_Bootloader_Report_*.json`

Both unlock-research reports are included in the diagnostic bundle.

## Other improvements

- Bootloader Analyzer schema v3 with a substantially larger Fastboot variable set.
- Raw and parsed `getvar all` are preserved for later analysis.
- A/B slot health interrogation: successful/unbootable/retry-count for both slots when supported.
- Fastbootd/userspace Fastboot identification.
- Battery voltage / `battery-soc-ok` interrogation when the bootloader exposes it.
- `has-slot`, partition type/size and logical-partition probes for boot/vendor_boot/init_boot/dtbo/vbmeta/recovery/system/vendor/product/super.
- Before `fastboot flashing unlock`, the UI warns if a Fastboot unlock-research report has not yet been collected.
- ADB report schema v7 and diagnostic bundle schema v2.
- Built-in Russian help updated for the new workflow.

## Safety rules

POSP Host v0.7 does **not** automatically erase or flash `preloader`, `nvram`, `nvdata`, `nvcfg`, `protect1/2`, `proinfo` or FRP-related partitions. The read-only questionnaire does not test undocumented unlock commands or bypasses. Standard `fastboot flashing unlock` remains a separate explicit, user-confirmed action and may factory-reset the target phone.

## Build

The GitHub Actions workflow is in `github-workflow/posp-host.yml`. The upload script copies it to `.github/workflows/posp-host.yml`.

Build command used by CI:

```bash
gradle :app:assembleDebug
```

Expected artifact: `POSP-Host-v0.7-debug`.
