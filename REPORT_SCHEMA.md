# Report schema v1

Top-level keys:
- schema: `phone_os_profiler_report`
- schema_version: `1`
- report_id: UUID
- generated_at_utc: ISO-8601 UTC
- profiler
- device / android / cpu / memory / storage / display / battery
- sensors / cameras / network / audio / telephony
- boot / treble / kernel / features / root / raw
- errors

`raw` keeps source dumps such as getprop, /proc/partitions and /proc/modules so the report can be re-interpreted later without rescanning the phone.
