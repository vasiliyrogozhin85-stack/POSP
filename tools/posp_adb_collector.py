#!/usr/bin/env python3
import argparse, datetime, hashlib, json, os, subprocess, sys, uuid

MAX_OUTPUT = 2 * 1024 * 1024

def run_host(argv, timeout=25):
    try:
        p = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           text=True, errors='replace', timeout=timeout)
        return p.returncode, p.stdout[:MAX_OUTPUT], p.stderr[:MAX_OUTPUT]
    except Exception as e:
        return -1, '', f'{type(e).__name__}: {e}'

def adb_shell(serial, command, root=False, timeout=30):
    adb = ['adb'] + (['-s', serial] if serial else []) + ['shell']
    if root:
        # Quote for su -c while preserving shell syntax.
        command = "su -c " + subprocess.list2cmdline([command])
    rc, out, err = run_host(adb + [command], timeout)
    return {'command': command, 'exit_code': rc, 'stdout': out, 'stderr': err}

def getprop(serial, key):
    r = adb_shell(serial, f'getprop {key}')
    return r['stdout'].strip()

def main():
    ap = argparse.ArgumentParser(description='Phone OS Profiler v0.3 advanced ADB collector')
    ap.add_argument('--serial', help='ADB serial when more than one device is connected')
    ap.add_argument('--root', action='store_true', help='Use su -c for probes when the device is rooted')
    ap.add_argument('--output', help='Output JSON path')
    args = ap.parse_args()

    rc, out, err = run_host(['adb', 'devices'])
    if rc != 0:
        print('ADB error:', err or out, file=sys.stderr); return 2
    serial = args.serial
    if not serial:
        devs = [ln.split()[0] for ln in out.splitlines()[1:] if '\tdevice' in ln]
        if len(devs) != 1:
            print('Connect exactly one authorized device or use --serial.', file=sys.stderr)
            print(out, file=sys.stderr); return 2
        serial = devs[0]

    if args.root:
        check = adb_shell(serial, 'id', root=True)
        if check['exit_code'] != 0 or 'uid=0' not in check['stdout']:
            print('Root requested but su root is not available.', file=sys.stderr); return 3

    commands = {
      'getprop': 'getprop',
      'uname': 'uname -a',
      'proc_version': 'cat /proc/version',
      'proc_cmdline': 'cat /proc/cmdline',
      'proc_partitions': 'cat /proc/partitions',
      'proc_mounts': 'cat /proc/mounts',
      'df': 'df -h',
      'block_by_name': 'ls -la /dev/block/by-name 2>&1; ls -la /dev/block/platform/bootdevice/by-name 2>&1',
      'block_devices': 'ls -la /dev/block 2>&1 | head -n 400',
      'vintf_vendor': 'for f in /vendor/etc/vintf/*.xml /vendor/manifest*.xml; do [ -f "$f" ] && echo "=== $f ===" && cat "$f"; done',
      'vintf_system': 'for f in /system/etc/vintf/*.xml /system/manifest*.xml; do [ -f "$f" ] && echo "=== $f ===" && cat "$f"; done',
      'vintf_odm': 'for f in /odm/etc/vintf/*.xml /odm/manifest*.xml; do [ -f "$f" ] && echo "=== $f ===" && cat "$f"; done',
      'hal_vendor_lib64': 'find /vendor/lib64/hw -maxdepth 1 -type f -printf "%f\\n" 2>/dev/null | sort',
      'hal_vendor_lib': 'find /vendor/lib/hw -maxdepth 1 -type f -printf "%f\\n" 2>/dev/null | sort',
      'hal_odm_lib64': 'find /odm/lib64/hw -maxdepth 1 -type f -printf "%f\\n" 2>/dev/null | sort',
      'lshal': 'lshal 2>&1',
      'service_list': 'service list',
      'surfaceflinger': 'dumpsys SurfaceFlinger',
      'display': 'dumpsys display; wm size; wm density',
      'graphics': 'dumpsys gpu 2>&1; dumpsys gfxinfo 2>&1 | head -n 300',
      'camera': 'dumpsys media.camera',
      'sensors': 'dumpsys sensorservice',
      'audio': 'dumpsys audio; dumpsys media.audio_flinger',
      'thermal': 'dumpsys thermalservice; for z in /sys/class/thermal/thermal_zone*; do [ -d "$z" ] || continue; echo "=== $z ==="; cat "$z/type" 2>/dev/null; cat "$z/temp" 2>/dev/null; done',
      'power': 'dumpsys power; for p in /sys/class/power_supply/*; do [ -d "$p" ] || continue; echo "=== $p ==="; for f in type status capacity voltage_now current_now charge_full charge_full_design technology model_name manufacturer; do [ -f "$p/$f" ] && echo "$f=$(cat "$p/$f" 2>/dev/null)"; done; done',
      'input': 'cat /proc/bus/input/devices 2>&1; getevent -lp 2>&1',
      'network': 'ip addr; ip route; dumpsys connectivity',
      'wifi': 'dumpsys wifi',
      'bluetooth': 'dumpsys bluetooth_manager',
      'telephony': 'dumpsys telephony.registry; dumpsys isub 2>&1; dumpsys iphonesubinfo 2>&1',
      'usb': 'dumpsys usb; getprop sys.usb.config; getprop sys.usb.state',
      'packages_features': 'pm list features',
      'selinux': 'getenforce; ls -la /sys/fs/selinux 2>&1 | head -n 80',
      'kernel_modules': 'cat /proc/modules 2>&1',
      'gpu_sysfs': 'for d in /sys/class/misc/mali* /sys/devices/platform/*gpu* /sys/kernel/debug/mali*; do [ -e "$d" ] && echo "=== $d ===" && find "$d" -maxdepth 2 -type f -o -type l 2>/dev/null | head -n 300; done',
      'device_tree': 'for b in /proc/device-tree /sys/firmware/devicetree/base; do [ -d "$b" ] && echo "=== $b ===" && find "$b" -maxdepth 4 -type f 2>/dev/null | head -n 2000; done',
      'fstab': 'for f in /vendor/etc/fstab* /odm/etc/fstab* /system/etc/fstab* /fstab.*; do [ -f "$f" ] && echo "=== $f ===" && cat "$f"; done',
      'init_rc_index': 'find /vendor/etc/init /odm/etc/init /system/etc/init -maxdepth 2 -type f 2>/dev/null | sort | head -n 1200',
      'firmware_dirs': 'ls -la /vendor/firmware /vendor/firmware_mnt /odm/firmware 2>&1',
      'cpu': 'cat /proc/cpuinfo; for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d "$p" ] || continue; echo "=== $p ==="; grep -H . "$p"/{cpuinfo_max_freq,cpuinfo_min_freq,scaling_governor,scaling_available_frequencies,scaling_available_governors} 2>/dev/null; done',
      'memory': 'cat /proc/meminfo',
      'boot_props': 'getprop | grep -E "ro\\.boot|ro\\.(build|vendor|product|odm)|gsm\\.|persist\\.radio|ro\\.treble|vndk|dynamic|virtual_ab"',
    }

    results = {}
    total = len(commands)
    for i, (name, cmd) in enumerate(commands.items(), 1):
        print(f'[{i}/{total}] {name}', flush=True)
        results[name] = adb_shell(serial, cmd, root=args.root, timeout=45)

    report = {
      'schema': 'phone_os_profiler_advanced_report',
      'schema_version': 1,
      'report_id': str(uuid.uuid4()),
      'generated_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),
      'profiler_version': '0.3',
      'mode': 'adb_root' if args.root else 'adb',
      'device': {
        'serial': serial,
        'manufacturer': getprop(serial, 'ro.product.manufacturer'),
        'model': getprop(serial, 'ro.product.model'),
        'device': getprop(serial, 'ro.product.device'),
        'product': getprop(serial, 'ro.product.name'),
        'board': getprop(serial, 'ro.product.board'),
        'hardware': getprop(serial, 'ro.hardware'),
        'platform': getprop(serial, 'ro.board.platform'),
        'android_release': getprop(serial, 'ro.build.version.release'),
        'sdk': getprop(serial, 'ro.build.version.sdk'),
        'security_patch': getprop(serial, 'ro.build.version.security_patch'),
        'fingerprint': getprop(serial, 'ro.build.fingerprint'),
      },
      'commands': results,
    }

    if not args.output:
        stamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
        args.output = f'PhoneOSProfiler_Advanced_{stamp}.json'
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    h = hashlib.sha256(open(args.output,'rb').read()).hexdigest()
    print(f'\nSaved: {os.path.abspath(args.output)}')
    print(f'SHA-256: {h}')
    return 0

if __name__ == '__main__':
    sys.exit(main())
