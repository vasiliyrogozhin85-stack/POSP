# Safety model

1. Identification before modification: manufacturer/model/codename/product and Fastboot product are compared when available.
2. Integrity: selected packages are hashed with SHA-256.
3. Pre-flight: battery, storage, bootloader state, package/device metadata and route are checked.
4. Dry-run first: ZIP/payload packages create an Install Plan instead of being blindly written.
5. Direct IMG: only through SERVICE, with explicit target partition and manual confirmation.
6. Dynamic partitions require Fastbootd.
7. userdata direct image remains blocked in this beta.
8. Logs/reports are saved so failed sessions can be diagnosed.
