# Phone OS Profiler report schema v4

Top-level fields include:

- `schema = phone_os_profiler_report`
- `schema_version = 4`
- `report_id`
- `generated_at_utc`
- `profiler.version = 0.5`
- `device`, `android`, `cpu`, `memory`, `storage`, `display`, `battery`
- `sensors`, `cameras`, `network`, `audio`, `telephony`
- `boot`, `treble`, `kernel`, `graphics`, `thermal`, `partitions`, `firmware`
- `features`, `root`, `raw`, `errors`
- `permissions`
- `bluetooth`
- `transport_capabilities`
- `collection_notes`
