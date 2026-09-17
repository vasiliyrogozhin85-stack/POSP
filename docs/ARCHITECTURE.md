# uPOSPa 0.8 beta architecture

UI layer: ModeChoice, Dashboard, Host, Client, Report, Service, HardwareTest, History, PackageLibrary.

Connection layer: AdbUsbClient, AoaHostConnection, FastbootUsbClient, UsbModeDetector.

Diagnostics layer: DeviceSnapshot, PartitionInspector, HardwareTestEngine, OemProfileEngine.

Firmware layer: FirmwareAnalyzer, FirmwareManifest, FirmwarePackageCache, CompatibilityEvaluator, PreflightEngine, SafetyGate, InstallPlanner.

Persistence: ReportLogger, DeviceHistoryStore, SessionStore, OperationJournal.

The design deliberately keeps destructive writes behind explicit checks and confirmations.
