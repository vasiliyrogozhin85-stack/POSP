package com.vasiliyrogozhin85.upospa;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.os.*;
import android.provider.Settings;

import org.json.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class DeviceSnapshot {
    static JSONObject collect(Context ctx) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "upospa_device_report");
        root.put("schema_version", 9);
        root.put("app_version", "0.8-beta-wizard");
        root.put("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));

        JSONObject d = new JSONObject();
        d.put("manufacturer", Build.MANUFACTURER);
        d.put("brand", Build.BRAND);
        d.put("model", Build.MODEL);
        d.put("product", Build.PRODUCT);
        d.put("device", Build.DEVICE);
        d.put("board", Build.BOARD);
        d.put("hardware", Build.HARDWARE);
        d.put("bootloader", Build.BOOTLOADER);
        d.put("fingerprint", Build.FINGERPRINT);
        d.put("abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
        root.put("device", d);

        JSONObject a = new JSONObject();
        a.put("release", Build.VERSION.RELEASE);
        a.put("sdk", Build.VERSION.SDK_INT);
        a.put("security_patch", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "");
        a.put("incremental", Build.VERSION.INCREMENTAL);
        root.put("android", a);

        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        JSONObject mem = new JSONObject();
        mem.put("total_bytes", mi.totalMem);
        mem.put("avail_bytes", mi.availMem);
        mem.put("low_memory", mi.lowMemory);
        root.put("memory", mem);

        StatFs sf = new StatFs(ctx.getFilesDir().getAbsolutePath());
        JSONObject st = new JSONObject();
        st.put("total_bytes", sf.getTotalBytes());
        st.put("avail_bytes", sf.getAvailableBytes());
        root.put("storage", st);

        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        JSONObject bat = new JSONObject();
        bat.put("capacity_percent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        bat.put("charge_counter_uah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
        bat.put("current_now_ua", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        root.put("battery", bat);

        PackageManager pm = ctx.getPackageManager();
        JSONObject caps = new JSONObject();
        caps.put("usb_host", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST));
        caps.put("usb_accessory", pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY));
        caps.put("bluetooth", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        caps.put("wifi", pm.hasSystemFeature(PackageManager.FEATURE_WIFI));
        caps.put("camera", pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        caps.put("nfc", pm.hasSystemFeature(PackageManager.FEATURE_NFC));
        caps.put("gps", pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));
        caps.put("telephony", pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        root.put("capabilities", caps);

        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        JSONObject usb = new JSONObject();
        usb.put("attached_device_count", um == null ? 0 : um.getDeviceList().size());
        usb.put("accessory_count", (um == null || um.getAccessoryList() == null) ? 0 : um.getAccessoryList().length);
        root.put("usb", usb);

        Map<String,String> gp = readGetprop();
        JSONObject props = new JSONObject();
        String[] wanted = {
                "ro.treble.enabled", "ro.boot.slot_suffix", "ro.boot.slot",
                "ro.boot.flash.locked", "ro.boot.vbmeta.device_state",
                "ro.boot.verifiedbootstate", "ro.build.ab_update",
                "ro.virtual_ab.enabled", "ro.boot.dynamic_partitions",
                "ro.product.first_api_level", "ro.vendor.build.security_patch",
                "ro.build.version.security_patch", "ro.boot.hardware.sku",
                "ro.product.vendor.device", "ro.product.system.device",
                "ro.bootmode", "ro.crypto.state", "ro.crypto.type",
                "ro.build.type", "ro.debuggable"
        };
        for (String key : wanted) if (gp.containsKey(key)) props.put(key, gp.get(key));
        root.put("system_properties", props);

        JSONObject sec = new JSONObject();
        String sel = readOneLine("/sys/fs/selinux/enforce");
        sec.put("selinux", "1".equals(sel) ? "enforcing" : "0".equals(sel) ? "permissive" : "unknown");
        sec.put("adb_enabled", safeIntSetting(ctx, Settings.Global.ADB_ENABLED) == 1);
        sec.put("developer_options", safeIntSetting(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1);
        root.put("security", sec);

        JSONObject kernel = new JSONObject();
        kernel.put("release", readOneLine("/proc/sys/kernel/osrelease"));
        kernel.put("version", readOneLine("/proc/version"));
        root.put("kernel", kernel);

        JSONArray sensors = new JSONArray();
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
                JSONObject x = new JSONObject();
                x.put("name", s.getName());
                x.put("vendor", s.getVendor());
                x.put("type", s.getType());
                sensors.put(x);
                if (sensors.length() >= 64) break;
            }
        }
        root.put("sensors", sensors);

        JSONArray features = new JSONArray();
        for (FeatureInfo f : pm.getSystemAvailableFeatures()) if (f.name != null) features.put(f.name);
        root.put("features", features);

        // Best-effort local partition map. On some OEM builds SELinux hides /dev/block/by-name;
        // PartitionInspector then falls back to /proc/partitions and /proc/mounts.
        root.put("partitions_local", PartitionInspector.collectLocal());
        root.put("hardware_quick", HardwareTestEngine.quick(ctx));
        root.put("oem_profile", OemProfileEngine.profile(root));
        return root;
    }

    static File write(Context ctx, JSONObject root) throws Exception {
        File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = ctx.getFilesDir();
        File dir = new File(base, "uPOSPa/reports");
        dir.mkdirs();
        File out = new File(dir, "uPOSPa_Report_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(root.toString(2).getBytes("UTF-8"));
        }
        return out;
    }

    static File collectToFile(Context ctx) throws Exception { return write(ctx, collect(ctx)); }

    private static int safeIntSetting(Context c, String key) {
        try { return Settings.Global.getInt(c.getContentResolver(), key, 0); }
        catch (Exception e) { return 0; }
    }

    private static String readOneLine(String p) {
        try (BufferedReader b = new BufferedReader(new FileReader(p))) {
            String s = b.readLine(); return s == null ? "" : s.trim();
        } catch (Exception e) { return ""; }
    }

    private static Map<String,String> readGetprop() {
        Map<String,String> out = new LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int b = line.indexOf("]: [");
                    int c = line.lastIndexOf(']');
                    if (line.startsWith("[") && b > 1 && c > b + 4) {
                        out.put(line.substring(1,b), line.substring(b+4,c));
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private DeviceSnapshot() {}
}
