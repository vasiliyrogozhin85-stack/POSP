package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ReportCollector {
    static final String[][] COMMANDS = new String[][]{
            {"00_identity.txt", "echo manufacturer=$(getprop ro.product.manufacturer); echo model=$(getprop ro.product.model); echo product=$(getprop ro.product.name); echo device=$(getprop ro.product.device); echo board=$(getprop ro.product.board); echo hardware=$(getprop ro.hardware); echo platform=$(getprop ro.board.platform)"},
            {"01_getprop.txt", "getprop"},
            {"02_uname.txt", "uname -a; cat /proc/version 2>&1; cat /proc/cmdline 2>&1"},
            {"03_cpuinfo.txt", "cat /proc/cpuinfo; echo --- cpufreq ---; for p in /sys/devices/system/cpu/cpufreq/policy*; do echo ==== $p ====; cat $p/cpuinfo_min_freq $p/cpuinfo_max_freq $p/scaling_governor 2>&1; done"},
            {"04_meminfo.txt", "cat /proc/meminfo"},
            {"05_mounts_storage.txt", "cat /proc/mounts; echo --- df ---; df -h"},
            {"06_partitions.txt", "cat /proc/partitions 2>&1; echo '--- by-name ---'; ls -l /dev/block/by-name 2>&1; ls -l /dev/block/platform/bootdevice/by-name 2>&1; echo '--- mapper ---'; ls -l /dev/block/mapper 2>&1"},
            {"07_boot_slots_avb.txt", "echo slot=$(getprop ro.boot.slot_suffix); echo dynamic=$(getprop ro.boot.dynamic_partitions); echo virtual_ab=$(getprop ro.virtual_ab.enabled); echo treble=$(getprop ro.treble.enabled); echo vndk=$(getprop ro.vndk.version); echo verified=$(getprop ro.boot.verifiedbootstate); echo vbmeta_state=$(getprop ro.boot.vbmeta.device_state); echo flash_locked=$(getprop ro.boot.flash.locked); echo verity=$(getprop ro.boot.veritymode)"},
            {"08_services.txt", "service list"},
            {"09_lshal.txt", "lshal 2>&1"},
            {"10_vintf.txt", "for f in /vendor/etc/vintf/manifest*.xml /system/etc/vintf/manifest*.xml /odm/etc/vintf/manifest*.xml /product/etc/vintf/manifest*.xml; do [ -f \"$f\" ] && echo ==== $f ==== && cat \"$f\" 2>&1; done"},
            {"11_surfaceflinger.txt", "dumpsys SurfaceFlinger 2>&1"},
            {"12_display.txt", "dumpsys display 2>&1"},
            {"13_camera.txt", "dumpsys media.camera 2>&1"},
            {"14_sensorservice.txt", "dumpsys sensorservice 2>&1"},
            {"15_audio.txt", "dumpsys audio 2>&1; dumpsys media.audio_flinger 2>&1"},
            {"16_thermal.txt", "dumpsys thermalservice 2>&1; for z in /sys/class/thermal/thermal_zone*; do echo ==== $z ====; cat $z/type 2>/dev/null; cat $z/temp 2>/dev/null; done"},
            {"17_power_battery.txt", "dumpsys power 2>&1; dumpsys battery 2>&1"},
            {"18_input.txt", "dumpsys input 2>&1; getevent -lp 2>&1"},
            {"19_wifi.txt", "dumpsys wifi 2>&1"},
            {"20_bluetooth.txt", "dumpsys bluetooth_manager 2>&1"},
            {"21_telephony.txt", "dumpsys telephony.registry 2>&1"},
            {"22_usb.txt", "dumpsys usb 2>&1"},
            {"23_features.txt", "pm list features"},
            {"24_selinux.txt", "getenforce 2>&1; cat /sys/fs/selinux/enforce 2>&1"},
            {"25_gpu.txt", "dumpsys SurfaceFlinger 2>&1 | grep -iE 'GLES|OpenGL|Vulkan|GPU' | head -200; for p in /sys/class/kgsl/kgsl-3d0 /sys/devices/platform/*gpu* /sys/class/devfreq/*gpu*; do echo ==== $p ====; ls -la $p 2>&1; done"},
            {"26_device_tree_index.txt", "find /proc/device-tree /sys/firmware/devicetree/base -maxdepth 5 -type f 2>/dev/null | head -8000"},
            {"27_fstab.txt", "ls -la /vendor/etc/fstab* /system/etc/fstab* /odm/etc/fstab* /product/etc/fstab* 2>&1; cat /vendor/etc/fstab* /system/etc/fstab* /odm/etc/fstab* /product/etc/fstab* 2>&1"},
            {"28_init_index.txt", "find /system/etc/init /vendor/etc/init /odm/etc/init -maxdepth 2 -type f 2>/dev/null | sort | head -5000"},
            {"29_firmware_index.txt", "find /vendor/firmware /vendor/firmware_mnt /odm/firmware -maxdepth 2 -type f 2>/dev/null | sort | head -5000"},
            {"30_packages_permissions.txt", "pm list packages -f; echo --- permissions ---; dumpsys package 2>&1 | grep -E 'android.permission|granted=true' | head -4000"}
    };

    interface Progress { void onProgress(String line); }

    static File collect(Context ctx, AdbUsbClient adb, String targetSummary, Progress progress, ReadinessReport readiness) throws Exception {
        File base = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "POSPHost");
        if (!base.exists() && !base.mkdirs()) throw new IOException("Cannot create output folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File zip = new File(base, "POSP_ADB_Report_" + stamp + ".zip");
        JSONObject manifest = new JSONObject();
        manifest.put("schema", "posp_host_adb_bundle");
        manifest.put("schema_version", 7);
        manifest.put("host_app", "POSP Host 0.7");
        manifest.put("created_utc", utcNow());
        manifest.put("host_android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        manifest.put("target_usb", targetSummary);
        if (readiness != null) manifest.put("readiness", readiness.toJson());
        JSONArray entries = new JSONArray();
        int success = 0, partial = 0, errors = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String[] item : COMMANDS) {
                String name = item[0], cmd = item[1];
                progress.onProgress("ADB: " + name);
                String out;
                long start = System.currentTimeMillis();
                String status = "collected";
                try { out = adb.shell(cmd, 60000); }
                catch (Exception e) { out = "<ERROR> " + e + "\n"; status = "error"; }
                String low = out.toLowerCase(Locale.US);
                if (status.equals("collected") && (low.contains("permission denied") || low.contains("not permitted") || low.contains("can't find service"))) status = "partial";
                if (status.equals("collected")) success++; else if (status.equals("partial")) partial++; else errors++;
                byte[] data = out.getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry("raw/" + name)); zos.write(data); zos.closeEntry();
                JSONObject e = new JSONObject();
                e.put("file", "raw/" + name); e.put("command", cmd); e.put("bytes", data.length);
                e.put("duration_ms", System.currentTimeMillis()-start); e.put("sha256", sha256(data)); e.put("status", status);
                entries.put(e);
            }
            JSONObject completeness = new JSONObject();
            completeness.put("collected", success); completeness.put("partial", partial); completeness.put("errors", errors);
            completeness.put("total", COMMANDS.length); completeness.put("percent", (int)Math.round(100.0 * success / COMMANDS.length));
            manifest.put("completeness", completeness);
            manifest.put("entries", entries);
            byte[] m = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("manifest.json")); zos.write(m); zos.closeEntry();
        }
        return zip;
    }

    static String sha256(File f) throws Exception { try(FileInputStream in=new FileInputStream(f)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[]b=new byte[8192];int n;while((n=in.read(b))>0)md.update(b,0,n);return hex(md.digest());} }
    private static String sha256(byte[] d)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(d));}
    private static String hex(byte[] d){StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.US,"%02x",b));return s.toString();}
    private static String utcNow(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date());}
}
