# Phone OS Profiler report formats

## Normal app report — schema v2

Top level:

- `schema`: `phone_os_profiler_report`
- `schema_version`: `2`
- `profiler.version`: `0.3`
- `profiler.mode`: `normal_app`
- hardware/system sections: `device`, `android`, `cpu`, `memory`, `storage`, `display`, `battery`, `sensors`, `cameras`, `network`, `audio`, `telephony`, `boot`, `treble`, `kernel`, `graphics`, `thermal`, `partitions`, `firmware`, `features`, `root`, `raw`, `errors`

Unavailable data is kept as an explicit `unavailable`/error string rather than silently omitted.

## Advanced ADB report

`tools/posp_adb_collector.py` creates a second JSON report:

- `schema`: `phone_os_profiler_advanced_report`
- `schema_version`: `1`
- `profiler_version`: `0.3`
- `mode`: `adb` or `adb_root`
- `device`: serial/model/device/product/platform and Android version
- `commands`: structured results for low-level ADB shell probes

Each command result contains the command, exit code, stdout and stderr. This makes the report lossless and easy to analyze automatically.
