#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
echo "=== Phone OS Profiler v0.3 — Advanced ADB collector ==="
pkg update -y
pkg install -y python android-tools
adb start-server
adb devices
echo
echo "Подключите целевой телефон по USB/ADB (или ADB over Wi-Fi), разрешите отладку и нажмите Enter."
read -r
python posp_adb_collector.py "$@"
